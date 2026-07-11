package com.opentonex.controller.midi

/** Mapa imutavel de entrada MIDI -> acao. CC usa 0..127; PC usa 128..255. */
data class MidiMapping(val ccToAction: Map<Int, MidiAction>) {

    fun actionFor(cc: Int): MidiAction? = ccToAction[cc]

    fun ccFor(action: MidiAction): Int? =
        ccToAction.entries.firstOrNull { it.value == action }?.key

    /** Aprende [cc] para [action], removendo o CC antigo da acao e a acao antiga do CC. */
    fun withLearned(action: MidiAction, cc: Int): MidiMapping {
        val cleaned = ccToAction.filterValues { it != action }.filterKeys { it != cc }
        return MidiMapping(cleaned + (cc to action))
    }

    companion object {
        val DEFAULT = MidiMapping(
            mapOf(
                20 to MidiAction.SELECT_SLOT_A,
                21 to MidiAction.SELECT_SLOT_B,
                22 to MidiAction.SELECT_SLOT_C,
                23 to MidiAction.NEXT_PRESET,
                24 to MidiAction.PREV_PRESET,
                25 to MidiAction.TOGGLE_BYPASS,
                26 to MidiAction.TOGGLE_CAB,
                27 to MidiAction.TOGGLE_GATE,
                28 to MidiAction.TOGGLE_COMP,
                29 to MidiAction.TOGGLE_EQ,
                30 to MidiAction.TOGGLE_MOD,
                31 to MidiAction.TOGGLE_DELAY,
                32 to MidiAction.TOGGLE_REVERB,
                102 to MidiAction.AMP_BASS,
                103 to MidiAction.AMP_MID,
                104 to MidiAction.AMP_TREBLE,
                105 to MidiAction.AMP_GAIN,
                106 to MidiAction.AMP_VOLUME
            )
        )
    }
}

/**
 * Codec texto simples ("cc=ACTION;cc=ACTION") em vez de JSON: sem dependencia nova e
 * testavel em JVM puro (org.json devolve stubs em unit test com returnDefaultValues).
 * Qualquer corrupcao devolve o mapa padrao (fail-safe do spec).
 */
object MidiMappingCodec {
    fun encode(mapping: MidiMapping): String =
        mapping.ccToAction.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value.name}" }

    fun decode(encoded: String?): MidiMapping {
        if (encoded.isNullOrBlank()) return MidiMapping.DEFAULT
        val result = mutableMapOf<Int, MidiAction>()
        for (entry in encoded.split(";")) {
            val parts = entry.split("=")
            if (parts.size != 2) return MidiMapping.DEFAULT
            val cc = parts[0].toIntOrNull() ?: return MidiMapping.DEFAULT
            if (cc !in 0..255) return MidiMapping.DEFAULT
            val action = runCatching { MidiAction.valueOf(parts[1]) }.getOrNull()
                ?: return MidiMapping.DEFAULT
            result[cc] = action
        }
        return MidiMapping(result)
    }
}

private const val PROGRAM_CHANGE_OFFSET = 128

internal fun programChangeKey(program: Int): Int = PROGRAM_CHANGE_OFFSET + program

internal fun midiMappingLabel(key: Int): String =
    if (key >= PROGRAM_CHANGE_OFFSET) "PC ${key - PROGRAM_CHANGE_OFFSET}" else "CC $key"
