package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.domain.TonexParam
import com.opentonex.controller.protocol.TonexFullPreset
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
        presetIds = listOf(0x0C, 0x08, 0x07),
        presetParameters = simulatedPresetParameters()
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

    /** Volume master simulado (0..10), como o pedal informa no 0x0309 de subtipo 0x03. */
    var masterVolume = 8f
        private set

    override suspend fun writeMasterVolume(value: Float) {
        masterVolume = value
        requestMasterVolume()
    }

    override suspend fun requestMasterVolume() {
        events.tryEmit(PedalRuntimeEvent.MasterVolumeChanged(value = masterVolume, payloadHex = ""))
    }

    /** Presets completos simulados (0x0303) por indice salvo, com Tone Model sintetico. */
    val fullPresets: MutableMap<Int, ByteArray> = (0 until 20).associateWith { index ->
        SimulatedFullPreset.body(
            bank = 1,
            index = index,
            presetName = "Preset ${index + 1}",
            toneModelName = "Demo Amp ${index + 1}"
        )
    }.toMutableMap()

    override suspend fun readFullPreset(bank: Int, index: Int): ByteArray {
        val stored = fullPresets[index] ?: error("preset simulado inexistente: $index")
        return stored.copyOf().also { body ->
            body[2] = bank.toByte()
            body[3] = (if (bank == 0) activeLibraryIndex() else index).toByte()
        }
    }

    override suspend fun writeFullPreset(body: ByteArray, targetIndex: Int) {
        val payload = TonexFullPreset.writePayload(body, targetIndex)
        fullPresets[targetIndex] = payload.copyOfRange(payload.size - body.size, payload.size)
    }

    private fun activeLibraryIndex(): Int = state.presetIds.getOrNull(state.activeSlot.ordinal) ?: 0

    /** Ultimo valor escrito por indice de parametro (inspecionavel nos testes). */
    val parameters = mutableMapOf<Int, Float>()
    val parameterWriteCounts = mutableMapOf<Int, Int>()

    override suspend fun disconnect() {
        events.tryEmit(PedalRuntimeEvent.Disconnected)
    }

    private companion object {
        /** Tamanho do bloco de parametros do 0x0304 (indices 0..108 da tabela tonex_params). */
        const val SIMULATED_PARAMETER_COUNT = 109

        /**
         * Bloco de parametros do preset simulado, para o editor e o detalhe de efeito terem o
         * que mostrar sem pedal. Valores de demonstracao escolhidos aqui, nao copiados de preset.
         */
        fun simulatedPresetParameters(): List<Float> {
            val values = MutableList(SIMULATED_PARAMETER_COUNT) { 5f } // meio da faixa 0..10
            val demo = mapOf(
                TonexParam.NOISE_GATE_ENABLE to 1f,
                TonexParam.COMP_ENABLE to 1f,
                TonexParam.COMP_MAKE_UP to 0f,
                TonexParam.EQ_BASS to 5.5f,
                TonexParam.EQ_MID to 4.5f,
                TonexParam.EQ_TREBLE to 6f,
                TonexParam.MODEL_AMP_ENABLE to 1f,
                TonexParam.MODEL_GAIN to 6.5f,
                TonexParam.MODEL_VOLUME to 7f,
                TonexParam.CABINET_TYPE to 0f,
                TonexParam.VIR_CABINET_MODEL to 0f,
                TonexParam.REVERB_ENABLE to 1f,
                TonexParam.REVERB_MODEL to 4f, // Room
                TonexParam.MODULATION_ENABLE to 0f,
                TonexParam.MODULATION_MODEL to 0f, // Chorus
                TonexParam.DELAY_ENABLE to 1f,
                TonexParam.DELAY_MODEL to 1f // Tape
            )
            demo.forEach { (param, value) -> values[param.index] = value }
            return values
        }
    }
}
