package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePedalConnectionTest {
    @Test fun `hello returns a firmware version`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        assertEquals("SIM-1.0.0", conn.sendHello().version)
    }

    @Test fun `request state returns three slots and default active A`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        val state = conn.requestState()
        assertEquals(3, state.slots.size)
        assertEquals(Slot.A, state.activeSlot)
    }

    @Test fun `writing a slot change updates active slot on next read`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        val state = conn.requestState()
        conn.writeState(state.withActiveSlot(Slot.B))
        assertEquals(Slot.B, conn.requestState().activeSlot)
    }
}
