package com.opentonex.controller.ui

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PedalViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `connectWith emits Connected state from the given connection`() = runTest {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        val state = viewModel.state.value
        assertTrue(state is ConnectionState.Connected)
        assertEquals(Slot.A, (state as ConnectionState.Connected).pedal.activeSlot)
    }

    @Test fun `selectSlot updates active slot after connecting`() = runTest {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        viewModel.selectSlot(Slot.B)
        val state = viewModel.state.value as ConnectionState.Connected
        assertEquals(Slot.B, state.pedal.activeSlot)
    }

    @Test fun `initial state is Disconnected`() {
        val viewModel = PedalViewModel()
        assertEquals(ConnectionState.Disconnected, viewModel.state.value)
    }
}
