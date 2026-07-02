package com.opentonex.controller.midi

/** Mensagens MIDI 1.0 que o app entende. Todo o resto e ignorado pelo parser. */
sealed interface MidiMessage {
    data class ProgramChange(val channel: Int, val program: Int) : MidiMessage
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiMessage
}
