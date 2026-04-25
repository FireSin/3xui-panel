package com.firesin.xuipanel.feature.panels.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PanelsListUiState {
    data object Loading : PanelsListUiState()
    data class Content(
        val panels: List<Panel>,
        val active: Panel?,
    ) : PanelsListUiState()
}

@HiltViewModel
class PanelsListViewModel @Inject constructor(
    private val repository: PanelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PanelsListUiState>(PanelsListUiState.Loading)
    val uiState: StateFlow<PanelsListUiState> = _uiState

    private val _errorMessage = MutableStateFlow<DomainError?>(null)
    val errorMessage: StateFlow<DomainError?> = _errorMessage

    init {
        combine(
            repository.observeAll(),
            repository.observeActive(),
        ) { panels, active ->
            PanelsListUiState.Content(panels = panels, active = active)
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun setActive(id: String) {
        viewModelScope.launch {
            when (val result = repository.setActive(id)) {
                is Result.Failure -> _errorMessage.value = result.error
                is Result.Success -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is Result.Failure -> _errorMessage.value = result.error
                is Result.Success -> Unit
            }
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }
}
