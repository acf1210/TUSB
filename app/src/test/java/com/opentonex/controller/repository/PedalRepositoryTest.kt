package com.opentonex.controller.repository

import com.opentonex.controller.capture.EventCaptureRecorder
import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.Handshake
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.PedalRuntimeEvent
import java.nio.file.Files
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingPedalConnection : PedalConnection {
    private val delegate = FakePedalConnection()
    var lastSelectedPresetId: Int? = null
    var writeStateCalls = 0
    override val runtimeEvents: Flow<PedalRuntimeEvent> = emptyFlow()

    override suspend fun connect() = delegate.connect()

    override suspend fun handshake() = delegate.handshake()

    override suspend fun requestState() = delegate.requestState()

    override suspend fun writeState(state: com.opentonex.controller.domain.PedalState) {
        writeStateCalls++
        delegate.writeState(state)
    }

    override suspend fun selectPreset(presetId: Int) {
        lastSelectedPresetId = presetId
        delegate.selectPreset(presetId)
    }

    override suspend fun disconnect() = delegate.disconnect()
}

private class DriftingPresetMapConnection : PedalConnection {
    private var requestCount = 0
    override val runtimeEvents: Flow<PedalRuntimeEvent> = emptyFlow()

    override suspend fun connect() = Unit

    override suspend fun handshake() = Handshake(FirmwareInfo("SIM-1.0.0"), requestState())

    override suspend fun requestState(): PedalState {
        val presetIds = if (requestCount++ == 0) {
            listOf(0x03, 0x0C, 0x0B)
        } else {
            listOf(0x03, 0x07, 0x0B)
        }
        return PedalState(
            activeSlot = Slot.A,
            inputTrim = 0.0f,
            a4Reference = 440,
            tempo = 120,
            slots = listOf(
                PresetSlot(0, "Preset A", Rgb(255, 0, 0)),
                PresetSlot(1, "Preset B", Rgb(0, 255, 0)),
                PresetSlot(2, "Preset C", Rgb(0, 0, 255))
            ),
            presetIds = presetIds
        )
    }

    override suspend fun writeState(state: PedalState) {
        lastWrittenState = state
    }

    override suspend fun selectPreset(presetId: Int) = Unit

    override suspend fun disconnect() = Unit

    var lastWrittenState: PedalState? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class PedalRepositoryTest {
    @Test fun `connect emits Connected with state`() = runTest {
        val repo = PedalRepository(FakePedalConnection(), this)
        repo.connect()
        advanceUntilIdle()
        val s = repo.state.value
        assertTrue(s is ConnectionState.Connected)
        assertEquals(Slot.A, (s as ConnectionState.Connected).pedal.activeSlot)
        repo.disconnect()
    }

    @Test fun `connect captures the handshake hello and state events`() = runTest {
        // Regressao: o coletor de runtimeEvents era lancado mas nao aguardava a inscricao,
        // entao os eventos emitidos durante o handshake (SharedFlow sem replay) eram
        // descartados e "hello"/estado nunca apareciam nas capturas. Ver protocol-notes.md.
        val captureDir = Files.createTempDirectory("tonex-handshake-capture-").toFile()
        val recorder = EventCaptureRecorder(File(captureDir, "captures"))
        val session = recorder.startSession()
        val repo = PedalRepository(FakePedalConnection(), this)
        repo.attachCaptureRecorder(recorder)

        repo.connect()
        advanceUntilIdle()
        repo.disconnect()

        val captured = session.file.readText()
        assertTrue("hello_response esperado na captura: $captured", captured.contains("hello_response"))
        assertTrue("state_snapshot esperado na captura: $captured", captured.contains("state_snapshot"))
        captureDir.deleteRecursively()
    }

    @Test fun `selectSlot updates active slot in emitted state`() = runTest {
        val repo = PedalRepository(FakePedalConnection(), this)
        repo.connect()
        repo.selectSlot(Slot.C)
        advanceUntilIdle()
        val s = repo.state.value as ConnectionState.Connected
        assertEquals(Slot.C, s.pedal.activeSlot)
        repo.disconnect()
    }

    @Test fun `selectSlot envia selectPreset com o presetId do slot`() = runTest {
        val connection = RecordingPedalConnection()
        val repo = PedalRepository(connection, this)

        repo.connect()
        repo.selectSlot(Slot.C)

        assertEquals(0, connection.writeStateCalls)
        // FakePedalConnection.presetIds = [0x0C, 0x08, 0x07] -> slot C = indice 2 = 0x07
        assertEquals(0x07, connection.lastSelectedPresetId)
        repo.disconnect()
    }

    @Test fun `refreshState re-reads the current pedal snapshot`() = runTest {
        val connection = FakePedalConnection()
        val repo = PedalRepository(connection, this)

        repo.connect()
        connection.selectPreset(0x08)
        repo.refreshState()
        advanceUntilIdle()

        val state = repo.state.value as ConnectionState.Connected
        assertEquals(Slot.B, state.pedal.activeSlot)
        repo.disconnect()
    }

    @Test fun `refreshState preserves the last confirmed slot over unreliable parsed snapshot`() = runTest {
        val connection = FakePedalConnection()
        val repo = PedalRepository(connection, this)

        repo.connect()
        repo.selectSlot(Slot.C)
        repo.refreshState()
        advanceUntilIdle()

        val state = repo.state.value as ConnectionState.Connected
        assertEquals(Slot.C, state.pedal.activeSlot)
        repo.disconnect()
    }

    @Test fun `selectSlot keeps using the trusted preset ids when later snapshots drift`() = runTest {
        val connection = DriftingPresetMapConnection()
        val repo = PedalRepository(connection, this)

        repo.connect()
        repo.selectSlot(Slot.B)
        repo.refreshState()
        repo.selectSlot(Slot.B)

        val state = repo.state.value as ConnectionState.Connected
        assertEquals(listOf(0x03, 0x0C, 0x0B), state.pedal.presetIds)
        repo.disconnect()
    }
}
