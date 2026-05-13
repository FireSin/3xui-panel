package com.firesin.xuipanel.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.prefs.AppSecurityPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
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

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            val raw = prefs[AppSecurityPrefs.THEME_MODE]
            raw?.let { runCatching { ThemeMode.valueOf(it.uppercase()) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[AppSecurityPrefs.THEME_MODE] = mode.name.lowercase()
        }
    }

    override val installId: Flow<String> = flow {
        val existing = dataStore.data
            .map { prefs -> prefs[AppSecurityPrefs.INSTALL_ID] }
            .first()
        val id = existing ?: run {
            val generated = UUID.randomUUID().toString()
            dataStore.edit { prefs ->
                if (prefs[AppSecurityPrefs.INSTALL_ID] == null) {
                    prefs[AppSecurityPrefs.INSTALL_ID] = generated
                }
            }
            generated
        }
        emit(id)
    }.distinctUntilChanged()
}
