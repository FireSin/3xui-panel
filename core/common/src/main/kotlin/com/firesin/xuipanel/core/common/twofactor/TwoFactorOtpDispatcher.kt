package com.firesin.xuipanel.core.common.twofactor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val OTP_TIMEOUT_MS = 90_000L

@Singleton
class TwoFactorOtpDispatcher @Inject constructor() : TwoFactorOtpBus {

    private val _requests = MutableSharedFlow<TwoFactorOtpRequest>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    override val requests: SharedFlow<TwoFactorOtpRequest> = _requests.asSharedFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    override suspend fun requestOtp(panelId: String, panelName: String): String? {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pending[requestId] = deferred
        _requests.emit(TwoFactorOtpRequest(requestId = requestId, panelId = panelId, panelName = panelName))
        return withTimeoutOrNull(OTP_TIMEOUT_MS) { deferred.await() }
            .also { pending.remove(requestId) }
    }

    override fun completeRequest(requestId: String, otp: String?) {
        pending.remove(requestId)?.complete(otp)
    }
}
