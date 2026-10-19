package com.opentonex.controller.repository

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.SimulatedFullPreset
import com.opentonex.controller.protocol.TonexFullPreset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalRepositoryFullPresetTest {

    @Test
    fun `reads a stored preset and the edit buffer`() = runTest {
        val repo = PedalRepository(FakePedalConnection(), backgroundScope)
        repo.connect()
        val stored = repo.readFullPreset(TonexFullPreset.BANK_STORED, 5)
        assertEquals("Preset 6", stored.presetName)
        assertEquals("Demo Amp 6", stored.toneModelName)
        val current = repo.readFullPreset(TonexFullPreset.BANK_EDIT_BUFFER, 0)
        assertEquals(TonexFullPreset.BANK_EDIT_BUFFER, current.bank)
    }

    @Test
    fun `write is confirmed by reading the preset back`() = runTest {
        val fake = FakePedalConnection()
        val repo = PedalRepository(fake, backgroundScope)
        repo.connect()
        val source = repo.readFullPreset(TonexFullPreset.BANK_STORED, 2)
        assertTrue(repo.writeFullPreset(source.body, 9))
        val written = repo.readFullPreset(TonexFullPreset.BANK_STORED, 9)
        assertEquals("Preset 3", written.presetName)
        assertEquals(9, written.index)
    }

    @Test
    fun `write is not confirmed when the pedal keeps the old preset`() = runTest {
        val fake = object : com.opentonex.controller.connection.PedalConnection by FakePedalConnection() {
            override suspend fun writeFullPreset(body: ByteArray, targetIndex: Int) = Unit
            override suspend fun readFullPreset(bank: Int, index: Int): ByteArray =
                SimulatedFullPreset.body(bank, index, "Old", "Old Model")
        }
        val repo = PedalRepository(fake, backgroundScope)
        repo.connect()
        val body = SimulatedFullPreset.body(1, 0, "New", "New Model")
        assertFalse(repo.writeFullPreset(body, 4))
    }

    @Test(expected = IllegalStateException::class)
    fun `full preset access requires a connected pedal`() = runTest {
        PedalRepository(FakePedalConnection(), backgroundScope).readFullPreset(TonexFullPreset.BANK_STORED, 0)
    }
}
