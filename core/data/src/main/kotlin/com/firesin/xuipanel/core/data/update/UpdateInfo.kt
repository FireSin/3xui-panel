package com.firesin.xuipanel.core.data.update

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseNotes: String?,
    val apkUrl: String?,
    val apkSizeBytes: Long,
    val releasePageUrl: String?,
)
