package com.opentonex.controller.protocol

/** Corpo de preset completo (0x0303) cujos bytes nao casam com o layout conhecido. */
class FullPresetParseException(message: String) : Exception(message)

/**
 * Metadados legiveis de um preset completo (0x0303). [body] e o corpo exato devolvido pelo
 * pedal (sem o header de resposta), incluindo o blob do Tone Model; e o que vai para a
 * biblioteca do app e o que e regravado no pedal.
 */
data class FullPreset(
    val bank: Int,
    val index: Int,
    val presetName: String,
    val toneModelName: String,
    val toneModelCreator: String?,
    val toneModelDate: String?,
    val toneModelSize: Int,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is FullPreset && bank == other.bank && index == other.index && body.contentEquals(other.body)

    override fun hashCode(): Int = 31 * (31 * bank + index) + body.contentHashCode()
}

/**
 * Preset completo do ToneX One, com o Tone Model (tipo 0x0303, ~30 KB). Formato decodificado
 * de tonex_full_session.pcap: o app oficial pede cada preset ao conectar com o mesmo comando
 * 0x0300 da fase de metadados, mas com a fase 0x01. Ver docs/protocol-notes.md.
 *
 * Codificacao das tags: `B9 n` struct, `BA n` array, `BC n` bytes, `88` float LE,
 * `80 x` u8, `81 lo hi` u16, `82 lo hi` u16 (cabecalho de comando), < 0x80 int cru.
 */
object TonexFullPreset {
    const val FULL_PRESET_TYPE = 0x0303

    /** Banco dos presets salvos (0..19); o banco 0 e o preset em edicao no pedal. */
    const val BANK_STORED = 0x01
    const val BANK_EDIT_BUFFER = 0x00
    const val STORED_PRESET_COUNT = 20

    private const val PHASE_FULL = 0x01

    private const val TAG_STRUCT = 0xB9
    private const val TAG_ARRAY = 0xBA
    private const val TAG_BYTES = 0xBC
    private const val TAG_FLOAT = 0x88
    private const val TAG_U8 = 0x80
    private const val TAG_U16 = 0x81
    private const val TAG_U16_CMD = 0x82

    /** Offsets fixos no corpo: `B9 03 <banco> <indice> ...` (banco e indice < 0x80, crus). */
    private const val BODY_BANK_OFFSET = 2
    private const val BODY_INDEX_OFFSET = 3

    /**
     * Posicoes nos metadados do Tone Model (struct de 9), conferidas nos 20 presets da
     * captura: 3 = id curto do amp ("JCM"), 5 = data, 6 = autor ("IK Multimedia").
     */
    private const val MODEL_INFO_DATE = 5
    private const val MODEL_INFO_CREATOR = 6

    /** Tamanho do header de resposta: tipo (5B) + tamanho (`81 lo hi`) + origem (1B). */
    private const val RESPONSE_HEADER_LENGTH = 9

    private val TYPE_HEADER = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x03, 0x03)

    /** Pede o preset completo [index] do [bank] (mesmo envelope 0x0300 do app oficial). */
    fun requestPayload(bank: Int, index: Int): ByteArray {
        require(bank == BANK_STORED || bank == BANK_EDIT_BUFFER) { "banco invalido: $bank" }
        require(index in 0 until STORED_PRESET_COUNT) { "indice de preset fora de 0..19: $index" }
        return byteArrayOf(
            0xB9.toByte(), 0x03, 0x81.toByte(), 0x00, 0x03,
            0x82.toByte(), 0x06, 0x00, 0x80.toByte(), 0x0B, 0x03,
            0xB9.toByte(), 0x04, 0x0B, bank.toByte(), index.toByte(), PHASE_FULL.toByte()
        )
    }

    /** Extrai o corpo de uma resposta 0x0303 decodificada (sem framing HDLC). */
    fun bodyFromResponse(payload: ByteArray): ByteArray {
        if (payload.size <= RESPONSE_HEADER_LENGTH || TonexMessages.messageType(payload) != FULL_PRESET_TYPE) {
            throw FullPresetParseException("resposta nao e um preset completo 0x0303")
        }
        if ((payload[5].toInt() and 0xFF) != TAG_U16) {
            throw FullPresetParseException("tamanho do 0x0303 sem tag u16")
        }
        val declared = u16(payload, 6)
        val body = payload.copyOfRange(RESPONSE_HEADER_LENGTH, payload.size)
        if (declared != body.size) {
            throw FullPresetParseException("0x0303 declara ${declared}B mas trouxe ${body.size}B")
        }
        return body
    }

    /**
     * Grava [body] no preset salvo [targetIndex]. Mesmo padrao do 0x0306 e do 0x0309: o comando
     * reenvia o tipo da resposta com o sufixo `82 <len16> 80 0B 03` no lugar do header de
     * resposta. Validado em bancada (2026-10-19): copia do preset 1 para o 20 e restauracao,
     * ambas confirmadas relendo o 0x0303.
     */
    fun writePayload(body: ByteArray, targetIndex: Int): ByteArray {
        require(targetIndex in 0 until STORED_PRESET_COUNT) { "indice de preset fora de 0..19: $targetIndex" }
        parse(body) // recusa corpo fora do layout antes de mandar ao pedal
        val retargeted = body.copyOf()
        retargeted[BODY_BANK_OFFSET] = BANK_STORED.toByte()
        retargeted[BODY_INDEX_OFFSET] = targetIndex.toByte()
        val size = retargeted.size
        require(size <= 0xFFFF) { "preset grande demais: ${size}B" }
        return TYPE_HEADER + byteArrayOf(
            TAG_U16_CMD.toByte(), (size and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte(),
            0x80.toByte(), 0x0B, 0x03
        ) + retargeted
    }

    /** Compara o conteudo de dois corpos ignorando banco e indice (usado na verificacao). */
    fun sameContent(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size || a.size <= BODY_INDEX_OFFSET) return false
        for (i in a.indices) {
            if (i == BODY_BANK_OFFSET || i == BODY_INDEX_OFFSET) continue
            if (a[i] != b[i]) return false
        }
        return true
    }

    /**
     * Valida [body] contra o layout inteiro (a arvore de tags precisa consumir o corpo todo)
     * e extrai os metadados. Lanca [FullPresetParseException] para qualquer outro formato,
     * inclusive arquivos importados corrompidos ou de outro aparelho.
     */
    fun parse(body: ByteArray): FullPreset {
        val root = try {
            Reader(body).let { reader ->
                val node = reader.node()
                if (reader.offset != body.size) {
                    throw FullPresetParseException("sobraram ${body.size - reader.offset}B apos o preset")
                }
                node
            }
        } catch (e: IndexOutOfBoundsException) {
            throw FullPresetParseException("preset truncado")
        }
        val top = root.struct(3, "raiz")
        val bank = top[0].int("banco")
        val index = top[1].int("indice")
        if (body[BODY_BANK_OFFSET].toInt() != bank || body[BODY_INDEX_OFFSET].toInt() != index) {
            throw FullPresetParseException("banco/indice fora da posicao esperada")
        }
        val preset = top[2].struct(4, "preset")
        val header = preset[0].struct(4, "cabecalho")
        val presetName = header[0].struct(2, "nome do preset")[0].text("nome do preset")
        val model = preset[1].struct(5, "tone model")
        val modelName = model[1].struct(2, "nome do tone model")[0].text("nome do tone model")
        val blob = (model[3] as? Node.Bytes)?.value
            ?: throw FullPresetParseException("tone model sem blob")
        val modelInfo = model[4].struct(9, "metadados do tone model")
        return FullPreset(
            bank = bank,
            index = index,
            presetName = presetName,
            toneModelName = modelName,
            toneModelCreator = modelInfo.optionalText(MODEL_INFO_CREATOR),
            toneModelDate = modelInfo.optionalText(MODEL_INFO_DATE),
            toneModelSize = blob.size,
            body = body
        )
    }

    private sealed interface Node {
        data class IntValue(val value: Int) : Node
        data class FloatValue(val value: Float) : Node
        class Bytes(val value: ByteArray) : Node
        class Group(val items: List<Node>) : Node
    }

    private fun Node.struct(size: Int, what: String): List<Node> {
        val items = (this as? Node.Group)?.items
        if (items == null || items.size != size) throw FullPresetParseException("$what fora do layout")
        return items
    }

    private fun Node.int(what: String): Int =
        (this as? Node.IntValue)?.value ?: throw FullPresetParseException("$what nao e inteiro")

    /** Texto ASCII terminado em zero; bytes apos o terminador sao lixo de memoria do pedal. */
    private fun Node.text(what: String): String {
        val bytes = (this as? Node.Bytes)?.value ?: throw FullPresetParseException("$what nao e texto")
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return String(bytes, 0, end, Charsets.ISO_8859_1).trim()
    }

    private fun List<Node>.optionalText(position: Int): String? =
        ((getOrNull(position) as? Node.Group)?.items?.firstOrNull() as? Node.Bytes)
            ?.let { node -> runCatching { node.text("texto") }.getOrNull() }
            ?.takeIf { text -> text.isNotEmpty() && text.all { it.code in 0x20..0x7E } }

    private class Reader(private val data: ByteArray) {
        var offset = 0

        fun node(): Node {
            val tag = data[offset].toInt() and 0xFF
            return when (tag) {
                TAG_FLOAT -> {
                    val bits = u32(data, offset + 1)
                    offset += 5
                    Node.FloatValue(Float.fromBits(bits))
                }
                TAG_STRUCT, TAG_ARRAY -> {
                    offset++
                    val count = int()
                    Node.Group(List(count) { node() })
                }
                TAG_BYTES -> {
                    offset++
                    val length = int()
                    val bytes = data.copyOfRange(offset, offset + length)
                    if (bytes.size != length) throw FullPresetParseException("bytes truncados")
                    offset += length
                    Node.Bytes(bytes)
                }
                else -> Node.IntValue(int())
            }
        }

        private fun int(): Int {
            val tag = data[offset].toInt() and 0xFF
            return when {
                tag < 0x80 -> { offset += 1; tag }
                tag == TAG_U8 -> { offset += 2; data[offset - 1].toInt() and 0xFF }
                tag == TAG_U16 || tag == TAG_U16_CMD -> { offset += 3; u16(data, offset - 2) }
                else -> throw FullPresetParseException("tag desconhecida 0x${tag.toString(16)} no offset $offset")
            }
        }
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, offset: Int): Int =
        u16(data, offset) or (u16(data, offset + 2) shl 16)
}
