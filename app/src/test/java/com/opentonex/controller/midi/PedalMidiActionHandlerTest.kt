package com.opentonex.controller.midi

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.PedalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PedalMidiActionHandlerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activePresetId is null when disconnected`() {
        val viewModel = PedalViewModel()
        val handler = PedalMidiActionHandler(viewModel)
        assertNull(handler.activePresetId())
    }

    @Test
    fun `activePresetId reflects connected pedal state`() = runTest(dispatcher.scheduler) {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        advanceUntilIdle()
        val connected = viewModel.state.value as ConnectionState.Connected
        val expected = connected.pedal.presetIds.getOrNull(connected.pedal.activeSlot.ordinal)
        val handler = PedalMidiActionHandler(viewModel)
        assertEquals(expected, handler.activePresetId())
    }
}
