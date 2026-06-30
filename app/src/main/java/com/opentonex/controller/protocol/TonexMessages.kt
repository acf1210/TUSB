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
    const val PRESET_DETAIL_TYPE = 0x0304

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

    /** Tipo da resposta do pedal ao comando de wake (visto na captura: `B9 03 02 2B 0B ...`). */
    const val WAKE_RESPONSE_TYPE = 0x0B2B

    /**
     * Comando de "acordar"/init que o app OFICIAL envia como PRIMEIRO comando ao conectar
     * (captura tonex_full_session.pcap, OUT #0). Sem ele, a interface serial do pedal fica
     * dormente e ignora o Hello ate o footswitch ser pressionado fisicamente - era a causa
     * raiz da "conexao lenta". O pedal responde com um frame tipo 0x0B2B. Ver protocol-notes.md.
     */
    fun wakePayload(): ByteArray = byteArrayOf(
        0xB9.toByte(), 0x03, 0x00, 0x82.toByte(), 0x04, 0x00,
        0x80.toByte(), 0x0B, 0x01, 0xB9.toByte(), 0x02, 0x02, 0x0B
    )

    // --- Comando de troca de preset (host->device), tipo 0x0300. Descoberto via captura
    // USB do app oficial (PC) trocando preset - ver docs/protocol-notes.md, Bug 2. ---

    /** Tipo de mensagem do comando de troca de preset (header `B9 03 81 00 03`). */
    const val PRESET_SELECT_TYPE = 0x0300

    /** Fase "arm/preview": o app oficial envia esta antes de efetivar. */
    const val PRESET_PHASE_ARM = 0x00

    /** Fase "commit/load": efetiva a troca. O app envia ARM e depois COMMIT. */
    const val PRESET_PHASE_COMMIT = 0x01

    /** Comando curto intercalado pelo app oficial entre ARM e COMMIT. */
    const val PRESET_BRIDGE_STAGE = 0x0A

    /** Variante curta observada ao final de algumas trocas no app oficial. */
    const val PRESET_SETTLE_STAGE = 0x01

    /**
     * Envelope constante observado em TODOS os comandos de troca da captura, ate o byte
     * imediatamente antes do par [presetId, phase]:
     * `B9 03 81 00 03 | 82 06 00 | 80 0B 03 | B9 04 | 0B 01`.
     */
    private val PRESET_SELECT_PREFIX = byteArrayOf(
        0xB9.toByte(), 0x03, 0x81.toByte(), 0x00, 0x03,
        0x82.toByte(), 0x06, 0x00,
        0x80.toByte(), 0x0B, 0x03,
        0xB9.toByte(), 0x04,
        0x0B, 0x01
    )

    private val PRESET_BRIDGE_PREFIX = byteArrayOf(
        0xB9.toByte(), 0x03, 0x00, 0x82.toByte(), 0x06, 0x00,
        0x80.toByte(), 0x0B, 0x03, 0xB9.toByte(), 0x02, 0x81.toByte()
    )

    /**
     * Monta o payload (sem framing HDLC) de UMA fase do comando de troca de preset.
     * [presetId] e o ID do preset na biblioteca do pedal (ex.: 0x0C, 0x08, 0x07 na captura);
     * o mapeamento slot A/B/C -> id vem da colecao de slots do StateResponse 0x0306.
     */
    fun selectPresetPayload(presetId: Int, phase: Int): ByteArray {
        require(presetId in 0..0xFF) { "presetId fora de 0..255: $presetId" }
        require(phase == PRESET_PHASE_ARM || phase == PRESET_PHASE_COMMIT) {
            "fase invalida (esperado ARM=0x00 ou COMMIT=0x01): 0x${phase.toString(16)}"
        }
        return PRESET_SELECT_PREFIX + byteArrayOf(presetId.toByte(), phase.toByte())
    }

    /**
     * As duas fases do comando de troca, na ordem que o app oficial envia (ARM -> COMMIT).
     * Cada elemento deve ser encapsulado com [HdlcCodec.encode] e escrito em sequencia.
     */
    fun selectPresetPayloads(presetId: Int): List<ByteArray> = listOf(
        selectPresetPayload(presetId, PRESET_PHASE_ARM),
        selectPresetPayload(presetId, PRESET_PHASE_COMMIT)
    )

    /**
     * Comando curto observado no app oficial entre fases da troca.
     * Ainda nao sabemos o nome semantico real; preservamos os bytes exatamente
     * como apareceram na captura para o teste no hardware fisico.
     */
    fun presetBridgePayload(stage: Int): ByteArray {
        require(stage in 0..0xFF) { "stage fora de 0..255: $stage" }
        return PRESET_BRIDGE_PREFIX + byteArrayOf(stage.toByte(), 0x03, 0x0B)
    }

    /**
     * Extrai a versao da resposta de Hello. O pedal devolve um dump de estado binario
     * (tipo 0x0306, nao uma string limpa - ver docs/protocol-notes.md). Pegamos a maior
     * sequencia CONTIGUA de ASCII imprimivel com >= 3 chars e ao menos um digito (versoes
     * tem digitos), evitando concatenar bytes binarios dispersos como lixo ("LBLG@//...").
     */
    fun parseFirmware(response: ByteArray): FirmwareInfo {
        val runs = mutableListOf<String>()
        val current = StringBuilder()
        for (b in response) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) {
                current.append(c.toChar())
            } else if (current.isNotEmpty()) {
                runs.add(current.toString()); current.clear()
            }
        }
        if (current.isNotEmpty()) runs.add(current.toString())
        val version = runs
            .map { it.trim() }
            .filter { it.length >= 3 && it.any(Char::isDigit) }
            .maxByOrNull { it.length }
        return FirmwareInfo(version = version ?: "ToneX One (versao nao mapeada)")
    }

    /**
     * Tamanho do header de uma resposta StateResponse (`B9 03 81 06 03 80 A0 02`) antes
     * do corpo dos campos. Descoberto via captura do app oficial trocando preset
     * (2026-06-25, tonex_isolated_switch.pcap): o COMANDO de troca de slot e o MESMO
     * corpo, com um header de comando diferente (ver [SET_STATE_COMMAND_SUFFIX]).
     */
    private const val STATE_RESPONSE_HEADER_LENGTH = 8

    /**
     * Sufixo do header do COMANDO de troca de estado (apos o tipo `B9 03 81 06 03`),
     * substituindo o sufixo `80 A0 02` usado nas RESPOSTAS. Bytes exatos da captura real.
     */
    private val SET_STATE_COMMAND_SUFFIX = byteArrayOf(
        0x82.toByte(), 0xA0.toByte(), 0x00, 0x80.toByte(), 0x0B, 0x03
    )

    /**
     * Regrava o estado completo mudando somente o byte do slot ativo, e troca o header
     * de RESPOSTA pelo header de COMANDO real do app oficial (mesmo corpo, envelope
     * diferente). [activeSlotOffset] e relativo a [rawState] (inclui o header de resposta).
     */
    fun buildSlotChangePayload(rawState: ByteArray, activeSlotOffset: Int, newSlotValue: Int): ByteArray {
        require(activeSlotOffset in rawState.indices) { "offset de slot fora do estado" }
        require(rawState.size > STATE_RESPONSE_HEADER_LENGTH) { "rawState curto demais para conter o header de resposta" }
        val typeHeader = rawState.copyOfRange(0, 5)
        val body = rawState.copyOfRange(STATE_RESPONSE_HEADER_LENGTH, rawState.size)
        body[activeSlotOffset - STATE_RESPONSE_HEADER_LENGTH] = newSlotValue.toByte()
        return typeHeader + SET_STATE_COMMAND_SUFFIX + body
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
            rawState = payload,
            presetIds = walk.presetIds
        )
    }

    /** ID do preset (na biblioteca do pedal) atribuido ao [slot], lido do ultimo [PedalState]. */
    fun presetIdForSlot(state: PedalState, slot: Slot): Int? = state.presetIds.getOrNull(slotToByte(slot))

    /**
     * Extrai o nome do preset a partir da notificacao 0x0304 enviada pelo pedal quando
     * o preset ativo muda. O nome aparece como `BC <len> <ascii...>`.
     */
    fun parsePresetNameFromDetail(payload: ByteArray): String? {
        if (messageType(payload) != PRESET_DETAIL_TYPE) return null
        for (index in 0 until payload.size - 2) {
            if ((payload[index].toInt() and 0xFF) != SLOT_COLLECTION_TAG) continue
            val nameLength = payload[index + 1].toInt() and 0xFF
            val nameStart = index + 2
            val nameEnd = nameStart + nameLength
            if (nameLength == 0 || nameEnd > payload.size) continue
            val bytes = payload.copyOfRange(nameStart, nameEnd)
            if (bytes.any { (it.toInt() and 0xFF) !in 0x20..0x7E }) continue
            return bytes.toString(Charsets.US_ASCII).trim().takeIf { it.isNotEmpty() }
        }
        return null
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
        val tempoBpm: Float,
        val presetIds: List<Int>
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
        if (slotBytesCount % 2 != 0) {
            throw PedalStateParseException("colecao de slots com quantidade impar de bytes: $slotBytesCount")
        }
        // bytes de slot assignment: presetId (u16 LE) por slot - mapeia slot A/B/C -> id
        // na biblioteca do pedal. Usado para montar o comando de troca (ver selectPresetPayload).
        val presetIds = ArrayList<Int>(slotBytesCount / 2)
        repeat(slotBytesCount / 2) {
            presetIds.add((payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8))
            offset += 2
        }

        offset += 1 // byte observado como constante 0x00 nas capturas reais
        val activeSlotOffset = offset
        val activeSlotByte = payload[offset].toInt() and 0xFF
        offset++

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
            tempoBpm = tempo.value,
            presetIds = presetIds
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
