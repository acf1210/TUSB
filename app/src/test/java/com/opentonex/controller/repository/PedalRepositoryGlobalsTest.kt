package com.opentonex.controller.repository

import com.opentonex.controller.connection.Handshake
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.PedalRuntimeEvent
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.protocol.GlobalSetting
import com.opentonex.controller.protocol.TonexGlobals
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conexao com o estado REAL capturado do pedal; registra o que iria pelo USB. */
private class GlobalsRecordingConnection(private val rawState: ByteArray) : PedalConnection {
    val events = MutableSharedFlow<PedalRuntimeEvent>(extraBufferCapacity = 16)
    val globalWrites = mutableListOf<PedalState>()
    val masterVolumeWrites = mutableListOf<Float>()
    var masterVolumeRequests = 0
    override val runtimeEvents = events

    override suspend fun connect() {}
    override suspend fun handshake() = Handshake(FirmwareInfo("test"), TonexMessages.parseState(rawState, 22))
    override suspend fun requestState() = TonexMessages.parseState(rawState, 22)
    override suspend fun writeState(state: PedalState) {}
    override suspend fun selectPreset(presetId: Int) {}
    override suspend fun loadPresetToSlot(currentState: PedalState, presetId: Int, slot: Slot, selectSlot: Boolean) {}
    override suspend fun switchMode(currentState: PedalState, targetMode: PedalMode) {}
    override suspend fun writeParameter(paramIndex: Int, value: Float) {}
    override suspend fun writeGlobalState(state: PedalState) { globalWrites += state }
    override suspend fun writeMasterVolume(value: Float) { masterVolumeWrites += value }
    override suspend fun requestMasterVolume() { masterVolumeRequests++ }
    override suspend fun disconnect() {}
}

class PedalRepositoryGlobalsTest {
    private fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private val realState = hex(
        "B9 03 81 06 03 80 A0 02 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 00 00 80 40 00 00 01 " +
            "BA 03 B9 03 80 9F 80 FF 00 B9 03 2F 00 80 FF B9 03 00 80 FF 00 " +
            "BC 06 0B 00 09 00 03 00 00 01 81 B8 01 01 00 88 F0 6F 26 43"
    )

    @Test fun `setGlobal sends the state with only that field changed`() = runTest {
        val connection = GlobalsRecordingConnection(realState)
        val repo = PedalRepository(connection, this)
        repo.connect()

        repo.setGlobal(GlobalSetting.TUNER_REFERENCE, 432f)
        advanceUntilIdle()

        val sent = connection.globalWrites.single()
        assertEquals(432, sent.a4Reference)
        assertEquals(432f, TonexGlobals.read(sent.rawState, GlobalSetting.TUNER_REFERENCE)!!, 0f)
        assertEquals(4.0f, TonexGlobals.read(sent.rawState, GlobalSetting.INPUT_TRIM)!!, 0f)
        val published = (repo.state.value as ConnectionState.Connected).pedal
        assertEquals(432, published.a4Reference)
        repo.disconnect()
    }

    @Test fun `consecutive globals build on each other`() = runTest {
        val connection = GlobalsRecordingConnection(realState)
        val repo = PedalRepository(connection, this)
        repo.connect()

        repo.setGlobal(GlobalSetting.INPUT_TRIM, -3f)
        repo.setGlobal(GlobalSetting.TEMPO_SOURCE, 1f)
        advanceUntilIdle()

        val last = connection.globalWrites.last()
        assertEquals(-3f, TonexGlobals.read(last.rawState, GlobalSetting.INPUT_TRIM)!!, 0f)
        assertEquals(1f, TonexGlobals.read(last.rawState, GlobalSetting.TEMPO_SOURCE)!!, 0f)
        assertTrue(last.tempoFromPreset)
        repo.disconnect()
    }

    @Test fun `master volume is requested on connect and follows the pedal`() = runTest {
        val connection = GlobalsRecordingConnection(realState)
        val repo = PedalRepository(connection, this)
        repo.connect()
        assertEquals(1, connection.masterVolumeRequests)
        assertNull(repo.masterVolume.value)

        connection.events.emit(PedalRuntimeEvent.MasterVolumeChanged(value = 8f, payloadHex = ""))
        advanceUntilIdle()
        assertEquals(8f, repo.masterVolume.value!!, 0f)

        repo.setMasterVolume(6.5f)
        assertEquals(listOf(6.5f), connection.masterVolumeWrites)
        assertEquals(6.5f, repo.masterVolume.value!!, 0f)

        repo.disconnect()
        assertNull(repo.masterVolume.value)
    }

    @Test fun `master volume request is retried on polls until the pedal answers`() = runTest {
        // Bancada: o pedido logo apos o handshake caiu (o pedal descarta parte dos comandos).
        val connection = GlobalsRecordingConnection(realState)
        val repo = PedalRepository(connection, this)
        repo.connect()
        repo.syncStateFromPedal()
        repo.syncStateFromPedal()
        assertEquals(3, connection.masterVolumeRequests)

        connection.events.emit(PedalRuntimeEvent.MasterVolumeChanged(value = 7f, payloadHex = ""))
        advanceUntilIdle()
        repo.syncStateFromPedal()
        assertEquals(3, connection.masterVolumeRequests)

        repo.disconnect()
    }

    @Test fun `master volume retries stop at the limit when the pedal never answers`() = runTest {
        val connection = GlobalsRecordingConnection(realState)
        val repo = PedalRepository(connection, this)
        repo.connect()
        repeat(30) { repo.syncStateFromPedal() }
        assertEquals(10, connection.masterVolumeRequests)
        repo.disconnect()
    }
}
