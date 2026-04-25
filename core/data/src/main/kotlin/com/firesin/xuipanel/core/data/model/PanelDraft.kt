package com.firesin.xuipanel.core.data.model

data class PanelDraft(
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val trustSelfSigned: Boolean,
)
