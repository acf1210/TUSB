package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePedalConnectionTest {
    @Test fun `handshake returns a firmware version and initial state`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        val handshake = conn.handshake()
        assertEquals("SIM-1.0.0", handshake.firmware.version)
        assertEquals(Slot.A, handshake.state.activeSlot)
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

    @Test fun `selectPreset updates the active slot from its assigned presetId`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()

        conn.selectPreset(0x07)

        assertEquals(Slot.C, conn.requestState().activeSlot)
    }
}
