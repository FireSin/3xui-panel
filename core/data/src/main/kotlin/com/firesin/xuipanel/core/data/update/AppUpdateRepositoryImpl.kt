package com.firesin.xuipanel.core.data.update

import android.content.Context
import android.util.Log
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApi: GitHubApi,
    @GitHubOkHttpClient private val okHttpClient: OkHttpClient,
) : AppUpdateRepository {

    override suspend fun checkLatest(): Result<UpdateInfo, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            }.getOrDefault("0.0.0")

            val response = gitHubApi.latestRelease(GITHUB_OWNER, GITHUB_REPO)
            if (!response.isSuccessful) {
                return@withContext Result.Failure(
                    DomainError.PanelResponse(response.code(), response.message()),
                )
            }
            val dto = response.body() ?: return@withContext Result.Failure(
                DomainError.Unexpected(IllegalStateException("Empty response body")),
            )

            val latestVersion = dto.tagName.removePrefix("v").trim()
            val isUpdateAvailable = isNewer(latestVersion, currentVersion)

            val apkAsset = dto.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) ||
                    asset.contentType == "application/vnd.android.package-archive"
            }

            Result.Success(
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
        }.getOrElse { Result.Failure(DomainError.Network(it)) }
    }

    override suspend fun downloadApk(
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File, DomainError> = withContext(Dispatchers.IO) {
        runCatching {
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Failure(
                    DomainError.PanelResponse(response.code, response.message),
                )
            }

            val body = response.body ?: return@withContext Result.Failure(
                DomainError.Unexpected(IllegalStateException("Empty APK response body")),
            )
            val total = body.contentLength()

            val updateDir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val latestVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")
            val apkFile = File(updateDir, "3xui-panel-$latestVersion.apk")

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }

            Result.Success(apkFile)
        }.getOrElse { Result.Failure(DomainError.Network(it)) }
    }

    private companion object {
        const val GITHUB_OWNER = "FireSin"
        const val GITHUB_REPO = "3xui-panel"
        const val BUFFER_SIZE = 64 * 1024

        /**
         * Returns true if [candidate] is semantically newer than [current].
         * Strips suffixes like "-rc1", parses major.minor.patch as Int.
         * Falls back to false on parse error.
         */
        fun isNewer(candidate: String, current: String): Boolean = runCatching {
            fun parse(v: String): Triple<Int, Int, Int> {
                val parts = v.substringBefore("-").split(".")
                val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                return Triple(major, minor, patch)
            }
            val (cMaj, cMin, cPat) = parse(candidate)
            val (eMaj, eMin, ePat) = parse(current)
            when {
                cMaj != eMaj -> cMaj > eMaj
                cMin != eMin -> cMin > eMin
                else -> cPat > ePat
            }
        }.getOrElse {
            Log.w("AppUpdateRepository", "Failed to parse versions: candidate=$candidate current=$current", it)
            false
        }
    }
}
