package com.opentonex.controller.midi

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiInputManagerTest {
    @Test
    fun hasBleMidiService_matchesStandardMidiUuid() {
        assertTrue(hasBleMidiService(listOf(MIDI_SERVICE_UUID)))
    }

    @Test
    fun hasBleMidiService_rejectsMissingOrDifferentUuid() {
        assertFalse(hasBleMidiService(null))
        assertFalse(hasBleMidiService(listOf(UUID.fromString("00001812-0000-1000-8000-00805F9B34FB"))))
    }

    @Test
    fun isKnownBleMidiDeviceName_matchesChocolateAliases() {
        assertTrue(isKnownBleMidiDeviceName("FootCtrlPlus"))
        assertTrue(isKnownBleMidiDeviceName("M-VAVE Chocolate"))
        assertTrue(isKnownBleMidiDeviceName("MVAVE"))
        assertFalse(isKnownBleMidiDeviceName("Ulanzi"))
    }
}
