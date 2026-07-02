package com.opentonex.controller.midi

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstracao minima de chave-valor: SharedPreferences real no app, mapa em memoria nos
 * testes (SharedPreferences com returnDefaultValues devolve edit() null em JVM).
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences
) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/** Fonte unica do mapeamento MIDI corrente, persistido a cada mudanca. */
class MidiMappingStore(private val storage: KeyValueStore) {

    private val _mapping = MutableStateFlow(MidiMappingCodec.decode(storage.get(KEY)))
    val mapping: StateFlow<MidiMapping> = _mapping.asStateFlow()

    fun learn(action: MidiAction, cc: Int) {
        update(_mapping.value.withLearned(action, cc))
    }

    fun reset() {
        update(MidiMapping.DEFAULT)
    }

    private fun update(mapping: MidiMapping) {
        _mapping.value = mapping
        storage.put(KEY, MidiMappingCodec.encode(mapping))
    }

    private companion object {
        const val KEY = "midi_mapping"
    }
}
