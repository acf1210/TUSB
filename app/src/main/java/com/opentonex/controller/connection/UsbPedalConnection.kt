package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
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
        val wake = TonexMessages.wakePayload()
        val hello = TonexMessages.helloPayload()
        var lastError: Exception? = null
        repeat(HANDSHAKE_ATTEMPTS) { attempt ->
            try {
                // Fallback: se ainda assim falhar, reabrir a porta antes de tentar de novo.
                if (attempt > 0) {
                    runCatching { transport.close() }
                    transport.open()
                }
                // 1. ACORDA o pedal com o comando de init do app oficial. Sem isto a serial do
                //    pedal fica dormente e ignora o Hello (causa raiz da conexao lenta).
                emitRequestEvent(requestKind = "wake", payload = wake)
                roundTripExpecting(wake, TonexMessages.WAKE_RESPONSE_TYPE, HANDSHAKE_ATTEMPT_TIMEOUT_MS)
                // 2. Ja acordado, o Hello responde com um UNICO frame de estado (0x0306) que ja
                //    carrega firmware + estado - nao e preciso requestState() separado.
                emitRequestEvent(requestKind = "hello", payload = hello)
                val response = roundTripExpecting(
                    hello, TonexMessages.STATE_RESPONSE_TYPE, HANDSHAKE_ATTEMPT_TIMEOUT_MS
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
                return Handshake(firmware = firmware, state = state)
            } catch (e: PedalTransportTimeoutException) {
                lastError = e
                emitEvent(
                    PedalRuntimeEvent.TransportError(
                        errorMessage = "Hello sem resposta (tentativa ${attempt + 1}/$HANDSHAKE_ATTEMPTS)"
                    )
                )
                if (attempt < HANDSHAKE_ATTEMPTS - 1) delay(HANDSHAKE_RETRY_DELAY_MS)
            } catch (e: java.io.IOException) {
                // Falha ao reabrir a porta: trata como tentativa perdida e tenta de novo.
                lastError = e
                if (attempt < HANDSHAKE_ATTEMPTS - 1) delay(HANDSHAKE_RETRY_DELAY_MS)
            }
        }
        throw PedalProtocolException(
            "pedal nao respondeu ao Hello apos $HANDSHAKE_ATTEMPTS tentativas",
            lastError
        )
    }
    override suspend fun requestState(): PedalState {
        val requestPayload = TonexMessages.requestStatePayload()
        emitRequestEvent(requestKind = "request_state", payload = requestPayload)
        val payload = roundTripExpecting(requestPayload, TonexMessages.STATE_RESPONSE_TYPE)
        return decodeStateFromResponse(payload)
    }

    /**
     * Decodifica um frame 0x0306 em [PedalState], emitindo [PedalRuntimeEvent.StateReceived]
     * em caso de sucesso ou [PedalRuntimeEvent.TransportError] em caso de falha. Usado tanto
     * pelo [handshake] quanto pelo [requestState], que recebem o mesmo tipo de frame.
     */
    private fun decodeStateFromResponse(payload: ByteArray): PedalState {
        return try {
            val parsed = TonexMessages.parseState(payload, fieldsOffset)
            val enriched = lastPresetNameFromNotification?.let(parsed::withActivePresetName) ?: parsed
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
        for (payload in TonexMessages.selectPresetPayloads(presetId)) {
            val frame = HdlcCodec.encode(payload)
            android.util.Log.d("ToneXConn", "  -> frame(${frame.size}B)=${frame.joinToString(" ") { "%02X".format(it) }}")
            emitRequestEvent(requestKind = "select_preset", payload = payload)
            try {
                transport.write(frame)
                android.util.Log.d("ToneXConn", "  -> write OK")
            } catch (e: Exception) {
                android.util.Log.e("ToneXConn", "  -> write FAILED: ${e.message}", e)
            }
            delay(PRESET_COMMAND_STEP_DELAY_MS)
        }
    }

    override suspend fun disconnect() {
        transport.close()
        emitEvent(PedalRuntimeEvent.Disconnected)
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
                TonexMessages.PRESET_DETAIL_TYPE -> {
                    lastPresetNameFromNotification = TonexMessages.parsePresetNameFromDetail(decoded)
                    lastPresetNameFromNotification?.let { presetName ->
                        emitEvent(
                            PedalRuntimeEvent.PresetDetailReceived(
                                name = presetName,
                                messageType = TonexMessages.PRESET_DETAIL_TYPE,
                                payloadHex = decoded.toHex()
                            )
                        )
                    }
                }
            }
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
    }
}
