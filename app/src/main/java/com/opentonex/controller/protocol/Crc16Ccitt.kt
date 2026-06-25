package com.opentonex.controller.protocol

/**
 * CRC-16/X-25 (poli reverso 0x8408, init 0xFFFF, XOR final 0xFFFF) - o CRC
 * real usado pelo framing HDLC do pedal (confirmado via captura de hardware
 * real na Fase 2; nao e o CRC-16/XModem usado por engano na Fase 1).
 */
object Crc16Ccitt {
    private const val POLY = 0x8408
    private const val INIT = 0xFFFF
    private const val XOROUT = 0xFFFF

    fun compute(data: ByteArray): Int {
        var crc = INIT
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x0001 != 0) (crc ushr 1) xor POLY else crc ushr 1
            }
        }
        return (crc xor XOROUT) and 0xFFFF
    }
}
