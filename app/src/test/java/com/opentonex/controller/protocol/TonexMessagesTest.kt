package com.opentonex.controller.protocol

import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TonexMessagesTest {
    @Test fun `requestState payload starts with documented header`() {
        val payload = TonexMessages.requestStatePayload()
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x06, 0x03), payload.copyOfRange(0, 3))
    }

    @Test fun `hello payload is non-empty`() {
        org.junit.Assert.assertTrue(TonexMessages.helloPayload().isNotEmpty())
    }

    @Test fun `parse firmware reads ascii version from response`() {
        val resp = byteArrayOf(0x81.toByte(), 0x0A, 0x00) +
            "1.2.3".toByteArray(Charsets.US_ASCII)
        assertEquals("1.2.3", TonexMessages.parseFirmware(resp).version)
    }

    @Test fun `slot change preserves all bytes except active slot byte`() {
        val raw = byteArrayOf(0x10, 0x20, 0x00 /*slot byte @2*/, 0x30, 0x40)
        val out = TonexMessages.buildSlotChangePayload(
            rawState = raw, activeSlotOffset = 2, newSlotValue = 2
        )
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x02, 0x30, 0x40), out)
    }
}

private fun syntheticStatePayload(activeSlotByte: Byte = 1): ByteArray {
    val header = ByteArray(13) // header bruto do StateResponse, ignorado pelo parser
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00) // cabSimBypass=on, tuningMode=mute
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

class TonexMessagesStateTest {
    @Test fun `parseState decodes documented fields from synthetic payload`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val state = TonexMessages.parseState(payload, fieldsOffset = 13)

        assertEquals(1.5f, state.inputTrim)
        assertEquals(Slot.B, state.activeSlot)
        assertEquals(440, state.a4Reference)
        assertEquals(120, state.tempo)
        assertEquals(3, state.slots.size)
        assertEquals(Rgb(255, 0, 0), state.slots[0].color)
        assertEquals(Rgb(0, 255, 0), state.slots[1].color)
        assertEquals(Rgb(0, 0, 255), state.slots[2].color)
        assertArrayEquals(payload, state.rawState)
    }

    @Test fun `buildSetStatePayload mutates only the active slot byte`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val updated = TonexMessages.buildSetStatePayload(payload, fieldsOffset = 13, newSlot = Slot.C)

        val expected = payload.copyOf()
        val activeSlotOffset = TonexMessages.activeSlotOffset(payload, fieldsOffset = 13)
        expected[activeSlotOffset] = 2
        assertArrayEquals(expected, updated)
    }
}
