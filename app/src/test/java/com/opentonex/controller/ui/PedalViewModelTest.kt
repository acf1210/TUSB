package com.opentonex.controller.ui

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.PedalRuntimeEvent
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class BlockingSelectPedalConnection : PedalConnection {
    private val delegate = FakePedalConnection()
    private val selectGate = CompletableDeferred<Unit>()

    override val runtimeEvents: Flow<PedalRuntimeEvent> = emptyFlow()

    override suspend fun connect() = delegate.connect()

    override suspend fun handshake() = delegate.handshake()

    override suspend fun requestState(): PedalState = delegate.requestState()

    override suspend fun writeState(state: PedalState) {
        selectGate.await()
        delegate.writeState(state)
    }

    override suspend fun selectPreset(presetId: Int) {
        delegate.selectPreset(presetId)
    }

    override suspend fun loadPresetToSlot(currentState: PedalState, presetId: Int, slot: Slot, selectSlot: Boolean) =
        delegate.loadPresetToSlot(currentState, presetId, slot, selectSlot)

    override suspend fun switchMode(currentState: PedalState, targetMode: PedalMode) =
        delegate.switchMode(currentState, targetMode)

    override suspend fun writeParameter(paramIndex: Int, value: Float) =
        delegate.writeParameter(paramIndex, value)

    override suspend fun disconnect() = delegate.disconnect()

    fun finishSelection() {
        selectGate.complete(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test fun `updateAmpKnob writes the denormalized parameter to the pedal`() = runTest {
        val fake = FakePedalConnection()
        val viewModel = PedalViewModel()
        viewModel.connectWith(fake)
        advanceUntilIdle()

        viewModel.updateAmpKnob(AmpKnob.GAIN, 0.5f) // GAIN = indice 20, range 0..10
        advanceUntilIdle()

        assertEquals(5.0f, fake.parameters[20] ?: Float.NaN, 0.0001f)
        assertEquals(1, fake.parameterWriteCounts[20])
    }

    @Test fun `updateAmpKnob writes captured hardware volume parameter`() = runTest {
        val fake = FakePedalConnection()
        val viewModel = PedalViewModel()
        viewModel.connectWith(fake)
        advanceUntilIdle()

        viewModel.updateAmpKnob(AmpKnob.VOLUME, 0.5f)
        advanceUntilIdle()

        assertEquals(-10.0f, fake.parameters[8] ?: Float.NaN, 0.0001f)
        assertEquals(1, fake.parameterWriteCounts[8])
    }

    @Test fun `toggleEffectEnabled writes the enable parameter to the pedal`() = runTest {
        val fake = FakePedalConnection()
        val viewModel = PedalViewModel()
        viewModel.connectWith(fake)
        advanceUntilIdle()

        viewModel.toggleEffectEnabled(com.opentonex.controller.ui.editor.EffectSlotType.REV)
        advanceUntilIdle()

        // REVERB_ENABLE = indice 37; toggle a partir do default (true) escreve 0 (off).
        assertEquals(0f, fake.parameters[37] ?: Float.NaN, 0.0001f)
    }

    @Test fun `initial state is Disconnected`() {
        val viewModel = PedalViewModel()
        assertEquals(ConnectionState.Disconnected, viewModel.state.value)
    }

    @Test fun `updateAmpKnob changes local knob value and clamps range`() {
        val viewModel = PedalViewModel()

        viewModel.updateAmpKnob(AmpKnob.GAIN, 0.82f)
        assertEquals(0.82f, viewModel.ampKnobs.value.gain, 0.0001f)

        viewModel.updateAmpKnob(AmpKnob.GAIN, 2f)
        assertEquals(1f, viewModel.ampKnobs.value.gain, 0.0001f)

        viewModel.updateAmpKnob(AmpKnob.GAIN, -1f)
        assertEquals(0f, viewModel.ampKnobs.value.gain, 0.0001f)
    }

    @Test fun `startCapture updates capture ui state with target file`() = runTest {
        val viewModel = PedalViewModel()
        val captureDir = Files.createTempDirectory("tonex-viewmodel-capture-").toFile()

        viewModel.startCapture(File(captureDir, "event-captures"))

        assertTrue(viewModel.capture.value.isCapturing)
        assertTrue(viewModel.capture.value.currentFilePath!!.endsWith(".jsonl"))
        captureDir.deleteRecursively()
    }

    @Test fun `busy state blocks concurrent slot changes`() = runTest {
        val viewModel = PedalViewModel()
        val connection = BlockingSelectPedalConnection()
        viewModel.connectWith(connection)

        viewModel.selectSlot(Slot.B)
        assertTrue(viewModel.busy.value.isBusy)

        viewModel.selectSlot(Slot.C)
        connection.finishSelection()
        advanceUntilIdle()

        val state = viewModel.state.value as ConnectionState.Connected
        assertEquals(Slot.B, state.pedal.activeSlot)
    }
}
