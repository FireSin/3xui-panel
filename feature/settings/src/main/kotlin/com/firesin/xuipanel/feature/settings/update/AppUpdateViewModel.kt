package com.firesin.xuipanel.feature.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.update.AppUpdateRepository
import com.firesin.xuipanel.core.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class UpToDate(val current: String) : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(
        val info: UpdateInfo,
        val downloaded: Long,
        val total: Long,
    ) : UpdateUiState()
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    fun checkForUpdate() {
        if (_state.value is UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = updateRepository.checkLatest()) {
                is Result.Success -> {
                    val info = result.data
                    _state.value = if (info.isUpdateAvailable) {
                        UpdateUiState.Available(info)
                    } else {
                        UpdateUiState.UpToDate(info.currentVersion)
                    }
                }
                is Result.Failure -> {
                    _state.value = UpdateUiState.Error(result.error.toString())
                }
            }
        }
    }

    fun downloadAndInstall() {
        val current = _state.value
        if (current !is UpdateUiState.Available) return
        val info = current.info
        val apkUrl = info.apkUrl ?: run {
            _state.value = UpdateUiState.Error("APK не найден в релизе")
            return
        }
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(info, downloaded = 0L, total = info.apkSizeBytes)
            when (val result = updateRepository.downloadApk(apkUrl) { downloaded, total ->
                _state.value = UpdateUiState.Downloading(info, downloaded, total)
            }) {
                is Result.Success -> _state.value = UpdateUiState.ReadyToInstall(info, result.data)
                is Result.Failure -> _state.value = UpdateUiState.Error(result.error.toString())
            }
        }
    }

    fun reset() {
        _state.value = UpdateUiState.Idle
    }
}
