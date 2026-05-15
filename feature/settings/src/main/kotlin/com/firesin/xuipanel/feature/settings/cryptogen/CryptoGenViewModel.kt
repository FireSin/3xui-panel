package com.firesin.xuipanel.feature.settings.cryptogen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A pair of (label, value) lines shown in the result dialog. */
data class KeyResult(val title: String, val lines: List<Pair<String, String>>)

@HiltViewModel
class CryptoGenViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _result = MutableStateFlow<KeyResult?>(null)
    val result: StateFlow<KeyResult?> = _result

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    fun generateX25519(title: String) = withPanel { panel ->
        val r = xuiClient.fetchNewX25519(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
        finish(title, r) { listOf("privateKey" to it.privateKey, "publicKey" to it.publicKey) }
    }

    fun generateMldsa65(title: String) = withPanel { panel ->
        val r = xuiClient.fetchNewMldsa65(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
        finish(title, r) { listOf("seed" to it.seed, "verify" to it.verify) }
    }

    fun generateMlkem768(title: String) = withPanel { panel ->
        val r = xuiClient.fetchNewMlkem768(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
        finish(title, r) { listOf("client" to it.client, "server" to it.server) }
    }

    fun generateVlessEnc(title: String) = withPanel { panel ->
        val r = xuiClient.fetchVlessEncAuths(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
        finish(title, r) { auths ->
            auths.flatMap {
                listOf(
                    "${it.label} · encryption" to it.encryption,
                    "${it.label} · decryption" to it.decryption,
                )
            }
        }
    }

    fun generateEchCert(sni: String, title: String) = withPanel { panel ->
        val r = xuiClient.fetchNewEchCert(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls(), sni)
        finish(title, r) { listOf("echConfigList" to it.echConfigList, "echServerKeys" to it.echServerKeys) }
    }

    fun consumeResult() {
        _result.value = null
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private fun withPanel(block: suspend (Panel) -> Unit) {
        viewModelScope.launch {
            val panel = repository.observeActive().first()
            if (panel == null) {
                _snackbarMessage.value = "Сначала выберите активную панель"
                return@launch
            }
            _isBusy.value = true
            try {
                block(panel)
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun <T> finish(title: String, r: Result<T, DomainError>, map: (T) -> List<Pair<String, String>>) {
        when (r) {
            is Result.Success -> _result.value = KeyResult(title, map(r.data))
            is Result.Failure -> _snackbarMessage.value = r.error.toString()
        }
    }
}
