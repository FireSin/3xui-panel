package com.firesin.xuipanel.core.common

sealed interface PanelAuth {
    data class Login(
        val username: String,
        val password: String,
        val twoFactorEnabled: Boolean = false,
        val panelName: String = "",
    ) : PanelAuth
    data class Bearer(val token: String) : PanelAuth
}
