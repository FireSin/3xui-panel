package com.firesin.xuipanel.feature.inbounds.add.ui

sealed class AddInboundUiState {
    data class Editing(
        val formState: AddInboundFormState = AddInboundFormState(),
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : AddInboundUiState()

    data object Saved : AddInboundUiState()
}
