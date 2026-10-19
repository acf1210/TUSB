package com.opentonex.controller.library

import com.opentonex.controller.protocol.FullPreset
import com.opentonex.controller.protocol.FullPresetParseException
import com.opentonex.controller.protocol.TonexFullPreset
import java.io.File

/** Um preset completo (com Tone Model) guardado no aparelho. */
data class ToneModelEntry(
    val fileName: String,
    val savedAt: Long,
    val preset: FullPreset
)

/**
 * Biblioteca local de presets completos lidos do pedal. Cada arquivo `.tusbtone` e o
 * cabecalho [MAGIC] + o corpo 0x0303 exato do pedal, para poder ser exportado,
 * compartilhado entre usuarios do TUSB e regravado em qualquer preset do pedal.
 */
class ToneModelLibrary(private val directory: File) {

    fun list(): List<ToneModelEntry> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { ToneModelEntry(file.name, file.lastModified(), decode(file.readBytes())) }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    /** Salva um preset lido do pedal; [label] vira parte do nome do arquivo. */
    fun save(preset: FullPreset, label: String = preset.presetName): ToneModelEntry {
        directory.mkdirs()
        val file = uniqueFile(label)
        file.writeBytes(encode(preset.body))
        return ToneModelEntry(file.name, file.lastModified(), preset)
    }

    /**
     * Importa um `.tusbtone` vindo de fora (arquivo escolhido pelo usuario). Valida o
     * conteudo inteiro antes de gravar; lanca [FullPresetParseException] se for invalido.
     */
    fun import(bytes: ByteArray, suggestedName: String?): ToneModelEntry {
        if (bytes.size > MAX_FILE_SIZE) throw FullPresetParseException("arquivo grande demais")
        val preset = decode(bytes)
        return save(preset, suggestedName?.removeSuffix(EXTENSION)?.takeIf { it.isNotBlank() } ?: preset.presetName)
    }

    fun export(entry: ToneModelEntry): ByteArray = File(directory, entry.fileName).readBytes()

    fun delete(entry: ToneModelEntry): Boolean = File(directory, entry.fileName).delete()

    private fun uniqueFile(label: String): File {
        val base = label.replace(UNSAFE_CHARS, "_").trim('_', ' ').take(MAX_NAME_LENGTH).ifEmpty { "preset" }
        var candidate = File(directory, base + EXTENSION)
        var counter = 2
        while (candidate.exists()) candidate = File(directory, "$base ($counter)$EXTENSION").also { counter++ }
        return candidate
    }

    companion object {
        const val EXTENSION = ".tusbtone"
        const val MIME_TYPE = "application/octet-stream"
        private val MAGIC = "TUSBTONE".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION: Byte = 1
        private const val MAX_FILE_SIZE = 256 * 1024
        private const val MAX_NAME_LENGTH = 48
        private val UNSAFE_CHARS = Regex("""[^A-Za-z0-9 ._()\-]""")

        fun encode(body: ByteArray): ByteArray = MAGIC + byteArrayOf(FORMAT_VERSION) + body

        fun decode(bytes: ByteArray): FullPreset {
            val headerSize = MAGIC.size + 1
            if (bytes.size <= headerSize || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw FullPresetParseException("nao e um arquivo .tusbtone")
            }
            if (bytes[MAGIC.size] != FORMAT_VERSION) {
                throw FullPresetParseException("versao de arquivo nao suportada: ${bytes[MAGIC.size]}")
            }
            return TonexFullPreset.parse(bytes.copyOfRange(headerSize, bytes.size))
        }
    }
}
