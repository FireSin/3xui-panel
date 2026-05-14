package com.firesin.xuipanel.feature.inbounds.add.ui

sealed class AddInboundUiState {
    data class Editing(
        val formState: AddInboundFormState = AddInboundFormState(),
        val isSaving: Boolean = false,
        val isLoading: Boolean = false,
        val editingInboundId: Int? = null,
        val errorMessage: String? = null,
    ) : AddInboundUiState()

    data object Saved : AddInboundUiState()
}
