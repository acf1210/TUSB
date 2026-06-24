package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TonexMessages

class PedalProtocolException(message: String) : Exception(message)

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

    override suspend fun requestState(): PedalState =
        TonexMessages.parseState(roundTrip(TonexMessages.requestStatePayload()), fieldsOffset)

    override suspend fun writeState(state: PedalState) {
        val payload = TonexMessages.buildSetStatePayload(state.rawState, fieldsOffset, state.activeSlot)
        transport.write(HdlcCodec.encode(payload))
    }

    override suspend fun disconnect() {
        transport.close()
    }

    private suspend fun roundTrip(payload: ByteArray): ByteArray {
        transport.write(HdlcCodec.encode(payload))
        val frame = transport.readFrame(RESPONSE_TIMEOUT_MS)
        return when (val decoded = HdlcCodec.decode(frame)) {
            is HdlcFrame.Valid -> decoded.payload
            HdlcFrame.CrcError -> throw PedalProtocolException("CRC invalido na resposta do pedal")
            HdlcFrame.Incomplete -> throw PedalProtocolException("frame incompleto recebido do pedal")
        }
    }

    companion object {
        /** Offset estimado do 1o campo do StateResponse - ver nota de calibracao no topo do plano. */
        const val STATE_FIELDS_OFFSET = 13
        const val RESPONSE_TIMEOUT_MS = 2000L
    }
}
