package com.opentonex.controller.ui.tools

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerModelsTest {
    @Test
    fun dropDPresetStartsWithLowD() {
        assertEquals("D2", TuningPreset.DROP_D.notes.first().label)
        assertEquals(73.42, TuningPreset.DROP_D.notes.first().frequencyHz, 0.01)
    }

    @Test
    fun readingUsesSelectedStringWhenProvided() {
        val reading = readingFor(
            frequencyHz = 82.41,
            preset = TuningPreset.DROP_D,
            selectedStringIndex = 0
        )

        assertEquals("D2", reading.target.label)
        assertTrue(reading.cents > 190.0)
    }

    @Test
    fun readingAutomaticallyChoosesNearestString() {
        val reading = readingFor(
            frequencyHz = 110.2,
            preset = TuningPreset.STANDARD,
            selectedStringIndex = null
        )

        assertEquals("A2", reading.target.label)
        assertTrue(abs(reading.cents) < 4.0)
    }

    @Test
    fun pitchDetectorFindsGuitarStringFrequency() {
        val sampleRate = 44_100
        val samples = generateSineSamples(
            frequencyHz = 146.83,
            sampleRate = sampleRate,
            size = 4096
        )

        val detected = detectPitchHz(samples, sampleRate)

        assertTrue(detected != null)
        assertEquals(146.83, detected!!, 2.0)
    }

    @Test
    fun pitchDetectorIgnoresSilence() {
        assertNull(detectPitchHz(ShortArray(4096), 44_100))
    }
}
