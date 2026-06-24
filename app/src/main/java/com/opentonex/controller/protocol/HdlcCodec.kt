package com.opentonex.controller.protocol

sealed interface HdlcFrame {
    data class Valid(val payload: ByteArray) : HdlcFrame
    data object CrcError : HdlcFrame
    data object Incomplete : HdlcFrame
}

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

    fun decode(stream: ByteArray): HdlcFrame {
        val start = stream.indexOfFirst { (it.toInt() and 0xFF) == FLAG }
        if (start < 0) return HdlcFrame.Incomplete
        var end = -1
        for (i in (start + 1) until stream.size) {
            if ((stream[i].toInt() and 0xFF) == FLAG) { end = i; break }
        }
        if (end < 0) return HdlcFrame.Incomplete

        val unstuffed = ArrayList<Byte>(end - start)
        var i = start + 1
        while (i < end) {
            val v = stream[i].toInt() and 0xFF
            if (v == ESC) {
                i++
                if (i >= end) return HdlcFrame.Incomplete
                unstuffed.add(((stream[i].toInt() and 0xFF) xor XOR).toByte())
            } else {
                unstuffed.add(stream[i])
            }
            i++
        }
        if (unstuffed.size < 2) return HdlcFrame.CrcError
        val bytes = unstuffed.toByteArray()
        val payload = bytes.copyOfRange(0, bytes.size - 2)
        val gotCrc = (bytes[bytes.size - 2].toInt() and 0xFF) or
            ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
        return if (gotCrc == Crc16Ccitt.compute(payload)) HdlcFrame.Valid(payload)
        else HdlcFrame.CrcError
    }
}
