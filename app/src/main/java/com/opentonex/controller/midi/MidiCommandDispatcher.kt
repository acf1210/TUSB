package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.editor.EffectSlotType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Porta de saida do MIDI para o app; implementada por PedalMidiActionHandler. */
interface MidiActionHandler {
    fun selectSlot(slot: Slot)
    fun loadPreset(presetId: Int)
    fun activePresetId(): Int?
    fun toggleBypass()
    fun toggleCab()
    fun toggleEffect(effect: EffectSlotType)
    fun setAmpKnob(knob: AmpKnob, normalized: Float)
}

/**
 * Traduz mensagens MIDI parseadas em acoes do app usando o mapping corrente.
 * PC n carrega o preset n (fixo). CCs consultam o mapping. Deve ser chamado na
 * main thread (MidiInputManager posta os callbacks nela).
 */
class MidiCommandDispatcher(
    private val handler: MidiActionHandler,
    private val mappingProvider: () -> MidiMapping
) {
    /** Acao armada para MIDI Learn; null quando desarmado. */
    private val _learnTarget = MutableStateFlow<MidiAction?>(null)
    val learnTarget: StateFlow<MidiAction?> = _learnTarget.asStateFlow()

    /** Ultima mensagem recebida, para debug de mapeamento na tela MIDI. */
    private val _lastMessage = MutableStateFlow<MidiMessage?>(null)
    val lastMessage: StateFlow<MidiMessage?> = _lastMessage.asStateFlow()

    /** Chamado quando o Learn captura um CC; o dono persiste no MidiMappingStore. */
    var onLearned: ((MidiAction, Int) -> Unit)? = null

    fun startLearn(action: MidiAction) {
        _learnTarget.value = action
    }

    fun cancelLearn() {
        _learnTarget.value = null
    }

    fun dispatch(message: MidiMessage) {
        _lastMessage.value = message
        val learn = _learnTarget.value
        if (learn != null) {
            val key = when (message) {
                is MidiMessage.ControlChange -> message.controller
                is MidiMessage.ProgramChange -> programChangeKey(message.program)
            }
            _learnTarget.value = null
            onLearned?.invoke(learn, key)
            return
        }
        when (message) {
            is MidiMessage.ProgramChange -> {
                val action = mappingProvider().actionFor(programChangeKey(message.program))
                if (action != null) perform(action, 127) else if (message.program in 0 until PRESET_COUNT) {
                    handler.loadPreset(message.program)
                }
            }
            is MidiMessage.ControlChange -> {
                val action = mappingProvider().actionFor(message.controller) ?: return
                perform(action, message.value)
            }
        }
    }

    private fun perform(action: MidiAction, value: Int) {
        if (action.isContinuous) {
            val normalized = value / 127f
            when (action) {
                MidiAction.AMP_BASS -> handler.setAmpKnob(AmpKnob.BASS, normalized)
                MidiAction.AMP_MID -> handler.setAmpKnob(AmpKnob.MID, normalized)
                MidiAction.AMP_TREBLE -> handler.setAmpKnob(AmpKnob.TREBLE, normalized)
                MidiAction.AMP_GAIN -> handler.setAmpKnob(AmpKnob.GAIN, normalized)
                MidiAction.AMP_VOLUME -> handler.setAmpKnob(AmpKnob.VOLUME, normalized)
                else -> Unit
            }
            return
        }
        // Toggles/selecoes: borda de subida (footswitch momentaneo manda 127 e depois 0).
        if (value < TOGGLE_THRESHOLD) return
        when (action) {
            MidiAction.SELECT_SLOT_A -> handler.selectSlot(Slot.A)
            MidiAction.SELECT_SLOT_B -> handler.selectSlot(Slot.B)
            MidiAction.SELECT_SLOT_C -> handler.selectSlot(Slot.C)
            MidiAction.NEXT_PRESET -> stepPreset(+1)
            MidiAction.PREV_PRESET -> stepPreset(-1)
            MidiAction.TOGGLE_BYPASS -> handler.toggleBypass()
            MidiAction.TOGGLE_CAB -> handler.toggleCab()
            MidiAction.TOGGLE_GATE -> handler.toggleEffect(EffectSlotType.GATE)
            MidiAction.TOGGLE_COMP -> handler.toggleEffect(EffectSlotType.CMP)
            MidiAction.TOGGLE_EQ -> handler.toggleEffect(EffectSlotType.EQ)
            MidiAction.TOGGLE_MOD -> handler.toggleEffect(EffectSlotType.MOD)
            MidiAction.TOGGLE_DELAY -> handler.toggleEffect(EffectSlotType.DLY)
            MidiAction.TOGGLE_REVERB -> handler.toggleEffect(EffectSlotType.REV)
            else -> Unit
        }
    }

    private fun stepPreset(delta: Int) {
        val current = handler.activePresetId() ?: return
        val next = (current + delta + PRESET_COUNT) % PRESET_COUNT
        handler.loadPreset(next)
    }

    private companion object {
        const val PRESET_COUNT = 20
        const val TOGGLE_THRESHOLD = 64
    }
}
