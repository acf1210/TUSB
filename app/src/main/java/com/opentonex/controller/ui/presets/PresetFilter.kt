package com.opentonex.controller.ui.presets

import com.opentonex.controller.domain.LibraryPreset

/**
 * Tags LOCAIS de um preset da biblioteca do pedal. O ToneX One nao guarda nenhuma delas,
 * entao ficam so no aparelho (PresetCustomizationStore) e servem para filtrar a lista.
 */
enum class PresetInstrument { ELECTRIC_GUITAR, ACOUSTIC_GUITAR, ELECTRIC_BASS, ACOUSTIC_BASS, OTHER }

enum class PresetGenre {
    ROCK, METAL, PUNK, POP, GRUNGE, BLUES, JAZZ, FUNK, FUSION, REGGAE, SURF, COUNTRY, VINTAGE, OTHER
}

enum class PresetCharacter { CLEAN, DRIVE, HI_GAIN, FUZZY, OTHER }

enum class PresetSort { NUMBER, NAME, FAVORITES }

/**
 * Criterio da lista de presets: ordenacao + filtros. Dentro de um grupo as opcoes somam (OU);
 * entre grupos todas precisam bater (E). Preset sem tag nunca passa por um grupo ativo.
 */
data class PresetFilter(
    val sort: PresetSort = PresetSort.NUMBER,
    val favoritesOnly: Boolean = false,
    val instruments: Set<PresetInstrument> = emptySet(),
    val genres: Set<PresetGenre> = emptySet(),
    val characters: Set<PresetCharacter> = emptySet()
) {
    /** Opcoes de filtro marcadas (badge do botao); a ordenacao nao conta. */
    val activeCount: Int
        get() = (if (favoritesOnly) 1 else 0) + instruments.size + genres.size + characters.size

    fun matches(custom: PresetCustomization?): Boolean {
        if (favoritesOnly && custom?.favorite != true) return false
        val instrument = custom?.instrument
        if (instruments.isNotEmpty() && (instrument == null || instrument !in instruments)) return false
        val genre = custom?.genre
        if (genres.isNotEmpty() && (genre == null || genre !in genres)) return false
        val character = custom?.character
        if (characters.isNotEmpty() && (character == null || character !in characters)) return false
        return true
    }

    /** Texto curto para sobreviver a rotacao/recriacao da tela (rememberSaveable). */
    fun encode(): String = listOf(
        sort.name,
        favoritesOnly.toString(),
        instruments.joinToString(",") { it.name },
        genres.joinToString(",") { it.name },
        characters.joinToString(",") { it.name }
    ).joinToString(";")

    companion object {
        /** Valores desconhecidos ou texto corrompido caem no padrao, sem lancar excecao. */
        fun decode(raw: String?): PresetFilter {
            val parts = raw?.split(";") ?: return PresetFilter()
            if (parts.size != 5) return PresetFilter()
            return PresetFilter(
                sort = enumOrNull<PresetSort>(parts[0]) ?: PresetSort.NUMBER,
                favoritesOnly = parts[1] == "true",
                instruments = enumSet<PresetInstrument>(parts[2]),
                genres = enumSet<PresetGenre>(parts[3]),
                characters = enumSet<PresetCharacter>(parts[4])
            )
        }
    }
}

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { wanted -> enumValues<T>().firstOrNull { it.name == wanted } }

private inline fun <reified T : Enum<T>> enumSet(csv: String): Set<T> =
    csv.split(",").mapNotNull { enumOrNull<T>(it) }.toSet()

/** Liga/desliga [item] num conjunto de filtro. */
fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * Aplica busca, filtros e ordenacao. A busca casa o apelido local, o nome original e o
 * nome do Tone Model; a ordenacao e' estavel (empates caem na ordem numerica do preset).
 */
fun List<LibraryPreset>.applyPresetFilter(
    criteria: PresetFilter,
    customizations: Map<Int, PresetCustomization>,
    query: String,
    toneModelNames: Map<Int, String> = emptyMap()
): List<LibraryPreset> {
    fun displayName(preset: LibraryPreset) = presetDisplayName(preset, customizations[preset.index], toneModelNames)

    val visible = this.filter { preset ->
        criteria.matches(customizations[preset.index]) &&
            (query.isBlank() ||
                displayName(preset).contains(query, ignoreCase = true) ||
                preset.name.contains(query, ignoreCase = true) ||
                toneModelNames[preset.index]?.contains(query, ignoreCase = true) == true)
    }
    return when (criteria.sort) {
        PresetSort.NUMBER -> visible.sortedBy { it.index }
        PresetSort.NAME -> visible.sortedWith(
            compareBy<LibraryPreset, String>(String.CASE_INSENSITIVE_ORDER) { displayName(it) }
                .thenBy { it.index }
        )
        PresetSort.FAVORITES -> visible.sortedWith(
            compareByDescending<LibraryPreset> { customizations[it.index]?.favorite == true }
                .thenBy { it.index }
        )
    }
}
