package com.firesin.xuipanel.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsrfInterceptorTest {

    private lateinit var apiServer: MockWebServer
    private lateinit var tokenServer: MockWebServer

    @BeforeEach
    fun setup() {
        apiServer = MockWebServer()
        tokenServer = MockWebServer()
        apiServer.start()
        tokenServer.start()
    }

    @AfterEach
    fun teardown() {
        apiServer.shutdown()
        tokenServer.shutdown()
    }

    @Test
    fun `GET request passes through without token fetch`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            Request.Builder().url(apiServer.url("api/info")).get().build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(0, tokenServer.requestCount)
    }

    @Test
    fun `csrf-token endpoint passes through without injection`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"obj":"token"}"""))

        val response = client.newCall(
            Request.Builder().url(tokenServer.url("csrf-token")).get().build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(1, tokenServer.requestCount)
    }

    @Test
    fun `cached token is reused`() {
        val store = CsrfTokenStore()
        store.put("panel-1", "cached-token")

        val tokenClient = OkHttpClient()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            Request.Builder()
                .url(apiServer.url("api/inbounds/del"))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(0, tokenServer.requestCount)

        val apiRequest = apiServer.takeRequest()
        assertEquals("cached-token", apiRequest.headers.get("X-CSRF-Token"))
    }

    @Test
    fun `HTTP 500 on token fetch throws IllegalStateException`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<IllegalStateException> {
            client.newCall(
                Request.Builder()
                    .url(apiServer.url("api/inbounds/add"))
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
    }

    @Test
    fun `empty response body throws IllegalStateException`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertThrows<IllegalStateException> {
            client.newCall(
                Request.Builder()
                    .url(apiServer.url("api/inbounds/add"))
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
    }

    @Test
    fun `blank token value throws IllegalStateException`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"obj":""}"""))

        assertThrows<IllegalStateException> {
            client.newCall(
                Request.Builder()
                    .url(apiServer.url("api/inbounds/add"))
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
    }

    @Test
    fun `malformed JSON throws IllegalStateException`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""not json"""))

        assertThrows<IllegalStateException> {
            client.newCall(
                Request.Builder()
                    .url(apiServer.url("api/inbounds/add"))
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
    }

    @Test
    fun `missing obj field throws IllegalStateException`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

        assertThrows<IllegalStateException> {
            client.newCall(
                Request.Builder()
                    .url(apiServer.url("api/inbounds/add"))
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
        }
    }

    @Test
    fun `different panelId uses separate cache entries`() {
        val store = CsrfTokenStore()
        store.put("panel-1", "token-1")
        store.put("panel-2", "token-2")

        val tokenClient = OkHttpClient()
        val interceptor1 = CsrfInterceptor(
            panelId = "panel-1",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )
        val interceptor2 = CsrfInterceptor(
            panelId = "panel-2",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient
        )

        val client1 = OkHttpClient.Builder().addInterceptor(interceptor1).build()
        val client2 = OkHttpClient.Builder().addInterceptor(interceptor2).build()

        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(apiServer.url("api/inbounds/list"))
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        client1.newCall(request).execute().close()
        client2.newCall(request).execute().close()

        val req1 = apiServer.takeRequest()
        val req2 = apiServer.takeRequest()

        assertEquals("token-1", req1.headers.get("X-CSRF-Token"))
        assertEquals("token-2", req2.headers.get("X-CSRF-Token"))
    }

    // ---- Positive paths: cold-start fetch + 403 retry ----

    @Test
    fun `first non-GET fetches token, caches it, injects header`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        val interceptor = CsrfInterceptor(
            panelId = "panel-cold",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient,
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"obj":"fresh-abc"}"""))
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            Request.Builder()
                .url(apiServer.url("api/inbounds/updateClient/uuid"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(1, tokenServer.requestCount)
        assertEquals(1, apiServer.requestCount)
        val apiReq = apiServer.takeRequest()
        assertEquals("fresh-abc", apiReq.headers.get("X-CSRF-Token"))
        assertEquals("fresh-abc", store.get("panel-cold"))
    }

    @Test
    fun `403 invalidates stale token, fetches fresh, retries once successfully`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        store.put("panel-retry", "stale")

        val interceptor = CsrfInterceptor(
            panelId = "panel-retry",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient,
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        apiServer.enqueue(MockResponse().setResponseCode(403))
        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"obj":"fresh-xyz"}"""))
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            Request.Builder()
                .url(apiServer.url("api/inbounds/addClient"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(2, apiServer.requestCount)
        assertEquals(1, tokenServer.requestCount)
        val firstApi = apiServer.takeRequest()
        val secondApi = apiServer.takeRequest()
        assertEquals("stale", firstApi.headers.get("X-CSRF-Token"))
        assertEquals("fresh-xyz", secondApi.headers.get("X-CSRF-Token"))
        assertEquals("fresh-xyz", store.get("panel-retry"))
    }

    @Test
    fun `403 after retry is returned to caller without infinite loop`() {
        val tokenClient = OkHttpClient()
        val store = CsrfTokenStore()
        store.put("panel-loop", "stale")

        val interceptor = CsrfInterceptor(
            panelId = "panel-loop",
            csrfTokenUrl = tokenServer.url("csrf-token").toString(),
            store = store,
            tokenClient = tokenClient,
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        apiServer.enqueue(MockResponse().setResponseCode(403))
        tokenServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"obj":"fresh-still-bad"}"""))
        apiServer.enqueue(MockResponse().setResponseCode(403))

        val response = client.newCall(
            Request.Builder()
                .url(apiServer.url("api/inbounds/delClient/1/uuid"))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.close()

        assertEquals(403, response.code)
        assertEquals(2, apiServer.requestCount)
        assertEquals(1, tokenServer.requestCount)
    }
}
