package com.opentonex.controller.domain

enum class Slot { A, B, C }

enum class PedalMode { AB, STOMP }

data class Rgb(val r: Int, val g: Int, val b: Int)

enum class ParamType { FLOAT, INT, BYTE }

data class Parameter(
    val id: String,
    val label: String,
    val type: ParamType,
    val value: Float,
    val min: Float,
    val max: Float
)

data class PresetSlot(
    val index: Int,
    val name: String,
    val color: Rgb,
    val parameters: Map<String, Parameter> = emptyMap()
)

data class LibraryPreset(
    val index: Int,
    val name: String,
    val color: Rgb
)

/** Tipo de cabinet do preset ativo (tonex_params indice 24). */
enum class CabinetType { TONE_MODEL, VIR, DISABLED }

/**
 * Modelos de amp/cab em uso no preset ATIVO, derivados do bloco de parametros 0x0304.
 * O "amp" do ToneX One e' o proprio Tone Model (capture) do preset; o cab pode ser o
 * do capture (Tone Model), um gabinete VIR (indice do modelo) ou estar desativado.
 * Campos nulos enquanto o pedal nao envia o detalhe do preset.
 */
data class RigModels(
    val ampEnabled: Boolean?,
    val cabinetType: CabinetType?,
    val virCabinetModel: Int?
) {
    /** Rotulo curto do cab para a UI (ex.: "TONE MODEL", "VIR 4", "OFF"). */
    fun cabLabel(cabSimBypass: Boolean): String = when {
        cabSimBypass -> "OFF"
        cabinetType == CabinetType.TONE_MODEL -> "TONE MODEL"
        cabinetType == CabinetType.VIR -> "VIR ${(virCabinetModel ?: 0) + 1}"
        cabinetType == CabinetType.DISABLED -> "OFF"
        else -> "—"
    }
}

data class PedalState(
    val activeSlot: Slot,
    val inputTrim: Float,
    val a4Reference: Int,
    val tempo: Int,
    val slots: List<PresetSlot>,
    val libraryPresets: List<LibraryPreset> = emptyList(),
    /** ID do preset (na biblioteca do pedal) atribuido a cada slot, na ordem A, B, C. */
    val presetIds: List<Int> = emptyList(),
    /** Bytes do estado completo recebidos do pedal, preservados para regravacao fiel. */
    val rawState: ByteArray = ByteArray(0),
    /** Modo operacional do pedal: AB (2 slots) ou STOMP (3 slots). */
    val pedalMode: PedalMode = PedalMode.AB,
    /** true = cab/IR em bypass; false = cab/IR ativo. */
    val cabSimBypass: Boolean = false,
    /**
     * Campo bypass_mode (rawState[27], APK: GlobalSettings.bypass_mode).
     * false = sinal ativo (pedal processa o audio); true = bypass (sinal passa direto).
     */
    val bypassMode: Boolean = false,
    /**
     * TODOS os floats do bloco de parametros do detalhe 0x0304 do preset ATIVO, na ordem
     * da tabela tonex_params. Necessario para os controles de efeito com indice dinamico
     * por modelo (reverb/modulacao/delay). Vazio ate o pedal enviar o detalhe.
     */
    val presetParameters: List<Float> = emptyList()
) {
    fun withActiveSlot(slot: Slot): PedalState = copy(activeSlot = slot)

    fun withPresetIds(ids: List<Int>): PedalState = copy(presetIds = ids)

    fun withBypassMode(enabled: Boolean): PedalState {
        val updatedRawState = rawState.copyOf()
        val responseHeaderLength = 8
        val bypassModeEndOffset = 12
        val rawOffset = updatedRawState.size - bypassModeEndOffset
        if (updatedRawState.size > responseHeaderLength && rawOffset in updatedRawState.indices) {
            updatedRawState[rawOffset] = if (enabled) 1 else 0
        }
        return copy(bypassMode = enabled, rawState = updatedRawState)
    }

    fun withPedalMode(mode: PedalMode): PedalState {
        val updatedRawState = rawState.copyOf()
        val responseHeaderLength = 8
        val stompModeBodyOffset = 19
        val rawOffset = responseHeaderLength + stompModeBodyOffset
        if (rawOffset in updatedRawState.indices) {
            updatedRawState[rawOffset] = if (mode == PedalMode.STOMP) 1 else 0
        }
        return copy(pedalMode = mode, rawState = updatedRawState)
    }

    fun withCabSimBypass(enabled: Boolean): PedalState {
        val updatedRawState = rawState.copyOf()
        val responseHeaderLength = 8
        val cabSimBypassBodyOffset = 20
        val rawOffset = responseHeaderLength + cabSimBypassBodyOffset
        if (rawOffset in updatedRawState.indices) {
            updatedRawState[rawOffset] = if (enabled) 1 else 0
        }
        return copy(cabSimBypass = enabled, rawState = updatedRawState)
    }

    fun withPresetInSlot(presetId: Int, slot: Slot, selectSlot: Boolean): PedalState {
        val updatedRawState = rawState.copyOf()
        val responseHeaderLength = 8
        val bodyLength = updatedRawState.size - responseHeaderLength
        fun bodyEndOffset(endOffset: Int): Int = responseHeaderLength + bodyLength - endOffset
        if (bodyLength > 0) {
            val modeOffset = responseHeaderLength + 19
            if (modeOffset in updatedRawState.indices) {
                updatedRawState[modeOffset] = if (slot == Slot.C) 1 else 0
            }
            val presetOffset = bodyEndOffset(
                when (slot) {
                    Slot.A -> 18
                    Slot.B -> 16
                    Slot.C -> 14
                }
            )
            if (presetOffset in updatedRawState.indices) {
                updatedRawState[presetOffset] = presetId.toByte()
                if (presetOffset + 1 in updatedRawState.indices) updatedRawState[presetOffset + 1] = 0
            }
            if (selectSlot) {
                val currentSlotOffset = bodyEndOffset(11)
                if (currentSlotOffset in updatedRawState.indices) {
                    updatedRawState[currentSlotOffset] = slot.ordinal.toByte()
                }
            }
            val directMonitorOffset = bodyEndOffset(7)
            if (directMonitorOffset in updatedRawState.indices) updatedRawState[directMonitorOffset] = 1
            val bypassOffset = bodyEndOffset(12)
            if (bypassOffset in updatedRawState.indices) updatedRawState[bypassOffset] = 0
        }
        val ids = presetIds.toMutableList()
        while (ids.size < Slot.entries.size) ids.add(0)
        ids[slot.ordinal] = presetId
        val presetName = libraryPresets.getOrNull(presetId)?.name ?: "Preset ${presetId + 1}"
        val updatedSlots = slots.mapIndexed { index, preset ->
            if (index == slot.ordinal) preset.copy(name = presetName) else preset
        }
        return copy(
            activeSlot = if (selectSlot) slot else activeSlot,
            slots = updatedSlots,
            presetIds = ids,
            rawState = updatedRawState,
            pedalMode = if (slot == Slot.C) PedalMode.STOMP else pedalMode,
            bypassMode = false
        )
    }

    /**
     * Aplica ao preset ATIVO os floats de parametros extraidos do detalhe 0x0304
     * ([values] indexado pela ordem da tabela tonex_params). So os parametros mapeados
     * em [TonexParam] entram no mapa; os demais indices sao preservados para o futuro.
     */
    fun withActivePresetParameters(values: List<Float>): PedalState {
        if (values.isEmpty()) return this
        val slotIndex = activeSlot.ordinal
        if (slotIndex !in slots.indices) return this
        val mapped = TonexParam.entries.mapNotNull { param ->
            values.getOrNull(param.index)?.let { value ->
                param.parameterId to Parameter(
                    id = param.parameterId,
                    label = param.name,
                    type = ParamType.FLOAT,
                    value = value,
                    min = param.min,
                    max = param.max
                )
            }
        }.toMap()
        if (mapped.isEmpty()) return this
        val updatedSlots = slots.mapIndexed { index, preset ->
            if (index == slotIndex) preset.copy(parameters = preset.parameters + mapped) else preset
        }
        return copy(slots = updatedSlots, presetParameters = values)
    }

    /** Valor real do parametro [param] no preset ativo, ou null se ainda nao recebido. */
    fun parameterValue(param: TonexParam): Float? = presetParameters.getOrNull(param.index)

    /** Modelos de amp/cab do preset ativo, derivados do detalhe 0x0304 (ver [RigModels]). */
    fun rigModels(): RigModels = RigModels(
        ampEnabled = parameterValue(TonexParam.MODEL_AMP_ENABLE)?.let { it >= 0.5f },
        cabinetType = parameterValue(TonexParam.CABINET_TYPE)?.let { raw ->
            CabinetType.entries.getOrNull(raw.toInt().coerceIn(0, 2))
        },
        virCabinetModel = parameterValue(TonexParam.VIR_CABINET_MODEL)?.toInt()
    )

    /**
     * Write-through local de um parametro escrito no pedal via 0x0309: o pedal NAO reenvia
     * o detalhe 0x0304 apos a escrita, entao sem atualizar a copia local o proximo re-sync
     * restauraria o valor antigo na UI (toggles de efeito voltavam sozinhos).
     */
    fun withParameterValue(index: Int, value: Float): PedalState {
        if (index !in presetParameters.indices) return this
        val updatedParams = presetParameters.toMutableList().also { it[index] = value }
        val param = TonexParam.fromIndex(index)
        val slotIndex = activeSlot.ordinal
        val updatedSlots = if (param != null && slotIndex in slots.indices) {
            slots.mapIndexed { i, preset ->
                if (i == slotIndex) {
                    preset.copy(
                        parameters = preset.parameters + (param.parameterId to Parameter(
                            id = param.parameterId,
                            label = param.name,
                            type = ParamType.FLOAT,
                            value = value,
                            min = param.min,
                            max = param.max
                        ))
                    )
                } else preset
            }
        } else slots
        return copy(presetParameters = updatedParams, slots = updatedSlots)
    }

    fun withActivePresetName(name: String): PedalState {
        val slotIndex = activeSlot.ordinal
        if (slotIndex !in slots.indices) return this
        val updatedSlots = slots.mapIndexed { index, preset ->
            if (index == slotIndex) preset.copy(name = name) else preset
        }
        return copy(slots = updatedSlots)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PedalState) return false
        return activeSlot == other.activeSlot &&
            inputTrim == other.inputTrim &&
            a4Reference == other.a4Reference &&
            tempo == other.tempo &&
            slots == other.slots &&
            libraryPresets == other.libraryPresets &&
            presetIds == other.presetIds &&
            pedalMode == other.pedalMode &&
            cabSimBypass == other.cabSimBypass &&
            bypassMode == other.bypassMode &&
            presetParameters == other.presetParameters &&
            rawState.contentEquals(other.rawState)
    }

    override fun hashCode(): Int {
        var result = activeSlot.hashCode()
        result = 31 * result + inputTrim.hashCode()
        result = 31 * result + a4Reference
        result = 31 * result + tempo
        result = 31 * result + slots.hashCode()
        result = 31 * result + libraryPresets.hashCode()
        result = 31 * result + presetIds.hashCode()
        result = 31 * result + pedalMode.hashCode()
        result = 31 * result + cabSimBypass.hashCode()
        result = 31 * result + bypassMode.hashCode()
        result = 31 * result + presetParameters.hashCode()
        result = 31 * result + rawState.contentHashCode()
        return result
    }
}

data class FirmwareInfo(val version: String, val serialNumber: String? = null)
