package com.opentonex.controller.ui.presets

import android.content.Context
import android.content.SharedPreferences

/** Par local de presets para os slots A e B. O id exibido é a posição na lista (01, 02…). */
data class PresetBank(
    val name: String,
    val presetA: Int,
    val presetB: Int
)

/**
 * Lista de bancos gravada só neste aparelho. O ToneX One não tem modo banco: ao escolher
 * um banco o app reescreve os presets de A e B no modo Dual.
 */
object PresetBankCodec {
    const val MAX_BANKS = 128

    fun encode(banks: List<PresetBank>): String =
        banks.take(MAX_BANKS).joinToString("\n") { bank ->
            val name = bank.name.replace("|", " ").replace("\n", " ").trim().ifEmpty { "Bank" }
            "$name|${bank.presetA.coerceIn(0, 19)}|${bank.presetB.coerceIn(0, 19)}"
        }

    fun decode(raw: String?): List<PresetBank> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 3) return@mapNotNull null
            val presetA = parts[1].toIntOrNull() ?: return@mapNotNull null
            val presetB = parts[2].toIntOrNull() ?: return@mapNotNull null
            if (presetA !in 0..19 || presetB !in 0..19) return@mapNotNull null
            PresetBank(name = parts[0].ifBlank { "Bank" }, presetA = presetA, presetB = presetB)
        }.take(MAX_BANKS).toList()
    }
}

class PresetBankStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("preset_banks", Context.MODE_PRIVATE)

    fun loadAll(): List<PresetBank> = PresetBankCodec.decode(prefs.getString(KEY, null))

    fun saveAll(banks: List<PresetBank>) {
        prefs.edit().putString(KEY, PresetBankCodec.encode(banks)).apply()
    }

    private companion object {
        const val KEY = "banks"
    }
}
