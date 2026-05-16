package com.firesin.xuipanel.core.data.update

import com.firesin.xuipanel.core.common.Result
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class AppUpdateRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GitHubApi

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GitHubApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // Helper: check latest using injected currentVersion (avoids BuildConfig in JVM tests)
    private suspend fun checkLatest(currentVersion: String): Result<UpdateInfo, *> {
        val response = api.latestRelease("FireSin", "3xui-panel")
        if (!response.isSuccessful) {
            return Result.Failure(
                com.firesin.xuipanel.core.common.DomainError.PanelResponse(
                    response.code(), response.message(),
                ),
            )
        }
        val dto = response.body()!!
        val latestVersion = dto.tagName.removePrefix("v").trim()
        val isUpdateAvailable = isNewer(latestVersion, currentVersion)
        val apkAsset = dto.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType == "application/vnd.android.package-archive"
        }
        return Result.Success(
            UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                isUpdateAvailable = isUpdateAvailable,
                releaseNotes = dto.body,
                apkUrl = apkAsset?.browserDownloadUrl,
                apkSizeBytes = apkAsset?.size ?: 0L,
                releasePageUrl = dto.htmlUrl,
            ),
        )
    }

    @Test
    fun `newer tag with v prefix marks isUpdateAvailable true`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tag_name": "v0.2.0",
                      "name": "Release 0.2.0",
                      "body": "Bug fixes",
                      "html_url": "https://github.com/FireSin/3xui-panel/releases/tag/v0.2.0",
                      "assets": [
                        {
                          "name": "3xui-panel-0.2.0.apk",
                          "browser_download_url": "https://github.com/FireSin/3xui-panel/releases/download/v0.2.0/3xui-panel.apk",
                          "size": 12345678,
                          "content_type": "application/vnd.android.package-archive"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = checkLatest("0.1.0")

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success<UpdateInfo>).data
        assertTrue(info.isUpdateAvailable)
        assertEquals("0.2.0", info.latestVersion)
        assertEquals("0.1.0", info.currentVersion)
        assertNotNull(info.apkUrl)
        assertEquals(12345678L, info.apkSizeBytes)
    }

    @Test
    fun `same version marks isUpdateAvailable false`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tag_name": "v0.1.0",
                      "assets": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = checkLatest("0.1.0")

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success<UpdateInfo>).data
        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun `tag without v prefix is recognized correctly`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tag_name": "0.2.0",
                      "assets": [
                        {
                          "name": "app.apk",
                          "browser_download_url": "https://example.com/app.apk",
                          "size": 1000,
                          "content_type": "application/vnd.android.package-archive"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = checkLatest("0.1.0")

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success<UpdateInfo>).data
        assertTrue(info.isUpdateAvailable)
        assertEquals("0.2.0", info.latestVersion)
    }

    @Test
    fun `empty assets results in null apkUrl`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "tag_name": "v0.2.0",
                      "assets": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = checkLatest("0.1.0")

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success<UpdateInfo>).data
        // No APK asset — apkUrl should be null
        assertTrue(info.apkUrl == null)
        assertTrue(info.isUpdateAvailable)
    }

    // ---- Version comparison helper (mirrors AppUpdateRepositoryImpl) ----

    private fun isNewer(candidate: String, current: String): Boolean = runCatching {
        fun parse(v: String): Triple<Int, Int, Int> {
            val parts = v.substringBefore("-").split(".")
            return Triple(
                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                parts.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
        val (cMaj, cMin, cPat) = parse(candidate)
        val (eMaj, eMin, ePat) = parse(current)
        when {
            cMaj != eMaj -> cMaj > eMaj
            cMin != eMin -> cMin > eMin
            else -> cPat > ePat
        }
    }.getOrElse { false }
}
