package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppSecurityRepository {
    val isLockEnabled: Flow<Boolean>
    suspend fun setLockEnabled(enabled: Boolean)

    val isLockOnPauseEnabled: Flow<Boolean>
    suspend fun setLockOnPauseEnabled(enabled: Boolean)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** Stable anonymous install id, lazily generated on first observation. */
    val installId: Flow<String>
}
