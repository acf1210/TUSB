package com.opentonex.controller.connection

/**
 * Corpo sintetico de preset completo (0x0303) com o mesmo layout de tags do pedal real,
 * para o pedal simulado e os testes. Nenhum byte vem de preset ou Tone Model real: o blob
 * do modelo e preenchido com um padrao deterministico.
 */
object SimulatedFullPreset {
    private const val NAME_FIELD = 33
    private const val SHORT_FIELD = 17
    private const val DATE_FIELD = 11
    private const val LONG_FIELD = 65
    private const val PARAMETER_COUNT = 109
    const val MODEL_BLOB_SIZE = 512

    fun body(
        bank: Int,
        index: Int,
        presetName: String,
        toneModelName: String,
        creator: String = "TUSB Demo",
        date: String = "2026-10-19"
    ): ByteArray {
        val header = struct(
            struct(text(presetName, NAME_FIELD), int(presetName.length)),
            array(float(0f), float(120f)),
            array(params(), params(), params()),
            struct(*Array(13) { struct(text("", NAME_FIELD), int(0)) })
        )
        val model = struct(
            bytes(ByteArray(16) { (it * 7 + index).toByte() }),
            struct(text(toneModelName, NAME_FIELD), int(toneModelName.length)),
            int(4),
            bytes(ByteArray(MODEL_BLOB_SIZE) { (it * 31 + index).toByte() }),
            struct(
                int(1), int(0), int(0),
                struct(text("DEMO", SHORT_FIELD), int(4)),
                struct(text("", SHORT_FIELD), int(0)),
                struct(text(date, DATE_FIELD), int(date.length)),
                struct(text(creator, NAME_FIELD), int(creator.length)),
                struct(text("", LONG_FIELD), int(0)),
                struct(text("", LONG_FIELD), int(0))
            )
        )
        val emptyModel = struct(
            bytes(ByteArray(16)),
            struct(text("", NAME_FIELD), int(0)),
            int(0),
            bytes(ByteArray(MODEL_BLOB_SIZE)),
            struct(
                int(0), int(0), int(0),
                struct(text("", SHORT_FIELD), int(0)),
                struct(text("", SHORT_FIELD), int(0)),
                struct(text("", DATE_FIELD), int(0)),
                struct(text("", NAME_FIELD), int(0)),
                struct(text("", LONG_FIELD), int(0)),
                struct(text("", LONG_FIELD), int(0))
            )
        )
        return struct(int(bank), int(index), struct(header, model, int(0), emptyModel))
    }

    private fun params(): ByteArray = array(*Array(PARAMETER_COUNT) { float(5f) })

    private fun struct(vararg items: ByteArray): ByteArray = group(0xB9, items)
    private fun array(vararg items: ByteArray): ByteArray = group(0xBA, items)

    private fun group(tag: Int, items: Array<out ByteArray>): ByteArray =
        byteArrayOf(tag.toByte()) + int(items.size) + items.fold(ByteArray(0)) { acc, item -> acc + item }

    private fun int(value: Int): ByteArray = when {
        value < 0x80 -> byteArrayOf(value.toByte())
        value <= 0xFF -> byteArrayOf(0x80.toByte(), value.toByte())
        else -> byteArrayOf(0x81.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    }

    private fun float(value: Float): ByteArray {
        val bits = value.toRawBits()
        return byteArrayOf(0x88.toByte(), bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
    }

    private fun bytes(value: ByteArray): ByteArray = byteArrayOf(0xBC.toByte()) + int(value.size) + value

    private fun text(value: String, field: Int): ByteArray =
        bytes(value.toByteArray(Charsets.ISO_8859_1).copyOf(field))
}
