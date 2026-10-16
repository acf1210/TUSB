package com.opentonex.controller.ui.presets

import java.nio.charset.StandardCharsets
import java.util.Base64
import com.opentonex.controller.domain.TonexParam

/** Portable local snapshot of the complete 109-parameter ToneX One preset layout. */
data class LocalTonePreset(
    val name: String,
    val parameters: List<Float>,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_PARAMETERS = TonexParam.TABLE_SIZE
        const val MAX_NAME_LENGTH = 80
    }
}

/** Versioned, text-based interchange format owned by TUSB (`.tusb-preset`). */
object LocalTonePresetCodec {
    private const val VERSION = "1"
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(preset: LocalTonePreset): String {
        validate(preset)
        val encodedName = encoder.encodeToString(preset.name.trim().toByteArray(StandardCharsets.UTF_8))
        return listOf(
            "format=tusb-preset",
            "version=$VERSION",
            "name=$encodedName",
            "createdAt=${preset.createdAtEpochMillis}",
            "parameters=${preset.parameters.joinToString(",") { it.toString() }}"
        ).joinToString("\n", postfix = "\n")
    }

    fun decode(contents: String): LocalTonePreset {
        require(contents.length <= MAX_FILE_LENGTH) { "Preset file is too large" }
        val fields = contents.lineSequence()
            .filter(String::isNotBlank)
            .map { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "Invalid preset line" }
                line.substring(0, separator) to line.substring(separator + 1)
            }
            .toList()
        require(fields.map { it.first }.distinct().size == fields.size) { "Duplicate preset field" }
        val values = fields.toMap()
        require(values.keys == REQUIRED_FIELDS) { "Unexpected preset fields" }
        require(values["format"] == "tusb-preset") { "Unsupported preset format" }
        require(values["version"] == VERSION) { "Unsupported preset version" }
        val name = runCatching {
            String(decoder.decode(requireNotNull(values["name"])), StandardCharsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("Invalid preset name", it) }
        val createdAt = requireNotNull(values["createdAt"]).toLongOrNull()
            ?: throw IllegalArgumentException("Invalid preset timestamp")
        val parameterText = requireNotNull(values["parameters"])
        val parameters = parameterText.split(',').map { token ->
            token.toFloatOrNull()?.takeIf(Float::isFinite)
                ?: throw IllegalArgumentException("Invalid preset parameter")
        }
        return LocalTonePreset(name, parameters, createdAt).also(::validate)
    }

    internal fun validParameterValues(values: List<Float>): Boolean = PresetParameterRanges.accepts(values)

    private fun validate(preset: LocalTonePreset) {
        require(preset.name.isNotBlank() && preset.name.trim().length <= LocalTonePreset.MAX_NAME_LENGTH) {
            "Preset name must contain 1 to ${LocalTonePreset.MAX_NAME_LENGTH} characters"
        }
        require(preset.parameters.size == LocalTonePreset.MAX_PARAMETERS) {
            "Preset must contain exactly ${LocalTonePreset.MAX_PARAMETERS} parameters"
        }
        require(preset.parameters.all(Float::isFinite)) { "Preset parameters must be finite" }
        require(PresetParameterRanges.accepts(preset.parameters)) {
            "Preset contains a value outside the supported parameter ranges"
        }
        require(preset.createdAtEpochMillis >= 0) { "Invalid preset timestamp" }
    }

    private val REQUIRED_FIELDS = setOf("format", "version", "name", "createdAt", "parameters")
    private const val MAX_FILE_LENGTH = 128 * 1024
}

/** Per-index limits for the original ToneX One parameter block (indices 0..108). */
internal object PresetParameterRanges {
    private data class Range(val min: Float, val max: Float, val integerOnly: Boolean = false)

    private val ranges: List<Range> = buildList {
        fun add(min: Number, max: Number, integerOnly: Boolean = false) = add(Range(min.toFloat(), max.toFloat(), integerOnly))
        add(0, 1, true); add(0, 1, true); add(-100, 0); add(5, 500); add(-100, -20)
        add(0, 1, true); add(0, 1, true); add(-40, 0); add(-30, 10); add(1, 51)
        add(0, 1, true); add(0, 10); add(75, 600); add(0, 10); add(0.2, 3); add(150, 5000); add(0, 10); add(1000, 4000)
        add(0, 1, true); add(0, 1, true); add(0, 10); add(0, 10); add(0, 100); add(0, 1, true); add(0, 2, true)
        add(0, 10, true); add(0, 10); add(0, 2, true); add(0, 10); add(0, 10); add(0, 2, true); add(0, 2, true)
        add(0, 10); add(-100, 100); add(0, 10); add(0, 10)
        add(0, 1, true); add(0, 1, true); add(0, 5, true)
        repeat(6) { add(0, 10); add(0, 500); add(-10, 10); add(0, 100) }
        add(0, 1, true); add(0, 1, true); add(0, 4, true)
        // Chorus, tremolo, phaser, flanger and rotary parameter groups.
        add(0, 1, true); add(0, 17, true); add(0.1, 10); add(0, 100); add(0, 10)
        add(0, 1, true); add(0, 17, true); add(0.1, 10); add(0, 10); add(0, 100); add(0, 10)
        add(0, 1, true); add(0, 17, true); add(0.1, 10); add(0, 100); add(0, 10)
        add(0, 1, true); add(0, 17, true); add(0.1, 10); add(0, 100); add(0, 100); add(0, 10)
        add(0, 1, true); add(0, 17, true); add(0, 400); add(0, 300); add(0, 100); add(0, 10)
        add(0, 1, true); add(0, 1, true); add(0, 1, true)
        add(0, 1, true); add(0, 17, true); add(0, 1000); add(0, 100); add(0, 1, true); add(0, 100)
        add(0, 1, true); add(0, 17, true); add(0, 1000); add(0, 100); add(0, 1, true); add(0, 100)
    }

    fun accepts(values: List<Float>): Boolean = values.size == ranges.size && values.indices.all { index ->
        val value = values[index]
        val range = ranges[index]
        value.isFinite() && value in range.min..range.max && (!range.integerOnly || value % 1f == 0f)
    }

    fun safeSample(): List<Float> = ranges.map { range ->
        if (range.integerOnly) range.min else (range.min + range.max) / 2f
    }
}
