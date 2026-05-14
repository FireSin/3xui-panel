package com.firesin.xuipanel.feature.nodes.ui

import com.firesin.xuipanel.core.xui.dto.NodeStatusProbeDto

data class NodeFormFields(
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: String = "2053",
    val basePath: String = "",
    val apiToken: String = "",
    val allowPrivateAddress: Boolean = false,
    val enable: Boolean = true,
)

val NodeFormFields.isValid: Boolean
    get() = name.isNotBlank() && address.isNotBlank() && port.toIntOrNull() != null && apiToken.isNotBlank()

sealed class NodeFormUiState {
    data class Editing(
        val fields: NodeFormFields = NodeFormFields(),
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val isTesting: Boolean = false,
        val testResult: TestResult? = null,
        val errorMessage: String? = null,
        val editingNodeId: Int? = null,
    ) : NodeFormUiState()

    data object Saved : NodeFormUiState()

    sealed class TestResult {
        data class Success(val probe: NodeStatusProbeDto) : TestResult()
        data class Failure(val message: String) : TestResult()
    }
}
