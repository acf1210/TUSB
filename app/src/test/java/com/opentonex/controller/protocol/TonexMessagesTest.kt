package com.opentonex.controller.protocol

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
