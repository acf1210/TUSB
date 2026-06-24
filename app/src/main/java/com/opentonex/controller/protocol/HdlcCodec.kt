package com.opentonex.controller.protocol

object HdlcCodec {
    private const val FLAG = 0x7E
    private const val ESC = 0x7D
    private const val XOR = 0x20

    fun encode(payload: ByteArray): ByteArray {
        val crc = Crc16Ccitt.compute(payload)
        val body = payload + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        val out = ArrayList<Byte>(body.size + 4)
        out.add(FLAG.toByte())
        for (b in body) {
            val v = b.toInt() and 0xFF
            if (v == FLAG || v == ESC) {
                out.add(ESC.toByte())
                out.add((v xor XOR).toByte())
            } else {
                out.add(b)
            }
        }
        out.add(FLAG.toByte())
        return out.toByteArray()
    }
}
