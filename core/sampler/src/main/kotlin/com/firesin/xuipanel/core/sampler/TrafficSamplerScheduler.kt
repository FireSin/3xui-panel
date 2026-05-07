package com.firesin.xuipanel.core.sampler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers a periodic [TrafficSamplerWorker] with WorkManager.
 *
 * Scheduling parameters (§3 design doc):
 * - 60-minute period (WorkManager minimum).
 * - 60-second initial delay to avoid cold-start network churn.
 * - Exponential backoff starting at 30 s.
 * - Requires [NetworkType.CONNECTED]; no battery-not-low constraint.
 * - [ExistingPeriodicWorkPolicy.KEEP] — if a schedule already exists, leave it intact.
 */
@Singleton
class TrafficSamplerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleIfNeeded(workManager: WorkManager) {
        val request = PeriodicWorkRequest.Builder(
            TrafficSamplerWorker::class.java,
            PERIOD_MINUTES,
            TimeUnit.MINUTES,
        )
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "traffic-sampler"
        const val PERIOD_MINUTES = 60L
        const val INITIAL_DELAY_SECONDS = 60L
        const val BACKOFF_SECONDS = 30L
    }
}
