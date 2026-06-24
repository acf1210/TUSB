package com.opentonex.controller.repository

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalRepositoryTest {
    @Test fun `connect emits Connected with state`() = runTest {
        val repo = PedalRepository(FakePedalConnection())
        repo.connect()
        val s = repo.state.value
        assertTrue(s is ConnectionState.Connected)
        assertEquals(Slot.A, (s as ConnectionState.Connected).pedal.activeSlot)
    }

    @Test fun `selectSlot updates active slot in emitted state`() = runTest {
        val repo = PedalRepository(FakePedalConnection())
        repo.connect()
        repo.selectSlot(Slot.C)
        val s = repo.state.value as ConnectionState.Connected
        assertEquals(Slot.C, s.pedal.activeSlot)
    }
}
