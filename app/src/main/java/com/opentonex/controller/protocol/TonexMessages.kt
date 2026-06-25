package com.opentonex.controller.protocol

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

/** Erro ao decodificar um StateResponse cujos bytes nao casam com o formato esperado. */
class PedalStateParseException(message: String) : Exception(message)

object TonexMessages {
    /** Tipo de mensagem (offsets 3-4, u16 LE) do StateResponse real do pedal. */
    const val STATE_RESPONSE_TYPE = 0x0306

    /**
     * Le o tipo de mensagem (header `B9 03 81 [2 bytes LE]`) de uma resposta decodificada.
     * O pedal intercala notificacoes assincronas (ex: medidor de nivel) com as respostas
     * aos comandos enviados - este tipo permite distinguir uma da outra.
     */
    fun messageType(payload: ByteArray): Int {
        if (payload.size < 5) {
            throw PedalStateParseException("payload curto demais (${payload.size}B) para ter tipo de mensagem")
        }
        return (payload[3].toInt() and 0xFF) or ((payload[4].toInt() and 0xFF) shl 8)
    }

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

    // --- StateResponse: offsets calibrados contra captura real do pedal
    // (ToneX One V1, Fase 2 Tarefa 8). Cores usam escape de bit alto: valores
    // >= 0x80 vem prefixados com 0x80 + o valor real; valores < 0x80 sao crus. ---

    private const val RGB_COLLECTION_TAG = 0xBA
    private const val SLOT_COLLECTION_TAG = 0xBC
    private const val COLOR_ITEM_TAG = 0xB9
    private const val ESCAPE_PREFIX = 0x80

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
        offset += 3 // cabSimBypass + tuningMode + campo desconhecido (bytes crus, ainda nao usados na UI)

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
            if ((payload[offset].toInt() and 0xFF) != COLOR_ITEM_TAG) {
                throw PedalStateParseException(
                    "esperava item de cor (0x${COLOR_ITEM_TAG.toString(16)}) no offset $offset"
                )
            }
            offset++
            val componentCount = payload[offset].toInt() and 0xFF
            offset++
            val components = IntArray(componentCount)
            for (c in 0 until componentCount) {
                val (value, nextOffset) = decodeColorComponent(payload, offset)
                components[c] = value
                offset = nextOffset
            }
            colors.add(Rgb(r = components[0], g = components[1], b = components[2]))
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
        offset += 1 // campo desconhecido entre o slot ativo e o A4

        val a4 = TaggedValue.decodeU16(payload, offset); offset = a4.nextOffset
        offset += 1 // directMonitor (byte cru, ainda nao usado na UI)
        offset += 1 // tempoSource (byte cru, ainda nao usado na UI)

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

    /** Decodifica um componente de cor (R, G ou B) com escape de bit alto. */
    private fun decodeColorComponent(payload: ByteArray, offset: Int): Pair<Int, Int> {
        val raw = payload[offset].toInt() and 0xFF
        return if (raw == ESCAPE_PREFIX) {
            (payload[offset + 1].toInt() and 0xFF) to (offset + 2)
        } else {
            raw to (offset + 1)
        }
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
