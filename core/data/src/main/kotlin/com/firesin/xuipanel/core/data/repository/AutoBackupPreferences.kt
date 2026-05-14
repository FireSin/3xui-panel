package com.firesin.xuipanel.core.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Persisted settings for the automatic x-ui.db backup feature.
 */
interface AutoBackupPreferences {

    /** SAF tree URI chosen by the user as the backup destination. Null if not set. */
    val targetUri: Flow<Uri?>

    suspend fun setTargetUri(uri: Uri?)

    /** Active schedule. Default: [AutoBackupSchedule.OFF]. */
    val schedule: Flow<AutoBackupSchedule>

    suspend fun setSchedule(schedule: AutoBackupSchedule)

    /**
     * Epoch-millis timestamp of the most recent backup run.
     * Null if the worker has never run.
     */
    val lastRunAt: Flow<Long?>

    /**
     * Human-readable result string of the most recent run.
     * Null if the worker has never run.
     */
    val lastResult: Flow<String?>

    suspend fun setLastRun(timestamp: Long, result: String)
}
