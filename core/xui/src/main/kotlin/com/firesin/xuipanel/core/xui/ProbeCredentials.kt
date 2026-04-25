package com.firesin.xuipanel.core.xui

data class ProbeCredentials(
    val baseUrl: String,
    val login: String,
    val password: String,
    val trustSelfSigned: Boolean,
)
