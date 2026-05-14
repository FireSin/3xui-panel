package com.firesin.xuipanel.core.sampler

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.firesin.xuipanel.core.data.repository.AutoBackupSchedule
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages [BackupWorker] scheduling in WorkManager.
 *
 * Call [apply] whenever the user changes the schedule setting.
 * Call [triggerNow] to enqueue a one-shot backup (from "Сделать сейчас").
 */
@Singleton
class BackupScheduler @Inject constructor() {

    /**
     * Applies the given [schedule] to WorkManager:
     * - [AutoBackupSchedule.OFF] → cancels any existing periodic work.
     * - [AutoBackupSchedule.DAILY] / [AutoBackupSchedule.WEEKLY] → enqueues/replaces periodic work.
     */
    fun apply(workManager: WorkManager, schedule: AutoBackupSchedule) {
        when (schedule) {
            AutoBackupSchedule.OFF -> workManager.cancelUniqueWork(PERIODIC_WORK_NAME)

            AutoBackupSchedule.DAILY -> enqueue(workManager, DAILY_HOURS, TimeUnit.HOURS)

            AutoBackupSchedule.WEEKLY -> enqueue(workManager, WEEKLY_DAYS, TimeUnit.DAYS)
        }
    }

    /**
     * Enqueues a one-time backup immediately.
     * Does not affect the periodic schedule.
     */
    fun triggerNow(workManager: WorkManager) {
        val request = OneTimeWorkRequest.Builder(BackupWorker::class.java)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueue(request)
    }

    private fun enqueue(workManager: WorkManager, interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequest.Builder(BackupWorker::class.java, interval, unit)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val PERIODIC_WORK_NAME = "auto-backup"
        private const val DAILY_HOURS = 24L
        private const val WEEKLY_DAYS = 7L
        private const val BACKOFF_SECONDS = 30L
    }
}
