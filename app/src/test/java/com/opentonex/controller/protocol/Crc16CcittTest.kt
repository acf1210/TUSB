package com.opentonex.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16CcittTest {
    @Test fun `crc of empty is zero`() {
        assertEquals(0x0000, Crc16Ccitt.compute(byteArrayOf()))
    }

    @Test fun `crc of ASCII 123456789 matches XModem vector`() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x31C3, Crc16Ccitt.compute(data))
    }

    @Test fun `crc of single byte A`() {
        assertEquals(0x58E5, Crc16Ccitt.compute(byteArrayOf(0x41)))
    }
}
