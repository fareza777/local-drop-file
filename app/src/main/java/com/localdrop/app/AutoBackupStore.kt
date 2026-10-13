package com.localdrop.app

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Auto Backup Kamera (#4): pushes new photos/videos from the camera roll to the
 * paired PC, making the PC a private, cloud-free replacement for Google Photos.
 *
 * Deduplication is two-layered, so 10,000 old photos are never re-scanned or
 * re-sent after the first run:
 *  1. `lastTimestampMs` — MediaStore is queried only for items added after the
 *     last successful batch (the index the feature note called for).
 *  2. `pushed:<fileId>` — a per-file "already on the PC" marker written after
 *     each successful push; PushService's server-driven skip (the receiver
 *     answers `skip: true` for files it already holds) is the final safety net.
 */
object AutoBackupStore {
    private const val PREFS = "auto_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_TS = "last_timestamp_ms"
    private const val KEY_LAST_RUN = "last_run_ms"
    private const val KEY_PUSHED_PREFIX = "pushed:"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) schedule(context) else cancel(context)
    }

    fun lastRunAt(context: Context): Long = prefs(context).getLong(KEY_LAST_RUN, 0L)

    fun markRun(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_RUN, System.currentTimeMillis()).apply()
    }

    fun lastTimestamp(context: Context): Long = prefs(context).getLong(KEY_LAST_TS, 0L)

    fun saveLastTimestamp(context: Context, ts: Long) {
        prefs(context).edit().putLong(KEY_LAST_TS, ts).apply()
    }

    /** True when this exact MediaStore row was already pushed successfully. */
    fun alreadyPushed(context: Context, id: Long): Boolean =
        prefs(context).getBoolean(KEY_PUSHED_PREFIX + id, false)

    fun markPushed(context: Context, id: Long) {
        prefs(context).edit().putBoolean(KEY_PUSHED_PREFIX + id, true).apply()
    }

    /** Only called when the user turns the feature off: forgets the pushed-marker set. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // Scheduling is provided by AutoBackupScheduler (alarm-based).
    fun schedule(context: Context) = AutoBackupScheduler.schedule(context)
    fun cancel(context: Context) = AutoBackupScheduler.cancel(context)

    /**
     * Queries MediaStore for camera-roll images and videos newer than [afterMs],
     * skipping rows already pushed. Returns ordered pairs of (row id, timestamp).
     */
    fun queryNewMedia(context: Context, afterMs: Long, limit: Int = 5000): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        val collection: Uri =
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        // DATE_ADDED is seconds; compare against seconds. Only images and videos.
        val afterSec = afterMs / 1000
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ? AND " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(afterSec.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        try {
            context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iTs = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(iId)
                    val tsSec = c.getLong(iTs)
                    if (!alreadyPushed(context, id)) out.add(id to tsSec * 1000L)
                }
            }
        } catch (_: Exception) {
            // MediaStore can throw on some vendor builds; treat as "nothing new".
        }
        return out
    }

    fun contentUriFor(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id)
}
