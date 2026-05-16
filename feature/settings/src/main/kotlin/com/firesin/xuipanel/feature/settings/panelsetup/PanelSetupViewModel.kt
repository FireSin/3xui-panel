package com.firesin.xuipanel.feature.settings.panelsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class PanelSetupViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PanelSetupUiState>(PanelSetupUiState.NoActivePanel)
    val uiState: StateFlow<PanelSetupUiState> = _uiState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = PanelSetupUiState.NoActivePanel
                } else {
                    _uiState.value = PanelSetupUiState.Loading(panel)
                    loadSettings(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            loadSettings(panel)
            _isBusy.value = false
        }
    }

    fun updateFlags(transform: (PanelFlags) -> PanelFlags) {
        val s = _uiState.value as? PanelSetupUiState.Content ?: return
        _uiState.value = s.copy(flags = transform(s.flags))
    }

    fun save(savedMsg: String) {
        val s = _uiState.value as? PanelSetupUiState.Content ?: return
        val panel = s.panel
        val merged = patchFields(s.raw, s.flags)
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.updateAllSettings(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                settings = merged,
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> {
                    _snackbarMessage.value = savedMsg
                    loadSettings(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun changeCredentials(newUsername: String, newPassword: String, doneMsg: String) {
        val panel = activePanel() ?: return
        if (newUsername.isBlank() || newPassword.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.updatePanelUser(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                newUsername = newUsername,
                newPassword = newPassword,
            )
            if (result is Result.Success) {
                // Persist new credentials locally so further calls stop using the stale pair.
                val draft = PanelDraft(
                    name = panel.name,
                    baseUrl = panel.baseUrl,
                    login = newUsername,
                    password = newPassword,
                    tlsMode = panel.tlsMode,
                    apiToken = panel.apiToken,
                    twoFactorEnabled = panel.twoFactorEnabled,
                )
                repository.update(panel.id, draft)
                _snackbarMessage.value = doneMsg
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
            _isBusy.value = false
        }
    }

    fun restartPanel(restartingMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.restartPanel(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
            )
            _isBusy.value = false
            _snackbarMessage.value = when (result) {
                is Result.Success -> restartingMsg
                is Result.Failure -> result.error.toString()
            }
        }
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private suspend fun loadSettings(panel: Panel) {
        val result = xuiClient.fetchAllSettings(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> PanelSetupUiState.Content(
                panel = panel,
                raw = result.data,
                flags = extractFlags(result.data),
            )
            is Result.Failure -> PanelSetupUiState.Error(panel, result.error)
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is PanelSetupUiState.Content -> s.panel
        is PanelSetupUiState.Loading -> s.panel
        is PanelSetupUiState.Error -> s.panel
        PanelSetupUiState.NoActivePanel -> null
    }

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun extractFlags(obj: JsonObject) = PanelFlags(
        subEnable = obj.bool("subEnable"),
        subJsonEnable = obj.bool("subJsonEnable"),
        subClashEnable = obj.bool("subClashEnable"),
        subUri = obj.str("subURI"),
        subJsonUri = obj.str("subJsonURI"),
        subClashUri = obj.str("subClashURI"),
        subPort = obj.int("subPort"),
        subPath = obj.str("subPath"),
        subJsonPath = obj.str("subJsonPath"),
        subClashPath = obj.str("subClashPath"),
        subDomain = obj.str("subDomain"),
        subUpdates = obj.int("subUpdates"),
        subEnableRouting = obj.bool("subEnableRouting"),
        subEncrypt = obj.bool("subEncrypt"),
        subShowInfo = obj.bool("subShowInfo"),

        tgBotEnable = obj.bool("tgBotEnable"),
        tgBotToken = obj.str("tgBotToken"),
        tgBotChatId = obj.str("tgBotChatId"),
        tgBotProxy = obj.str("tgBotProxy"),
        tgBotApiServer = obj.str("tgBotAPIServer"),
        tgRunTime = obj.str("tgRunTime"),
        tgBotBackup = obj.bool("tgBotBackup"),
        tgBotLoginNotify = obj.bool("tgBotLoginNotify"),
        tgLang = obj.str("tgLang"),
        tgCpu = obj.int("tgCpu"),
        timeLocation = obj.str("timeLocation"),

        webDomain = obj.str("webDomain"),
        webPort = obj.int("webPort"),
        webBasePath = obj.str("webBasePath"),
        sessionMaxAge = obj.int("sessionMaxAge"),
        pageSize = obj.int("pageSize"),
        trustedProxyCIDRs = obj.str("trustedProxyCIDRs"),
        datepicker = obj.str("datepicker"),

        twoFactorEnable = obj.bool("twoFactorEnable"),
    )

    private fun patchFields(original: JsonObject, flags: PanelFlags): JsonObject {
        val patched = original.toMutableMap()
        // Subscription
        patched["subEnable"] = JsonPrimitive(flags.subEnable)
        patched["subJsonEnable"] = JsonPrimitive(flags.subJsonEnable)
        patched["subClashEnable"] = JsonPrimitive(flags.subClashEnable)
        patched["subURI"] = JsonPrimitive(flags.subUri)
        patched["subJsonURI"] = JsonPrimitive(flags.subJsonUri)
        patched["subClashURI"] = JsonPrimitive(flags.subClashUri)
        patched["subPort"] = JsonPrimitive(flags.subPort)
        patched["subPath"] = JsonPrimitive(flags.subPath)
        patched["subJsonPath"] = JsonPrimitive(flags.subJsonPath)
        patched["subClashPath"] = JsonPrimitive(flags.subClashPath)
        patched["subDomain"] = JsonPrimitive(flags.subDomain)
        patched["subUpdates"] = JsonPrimitive(flags.subUpdates)
        patched["subEnableRouting"] = JsonPrimitive(flags.subEnableRouting)
        patched["subEncrypt"] = JsonPrimitive(flags.subEncrypt)
        patched["subShowInfo"] = JsonPrimitive(flags.subShowInfo)
        // Telegram
        patched["tgBotEnable"] = JsonPrimitive(flags.tgBotEnable)
        patched["tgBotToken"] = JsonPrimitive(flags.tgBotToken)
        patched["tgBotChatId"] = JsonPrimitive(flags.tgBotChatId)
        patched["tgBotProxy"] = JsonPrimitive(flags.tgBotProxy)
        patched["tgBotAPIServer"] = JsonPrimitive(flags.tgBotApiServer)
        patched["tgRunTime"] = JsonPrimitive(flags.tgRunTime)
        patched["tgBotBackup"] = JsonPrimitive(flags.tgBotBackup)
        patched["tgBotLoginNotify"] = JsonPrimitive(flags.tgBotLoginNotify)
        patched["tgLang"] = JsonPrimitive(flags.tgLang)
        patched["tgCpu"] = JsonPrimitive(flags.tgCpu)
        patched["timeLocation"] = JsonPrimitive(flags.timeLocation)
        // Web
        patched["webDomain"] = JsonPrimitive(flags.webDomain)
        patched["webPort"] = JsonPrimitive(flags.webPort)
        patched["webBasePath"] = JsonPrimitive(flags.webBasePath)
        patched["sessionMaxAge"] = JsonPrimitive(flags.sessionMaxAge)
        patched["pageSize"] = JsonPrimitive(flags.pageSize)
        patched["trustedProxyCIDRs"] = JsonPrimitive(flags.trustedProxyCIDRs)
        patched["datepicker"] = JsonPrimitive(flags.datepicker)
        // 2FA — only the on/off flag; secret rotation is a separate panel-side action.
        patched["twoFactorEnable"] = JsonPrimitive(flags.twoFactorEnable)
        return JsonObject(patched)
    }
}
