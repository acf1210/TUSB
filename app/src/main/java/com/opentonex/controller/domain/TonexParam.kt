package com.opentonex.controller.domain

/**
 * Parametros de preset do ToneX One com indice na tabela `tonex_params` (a ordem do bloco
 * de floats no detalhe de preset 0x0304 e o indice usado nas mensagens 0x0309) e range real.
 *
 * Fonte dos indices/ranges: firmware de referencia Builty/TonexOneController
 * (`tonex_params.{h,c}`). O indice 21 (volume, range 0..10) foi validado contra captura
 * real de knob fisico deste projeto; a escrita 0x0309 foi validada em bancada.
 * Ver docs/protocol-notes.md.
 */
enum class TonexParam(
    val index: Int,
    val min: Float,
    val max: Float,
    /** ID canonico usado no mapa de parametros do preset (ver AmpKnob.parameterIds). */
    val parameterId: String
) {
    NOISE_GATE_ENABLE(1, 0f, 1f, "ParameterXNoiseGateEnable"),
    COMP_ENABLE(6, 0f, 1f, "ParameterXCompEnable"),
    COMP_MAKE_UP(8, -30f, 10f, "ParameterXCompMakeUp"),
    EQ_BASS(11, 0f, 10f, "ParameterXEqBass"),
    EQ_MID(13, 0f, 10f, "ParameterXEqMid"),
    EQ_TREBLE(16, 0f, 10f, "ParameterXEqTreble"),
    MODEL_AMP_ENABLE(18, 0f, 1f, "ParameterXModelAmpEnable"),
    MODEL_GAIN(20, 0f, 10f, "ParameterXModelGain"),
    MODEL_VOLUME(21, 0f, 10f, "ParameterXModelVolume"),
    /** SELECT: 0=cab do Tone Model, 1=VIR, 2=desativado (Builty TONEX_PARAM_CABINET_TYPE). */
    CABINET_TYPE(24, 0f, 2f, "ParameterXCabinetType"),
    /** SELECT 0..10: modelo de gabinete VIR (nomes so existem no app oficial). */
    VIR_CABINET_MODEL(25, 0f, 10f, "ParameterXVirCabinetModel"),
    REVERB_ENABLE(37, 0f, 1f, "ParameterXReverbEnable"),
    REVERB_MODEL(38, 0f, 5f, "ParameterXReverbModel"),
    MODULATION_ENABLE(64, 0f, 1f, "ParameterXModulationEnable"),
    MODULATION_MODEL(65, 0f, 4f, "ParameterXModulationModel"),
    DELAY_ENABLE(95, 0f, 1f, "ParameterXDelayEnable"),
    DELAY_MODEL(96, 0f, 1f, "ParameterXDelayModel");

    /** Converte um valor normalizado 0..1 da UI para o valor real esperado pelo pedal. */
    fun denormalize(normalized: Float): Float =
        min + normalized.coerceIn(0f, 1f) * (max - min)

    /** Converte um valor real do pedal para o 0..1 usado pelos knobs da UI. */
    fun normalize(value: Float): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

    companion object {
        fun fromIndex(index: Int): TonexParam? = entries.firstOrNull { it.index == index }
    }
}

/**
 * Binding de um controle da UI para um indice REAL da tabela tonex_params, com range.
 * Necessario porque reverb/modulacao/delay tem um conjunto de indices POR MODELO
 * (spring1..4/room/plate; chorus/tremolo/phaser/flanger/rotary; digital/tape).
 */
data class TonexParamBinding(val index: Int, val min: Float, val max: Float) {
    fun denormalize(normalized: Float): Float =
        min + normalized.coerceIn(0f, 1f) * (max - min)

    fun normalize(value: Float): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)
}

/**
 * Resolve os controles de cada bloco da cadeia de efeitos para indices reais, seguindo o
 * layout da tabela tonex_params do firmware de referencia (Builty). Os blocos com modelo
 * selecionavel tem os indices calculados a partir do valor atual do parametro *_MODEL.
 */
object TonexEffectParams {
    // Noise gate (indices fixos)
    val GATE_THRESHOLD = TonexParamBinding(2, -100f, 0f)
    val GATE_RELEASE = TonexParamBinding(3, 5f, 500f)
    val GATE_DEPTH = TonexParamBinding(4, -100f, -20f)

    // Compressor (indices fixos)
    val COMP_THRESHOLD = TonexParamBinding(7, -40f, 0f)
    val COMP_MAKE_UP = TonexParamBinding(8, -30f, 10f)
    val COMP_ATTACK = TonexParamBinding(9, 1f, 51f)

    // EQ (indices fixos)
    val EQ_BASS_FREQ = TonexParamBinding(12, 75f, 600f)
    val EQ_MID_FREQ = TonexParamBinding(15, 150f, 5000f)
    val EQ_TREBLE_FREQ = TonexParamBinding(17, 1000f, 4000f)

    // Cabinet VIR (indices fixos)
    val VIR_RESO = TonexParamBinding(26, 0f, 10f)
    val VIR_MIC_1_X = TonexParamBinding(28, 0f, 10f)
    val VIR_BLEND = TonexParamBinding(33, -100f, 100f)

    // Posicao pre/post de cada bloco (0=pre, 1=post); reverb usa REVERB_POSITION.
    val GATE_POST = TonexParamBinding(0, 0f, 1f)
    val COMP_POST = TonexParamBinding(5, 0f, 1f)
    val EQ_POST = TonexParamBinding(10, 0f, 1f)
    val REVERB_POSITION = TonexParamBinding(36, 0f, 1f)
    val MODULATION_POST = TonexParamBinding(63, 0f, 1f)
    val DELAY_POST = TonexParamBinding(94, 0f, 1f)

    /**
     * Reverb: modelos 0..5 (spring1..4, room, plate), cada um com 4 floats consecutivos
     * TIME/PREDELAY/COLOR/MIX a partir do indice 39.
     */
    fun reverbTime(model: Int) = TonexParamBinding(reverbBase(model), 0f, 10f)
    fun reverbPredelay(model: Int) = TonexParamBinding(reverbBase(model) + 1, 0f, 500f)
    fun reverbMix(model: Int) = TonexParamBinding(reverbBase(model) + 3, 0f, 100f)
    private fun reverbBase(model: Int) = 39 + model.coerceIn(0, 5) * 4

    /**
     * Modulacao: modelos 0..4 (chorus, tremolo, phaser, flanger, rotary), com conjuntos de
     * tamanhos DIFERENTES (5/6/5/6/6 params) - por isso a tabela explicita de bases.
     * Os tres controles da UI (RATE/DEPTH/MIX) mapeiam para o parametro equivalente
     * de cada modelo (tremolo: shape; rotary: speed/radius).
     */
    fun modulationRate(model: Int): TonexParamBinding = when (model.coerceIn(0, 4)) {
        0 -> TonexParamBinding(68, 0.1f, 10f)  // chorus rate
        1 -> TonexParamBinding(73, 0.1f, 10f)  // tremolo rate
        2 -> TonexParamBinding(79, 0.1f, 10f)  // phaser rate
        3 -> TonexParamBinding(84, 0.1f, 10f)  // flanger rate
        else -> TonexParamBinding(90, 0f, 400f) // rotary speed
    }

    fun modulationDepth(model: Int): TonexParamBinding = when (model.coerceIn(0, 4)) {
        0 -> TonexParamBinding(69, 0f, 100f)   // chorus depth
        1 -> TonexParamBinding(74, 0f, 10f)    // tremolo shape
        2 -> TonexParamBinding(80, 0f, 100f)   // phaser depth
        3 -> TonexParamBinding(85, 0f, 100f)   // flanger depth
        else -> TonexParamBinding(91, 0f, 300f) // rotary radius
    }

    fun modulationLevel(model: Int): TonexParamBinding = when (model.coerceIn(0, 4)) {
        0 -> TonexParamBinding(70, 0f, 10f)    // chorus level
        1 -> TonexParamBinding(76, 0f, 10f)    // tremolo level
        2 -> TonexParamBinding(81, 0f, 10f)    // phaser level
        3 -> TonexParamBinding(87, 0f, 10f)    // flanger level
        else -> TonexParamBinding(93, 0f, 10f)  // rotary level
    }

    /**
     * Delay: modelos 0..1 (digital, tape), cada um com 6 floats consecutivos
     * SYNC/TS/TIME/FEEDBACK/MODE/MIX a partir do indice 97.
     */
    fun delayTime(model: Int) = TonexParamBinding(delayBase(model) + 2, 0f, 1000f)
    fun delayFeedback(model: Int) = TonexParamBinding(delayBase(model) + 3, 0f, 100f)
    fun delayMix(model: Int) = TonexParamBinding(delayBase(model) + 5, 0f, 100f)
    fun delaySync(model: Int) = TonexParamBinding(delayBase(model), 0f, 1f)
    /** MODE: 0=normal, 1=ping-pong. */
    fun delayMode(model: Int) = TonexParamBinding(delayBase(model) + 4, 0f, 1f)
    private fun delayBase(model: Int) = 97 + model.coerceIn(0, 1) * 6
}
