package com.localdrop.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Alarm-based scheduling for camera auto backup. Runs roughly every 30 minutes
 * while the feature is on, and right after boot. Deliberately tiny: the heavy
 * lifting (MediaStore query, push, dedup) lives in [AutoBackupWorker].
 */
object AutoBackupScheduler {
    private const val REQUEST_CODE = 4243
    private const val INTERVAL_MS = 30L * 60 * 1000

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val trigger = System.currentTimeMillis() + 15_000 // first check shortly after enabling
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val trigger = System.currentTimeMillis() + INTERVAL_MS
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AutoBackupAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** Alarm tick (and boot event) → run one backup check. */
class AutoBackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AutoBackupStore.isEnabled(context)) return
        AutoBackupWorker.runCheck(context.applicationContext)
        AutoBackupScheduler.scheduleNext(context.applicationContext)
    }
}

/** Runs after boot so backups continue across restarts. */
class AutoBackupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AutoBackupStore.isEnabled(context)) {
            AutoBackupScheduler.scheduleNext(context.applicationContext)
        }
    }
}
