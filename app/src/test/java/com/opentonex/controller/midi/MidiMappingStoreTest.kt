package com.opentonex.controller.midi

import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}

class MidiMappingStoreTest {

    @Test
    fun `starts with default when storage empty`() {
        val store = MidiMappingStore(FakeKeyValueStore())
        assertEquals(MidiMapping.DEFAULT, store.mapping.value)
    }

    @Test
    fun `starts with persisted mapping`() {
        val kv = FakeKeyValueStore()
        val custom = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, 40)
        kv.put("midi_mapping", MidiMappingCodec.encode(custom))
        val store = MidiMappingStore(kv)
        assertEquals(custom, store.mapping.value)
    }

    @Test
    fun `learn updates flow and persists`() {
        val kv = FakeKeyValueStore()
        val store = MidiMappingStore(kv)
        store.learn(MidiAction.TOGGLE_CAB, 50)
        assertEquals(MidiAction.TOGGLE_CAB, store.mapping.value.actionFor(50))
        assertEquals(
            store.mapping.value,
            MidiMappingCodec.decode(kv.get("midi_mapping"))
        )
    }

    @Test
    fun `reset restores default and persists`() {
        val kv = FakeKeyValueStore()
        val store = MidiMappingStore(kv)
        store.learn(MidiAction.TOGGLE_CAB, 50)
        store.reset()
        assertEquals(MidiMapping.DEFAULT, store.mapping.value)
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode(kv.get("midi_mapping")))
    }

    @Test
    fun `corrupted storage falls back to default`() {
        val kv = FakeKeyValueStore()
        kv.put("midi_mapping", "###corrupted###")
        val store = MidiMappingStore(kv)
        assertEquals(MidiMapping.DEFAULT, store.mapping.value)
    }
}
