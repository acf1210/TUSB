package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TonexMessages

class PedalProtocolException(message: String) : Exception(message)

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** Implementacao real de [PedalConnection], falando HDLC/ToneX sobre um [PedalTransport]. */
class UsbPedalConnection(
    private val transport: PedalTransport,
    private val fieldsOffset: Int = STATE_FIELDS_OFFSET
) : PedalConnection {

    override suspend fun connect() {
        transport.open()
    }

    override suspend fun sendHello(): FirmwareInfo =
        TonexMessages.parseFirmware(roundTrip(TonexMessages.helloPayload()))

    override suspend fun requestState(): PedalState {
        val payload = roundTripExpecting(TonexMessages.requestStatePayload(), TonexMessages.STATE_RESPONSE_TYPE)
        return try {
            TonexMessages.parseState(payload, fieldsOffset)
        } catch (e: Exception) {
            throw PedalProtocolException(
                "falha ao decodificar StateResponse (${e.javaClass.simpleName}: ${e.message}) | payload (${payload.size}B): ${payload.toHex()}"
            )
        }
    }

    override suspend fun writeState(state: PedalState) {
        val payload = TonexMessages.buildSetStatePayload(state.rawState, fieldsOffset, state.activeSlot)
        transport.write(HdlcCodec.encode(payload))
    }

    override suspend fun disconnect() {
        transport.close()
    }

    private suspend fun roundTrip(payload: ByteArray): ByteArray {
        transport.write(HdlcCodec.encode(payload))
        return decodeFrame(transport.readFrame(RESPONSE_TIMEOUT_MS))
    }

    /**
     * Igual a [roundTrip], mas descarta notificacoes assincronas do pedal (ex: medidor
     * de nivel, tipo diferente do esperado) ate achar a resposta correta ou esgotar o
     * timeout total.
     */
    private suspend fun roundTripExpecting(payload: ByteArray, expectedType: Int): ByteArray {
        transport.write(HdlcCodec.encode(payload))
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw PedalProtocolException(
                    "sem resposta do tipo 0x${expectedType.toString(16)} dentro do timeout"
                )
            }
            val decoded = decodeFrame(transport.readFrame(remaining))
            if (TonexMessages.messageType(decoded) == expectedType) return decoded
        }
    }

    private fun decodeFrame(frame: ByteArray): ByteArray =
        when (val decoded = HdlcCodec.decode(frame)) {
            is HdlcFrame.Valid -> decoded.payload
            HdlcFrame.CrcError -> throw PedalProtocolException(
                "CRC invalido na resposta do pedal | frame: ${frame.toHex()}"
            )
            HdlcFrame.Incomplete -> throw PedalProtocolException(
                "frame incompleto recebido do pedal | frame: ${frame.toHex()}"
            )
        }

    companion object {
        /** Offset do 1o campo do StateResponse, calibrado contra captura real do pedal (Fase 2, Tarefa 8). */
        const val STATE_FIELDS_OFFSET = 22
        const val RESPONSE_TIMEOUT_MS = 2000L
    }
}
