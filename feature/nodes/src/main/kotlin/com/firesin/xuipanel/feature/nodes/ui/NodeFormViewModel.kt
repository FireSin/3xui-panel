package com.firesin.xuipanel.feature.nodes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.AddNodeRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodeFormViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NodeFormUiState>(NodeFormUiState.Editing())
    val uiState: StateFlow<NodeFormUiState> = _uiState

    private var activePanel: Panel? = null

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel -> activePanel = panel }
            .launchIn(viewModelScope)

        val nodeId = savedStateHandle.get<Int>("nodeId")
        if (nodeId != null) {
            loadForEdit(nodeId)
        }
    }

    private fun loadForEdit(nodeId: Int) {
        _uiState.value = NodeFormUiState.Editing(isLoading = true, editingNodeId = nodeId)
        viewModelScope.launch {
            val panel = repository.observeActive().first { it != null } ?: run {
                _uiState.value = NodeFormUiState.Editing(
                    errorMessage = "Нет активной панели",
                    editingNodeId = nodeId,
                )
                return@launch
            }
            when (val result = xuiClient.fetchNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = nodeId,
            )) {
                is Result.Success -> {
                    val dto = result.data
                    _uiState.value = NodeFormUiState.Editing(
                        fields = NodeFormFields(
                            name = dto.name,
                            remark = dto.remark,
                            scheme = dto.scheme,
                            address = dto.address,
                            port = dto.port.toString(),
                            basePath = dto.basePath,
                            apiToken = dto.apiToken,
                            allowPrivateAddress = dto.allowPrivateAddress,
                            enable = dto.enable,
                        ),
                        isLoading = false,
                        editingNodeId = nodeId,
                    )
                }
                is Result.Failure -> {
                    _uiState.value = NodeFormUiState.Editing(
                        errorMessage = result.error.toUserMessage(),
                        isLoading = false,
                        editingNodeId = nodeId,
                    )
                }
            }
        }
    }

    // ---- field updaters ----

    private fun updateForm(block: NodeFormUiState.Editing.() -> NodeFormUiState.Editing) {
        val editing = _uiState.value as? NodeFormUiState.Editing ?: return
        _uiState.value = editing.block()
    }

    fun updateName(v: String) = updateForm { copy(fields = fields.copy(name = v)) }
    fun updateRemark(v: String) = updateForm { copy(fields = fields.copy(remark = v)) }
    fun updateScheme(v: String) = updateForm { copy(fields = fields.copy(scheme = v)) }
    fun updateAddress(v: String) = updateForm { copy(fields = fields.copy(address = v)) }
    fun updatePort(v: String) = updateForm { copy(fields = fields.copy(port = v)) }
    fun updateBasePath(v: String) = updateForm { copy(fields = fields.copy(basePath = v)) }
    fun updateApiToken(v: String) = updateForm { copy(fields = fields.copy(apiToken = v)) }
    fun updateAllowPrivateAddress(v: Boolean) = updateForm { copy(fields = fields.copy(allowPrivateAddress = v)) }
    fun updateEnable(v: Boolean) = updateForm { copy(fields = fields.copy(enable = v)) }

    // ---- actions ----

    fun testConnection() {
        val editing = _uiState.value as? NodeFormUiState.Editing ?: return
        val panel = activePanel ?: run {
            _uiState.value = editing.copy(errorMessage = "Нет активной панели")
            return
        }
        val body = editing.fields.toRequestDto() ?: return
        _uiState.value = editing.copy(isTesting = true, testResult = null, errorMessage = null)
        viewModelScope.launch {
            when (val result = xuiClient.testNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                body = body,
            )) {
                is Result.Success -> {
                    _uiState.value = ((_uiState.value as? NodeFormUiState.Editing) ?: editing).copy(
                        isTesting = false,
                        testResult = NodeFormUiState.TestResult.Success(result.data),
                    )
                }
                is Result.Failure -> {
                    _uiState.value = ((_uiState.value as? NodeFormUiState.Editing) ?: editing).copy(
                        isTesting = false,
                        testResult = NodeFormUiState.TestResult.Failure(result.error.toUserMessage()),
                    )
                }
            }
        }
    }

    fun save() {
        val editing = _uiState.value as? NodeFormUiState.Editing ?: return
        val panel = activePanel ?: run {
            _uiState.value = editing.copy(errorMessage = "Нет активной панели")
            return
        }
        val body = editing.fields.toRequestDto() ?: return
        _uiState.value = editing.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val editId = editing.editingNodeId
            val result = if (editId != null) {
                xuiClient.updateNode(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                    id = editId,
                    body = body,
                )
            } else {
                xuiClient.addNode(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                    body = body,
                )
            }
            when (result) {
                is Result.Success -> _uiState.value = NodeFormUiState.Saved
                is Result.Failure -> {
                    _uiState.value = ((_uiState.value as? NodeFormUiState.Editing) ?: editing).copy(
                        isSaving = false,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun errorShown() {
        val editing = _uiState.value as? NodeFormUiState.Editing ?: return
        _uiState.value = editing.copy(errorMessage = null)
    }

    private fun NodeFormFields.toRequestDto(): AddNodeRequestDto? {
        val portInt = port.toIntOrNull() ?: return null
        return AddNodeRequestDto(
            name = name,
            remark = remark,
            scheme = scheme,
            address = address,
            port = portInt,
            basePath = basePath,
            apiToken = apiToken,
            enable = enable,
            allowPrivateAddress = allowPrivateAddress,
        )
    }

    private fun DomainError.toUserMessage(): String = when (this) {
        is DomainError.InvalidCredentials -> "Неверный логин или пароль"
        is DomainError.Tls -> "Ошибка TLS: $message"
        is DomainError.Network -> {
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
            "Нет соединения с панелью ($detail)"
        }
        is DomainError.PanelUnreachable -> "Панель недоступна"
        is DomainError.PanelResponse -> "Ответ панели: $body"
        is DomainError.Unexpected -> {
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
            "Неожиданная ошибка ($detail)"
        }
        is DomainError.PinMismatch -> "Сертификат панели изменился"
    }
}
