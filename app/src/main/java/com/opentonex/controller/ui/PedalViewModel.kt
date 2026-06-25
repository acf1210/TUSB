package com.opentonex.controller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PedalViewModel : ViewModel() {
    private var repository: PedalRepository? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connectWith(connection: PedalConnection) {
        val repo = PedalRepository(connection)
        repository = repo
        viewModelScope.launch {
            try {
                _error.value = null
                repo.connect()
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao conectar ao pedal"
            }
        }
    }

    fun selectSlot(slot: Slot) {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                repo.selectSlot(slot)
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao trocar de slot"
            }
        }
    }

    fun disconnect() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.disconnect()
            _state.value = repo.state.value
            repository = null
        }
    }
}
