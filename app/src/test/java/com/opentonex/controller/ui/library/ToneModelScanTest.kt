package com.opentonex.controller.ui.library

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.protocol.TonexFullPreset
import com.opentonex.controller.repository.PedalRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneModelScanTest {

    @Test
    fun `scan reads the Tone Model of every stored preset`() = runTest {
        val repo = PedalRepository(FakePedalConnection(), backgroundScope)
        repo.connect()
        val controller = ToneModelLibraryController(this, { repo }, { _, _ -> })
        controller.scanPedal()
        advanceUntilIdle()
        val names = controller.pedalToneModels.value
        assertEquals(TonexFullPreset.STORED_PRESET_COUNT, names.size)
        assertEquals("Demo Amp 1", names[0])
        assertEquals("Demo Amp 20", names[19])
    }

    @Test
    fun `a preset that fails to read is skipped`() = runTest {
        val delegate = FakePedalConnection()
        val fake = object : PedalConnection by delegate {
            override suspend fun readFullPreset(bank: Int, index: Int): ByteArray {
                if (index == 3) throw IllegalStateException("timeout")
                return delegate.readFullPreset(bank, index)
            }
        }
        val repo = PedalRepository(fake, backgroundScope)
        repo.connect()
        val controller = ToneModelLibraryController(this, { repo }, { _, _ -> })
        controller.scanPedal()
        advanceUntilIdle()
        val names = controller.pedalToneModels.value
        assertFalse(3 in names)
        assertEquals("Demo Amp 5", names[4])
    }

    @Test
    fun `stopping the scan clears the names`() = runTest {
        val repo = PedalRepository(FakePedalConnection(), backgroundScope)
        repo.connect()
        val controller = ToneModelLibraryController(this, { repo }, { _, _ -> })
        controller.scanPedal()
        advanceUntilIdle()
        assertTrue(controller.pedalToneModels.value.isNotEmpty())
        controller.stopScan()
        assertTrue(controller.pedalToneModels.value.isEmpty())
    }
}
