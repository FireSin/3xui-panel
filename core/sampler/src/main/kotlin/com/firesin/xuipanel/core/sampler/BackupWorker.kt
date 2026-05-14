package com.firesin.xuipanel.core.sampler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs one automatic backup tick.
 *
 * Retry policy:
 * - [Result.retry] only when all panels fail AND the cause appears network-related
 *   (i.e. [BackupWorkerCore.Outcome.Failure] from an all-panel network outage).
 * - [Result.success] on partial success, zero panels, or 2FA-skip.
 * - Retention is applied by [BackupWorkerCore] only on per-panel success.
 *
 * Constraints set at schedule time: [NetworkType.CONNECTED], no charging constraint.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val core: BackupWorkerCore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (core.runOnce()) {
            is BackupWorkerCore.Outcome.Failure -> Result.retry()
            is BackupWorkerCore.Outcome.Success,
            is BackupWorkerCore.Outcome.PartialFailure,
            -> Result.success()
        }
    }
}
