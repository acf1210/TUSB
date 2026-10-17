package com.opentonex.controller.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class PedalSettingsTabsTest {
    @Test fun `slider values snap to the exact step before reaching the pedal`() {
        assertEquals(5.0f, snapToStep(5.0000005f, 0.5f))
        assertEquals(-7.5f, snapToStep(-7.4999995f, 0.5f))
        assertEquals(8.6f, snapToStep(8.6000004f, 0.1f))
        assertEquals(0f, snapToStep(0.04f, 0.1f))
    }
}
