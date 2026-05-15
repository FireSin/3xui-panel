package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.PinMismatchEvent
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.network.tls.ProbePinCaptureListener
import com.firesin.xuipanel.core.network.tls.SpkiPinMismatchException
import com.firesin.xuipanel.core.xui.dto.AddCustomGeoRequestDto
import com.firesin.xuipanel.core.xui.dto.CustomGeoAliasesResponseDto
import com.firesin.xuipanel.core.xui.dto.AddInboundRequestDto
import com.firesin.xuipanel.core.xui.dto.AddNodeRequestDto
import com.firesin.xuipanel.core.xui.dto.CopyClientsRequestDto
import com.firesin.xuipanel.core.xui.dto.ClientTrafficDto
import com.firesin.xuipanel.core.xui.dto.CustomGeoResourceDto
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.ClientSettingsBodyDto
import com.firesin.xuipanel.core.xui.dto.ClientsJson
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.LoginRequestDto
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.dto.NodeStatusProbeDto
import com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoObj
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.firesin.xuipanel.core.xui.dto.SetEnableRequestDto
import com.firesin.xuipanel.core.xui.dto.SetNodeEnableRequestDto
import com.firesin.xuipanel.core.xui.dto.X25519KeyPairDto
import com.firesin.xuipanel.core.xui.dto.XrayLogEntryDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import java.io.EOFException
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

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

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
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
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
        val response = api.login(LoginRequestDto(username = username, password = password))
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
     * Map of client email → last-seen unix timestamp (seconds). Empty map if the endpoint
     * returns nothing. Used by the clients list to show a "был в сети" hint on offline rows.
     *
     * Server stores the timestamp as `time.Now().UnixMilli()` — convert ms → sec here so
     * downstream code (and UI) can work in seconds uniformly. Zero stays zero (never seen).
     */
    suspend fun fetchLastOnline(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Map<String, Long>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.lastOnline()
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    val seconds = response.obj.orEmpty().mapValues { (_, ms) ->
                        if (ms > 0L) ms / 1000L else 0L
                    }
                    Result.Success(seconds)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Deletes every depleted/expired client in [inboundId]. Pass -1 to sweep across every inbound.
     */
    suspend fun deleteDepletedClients(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.delDepletedClients(inboundId)
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
     * Resets upload + download counters for every client in [inboundId]. Destructive — accounting
     * history is lost. Used by the "Сбросить трафик клиентов" entry in ClientsList overflow.
     */
    suspend fun resetAllClientTraffics(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.resetAllClientTraffics(inboundId)
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
     * Returns server-rendered share URLs for one client on one inbound.
     * Empty list for protocols that have no URL form (socks/http/mixed/wireguard/dokodemo/tunnel).
     * Falls back to empty on success=false rather than failing — server might be older.
     */
    suspend fun fetchClientLinks(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        email: String,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getClientLinks(inboundId, email)
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
     * Panel-level settings — used by the share screen to assemble the subscription
     * and Clash URLs (base URI + [ClientConfig.subId]).
     *
     * The `panel/setting/` path family isn't covered by the Bearer-API middleware,
     * so this call always uses a cookie session (login → CSRF-token → POST). Callers
     * that only have a Bearer token (no login/password stored) must pass blank
     * credentials — the call short-circuits and returns [DomainError.InvalidCredentials]
     * so the share screen can degrade gracefully.
     */
    suspend fun fetchPanelSettings(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<com.firesin.xuipanel.core.xui.dto.PanelSettingsDto, DomainError> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.Failure(DomainError.InvalidCredentials)
            }
            runCatching {
                val api = apiFor(baseUrl, panelId, tls)
                // Force a fresh login so the cookie jar holds the session under which
                // the upcoming CSRF token is valid. Skipping this and reusing a
                // possibly-stale cached session may yield 403s.
                login(api, panelId, username, password)
                val csrfResponse = api.csrfToken()
                val csrf = csrfResponse.body()?.obj.orEmpty()
                if (!csrfResponse.isSuccessful || csrf.isBlank()) {
                    error("csrf token unavailable for panel $panelId")
                }
                api.panelSettings(csrf)
            }.fold(
                onSuccess = { response ->
                    val body = response.body()
                    val obj = body?.obj
                    if (response.isSuccessful && body?.success == true && obj != null) {
                        Result.Success(obj)
                    } else {
                        Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                    }
                },
                onFailure = { cause -> cause.toDomainError(panelId) },
            )
        }

    /**
     * Returns every protocol URL for clients matching [subId]. Empty list when nothing matches.
     */
    suspend fun fetchSubLinks(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        subId: String,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getSubLinks(subId)
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
     * Toggles an inbound's enabled state.
     *
     * 3x-ui's setEnable handler writes the JSON response and then runs a websocket
     * broadcast — on some setups OkHttp surfaces this as [EOFException] before the
     * body is fully read, even though the toggle was persisted server-side. We
     * verify by re-fetching inbounds and treat the call as a success when the new
     * state matches what was requested.
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
                api.setInboundEnable(id, SetEnableRequestDto(enable = enabled))
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                val rootEof = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException }
                if (rootEof) {
                    // Server persists state before writing the JSON response; EOF
                    // surfaces because the post-response websocket broadcast closes
                    // the connection before OkHttp reads the body. The toggle is
                    // already applied — let OkHttp evict the broken connection
                    // from its pool on the next call.
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
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
     * Creates a new inbound. Caller serialises the three JSON blobs in [body]
     * (settings/streamSettings/sniffing) — see `InboundEncoder` in :feature:inbounds.
     */
    suspend fun addInbound(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        body: AddInboundRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.addInbound(body)
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

    /** Replaces an existing inbound. Same body shape as [addInbound]. */
    suspend fun updateInbound(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
        body: AddInboundRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateInbound(id, body)
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

    /** Asks the panel to mint a fresh UUID v4 (used as the client id during inbound creation). */
    suspend fun fetchNewUuid(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getNewUuid()
            }
        }.fold(
            onSuccess = { response ->
                val uuid = response.obj
                if (response.success && !uuid.isNullOrBlank()) {
                    Result.Success(uuid)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /** Asks the panel for a fresh X25519 keypair (Reality private + public). */
    suspend fun fetchNewX25519(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<X25519KeyPairDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getNewX25519Cert()
            }
        }.fold(
            onSuccess = { response ->
                val pair = response.obj
                if (response.success && pair != null) {
                    Result.Success(pair)
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
     * Returns the last [count] Xray access-log entries, formatted into human-readable lines.
     * 3x-ui returns structured records ([XrayLogEntryDto]); this method flattens them.
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
                    Result.Success(response.obj.orEmpty().map { it.toLogLine() })
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    private fun XrayLogEntryDto.toLogLine(): String {
        val ts = (dateTime ?: "").replace('T', ' ').take(19)
        val tag = when (event) {
            0 -> "DIRECT"
            1 -> "BLOCKED"
            2 -> "PROXIED"
            else -> "?"
        }
        val from = fromAddress.orEmpty()
        val to = toAddress.orEmpty()
        val ib = inbound.orEmpty()
        val ob = outbound.orEmpty()
        val em = email.orEmpty().let { if (it.isBlank()) "" else " $it" }
        return "$ts $tag $from -> $to [$ib -> $ob]$em".trim()
    }

    /**
     * Time-series for one server metric.
     *
     * @param metric one of: cpu, mem, swap, netIn, netOut, tcpCount, udpCount, load1, online
     * @param bucket aggregation bucket in seconds (allowed: 2, 30, 60, 120, 180, 300)
     */
    suspend fun fetchServerHistory(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        metric: String,
        bucket: Int,
    ): Result<List<ServerHistoryPointDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.serverHistory(metric, bucket)
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
                api.addClient(ClientSettingsBodyDto(inboundId = inboundId, settings = settings))
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
                api.updateClient(
                    clientKey,
                    ClientSettingsBodyDto(inboundId = inboundId, settings = settings),
                )
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

    /**
     * Copies clients from [sourceInboundId] into [targetInboundId].
     * [clientEmails] empty = copy all; [flow] null/blank = preserve original flow.
     * api.txt lines 164–172: POST /panel/api/inbounds/:id/copyClients.
     */
    suspend fun copyClients(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        targetInboundId: Int,
        sourceInboundId: Int,
        clientEmails: List<String>,
        flow: String?,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        val body = CopyClientsRequestDto(
            targetInboundId = targetInboundId,
            sourceInboundId = sourceInboundId,
            clientEmails = clientEmails,
            flow = flow.orEmpty(),
        )
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.copyClients(targetInboundId, body)
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
     * Bulk-imports inbounds from a raw JSON string (as exported by the 3x-ui panel).
     * The body is sent as a form-encoded field "data" — api.txt line 220–225.
     *
     * [jsonText] is passed verbatim; no local validation is performed.
     * Assumption: api.txt says «body uses form encoding with a single "data" field»;
     * the value is the JSON-encoded inbound payload. Zapped as-is.
     */
    suspend fun importInbounds(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        jsonText: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        val requestBody = jsonText.toRequestBody("text/plain".toMediaTypeOrNull())
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.importInbounds(requestBody)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                // Long-running import; panel may restart Xray — apply EOF tolerance
                val isEofOrIo = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException || it is IOException }
                if (isEofOrIo) {
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
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
     * Recent IPs observed for [email]. Server returns the literal `"No IP Record"` when empty;
     * this method normalises that to an empty list.
     */
    suspend fun fetchClientIps(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        email: String,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.clientIps(email)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    val list = (response.obj as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        .orEmpty()
                    Result.Success(list)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    suspend fun clearClientIps(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        email: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.clearClientIps(email)
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
     * Traffic counters for a single client, looked up by email.
     * Endpoint: GET /panel/api/inbounds/getClientTraffics/{email}
     * Returns [DomainError.PanelResponse] when [success] is false (e.g. email not found).
     */
    suspend fun fetchClientTrafficsByEmail(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        email: String,
    ): Result<ClientTrafficDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getClientTrafficsByEmail(email)
            }
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

    /**
     * Traffic counters for a single client, looked up by its numeric client-stats row id.
     * Endpoint: GET /panel/api/inbounds/getClientTrafficsById/{id}
     * The [id] is the string representation of the integer row id from [ClientTrafficDto.id].
     */
    suspend fun fetchClientTrafficsById(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: String,
    ): Result<ClientTrafficDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getClientTrafficsById(id)
            }
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

    suspend fun fetchCustomGeoList(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<CustomGeoResourceDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.listCustomGeo()
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

    suspend fun fetchCustomGeoAliases(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getCustomGeoAliases()
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

    suspend fun addCustomGeo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        body: AddCustomGeoRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.addCustomGeo(body)
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

    suspend fun updateCustomGeo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
        body: AddCustomGeoRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateCustomGeo(id, body)
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

    suspend fun deleteCustomGeo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.deleteCustomGeo(id)
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

    suspend fun downloadCustomGeo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.downloadCustomGeo(id)
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

    suspend fun updateAllCustomGeo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateAllCustomGeo()
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

    // ---- Nodes ----

    suspend fun fetchNodes(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<NodeDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.listNodes()
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

    suspend fun fetchNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<NodeDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getNode(id)
            }
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

    suspend fun addNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        body: AddNodeRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.addNode(body)
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

    suspend fun updateNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
        body: AddNodeRequestDto,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateNode(id, body)
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

    suspend fun deleteNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.deleteNode(id)
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
     * Toggles a node's enabled state. Mirrors [setInboundEnabled] — applies the same
     * EOFException-tolerance pattern for setEnable endpoints.
     */
    suspend fun setNodeEnabled(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
        enable: Boolean,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.setNodeEnable(id, SetNodeEnableRequestDto(enable = enable))
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                val rootEof = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException }
                if (rootEof) {
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
        )
    }

    /**
     * Tests connectivity to a node configuration without saving it.
     * Returns [NodeStatusProbeDto] on success if the server provides it,
     * or [Unit] if the body is absent (older server).
     */
    suspend fun testNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        body: AddNodeRequestDto,
    ): Result<NodeStatusProbeDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.testNode(body)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(response.obj ?: NodeStatusProbeDto())
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Time-series for one node metric.
     *
     * @param nodeId    target node id
     * @param metric    one of: cpu, mem, netIn, netOut, latency, online
     * @param bucket    aggregation bucket in seconds (allowed: 2, 30, 60, 120, 180, 300)
     */
    suspend fun fetchNodeHistory(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        nodeId: Int,
        metric: String,
        bucket: Int,
    ): Result<List<ServerHistoryPointDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getNodeHistory(nodeId, metric, bucket)
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

    // ---- Power-user system actions ----

    /**
     * Resets upload + download counters on every inbound. Destructive — all accounting
     * history is lost. The endpoint takes no body and no path params.
     */
    suspend fun resetAllTraffics(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.resetAllTraffics()
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
     * Tells the panel to self-update to the latest version and restart.
     *
     * The server-restart typically causes OkHttp to surface an [EOFException] or
     * [IOException] before the JSON response body arrives — this mirrors the pattern
     * used by [setInboundEnabled]. We treat both as success because the update
     * was already triggered server-side.
     */
    suspend fun updatePanel(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updatePanel()
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                val isEofOrIo = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException || it is IOException }
                if (isEofOrIo) {
                    // Panel restarted before writing the response — treat as success.
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
        )
    }

    /**
     * Downloads and installs the specified Xray [version] tag (e.g. "v25.5.16" or "latest").
     * Long-running — applies the same EOF/IO tolerance as [updatePanel].
     */
    suspend fun installXray(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        version: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.installXray(version)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                val isEofOrIo = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException || it is IOException }
                if (isEofOrIo) {
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
        )
    }

    /**
     * Sends a fresh DB backup to every Telegram admin recipient configured on the panel.
     */
    suspend fun backupToTgBot(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.backupToTgBot()
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

    /** Triggers a live probe of an existing node, updating its cached status server-side. */
    suspend fun probeNode(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.probeNode(id)
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
     * Checks whether the panel at [baseUrl] has TOTP 2FA enabled.
     * Uses a one-shot transient client (no auth, no session).
     * Returns [Result.Success]`(true)` when 2FA is enabled, `(false)` when disabled,
     * or a [DomainError] on network/TLS failure.
     */
    suspend fun probeTwoFactorEnabled(
        baseUrl: String,
        tlsMode: com.firesin.xuipanel.core.common.TlsMode,
        pinnedSpkiSha256: String?,
    ): Result<Boolean, DomainError> = withContext(Dispatchers.IO) {
        val tls = PanelTls(mode = tlsMode, pinnedSpkiSha256 = pinnedSpkiSha256)
        val (client, _) = clientFactory.buildTransient(tls)
        val api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .client(client)
            .build()
            .create(XuiApi::class.java)
        runCatching {
            api.getTwoFactorEnable()
        }.fold(
            onSuccess = { response ->
                when {
                    response.isSuccessful && response.body()?.success == true ->
                        Result.Success(response.body()?.obj == true)

                    response.isSuccessful ->
                        Result.Success(false)

                    else ->
                        Result.Failure(DomainError.PanelUnreachable(response.code()))
                }
            },
            onFailure = { cause -> cause.toProbeDomainError() },
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
                .baseUrl(credentials.baseUrl.ensureTrailingSlash())
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
                    api.login(
                        LoginRequestDto(
                            username = credentials.login,
                            password = credentials.password,
                            twoFactorCode = credentials.twoFactorCode?.takeIf { it.isNotBlank() },
                        ),
                    )
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

    // ---- Bundle A: server info ----

    /**
     * Returns the currently-installed Xray binary version string (e.g. "v25.5.16").
     * api.txt line 312–313: GET /panel/api/server/getXrayVersion.
     */
    suspend fun fetchXrayVersion(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getXrayVersion()
            }
        }.fold(
            onSuccess = { response ->
                val version = response.obj
                if (response.success && !version.isNullOrBlank()) {
                    Result.Success(version)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Checks if a newer 3x-ui panel release is available.
     * api.txt line 316–317: GET /panel/api/server/getPanelUpdateInfo.
     */
    suspend fun fetchPanelUpdateInfo(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<PanelUpdateInfoObj, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getPanelUpdateInfo()
            }
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

    // ---- Bundle B: backup / config ----

    /**
     * Streams the panel SQLite backup into [sink], which is called on [Dispatchers.IO].
     * The sink receives the raw [java.io.InputStream] — callers must NOT close it; this
     * method closes both the stream and the [ResponseBody] when done.
     * api.txt line 324–325: GET /panel/api/server/getDb.
     */
    suspend fun fetchDbInto(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        sink: suspend (java.io.InputStream) -> Unit,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getDb()
            }
        }.fold(
            onSuccess = { body ->
                try {
                    sink(body.byteStream())
                    Result.Success(Unit)
                } finally {
                    body.close()
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Uploads a SQLite backup file to restore the panel DB.
     * api.txt line 397–399: POST /panel/api/server/importDB, multipart field "db".
     * Panel restarts on success — applies EOF/IO tolerance pattern.
     *
     * @param fileBytes raw bytes of the DB file
     * @param fileName  display name for the multipart part (e.g. "x-ui.db")
     */
    suspend fun importDb(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        fileBytes: ByteArray,
        fileName: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        val requestBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())
        val part = MultipartBody.Part.createFormData("db", fileName, requestBody)
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.importDb(part)
            }
        }.fold(
            onSuccess = { response ->
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                val isEofOrIo = generateSequence(cause as Throwable?) { it.cause }
                    .any { it is EOFException || it is IOException }
                if (isEofOrIo) {
                    // Panel restarts after DB restore — treat connection drop as success.
                    return@fold Result.Success(Unit)
                }
                cause.toDomainError(panelId)
            },
        )
    }

    /**
     * Refreshes ALL built-in GeoIP/GeoSite data files.
     * api.txt line 367–368: POST /panel/api/server/updateGeofile.
     */
    suspend fun updateBuiltinGeofile(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateGeofile()
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
     * Returns the raw Xray config JSON currently running on this host, pretty-printed.
     * api.txt line 319–320: GET /panel/api/server/getConfigJson.
     */
    suspend fun fetchConfigJson(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getConfigJson()
            }
        }.fold(
            onSuccess = { response ->
                val element = response.obj
                if (response.success && element != null) {
                    val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }
                    Result.Success(
                        prettyJson.encodeToString(
                            kotlinx.serialization.json.JsonElement.serializer(),
                            element,
                        ),
                    )
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
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
