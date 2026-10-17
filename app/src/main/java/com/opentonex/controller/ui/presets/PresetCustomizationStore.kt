package com.opentonex.controller.ui.presets

import android.content.Context
import android.content.SharedPreferences

/**
 * Personalizacao LOCAL de um preset da biblioteca do pedal (indice 0..19): apelido do
 * preset e nomes de amp/cab informados manualmente pelo usuario, mais favorito e tags
 * de instrumento/genero/carater usadas pelo filtro. O protocolo USB do ToneX One nao
 * expoe escrita de nome nem os nomes reais de amp/cab dos captures, entao estes dados
 * vivem so no aparelho (SharedPreferences) e servem como camada de exibicao.
 */
data class PresetCustomization(
    val name: String? = null,
    val ampName: String? = null,
    val cabName: String? = null,
    val favorite: Boolean = false,
    val instrument: PresetInstrument? = null,
    val genre: PresetGenre? = null,
    val character: PresetCharacter? = null
) {
    val isEmpty: Boolean
        get() = name.isNullOrBlank() && ampName.isNullOrBlank() && cabName.isNullOrBlank() &&
            !favorite && instrument == null && genre == null && character == null
}

class PresetCustomizationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("preset_customizations", Context.MODE_PRIVATE)

    fun load(index: Int): PresetCustomization = PresetCustomization(
        name = prefs.getString("name_$index", null)?.takeIf { it.isNotBlank() },
        ampName = prefs.getString("amp_$index", null)?.takeIf { it.isNotBlank() },
        cabName = prefs.getString("cab_$index", null)?.takeIf { it.isNotBlank() },
        favorite = prefs.getBoolean("fav_$index", false),
        instrument = enumOrNull<PresetInstrument>(prefs.getString("instrument_$index", null)),
        genre = enumOrNull<PresetGenre>(prefs.getString("genre_$index", null)),
        character = enumOrNull<PresetCharacter>(prefs.getString("character_$index", null))
    )

    fun loadAll(): Map<Int, PresetCustomization> =
        (0 until 20).associateWith(::load).filterValues { !it.isEmpty }

    fun save(index: Int, customization: PresetCustomization) {
        prefs.edit()
            .putString("name_$index", customization.name?.trim().orEmpty())
            .putString("amp_$index", customization.ampName?.trim().orEmpty())
            .putString("cab_$index", customization.cabName?.trim().orEmpty())
            .putBoolean("fav_$index", customization.favorite)
            .putString("instrument_$index", customization.instrument?.name.orEmpty())
            .putString("genre_$index", customization.genre?.name.orEmpty())
            .putString("character_$index", customization.character?.name.orEmpty())
            .apply()
    }
}
