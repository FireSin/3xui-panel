package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.PinMismatchEvent
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.network.tls.ProbePinCaptureListener
import com.firesin.xuipanel.core.network.tls.SpkiPinMismatchException
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.ClientsJson
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * High-level facade for talking to a single 3x-ui panel.
 *
 * Handles automatic login and a single re-auth on 401.
 * Callers must provide credentials on every call (looked up from the repo).
 *
 * When [PanelAuth.Bearer] is used, the token is sent as `Authorization: Bearer <token>` header.
 * No login() call is made; 401 is a terminal error.
 */
@Singleton
class XuiClient @Inject constructor(
    private val clientFactory: OkHttpClientFactory,
    private val sessionCache: XuiSessionCache,
    private val pinMismatchEvents: PinMismatchEventDispatcher,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun apiFor(baseUrl: String, panelId: String, tls: PanelTls, bearer: String? = null): XuiApi {
        val baseClient = clientFactory.getClient(panelId, tls)
        val client = if (bearer != null) {
            baseClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $bearer")
                            .build(),
                    )
                }
                .build()
        } else {
            baseClient
        }
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
            .create(XuiApi::class.java)
    }

    suspend fun listInbounds(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): InboundListResponseDto {
        return withSession(panelId, baseUrl, auth, tls) { api ->
            api.listInbounds()
        }
    }

    suspend fun serverStatus(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): ServerStatusResponseDto {
        return withSession(panelId, baseUrl, auth, tls) { api ->
            api.serverStatus()
        }
    }

    /**
     * Executes [call], performing auto-login if needed (Login mode only).
     * Bearer mode: sends the token header, on 401 throws [XuiAuthException] immediately.
     * Login mode: on 401 — invalidates the session and retries once.
     */
    private suspend fun <T> withSession(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        call: suspend (XuiApi) -> Response<T>,
    ): T {
        return when (auth) {
            is PanelAuth.Bearer -> {
                val api = apiFor(baseUrl, panelId, tls, bearer = auth.token)
                val response = call(api)
                if (response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN) {
                    throw XuiAuthException(panelId)
                }
                response.body() ?: error("Empty body from panel $panelId")
            }
            is PanelAuth.Login -> {
                val api = apiFor(baseUrl, panelId, tls)
                if (sessionCache.get(panelId) == null) {
                    login(api, panelId, auth.username, auth.password)
                }

                val response = call(api)

                if (response.code() == HTTP_UNAUTHORIZED) {
                    sessionCache.invalidate(panelId)
                    clientFactory.getCookieJar(panelId, tls).clear()
                    login(api, panelId, auth.username, auth.password)

                    val retryResponse = call(api)
                    if (retryResponse.code() == HTTP_UNAUTHORIZED) {
                        throw XuiAuthException(panelId)
                    }
                    return retryResponse.body() ?: error("Empty body after re-auth for panel $panelId")
                }

                response.body() ?: error("Empty body from panel $panelId")
            }
        }
    }

    private suspend fun login(api: XuiApi, panelId: String, username: String, password: String) {
        val response = api.login(username, password)
        if (!response.isSuccessful || response.body()?.success != true) {
            throw XuiAuthException(panelId)
        }
        sessionCache.put(panelId)
    }

    /**
     * Fetches the list of inbounds, mapping exceptions to typed [DomainError].
     */
    suspend fun fetchInbounds(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<InboundDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            listInbounds(panelId, baseUrl, auth, tls)
        }.fold(
            onSuccess = { response ->
                val list = response.obj
                if (response.success && list != null) {
                    Result.Success(list)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Fetches the set of currently-online client emails.
     * Returns an empty set on an empty or absent [OnlinesResponseDto.obj].
     */
    suspend fun fetchOnlines(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Set<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.onlines()
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success((response.obj ?: emptyList()).toSet())
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Toggles an inbound's enabled state.
     */
    suspend fun setInboundEnabled(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        enabled: Boolean,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.setInboundEnable(id, enabled)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Deletes an inbound by id.
     */
    suspend fun deleteInbound(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.deleteInbound(id)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Restarts the Xray service on the panel.
     */
    suspend fun restartXray(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.restartXrayService()
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Stops the Xray service on the panel.
     */
    suspend fun stopXray(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.stopXrayService()
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Returns the last [count] lines of the panel log.
     */
    suspend fun fetchPanelLogs(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        count: Int,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.panelLogs(count)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(response.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Returns the last [count] lines of the Xray log.
     */
    suspend fun fetchXrayLogs(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        count: Int,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.xrayLogs(count)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(response.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Fetches server status, mapping exceptions to typed [DomainError].
     */
    suspend fun fetchServerStatus(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<ServerStatusDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            serverStatus(panelId, baseUrl, auth, tls)
        }.fold(
            onSuccess = { response ->
                val obj = response.obj
                if (response.success && obj != null) {
                    Result.Success(obj)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    suspend fun addClient(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        client: ClientConfig,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        val settings = ClientsJson.encodeSettingsBody(client)
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.addClient(inboundId, settings)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    suspend fun updateClient(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        clientKey: String,
        client: ClientConfig,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        val settings = ClientsJson.encodeSettingsBody(client)
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateClient(clientKey, inboundId, settings)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    suspend fun deleteClient(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        clientKey: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.deleteClient(inboundId, clientKey)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    suspend fun resetClientTraffic(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        email: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.resetClientTraffic(inboundId, email)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Attempts a one-off login against [credentials] without persisting the session.
     * Returns [Result.Success] with [ProbeOutcome] (containing captured SPKI) if login succeeds,
     * or a typed [DomainError] otherwise.
     *
     * When [ProbeCredentials.apiToken] is non-blank, verifies the token by calling serverStatus
     * with a Bearer header instead of performing a form login.
     */
    suspend fun probeLogin(credentials: ProbeCredentials): Result<ProbeOutcome, DomainError> =
        withContext(Dispatchers.IO) {
            val tls = PanelTls(
                mode = credentials.tlsMode,
                pinnedSpkiSha256 = credentials.pinnedSpkiSha256,
            )
            val (client, probeCaptureListener) = clientFactory.buildTransient(tls)

            val apiBase = Retrofit.Builder()
                .baseUrl(credentials.baseUrl)
                .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))

            if (!credentials.apiToken.isNullOrBlank()) {
                val bearerClient = client.newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer ${credentials.apiToken}")
                                .build(),
                        )
                    }
                    .build()
                val api = apiBase.client(bearerClient).build().create(XuiApi::class.java)
                runCatching {
                    api.serverStatus()
                }.fold(
                    onSuccess = { response ->
                        when {
                            response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN ->
                                Result.Failure(DomainError.InvalidCredentials)

                            response.isSuccessful ->
                                Result.Success(ProbeOutcome(capturedSpkiBase64 = probeCaptureListener?.getOrNull()))

                            else ->
                                Result.Failure(DomainError.PanelUnreachable(response.code()))
                        }
                    },
                    onFailure = { cause -> cause.toProbeDomainError() },
                )
            } else {
                val api = apiBase.client(client).build().create(XuiApi::class.java)
                runCatching {
                    api.login(credentials.login, credentials.password)
                }.fold(
                    onSuccess = { response ->
                        when {
                            response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN ->
                                Result.Failure(DomainError.InvalidCredentials)

                            response.isSuccessful && response.body()?.success == true ->
                                Result.Success(ProbeOutcome(capturedSpkiBase64 = probeCaptureListener?.getOrNull()))

                            response.isSuccessful ->
                                Result.Failure(DomainError.InvalidCredentials)

                            else ->
                                Result.Failure(DomainError.PanelUnreachable(response.code()))
                        }
                    },
                    onFailure = { cause -> cause.toProbeDomainError() },
                )
            }
        }

    /**
     * Maps a mid-session throwable to a [Result.Failure]. On [DomainError.PinMismatch], also
     * fire-and-forgets a global [PinMismatchEvent] so [MainActivity] can show a kill-switch dialog.
     */
    private fun Throwable.toDomainError(panelId: String): Result.Failure<DomainError> {
        val pinEx = generateSequence(this) { it.cause }
            .filterIsInstance<SpkiPinMismatchException>()
            .firstOrNull()
        if (pinEx != null) {
            val error = DomainError.PinMismatch(panelId, pinEx.observedSpki)
            scope.launch {
                pinMismatchEvents.emit(PinMismatchEvent(panelId = error.panelId, observedSpki = error.observedSpki))
            }
            return Result.Failure(error)
        }
        val error = when {
            this is XuiAuthException -> DomainError.InvalidCredentials
            this is SSLException -> DomainError.Tls(message ?: javaClass.simpleName)
            this is IOException -> DomainError.Network(this)
            else -> DomainError.Unexpected(this)
        }
        return Result.Failure(error)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

/** Probe path: no panelId yet; [SpkiPinMismatchException] from redirect-conflict is still typed. */
private fun Throwable.toProbeDomainError(): Result.Failure<DomainError> {
    val pinEx = generateSequence(this) { it.cause }
        .filterIsInstance<SpkiPinMismatchException>()
        .firstOrNull()
    if (pinEx != null) return Result.Failure(DomainError.PinMismatch(panelId = "", observedSpki = pinEx.observedSpki))
    return Result.Failure(
        when {
            this is SSLException -> DomainError.Tls(message ?: javaClass.simpleName)
            this is IOException -> DomainError.Network(this)
            else -> DomainError.Unexpected(this)
        },
    )
}
