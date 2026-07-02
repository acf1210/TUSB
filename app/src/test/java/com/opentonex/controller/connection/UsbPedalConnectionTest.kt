package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TaggedValue
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePedalTransport : PedalTransport {
    val written = mutableListOf<ByteArray>()
    var nextFrame: ByteArray = ByteArray(0)
    val pendingFrames = mutableListOf<ByteArray>()
    var opened = false
    var closed = false
    /** Simula a porta "fria": as primeiras N leituras estouram timeout (pedal mudo). */
    var failReadsBeforeSuccess = 0
    private var failedReads = 0

    val directWritten = mutableListOf<ByteArray>()

    override suspend fun open() { opened = true }
    override suspend fun write(bytes: ByteArray) { written.add(bytes) }
    override suspend fun writeDirect(bytes: ByteArray) { directWritten.add(bytes) }
    override suspend fun readFrame(timeoutMs: Long): ByteArray {
        if (failedReads < failReadsBeforeSuccess) {
            failedReads++
            throw PedalTransportTimeoutException("sem resposta (mudo) na leitura $failedReads")
        }
        return if (pendingFrames.isNotEmpty()) pendingFrames.removeAt(0) else nextFrame
    }
    override suspend fun close() { closed = true }
}

/** Frame de resposta ao wake (tipo 0x0B2B), como visto na captura real do app oficial. */
private fun wakeResponseFrame(): ByteArray =
    HdlcCodec.encode(byteArrayOf(0xB9.toByte(), 0x03, 0x02, 0x2B, 0x0B, 0x00, 0x00))

private fun encodeColorItem(r: Int, g: Int, b: Int): ByteArray {
    fun component(v: Int): ByteArray =
        if (v >= 0x80) byteArrayOf(0x80.toByte(), v.toByte()) else byteArrayOf(v.toByte())
    return byteArrayOf(0xB9.toByte(), 3) + component(r) + component(g) + component(b)
}

private fun syntheticStatePayload(activeSlotByte: Byte): ByteArray {
    val headerWithType = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x06, 0x03)
    val header = headerWithType + ByteArray(UsbPedalConnection.STATE_FIELDS_OFFSET - headerWithType.size)
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00, 0x00)
    val colors = byteArrayOf(0xBA.toByte(), 3) +
        encodeColorItem(255, 0, 0) + encodeColorItem(0, 255, 0) + encodeColorItem(0, 0, 255)
    val slotAssignment = byteArrayOf(0xBC.toByte(), 6, 0x0C, 0x00, 0x08, 0x00, 0x07, 0x00)
    val preActiveSlotByte = byteArrayOf(0)
    val a4 = TaggedValue.encodeU16(440, tag = 0x81)
    val directMonitor = byteArrayOf(0)
    val tempoSource = byteArrayOf(0)
    val tempo = TaggedValue.encodeFloat(120.0f)
    return header + trim + flags + colors + slotAssignment +
        preActiveSlotByte + byteArrayOf(activeSlotByte) + a4 + directMonitor + tempoSource + tempo
}

class UsbPedalConnectionTest {
    @Test fun `connect opens the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.connect()

        assertTrue(transport.opened)
    }

    @Test fun `handshake wakes the pedal then reads state from the hello response`() = runTest {
        val transport = FakePedalTransport()
        // Sequencia real: wake -> resposta 0x0B2B; Hello -> resposta 0x0306 com o estado.
        transport.pendingFrames.add(wakeResponseFrame())
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val handshake = connection.handshake()

        assertEquals(Slot.B, handshake.state.activeSlot)
        assertTrue(handshake.firmware.version.isNotBlank())
        // Dois writes: o wake primeiro (acorda o pedal), depois o Hello.
        assertEquals(2, transport.written.size)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.wakePayload()), transport.written[0])
        assertArrayEquals(HdlcCodec.encode(TonexMessages.helloPayload()), transport.written[1])
    }

    @Test fun `handshake retries the wake on a cold port until the pedal responds`() = runTest {
        val transport = FakePedalTransport()
        transport.failReadsBeforeSuccess = 2 // porta fria: 2 wakes ignorados, o 3o acorda o pedal
        transport.pendingFrames.add(wakeResponseFrame())
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val handshake = connection.handshake()

        assertEquals(Slot.B, handshake.state.activeSlot)
        // 2 wakes mudos + (wake+hello que respondem) = 4 writes numa unica conexao.
        assertEquals(4, transport.written.size)
    }

    @Test fun `handshake gives up after the maximum number of attempts`() = runTest {
        val transport = FakePedalTransport()
        transport.failReadsBeforeSuccess = Int.MAX_VALUE // pedal nunca responde
        val connection = UsbPedalConnection(transport)

        try {
            connection.handshake()
            error("esperava PedalProtocolException")
        } catch (e: PedalProtocolException) {
            // Cada tentativa escreve so o wake (a leitura falha antes do Hello).
            assertEquals(UsbPedalConnection.HANDSHAKE_ATTEMPTS, transport.written.size)
        }
    }

    @Test fun `requestState decodes pedal state from the response frame`() = runTest {
        val transport = FakePedalTransport()
        transport.pendingFrames.add(wakeResponseFrame())
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        assertEquals(Slot.B, state.activeSlot)
        assertEquals(2, transport.written.size)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.wakePayload()), transport.written[0])
        assertArrayEquals(HdlcCodec.encode(TonexMessages.helloPayload()), transport.written[1])
    }

    @Test fun `writeState sends the rewritten state with the new active slot byte`() = runTest {
        val transport = FakePedalTransport()
        val statePayload = syntheticStatePayload(activeSlotByte = 1)
        val connection = UsbPedalConnection(transport)
        val state = TonexMessages.parseState(statePayload, fieldsOffset = UsbPedalConnection.STATE_FIELDS_OFFSET)
            .withActiveSlot(Slot.C)

        connection.writeState(state)

        val expected = TonexMessages.buildSetStatePayload(
            statePayload, UsbPedalConnection.STATE_FIELDS_OFFSET, Slot.C
        )
        assertArrayEquals(HdlcCodec.encode(expected), transport.written.single())
    }

    @Test fun `requestState ignores async notifications and waits for the StateResponse type`() = runTest {
        val transport = FakePedalTransport()
        val noisePayload = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x09, 0x03, 0x0A, 0x02)
        transport.pendingFrames.add(wakeResponseFrame())
        transport.pendingFrames.add(HdlcCodec.encode(noisePayload))
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        assertEquals(Slot.B, state.activeSlot)
    }

    @Test fun `requestState applies preset name learned from async 0304 notification`() = runTest {
        val transport = FakePedalTransport()
        val presetName = "John Mayer/NDSP Fat US Clean"
        val detailPayload = byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x04, 0x03,
            0x00, 0x00,
            0xBC.toByte(), presetName.length.toByte()
        ) + presetName.toByteArray(Charsets.US_ASCII)
        transport.pendingFrames.add(wakeResponseFrame())
        transport.pendingFrames.add(HdlcCodec.encode(detailPayload))
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        assertEquals(presetName, state.slots[Slot.B.ordinal].name)
    }

    @Test fun `readPassiveState decodes an unsolicited state frame without writing`() = runTest {
        val transport = FakePedalTransport()
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.readPassiveState()

        assertEquals(Slot.B, state?.activeSlot)
        assertTrue(transport.written.isEmpty())
    }

    @Test fun `selectPreset envia 6 bytes raw no CDC serial sem framing HDLC`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.selectPreset(0x0D)

        assertEquals(1, transport.written.size)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x0D.toByte(), 0xF7.toByte(), 0x05, 0x00, 0x01),
            transport.written[0]
        )
    }

    @Test fun `requestState applies preset parameters learned from async 0304 notification`() = runTest {
        val transport = FakePedalTransport()
        val presetName = "Fat US Clean"
        // Bloco de parametros: 22 floats 88 <LE> apos o marcador BA 03 BA 6D; indice 20
        // (MODEL_GAIN) = 5.0f, indice 21 (MODEL_VOLUME) = 8.2f.
        var paramBlock = byteArrayOf(0xBA.toByte(), 0x03, 0xBA.toByte(), 0x6D)
        repeat(22) { index ->
            val value = when (index) {
                20 -> 5.0f
                21 -> 8.2f
                else -> 0f
            }
            val bits = value.toRawBits()
            paramBlock += byteArrayOf(
                0x88.toByte(), bits.toByte(), (bits shr 8).toByte(),
                (bits shr 16).toByte(), (bits shr 24).toByte()
            )
        }
        val detailPayload = byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x04, 0x03, 0x00, 0x00,
            0xBC.toByte(), presetName.length.toByte()
        ) + presetName.toByteArray(Charsets.US_ASCII) + paramBlock
        transport.pendingFrames.add(wakeResponseFrame())
        transport.pendingFrames.add(HdlcCodec.encode(detailPayload))
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        val gain = state.slots[Slot.B.ordinal].parameters["ParameterXModelGain"]
        assertEquals(5.0f, gain?.value ?: Float.NaN, 0.0001f)
        val volume = state.slots[Slot.B.ordinal].parameters["ParameterXModelVolume"]
        assertEquals(8.2f, volume?.value ?: Float.NaN, 0.0001f)
    }

    @Test fun `writeParameter sends the framed single-parameter command`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.writeParameter(paramIndex = 20, value = 5.0f)

        val expected = TonexMessages.buildSetParameterPayload(index = 20, value = 5.0f)
        assertArrayEquals(HdlcCodec.encode(expected), transport.written.single())
    }

    @Test fun `readPassiveState decodes a physical knob notification without state change`() = runTest {
        val transport = FakePedalTransport()
        // Frame real de knob fisico (volume=8.2) capturado do pedal.
        transport.nextFrame = HdlcCodec.encode(
            byteArrayOf(
                0xB9.toByte(), 0x03, 0x81.toByte(), 0x09, 0x03, 0x0A, 0x02,
                0xB9.toByte(), 0x04, 0x02, 0x00, 0x15, 0x88.toByte(), 0x33, 0x33, 0x03, 0x41
            )
        )
        val connection = UsbPedalConnection(transport)
        val events = mutableListOf<PedalRuntimeEvent>()
        val collector = launch {
            connection.runtimeEvents.collect { events.add(it) }
        }
        kotlinx.coroutines.yield() // garante a inscricao antes da emissao (SharedFlow sem replay)

        val state = connection.readPassiveState()
        kotlinx.coroutines.yield()
        collector.cancel()

        assertEquals(null, state)
        val change = events.filterIsInstance<PedalRuntimeEvent.ParameterChanged>().single()
        assertEquals(21, change.paramIndex)
        assertEquals(8.2f, change.value, 0.0001f)
    }

    @Test fun `disconnect closes the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.disconnect()

        assertTrue(transport.closed)
    }
}
