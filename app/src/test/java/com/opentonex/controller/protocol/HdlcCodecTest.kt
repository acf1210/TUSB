package com.opentonex.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HdlcCodecTest {
    @Test fun `encode wraps with flags and appends crc little-endian`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val crc = Crc16Ccitt.compute(payload)
        val expected = byteArrayOf(
            0x7E, 0x01, 0x02, 0x03,
            (crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(),
            0x7E
        )
        assertArrayEquals(expected, HdlcCodec.encode(payload))
    }

    @Test fun `encode stuffs flag bytes in payload`() {
        val payload = byteArrayOf(0x7E, 0x7D)
        val out = HdlcCodec.encode(payload)
        assertArrayEquals(byteArrayOf(0x7E), byteArrayOf(out.first()))
        assertArrayEquals(byteArrayOf(0x7E), byteArrayOf(out.last()))
        // 0x7E -> 0x7D 0x5E ; 0x7D -> 0x7D 0x5D
        assertArrayEquals(
            byteArrayOf(0x7D, 0x5E, 0x7D, 0x5D),
            out.copyOfRange(1, 5)
        )
    }
}
