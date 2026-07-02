# Suporte MIDI V1.0.2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Footswitches MIDI (M-Vave Chocolate BLE e controladores USB MIDI) comandam o app TUSB, que repassa as ações ao ToneX One via USB serial.

**Architecture:** Novo pacote `com.opentonex.controller.midi` com parser puro (bytes → mensagens), mapping persistido (CC → ação) e dispatcher (mensagem → chamadas no `PedalViewModel` existente). Única camada Android é `MidiInputManager` (envolve `android.media.midi.MidiManager` para USB e BLE). UI substitui o placeholder da aba MIDI do `MenuScreen`.

**Tech Stack:** Kotlin, `android.media.midi` (API 23+, minSdk 24), Compose Material3, JUnit4 + kotlinx-coroutines-test (JVM). Zero dependências novas.

**Spec:** `docs/superpowers/specs/2026-07-02-midi-support-design.md`

**Comando de teste:** `./gradlew :app:testDebugUnitTest` (no Windows PowerShell: `.\gradlew.bat :app:testDebugUnitTest`)

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `app/src/main/java/com/opentonex/controller/midi/MidiMessage.kt` | Tipos `ProgramChange`/`ControlChange` |
| `app/src/main/java/com/opentonex/controller/midi/MidiParser.kt` | Bytes crus → mensagens (running status, fragmentação) |
| `app/src/main/java/com/opentonex/controller/midi/MidiAction.kt` | Enum de ações mapeáveis + labels |
| `app/src/main/java/com/opentonex/controller/midi/MidiMapping.kt` | Mapa CC→ação imutável + default + codec string |
| `app/src/main/java/com/opentonex/controller/midi/MidiMappingStore.kt` | Persistência via `KeyValueStore` + StateFlow |
| `app/src/main/java/com/opentonex/controller/midi/MidiCommandDispatcher.kt` | Mensagem + mapping → `MidiActionHandler`; modo Learn |
| `app/src/main/java/com/opentonex/controller/midi/PedalMidiActionHandler.kt` | Adapter `MidiActionHandler` → `PedalViewModel` |
| `app/src/main/java/com/opentonex/controller/midi/MidiInputManager.kt` | MidiManager Android: USB + scan/conexão BLE |
| `app/src/main/java/com/opentonex/controller/midi/MidiController.kt` | Composição (store + dispatcher + input manager) |
| `app/src/main/java/com/opentonex/controller/ui/menu/MidiTab.kt` | UI da aba MIDI (dispositivos + mapeamento + Learn) |
| Modificar: `MenuScreen.kt`, `ToneXApp.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `res/values*/strings.xml`, `README.md`, `docs/USER_GUIDE.md` | Integração, permissões, versão, docs |
| Testes: `app/src/test/java/com/opentonex/controller/midi/{MidiParserTest,MidiMappingTest,MidiMappingStoreTest,MidiCommandDispatcherTest}.kt` | Cobertura JVM |

---

### Task 1: MidiMessage + MidiParser

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiMessage.kt`
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiParser.kt`
- Test: `app/src/test/java/com/opentonex/controller/midi/MidiParserTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.opentonex.controller.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `parses program change`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xC0, 0x05))
        assertEquals(listOf(MidiMessage.ProgramChange(channel = 0, program = 5)), messages)
    }

    @Test
    fun `parses control change`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xB1, 20, 127))
        assertEquals(listOf(MidiMessage.ControlChange(channel = 1, controller = 20, value = 127)), messages)
    }

    @Test
    fun `parses running status control changes`() {
        val parser = MidiParser()
        // Um status 0xB0 seguido de dois pares de data bytes (running status).
        val messages = parser.parse(bytes(0xB0, 20, 127, 21, 0))
        assertEquals(
            listOf(
                MidiMessage.ControlChange(0, 20, 127),
                MidiMessage.ControlChange(0, 21, 0)
            ),
            messages
        )
    }

    @Test
    fun `parses message fragmented across packets`() {
        val parser = MidiParser()
        assertTrue(parser.parse(bytes(0xB0, 25)).isEmpty())
        // O value chega no pacote seguinte; o parser deve manter estado.
        assertEquals(
            listOf(MidiMessage.ControlChange(0, 25, 127)),
            parser.parse(bytes(127))
        )
    }

    @Test
    fun `ignores realtime bytes interleaved in message`() {
        val parser = MidiParser()
        // 0xF8 (clock) no meio de um CC nao pode corromper o parse.
        val messages = parser.parse(bytes(0xB0, 20, 0xF8, 127))
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }

    @Test
    fun `ignores sysex content`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0xF0, 0x7E, 0x7F, 0x06, 0xF7, 0xC0, 0x03))
        assertEquals(listOf(MidiMessage.ProgramChange(0, 3)), messages)
    }

    @Test
    fun `ignores note on and off`() {
        val parser = MidiParser()
        val messages = parser.parse(bytes(0x90, 60, 100, 0x80, 60, 0, 0xB0, 20, 127))
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }

    @Test
    fun `discards data bytes without status`() {
        val parser = MidiParser()
        assertTrue(parser.parse(bytes(20, 127, 55)).isEmpty())
    }

    @Test
    fun `respects offset and count`() {
        val parser = MidiParser()
        val buffer = bytes(0x00, 0xB0, 20, 127, 0x00)
        val messages = parser.parse(buffer, offset = 1, count = 3)
        assertEquals(listOf(MidiMessage.ControlChange(0, 20, 127)), messages)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiParserTest"`
Expected: FAIL (compilação: `MidiParser`/`MidiMessage` não existem)

- [ ] **Step 3: Write the implementation**

`MidiMessage.kt`:

```kotlin
package com.opentonex.controller.midi

/** Mensagens MIDI 1.0 que o app entende. Todo o resto e ignorado pelo parser. */
sealed interface MidiMessage {
    data class ProgramChange(val channel: Int, val program: Int) : MidiMessage
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiMessage
}
```

`MidiParser.kt`:

```kotlin
package com.opentonex.controller.midi

/**
 * Converte o stream de bytes MIDI 1.0 em mensagens tipadas. Mantem estado entre chamadas
 * porque pacotes BLE/USB podem fragmentar uma mensagem (status num pacote, data no
 * seguinte) e porque running status omite o status byte em mensagens repetidas.
 *
 * Tolerante a lixo: data bytes sem status, SysEx e mensagens nao suportadas sao
 * descartados sem lancar excecao.
 */
class MidiParser {
    private var runningStatus = 0
    private var firstDataByte = -1
    private var inSysEx = false

    fun parse(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size): List<MidiMessage> {
        val messages = mutableListOf<MidiMessage>()
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and 0xFF
            when {
                // Realtime (0xF8..0xFF): pode aparecer no MEIO de outra mensagem e nao
                // afeta running status nem o data byte pendente.
                b >= 0xF8 -> Unit
                b == 0xF0 -> { inSysEx = true; runningStatus = 0; firstDataByte = -1 }
                b == 0xF7 -> inSysEx = false
                // System common (0xF1..0xF6) cancela running status.
                b >= 0xF0 -> { runningStatus = 0; firstDataByte = -1 }
                b >= 0x80 -> { runningStatus = b; firstDataByte = -1; inSysEx = false }
                inSysEx -> Unit
                else -> consumeDataByte(b, messages)
            }
        }
        return messages
    }

    private fun consumeDataByte(data: Int, messages: MutableList<MidiMessage>) {
        val channel = runningStatus and 0x0F
        when (runningStatus and 0xF0) {
            0xC0 -> messages += MidiMessage.ProgramChange(channel, data)
            0xB0 -> if (firstDataByte < 0) {
                firstDataByte = data
            } else {
                messages += MidiMessage.ControlChange(channel, firstDataByte, data)
                firstDataByte = -1
            }
            // Note on/off, aftertouch e pitch bend tem 2 data bytes: consome sem emitir.
            0x80, 0x90, 0xA0, 0xE0 -> firstDataByte = if (firstDataByte < 0) data else -1
            // Channel pressure (0xD0) tem 1 data byte: consome sem emitir.
            0xD0 -> Unit
            // Sem status corrente: byte orfao, descarta.
            else -> Unit
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiParserTest"`
Expected: PASS (9 testes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/ app/src/test/java/com/opentonex/controller/midi/
git commit -m "feat(midi): parser MIDI 1.0 com running status e fragmentacao"
```

---

### Task 2: MidiAction + MidiMapping + codec

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiAction.kt`
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiMapping.kt`
- Test: `app/src/test/java/com/opentonex/controller/midi/MidiMappingTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.opentonex.controller.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidiMappingTest {

    @Test
    fun `default map has expected assignments`() {
        val map = MidiMapping.DEFAULT
        assertEquals(MidiAction.SELECT_SLOT_A, map.actionFor(20))
        assertEquals(MidiAction.SELECT_SLOT_B, map.actionFor(21))
        assertEquals(MidiAction.SELECT_SLOT_C, map.actionFor(22))
        assertEquals(MidiAction.NEXT_PRESET, map.actionFor(23))
        assertEquals(MidiAction.PREV_PRESET, map.actionFor(24))
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(25))
        assertEquals(MidiAction.TOGGLE_CAB, map.actionFor(26))
        assertEquals(MidiAction.TOGGLE_GATE, map.actionFor(27))
        assertEquals(MidiAction.TOGGLE_COMP, map.actionFor(28))
        assertEquals(MidiAction.TOGGLE_EQ, map.actionFor(29))
        assertEquals(MidiAction.TOGGLE_MOD, map.actionFor(30))
        assertEquals(MidiAction.TOGGLE_DELAY, map.actionFor(31))
        assertEquals(MidiAction.TOGGLE_REVERB, map.actionFor(32))
        assertEquals(MidiAction.AMP_BASS, map.actionFor(102))
        assertEquals(MidiAction.AMP_MID, map.actionFor(103))
        assertEquals(MidiAction.AMP_TREBLE, map.actionFor(104))
        assertEquals(MidiAction.AMP_GAIN, map.actionFor(105))
        assertEquals(MidiAction.AMP_VOLUME, map.actionFor(106))
        assertNull(map.actionFor(64))
    }

    @Test
    fun `withLearned moves action to new cc and frees old cc`() {
        val map = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, 40)
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(40))
        assertNull(map.actionFor(25))
        assertEquals(40, map.ccFor(MidiAction.TOGGLE_BYPASS))
    }

    @Test
    fun `withLearned steals cc already used by another action`() {
        val map = MidiMapping.DEFAULT.withLearned(MidiAction.TOGGLE_BYPASS, 20)
        assertEquals(MidiAction.TOGGLE_BYPASS, map.actionFor(20))
        assertNull(map.ccFor(MidiAction.SELECT_SLOT_A))
    }

    @Test
    fun `codec round trip preserves mapping`() {
        val original = MidiMapping.DEFAULT.withLearned(MidiAction.AMP_GAIN, 7)
        val decoded = MidiMappingCodec.decode(MidiMappingCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `decode of null or blank returns default`() {
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode(null))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode(""))
    }

    @Test
    fun `decode of corrupted payload returns default`() {
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("20=NOT_AN_ACTION"))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("garbage"))
        assertEquals(MidiMapping.DEFAULT, MidiMappingCodec.decode("999=TOGGLE_BYPASS"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiMappingTest"`
Expected: FAIL (compilação)

- [ ] **Step 3: Write the implementation**

`MidiAction.kt`:

```kotlin
package com.opentonex.controller.midi

/**
 * Acoes do app que podem ser disparadas por Control Change. Program Change NAO esta aqui
 * de proposito: PC n carrega sempre o preset n (0..19), fixo e nao remapeavel.
 *
 * [label] usa jargao de guitarra identico em PT/EN/ES, por isso nao e um string resource.
 */
enum class MidiAction(val label: String, val isContinuous: Boolean = false) {
    SELECT_SLOT_A("Slot A"),
    SELECT_SLOT_B("Slot B"),
    SELECT_SLOT_C("Slot C"),
    NEXT_PRESET("Preset +"),
    PREV_PRESET("Preset -"),
    TOGGLE_BYPASS("Bypass"),
    TOGGLE_CAB("Cab / IR"),
    TOGGLE_GATE("Gate"),
    TOGGLE_COMP("Comp"),
    TOGGLE_EQ("EQ"),
    TOGGLE_MOD("Mod"),
    TOGGLE_DELAY("Delay"),
    TOGGLE_REVERB("Reverb"),
    AMP_BASS("Bass", isContinuous = true),
    AMP_MID("Mid", isContinuous = true),
    AMP_TREBLE("Treble", isContinuous = true),
    AMP_GAIN("Gain", isContinuous = true),
    AMP_VOLUME("Volume", isContinuous = true)
}
```

`MidiMapping.kt`:

```kotlin
package com.opentonex.controller.midi

/** Mapa imutavel CC -> acao. Cada CC aciona no maximo uma acao e vice-versa. */
data class MidiMapping(val ccToAction: Map<Int, MidiAction>) {

    fun actionFor(cc: Int): MidiAction? = ccToAction[cc]

    fun ccFor(action: MidiAction): Int? =
        ccToAction.entries.firstOrNull { it.value == action }?.key

    /** Aprende [cc] para [action], removendo o CC antigo da acao e a acao antiga do CC. */
    fun withLearned(action: MidiAction, cc: Int): MidiMapping {
        val cleaned = ccToAction.filterValues { it != action }.filterKeys { it != cc }
        return MidiMapping(cleaned + (cc to action))
    }

    companion object {
        val DEFAULT = MidiMapping(
            mapOf(
                20 to MidiAction.SELECT_SLOT_A,
                21 to MidiAction.SELECT_SLOT_B,
                22 to MidiAction.SELECT_SLOT_C,
                23 to MidiAction.NEXT_PRESET,
                24 to MidiAction.PREV_PRESET,
                25 to MidiAction.TOGGLE_BYPASS,
                26 to MidiAction.TOGGLE_CAB,
                27 to MidiAction.TOGGLE_GATE,
                28 to MidiAction.TOGGLE_COMP,
                29 to MidiAction.TOGGLE_EQ,
                30 to MidiAction.TOGGLE_MOD,
                31 to MidiAction.TOGGLE_DELAY,
                32 to MidiAction.TOGGLE_REVERB,
                102 to MidiAction.AMP_BASS,
                103 to MidiAction.AMP_MID,
                104 to MidiAction.AMP_TREBLE,
                105 to MidiAction.AMP_GAIN,
                106 to MidiAction.AMP_VOLUME
            )
        )
    }
}

/**
 * Codec texto simples ("cc=ACTION;cc=ACTION") em vez de JSON: sem dependencia nova e
 * testavel em JVM puro (org.json devolve stubs em unit test com returnDefaultValues).
 * Qualquer corrupcao devolve o mapa padrao (fail-safe do spec).
 */
object MidiMappingCodec {
    fun encode(mapping: MidiMapping): String =
        mapping.ccToAction.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value.name}" }

    fun decode(encoded: String?): MidiMapping {
        if (encoded.isNullOrBlank()) return MidiMapping.DEFAULT
        val result = mutableMapOf<Int, MidiAction>()
        for (entry in encoded.split(";")) {
            val parts = entry.split("=")
            if (parts.size != 2) return MidiMapping.DEFAULT
            val cc = parts[0].toIntOrNull() ?: return MidiMapping.DEFAULT
            if (cc !in 0..127) return MidiMapping.DEFAULT
            val action = runCatching { MidiAction.valueOf(parts[1]) }.getOrNull()
                ?: return MidiMapping.DEFAULT
            result[cc] = action
        }
        return MidiMapping(result)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiMappingTest"`
Expected: PASS (6 testes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/ app/src/test/java/com/opentonex/controller/midi/
git commit -m "feat(midi): acoes mapeaveis, mapa padrao e codec de persistencia"
```

---

### Task 3: MidiMappingStore

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiMappingStore.kt`
- Test: `app/src/test/java/com/opentonex/controller/midi/MidiMappingStoreTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiMappingStoreTest"`
Expected: FAIL (compilação: `KeyValueStore`/`MidiMappingStore` não existem)

- [ ] **Step 3: Write the implementation**

`MidiMappingStore.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiMappingStoreTest"`
Expected: PASS (5 testes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/ app/src/test/java/com/opentonex/controller/midi/
git commit -m "feat(midi): store de mapeamento com persistencia e fallback"
```

---

### Task 4: MidiActionHandler + MidiCommandDispatcher

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiCommandDispatcher.kt`
- Test: `app/src/test/java/com/opentonex/controller/midi/MidiCommandDispatcherTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.editor.EffectSlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHandler : MidiActionHandler {
    val calls = mutableListOf<String>()
    var activePreset: Int? = 4
    override fun selectSlot(slot: Slot) { calls += "slot:${slot.name}" }
    override fun loadPreset(presetId: Int) { calls += "preset:$presetId" }
    override fun activePresetId(): Int? = activePreset
    override fun toggleBypass() { calls += "bypass" }
    override fun toggleCab() { calls += "cab" }
    override fun toggleEffect(effect: EffectSlotType) { calls += "effect:${effect.name}" }
    override fun setAmpKnob(knob: AmpKnob, normalized: Float) { calls += "knob:${knob.name}:$normalized" }
}

class MidiCommandDispatcherTest {

    private fun dispatcher(handler: FakeHandler, mapping: MidiMapping = MidiMapping.DEFAULT) =
        MidiCommandDispatcher(handler) { mapping }

    @Test
    fun `program change loads preset`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ProgramChange(0, 7))
        assertEquals(listOf("preset:7"), handler.calls)
    }

    @Test
    fun `program change out of range is ignored`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ProgramChange(0, 20))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `cc toggle fires only on value at or above 64`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 25, 0))
        d.dispatch(MidiMessage.ControlChange(0, 25, 63))
        assertTrue(handler.calls.isEmpty())
        d.dispatch(MidiMessage.ControlChange(0, 25, 64))
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass", "bypass"), handler.calls)
    }

    @Test
    fun `slot and effect ccs dispatch to handler`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 20, 127))
        d.dispatch(MidiMessage.ControlChange(0, 22, 127))
        d.dispatch(MidiMessage.ControlChange(0, 26, 127))
        d.dispatch(MidiMessage.ControlChange(0, 27, 127))
        d.dispatch(MidiMessage.ControlChange(0, 32, 127))
        assertEquals(
            listOf("slot:A", "slot:C", "cab", "effect:GATE", "effect:REV"),
            handler.calls
        )
    }

    @Test
    fun `continuous cc maps full range to knob`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.dispatch(MidiMessage.ControlChange(0, 105, 0))
        d.dispatch(MidiMessage.ControlChange(0, 105, 127))
        assertEquals(listOf("knob:GAIN:0.0", "knob:GAIN:1.0"), handler.calls)
    }

    @Test
    fun `next preset wraps around at 19`() {
        val handler = FakeHandler()
        handler.activePreset = 19
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 23, 127))
        assertEquals(listOf("preset:0"), handler.calls)
    }

    @Test
    fun `prev preset wraps around at 0`() {
        val handler = FakeHandler()
        handler.activePreset = 0
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 24, 127))
        assertEquals(listOf("preset:19"), handler.calls)
    }

    @Test
    fun `next preset without active preset is ignored`() {
        val handler = FakeHandler()
        handler.activePreset = null
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 23, 127))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `unmapped cc is ignored`() {
        val handler = FakeHandler()
        dispatcher(handler).dispatch(MidiMessage.ControlChange(0, 64, 127))
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `learn mode captures next cc instead of dispatching`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        var learned: Pair<MidiAction, Int>? = null
        d.onLearned = { action, cc -> learned = action to cc }
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.dispatch(MidiMessage.ControlChange(0, 45, 127))
        assertEquals(MidiAction.TOGGLE_CAB to 45, learned)
        assertTrue(handler.calls.isEmpty())
        assertNull(d.learnTarget.value)
        // Depois de aprender, dispatch volta ao normal.
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass"), handler.calls)
    }

    @Test
    fun `learn mode ignores program change`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.dispatch(MidiMessage.ProgramChange(0, 3))
        assertEquals(MidiAction.TOGGLE_CAB, d.learnTarget.value)
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun `cancelLearn disarms learn mode`() {
        val handler = FakeHandler()
        val d = dispatcher(handler)
        d.startLearn(MidiAction.TOGGLE_CAB)
        d.cancelLearn()
        d.dispatch(MidiMessage.ControlChange(0, 25, 127))
        assertEquals(listOf("bypass"), handler.calls)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiCommandDispatcherTest"`
Expected: FAIL (compilação)

- [ ] **Step 3: Write the implementation**

`MidiCommandDispatcher.kt`:

```kotlin
package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.editor.EffectSlotType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Porta de saida do MIDI para o app; implementada por PedalMidiActionHandler. */
interface MidiActionHandler {
    fun selectSlot(slot: Slot)
    fun loadPreset(presetId: Int)
    fun activePresetId(): Int?
    fun toggleBypass()
    fun toggleCab()
    fun toggleEffect(effect: EffectSlotType)
    fun setAmpKnob(knob: AmpKnob, normalized: Float)
}

/**
 * Traduz mensagens MIDI parseadas em acoes do app usando o mapping corrente.
 * PC n carrega o preset n (fixo). CCs consultam o mapping. Deve ser chamado na
 * main thread (MidiInputManager posta os callbacks nela).
 */
class MidiCommandDispatcher(
    private val handler: MidiActionHandler,
    private val mappingProvider: () -> MidiMapping
) {
    /** Acao armada para MIDI Learn; null quando desarmado. */
    private val _learnTarget = MutableStateFlow<MidiAction?>(null)
    val learnTarget: StateFlow<MidiAction?> = _learnTarget.asStateFlow()

    /** Ultima mensagem recebida, para debug de mapeamento na tela MIDI. */
    private val _lastMessage = MutableStateFlow<MidiMessage?>(null)
    val lastMessage: StateFlow<MidiMessage?> = _lastMessage.asStateFlow()

    /** Chamado quando o Learn captura um CC; o dono persiste no MidiMappingStore. */
    var onLearned: ((MidiAction, Int) -> Unit)? = null

    fun startLearn(action: MidiAction) {
        _learnTarget.value = action
    }

    fun cancelLearn() {
        _learnTarget.value = null
    }

    fun dispatch(message: MidiMessage) {
        _lastMessage.value = message
        val learn = _learnTarget.value
        if (learn != null) {
            // PC nao e remapeavel: em modo Learn so CC captura.
            if (message is MidiMessage.ControlChange) {
                _learnTarget.value = null
                onLearned?.invoke(learn, message.controller)
            }
            return
        }
        when (message) {
            is MidiMessage.ProgramChange ->
                if (message.program in 0 until PRESET_COUNT) handler.loadPreset(message.program)
            is MidiMessage.ControlChange -> {
                val action = mappingProvider().actionFor(message.controller) ?: return
                perform(action, message.value)
            }
        }
    }

    private fun perform(action: MidiAction, value: Int) {
        if (action.isContinuous) {
            val normalized = value / 127f
            when (action) {
                MidiAction.AMP_BASS -> handler.setAmpKnob(AmpKnob.BASS, normalized)
                MidiAction.AMP_MID -> handler.setAmpKnob(AmpKnob.MID, normalized)
                MidiAction.AMP_TREBLE -> handler.setAmpKnob(AmpKnob.TREBLE, normalized)
                MidiAction.AMP_GAIN -> handler.setAmpKnob(AmpKnob.GAIN, normalized)
                MidiAction.AMP_VOLUME -> handler.setAmpKnob(AmpKnob.VOLUME, normalized)
                else -> Unit
            }
            return
        }
        // Toggles/selecoes: borda de subida (footswitch momentaneo manda 127 e depois 0).
        if (value < TOGGLE_THRESHOLD) return
        when (action) {
            MidiAction.SELECT_SLOT_A -> handler.selectSlot(Slot.A)
            MidiAction.SELECT_SLOT_B -> handler.selectSlot(Slot.B)
            MidiAction.SELECT_SLOT_C -> handler.selectSlot(Slot.C)
            MidiAction.NEXT_PRESET -> stepPreset(+1)
            MidiAction.PREV_PRESET -> stepPreset(-1)
            MidiAction.TOGGLE_BYPASS -> handler.toggleBypass()
            MidiAction.TOGGLE_CAB -> handler.toggleCab()
            MidiAction.TOGGLE_GATE -> handler.toggleEffect(EffectSlotType.GATE)
            MidiAction.TOGGLE_COMP -> handler.toggleEffect(EffectSlotType.CMP)
            MidiAction.TOGGLE_EQ -> handler.toggleEffect(EffectSlotType.EQ)
            MidiAction.TOGGLE_MOD -> handler.toggleEffect(EffectSlotType.MOD)
            MidiAction.TOGGLE_DELAY -> handler.toggleEffect(EffectSlotType.DLY)
            MidiAction.TOGGLE_REVERB -> handler.toggleEffect(EffectSlotType.REV)
            else -> Unit
        }
    }

    private fun stepPreset(delta: Int) {
        val current = handler.activePresetId() ?: return
        val next = (current + delta + PRESET_COUNT) % PRESET_COUNT
        handler.loadPreset(next)
    }

    private companion object {
        const val PRESET_COUNT = 20
        const val TOGGLE_THRESHOLD = 64
    }
}
```

**Atenção:** os testes usam `effect:REV` — o enum existente é `EffectSlotType.REV` (não `REVERB`). Confirmar em `app/src/main/java/com/opentonex/controller/ui/editor/EditorScreen.kt` os nomes reais (`GATE, CMP, EQ, MOD, DLY, REV, CAB`) antes de rodar; ajustar o teste se diferirem.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.MidiCommandDispatcherTest"`
Expected: PASS (12 testes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/ app/src/test/java/com/opentonex/controller/midi/
git commit -m "feat(midi): dispatcher de comandos com MIDI Learn"
```

---

### Task 5: PedalMidiActionHandler (adapter para o ViewModel)

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/PedalMidiActionHandler.kt`
- Test: `app/src/test/java/com/opentonex/controller/midi/PedalMidiActionHandlerTest.kt`

- [ ] **Step 1: Write the failing test**

O teste usa `FakePedalConnection` (já existe) e o padrão de `PedalViewModelTest` (Dispatchers.setMain). Verificar o setup exato em `app/src/test/java/com/opentonex/controller/ui/PedalViewModelTest.kt` e replicar.

```kotlin
package com.opentonex.controller.midi

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.PedalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PedalMidiActionHandlerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activePresetId is null when disconnected`() {
        val viewModel = PedalViewModel()
        val handler = PedalMidiActionHandler(viewModel)
        assertNull(handler.activePresetId())
    }

    @Test
    fun `activePresetId reflects connected pedal state`() = runTest(dispatcher.scheduler) {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        advanceUntilIdle()
        val connected = viewModel.state.value as ConnectionState.Connected
        val expected = connected.pedal.presetIds.getOrNull(connected.pedal.activeSlot.ordinal)
        val handler = PedalMidiActionHandler(viewModel)
        assertEquals(expected, handler.activePresetId())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.PedalMidiActionHandlerTest"`
Expected: FAIL (compilação)

- [ ] **Step 3: Write the implementation**

`PedalMidiActionHandler.kt`:

```kotlin
package com.opentonex.controller.midi

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.AmpKnob
import com.opentonex.controller.ui.PedalViewModel
import com.opentonex.controller.ui.editor.EffectSlotType

/**
 * Liga o MIDI aos metodos ja existentes do PedalViewModel. Sem pedal conectado os
 * metodos do ViewModel ja fazem no-op; o gate busy do ViewModel absorve rajadas.
 * Deve ser chamado na main thread.
 */
class PedalMidiActionHandler(private val viewModel: PedalViewModel) : MidiActionHandler {

    override fun selectSlot(slot: Slot) = viewModel.selectSlot(slot)

    override fun loadPreset(presetId: Int) = viewModel.loadPresetToActiveSlot(presetId)

    override fun activePresetId(): Int? {
        val connected = viewModel.state.value as? ConnectionState.Connected ?: return null
        return connected.pedal.presetIds.getOrNull(connected.pedal.activeSlot.ordinal)
    }

    override fun toggleBypass() = viewModel.toggleBypass()

    override fun toggleCab() = viewModel.toggleCabSimBypass()

    override fun toggleEffect(effect: EffectSlotType) = viewModel.toggleEffectEnabled(effect)

    override fun setAmpKnob(knob: AmpKnob, normalized: Float) =
        viewModel.updateAmpKnob(knob, normalized)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.opentonex.controller.midi.PedalMidiActionHandlerTest"`
Expected: PASS (2 testes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/ app/src/test/java/com/opentonex/controller/midi/
git commit -m "feat(midi): adapter do dispatcher para o PedalViewModel"
```

---

### Task 6: MidiInputManager (camada Android — validação manual)

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiInputManager.kt`

Sem unit test (só APIs Android); validação manual na Task 12.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.opentonex.controller.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado da conexao MIDI exibido na aba MIDI. */
sealed interface MidiConnectionState {
    data object Idle : MidiConnectionState
    data object Scanning : MidiConnectionState
    data class Connecting(val deviceName: String) : MidiConnectionState
    data class Connected(val deviceName: String) : MidiConnectionState
    data class Error(val message: String) : MidiConnectionState
}

/** Dispositivo listado na UI: USB MIDI ja plugado ou BLE encontrado no scan. */
data class MidiDeviceUi(
    val id: String,
    val name: String,
    val isBluetooth: Boolean,
    val usbInfo: MidiDeviceInfo? = null,
    val bleDevice: BluetoothDevice? = null
)

/**
 * Unica camada que toca as APIs Android de MIDI/BLE. Lista dispositivos USB MIDI,
 * escaneia BLE MIDI (service UUID padrao) e conecta um dispositivo por vez, entregando
 * as mensagens parseadas em [onMessages] SEMPRE na main thread.
 *
 * Sem logica de dominio aqui: parse fica no MidiParser, mapeamento no dispatcher.
 * As permissoes de Bluetooth devem ser pedidas pela UI ANTES de chamar startBleScan.
 */
class MidiInputManager(
    private val context: Context,
    private val onMessages: (List<MidiMessage>) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val midiManager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else null

    private val parser = MidiParser()
    private var openDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var scanCallback: ScanCallback? = null

    private val _devices = MutableStateFlow<List<MidiDeviceUi>>(emptyList())
    val devices: StateFlow<List<MidiDeviceUi>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<MidiConnectionState>(MidiConnectionState.Idle)
    val state: StateFlow<MidiConnectionState> = _state.asStateFlow()

    val isMidiSupported: Boolean get() = midiManager != null

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val messages = parser.parse(msg, offset, count)
            if (messages.isNotEmpty()) mainHandler.post { onMessages(messages) }
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = refreshUsbDevices()
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            refreshUsbDevices()
            val current = openDevice?.info
            if (current != null && current.id == device.id) handleDisconnected()
        }
    }

    init {
        @Suppress("DEPRECATION")
        midiManager?.registerDeviceCallback(deviceCallback, mainHandler)
        refreshUsbDevices()
    }

    /** Recarrega a lista de dispositivos USB MIDI (BLE encontrados no scan sao mantidos). */
    fun refreshUsbDevices() {
        val manager = midiManager ?: return
        @Suppress("DEPRECATION")
        val usb = manager.devices
            .filter { it.outputPortCount > 0 && it.type != MidiDeviceInfo.TYPE_BLUETOOTH }
            .map { info ->
                MidiDeviceUi(
                    id = "usb-${info.id}",
                    name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                        ?: "USB MIDI ${info.id}",
                    isBluetooth = false,
                    usbInfo = info
                )
            }
        val ble = _devices.value.filter { it.isBluetooth }
        _devices.value = usb + ble
    }

    /** Requer BLUETOOTH_SCAN (S+) ou ACCESS_FINE_LOCATION (<=R) ja concedidas pela UI. */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (midiManager == null) return
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = MidiConnectionState.Error("Bluetooth indisponível")
            return
        }
        stopBleScan()
        _state.value = MidiConnectionState.Scanning
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name ?: return
                val ui = MidiDeviceUi(
                    id = "ble-${device.address}",
                    name = name,
                    isBluetooth = true,
                    bleDevice = device
                )
                if (_devices.value.none { it.id == ui.id }) {
                    _devices.value = _devices.value + ui
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = MidiConnectionState.Error("Scan BLE falhou ($errorCode)")
            }
        }
        scanCallback = callback
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MIDI_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        mainHandler.postDelayed({ stopBleScan() }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
        if (_state.value is MidiConnectionState.Scanning) {
            _state.value = MidiConnectionState.Idle
        }
    }

    fun connect(device: MidiDeviceUi) {
        val manager = midiManager ?: return
        stopBleScan()
        disconnect()
        _state.value = MidiConnectionState.Connecting(device.name)
        val listener = MidiManager.OnDeviceOpenedListener { opened ->
            if (opened == null) {
                _state.value = MidiConnectionState.Error("Falha ao abrir ${device.name}")
                return@OnDeviceOpenedListener
            }
            val port = opened.openOutputPort(0)
            if (port == null) {
                runCatching { opened.close() }
                _state.value = MidiConnectionState.Error("Sem porta MIDI em ${device.name}")
                return@OnDeviceOpenedListener
            }
            port.connect(receiver)
            openDevice = opened
            outputPort = port
            _state.value = MidiConnectionState.Connected(device.name)
        }
        when {
            device.usbInfo != null -> manager.openDevice(device.usbInfo, listener, mainHandler)
            device.bleDevice != null ->
                manager.openBluetoothDevice(device.bleDevice, listener, mainHandler)
            else -> _state.value = MidiConnectionState.Idle
        }
    }

    fun disconnect() {
        runCatching { outputPort?.disconnect(receiver) }
        runCatching { outputPort?.close() }
        runCatching { openDevice?.close() }
        outputPort = null
        openDevice = null
        if (_state.value !is MidiConnectionState.Scanning) {
            _state.value = MidiConnectionState.Idle
        }
    }

    private fun handleDisconnected() {
        disconnect()
        _state.value = MidiConnectionState.Error("Dispositivo MIDI desconectado")
    }

    fun release() {
        stopBleScan()
        disconnect()
        midiManager?.unregisterDeviceCallback(deviceCallback)
    }

    private companion object {
        /** Service UUID padrao do BLE MIDI (spec MMA). */
        val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/midi/MidiInputManager.kt
git commit -m "feat(midi): input manager USB + BLE via android.media.midi"
```

---

### Task 7: MidiController (composição) + wiring no MainActivity

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/midi/MidiController.kt`
- Modify: `app/src/main/java/com/opentonex/controller/MainActivity.kt`
- Modify: `app/src/main/java/com/opentonex/controller/ui/ToneXApp.kt` (assinatura + repasse ao MenuScreen; ver Task 9)

- [ ] **Step 1: Write MidiController**

```kotlin
package com.opentonex.controller.midi

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Composicao de todo o subsistema MIDI: store (mapeamento persistido), dispatcher
 * (mensagem -> acao) e input manager (USB/BLE). Criado uma vez no MainActivity e
 * repassado a UI. E o unico objeto que a UI da aba MIDI conhece.
 */
class MidiController(
    context: Context,
    handler: MidiActionHandler
) {
    private val store = MidiMappingStore(
        SharedPreferencesKeyValueStore(
            context.getSharedPreferences("midi", Context.MODE_PRIVATE)
        )
    )

    val dispatcher = MidiCommandDispatcher(handler) { store.mapping.value }

    private val inputManager = MidiInputManager(context) { messages ->
        messages.forEach(dispatcher::dispatch)
    }

    init {
        dispatcher.onLearned = { action, cc -> store.learn(action, cc) }
    }

    val mapping: StateFlow<MidiMapping> = store.mapping
    val devices: StateFlow<List<MidiDeviceUi>> = inputManager.devices
    val connectionState: StateFlow<MidiConnectionState> = inputManager.state
    val learnTarget: StateFlow<MidiAction?> = dispatcher.learnTarget
    val lastMessage: StateFlow<MidiMessage?> = dispatcher.lastMessage
    val isMidiSupported: Boolean get() = inputManager.isMidiSupported

    fun startBleScan() = inputManager.startBleScan()
    fun stopBleScan() = inputManager.stopBleScan()
    fun refreshUsbDevices() = inputManager.refreshUsbDevices()
    fun connect(device: MidiDeviceUi) = inputManager.connect(device)
    fun disconnect() = inputManager.disconnect()
    fun startLearn(action: MidiAction) = dispatcher.startLearn(action)
    fun cancelLearn() = dispatcher.cancelLearn()
    fun resetMapping() = store.reset()
    fun release() = inputManager.release()
}
```

- [ ] **Step 2: Wire no MainActivity**

Modificar `MainActivity.kt` — o ViewModel passa a ser obtido via `by viewModels()` para que a Activity e o Compose usem a MESMA instância:

```kotlin
package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import java.io.File
import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.midi.MidiController
import com.opentonex.controller.midi.PedalMidiActionHandler
import com.opentonex.controller.ui.PedalViewModel
import com.opentonex.controller.ui.ToneXApp
import com.opentonex.controller.ui.theme.ToneXTheme
import com.opentonex.controller.usb.UsbSerialTransport

class MainActivity : ComponentActivity() {
    private val pedalViewModel: PedalViewModel by viewModels()
    private var midiController: MidiController? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val midi = MidiController(
            context = applicationContext,
            handler = PedalMidiActionHandler(pedalViewModel)
        )
        midiController = midi
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ToneXTheme {
                ToneXApp(
                    windowSizeClass = windowSizeClass,
                    onCreateRealConnection = { createRealConnection() },
                    onCreateFakeConnection = { FakePedalConnection() },
                    onResolveCaptureDirectory = { resolveCaptureDirectory() },
                    midiController = midi,
                    viewModel = pedalViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        midiController?.release()
        midiController = null
        super.onDestroy()
    }

    private suspend fun createRealConnection(): PedalConnection? {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager) ?: return null
        return UsbPedalConnection(transport)
    }

    private fun resolveCaptureDirectory(): File {
        val externalRoot = getExternalFilesDir(null)
        return File(externalRoot ?: filesDir, "event-captures")
    }
}
```

Adicionar em `ToneXApp.kt` o parâmetro `midiController: MidiController? = null` na assinatura de `ToneXApp` e repassar por `ConnectedApp` → `ConnectedNavHost` → `MenuScreen` (o `MenuScreen` ganha `midiController: MidiController? = null`; o uso real na aba é a Task 9 — nesta task só o fio passa, a aba continua placeholder).

- [ ] **Step 3: Verify it compiles and existing tests pass**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos os testes existentes PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/
git commit -m "feat(midi): controller de composicao e wiring no MainActivity"
```

---

### Task 8: Manifest + permissões Bluetooth

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions and features**

Inserir após `<uses-permission android:name="android.permission.RECORD_AUDIO" />`:

```xml
    <uses-feature android:name="android.software.midi" android:required="false" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />

    <!-- BLE scan/connect em Android 6-11 -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <!-- BLE scan/connect em Android 12+ (scan nunca usado para localizacao) -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

E no elemento raiz `<manifest>` adicionar o namespace tools:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(midi): permissoes BLE e features opcionais no manifest"
```

---

### Task 9: Strings (3 idiomas) + MidiTab UI

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/menu/MidiTab.kt`
- Modify: `app/src/main/java/com/opentonex/controller/ui/menu/MenuScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-pt-rBR/strings.xml`, `values-es/strings.xml`

- [ ] **Step 1: Add string resources**

`values/strings.xml` (inglês, base):

```xml
    <string name="midi_devices_title">MIDI devices</string>
    <string name="midi_scan">Scan BLE devices</string>
    <string name="midi_scanning">Scanning…</string>
    <string name="midi_refresh_usb">Refresh USB list</string>
    <string name="midi_connect">Connect</string>
    <string name="midi_disconnect">Disconnect</string>
    <string name="midi_connected_to">Connected to %1$s</string>
    <string name="midi_connecting_to">Connecting to %1$s…</string>
    <string name="midi_no_devices">No MIDI devices found. Pair your footswitch (e.g. M-Vave Chocolate) and scan, or plug a USB MIDI controller through an OTG hub.</string>
    <string name="midi_mapping_title">MIDI mapping</string>
    <string name="midi_learn">Learn</string>
    <string name="midi_learning">Press a switch…</string>
    <string name="midi_cancel">Cancel</string>
    <string name="midi_restore_default">Restore default mapping</string>
    <string name="midi_last_message">Last MIDI message: %1$s</string>
    <string name="midi_permission_needed">Bluetooth permission is required to scan for MIDI footswitches.</string>
    <string name="midi_not_supported">This device does not support MIDI.</string>
    <string name="midi_pc_note">Program Change 0–19 always loads preset 1–20 (matches M-Vave Chocolate factory default).</string>
    <string name="midi_cc_none">—</string>
```

`values-pt-rBR/strings.xml`:

```xml
    <string name="midi_devices_title">Dispositivos MIDI</string>
    <string name="midi_scan">Buscar dispositivos BLE</string>
    <string name="midi_scanning">Buscando…</string>
    <string name="midi_refresh_usb">Atualizar lista USB</string>
    <string name="midi_connect">Conectar</string>
    <string name="midi_disconnect">Desconectar</string>
    <string name="midi_connected_to">Conectado a %1$s</string>
    <string name="midi_connecting_to">Conectando a %1$s…</string>
    <string name="midi_no_devices">Nenhum dispositivo MIDI encontrado. Pareie seu footswitch (ex.: M-Vave Chocolate) e busque, ou conecte um controlador USB MIDI via hub OTG.</string>
    <string name="midi_mapping_title">Mapeamento MIDI</string>
    <string name="midi_learn">Learn</string>
    <string name="midi_learning">Pise em um switch…</string>
    <string name="midi_cancel">Cancelar</string>
    <string name="midi_restore_default">Restaurar mapa padrão</string>
    <string name="midi_last_message">Última mensagem MIDI: %1$s</string>
    <string name="midi_permission_needed">Permissão de Bluetooth é necessária para buscar footswitches MIDI.</string>
    <string name="midi_not_supported">Este aparelho não tem suporte a MIDI.</string>
    <string name="midi_pc_note">Program Change 0–19 sempre carrega o preset 1–20 (compatível com o padrão de fábrica do M-Vave Chocolate).</string>
    <string name="midi_cc_none">—</string>
```

`values-es/strings.xml`:

```xml
    <string name="midi_devices_title">Dispositivos MIDI</string>
    <string name="midi_scan">Buscar dispositivos BLE</string>
    <string name="midi_scanning">Buscando…</string>
    <string name="midi_refresh_usb">Actualizar lista USB</string>
    <string name="midi_connect">Conectar</string>
    <string name="midi_disconnect">Desconectar</string>
    <string name="midi_connected_to">Conectado a %1$s</string>
    <string name="midi_connecting_to">Conectando a %1$s…</string>
    <string name="midi_no_devices">No se encontraron dispositivos MIDI. Empareja tu footswitch (p. ej. M-Vave Chocolate) y busca, o conecta un controlador USB MIDI mediante un hub OTG.</string>
    <string name="midi_mapping_title">Mapeo MIDI</string>
    <string name="midi_learn">Learn</string>
    <string name="midi_learning">Pisa un switch…</string>
    <string name="midi_cancel">Cancelar</string>
    <string name="midi_restore_default">Restaurar mapa por defecto</string>
    <string name="midi_last_message">Último mensaje MIDI: %1$s</string>
    <string name="midi_permission_needed">Se necesita permiso de Bluetooth para buscar footswitches MIDI.</string>
    <string name="midi_not_supported">Este dispositivo no es compatible con MIDI.</string>
    <string name="midi_pc_note">Program Change 0–19 siempre carga el preset 1–20 (compatible con el ajuste de fábrica del M-Vave Chocolate).</string>
    <string name="midi_cc_none">—</string>
```

- [ ] **Step 2: Write MidiTab composable**

`MidiTab.kt`:

```kotlin
package com.opentonex.controller.ui.menu

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opentonex.controller.R
import com.opentonex.controller.midi.MidiAction
import com.opentonex.controller.midi.MidiConnectionState
import com.opentonex.controller.midi.MidiController
import com.opentonex.controller.midi.MidiMessage

/** Permissoes de runtime necessarias para o scan BLE conforme a versao do Android. */
private fun bleScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun MidiTab(controller: MidiController?) {
    if (controller == null || !controller.isMidiSupported) {
        Text(
            text = stringResource(R.string.midi_not_supported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val devices by controller.devices.collectAsStateWithLifecycle()
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val mapping by controller.mapping.collectAsStateWithLifecycle()
    val learnTarget by controller.learnTarget.collectAsStateWithLifecycle()
    val lastMessage by controller.lastMessage.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            permissionDenied = false
            controller.startBleScan()
        } else {
            permissionDenied = true
        }
    }

    // --- Dispositivos ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.midi_devices_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            when (val state = connectionState) {
                is MidiConnectionState.Connected -> Text(
                    text = stringResource(R.string.midi_connected_to, state.deviceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                is MidiConnectionState.Connecting -> Text(
                    text = stringResource(R.string.midi_connecting_to, state.deviceName),
                    style = MaterialTheme.typography.bodyMedium
                )
                is MidiConnectionState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { permissionLauncher.launch(bleScanPermissions()) },
                    enabled = connectionState !is MidiConnectionState.Scanning
                ) {
                    Text(
                        stringResource(
                            if (connectionState is MidiConnectionState.Scanning) {
                                R.string.midi_scanning
                            } else {
                                R.string.midi_scan
                            }
                        )
                    )
                }
                OutlinedButton(onClick = { controller.refreshUsbDevices() }) {
                    Text(stringResource(R.string.midi_refresh_usb))
                }
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.midi_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.midi_no_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (if (device.isBluetooth) "BLE · " else "USB · ") + device.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val isConnectedToThis = (connectionState as? MidiConnectionState.Connected)
                        ?.deviceName == device.name
                    if (isConnectedToThis) {
                        TextButton(onClick = { controller.disconnect() }) {
                            Text(stringResource(R.string.midi_disconnect))
                        }
                    } else {
                        TextButton(onClick = { controller.connect(device) }) {
                            Text(stringResource(R.string.midi_connect))
                        }
                    }
                }
            }
        }
    }

    // --- Mapeamento ---
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.midi_mapping_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.midi_pc_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MidiAction.entries.forEach { action ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = action.label, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mapping.ccFor(action)?.let { "CC $it" }
                                ?: stringResource(R.string.midi_cc_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (learnTarget == action) {
                            TextButton(onClick = { controller.cancelLearn() }) {
                                Text(stringResource(R.string.midi_cancel))
                            }
                            Text(
                                text = stringResource(R.string.midi_learning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = { controller.startLearn(action) }) {
                                Text(stringResource(R.string.midi_learn))
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { controller.resetMapping() }) {
                Text(stringResource(R.string.midi_restore_default))
            }
            val last = lastMessage
            if (last != null) {
                val description = when (last) {
                    is MidiMessage.ProgramChange -> "PC ${last.program}"
                    is MidiMessage.ControlChange -> "CC ${last.controller} = ${last.value}"
                }
                Text(
                    text = stringResource(R.string.midi_last_message, description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 3: Replace placeholder in MenuScreen**

Em `MenuScreen.kt`, adicionar o parâmetro `midiController: MidiController? = null` (import `com.opentonex.controller.midi.MidiController`) e trocar:

```kotlin
                MenuTab.MIDI -> PlaceholderFieldsTab(
                    fields = listOf(
                        "MIDI Channel" to "1",
                        "MIDI Thru" to "Off / Thru / Merge",
                        "Clock Mode" to "Master",
                        "Repeated PC" to "Bypass"
                    )
                )
```

por:

```kotlin
                MenuTab.MIDI -> MidiTab(controller = midiController)
```

A coluna que envolve as tabs precisa rolar (a lista de mapeamento é longa): trocar o `Column` interno por

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
```

com imports `androidx.compose.foundation.rememberScrollState` e `androidx.compose.foundation.verticalScroll`.

Em `ToneXApp.kt`, repassar `midiController` de `ToneXApp` até a chamada do `MenuScreen` (parâmetro novo em `ConnectedApp` e `ConnectedNavHost`, default `null`).

- [ ] **Step 4: Verify build and tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos os testes PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/ app/src/main/res/
git commit -m "feat(midi): aba MIDI com dispositivos, mapeamento e MIDI Learn"
```

---

### Task 10: Version bump V1.0.2

**Files:**
- Modify: `app/build.gradle.kts:15-16`

- [ ] **Step 1: Bump version**

```kotlin
        versionCode = 11
        versionName = "1.0.2"
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump versao para 1.0.2"
```

---

### Task 11: Documentação (README 3 idiomas + USER_GUIDE)

**Files:**
- Modify: `README.md` (badge de versão + seção de destaques V1.0.2 nos 3 idiomas + item "Controle por footswitch MIDI" nas listas de recursos)
- Modify: `docs/USER_GUIDE.md` (seção MIDI nos 3 idiomas)

- [ ] **Step 1: README**

- Badge: `version-1.0.2-brightgreen`.
- Em cada idioma, adicionar bloco "Destaques da V1.0.2" / "V1.0.2 highlights" / "Novedades de la V1.0.2" acima dos destaques da 1.0.1, com: suporte a footswitches MIDI por Bluetooth LE (M-Vave Chocolate e similares) e USB MIDI (via hub OTG); mapa padrão PC 0–19 → presets 1–20 e CCs para slots/bypass/efeitos/knobs; MIDI Learn para remapear qualquer ação.
- Em cada lista de recursos, adicionar: "Controle por footswitch MIDI (BLE e USB) com MIDI Learn." / "MIDI footswitch control (BLE and USB) with MIDI Learn." / "Control por footswitch MIDI (BLE y USB) con MIDI Learn."

- [ ] **Step 2: USER_GUIDE**

Adicionar seção "MIDI" nos 3 idiomas com:
1. Onde fica: Menu → aba MIDI.
2. M-Vave Chocolate: ligar o footswitch, tocar em "Buscar dispositivos BLE", conceder permissão de Bluetooth, tocar "Conectar" no dispositivo. De fábrica o Chocolate envia PC 0–3, que trocam os presets 1–4.
3. Tabela do mapa padrão (a mesma do spec: PC 0–19, CC 20–32, CC 102–106).
4. MIDI Learn: tocar "Learn" na ação, pisar no switch desejado, o CC é gravado; "Restaurar mapa padrão" desfaz tudo.
5. USB MIDI: requer hub OTG (pedal + controlador no mesmo hub).

- [ ] **Step 3: Commit**

```bash
git add README.md docs/USER_GUIDE.md
git commit -m "docs: documenta suporte MIDI da V1.0.2 em tres idiomas"
```

---

### Task 12: Verificação final

- [ ] **Step 1: Full test suite + build**

Run: `./gradlew test assembleDebug`
Expected: BUILD SUCCESSFUL, 0 falhas

- [ ] **Step 2: Smoke test manual (requer hardware)**

No aparelho físico com ToneX One conectado:
1. Menu → MIDI → Buscar BLE → conectar M-Vave Chocolate → pisar switches 1–4 → presets 1–4 trocam no pedal.
2. Learn em "Bypass" → pisar um switch → pisar de novo → bypass alterna.
3. "Restaurar mapa padrão" → mapeamento volta.
4. Desligar o Chocolate → estado de erro aparece na aba MIDI; reconectar funciona.
5. (Se disponível) controlador USB MIDI via hub OTG aparece na lista e funciona.

Registrar os resultados no commit final ou em nota da release.

- [ ] **Step 3: Final commit (se houver ajustes)**

```bash
git add -A
git commit -m "fix(midi): ajustes do smoke test em hardware"
```
