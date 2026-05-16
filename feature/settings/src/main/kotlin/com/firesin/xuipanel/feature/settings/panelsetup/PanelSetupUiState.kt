package com.firesin.xuipanel.feature.settings.panelsetup

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import kotlinx.serialization.json.JsonObject

/**
 * Mutable subset of `/panel/setting/all` exposed in the UI. The full blob is preserved in
 * [PanelSetupUiState.Content.raw] and only fields listed here are overwritten on save —
 * unknown keys round-trip untouched.
 */
data class PanelFlags(
    // Subscription
    val subEnable: Boolean = false,
    val subJsonEnable: Boolean = false,
    val subClashEnable: Boolean = false,
    val subUri: String = "",
    val subJsonUri: String = "",
    val subClashUri: String = "",
    val subPort: Int = 0,
    val subPath: String = "",
    val subJsonPath: String = "",
    val subClashPath: String = "",
    val subDomain: String = "",
    val subUpdates: Int = 0,
    val subEnableRouting: Boolean = false,
    val subEncrypt: Boolean = false,
    val subShowInfo: Boolean = false,

    // Telegram bot
    val tgBotEnable: Boolean = false,
    val tgBotToken: String = "",
    val tgBotChatId: String = "",
    val tgBotProxy: String = "",
    val tgBotApiServer: String = "",
    val tgRunTime: String = "",
    val tgBotBackup: Boolean = false,
    val tgBotLoginNotify: Boolean = false,
    val tgLang: String = "",
    val tgCpu: Int = 0,
    val timeLocation: String = "",

    // Web
    val webDomain: String = "",
    val webPort: Int = 0,
    val webBasePath: String = "",
    val sessionMaxAge: Int = 0,
    val pageSize: Int = 0,
    val trustedProxyCIDRs: String = "",
    val datepicker: String = "",

    // 2FA
    val twoFactorEnable: Boolean = false,
)

sealed class PanelSetupUiState {
    data object NoActivePanel : PanelSetupUiState()
    data class Loading(val panel: Panel) : PanelSetupUiState()
    data class Content(
        val panel: Panel,
        val raw: JsonObject,
        val flags: PanelFlags,
    ) : PanelSetupUiState()
    data class Error(val panel: Panel, val error: DomainError) : PanelSetupUiState()
}
