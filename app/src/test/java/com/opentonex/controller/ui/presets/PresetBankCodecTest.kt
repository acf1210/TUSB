package com.opentonex.controller.ui.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetBankCodecTest {
    @Test fun `round trip keeps name and both presets`() {
        val banks = listOf(
            PresetBank(name = "Clean", presetA = 0, presetB = 3),
            PresetBank(name = "Lead", presetA = 11, presetB = 4)
        )
        assertEquals(banks, PresetBankCodec.decode(PresetBankCodec.encode(banks)))
    }

    @Test fun `blank storage is an empty list`() {
        assertTrue(PresetBankCodec.decode(null).isEmpty())
        assertTrue(PresetBankCodec.decode("").isEmpty())
    }

    @Test fun `corrupt line is skipped`() {
        val decoded = PresetBankCodec.decode("Clean|1|2\nbad\nLead|3|not-a-number")
        assertEquals(listOf(PresetBank("Clean", 1, 2)), decoded)
    }
}
