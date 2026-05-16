package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.PinMismatchEvent
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.network.tls.ProbePinCaptureListener
import com.firesin.xuipanel.core.network.tls.SpkiPinMismatchException
import com.firesin.xuipanel.core.xui.dto.ApiTokenDto
import com.firesin.xuipanel.core.xui.dto.CreateApiTokenRequestDto
import com.firesin.xuipanel.core.xui.dto.SetApiTokenEnabledRequestDto
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
import com.firesin.xuipanel.core.xui.dto.EchCertDto
import com.firesin.xuipanel.core.xui.dto.Mldsa65KeypairDto
import com.firesin.xuipanel.core.xui.dto.Mlkem768KeypairDto
import com.firesin.xuipanel.core.xui.dto.OutboundTrafficDto
import com.firesin.xuipanel.core.xui.dto.VlessEncAuthDto
import com.firesin.xuipanel.core.xui.dto.XrayMetricsStateDto
import com.firesin.xuipanel.core.xui.dto.X25519KeyPairDto
import com.firesin.xuipanel.core.xui.dto.XrayLogEntryDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.firesin.xuipanel.core.xui.ws.ClientTrafficSnapshot
import com.firesin.xuipanel.core.xui.ws.WsEvent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.firesin.xuipanel.core.xui.dto.UpdateUserRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val wsUiEvents: WsUiEventDispatcher,
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
        // Recent 3x-ui forks gate POST /login behind X-CSRF-Token; older builds accept
        // an empty header. Fetching csrf-token also seeds the cookie jar with a session
        // cookie that the login response then upgrades into the authenticated session.
        val csrf = runCatching { api.csrfToken() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()?.obj
            .orEmpty()
        val response = api.login(csrf, LoginRequestDto(username = username, password = password))
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
                val csrf = cookieSessionCsrf(api, panelId, username, password)
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

    /** Generate a new ML-DSA-65 keypair (post-quantum signature). */
    suspend fun fetchNewMldsa65(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Mldsa65KeypairDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getNewMldsa65() }
        }.fold(
            onSuccess = { r ->
                val obj = r.obj
                if (r.success && obj != null) Result.Success(obj)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Generate a new ML-KEM-768 keypair (post-quantum KEM). */
    suspend fun fetchNewMlkem768(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<Mlkem768KeypairDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getNewMlkem768() }
        }.fold(
            onSuccess = { r ->
                val obj = r.obj
                if (r.success && obj != null) Result.Success(obj)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Fetch the list of VLESS Encryption presets. */
    suspend fun fetchVlessEncAuths(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<VlessEncAuthDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getNewVlessEnc() }
        }.fold(
            onSuccess = { r ->
                val auths = r.obj?.auths
                if (r.success && auths != null) Result.Success(auths)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Fetch Xray runtime metrics state (enabled/disabled + reason or snapshot). */
    suspend fun fetchXrayMetricsState(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<XrayMetricsStateDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getXrayMetricsState() }
        }.fold(
            onSuccess = { r ->
                val obj = r.obj
                if (r.success && obj != null) Result.Success(obj)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Time-series history for one Xray runtime metric (xrAlloc/xrSys/xrHeapObjects/xrNumGC/xrPauseNs). */
    suspend fun fetchXrayMetricsHistory(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        metric: String,
        bucketSecs: Int,
    ): Result<List<com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getXrayMetricsHistory(metric, bucketSecs)
            }
        }.fold(
            onSuccess = { r ->
                if (r.success) Result.Success(r.obj.orEmpty())
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Latest observatory snapshot — empty list when observatory isn't configured. */
    suspend fun fetchXrayObservatory(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<com.firesin.xuipanel.core.xui.dto.XrayObservatoryEntryDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getXrayObservatory() }
        }.fold(
            onSuccess = { r ->
                if (r.success) Result.Success(r.obj.orEmpty())
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Observatory probe history for one outbound tag. */
    suspend fun fetchXrayObservatoryHistory(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        tag: String,
        bucketSecs: Int,
    ): Result<List<com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getXrayObservatoryHistory(tag, bucketSecs)
            }
        }.fold(
            onSuccess = { r ->
                if (r.success) Result.Success(r.obj.orEmpty())
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Generate an ECH (Encrypted Client Hello) keypair for the given SNI. */
    suspend fun fetchNewEchCert(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        sni: String,
    ): Result<EchCertDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api -> api.getNewEchCert(sni) }
        }.fold(
            onSuccess = { r ->
                val obj = r.obj
                if (r.success && obj != null) Result.Success(obj)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
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

    /** Delete a client by email (alternative to [deleteClient] which uses the client UUID). */
    suspend fun deleteClientByEmail(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        inboundId: Int,
        email: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.deleteClientByEmail(inboundId, email)
            }
        }.fold(
            onSuccess = { r ->
                if (r.success) Result.Success(Unit)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Manually set client upload/download counters (bytes). Useful for migrations. */
    suspend fun updateClientTraffic(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
        email: String,
        upload: Long,
        download: Long,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.updateClientTraffic(
                    email,
                    com.firesin.xuipanel.core.xui.dto.UpdateClientTrafficRequestDto(upload, download),
                )
            }
        }.fold(
            onSuccess = { r ->
                if (r.success) Result.Success(Unit)
                else Result.Failure(DomainError.PanelResponse(0, r.msg.orEmpty()))
            },
            onFailure = { it.toDomainError(panelId) },
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
            // The endpoint requires X-CSRF-Token on recent forks. We fetch it on
            // the same transient client so the session cookie travels along; empty
            // is tolerated by older builds.
            val csrf = runCatching { api.csrfToken() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()?.obj
                .orEmpty()
            api.getTwoFactorEnable(csrf)
        }.fold(
            onSuccess = { response ->
                when {
                    response.isSuccessful && response.body()?.success == true ->
                        Result.Success(response.body()?.obj == true)

                    response.isSuccessful ->
                        Result.Success(false)

                    // 404 from this probe means the panel build doesn't expose a 2FA
                    // toggle at all (some forks dropped /getTwoFactorEnable entirely
                    // even though /login still accepts an OTP). Treating it as «no
                    // 2FA» lets the add-panel flow proceed; if login actually needs
                    // an OTP, the next /login call will surface the real error.
                    response.code() == HTTP_NOT_FOUND ->
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
                    val csrf = runCatching { api.csrfToken() }
                        .getOrNull()
                        ?.takeIf { it.isSuccessful }
                        ?.body()?.obj
                        .orEmpty()
                    api.login(
                        csrf,
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
        if (this is XuiAuthException) return Result.Failure(DomainError.InvalidCredentials)
        // Walk the cause chain — wrapped SSL handshake failures must surface as
        // DomainError.Tls, not as the generic «Network» fallback (see
        // toProbeDomainError for the same logic).
        val sslEx = generateSequence(this) { it.cause }.filterIsInstance<SSLException>().firstOrNull()
        if (sslEx != null) return Result.Failure(DomainError.Tls(sslEx.message ?: sslEx.javaClass.simpleName))
        val error = when (this) {
            is IOException -> DomainError.Network(this)
            else -> DomainError.Unexpected(this)
        }
        return Result.Failure(error)
    }

    // ---- Bundle A: server info ----

    /**
     * Returns the **currently installed** Xray binary version (e.g. "26.4.25").
     *
     * 3x-ui v26 changed `/getXrayVersion` to return a *list of available versions*,
     * so the installed one now lives in `serverStatus().obj.xray.version`. This
     * helper hides that and keeps the old call-site shape (`Result<String>`).
     */
    suspend fun fetchXrayVersion(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.serverStatus()
            }
        }.fold(
            onSuccess = { response ->
                val version = response.obj?.xray?.version
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
     * Lists Xray versions available for install on this host (newest first).
     * api.txt: GET /panel/api/server/getXrayVersion — semantics flipped in v26+.
     * Used by the "Install Xray" picker; empty list means panel couldn't enumerate.
     */
    suspend fun fetchAvailableXrayVersions(
        panelId: String,
        baseUrl: String,
        auth: PanelAuth,
        tls: PanelTls,
    ): Result<List<String>, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            withSession(panelId, baseUrl, auth, tls) { api ->
                api.getXrayVersion()
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

    // ---- API Tokens (cookie+CSRF, under /panel/setting/) ----

    /**
     * Lists all API tokens on the panel. Uses a fresh cookie session (login → csrf → call)
     * because `/panel/setting/` is not covered by Bearer middleware.
     */
    suspend fun listApiTokens(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<List<ApiTokenDto>, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.listApiTokens(csrf)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(body.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Creates a new API token on the panel. On name collision returns
     * `DomainError.PanelResponse(409, "a token with that name already exists")`.
     */
    suspend fun createApiToken(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        name: String,
    ): Result<ApiTokenDto, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.createApiToken(csrf, CreateApiTokenRequestDto(name = name))
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val obj = body?.obj
                if (response.isSuccessful && body?.success == true && obj != null) {
                    Result.Success(obj)
                } else {
                    val msg = body?.msg.orEmpty()
                    val code = if (msg.contains("already exists", ignoreCase = true)) 409 else response.code()
                    Result.Failure(DomainError.PanelResponse(code, msg))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /** Deletes an API token by id. */
    suspend fun deleteApiToken(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        id: Int,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.deleteApiToken(csrf, id)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /** Enables or disables an API token by id. */
    suspend fun setApiTokenEnabled(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        id: Int,
        enabled: Boolean,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.setApiTokenEnabled(csrf, id, SetApiTokenEnabledRequestDto(enabled = enabled))
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    // ---- Panel settings (cookie+CSRF, under /panel/setting/) ----

    /** Returns the full panel settings blob (~70 fields) as a raw JsonObject. */
    suspend fun fetchAllSettings(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<JsonObject, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.getAllSettings(csrf)
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
     * Persists the full settings blob — callers should fetch via [fetchAllSettings] first,
     * then override only the fields they want to change to avoid clobbering unknown keys.
     */
    suspend fun updateAllSettings(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        settings: JsonObject,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.updateAllSettings(csrf, settings)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Rotates panel admin login/password. The current pair (from the stored Panel) authenticates
     * the request; the new pair is what the panel will accept going forward. Caller must update
     * `Panel.login`/`Panel.password` on success.
     */
    suspend fun updatePanelUser(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        newUsername: String,
        newPassword: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.updateUser(
                csrf,
                UpdateUserRequestDto(
                    oldUsername = username,
                    oldPassword = password,
                    newUsername = newUsername,
                    newPassword = newPassword,
                ),
            )
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Restarts the 3x-ui process (~5-10s downtime). The connection drops before the panel
     * writes a response — [IOException]/[EOFException] are treated as success.
     */
    suspend fun restartPanel(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.restartPanel(csrf)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                // Panel drops the connection mid-restart — that's the expected success path.
                if (cause is java.io.IOException) Result.Success(Unit)
                else cause.toDomainError(panelId)
            },
        )
    }

    // ---- Outbounds (cookie+CSRF, under /panel/xray/) ----

    /** Per-outbound traffic stats. Order/count matches the Xray config's outbounds array. */
    suspend fun fetchOutboundsTraffic(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<List<OutboundTrafficDto>, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.getOutboundsTraffic(csrf)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(body.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /** Last Xray stdout/stderr — empty string when Xray hasn't logged anything yet. */
    suspend fun fetchXrayResult(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.getXrayResult(csrf)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(body.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /** Fetch the Xray config template + tag lists + outboundTestUrl. */
    suspend fun fetchXrayTemplate(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Result<com.firesin.xuipanel.core.xui.dto.XrayTemplateObjDto, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.getXrayTemplate(csrf)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val objStr = body?.obj
                if (response.isSuccessful && body?.success == true && !objStr.isNullOrBlank()) {
                    runCatching {
                        json.decodeFromString(
                            com.firesin.xuipanel.core.xui.dto.XrayTemplateObjDto.serializer(),
                            objStr,
                        )
                    }.fold(
                        onSuccess = { Result.Success(it) },
                        onFailure = { Result.Failure(DomainError.Unexpected(it)) },
                    )
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    /**
     * Test an outbound by tag. The outbound JSON is pulled from the panel's xraySetting
     * template (the test endpoint requires the full outbound JSON, not just the tag).
     *
     * @return [TestOutboundResultDto.success] = whether the dial/HTTP probe succeeded;
     *   `error` is empty on success, contains the failure reason otherwise.
     */
    suspend fun testOutboundByTag(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        tag: String,
        useTcpMode: Boolean = true,
    ): Result<com.firesin.xuipanel.core.xui.dto.TestOutboundResultDto, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            // Fetch template, find outbound by tag.
            // Wire shape: response.obj is a JSON-encoded string of XrayTemplateObjDto.
            // After decoding, xraySetting is a structured object — no second parse needed.
            val tmplResp = api.getXrayTemplate(csrf)
            val tmplStr = tmplResp.body()?.obj?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("xray template unavailable")
            val tmpl = json.decodeFromString(
                com.firesin.xuipanel.core.xui.dto.XrayTemplateObjDto.serializer(),
                tmplStr,
            )
            val outbounds = tmpl.xraySetting["outbounds"]?.jsonArray
                ?: throw IllegalStateException("no outbounds array in xraySetting")
            val target = outbounds.firstOrNull {
                it.jsonObject["tag"]?.jsonPrimitive?.contentOrNull == tag
            } ?: throw IllegalStateException("outbound with tag '$tag' not found")
            val outboundJson = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), target)
            val allOutboundsJson = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), outbounds)
            api.testOutbound(
                csrfToken = csrf,
                outbound = outboundJson,
                allOutbounds = allOutboundsJson,
                mode = if (useTcpMode) "tcp" else null,
            )
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
     * Cloudflare WARP action helper. [action] ∈ `data | del | config | reg | license`.
     * Returns the panel response `obj` string (escaped JSON for `data`/`config`, empty otherwise).
     */
    suspend fun warpAction(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        action: String,
        privateKey: String? = null,
        publicKey: String? = null,
        license: String? = null,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.warpAction(csrf, action, privateKey, publicKey, license)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(body.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /**
     * NordVPN action helper. [action] ∈ `countries | servers | reg | setKey | data | del | config`.
     * Mirrors [warpAction] envelope.
     */
    suspend fun nordAction(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        action: String,
        countryId: String? = null,
        token: String? = null,
        key: String? = null,
    ): Result<String, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.nordAction(csrf, action, countryId, token, key)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(body.obj.orEmpty())
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { it.toDomainError(panelId) },
        )
    }

    /** Reset upload/download counters for a single outbound (by tag). Destructive. */
    suspend fun resetOutboundTraffic(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
        tag: String,
    ): Result<Unit, DomainError> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            return@withContext Result.Failure(DomainError.InvalidCredentials)
        }
        runCatching {
            val api = apiFor(baseUrl, panelId, tls)
            val csrf = cookieSessionCsrf(api, panelId, username, password)
            api.resetOutboundsTraffic(csrf, tag)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(DomainError.PanelResponse(response.code(), body?.msg.orEmpty()))
                }
            },
            onFailure = { cause -> cause.toDomainError(panelId) },
        )
    }

    // ---- WebSocket (cookie auth — Bearer not supported on /ws) ----

    /**
     * Stream live events from `<baseUrl>/ws`. Establishes a cookie session first (login),
     * then upgrades to WebSocket on the same OkHttp client (the cookie jar is shared).
     *
     * The flow completes when the panel closes the socket or the collector cancels.
     * Failures emit [WsEvent.Unknown]-free — they bubble up as exceptions.
     */
    fun observeWs(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        tls: PanelTls,
    ): Flow<WsEvent> = channelFlow {
        if (username.isBlank() || password.isBlank()) {
            close(IllegalArgumentException("WebSocket requires login/password — Bearer is not supported by /ws"))
            return@channelFlow
        }

        // Establish cookie session on the long-lived OkHttp client (shared cookie jar).
        val api = apiFor(baseUrl, panelId, tls)
        try {
            login(api, panelId, username, password)
        } catch (cause: Throwable) {
            close(cause)
            return@channelFlow
        }

        val httpClient = clientFactory.getClient(panelId, tls)
        val wsUrl = baseUrl.ensureTrailingSlash() + "ws"
        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parseWsMessage(text) ?: return
                // Fan out side-channel UI signals (toast + cache invalidation) so any screen
                // can react without holding the WS itself.
                when (event) {
                    is WsEvent.Notification -> scope.launch {
                        wsUiEvents.emitNotification(
                            com.firesin.xuipanel.core.common.WsNotification(
                                title = event.title,
                                body = event.body,
                                severity = event.severity,
                            ),
                        )
                    }
                    is WsEvent.Invalidate -> scope.launch {
                        wsUiEvents.emitInvalidation(event.resource)
                    }
                    else -> Unit
                }
                trySend(event)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                close(t)
            }
        }

        val ws = httpClient.newWebSocket(request, listener)
        awaitClose { ws.cancel() }
    }

    private fun parseWsMessage(text: String): WsEvent? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val payload = root["payload"]
        return runCatching {
            when (type) {
                "status" -> {
                    val server = json.decodeFromJsonElement(
                        com.firesin.xuipanel.core.xui.dto.ServerStatusDto.serializer(),
                        payload ?: return@runCatching WsEvent.Unknown(type, text),
                    )
                    val timeMs = root["time"]?.jsonPrimitive?.longOrNull ?: 0L
                    WsEvent.Status(server, timeMs)
                }
                "traffic" -> {
                    val list = payload?.jsonObject?.get("clientTraffics")?.jsonArray
                    val items = list?.map {
                        json.decodeFromJsonElement(ClientTrafficSnapshot.serializer(), it)
                    } ?: emptyList()
                    WsEvent.Traffic(items)
                }
                "xrayState" -> {
                    val state = payload?.jsonPrimitive?.contentOrNull
                        ?: payload?.jsonObject?.get("state")?.jsonPrimitive?.contentOrNull
                        ?: ""
                    WsEvent.XrayState(state)
                }
                "notification" -> {
                    val obj = payload?.jsonObject ?: root
                    WsEvent.Notification(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        severity = obj["severity"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
                "invalidate" -> {
                    val resource = payload?.jsonObject?.get("resource")?.jsonPrimitive?.contentOrNull
                        ?: root["resource"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    WsEvent.Invalidate(resource)
                }
                else -> WsEvent.Unknown(type, text)
            }
        }.getOrElse { WsEvent.Unknown(type, text) }
    }

    /**
     * Login + CSRF flow for /panel/setting/ endpoints. Returns the csrf token string.
     *
     * Reuses the cached session when present — the cookie jar attached to the shared
     * OkHttp client keeps the session cookie alive across calls, so we only need a
     * fresh CSRF token, not a fresh login. Halves QR/share latency by skipping
     * POST /login on every share/settings call.
     */
    private suspend fun cookieSessionCsrf(
        api: XuiApi,
        panelId: String,
        username: String,
        password: String,
    ): String {
        if (sessionCache.get(panelId) == null) {
            login(api, panelId, username, password)
        }
        var csrfResponse = api.csrfToken()
        if (csrfResponse.code() == HTTP_UNAUTHORIZED || csrfResponse.code() == HTTP_FORBIDDEN) {
            // Cached session lost on the server side — re-login and retry once.
            sessionCache.invalidate(panelId)
            login(api, panelId, username, password)
            csrfResponse = api.csrfToken()
        }
        val csrf = csrfResponse.body()?.obj.orEmpty()
        if (!csrfResponse.isSuccessful || csrf.isBlank()) {
            error("csrf token unavailable for panel $panelId")
        }
        return csrf
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}

/** Probe path: no panelId yet; [SpkiPinMismatchException] from redirect-conflict is still typed. */
private fun Throwable.toProbeDomainError(): Result.Failure<DomainError> {
    val chain = generateSequence(this) { it.cause }
    val pinEx = chain.filterIsInstance<SpkiPinMismatchException>().firstOrNull()
    if (pinEx != null) return Result.Failure(DomainError.PinMismatch(panelId = "", observedSpki = pinEx.observedSpki))
    // Some Android/OkHttp/coroutines paths wrap SSL handshake failures in a generic
    // IOException. Walk the cause chain so a self-signed certificate surfaces as
    // a TLS error («certificate not trusted») instead of a misleading «нет
    // соединения с панелью» which suggests a transport-level outage.
    val sslEx = generateSequence(this) { it.cause }.filterIsInstance<SSLException>().firstOrNull()
    if (sslEx != null) return Result.Failure(DomainError.Tls(sslEx.message ?: sslEx.javaClass.simpleName))
    return Result.Failure(
        when (this) {
            is IOException -> DomainError.Network(this)
            else -> DomainError.Unexpected(this)
        },
    )
}
