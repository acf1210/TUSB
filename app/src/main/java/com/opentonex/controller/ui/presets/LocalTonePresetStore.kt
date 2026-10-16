package com.opentonex.controller.ui.presets

import android.content.Context
import java.io.File
import java.util.UUID

data class StoredLocalTonePreset(val id: String, val preset: LocalTonePreset)

/** App-private library for TUSB snapshots; import/export is handled through document URIs. */
class LocalTonePresetStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "local-tone-presets")
    private val idPattern = Regex("[0-9a-fA-F-]{36}")

    fun loadAll(): List<StoredLocalTonePreset> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension == "tusb-preset" }
            .orEmpty()
            .mapNotNull { file -> runCatching {
                val id = file.nameWithoutExtension
                require(idPattern.matches(id)) { "Invalid local preset filename" }
                StoredLocalTonePreset(id, LocalTonePresetCodec.decode(file.readText(Charsets.UTF_8)))
            }.getOrNull() }
            .sortedBy { it.preset.name.lowercase() }
    }

    fun save(preset: LocalTonePreset): StoredLocalTonePreset {
        val contents = LocalTonePresetCodec.encode(preset)
        check(directory.exists() || directory.mkdirs()) { "Could not create local preset library" }
        val id = UUID.randomUUID().toString()
        val destination = File(directory, "$id.tusb-preset")
        val temporary = File(directory, "$id.tmp")
        try {
            temporary.writeText(contents, Charsets.UTF_8)
            check(temporary.renameTo(destination)) { "Could not save local preset" }
        } finally {
            temporary.delete()
        }
        return StoredLocalTonePreset(id, preset.copy(name = preset.name.trim()))
    }

    fun import(contents: String): StoredLocalTonePreset = save(LocalTonePresetCodec.decode(contents))

    fun export(id: String): String = fileFor(id).readText(Charsets.UTF_8)

    fun delete(id: String): Boolean = fileFor(id).delete()

    private fun fileFor(id: String): File {
        require(idPattern.matches(id)) { "Invalid local preset ID" }
        return File(directory, "$id.tusb-preset").also {
            require(it.isFile) { "Local preset not found" }
        }
    }
}
