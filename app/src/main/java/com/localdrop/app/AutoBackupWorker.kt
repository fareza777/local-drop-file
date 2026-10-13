package com.localdrop.app

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One auto-backup pass: query MediaStore for new camera media, hand the URIs to
 * the existing PushService (which resumes and lets the receiver skip duplicates),
 * and record what went out so the next pass only looks forward.
 *
 * Dedup layers: the [AutoBackupStore] timestamp keeps each pass from re-scanning
 * old photos; per-row "pushed" markers are written when the batch finishes; and
 * PushService's server-driven skip is the final safety net.
 */
object AutoBackupWorker {

    private val checkRunning = AtomicBoolean(false)
    private val threadRunning = AtomicBoolean(false)

    @Volatile private var batchIds: List<Long> = emptyList()

    fun runCheck(context: Context) {
        if (!AutoBackupStore.isEnabled(context)) return
        if (PushService.state.value.running) return // a manual push is in flight; do not stack
        if (!checkRunning.compareAndSet(false, true)) return
        try {
            val address = context.getSharedPreferences("localdrop", Context.MODE_PRIVATE)
                .getString("push_address", "")?.trim().orEmpty()
            if (address.isEmpty()) return

            val items = AutoBackupStore.queryNewMedia(context, AutoBackupStore.lastTimestamp(context))
            AutoBackupStore.markRun(context)
            if (items.isEmpty()) return

            batchIds = items.map { it.first }
            val uris = batchIds.map { AutoBackupStore.contentUriFor(it).toString() }
            AutoBackupStore.saveLastTimestamp(context, items.last().second)
            PushService.start(context, address, uris)
            watchCompletion(context.applicationContext)
        } finally {
            checkRunning.set(false)
        }
    }

    /**
     * One watcher thread per batch: when PushService reports the batch finished,
     * mark the completed rows as pushed. filesDone counts both sent and skipped
     * files (skipped means the PC already has it), so "pushed" really means
     * "safely on the PC".
     */
    private fun watchCompletion(context: Context) {
        if (!threadRunning.compareAndSet(false, true)) return
        Thread {
            try {
                val ids = batchIds
                var waited = 0
                // Wait for the batch to end (success, failure, or stop), max 1 hour.
                while (PushService.state.value.running && waited < 60 * 60 * 1000) {
                    Thread.sleep(5_000); waited += 5_000
                }
                val s = PushService.state.value
                if (s.filesDone > 0) {
                    ids.take(s.filesDone).forEach { id ->
                        if (!AutoBackupStore.alreadyPushed(context, id)) {
                            AutoBackupStore.markPushed(context, id)
                        }
                    }
                }
            } catch (_: Exception) {
                // Markers are best-effort; the receiver-side skip still protects us.
            } finally {
                threadRunning.set(false)
            }
        }.start()
    }
}
