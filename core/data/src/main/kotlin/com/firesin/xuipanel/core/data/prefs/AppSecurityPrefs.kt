package com.firesin.xuipanel.core.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey

object AppSecurityPrefs {
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_ON_PAUSE_ENABLED = booleanPreferencesKey("app_lock_on_pause_enabled")
}
