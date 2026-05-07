package com.firesin.xuipanel.core.sampler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Thin WorkManager wrapper around [TrafficSamplerCore].
 *
 * Retry policy (§3 design doc):
 * - [Result.retry] only when 0/N panels responded successfully (presumed global network failure).
 * - [Result.success] on partial success or no panels configured.
 */
@HiltWorker
class TrafficSamplerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val core: TrafficSamplerCore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (core.runOnce()) {
            TrafficSamplerCore.Outcome.AllFailed -> Result.retry()
            TrafficSamplerCore.Outcome.PartialOrFullSuccess,
            TrafficSamplerCore.Outcome.NoPanels,
            -> Result.success()
        }
    }
}
