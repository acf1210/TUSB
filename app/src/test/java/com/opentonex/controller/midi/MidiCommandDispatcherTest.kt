package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.editor.EffectSlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHandler : MidiActionHandler {
    val calls = mutableListOf<String>()
    var activePreset: Int? = 4
    override fun selectSlot(slot: Slot) { calls += "slot:${slot.name}" }
    override fun loadPreset(presetId: Int) { calls += "preset:$presetId" }
    override fun activePresetId(): Int? = activePreset
    override fun toggleBypass() { calls += "bypass" }
    override fun toggleCab() { calls += "cab" }
    override fun toggleEffect(effect: EffectSlotType) { calls += "effect:${effect.name}" }
    override fun setAmpKnob(knob: AmpKnob, normalized: Float) { calls += "knob:${knob.name}:$normalized" }
}

class MidiCommandDispatcherTest {

    private fun dispatcher(handler: FakeHandler, mapping: MidiMapping = MidiMapping.DEFAULT) =
        MidiCommandDispatcher(handler) { mapping }

    @Test
    fun `program change loads preset`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ProgramChange(0, 7))
        assertEquals(listOf("preset:7"), handler.calls)
    }

    @Test
    fun `program change out of range is ignored`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ProgramChange(0, 20))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `cc toggle fires only on value at or above 64`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 25, 0))
        d.dispatch(MidiMessage.ControlChange(0, 25, 63))
        assertTrue(handler.calls.isEmpty())
        d.dispatch(MidiMessage.ControlChange(0, 25, 64))
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass", "bypass"), handler.calls)
    }

    @Test
    fun `slot and effect ccs dispatch to handler`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 20, 127))
        d.dispatch(MidiMessage.ControlChange(0, 22, 127))
        d.dispatch(MidiMessage.ControlChange(0, 26, 127))
        d.dispatch(MidiMessage.ControlChange(0, 27, 127))
        d.dispatch(MidiMessage.ControlChange(0, 32, 127))
        assertEquals(
            listOf("slot:A", "slot:C", "cab", "effect:GATE", "effect:REV"),
            handler.calls
        )
    }

    @Test
    fun `continuous cc maps full range to knob`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 105, 0))
        d.dispatch(MidiMessage.ControlChange(0, 105, 127))
        assertEquals(listOf("knob:GAIN:0.0", "knob:GAIN:1.0"), handler.calls)
    }

    @Test
    fun `next preset wraps around at 19`() {
        val handler = FakeHandler()
        handler.activePreset = 19
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 23, 127))
        assertEquals(listOf("preset:0"), handler.calls)
    }

    @Test
    fun `prev preset wraps around at 0`() {
        val handler = FakeHandler()
        handler.activePreset = 0
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 24, 127))
        assertEquals(listOf("preset:19"), handler.calls)
    }

    @Test
    fun `next preset without active preset is ignored`() {
        val handler = FakeHandler()
        handler.activePreset = null
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 23, 127))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `unmapped cc is ignored`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 64, 127))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `learn mode captures next cc instead of dispatching`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        var learned: Pair<MidiAction, Int>? = null
        d.onLearned = { action, cc -> learned = action to cc }
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.dispatch(MidiMessage.ControlChange(0, 45, 127))
        assertEquals(MidiAction.TOGGLE_CAB to 45, learned)
        assertTrue(handler.calls.isEmpty())
        assertNull(d.learnTarget.value)
        // Depois de aprender, dispatch volta ao normal.
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass"), handler.calls)
    }

    @Test
    fun `learn mode captures program change`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        var learned: Pair<MidiAction, Int>? = null
        d.onLearned = { action, key -> learned = action to key }
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.dispatch(MidiMessage.ProgramChange(0, 3))
        assertEquals(MidiAction.TOGGLE_CAB to programChangeKey(3), learned)
        assertNull(d.learnTarget.value)
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `mapped program change dispatches mapped action instead of loading preset`() {
        val handler = FakeHandler()
        val mapping = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, programChangeKey(3))
        dispatcher(handler, mapping).dispatch(MidiMessage.ProgramChange(0, 3))
        assertEquals(listOf("bypass"), handler.calls)
    }

    @Test
    fun `cancelLearn disarms learn mode`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.cancelLearn()
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass"), handler.calls)
    }
}
