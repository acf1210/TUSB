package com.opentonex.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TaggedValueTest {
    @Test fun `encode 2-byte number is tag then little-endian`() {
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x06, 0x00),
            TaggedValue.encodeU16(0x0006, tag = 0x81)
        )
    }

    @Test fun `decode 2-byte number reads little-endian`() {
        val r = TaggedValue.decodeU16(byteArrayOf(0x81.toByte(), 0x06, 0x00), offset = 0)
        assertEquals(0x0006, r.value)
        assertEquals(3, r.nextOffset)
    }

    @Test fun `encode then decode float round-trips`() {
        val encoded = TaggedValue.encodeFloat(0.75f)
        assertEquals(0x88, encoded[0].toInt() and 0xFF)
        val r = TaggedValue.decodeFloat(encoded, offset = 0)
        assertEquals(0.75f, r.value, 0.0f)
        assertEquals(5, r.nextOffset)
    }
}
