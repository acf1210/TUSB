package com.opentonex.controller.ui.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalTonePresetCodecTest {
    @Test
    fun `round trips name and full captured parameter array`() {
        val original = LocalTonePreset(
            name = "Clean \"Studio\" 🎸",
            parameters = PresetParameterRanges.safeSample(),
            createdAtEpochMillis = 1_760_000_000_000
        )

        assertEquals(original, LocalTonePresetCodec.decode(LocalTonePresetCodec.encode(original)))
    }

    @Test
    fun `rejects unsupported schema version`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.decode("{\"version\":99,\"name\":\"x\",\"parameters\":[1]}")
        }
    }

    @Test
    fun `rejects duplicate and missing fields`() {
        val valid = LocalTonePresetCodec.encode(
            LocalTonePreset("Clean", PresetParameterRanges.safeSample())
        )
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.decode(valid + "version=1\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.decode(valid.lineSequence().filterNot { it.startsWith("createdAt=") }.joinToString("\n"))
        }
    }

    @Test
    fun `rejects empty or oversized parameter arrays`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.encode(LocalTonePreset("Empty", emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.encode(
                LocalTonePreset("Too many", List(LocalTonePreset.MAX_PARAMETERS + 1) { 0f })
            )
        }
    }

    @Test
    fun `rejects non finite values`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalTonePresetCodec.encode(LocalTonePreset("Invalid", listOf(Float.NaN)))
        }
    }

    @Test
    fun `rejects values outside per-index range and fractional selectors`() {
        val outOfRange = PresetParameterRanges.safeSample().toMutableList().apply { this[2] = 500f }
        val fractionalSwitch = PresetParameterRanges.safeSample().toMutableList().apply { this[1] = 0.5f }
        assertEquals(false, LocalTonePresetCodec.validParameterValues(outOfRange))
        assertEquals(false, LocalTonePresetCodec.validParameterValues(fractionalSwitch))
        assertEquals(true, LocalTonePresetCodec.validParameterValues(PresetParameterRanges.safeSample()))
    }
}
