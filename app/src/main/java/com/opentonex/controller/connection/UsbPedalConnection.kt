package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PedalProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** Implementacao real de [PedalConnection], falando HDLC/ToneX sobre um [PedalTransport]. */
class UsbPedalConnection(
    private val transport: PedalTransport,
    private val fieldsOffset: Int = STATE_FIELDS_OFFSET
) : PedalConnection {
    private val events = MutableSharedFlow<PedalRuntimeEvent>(extraBufferCapacity = 64)
    private var lastPresetNameFromNotification: String? = null
    private var lastPresetParamsFromNotification: List<Float>? = null

    override val runtimeEvents: Flow<PedalRuntimeEvent> = events.asSharedFlow()

    override suspend fun connect() {
        transport.open()
    }

    override suspend fun handshake(): Handshake {
        // Numa porta CDC-ACM recem-aberta o pedal costuma IGNORAR o primeiro Hello (porta
        // "fria"): a captura real mostrou ~9 Hellos sem resposta e o 10o respondendo em 24ms.
        // Em vez de falhar e obrigar o usuario a tocar "Conectar" varias vezes, reenviamos o
        // Hello internamente, com timeout curto por tentativa, ate o pedal responder.
        // Ver docs/protocol-notes.md (Bug 3 - conexao lenta).
        val response = retryWakeHelloSequence(
            attempts = HANDSHAKE_ATTEMPTS,
            attemptTimeoutMs = HANDSHAKE_ATTEMPT_TIMEOUT_MS,
            retryDelayMs = HANDSHAKE_RETRY_DELAY_MS,
            reopenPortOnFirstAttempt = false,
            timeoutMessage = { attempt -> "Hello sem resposta (tentativa ${attempt + 1}/$HANDSHAKE_ATTEMPTS)" },
            exhaustedMessage = "pedal nao respondeu ao Hello apos $HANDSHAKE_ATTEMPTS tentativas"
        )
        val firmware = TonexMessages.parseFirmware(response)
        emitEvent(
            PedalRuntimeEvent.HelloResponseReceived(
                firmwareVersion = firmware.version,
                messageType = TonexMessages.STATE_RESPONSE_TYPE,
                payloadHex = response.toHex()
            )
        )
        val state = decodeStateFromResponse(response)
        android.util.Log.d("ToneXConn", "handshake rawState(${response.size}B)=${response.joinToString(" ") { "%02X".format(it) }}")
        return Handshake(firmware = firmware, state = state)
    }

    override suspend fun requestState(): PedalState {
        val response = retryWakeHelloSequence(
            attempts = REQUEST_STATE_ATTEMPTS,
            attemptTimeoutMs = REQUEST_STATE_ATTEMPT_TIMEOUT_MS,
            retryDelayMs = REQUEST_STATE_RETRY_DELAY_MS,
            reopenPortOnFirstAttempt = true,
            timeoutMessage = { attempt -> "State sem resposta (tentativa ${attempt + 1}/$REQUEST_STATE_ATTEMPTS)" },
            exhaustedMessage = "pedal nao respondeu ao StateRequest apos $REQUEST_STATE_ATTEMPTS tentativas"
        )
        return decodeStateFromResponse(response)
    }

    /**
     * Envia wake+hello e retorna o payload de estado (0x0306), tentando ate [attempts] vezes.
     * Compartilhado por [handshake] e [requestState], que so diferem em reabrir a porta ja na
     * 1a tentativa ([reopenPortOnFirstAttempt]) e no que fazem com a resposta.
     */
    private suspend fun retryWakeHelloSequence(
        attempts: Int,
        attemptTimeoutMs: Long,
        retryDelayMs: Long,
        reopenPortOnFirstAttempt: Boolean,
        timeoutMessage: (attempt: Int) -> String,
        exhaustedMessage: String
    ): ByteArray {
        val wake = TonexMessages.wakePayload()
        val hello = TonexMessages.helloPayload()
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                // Fallback: se ainda assim falhar, reabrir a porta antes de tentar de novo.
                if (reopenPortOnFirstAttempt || attempt > 0) {
                    runCatching { transport.close() }
                    transport.open()
                }
                // 1. ACORDA o pedal com o comando de init do app oficial. Sem isto a serial do
                //    pedal fica dormente e ignora o Hello (causa raiz da conexao lenta).
                emitRequestEvent(requestKind = "wake", payload = wake)
                roundTripExpecting(wake, TonexMessages.WAKE_RESPONSE_TYPE, attemptTimeoutMs)
                // 2. Ja acordado, o Hello responde com um UNICO frame de estado (0x0306) que ja
                //    carrega firmware + estado - nao e preciso requestState() separado.
                emitRequestEvent(requestKind = "hello", payload = hello)
                return roundTripExpecting(hello, TonexMessages.STATE_RESPONSE_TYPE, attemptTimeoutMs)
            } catch (e: PedalTransportTimeoutException) {
                lastError = e
                emitEvent(PedalRuntimeEvent.TransportError(errorMessage = timeoutMessage(attempt)))
                if (attempt < attempts - 1) delay(retryDelayMs)
            } catch (e: PedalProtocolException) {
                lastError = e
                if (attempt < attempts - 1) delay(retryDelayMs)
            } catch (e: java.io.IOException) {
                // Falha ao reabrir a porta: trata como tentativa perdida e tenta de novo.
                lastError = e
                if (attempt < attempts - 1) delay(retryDelayMs)
            }
        }
        throw PedalProtocolException(exhaustedMessage, lastError)
    }

    /**
     * Decodifica um frame 0x0306 em [PedalState], emitindo [PedalRuntimeEvent.StateReceived]
     * em caso de sucesso ou [PedalRuntimeEvent.TransportError] em caso de falha. Usado tanto
     * pelo [handshake] quanto pelo [requestState], que recebem o mesmo tipo de frame.
     */
    private fun decodeStateFromResponse(payload: ByteArray): PedalState {
        return try {
            val parsed = TonexMessages.parseState(payload, fieldsOffset)
            val named = lastPresetNameFromNotification?.let(parsed::withActivePresetName) ?: parsed
            val enriched = lastPresetParamsFromNotification?.let(named::withActivePresetParameters) ?: named
            emitEvent(
                PedalRuntimeEvent.StateReceived(
                    state = enriched,
                    messageType = TonexMessages.STATE_RESPONSE_TYPE,
                    payloadHex = payload.toHex()
                )
            )
            enriched
        } catch (e: Exception) {
            emitEvent(
                PedalRuntimeEvent.TransportError(
                    errorMessage = "falha ao decodificar StateResponse: ${e.message}",
                    payloadHex = payload.toHex()
                )
            )
            throw PedalProtocolException(
                "falha ao decodificar StateResponse (${e.javaClass.simpleName}: ${e.message}) | payload (${payload.size}B): ${payload.toHex()}"
            )
        }
    }

    override suspend fun writeState(state: PedalState) {
        val payload = TonexMessages.buildSetStatePayload(state.rawState, fieldsOffset, state.activeSlot)
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d("ToneXConn", "writeState slot=${state.activeSlot} frame(${frame.size}B)=${frame.take(12).joinToString(" ") { "%02X".format(it) }}...")
        emitRequestEvent(requestKind = "write_state", payload = payload)
        try {
            transport.write(frame)
            android.util.Log.d("ToneXConn", "writeState write OK")
        } catch (e: Exception) {
            android.util.Log.e("ToneXConn", "writeState FAILED: ${e.message}", e)
        }
    }

    override suspend fun selectPreset(presetId: Int) {
        android.util.Log.d("ToneXConn", "selectPreset presetId=0x${presetId.toString(16)}")
        val payload = TonexMessages.rawPresetSelectPayload(presetId)
        android.util.Log.d("ToneXConn", "  -> raw(${payload.size}B)=${payload.joinToString(" ") { "%02X".format(it) }}")
        emitRequestEvent(requestKind = "select_preset_raw", payload = payload)
        try {
            transport.write(payload)
            android.util.Log.d("ToneXConn", "  -> write OK")
        } catch (e: Exception) {
            android.util.Log.e("ToneXConn", "  -> write FAILED: ${e.message}", e)
        }
    }

    override suspend fun loadPresetToSlot(
        currentState: PedalState,
        presetId: Int,
        slot: com.opentonex.controller.domain.Slot,
        selectSlot: Boolean
    ) {
        val payload = TonexMessages.buildLoadPresetToSlotPayload(
            rawState = currentState.rawState,
            presetId = presetId,
            slot = slot,
            selectSlot = selectSlot
        )
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d(
            "ToneXConn",
            "loadPresetToSlot preset=$presetId slot=$slot select=$selectSlot frame(${frame.size}B)=" +
                frame.take(12).joinToString(" ") { "%02X".format(it) } + "..."
        )
        emitRequestEvent(requestKind = "load_preset_to_slot", payload = payload)
        transport.write(frame)
    }

    override suspend fun switchMode(currentState: PedalState, targetMode: PedalMode) {
        val payload = TonexMessages.buildSwitchModePayload(currentState.rawState, targetMode)
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d("ToneXConn", "switchMode target=$targetMode frame(${frame.size}B)=${frame.take(12).joinToString(" ") { "%02X".format(it) }}...")
        emitRequestEvent(requestKind = "switch_mode", payload = payload)
        transport.write(frame)
    }

    suspend fun writeBypass(state: PedalState, bypass: Boolean) {
        val payload = TonexMessages.buildSetBypassPayload(state.rawState, fieldsOffset, bypass)
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d("ToneXConn", "writeBypass bypass=$bypass frame(${frame.size}B)=${frame.take(12).joinToString(" ") { "%02X".format(it) }}...")
        emitRequestEvent(requestKind = "write_bypass", payload = payload)
        try {
            transport.write(frame)
        } catch (e: Exception) {
            android.util.Log.e("ToneXConn", "writeBypass FAILED: ${e.message}", e)
        }
    }

    suspend fun writeCabSimBypass(state: PedalState, bypass: Boolean) {
        val payload = TonexMessages.buildSetCabSimBypassPayload(state.rawState, bypass)
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d("ToneXConn", "writeCabSimBypass bypass=$bypass frame(${frame.size}B)=${frame.take(12).joinToString(" ") { "%02X".format(it) }}...")
        emitRequestEvent(requestKind = "write_cab_sim_bypass", payload = payload)
        transport.write(frame)
    }

    override suspend fun writeParameter(paramIndex: Int, value: Float) {
        val payload = TonexMessages.buildSetParameterPayload(paramIndex, value)
        val frame = HdlcCodec.encode(payload)
        android.util.Log.d("ToneXConn", "writeParameter index=$paramIndex value=$value frame(${frame.size}B)")
        emitRequestEvent(requestKind = "write_parameter", payload = payload)
        transport.write(frame)
    }

    override suspend fun disconnect() {
        transport.close()
        emitEvent(PedalRuntimeEvent.Disconnected)
    }

    /** Resultado de uma leitura passiva: estado completo ou notificacao (0x0304/0x0309/etc). */
    private sealed interface PassiveFrame {
        data class State(val state: PedalState) : PassiveFrame
        data object Notification : PassiveFrame
    }

    private suspend fun readPassiveFrame(timeoutMs: Long): PassiveFrame? {
        val decoded = try {
            decodeFrame(transport.readFrame(timeoutMs))
        } catch (e: PedalTransportTimeoutException) {
            return null
        }
        val messageType = TonexMessages.messageType(decoded)
        android.util.Log.d("ToneXConn", "passiveFrame type=0x${messageType.toString(16)} (${decoded.size}B)")
        emitEvent(
            PedalRuntimeEvent.FrameReceived(
                messageType = messageType,
                payloadHex = decoded.toHex()
            )
        )
        return when (messageType) {
            TonexMessages.STATE_RESPONSE_TYPE -> PassiveFrame.State(decodeStateFromResponse(decoded))
            TonexMessages.PRESET_DETAIL_TYPE -> {
                emitPresetDetail(decoded)
                PassiveFrame.Notification
            }
            TonexMessages.PARAM_CHANGE_TYPE -> {
                emitParameterChange(decoded)
                PassiveFrame.Notification
            }
            else -> PassiveFrame.Notification
        }
    }

    suspend fun readPassiveState(timeoutMs: Long = PASSIVE_READ_TIMEOUT_MS): PedalState? =
        (readPassiveFrame(timeoutMs) as? PassiveFrame.State)?.state

    /**
     * Drena TODOS os frames pendentes na serial (ate [maxFrames]), nao apenas um. Essencial
     * durante rajadas: o knob fisico girando emite dezenas de 0x0309 entre dois polls; ler
     * um unico frame por poll deixava as notificacoes envelhecerem no buffer. Retorna o
     * ultimo StateResponse visto (ou null); as notificacoes viram eventos de runtime.
     */
    suspend fun drainPassiveFrames(maxFrames: Int = PASSIVE_DRAIN_MAX_FRAMES): PedalState? {
        var lastState: PedalState? = null
        var timeout = PASSIVE_READ_TIMEOUT_MS
        repeat(maxFrames) {
            val frame = try {
                readPassiveFrame(timeout)
            } catch (e: PedalProtocolException) {
                // Frame corrompido no meio da rajada: descarta e segue drenando.
                android.util.Log.w("ToneXConn", "drain: frame invalido descartado (${e.message})")
                PassiveFrame.Notification
            } ?: return lastState
            if (frame is PassiveFrame.State) lastState = frame.state
            // Apos o 1o frame, os demais ja estao no buffer: timeout curto.
            timeout = PASSIVE_DRAIN_NEXT_TIMEOUT_MS
        }
        return lastState
    }

    /**
     * Escreve [payload] e descarta notificacoes assincronas do pedal (ex: medidor de
     * nivel, tipo diferente do esperado) ate achar a resposta do tipo esperado ou
     * esgotar o timeout total.
     */
    private suspend fun roundTripExpecting(
        payload: ByteArray,
        expectedType: Int,
        timeoutMs: Long = RESPONSE_TIMEOUT_MS
    ): ByteArray {
        transport.write(HdlcCodec.encode(payload))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                emitEvent(
                    PedalRuntimeEvent.TransportError(
                        errorMessage = "sem resposta do tipo 0x${expectedType.toString(16)} dentro do timeout"
                    )
                )
                throw PedalProtocolException(
                    "sem resposta do tipo 0x${expectedType.toString(16)} dentro do timeout"
                )
            }
            val decoded = decodeFrame(transport.readFrame(remaining))
            when (TonexMessages.messageType(decoded)) {
                expectedType -> return decoded
                TonexMessages.PRESET_DETAIL_TYPE -> emitPresetDetail(decoded)
                TonexMessages.PARAM_CHANGE_TYPE -> emitParameterChange(decoded)
            }
        }
    }

    /** Decodifica uma notificacao 0x0309 (knob fisico) e a publica como evento de runtime. */
    private fun emitParameterChange(payload: ByteArray) {
        val change = TonexMessages.parseParameterChange(payload) ?: run {
            android.util.Log.w("ToneXConn", "paramChange nao parseado: ${payload.toHex()}")
            return
        }
        android.util.Log.d("ToneXConn", "paramChange index=${change.index} value=${change.value}")
        emitEvent(
            PedalRuntimeEvent.ParameterChanged(
                paramIndex = change.index,
                value = change.value,
                messageType = TonexMessages.PARAM_CHANGE_TYPE,
                payloadHex = payload.toHex()
            )
        )
    }

    private fun emitPresetDetail(payload: ByteArray) {
        lastPresetNameFromNotification = TonexMessages.parsePresetNameFromDetail(payload)
        val parameters = TonexMessages.parsePresetParameters(payload)
        if (parameters.isNotEmpty()) lastPresetParamsFromNotification = parameters
        lastPresetNameFromNotification?.let { presetName ->
            emitEvent(
                PedalRuntimeEvent.PresetDetailReceived(
                    name = presetName,
                    messageType = TonexMessages.PRESET_DETAIL_TYPE,
                    payloadHex = payload.toHex(),
                    parameters = parameters
                )
            )
        }
    }

    private fun decodeFrame(frame: ByteArray): ByteArray =
        when (val decoded = HdlcCodec.decode(frame)) {
            is HdlcFrame.Valid -> decoded.payload
            HdlcFrame.CrcError -> {
                emitEvent(
                    PedalRuntimeEvent.TransportError(
                        errorMessage = "CRC invalido na resposta do pedal",
                        payloadHex = frame.toHex()
                    )
                )
                throw PedalProtocolException(
                    "CRC invalido na resposta do pedal | frame: ${frame.toHex()}"
                )
            }
            HdlcFrame.Incomplete -> {
                emitEvent(
                    PedalRuntimeEvent.TransportError(
                        errorMessage = "frame incompleto recebido do pedal",
                        payloadHex = frame.toHex()
                    )
                )
                throw PedalProtocolException(
                    "frame incompleto recebido do pedal | frame: ${frame.toHex()}"
                )
            }
        }

    private suspend fun emitRequestEvent(requestKind: String, payload: ByteArray) {
        emitEvent(
            PedalRuntimeEvent.RequestSent(
                requestKind = requestKind,
                messageType = payload.messageTypeOrNull(),
                payloadHex = payload.toHex()
            )
        )
    }

    private fun emitEvent(event: PedalRuntimeEvent) {
        events.tryEmit(event)
    }

    private fun ByteArray.messageTypeOrNull(): Int? = runCatching {
        TonexMessages.messageType(this)
    }.getOrNull()

    companion object {
        /** Offset do 1o campo do StateResponse, calibrado contra captura real do pedal (Fase 2, Tarefa 8). */
        const val STATE_FIELDS_OFFSET = 22
        const val RESPONSE_TIMEOUT_MS = 2000L
        private const val PRESET_COMMAND_STEP_DELAY_MS = 120L

        /**
         * Tentativas de handshake (wake+hello) por toque em "Conectar". Comandos sao descartados
         * ~50% no nivel do USB (a 1a tentativa apos abrir quase sempre cai - porta "fria"), mas
         * quando wake+hello pegam, conecta na hora. Muitas tentativas internas fazem um unico
         * toque quase sempre acertar, em vez do usuario tocar varias vezes. Ver protocol-notes.md.
         */
        const val HANDSHAKE_ATTEMPTS = 30
        /** Timeout curto por tentativa de Hello: o pedal responde em ~24ms quando pega. */
        const val HANDSHAKE_ATTEMPT_TIMEOUT_MS = 400L
        private const val HANDSHAKE_RETRY_DELAY_MS = 60L
        private const val REQUEST_STATE_ATTEMPTS = 30
        private const val REQUEST_STATE_ATTEMPT_TIMEOUT_MS = 400L
        private const val REQUEST_STATE_RETRY_DELAY_MS = 60L
        private const val PASSIVE_READ_TIMEOUT_MS = 250L
        private const val PASSIVE_DRAIN_MAX_FRAMES = 64
        private const val PASSIVE_DRAIN_NEXT_TIMEOUT_MS = 60L
    }
}
