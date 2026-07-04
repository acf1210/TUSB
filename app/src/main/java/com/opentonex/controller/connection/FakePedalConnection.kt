package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.domain.PedalMode
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
        libraryPresets = (0 until 20).map { index ->
            LibraryPreset(
                index = index,
                name = "Preset ${index + 1}",
                color = when (index % 3) {
                    0 -> Rgb(255, 80, 56)
                    1 -> Rgb(46, 204, 113)
                    else -> Rgb(52, 152, 219)
                }
            )
        },
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
    override suspend fun switchMode(currentState: PedalState, targetMode: PedalMode) {
        state = state.copy(pedalMode = targetMode)
    }

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

    override suspend fun loadPresetToSlot(
        currentState: PedalState,
        presetId: Int,
        slot: Slot,
        selectSlot: Boolean
    ) {
        val ids = state.presetIds.toMutableList()
        while (ids.size < Slot.entries.size) ids.add(0)
        ids[slot.ordinal] = presetId
        val updatedSlots = state.slots.mapIndexed { index, preset ->
            if (index == slot.ordinal) preset.copy(name = "Preset ${presetId + 1}") else preset
        }
        state = state.copy(
            activeSlot = if (selectSlot) slot else state.activeSlot,
            presetIds = ids,
            slots = updatedSlots,
            pedalMode = if (slot == Slot.C) PedalMode.STOMP else state.pedalMode,
            bypassMode = false
        )
    }

    override suspend fun writeParameter(paramIndex: Int, value: Float) {
        parameters[paramIndex] = value
        parameterWriteCounts[paramIndex] = (parameterWriteCounts[paramIndex] ?: 0) + 1
        events.tryEmit(
            PedalRuntimeEvent.ParameterChanged(
                paramIndex = paramIndex,
                value = value,
                messageType = 0x0309,
                payloadHex = ""
            )
        )
    }

    /** Ultimo valor escrito por indice de parametro (inspecionavel nos testes). */
    val parameters = mutableMapOf<Int, Float>()
    val parameterWriteCounts = mutableMapOf<Int, Int>()

    override suspend fun disconnect() {
        events.tryEmit(PedalRuntimeEvent.Disconnected)
    }
}
