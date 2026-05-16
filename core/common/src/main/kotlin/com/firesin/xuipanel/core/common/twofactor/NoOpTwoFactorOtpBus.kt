package com.firesin.xuipanel.core.common.twofactor

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * No-op implementation of [TwoFactorOtpBus] for use in unit tests.
 * Always returns null from [requestOtp] — simulates user cancel / no UI present.
 */
object NoOpTwoFactorOtpBus : TwoFactorOtpBus {
    override val requests: SharedFlow<TwoFactorOtpRequest> =
        MutableSharedFlow(replay = 0)

    override suspend fun requestOtp(panelId: String, panelName: String): String? = null

    override fun completeRequest(requestId: String, otp: String?) = Unit
}
