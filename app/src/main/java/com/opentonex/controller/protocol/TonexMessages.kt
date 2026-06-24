package com.opentonex.controller.protocol

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

/** Erro ao decodificar um StateResponse cujos bytes nao casam com o formato esperado. */
class PedalStateParseException(message: String) : Exception(message)

object TonexMessages {
    /** Header documentado do request de estado: 0x81 0x06 0x03. */
    fun requestStatePayload(): ByteArray = byteArrayOf(0x81.toByte(), 0x06, 0x03)

    /** Mensagem inicial de handshake. Bytes refinados contra captura real na Fase 2. */
    fun helloPayload(): ByteArray = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x03, 0x00)

    /** Extrai a versao ASCII imprimivel da resposta de Hello. */
    fun parseFirmware(response: ByteArray): FirmwareInfo {
        val version = response
            .filter { it.toInt() in 0x20..0x7E }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
        return FirmwareInfo(version = version.ifEmpty { "desconhecida" })
    }

    /**
     * Regrava o estado completo mudando somente o byte do slot ativo.
     * Preserva todos os demais bytes (campos ainda nao decifrados).
     */
    fun buildSlotChangePayload(rawState: ByteArray, activeSlotOffset: Int, newSlotValue: Int): ByteArray {
        require(activeSlotOffset in rawState.indices) { "offset de slot fora do estado" }
        val copy = rawState.copyOf()
        copy[activeSlotOffset] = newSlotValue.toByte()
        return copy
    }

    // --- StateResponse: offsets best-effort (engenharia reversa de terceiros,
    // nao verificados contra o pedal real ainda - calibrar na Fase 2, Tarefa 8) ---

    private const val RGB_COLLECTION_TAG = 0xBA
    private const val SLOT_COLLECTION_TAG = 0xBC

    /**
     * Decodifica os campos conhecidos do StateResponse a partir de [fieldsOffset]
     * (posicao do primeiro byte de tag do campo "input trim"). Sempre preserva
     * [payload] completo em [PedalState.rawState], mesmo que os offsets estejam errados.
     */
    fun parseState(payload: ByteArray, fieldsOffset: Int): PedalState {
        val walk = walkFields(payload, fieldsOffset)
        val slots = walk.colors.take(3).mapIndexed { index, color ->
            PresetSlot(
                index = index,
                name = "Preset ${('A' + index)}",
                color = color
            )
        }
        return PedalState(
            activeSlot = slotFromByte(walk.activeSlotByte),
            inputTrim = walk.inputTrim,
            a4Reference = walk.a4Reference,
            tempo = walk.tempoBpm.toInt(),
            slots = slots,
            rawState = payload
        )
    }

    /** Offset absoluto, dentro de [rawState], do byte de slot ativo. */
    fun activeSlotOffset(rawState: ByteArray, fieldsOffset: Int): Int =
        walkFields(rawState, fieldsOffset).activeSlotOffset

    /** Regrava o estado mudando so o byte de slot ativo, preservando todo o resto. */
    fun buildSetStatePayload(
        rawState: ByteArray,
        fieldsOffset: Int,
        newSlot: Slot
    ): ByteArray = buildSlotChangePayload(
        rawState = rawState,
        activeSlotOffset = activeSlotOffset(rawState, fieldsOffset),
        newSlotValue = slotToByte(newSlot)
    )

    private class FieldsWalk(
        val inputTrim: Float,
        val colors: List<Rgb>,
        val activeSlotByte: Int,
        val activeSlotOffset: Int,
        val a4Reference: Int,
        val tempoBpm: Float
    )

    private fun walkFields(payload: ByteArray, fieldsOffset: Int): FieldsWalk {
        var offset = fieldsOffset
        val trim = TaggedValue.decodeFloat(payload, offset); offset = trim.nextOffset
        offset += 2 // cabSimBypass + tuningMode (bytes crus, ainda nao usados na UI)

        if ((payload[offset].toInt() and 0xFF) != RGB_COLLECTION_TAG) {
            throw PedalStateParseException(
                "esperava colecao RGB (0x${RGB_COLLECTION_TAG.toString(16)}) no offset $offset"
            )
        }
        offset++
        val colorCount = payload[offset].toInt() and 0xFF
        offset++
        val colors = ArrayList<Rgb>(colorCount)
        repeat(colorCount) {
            colors.add(
                Rgb(
                    r = payload[offset].toInt() and 0xFF,
                    g = payload[offset + 1].toInt() and 0xFF,
                    b = payload[offset + 2].toInt() and 0xFF
                )
            )
            offset += 3
        }

        if ((payload[offset].toInt() and 0xFF) != SLOT_COLLECTION_TAG) {
            throw PedalStateParseException(
                "esperava colecao de slots (0x${SLOT_COLLECTION_TAG.toString(16)}) no offset $offset"
            )
        }
        offset++
        val slotBytesCount = payload[offset].toInt() and 0xFF
        offset++
        offset += slotBytesCount // bytes de slot assignment, ainda nao usados na UI

        val activeSlotOffset = offset
        val activeSlotByte = payload[offset].toInt() and 0xFF
        offset++

        val a4 = TaggedValue.decodeU16(payload, offset); offset = a4.nextOffset
        offset += 1 // directMonitor (byte cru, ainda nao usado na UI)

        val tempo = TaggedValue.decodeFloat(payload, offset)

        return FieldsWalk(
            inputTrim = trim.value,
            colors = colors,
            activeSlotByte = activeSlotByte,
            activeSlotOffset = activeSlotOffset,
            a4Reference = a4.value,
            tempoBpm = tempo.value
        )
    }

    private fun slotFromByte(value: Int): Slot = when (value) {
        0 -> Slot.A
        1 -> Slot.B
        else -> Slot.C
    }

    fun slotToByte(slot: Slot): Int = when (slot) {
        Slot.A -> 0
        Slot.B -> 1
        Slot.C -> 2
    }
}
