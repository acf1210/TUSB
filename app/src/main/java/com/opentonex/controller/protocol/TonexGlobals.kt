package com.opentonex.controller.protocol

/**
 * Ajustes globais do ToneX One. Os que vivem no StateData (0x0306) sao gravados mudando os
 * bytes no lugar e reenviando o estado com o header de comando, como a troca de slot.
 *
 * Offsets: firmware de referencia Builty (`usb_tonex_one.c`, `usb_tonex_one_modify_global`),
 * conferidos byte a byte contra o estado real do pedal (`captures/tonex-session-1782929439644
 * .jsonl`: trim +4,0 dB, A4 440 Hz, 166,4 BPM, tempo global, afinador em thru). Todos os
 * campos tem tamanho fixo, entao escrever no lugar nao desloca os offsets contados do fim.
 */
enum class GlobalSetting(val min: Float, val max: Float) {
    /** dB, float. StateData[14] = tag 0x88, [15..18] = valor. */
    INPUT_TRIM(-15f, 15f),
    /** Hz, u16. StateData[len-10] = tag, [len-9..len-8] = valor. */
    TUNER_REFERENCE(415f, 465f),
    /** BPM, float. StateData[len-5] = tag 0x88, [len-4..len-1] = valor. */
    TEMPO(40f, 240f),
    /** StateData[len-6]: 0 = tempo global, 1 = tempo do preset. */
    TEMPO_SOURCE(0f, 1f),
    /** StateData[21]: 0 = afinador muta a saida, 1 = som passa (thru). */
    TUNER_OUTPUT(0f, 1f)
}

object TonexGlobals {
    private const val HEADER = 8 // header de RESPOSTA do 0x0306 (ver TonexMessages)
    private const val FLOAT_TAG = 0x88
    private const val U16_TAG = 0x81

    private const val TRIM_TAG_BODY = 14
    private const val TUNER_OUTPUT_BODY = 21
    private const val TUNER_REF_TAG_END = 10
    private const val TEMPO_SOURCE_END = 6
    private const val TEMPO_TAG_END = 5

    /** Le [setting] de um StateResponse completo; null se o layout nao for o esperado. */
    fun read(rawState: ByteArray, setting: GlobalSetting): Float? {
        val pos = position(rawState, setting) ?: return null
        return when (setting) {
            GlobalSetting.INPUT_TRIM, GlobalSetting.TEMPO -> TaggedValue.decodeFloat(rawState, pos).value
            GlobalSetting.TUNER_REFERENCE -> TaggedValue.decodeU16(rawState, pos).value.toFloat()
            GlobalSetting.TEMPO_SOURCE, GlobalSetting.TUNER_OUTPUT -> (rawState[pos].toInt() and 0xFF).toFloat()
        }
    }

    /**
     * Copia de [rawState] com [setting] = [value] (limitado a faixa do ajuste). Lanca
     * [IllegalArgumentException] se o byte de tag nao estiver onde deveria: nesse caso o
     * estado nao e o layout conhecido e reenvia-lo poderia gravar lixo no pedal.
     */
    fun write(rawState: ByteArray, setting: GlobalSetting, value: Float): ByteArray {
        val pos = requireNotNull(position(rawState, setting)) {
            "estado sem o campo $setting no layout esperado"
        }
        val clamped = value.coerceIn(setting.min, setting.max)
        val updated = rawState.copyOf()
        val encoded = when (setting) {
            GlobalSetting.INPUT_TRIM, GlobalSetting.TEMPO -> TaggedValue.encodeFloat(clamped)
            GlobalSetting.TUNER_REFERENCE -> TaggedValue.encodeU16(Math.round(clamped), U16_TAG)
            GlobalSetting.TEMPO_SOURCE, GlobalSetting.TUNER_OUTPUT -> byteArrayOf(Math.round(clamped).toByte())
        }
        encoded.copyInto(updated, pos)
        return updated
    }

    /** Posicao absoluta do campo (o byte de tag, para os valores com tag) ou null. */
    private fun position(rawState: ByteArray, setting: GlobalSetting): Int? {
        if (rawState.size <= HEADER + TUNER_OUTPUT_BODY + TUNER_REF_TAG_END) return null
        val end = rawState.size
        fun tagged(pos: Int, tag: Int) = pos.takeIf { (rawState[it].toInt() and 0xFF) == tag }
        return when (setting) {
            GlobalSetting.INPUT_TRIM -> tagged(HEADER + TRIM_TAG_BODY, FLOAT_TAG)
            GlobalSetting.TUNER_REFERENCE -> tagged(end - TUNER_REF_TAG_END, U16_TAG)
            GlobalSetting.TEMPO -> tagged(end - TEMPO_TAG_END, FLOAT_TAG)
            // Bytes crus sem tag: so confiamos neles se os vizinhos com tag estiverem no lugar.
            GlobalSetting.TEMPO_SOURCE -> (end - TEMPO_SOURCE_END).takeIf {
                tagged(end - TEMPO_TAG_END, FLOAT_TAG) != null && tagged(end - TUNER_REF_TAG_END, U16_TAG) != null
            }
            GlobalSetting.TUNER_OUTPUT -> (HEADER + TUNER_OUTPUT_BODY).takeIf {
                tagged(HEADER + TRIM_TAG_BODY, FLOAT_TAG) != null
            }
        }
    }

    // --- Volume master: comando proprio, fora do StateData (Builty send/request_master_volume;
    // pedido e resposta conferidos no trafego do app oficial, tonex_full_session.pcap). ---

    private val MASTER_VOLUME_PREFIX = byteArrayOf(0xB9.toByte(), 0x04, 0x03, 0x00, 0x00, FLOAT_TAG.toByte())
    const val MASTER_VOLUME_MIN = 0f
    const val MASTER_VOLUME_MAX = 10f

    /** Escreve o volume master (0..10): mesmo envelope do 0x0309, subtipo 0x03 em vez de 0x02. */
    fun buildSetMasterVolumePayload(value: Float): ByteArray {
        val bits = value.coerceIn(MASTER_VOLUME_MIN, MASTER_VOLUME_MAX).toRawBits()
        return byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x09, 0x03, 0x82.toByte(), 0x0A, 0x00, 0x80.toByte(), 0x0B, 0x03
        ) + MASTER_VOLUME_PREFIX +
            byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
    }

    /** Pede o volume master atual; o pedal responde com um 0x0309 lido por [parseMasterVolume]. */
    fun requestMasterVolumePayload(): ByteArray = byteArrayOf(
        0xB9.toByte(), 0x03, 0x81.toByte(), 0x0D, 0x03, 0x82.toByte(), 0x05, 0x00, 0x80.toByte(), 0x0B, 0x03,
        0xB9.toByte(), 0x03, 0x03, 0x00, 0x00
    )

    /** Volume master de um 0x0309 do pedal, ou null se o frame for de outro parametro. */
    fun parseMasterVolume(payload: ByteArray): Float? {
        if (payload.size < 5 || TonexMessages.messageType(payload) != TonexMessages.PARAM_CHANGE_TYPE) return null
        for (i in 0..payload.size - MASTER_VOLUME_PREFIX.size - 4) {
            if (MASTER_VOLUME_PREFIX.indices.all { payload[i + it] == MASTER_VOLUME_PREFIX[it] }) {
                return TaggedValue.decodeFloat(payload, i + MASTER_VOLUME_PREFIX.size - 1).value
            }
        }
        return null
    }
}
