package com.firesin.xuipanel.core.data.prefs

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object BackupSchedulePrefs {
    val BACKUP_TARGET_URI = stringPreferencesKey("backup_target_uri")
    val BACKUP_SCHEDULE = stringPreferencesKey("backup_schedule")
    val BACKUP_LAST_RUN_AT = longPreferencesKey("backup_last_run_at")
    val BACKUP_LAST_RESULT = stringPreferencesKey("backup_last_result")
}
