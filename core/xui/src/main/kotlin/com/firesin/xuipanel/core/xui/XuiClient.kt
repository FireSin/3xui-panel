package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.xui.dto.InboundListResponseDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusResponseDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

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

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
