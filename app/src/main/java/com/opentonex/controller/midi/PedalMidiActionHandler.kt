package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.PedalViewModel
import com.opentonex.controller.ui.editor.EffectSlotType

/**
 * Liga o MIDI aos metodos ja existentes do PedalViewModel. Sem pedal conectado os
 * metodos do ViewModel ja fazem no-op; o gate busy do ViewModel absorve rajadas.
 * Deve ser chamado na main thread.
 */
class PedalMidiActionHandler(private val viewModel: PedalViewModel) : MidiActionHandler {

    override fun selectSlot(slot: Slot) = viewModel.selectSlot(slot)

    override fun loadPreset(presetId: Int) = viewModel.loadPresetToActiveSlot(presetId)

    override fun activePresetId(): Int? {
        val connected = viewModel.state.value as? ConnectionState.Connected ?: return null
        return connected.pedal.presetIds.getOrNull(connected.pedal.activeSlot.ordinal)
    }

    override fun toggleBypass() = viewModel.toggleBypass()

    override fun toggleCab() = viewModel.toggleCabSimBypass()

    override fun toggleEffect(effect: EffectSlotType) = viewModel.toggleEffectEnabled(effect)

    override fun setAmpKnob(knob: AmpKnob, normalized: Float) =
        viewModel.updateAmpKnob(knob, normalized)
}
