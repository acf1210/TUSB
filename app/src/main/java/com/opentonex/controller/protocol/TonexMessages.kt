package com.opentonex.controller.protocol

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.LibraryPreset
import com.opentonex.controller.domain.PedalMode
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

    /** Mensagem inicial de handshake. Bytes refinados contra captura real na Fase 2. */
    fun helloPayload(): ByteArray = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x03, 0x00)

    /**
     * RequestState canonico dos firmwares de referencia (identico em `usb_tonex_one.c`
     * do Builty/TonexOneController e `tonex.cpp` do vit3k/tonex_controller — duas fontes
     * independentes). NAO esta ligado ao fluxo de conexao: o [helloPayload] de 5B ja foi
     * validado no hardware fisico e tambem devolve o estado 0x0306. Mantido aqui como
     * alternativa pronta para teste A/B com o pedal, caso o de 5B falhe em algum firmware.
     * Ver docs/protocol-notes.md (validacao cruzada).
     */
    fun requestStatePayload(): ByteArray = byteArrayOf(
        0xB9.toByte(), 0x03, 0x00, 0x82.toByte(), 0x06, 0x00,
        0x80.toByte(), 0x0B, 0x03, 0xB9.toByte(), 0x02, 0x81.toByte(), 0x06, 0x03, 0x0B
    )

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
     * Comando cru de troca de preset usado pelo firmware USB de referencia
     * (`usb_tonex_one.c`). Ele trafega sem framing HDLC.
     */
    fun rawPresetSelectPayload(presetId: Int): ByteArray {
        require(presetId in 0..0xFF) { "presetId fora de 0..255: $presetId" }
        return byteArrayOf(0xF0.toByte(), presetId.toByte(), 0xF7.toByte(), 0x05, 0x00, 0x01)
    }

    /**
     * Comando curto observado no app oficial entre fases da troca.
     * Ainda nao sabemos o nome semantico real; preservamos os bytes exatamente
     * como apareceram na captura para o teste no hardware fisico.
     */
    fun presetBridgePayload(stage: Int): ByteArray {
        require(stage in 0..0xFF) { "stage fora de 0..255: $stage" }
        return PRESET_BRIDGE_PREFIX + byteArrayOf(stage.toByte(), 0x03, 0x0B)
    }

    // --- Parametros de preset (tipo 0x0309). Layout confirmado por DUAS fontes: o firmware
    // Builty/usb_tonex_one.c (usb_tonex_one_send_single_parameter) e a captura real de knob
    // fisico deste projeto (tonex-session-1782930773375.jsonl: indice 0x15=21=volume, floats
    // 0..10). Payload: `B9 04 02 00 <indice> 88 <float LE 4B>`. Ver docs/protocol-notes.md. ---

    /** Tipo das mensagens de parametro: notificacao de knob fisico E escrita de parametro. */
    const val PARAM_CHANGE_TYPE = 0x0309

    /** Um parametro de preset: indice na tabela tonex_params + valor float real do pedal. */
    data class ParameterChange(val index: Int, val value: Float)

    /** Prefixo do payload de parametro, comum a notificacao e escrita. */
    private val PARAM_PAYLOAD_PREFIX = byteArrayOf(0xB9.toByte(), 0x04, 0x02, 0x00)

    /** Marcador que antecede todo float de 4B LE no protocolo do pedal. */
    private const val FLOAT_MARKER = 0x88

    /**
     * Header do comando de escrita de parametro unico: tipo 0x0309 + sufixo de comando
     * com tamanho fixo 0x000A (10B de payload). Nao reenvia o preset inteiro.
     */
    private val SET_PARAM_HEADER = byteArrayOf(
        0xB9.toByte(), 0x03, 0x81.toByte(), 0x09, 0x03,
        0x82.toByte(), 0x0A, 0x00, 0x80.toByte(), 0x0B, 0x03
    )

    /** Marcador do inicio do bloco de floats de parametros no detalhe de preset 0x0304. */
    private val PARAM_BLOCK_MARKER = byteArrayOf(0xBA.toByte(), 0x03, 0xBA.toByte(), 0x6D)

    /** Monta o comando (sem framing HDLC) que escreve UM parametro no preset ativo. */
    fun buildSetParameterPayload(index: Int, value: Float): ByteArray {
        require(index in 0..0xFF) { "indice de parametro fora de 0..255: $index" }
        return SET_PARAM_HEADER + PARAM_PAYLOAD_PREFIX +
            byteArrayOf(index.toByte(), FLOAT_MARKER.toByte()) + floatToLeBytes(value)
    }

    /**
     * Decodifica uma mensagem 0x0309 (knob fisico girado no pedal) em [ParameterChange].
     * Retorna null se o payload nao for 0x0309 ou nao contiver o padrao esperado.
     */
    fun parseParameterChange(payload: ByteArray): ParameterChange? {
        if (payload.size < 5 || messageType(payload) != PARAM_CHANGE_TYPE) return null
        val start = indexOfSequence(payload, PARAM_PAYLOAD_PREFIX) ?: return null
        val indexPos = start + PARAM_PAYLOAD_PREFIX.size
        if (indexPos + 6 > payload.size) return null // indice + marcador + 4B float
        if ((payload[indexPos + 1].toInt() and 0xFF) != FLOAT_MARKER) return null
        return ParameterChange(
            index = payload[indexPos].toInt() and 0xFF,
            value = floatFromLeBytes(payload, indexPos + 2)
        )
    }

    /**
     * Extrai o bloco de floats de parametros do detalhe de preset 0x0304, na ordem da
     * tabela tonex_params. Lista vazia se o marcador nao existir no payload.
     */
    fun parsePresetParameters(payload: ByteArray): List<Float> {
        val start = indexOfSequence(payload, PARAM_BLOCK_MARKER) ?: return emptyList()
        val values = ArrayList<Float>()
        var offset = start + PARAM_BLOCK_MARKER.size
        while (offset + 5 <= payload.size && (payload[offset].toInt() and 0xFF) == FLOAT_MARKER) {
            values.add(floatFromLeBytes(payload, offset + 1))
            offset += 5
        }
        return values
    }

    private fun floatToLeBytes(value: Float): ByteArray {
        val bits = value.toRawBits()
        return byteArrayOf(
            bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte()
        )
    }

    private fun floatFromLeBytes(payload: ByteArray, offset: Int): Float {
        val bits = (payload[offset].toInt() and 0xFF) or
            ((payload[offset + 1].toInt() and 0xFF) shl 8) or
            ((payload[offset + 2].toInt() and 0xFF) shl 16) or
            ((payload[offset + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    private fun indexOfSequence(haystack: ByteArray, needle: ByteArray): Int? {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
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
        // 1a escolha: padrao de versao semantica (ex.: "1.3.16"), como o app oficial exibe.
        val semverRegex = Regex("""\d+\.\d+(\.\d+)?""")
        val semver = runs.asSequence()
            .mapNotNull { run -> semverRegex.find(run)?.value }
            .maxByOrNull { it.length }
        if (semver != null) return FirmwareInfo(version = semver)
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
    private fun setStateCommandSuffix(bodyLength: Int): ByteArray {
        require(bodyLength in 0..0xFFFF) { "StateData grande demais: ${bodyLength}B" }
        return byteArrayOf(
            0x82.toByte(),
            (bodyLength and 0xFF).toByte(),
            ((bodyLength shr 8) and 0xFF).toByte(),
            0x80.toByte(),
            0x0B,
            0x03
        )
    }

    /**
     * Regrava o estado completo mudando somente o byte do slot ativo, e troca o header
     * de RESPOSTA pelo header de COMANDO real do app oficial (mesmo corpo, envelope
     * diferente). [activeSlotOffset] e relativo a [rawState] (inclui o header de resposta).
     */
    // Offsets contados do FIM do StateData (= body sem o header de 8B), confirmados via
    // firmware ESP32 de referencia: usb_tonex_one.c (set_preset_in_slot / set_active_slot).
    private const val STOMP_MODE_BODY_OFFSET = 19 // StateData[19]: 0=A/B, 1=Stomp
    private const val CAB_SIM_BYPASS_BODY_OFFSET = 20 // StateData[20]: 0=IR/Cab on, 1=bypass
    private const val DIRECT_MONITOR_END_OFFSET = 7   // StateData[len-7]: 0=mute, 1=on
    private const val BYPASS_MODE_END_OFFSET    = 12  // StateData[len-12]: 0=active, 1=bypass
    private const val CURRENT_SLOT_END_OFFSET   = 11
    private const val SLOT_C_PRESET_END_OFFSET  = 14
    private const val SLOT_B_PRESET_END_OFFSET  = 16
    private const val SLOT_A_PRESET_END_OFFSET  = 18

    /**
     * Aplica [mutateBody] ao corpo do StateData (apos o header de resposta de 8B), fixa o campo
     * "direct monitor ON" (comum a todo comando de escrita de estado) e remonta o comando
     * completo com o header/sufixo de COMANDO (ver [setStateCommandSuffix]).
     */
    private fun rebuildStateCommand(rawState: ByteArray, mutateBody: (ByteArray) -> Unit): ByteArray {
        val typeHeader = rawState.copyOfRange(0, 5)
        val body = rawState.copyOfRange(STATE_RESPONSE_HEADER_LENGTH, rawState.size)
        mutateBody(body)
        // direct monitor ON: sem isso o pedal muta o audio quando conectado via USB
        if (body.size > DIRECT_MONITOR_END_OFFSET) body[body.size - DIRECT_MONITOR_END_OFFSET] = 1
        return typeHeader + setStateCommandSuffix(body.size) + body
    }

    fun buildSlotChangePayload(rawState: ByteArray, activeSlotOffset: Int, newSlotValue: Int, bypass: Boolean = false): ByteArray {
        require(activeSlotOffset in rawState.indices) { "offset de slot fora do estado" }
        require(rawState.size > STATE_RESPONSE_HEADER_LENGTH) { "rawState curto demais para conter o header de resposta" }
        return rebuildStateCommand(rawState) { body ->
            body[activeSlotOffset - STATE_RESPONSE_HEADER_LENGTH] = newSlotValue.toByte()
            if (body.size > BYPASS_MODE_END_OFFSET) body[body.size - BYPASS_MODE_END_OFFSET] = if (bypass) 1 else 0
        }
    }

    /**
     * Monta o payload para ligar/desligar o bypass do pedal, sem alterar slot ativo.
     * [bypass] = true → bypass ON (sinal passa sem processamento).
     * [bypass] = false → bypass OFF (processamento normal).
     */
    fun buildSetBypassPayload(rawState: ByteArray, fieldsOffset: Int, bypass: Boolean): ByteArray {
        val slotOff = activeSlotOffset(rawState, fieldsOffset)
        val currentSlotByte = rawState[slotOff].toInt() and 0xFF
        return buildSlotChangePayload(rawState, slotOff, currentSlotByte, bypass)
    }

    /**
     * Monta o payload para alternar o modo global do ToneX One: A/B (0) ou Stomp (1).
     * O slot ativo acompanha o modo (Stomp <=> slot C, como no firmware de referencia
     * Builty, onde set_preset_in_slot amarra stomp_mode ao slot C): sem alinhar o slot,
     * o pedal recebia stomp_mode=1 com slot A/B e ignorava a troca.
     */
    fun buildSwitchModePayload(rawState: ByteArray, targetMode: PedalMode): ByteArray {
        require(rawState.size > STATE_RESPONSE_HEADER_LENGTH + STOMP_MODE_BODY_OFFSET) {
            "rawState curto demais para conter stomp_mode"
        }
        return rebuildStateCommand(rawState) { body ->
            body[STOMP_MODE_BODY_OFFSET] = if (targetMode == PedalMode.STOMP) 1 else 0
            val slotOffset = body.size - CURRENT_SLOT_END_OFFSET
            if (slotOffset in body.indices) {
                val currentSlot = body[slotOffset].toInt() and 0xFF
                body[slotOffset] = when {
                    targetMode == PedalMode.STOMP -> slotToByte(Slot.C).toByte()
                    currentSlot == slotToByte(Slot.C) -> slotToByte(Slot.A).toByte()
                    else -> currentSlot.toByte()
                }
            }
        }
    }

    /** Monta o payload para ligar/desligar o bypass do Cab Sim / IR. */
    fun buildSetCabSimBypassPayload(rawState: ByteArray, bypass: Boolean): ByteArray {
        require(rawState.size > STATE_RESPONSE_HEADER_LENGTH + CAB_SIM_BYPASS_BODY_OFFSET) {
            "rawState curto demais para conter cab_sim_bypass"
        }
        return rebuildStateCommand(rawState) { body ->
            body[CAB_SIM_BYPASS_BODY_OFFSET] = if (bypass) 1 else 0
        }
    }

    /**
     * Carrega [presetId] em [slot] regravando o StateData do ToneX One. Espelha
     * `usb_tonex_one_set_preset_in_slot` do controlador de referencia.
     */
    fun buildLoadPresetToSlotPayload(
        rawState: ByteArray,
        presetId: Int,
        slot: Slot,
        selectSlot: Boolean
    ): ByteArray {
        require(presetId in 0 until 20) { "presetId fora de 0..19: $presetId" }
        require(rawState.size > STATE_RESPONSE_HEADER_LENGTH) { "rawState curto demais para conter StateData" }
        return rebuildStateCommand(rawState) { body ->
            body[STOMP_MODE_BODY_OFFSET] = if (slot == Slot.C) 1 else 0
            if (body.size > BYPASS_MODE_END_OFFSET) body[body.size - BYPASS_MODE_END_OFFSET] = 0

            val presetOffset = body.size - slotPresetEndOffset(slot)
            require(presetOffset in body.indices) { "offset de preset fora do StateData" }
            body[presetOffset] = presetId.toByte()
            if (presetOffset + 1 in body.indices) body[presetOffset + 1] = 0

            if (selectSlot) {
                val activeSlotOffset = body.size - CURRENT_SLOT_END_OFFSET
                require(activeSlotOffset in body.indices) { "offset de slot ativo fora do StateData" }
                body[activeSlotOffset] = slotToByte(slot).toByte()
            }
        }
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
        val libraryPresets = walk.colors.take(20).mapIndexed { index, color ->
            LibraryPreset(
                index = index,
                name = "Preset ${(index + 1).toString().padStart(2, '0')}",
                color = color
            )
        }
        return PedalState(
            activeSlot = slotFromByte(walk.activeSlotByte),
            inputTrim = walk.inputTrim,
            a4Reference = walk.a4Reference,
            tempo = walk.tempoBpm.toInt(),
            slots = slots,
            libraryPresets = libraryPresets,
            rawState = payload,
            presetIds = walk.presetIds,
            pedalMode = if (walk.stompMode) PedalMode.STOMP else PedalMode.AB,
            cabSimBypass = walk.cabSimBypass,
            bypassMode = walk.bypassMode
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
        val presetIds: List<Int>,
        val stompMode: Boolean,
        val cabSimBypass: Boolean,
        val bypassMode: Boolean = false
    )

    private fun walkFields(payload: ByteArray, fieldsOffset: Int): FieldsWalk {
        var offset = fieldsOffset
        val trim = TaggedValue.decodeFloat(payload, offset); offset = trim.nextOffset
        val stompMode = (payload[offset].toInt() and 0xFF) != 0
        val cabSimBypass = (payload[offset + 1].toInt() and 0xFF) != 0
        offset += 3 // stompMode + cabSimBypass + tuningMode (bytes crus; so stompMode e usado)

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

        // bypass_mode: body[len-12], confirmado contra firmware ESP32 (usb_tonex_one.c).
        // 0 = sinal ativo, 1 = bypass (sinal passa direto sem processamento).
        val body = payload.copyOfRange(STATE_RESPONSE_HEADER_LENGTH, payload.size)
        val bypassMode = if (body.size > BYPASS_MODE_END_OFFSET) {
            (body[body.size - BYPASS_MODE_END_OFFSET].toInt() and 0xFF) != 0
        } else false

        return FieldsWalk(
            inputTrim = trim.value,
            colors = colors,
            activeSlotByte = activeSlotByte,
            activeSlotOffset = activeSlotOffset,
            a4Reference = a4.value,
            tempoBpm = tempo.value,
            presetIds = presetIds,
            stompMode = stompMode,
            cabSimBypass = cabSimBypass,
            bypassMode = bypassMode
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

    private fun slotPresetEndOffset(slot: Slot): Int = when (slot) {
        Slot.A -> SLOT_A_PRESET_END_OFFSET
        Slot.B -> SLOT_B_PRESET_END_OFFSET
        Slot.C -> SLOT_C_PRESET_END_OFFSET
    }

    /** Detecta o modo do pedal a partir do StateResponse parseado. */
    fun detectMode(state: PedalState): PedalMode = state.pedalMode
}
