package com.opentonex.controller.repository

import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connected(val firmware: FirmwareInfo, val pedal: PedalState) : ConnectionState
}

class PedalRepository(private val connection: PedalConnection) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    suspend fun connect() {
        connection.connect()
        val fw = connection.sendHello()
        val pedal = connection.requestState()
        _state.value = ConnectionState.Connected(fw, pedal)
    }

    suspend fun selectSlot(slot: Slot) {
        val current = _state.value as? ConnectionState.Connected ?: return
        val updated = current.pedal.withActiveSlot(slot)
        connection.writeState(updated)
        _state.value = current.copy(pedal = connection.requestState())
    }

    suspend fun disconnect() {
        connection.disconnect()
        _state.value = ConnectionState.Disconnected
    }
}
