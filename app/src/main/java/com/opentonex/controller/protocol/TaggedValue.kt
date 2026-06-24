package com.opentonex.controller.protocol

object TaggedValue {
    data class IntResult(val value: Int, val nextOffset: Int)
    data class FloatResult(val value: Float, val nextOffset: Int)

    fun encodeU16(value: Int, tag: Int): ByteArray =
        byteArrayOf(tag.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    fun decodeU16(data: ByteArray, offset: Int): IntResult {
        val lo = data[offset + 1].toInt() and 0xFF
        val hi = data[offset + 2].toInt() and 0xFF
        return IntResult(lo or (hi shl 8), offset + 3)
    }

    fun encodeFloat(value: Float): ByteArray {
        val bits = java.lang.Float.floatToIntBits(value)
        return byteArrayOf(
            0x88.toByte(),
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte()
        )
    }

    fun decodeFloat(data: ByteArray, offset: Int): FloatResult {
        val bits = (data[offset + 1].toInt() and 0xFF) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            ((data[offset + 3].toInt() and 0xFF) shl 16) or
            ((data[offset + 4].toInt() and 0xFF) shl 24)
        return FloatResult(java.lang.Float.intBitsToFloat(bits), offset + 5)
    }
}
