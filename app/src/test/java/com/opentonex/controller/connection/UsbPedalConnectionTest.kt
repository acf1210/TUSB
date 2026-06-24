package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TaggedValue
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePedalTransport : PedalTransport {
    val written = mutableListOf<ByteArray>()
    var nextFrame: ByteArray = ByteArray(0)
    var opened = false
    var closed = false

    override suspend fun open() { opened = true }
    override suspend fun write(bytes: ByteArray) { written.add(bytes) }
    override suspend fun readFrame(timeoutMs: Long): ByteArray = nextFrame
    override suspend fun close() { closed = true }
}

private fun syntheticStatePayload(activeSlotByte: Byte): ByteArray {
    val header = ByteArray(UsbPedalConnection.STATE_FIELDS_OFFSET)
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00)
    val colors = byteArrayOf(
        0xBA.toByte(), 3,
        255.toByte(), 0, 0,
        0, 255.toByte(), 0,
        0, 0, 255.toByte()
    )
    val slotAssignment = byteArrayOf(0xBC.toByte(), 6, 0, 0, 0, 0, 0, 0)
    val a4 = TaggedValue.encodeU16(440, tag = 0x81)
    val directMonitor = byteArrayOf(0)
    val tempo = TaggedValue.encodeFloat(120.0f)
    return header + trim + flags + colors + slotAssignment +
        byteArrayOf(activeSlotByte) + a4 + directMonitor + tempo
}

class UsbPedalConnectionTest {
    @Test fun `connect opens the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.connect()

        assertTrue(transport.opened)
    }

    @Test fun `sendHello writes encoded hello and parses firmware from the response`() = runTest {
        val transport = FakePedalTransport()
        val responsePayload = byteArrayOf(0x81.toByte(), 0x0A, 0x00) + "1.2.3".toByteArray(Charsets.US_ASCII)
        transport.nextFrame = HdlcCodec.encode(responsePayload)
        val connection = UsbPedalConnection(transport)

        val firmware = connection.sendHello()

        assertEquals("1.2.3", firmware.version)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.helloPayload()), transport.written.single())
    }

    @Test fun `requestState decodes pedal state from the response frame`() = runTest {
        val transport = FakePedalTransport()
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        assertEquals(Slot.B, state.activeSlot)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.requestStatePayload()), transport.written.single())
    }

    @Test fun `writeState sends the mutated raw bytes back through the transport`() = runTest {
        val transport = FakePedalTransport()
        val statePayload = syntheticStatePayload(activeSlotByte = 1)
        val connection = UsbPedalConnection(transport)
        val state = TonexMessages.parseState(statePayload, fieldsOffset = UsbPedalConnection.STATE_FIELDS_OFFSET)
            .withActiveSlot(Slot.C)

        connection.writeState(state)

        val sentFrame = transport.written.single()
        val decoded = HdlcCodec.decode(sentFrame) as HdlcFrame.Valid
        val resultState = TonexMessages.parseState(decoded.payload, fieldsOffset = UsbPedalConnection.STATE_FIELDS_OFFSET)
        assertEquals(Slot.C, resultState.activeSlot)
    }

    @Test fun `disconnect closes the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.disconnect()

        assertTrue(transport.closed)
    }
}
