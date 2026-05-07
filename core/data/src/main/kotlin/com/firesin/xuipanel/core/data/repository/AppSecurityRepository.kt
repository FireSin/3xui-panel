package com.firesin.xuipanel.core.data.repository

import kotlinx.coroutines.flow.Flow

interface AppSecurityRepository {
    val isLockEnabled: Flow<Boolean>
    suspend fun setLockEnabled(enabled: Boolean)

    val isLockOnPauseEnabled: Flow<Boolean>
    suspend fun setLockOnPauseEnabled(enabled: Boolean)
}
