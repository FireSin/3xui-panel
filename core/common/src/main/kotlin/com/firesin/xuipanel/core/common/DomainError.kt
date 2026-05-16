package com.firesin.xuipanel.core.common

sealed class DomainError {
    data class Network(val cause: Throwable) : DomainError()
    data class Tls(val message: String) : DomainError()
    data object InvalidCredentials : DomainError()
    data class PanelUnreachable(val httpCode: Int?) : DomainError()
    data class PanelResponse(val code: Int, val body: String) : DomainError()
    data class Unexpected(val cause: Throwable) : DomainError()
    data class PinMismatch(val panelId: String, val observedSpki: String) : DomainError()
    data class NotFound(val message: String) : DomainError()
}
