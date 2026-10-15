package com.localdrop.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Camera auto backup runs on WorkManager: it survives reboots without a boot receiver, respects
 * Doze and battery limits, retries with backoff, and only runs on a network the PC can be reached on.
 * The heavy lifting (MediaStore query, push, dedup) lives in [AutoBackupWorker].
 */
object AutoBackupScheduler {
    const val WORK_NAME = "localdrop_auto_backup"
    private const val INTERVAL_MINUTES = 30L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWork>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/** One backup check. Retries later when the PC is unreachable or Android blocks starting the transfer service. */
class AutoBackupWork(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!AutoBackupStore.isEnabled(applicationContext)) return Result.success()
        return when (AutoBackupWorker.runCheck(applicationContext)) {
            AutoBackupWorker.Outcome.RETRY -> Result.retry()
            else -> Result.success()
        }
    }
}
