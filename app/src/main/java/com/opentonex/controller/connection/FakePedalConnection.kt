package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakePedalConnection : PedalConnection {
    private val events = MutableSharedFlow<PedalRuntimeEvent>(extraBufferCapacity = 32)
    private var state = PedalState(
        activeSlot = Slot.A,
        inputTrim = 0.0f,
        a4Reference = 440,
        tempo = 120,
        slots = listOf(
            PresetSlot(0, "Preset A", Rgb(255, 0, 0)),
            PresetSlot(1, "Preset B", Rgb(0, 255, 0)),
            PresetSlot(2, "Preset C", Rgb(0, 0, 255))
        ),
        presetIds = listOf(0x0C, 0x08, 0x07)
    )

    override val runtimeEvents: Flow<PedalRuntimeEvent> = events.asSharedFlow()

    override suspend fun connect() { /* no-op no simulador */ }
    override suspend fun handshake(): Handshake {
        val firmware = FirmwareInfo("SIM-1.0.0")
        events.tryEmit(
            PedalRuntimeEvent.HelloResponseReceived(
                firmwareVersion = firmware.version,
                messageType = 0x0306,
                payloadHex = ""
            )
        )
        events.tryEmit(
            PedalRuntimeEvent.StateReceived(
                state = state,
                messageType = 0x0306,
                payloadHex = ""
            )
        )
        return Handshake(firmware = firmware, state = state)
    }
    override suspend fun requestState(): PedalState {
        events.tryEmit(
            PedalRuntimeEvent.StateReceived(
                state = state,
                messageType = 0x0306,
                payloadHex = ""
            )
        )
        return state
    }
    override suspend fun writeState(state: PedalState) { this.state = state }
    override suspend fun selectPreset(presetId: Int) {
        val slot = state.presetIds.indexOf(presetId).takeIf { it >= 0 } ?: return
        state = state.copy(activeSlot = Slot.entries[slot])
        events.tryEmit(
            PedalRuntimeEvent.PresetDetailReceived(
                name = state.slots[slot].name,
                messageType = 0x0304,
                payloadHex = ""
            )
        )
    }
    override suspend fun disconnect() {
        events.tryEmit(PedalRuntimeEvent.Disconnected)
    }
}
