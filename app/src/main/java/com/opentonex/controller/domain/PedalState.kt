package com.opentonex.controller.domain

enum class Slot { A, B, C }

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
    /** Bytes do estado completo recebidos do pedal, preservados para regravacao fiel. */
    val rawState: ByteArray = ByteArray(0)
) {
    fun withActiveSlot(slot: Slot): PedalState = copy(activeSlot = slot)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PedalState) return false
        return activeSlot == other.activeSlot &&
            inputTrim == other.inputTrim &&
            a4Reference == other.a4Reference &&
            tempo == other.tempo &&
            slots == other.slots &&
            rawState.contentEquals(other.rawState)
    }

    override fun hashCode(): Int {
        var result = activeSlot.hashCode()
        result = 31 * result + inputTrim.hashCode()
        result = 31 * result + a4Reference
        result = 31 * result + tempo
        result = 31 * result + slots.hashCode()
        result = 31 * result + rawState.contentHashCode()
        return result
    }
}

data class FirmwareInfo(val version: String)
