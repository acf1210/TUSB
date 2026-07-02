package com.opentonex.controller.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `parses program change`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xC0, 0x05))
        assertEquals(listOf(MidiMessage.ProgramChange(channel = 0, program = 5)), messages)
    }

    @Test
    fun `parses control change`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xB1, 20, 127))
        assertEquals(listOf(MidiMessage.ControlChange(channel = 1, controller = 20, value = 127)), messages)
    }

    @Test
    fun `parses running status control changes`() {
        val parser = MidiParser()
        // Um status 0xB0 seguido de dois pares de data bytes (running status).
        val messages = parser.parse(bytes(0xB0, 20, 127, 21, 0))
        assertEquals(
            listOf(
                MidiMessage.ControlChange(0, 20, 127),
                MidiMessage.ControlChange(0, 21, 0)
            ),
            messages
        )
    }

    @Test
    fun `parses message fragmented across packets`() {
        val parser = MidiParser()
        assertTrue(parser.parse(bytes(0xB0, 25)).isEmpty())
        // O value chega no pacote seguinte; o parser deve manter estado.
        assertEquals(
            listOf(MidiMessage.ControlChange(0, 25, 127)),
            parser.parse(bytes(127))
        )
    }

    @Test
    fun `ignores realtime bytes interleaved in message`() {
        val parser = MidiParser()
        // 0xF8 (clock) no meio de um CC nao pode corromper o parse.
        val messages = parser.parse(bytes(0xB0, 20, 0xF8, 127))
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }

    @Test
    fun `ignores sysex content`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xF0, 0x7E, 0x7F, 0x06, 0xF7, 0xC0, 0x03))
        assertEquals(listOf(MidiMessage.ProgramChange(0, 3)), messages)
    }

    @Test
    fun `ignores note on and off`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0x90, 60, 100, 0x80, 60, 0, 0xB0, 20, 127))
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }

    @Test
    fun `discards data bytes without status`() {
        val parser = MidiParser()
        assertTrue(parser.parse(bytes(20, 127, 55)).isEmpty())
    }

    @Test
    fun `respects offset and count`() {
        val parser = MidiParser()
        val buffer = bytes(0x00, 0xB0, 20, 127, 0x00)
        val messages = parser.parse(buffer, offset = 1, count = 3)
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }
}
