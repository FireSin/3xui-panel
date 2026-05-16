package com.firesin.xuipanel.core.data.update

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import java.io.File

interface AppUpdateRepository {
    suspend fun checkLatest(): Result<UpdateInfo, DomainError>
    suspend fun downloadApk(
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File, DomainError>
}
