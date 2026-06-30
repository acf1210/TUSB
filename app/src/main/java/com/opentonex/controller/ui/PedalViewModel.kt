package com.opentonex.controller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opentonex.controller.capture.EventCaptureRecorder
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val currentFilePath: String? = null,
    val lastFilePath: String? = null
)

data class UiBusyState(
    val isBusy: Boolean = false,
    val busyReason: String? = null
)

class PedalViewModel : ViewModel() {
    private var repository: PedalRepository? = null
    private var captureRecorder: EventCaptureRecorder? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    private val _busy = MutableStateFlow(UiBusyState())
    val busy: StateFlow<UiBusyState> = _busy.asStateFlow()

    fun connectWith(connection: PedalConnection) {
        if (_busy.value.isBusy) return
        val repo = PedalRepository(connection, viewModelScope)
        repo.attachCaptureRecorder(captureRecorder)
        repository = repo
        viewModelScope.launch {
            try {
                setBusy("Conectando ao pedal...")
                _error.value = null
                repo.connect()
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao conectar ao pedal"
            } finally {
                clearBusy()
            }
        }
    }

    /**
     * Conecta ao pedal real. A [factory] e suspensa porque abrir a porta USB exige o fluxo
     * assincrono de permissao do Android (ver UsbSerialTransport.connect). Retornar null
     * significa "pedal nao encontrado" e e reportado via [error].
     */
    fun connectReal(factory: suspend () -> PedalConnection?) {
        if (_busy.value.isBusy) return
        viewModelScope.launch {
            try {
                setBusy("Conectando ao pedal...")
                _error.value = null
                val connection = factory()
                if (connection == null) {
                    _error.value = "Pedal nao encontrado via USB"
                    return@launch
                }
                val repo = PedalRepository(connection, viewModelScope)
                repo.attachCaptureRecorder(captureRecorder)
                repository = repo
                repo.connect()
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao conectar ao pedal"
            } finally {
                clearBusy()
            }
        }
    }

    fun selectSlot(slot: Slot) {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy("Trocando preset para ${slot.name}...")
                repo.selectSlot(slot)
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao trocar de slot"
            } finally {
                clearBusy()
            }
        }
    }

    fun switchMode(targetMode: PedalMode) {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy("Alterando modo para ${targetMode.name}...")
                _error.value = null
                repo.switchMode(targetMode)
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao alterar modo"
            } finally {
                clearBusy()
            }
        }
    }

    fun refreshState() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy("Atualizando estado do pedal...")
                _error.value = null
                repo.refreshState()
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao atualizar o estado do pedal"
            } finally {
                clearBusy()
            }
        }
    }

    fun startCapture(rootDirectory: File) {
        try {
            val recorder = captureRecorder ?: EventCaptureRecorder(rootDirectory).also {
                captureRecorder = it
            }
            val session = recorder.startSession()
            repository?.attachCaptureRecorder(recorder)
            _capture.value = CaptureUiState(
                isCapturing = true,
                currentFilePath = session.file.absolutePath,
                lastFilePath = session.file.absolutePath
            )
            _error.value = null
        } catch (e: Exception) {
            _error.value = e.message ?: "Falha ao iniciar a captura"
        }
    }

    fun stopCapture() {
        val recorder = captureRecorder ?: return
        val file = recorder.stopSession()
        _capture.value = _capture.value.copy(
            isCapturing = false,
            currentFilePath = null,
            lastFilePath = file?.absolutePath ?: _capture.value.lastFilePath
        )
    }

    fun disconnect() {
        if (_busy.value.isBusy) return
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                setBusy("Desconectando...")
                repo.disconnect()
                _state.value = repo.state.value
                repository = null
                // A captura NAO para no desconectar: senao o handshake do reconnect seguinte
                // (justamente o que precisamos medir) nunca seria gravado. A captura e
                // controlada so pelos botoes Iniciar/Parar captura.
            } finally {
                clearBusy()
            }
        }
    }

    private fun setBusy(reason: String) {
        _busy.value = UiBusyState(isBusy = true, busyReason = reason)
    }

    private fun clearBusy() {
        _busy.value = UiBusyState()
    }
}
