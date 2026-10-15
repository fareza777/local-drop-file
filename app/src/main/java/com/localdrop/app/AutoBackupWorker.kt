package com.localdrop.app

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One auto-backup pass: query MediaStore for camera media added since the last fully completed
 * batch, hand the URIs to PushService (which resumes and lets the receiver skip duplicates),
 * and advance the checkpoint only for files that really reached the PC.
 *
 * A failed or interrupted batch therefore never loses files: the checkpoint stays behind them
 * and the next pass retries. Per-file "pushed" markers and the receiver's server-driven skip
 * keep retries from sending duplicates.
 */
object AutoBackupWorker {

    enum class Outcome { NOTHING_NEW, STARTED, SKIPPED, RETRY }

    private val checkRunning = AtomicBoolean(false)
    private val threadRunning = AtomicBoolean(false)

    @Volatile private var batch: List<Pair<Long, Long>> = emptyList()

    fun runCheck(context: Context): Outcome {
        if (!AutoBackupStore.isEnabled(context)) return Outcome.SKIPPED
        if (PushService.state.value.running) return Outcome.SKIPPED // a push is in flight; do not stack
        if (!checkRunning.compareAndSet(false, true)) return Outcome.SKIPPED
        try {
            val address = context.getSharedPreferences("localdrop", Context.MODE_PRIVATE)
                .getString("push_address", "")?.trim().orEmpty()
            if (address.isEmpty()) return Outcome.SKIPPED

            val items = AutoBackupStore.queryNewMedia(context, AutoBackupStore.lastTimestamp(context))
            AutoBackupStore.markRun(context)
            if (items.isEmpty()) return Outcome.NOTHING_NEW

            batch = items
            try {
                PushService.start(context, address, items.map { AutoBackupStore.contentUriFor(it.first).toString() })
            } catch (_: Exception) {
                // Android may refuse to start a foreground service from the background; try again later.
                return Outcome.RETRY
            }
            watchCompletion(context.applicationContext)
            return Outcome.STARTED
        } finally {
            checkRunning.set(false)
        }
    }

    /**
     * When PushService reports the batch finished, mark completed rows as pushed and move the
     * checkpoint to the newest completed file. filesDone counts sent and skipped files (the PC
     * already has a skipped one), so "pushed" means "safely on the PC".
     */
    private fun watchCompletion(context: Context) {
        if (!threadRunning.compareAndSet(false, true)) return
        Thread {
            try {
                val items = batch
                var waited = 0
                while (PushService.state.value.running && waited < 60 * 60 * 1000) {
                    Thread.sleep(5_000); waited += 5_000
                }
                val completed = PushService.state.value.filesDone.coerceIn(0, items.size)
                items.take(completed).forEach { (id, _) -> AutoBackupStore.markPushed(context, id) }
                if (completed > 0) AutoBackupStore.saveLastTimestamp(context, items[completed - 1].second)
            } catch (_: Exception) {
                // Markers are best-effort; the receiver-side skip still protects us.
            } finally {
                threadRunning.set(false)
            }
        }.start()
    }
}
