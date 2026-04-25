package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
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
 */
@Singleton
class XuiClient @Inject constructor(
    private val clientFactory: OkHttpClientFactory,
    private val sessionCache: XuiSessionCache,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun apiFor(baseUrl: String, panelId: String, trustSelfSigned: Boolean): XuiApi {
        val client = clientFactory.getClient(panelId, trustSelfSigned)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
            .create(XuiApi::class.java)
    }

    suspend fun listInbounds(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): InboundListResponseDto {
        return withSession(panelId, baseUrl, username, password, trustSelfSigned) { api ->
            api.listInbounds()
        }
    }

    suspend fun serverStatus(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): ServerStatusResponseDto {
        return withSession(panelId, baseUrl, username, password, trustSelfSigned) { api ->
            api.serverStatus()
        }
    }

    /**
     * Executes [call], performing auto-login if needed.
     * On 401 — invalidates the session and retries once. Throws [XuiAuthException] on second failure.
     */
    private suspend fun <T> withSession(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
        call: suspend (XuiApi) -> Response<T>,
    ): T {
        val api = apiFor(baseUrl, panelId, trustSelfSigned)

        if (sessionCache.get(panelId) == null) {
            login(api, panelId, username, password)
        }

        val response = call(api)

        if (response.code() == HTTP_UNAUTHORIZED) {
            sessionCache.invalidate(panelId)
            clientFactory.getCookieJar(panelId, trustSelfSigned).clear()
            login(api, panelId, username, password)

            val retryResponse = call(api)
            if (retryResponse.code() == HTTP_UNAUTHORIZED) {
                throw XuiAuthException(panelId)
            }
            return retryResponse.body() ?: error("Empty body after re-auth for panel $panelId")
        }

        return response.body() ?: error("Empty body from panel $panelId")
    }

    private suspend fun login(api: XuiApi, panelId: String, username: String, password: String) {
        val response = api.login(username, password)
        if (!response.isSuccessful || response.body()?.success != true) {
            throw XuiAuthException(panelId)
        }
        sessionCache.put(panelId)
    }

    /**
     * Fetches server status, mapping exceptions to typed [DomainError].
     */
    suspend fun fetchServerStatus(
        panelId: String,
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): Result<ServerStatusDto, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            serverStatus(panelId, baseUrl, username, password, trustSelfSigned)
        }.fold(
            onSuccess = { response ->
                val obj = response.obj
                if (response.success && obj != null) {
                    Result.Success(obj)
                } else {
                    Result.Failure(DomainError.PanelResponse(0, response.msg.orEmpty()))
                }
            },
            onFailure = { cause ->
                when (cause) {
                    is XuiAuthException -> Result.Failure(DomainError.InvalidCredentials)
                    is SSLException -> Result.Failure(DomainError.Tls(cause.message ?: cause.javaClass.simpleName))
                    is IOException -> Result.Failure(DomainError.Network(cause))
                    else -> Result.Failure(DomainError.Unexpected(cause))
                }
            },
        )
    }

    /**
     * Attempts a one-off login against [credentials] without persisting the session.
     * Returns [Result.Success] if login succeeds, or a typed [DomainError] otherwise.
     */
    suspend fun probeLogin(credentials: ProbeCredentials): Result<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            val client = clientFactory.buildTransient(credentials.trustSelfSigned)
            val api = Retrofit.Builder()
                .baseUrl(credentials.baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
                .build()
                .create(XuiApi::class.java)

            runCatching {
                api.login(credentials.login, credentials.password)
            }.fold(
                onSuccess = { response ->
                    when {
                        response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN ->
                            Result.Failure(DomainError.InvalidCredentials)

                        response.isSuccessful && response.body()?.success == true ->
                            Result.Success(Unit)

                        response.isSuccessful ->
                            Result.Failure(DomainError.InvalidCredentials)

                        else ->
                            Result.Failure(DomainError.PanelUnreachable(response.code()))
                    }
                },
                onFailure = { cause ->
                    when (cause) {
                        is SSLException -> Result.Failure(DomainError.Tls(cause.message ?: cause.javaClass.simpleName))
                        is IOException -> Result.Failure(DomainError.Network(cause))
                        else -> Result.Failure(DomainError.Unexpected(cause))
                    }
                },
            )
        }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
