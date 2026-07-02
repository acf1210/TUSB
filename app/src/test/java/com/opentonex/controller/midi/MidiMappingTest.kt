package com.opentonex.controller.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidiMappingTest {

    @Test
    fun `default map has expected assignments`() {
        val map = MidiMapping.DEFAULT
        assertEquals(MidiAction.SELECT_SLOT_A, map.actionFor(20))
        assertEquals(MidiAction.SELECT_SLOT_B, map.actionFor(21))
        assertEquals(MidiAction.SELECT_SLOT_C, map.actionFor(22))
        assertEquals(MidiAction.NEXT_PRESET, map.actionFor(23))
        assertEquals(MidiAction.PREV_PRESET, map.actionFor(24))
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(25))
        assertEquals(MidiAction.TOGGLE_CAB, map.actionFor(26))
        assertEquals(MidiAction.TOGGLE_GATE, map.actionFor(27))
        assertEquals(MidiAction.TOGGLE_COMP, map.actionFor(28))
        assertEquals(MidiAction.TOGGLE_EQ, map.actionFor(29))
        assertEquals(MidiAction.TOGGLE_MOD, map.actionFor(30))
        assertEquals(MidiAction.TOGGLE_DELAY, map.actionFor(31))
        assertEquals(MidiAction.TOGGLE_REVERB, map.actionFor(32))
        assertEquals(MidiAction.AMP_BASS, map.actionFor(102))
        assertEquals(MidiAction.AMP_MID, map.actionFor(103))
        assertEquals(MidiAction.AMP_TREBLE, map.actionFor(104))
        assertEquals(MidiAction.AMP_GAIN, map.actionFor(105))
        assertEquals(MidiAction.AMP_VOLUME, map.actionFor(106))
        assertNull(map.actionFor(64))
    }

    @Test
    fun `withLearned moves action to new cc and frees old cc`() {
        val map = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, 40)
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(40))
        assertNull(map.actionFor(25))
        assertEquals(40, map.ccFor(MidiAction.TOGGLE_BYPASS))
    }

    @Test
    fun `withLearned steals cc already used by another action`() {
        val map = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, 20)
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(20))
        assertNull(map.ccFor(MidiAction.SELECT_SLOT_A))
    }

    @Test
    fun `codec round trip preserves mapping`() {
        val original = MidiMapping.DEFAULT.withLearned(MidiAction.AMP_GAIN, 7)
        val decoded = MidiMappingCodec.decode(MidiMappingCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `decode of null or blank returns default`() {
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode(null))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode(""))
    }

    @Test
    fun `decode of corrupted payload returns default`() {
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("20=NOT_AN_ACTION"))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("garbage"))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("999=TOGGLE_BYPASS"))
    }
}
