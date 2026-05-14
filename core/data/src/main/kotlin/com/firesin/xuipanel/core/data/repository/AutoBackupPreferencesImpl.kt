package com.firesin.xuipanel.core.data.repository

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.firesin.xuipanel.core.data.prefs.BackupSchedulePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoBackupPreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoBackupPreferences {

    override val targetUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[BackupSchedulePrefs.BACKUP_TARGET_URI]?.let { Uri.parse(it) }
    }

    override suspend fun setTargetUri(uri: Uri?) {
        dataStore.edit { prefs ->
            if (uri != null) {
                prefs[BackupSchedulePrefs.BACKUP_TARGET_URI] = uri.toString()
            } else {
                prefs.remove(BackupSchedulePrefs.BACKUP_TARGET_URI)
            }
        }
    }

    override val schedule: Flow<AutoBackupSchedule> = dataStore.data.map { prefs ->
        val raw = prefs[BackupSchedulePrefs.BACKUP_SCHEDULE]
        raw?.let { runCatching { AutoBackupSchedule.valueOf(it) }.getOrNull() }
            ?: AutoBackupSchedule.OFF
    }

    override suspend fun setSchedule(schedule: AutoBackupSchedule) {
        dataStore.edit { prefs ->
            prefs[BackupSchedulePrefs.BACKUP_SCHEDULE] = schedule.name
        }
    }

    override val lastRunAt: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[BackupSchedulePrefs.BACKUP_LAST_RUN_AT]
    }

    override val lastResult: Flow<String?> = dataStore.data.map { prefs ->
        prefs[BackupSchedulePrefs.BACKUP_LAST_RESULT]
    }

    override suspend fun setLastRun(timestamp: Long, result: String) {
        dataStore.edit { prefs ->
            prefs[BackupSchedulePrefs.BACKUP_LAST_RUN_AT] = timestamp
            prefs[BackupSchedulePrefs.BACKUP_LAST_RESULT] = result
        }
    }
}
