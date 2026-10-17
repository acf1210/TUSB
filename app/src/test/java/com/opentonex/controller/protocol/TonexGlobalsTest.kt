package com.opentonex.controller.protocol

import com.opentonex.controller.domain.Slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonexGlobalsTest {
    private fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    // StateResponse real do ToneX One (captures/tonex-session-1782929439644.jsonl, hello_response).
    private val realState = hex(
        "B9 03 81 06 03 80 A0 02 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 00 00 80 40 00 00 01 " +
            "BA 14 B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 B9 03 00 80 FF 00 B9 03 0F 80 FF 2F " +
            "B9 03 80 FF 00 00 B9 03 00 00 80 FF B9 03 80 BF 80 BF 80 BF B9 03 80 9F 80 FF 00 B9 03 00 80 FF 80 FF " +
            "B9 03 11 11 00 B9 03 80 FF 00 00 B9 03 00 11 00 B9 03 0A 00 0A B9 03 00 22 06 B9 03 11 00 00 " +
            "B9 03 00 00 11 B9 03 0B 0B 0B B9 03 11 22 00 B9 03 00 19 19 BC 06 0B 00 09 00 03 00 00 01 " +
            "81 B8 01 01 00 88 F0 6F 26 43"
    )

    @Test fun `reads every global from the captured pedal state`() {
        assertEquals(4.0f, TonexGlobals.read(realState, GlobalSetting.INPUT_TRIM)!!, 0f)
        assertEquals(440f, TonexGlobals.read(realState, GlobalSetting.TUNER_REFERENCE)!!, 0f)
        assertEquals(166.437f, TonexGlobals.read(realState, GlobalSetting.TEMPO)!!, 0.001f)
        assertEquals(0f, TonexGlobals.read(realState, GlobalSetting.TEMPO_SOURCE)!!, 0f)
        assertEquals(1f, TonexGlobals.read(realState, GlobalSetting.TUNER_OUTPUT)!!, 0f)
    }

    @Test fun `parseState agrees with the global reader`() {
        val state = TonexMessages.parseState(realState, fieldsOffset = 22)
        assertEquals(4.0f, state.inputTrim, 0f)
        assertEquals(440, state.a4Reference)
        assertEquals(166, state.tempo)
        assertFalse(state.tempoFromPreset)
        assertTrue(state.tunerThru)
    }

    @Test fun `write changes only the bytes of the setting`() {
        val cases = mapOf(
            GlobalSetting.INPUT_TRIM to -7.5f,
            GlobalSetting.TUNER_REFERENCE to 432f,
            GlobalSetting.TEMPO to 96f,
            GlobalSetting.TEMPO_SOURCE to 1f,
            GlobalSetting.TUNER_OUTPUT to 0f
        )
        cases.forEach { (setting, value) ->
            val written = TonexGlobals.write(realState, setting, value)
            assertEquals("$setting", value, TonexGlobals.read(written, setting)!!, 0f)
            assertEquals("$setting tamanho", realState.size, written.size)
            val changed = realState.indices.count { realState[it] != written[it] }
            assertTrue("$setting mudou $changed bytes", changed in 1..4)
            // Os outros ajustes continuam iguais.
            (cases.keys - setting).forEach { other ->
                assertEquals("$other apos $setting", TonexGlobals.read(realState, other), TonexGlobals.read(written, other))
            }
        }
    }

    @Test fun `trim encodes like the reference firmware comment`() {
        // Builty: 0x000070c1 (-15.0) ... 0x00007041 (15.0)
        val low = TonexGlobals.write(realState, GlobalSetting.INPUT_TRIM, -15f)
        assertArrayEquals(hex("88 00 00 70 C1"), low.copyOfRange(22, 27))
        val high = TonexGlobals.write(realState, GlobalSetting.INPUT_TRIM, 15f)
        assertArrayEquals(hex("88 00 00 70 41"), high.copyOfRange(22, 27))
    }

    @Test fun `write clamps to the setting range`() {
        val written = TonexGlobals.write(realState, GlobalSetting.TUNER_REFERENCE, 500f)
        assertEquals(465f, TonexGlobals.read(written, GlobalSetting.TUNER_REFERENCE)!!, 0f)
        val slow = TonexGlobals.write(realState, GlobalSetting.TEMPO, 10f)
        assertEquals(40f, TonexGlobals.read(slow, GlobalSetting.TEMPO)!!, 0f)
    }

    @Test fun `unknown layout is refused instead of written`() {
        // Um byte a mais no fim desloca todos os campos contados do fim: as tags somem.
        val shifted = realState + 0x00
        assertNull(TonexGlobals.read(shifted, GlobalSetting.TEMPO))
        assertNull(TonexGlobals.read(shifted, GlobalSetting.TEMPO_SOURCE))
        val failure = runCatching { TonexGlobals.write(shifted, GlobalSetting.TUNER_REFERENCE, 432f) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertNull(TonexGlobals.read(ByteArray(12), GlobalSetting.INPUT_TRIM))
    }

    @Test fun `global write goes out as the state command with the current slot`() {
        val written = TonexGlobals.write(realState, GlobalSetting.TUNER_REFERENCE, 432f)
        val command = TonexMessages.buildSlotChangePayload(
            rawState = written,
            activeSlotOffset = TonexMessages.activeSlotOffset(written, 22),
            newSlotValue = TonexMessages.slotToByte(Slot.B)
        )
        assertArrayEquals(hex("B9 03 81 06 03 82 A0 00 80 0B 03"), command.copyOfRange(0, 11))
        val body = command.copyOfRange(11, command.size)
        assertArrayEquals(written.copyOfRange(8, written.size), body)
        assertArrayEquals(hex("81 B0 01"), body.copyOfRange(body.size - 10, body.size - 7)) // 432 Hz
    }

    @Test fun `master volume command matches the reference firmware`() {
        assertArrayEquals(
            hex("B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 03 00 00 88 00 00 A0 40"),
            TonexGlobals.buildSetMasterVolumePayload(5f)
        )
        val loud = TonexGlobals.buildSetMasterVolumePayload(99f)
        assertEquals(10f, TonexGlobals.parseMasterVolume(hex("B9 03 81 09 03 0A 02") + loud.copyOfRange(11, loud.size))!!, 0f)
    }

    @Test fun `master volume request and response match the official app capture`() {
        // tonex_full_session.pcap: OUT 0x030D e a resposta 0x0309 de subtipo 03 (volume 8,0).
        assertArrayEquals(hex("B9 03 81 0D 03 82 05 00 80 0B 03 B9 03 03 00 00"), TonexGlobals.requestMasterVolumePayload())
        val response = hex("B9 03 81 09 03 0A 02 B9 04 03 00 00 88 00 00 00 41")
        assertEquals(8.0f, TonexGlobals.parseMasterVolume(response)!!, 0f)
        assertNull(TonexMessages.parseParameterChange(response))
    }

    @Test fun `knob notifications are not mistaken for master volume`() {
        val knob = hex("B9 03 81 09 03 0A 02 B9 04 02 00 15 88 33 33 03 41")
        assertNull(TonexGlobals.parseMasterVolume(knob))
        assertNull(TonexGlobals.parseMasterVolume(realState))
    }
}
