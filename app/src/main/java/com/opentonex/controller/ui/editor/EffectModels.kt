package com.opentonex.controller.ui.editor

/**
 * Modelos selecionaveis dos blocos MOD/DLY/REV, na ordem dos valores do parametro *_MODEL
 * (tonex_params 65/96/38, ver TonexEffectParams). Os nomes seguem a tabela do firmware de
 * referencia; a ordem define o indice escrito no pedal, entao so mude os dois juntos.
 */
object EffectModels {
    private val reverb = listOf("Spring 1", "Spring 2", "Spring 3", "Spring 4", "Room", "Plate")
    private val modulation = listOf("Chorus", "Tremolo", "Phaser", "Flanger", "Rotary")
    private val delay = listOf("Digital", "Tape")

    /** Nomes dos modelos do [effect]; vazio para blocos sem escolha de modelo. */
    fun namesFor(effect: EffectSlotType): List<String> = when (effect) {
        EffectSlotType.REV -> reverb
        EffectSlotType.MOD -> modulation
        EffectSlotType.DLY -> delay
        else -> emptyList()
    }

    /**
     * Rotulos dos 3 knobs de modulacao conforme o modelo ativo; espelham o mapeamento de
     * TonexEffectParams.modulationRate/Depth/Level (tremolo: shape; rotary: speed/radius).
     */
    fun modulationKnobLabels(model: Int): Triple<String, String, String> = when (model) {
        1 -> Triple("RATE", "SHAPE", "LEVEL")
        4 -> Triple("SPEED", "RADIUS", "LEVEL")
        else -> Triple("RATE", "DEPTH", "LEVEL")
    }
}
