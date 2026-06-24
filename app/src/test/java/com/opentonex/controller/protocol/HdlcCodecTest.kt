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

    @Test fun `decode round-trips an encoded payload`() {
        val payload = byteArrayOf(0x10, 0x7E, 0x7D, 0x20)
        val encoded = HdlcCodec.encode(payload)
        val result = HdlcCodec.decode(encoded)
        org.junit.Assert.assertTrue(result is HdlcFrame.Valid)
        assertArrayEquals(payload, (result as HdlcFrame.Valid).payload)
    }

    @Test fun `decode reports crc error when crc bytes are corrupted`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = HdlcCodec.encode(payload).copyOf()
        encoded[encoded.size - 2] = (encoded[encoded.size - 2] + 1).toByte()
        org.junit.Assert.assertTrue(HdlcCodec.decode(encoded) is HdlcFrame.CrcError)
    }

    @Test fun `decode returns incomplete when no closing flag`() {
        val partial = byteArrayOf(0x7E, 0x01, 0x02)
        org.junit.Assert.assertTrue(HdlcCodec.decode(partial) is HdlcFrame.Incomplete)
    }
}
