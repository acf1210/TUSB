package com.opentonex.controller.protocol

import com.opentonex.controller.connection.SimulatedFullPreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonexFullPresetTest {
    private fun hex(text: String): ByteArray =
        text.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val body = SimulatedFullPreset.body(
        bank = 1,
        index = 7,
        presetName = "UK AC",
        toneModelName = "Demo AC30",
        creator = "TUSB Demo",
        date = "2026-10-19"
    )

    @Test
    fun `request payload matches the official app full preset request`() {
        // tonex_full_session.pcap: OUT `... B9 04 0B 01 13 01` pede o 0x0303 do preset 19
        assertArrayEquals(
            hex("B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 13 01"),
            TonexFullPreset.requestPayload(TonexFullPreset.BANK_STORED, 19)
        )
        assertArrayEquals(
            hex("B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 00 00 01"),
            TonexFullPreset.requestPayload(TonexFullPreset.BANK_EDIT_BUFFER, 0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `request payload rejects index outside the 20 stored presets`() {
        TonexFullPreset.requestPayload(TonexFullPreset.BANK_STORED, 20)
    }

    @Test
    fun `parse extracts preset and tone model metadata`() {
        val preset = TonexFullPreset.parse(body)
        assertEquals(1, preset.bank)
        assertEquals(7, preset.index)
        assertEquals("UK AC", preset.presetName)
        assertEquals("Demo AC30", preset.toneModelName)
        assertEquals("TUSB Demo", preset.toneModelCreator)
        assertEquals("2026-10-19", preset.toneModelDate)
        assertEquals(SimulatedFullPreset.MODEL_BLOB_SIZE, preset.toneModelSize)
    }

    @Test
    fun `body from response checks type and declared size`() {
        val size = body.size
        val response = hex("B9 03 81 03 03 81") +
            byteArrayOf((size and 0xFF).toByte(), (size shr 8).toByte(), 0x0B) + body
        assertArrayEquals(body, TonexFullPreset.bodyFromResponse(response))
    }

    @Test(expected = FullPresetParseException::class)
    fun `body from response rejects a size mismatch`() {
        val response = hex("B9 03 81 03 03 81 00 01 0B") + body
        TonexFullPreset.bodyFromResponse(response)
    }

    @Test(expected = FullPresetParseException::class)
    fun `parse rejects a truncated body`() {
        TonexFullPreset.parse(body.copyOfRange(0, body.size - 10))
    }

    @Test(expected = FullPresetParseException::class)
    fun `parse rejects trailing bytes`() {
        TonexFullPreset.parse(body + byteArrayOf(0))
    }

    @Test
    fun `write payload uses the command envelope and retargets the preset index`() {
        val payload = TonexFullPreset.writePayload(body, 3)
        val size = body.size
        assertArrayEquals(
            hex("B9 03 81 03 03 82") +
                byteArrayOf((size and 0xFF).toByte(), (size shr 8).toByte()) + hex("80 0B 03"),
            payload.copyOfRange(0, 11)
        )
        val written = payload.copyOfRange(11, payload.size)
        assertEquals(3, TonexFullPreset.parse(written).index)
        assertTrue(TonexFullPreset.sameContent(body, written))
    }

    @Test
    fun `same content ignores bank and index but not the model`() {
        val other = SimulatedFullPreset.body(bank = 0, index = 7, presetName = "UK AC", toneModelName = "Demo AC30")
        assertTrue(TonexFullPreset.sameContent(body, other))
        val changed = body.copyOf().also { it[it.size - 200] = (it[it.size - 200] + 1).toByte() }
        assertFalse(TonexFullPreset.sameContent(body, changed))
    }
}
