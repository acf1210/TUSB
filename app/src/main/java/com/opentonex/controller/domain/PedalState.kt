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

data class PedalState(
    val activeSlot: Slot,
    val inputTrim: Float,
    val a4Reference: Int,
    val tempo: Int,
    val slots: List<PresetSlot>,
    /** ID do preset (na biblioteca do pedal) atribuido a cada slot, na ordem A, B, C. */
    val presetIds: List<Int> = emptyList(),
    /** Bytes do estado completo recebidos do pedal, preservados para regravacao fiel. */
    val rawState: ByteArray = ByteArray(0),
    /** Modo operacional do pedal: AB (2 slots) ou STOMP (3 slots). */
    val pedalMode: PedalMode = PedalMode.AB
) {
    fun withActiveSlot(slot: Slot): PedalState = copy(activeSlot = slot)

    fun withPresetIds(ids: List<Int>): PedalState = copy(presetIds = ids)

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
            presetIds == other.presetIds &&
            rawState.contentEquals(other.rawState)
    }

    override fun hashCode(): Int {
        var result = activeSlot.hashCode()
        result = 31 * result + inputTrim.hashCode()
        result = 31 * result + a4Reference
        result = 31 * result + tempo
        result = 31 * result + slots.hashCode()
        result = 31 * result + presetIds.hashCode()
        result = 31 * result + rawState.contentHashCode()
        return result
    }
}

data class FirmwareInfo(val version: String)
