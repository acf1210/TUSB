package com.opentonex.controller.midi

/**
 * Acoes do app que podem ser disparadas por Control Change. Program Change NAO esta aqui
 * de proposito: PC n carrega sempre o preset n (0..19), fixo e nao remapeavel.
 *
 * [label] usa jargao de guitarra identico em PT/EN/ES, por isso nao e um string resource.
 */
enum class MidiAction(val label: String, val isContinuous: Boolean = false) {
    SELECT_SLOT_A("Slot A"),
    SELECT_SLOT_B("Slot B"),
    SELECT_SLOT_C("Slot C"),
    NEXT_PRESET("Preset +"),
    PREV_PRESET("Preset -"),
    TOGGLE_BYPASS("Bypass"),
    TOGGLE_CAB("Cab / IR"),
    TOGGLE_GATE("Gate"),
    TOGGLE_COMP("Comp"),
    TOGGLE_EQ("EQ"),
    TOGGLE_MOD("Mod"),
    TOGGLE_DELAY("Delay"),
    TOGGLE_REVERB("Reverb"),
    AMP_BASS("Bass", isContinuous = true),
    AMP_MID("Mid", isContinuous = true),
    AMP_TREBLE("Treble", isContinuous = true),
    AMP_GAIN("Gain", isContinuous = true),
    AMP_VOLUME("Volume", isContinuous = true)
}
