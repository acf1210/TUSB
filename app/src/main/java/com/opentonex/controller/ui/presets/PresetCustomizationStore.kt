package com.opentonex.controller.ui.presets

import android.content.Context
import android.content.SharedPreferences

/**
 * Personalizacao LOCAL de um preset da biblioteca do pedal (indice 0..19): apelido do
 * preset e nomes de amp/cab informados manualmente pelo usuario. O protocolo USB do
 * ToneX One nao expoe escrita de nome nem os nomes reais de amp/cab dos captures, entao
 * estes dados vivem so no aparelho (SharedPreferences) e servem como camada de exibicao.
 */
data class PresetCustomization(
    val name: String? = null,
    val ampName: String? = null,
    val cabName: String? = null
) {
    val isEmpty: Boolean get() = name.isNullOrBlank() && ampName.isNullOrBlank() && cabName.isNullOrBlank()
}

class PresetCustomizationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("preset_customizations", Context.MODE_PRIVATE)

    fun load(index: Int): PresetCustomization = PresetCustomization(
        name = prefs.getString("name_$index", null)?.takeIf { it.isNotBlank() },
        ampName = prefs.getString("amp_$index", null)?.takeIf { it.isNotBlank() },
        cabName = prefs.getString("cab_$index", null)?.takeIf { it.isNotBlank() }
    )

    fun loadAll(): Map<Int, PresetCustomization> =
        (0 until 20).associateWith(::load).filterValues { !it.isEmpty }

    fun save(index: Int, customization: PresetCustomization) {
        prefs.edit()
            .putString("name_$index", customization.name?.trim().orEmpty())
            .putString("amp_$index", customization.ampName?.trim().orEmpty())
            .putString("cab_$index", customization.cabName?.trim().orEmpty())
            .apply()
    }
}
