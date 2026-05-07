package com.firesin.xuipanel.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.firesin.xuipanel.core.data.prefs.AppSecurityPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSecurityRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSecurityRepository {

    override val isLockEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[AppSecurityPrefs.APP_LOCK_ENABLED] ?: false
        }

    override suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSecurityPrefs.APP_LOCK_ENABLED] = enabled
        }
    }

    override val isLockOnPauseEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[AppSecurityPrefs.APP_LOCK_ON_PAUSE_ENABLED] ?: false
        }

    override suspend fun setLockOnPauseEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSecurityPrefs.APP_LOCK_ON_PAUSE_ENABLED] = enabled
        }
    }
}
