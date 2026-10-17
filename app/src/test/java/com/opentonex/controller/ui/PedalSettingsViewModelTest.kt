package com.opentonex.controller.ui

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.protocol.GlobalSetting
import com.opentonex.controller.repository.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PedalSettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `global settings reach the pedal state`() = runTest {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        advanceUntilIdle()

        viewModel.updateGlobalSetting(GlobalSetting.TEMPO, 133f)
        viewModel.updateGlobalSetting(GlobalSetting.INPUT_TRIM, -4.5f)
        viewModel.updateGlobalSetting(GlobalSetting.TUNER_REFERENCE, 432f)
        viewModel.updateGlobalSetting(GlobalSetting.TEMPO_SOURCE, 1f)
        viewModel.updateGlobalSetting(GlobalSetting.TUNER_OUTPUT, 0f)
        advanceUntilIdle()

        val pedal = (viewModel.state.value as ConnectionState.Connected).pedal
        assertEquals(133, pedal.tempo)
        assertEquals(-4.5f, pedal.inputTrim, 0f)
        assertEquals(432, pedal.a4Reference)
        assertTrue(pedal.tempoFromPreset)
        assertEquals(false, pedal.tunerThru)
    }

    @Test fun `master volume comes from the pedal and is written back`() = runTest {
        val fake = FakePedalConnection()
        val viewModel = PedalViewModel()
        assertNull(viewModel.menu.value.masterVolume)
        viewModel.connectWith(fake)
        advanceUntilIdle()
        assertEquals(8f, viewModel.menu.value.masterVolume!!, 0f)

        viewModel.updateMasterVolume(12f)
        advanceUntilIdle()
        assertEquals(10f, fake.masterVolume, 0f)
        assertEquals(10f, viewModel.menu.value.masterVolume!!, 0f)
    }
}
