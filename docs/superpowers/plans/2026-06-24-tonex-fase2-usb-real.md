# ToneX Fase 2 — Conexão USB Real Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o `FakePedalConnection` por uma conexão USB real (`UsbPedalConnection` + `UsbSerialTransport`) que fala com o pedal físico ToneX One V1 (USB CDC ACM, VID `0x1963` / PID `0x00D1`), reaproveitando 100% da camada `protocol`/`repository` já testada na Fase 1.

**Architecture:** Nova camada `connection.PedalTransport` (interface de I/O puro: write/readFrame/open/close) isola o protocolo HDLC/tagged-values (testável em JVM com `FakePedalTransport`) do código Android-specific (`usb.UsbSerialTransport`, que usa `UsbManager` + a lib `usb-serial-for-android` e só pode ser verificado manualmente, sem unit test JVM). `UsbPedalConnection` implementa a interface `PedalConnection` existente sobre um `PedalTransport`, reaproveitando `HdlcCodec` e `TonexMessages`. `TonexMessages` ganha `parseState`/`buildSetStatePayload` para decodificar/codificar o `StateResponse` real do pedal — os offsets de campo são **best-effort** (baseados em engenharia reversa de terceiros, não verificados ainda) e a última tarefa do plano calibra isso contra o pedal físico que já está conectado.

**Tech Stack:** Kotlin, Coroutines, Android `UsbManager`/USB Host, biblioteca `com.github.mik3y:usb-serial-for-android:3.10.0` (via JitPack) para o transporte CDC ACM, JUnit4 + `kotlinx-coroutines-test` para os testes JVM existentes.

---

## Contexto importante

- O offset inicial dos campos do `StateResponse` (`STATE_FIELDS_OFFSET`) e a ordem dos campos (input trim float → cab sim bypass byte → tuning mode byte → coleção RGB `0xBA` → coleção de slots `0xBC` → active slot byte → A4 `u16` → direct monitor byte → tempo BPM float) vêm de um resumo de engenharia reversa de terceiros (`vit3k/tonex_controller/protocol.md`) e **não foram confirmados com o pedal real**. Eles estão centralizados em constantes nomeadas (`UsbPedalConnection.STATE_FIELDS_OFFSET`, schema sequencial em `TonexMessages.parseState`) propositalmente, para serem fáceis de corrigir na Tarefa 8 (calibração manual).
- `rawState` é **sempre** preservado intacto a partir dos bytes recebidos do pedal — mesmo que o parse de campos individuais esteja errado, o ciclo ler→trocar slot→escrever nunca corrompe bytes desconhecidos (mesma regra de ouro da Fase 1).
- Nenhuma UI nova é construída aqui (isso é Fase 3). A Tarefa 7 adiciona só um botão de debug temporário na `MainActivity` para permitir a verificação manual da Tarefa 8.

---

### Task 1: Dependência `usb-serial-for-android`

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Adicionar o repositório JitPack**

Em `settings.gradle.kts`, dentro do bloco `dependencyResolutionManagement { repositories { ... } }`, adicione a linha `maven(url = "https://jitpack.io")` depois de `mavenCentral()`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

- [ ] **Step 2: Adicionar a versão e a lib no catálogo**

Em `gradle/libs.versions.toml`, na seção `[versions]` adicione:

```toml
usbSerial = "3.10.0"
```

Na seção `[libraries]` adicione:

```toml
usb-serial-android = { module = "com.github.mik3y:usb-serial-for-android", version.ref = "usbSerial" }
```

- [ ] **Step 3: Adicionar a dependência no módulo `app`**

Em `app/build.gradle.kts`, dentro do bloco `dependencies { ... }`, adicione junto às outras `implementation`:

```kotlin
implementation(libs.usb.serial.android)
```

- [ ] **Step 4: Sincronizar o Gradle e confirmar resolução**

Run: `./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` (ou `gradlew.bat` se não houver wrapper Unix)
Expected: a árvore de dependências lista `com.github.mik3y:usb-serial-for-android:3.10.0` sem erro de resolução.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: adiciona dependencia usb-serial-for-android (JitPack)"
```

---

### Task 2: Manifest e filtro de dispositivo USB

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/usb_device_filter.xml`

- [ ] **Step 1: Criar o filtro de dispositivo USB**

Crie `app/src/main/res/xml/usb_device_filter.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ToneX One V1: VID 0x1963 = 6499, PID 0x00D1 = 209 -->
    <usb-device vendor-id="6499" product-id="209" />
</resources>
```

- [ ] **Step 2: Declarar suporte a USB Host e o intent-filter de dispositivo conectado**

Edite `app/src/main/AndroidManifest.xml` para o conteúdo abaixo (adiciona `uses-feature` e o `intent-filter`/`meta-data` de USB na `MainActivity`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.usb.host" android:required="true" />

    <application
        android:label="ToneX Controller"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
            </intent-filter>
            <meta-data
                android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
                android:resource="@xml/usb_device_filter" />
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Verificar build**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, sem erro de manifest/recurso.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/usb_device_filter.xml
git commit -m "feat(usb): declara suporte a USB Host e filtro de dispositivo do ToneX One"
```

---

### Task 3: Interface `PedalTransport`

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/connection/PedalTransport.kt`

- [ ] **Step 1: Criar a interface e a exceção de timeout**

Crie `app/src/main/java/com/opentonex/controller/connection/PedalTransport.kt`:

```kotlin
package com.opentonex.controller.connection

/** I/O puro de bytes, sem conhecimento de HDLC ou do protocolo ToneX. */
interface PedalTransport {
    suspend fun open()
    suspend fun write(bytes: ByteArray)
    /** Bloqueia até receber um frame HDLC completo (0x7E ... 0x7E) ou estourar o timeout. */
    suspend fun readFrame(timeoutMs: Long): ByteArray
    suspend fun close()
}

class PedalTransportTimeoutException(message: String) : Exception(message)
```

Não há teste isolado para esta interface (é só um contrato); ela é exercida pelos testes da Task 5 via uma fake implementação.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/connection/PedalTransport.kt
git commit -m "feat(connection): interface PedalTransport para I/O de bytes desacoplado do USB"
```

---

### Task 4: `TonexMessages.parseState` e `buildSetStatePayload`

**Files:**
- Modify: `app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt`
- Modify: `app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt`

- [ ] **Step 1: Escrever os testes que falham (payload sintético do StateResponse)**

Adicione ao final de `app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt` (mantendo os testes existentes):

```kotlin
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

private fun syntheticStatePayload(activeSlotByte: Byte = 1): ByteArray {
    val header = ByteArray(13) // header bruto do StateResponse, ignorado pelo parser
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00) // cabSimBypass=on, tuningMode=mute
    val colors = byteArrayOf(
        0xBA.toByte(), 3,
        255.toByte(), 0, 0,
        0, 255.toByte(), 0,
        0, 0, 255.toByte()
    )
    val slotAssignment = byteArrayOf(0xBC.toByte(), 6, 0, 0, 0, 0, 0, 0)
    val a4 = TaggedValue.encodeU16(440, tag = 0x81)
    val directMonitor = byteArrayOf(0)
    val tempo = TaggedValue.encodeFloat(120.0f)

    return header + trim + flags + colors + slotAssignment +
        byteArrayOf(activeSlotByte) + a4 + directMonitor + tempo
}

class TonexMessagesStateTest {
    @Test fun `parseState decodes documented fields from synthetic payload`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val state = TonexMessages.parseState(payload, fieldsOffset = 13)

        assertEquals(1.5f, state.inputTrim)
        assertEquals(Slot.B, state.activeSlot)
        assertEquals(440, state.a4Reference)
        assertEquals(120, state.tempo)
        assertEquals(3, state.slots.size)
        assertEquals(Rgb(255, 0, 0), state.slots[0].color)
        assertEquals(Rgb(0, 255, 0), state.slots[1].color)
        assertEquals(Rgb(0, 0, 255), state.slots[2].color)
        assertArrayEquals(payload, state.rawState)
    }

    @Test fun `buildSetStatePayload mutates only the active slot byte`() {
        val payload = syntheticStatePayload(activeSlotByte = 1)

        val updated = TonexMessages.buildSetStatePayload(payload, fieldsOffset = 13, newSlot = Slot.C)

        val expected = payload.copyOf()
        val activeSlotOffset = TonexMessages.activeSlotOffset(payload, fieldsOffset = 13)
        expected[activeSlotOffset] = 2
        assertArrayEquals(expected, updated)
    }
}
```

Esse teste fica em uma classe separada (`TonexMessagesStateTest`) no mesmo arquivo para não conflitar com os imports/testes existentes de `TonexMessagesTest`.

- [ ] **Step 2: Rodar e confirmar que falha (métodos ainda não existem)**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.opentonex.controller.protocol.TonexMessagesStateTest"`
Expected: FAIL — `unresolved reference: parseState` (erro de compilação).

- [ ] **Step 3: Implementar `parseState`, `activeSlotOffset` e `buildSetStatePayload`**

Adicione ao final de `app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt` (dentro do `object TonexMessages`, antes do `}` final):

```kotlin
    // --- StateResponse: offsets best-effort (engenharia reversa de terceiros,
    // nao verificados contra o pedal real ainda - calibrar na Fase 2, Tarefa 8) ---

    private const val RGB_COLLECTION_TAG = 0xBA
    private const val SLOT_COLLECTION_TAG = 0xBC

    /**
     * Decodifica os campos conhecidos do StateResponse a partir de [fieldsOffset]
     * (posicao do primeiro byte de tag do campo "input trim"). Sempre preserva
     * [payload] completo em [PedalState.rawState], mesmo que os offsets estejam errados.
     */
    fun parseState(payload: ByteArray, fieldsOffset: Int): com.opentonex.controller.domain.PedalState {
        val walk = walkFields(payload, fieldsOffset)
        val slots = walk.colors.take(3).mapIndexed { index, color ->
            com.opentonex.controller.domain.PresetSlot(
                index = index,
                name = "Preset ${('A' + index)}",
                color = color
            )
        }
        return com.opentonex.controller.domain.PedalState(
            activeSlot = slotFromByte(walk.activeSlotByte),
            inputTrim = walk.inputTrim,
            a4Reference = walk.a4Reference,
            tempo = walk.tempoBpm.toInt(),
            slots = slots,
            rawState = payload
        )
    }

    /** Offset absoluto, dentro de [rawState], do byte de slot ativo. */
    fun activeSlotOffset(rawState: ByteArray, fieldsOffset: Int): Int =
        walkFields(rawState, fieldsOffset).activeSlotOffset

    /** Regrava o estado mudando so o byte de slot ativo, preservando todo o resto. */
    fun buildSetStatePayload(
        rawState: ByteArray,
        fieldsOffset: Int,
        newSlot: com.opentonex.controller.domain.Slot
    ): ByteArray = buildSlotChangePayload(
        rawState = rawState,
        activeSlotOffset = activeSlotOffset(rawState, fieldsOffset),
        newSlotValue = slotToByte(newSlot)
    )

    private class FieldsWalk(
        val inputTrim: Float,
        val colors: List<com.opentonex.controller.domain.Rgb>,
        val activeSlotByte: Int,
        val activeSlotOffset: Int,
        val a4Reference: Int,
        val tempoBpm: Float
    )

    private fun walkFields(payload: ByteArray, fieldsOffset: Int): FieldsWalk {
        var offset = fieldsOffset
        val trim = TaggedValue.decodeFloat(payload, offset); offset = trim.nextOffset
        offset += 2 // cabSimBypass + tuningMode (bytes crus, ainda nao usados na UI)

        require((payload[offset].toInt() and 0xFF) == RGB_COLLECTION_TAG) {
            "esperava colecao RGB (0x${RGB_COLLECTION_TAG.toString(16)}) no offset $offset"
        }
        offset++
        val colorCount = payload[offset].toInt() and 0xFF
        offset++
        val colors = ArrayList<com.opentonex.controller.domain.Rgb>(colorCount)
        repeat(colorCount) {
            colors.add(
                com.opentonex.controller.domain.Rgb(
                    r = payload[offset].toInt() and 0xFF,
                    g = payload[offset + 1].toInt() and 0xFF,
                    b = payload[offset + 2].toInt() and 0xFF
                )
            )
            offset += 3
        }

        require((payload[offset].toInt() and 0xFF) == SLOT_COLLECTION_TAG) {
            "esperava colecao de slots (0x${SLOT_COLLECTION_TAG.toString(16)}) no offset $offset"
        }
        offset++
        val slotBytesCount = payload[offset].toInt() and 0xFF
        offset++
        offset += slotBytesCount // bytes de slot assignment, ainda nao usados na UI

        val activeSlotOffset = offset
        val activeSlotByte = payload[offset].toInt() and 0xFF
        offset++

        val a4 = TaggedValue.decodeU16(payload, offset); offset = a4.nextOffset
        offset += 1 // directMonitor (byte cru, ainda nao usado na UI)

        val tempo = TaggedValue.decodeFloat(payload, offset)

        return FieldsWalk(
            inputTrim = trim.value,
            colors = colors,
            activeSlotByte = activeSlotByte,
            activeSlotOffset = activeSlotOffset,
            a4Reference = a4.value,
            tempoBpm = tempo.value
        )
    }

    private fun slotFromByte(value: Int): com.opentonex.controller.domain.Slot = when (value) {
        0 -> com.opentonex.controller.domain.Slot.A
        1 -> com.opentonex.controller.domain.Slot.B
        else -> com.opentonex.controller.domain.Slot.C
    }

    fun slotToByte(slot: com.opentonex.controller.domain.Slot): Int = when (slot) {
        com.opentonex.controller.domain.Slot.A -> 0
        com.opentonex.controller.domain.Slot.B -> 1
        com.opentonex.controller.domain.Slot.C -> 2
    }
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.opentonex.controller.protocol.*"`
Expected: `BUILD SUCCESSFUL`, todos os testes de `TonexMessagesTest` e `TonexMessagesStateTest` em verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt
git commit -m "feat(protocol): parseState/buildSetStatePayload best-effort do StateResponse real"
```

---

### Task 5: `UsbPedalConnection`

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/connection/UsbPedalConnection.kt`
- Create: `app/src/test/java/com/opentonex/controller/connection/UsbPedalConnectionTest.kt`

- [ ] **Step 1: Escrever os testes que falham**

Crie `app/src/test/java/com/opentonex/controller/connection/UsbPedalConnectionTest.kt`:

```kotlin
package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TaggedValue
import com.opentonex.controller.protocol.TonexMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePedalTransport : PedalTransport {
    val written = mutableListOf<ByteArray>()
    var nextFrame: ByteArray = ByteArray(0)
    var opened = false
    var closed = false

    override suspend fun open() { opened = true }
    override suspend fun write(bytes: ByteArray) { written.add(bytes) }
    override suspend fun readFrame(timeoutMs: Long): ByteArray = nextFrame
    override suspend fun close() { closed = true }
}

private fun syntheticStatePayload(activeSlotByte: Byte): ByteArray {
    val header = ByteArray(UsbPedalConnection.STATE_FIELDS_OFFSET)
    val trim = TaggedValue.encodeFloat(1.5f)
    val flags = byteArrayOf(0x01, 0x00)
    val colors = byteArrayOf(
        0xBA.toByte(), 3,
        255.toByte(), 0, 0,
        0, 255.toByte(), 0,
        0, 0, 255.toByte()
    )
    val slotAssignment = byteArrayOf(0xBC.toByte(), 6, 0, 0, 0, 0, 0, 0)
    val a4 = TaggedValue.encodeU16(440, tag = 0x81)
    val directMonitor = byteArrayOf(0)
    val tempo = TaggedValue.encodeFloat(120.0f)
    return header + trim + flags + colors + slotAssignment +
        byteArrayOf(activeSlotByte) + a4 + directMonitor + tempo
}

class UsbPedalConnectionTest {
    @Test fun `connect opens the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.connect()

        assertTrue(transport.opened)
    }

    @Test fun `sendHello writes encoded hello and parses firmware from the response`() = runTest {
        val transport = FakePedalTransport()
        val responsePayload = byteArrayOf(0x81.toByte(), 0x0A, 0x00) + "1.2.3".toByteArray(Charsets.US_ASCII)
        transport.nextFrame = HdlcCodec.encode(responsePayload)
        val connection = UsbPedalConnection(transport)

        val firmware = connection.sendHello()

        assertEquals("1.2.3", firmware.version)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.helloPayload()), transport.written.single())
    }

    @Test fun `requestState decodes pedal state from the response frame`() = runTest {
        val transport = FakePedalTransport()
        transport.nextFrame = HdlcCodec.encode(syntheticStatePayload(activeSlotByte = 1))
        val connection = UsbPedalConnection(transport)

        val state = connection.requestState()

        assertEquals(Slot.B, state.activeSlot)
        assertArrayEquals(HdlcCodec.encode(TonexMessages.requestStatePayload()), transport.written.single())
    }

    @Test fun `writeState sends the mutated raw bytes back through the transport`() = runTest {
        val transport = FakePedalTransport()
        val statePayload = syntheticStatePayload(activeSlotByte = 1)
        val connection = UsbPedalConnection(transport)
        val state = TonexMessages.parseState(statePayload, fieldsOffset = UsbPedalConnection.STATE_FIELDS_OFFSET)
            .withActiveSlot(Slot.C)

        connection.writeState(state)

        val sentFrame = transport.written.single()
        val decoded = HdlcCodec.decode(sentFrame) as HdlcFrame.Valid
        val resultState = TonexMessages.parseState(decoded.payload, fieldsOffset = UsbPedalConnection.STATE_FIELDS_OFFSET)
        assertEquals(Slot.C, resultState.activeSlot)
    }

    @Test fun `disconnect closes the transport`() = runTest {
        val transport = FakePedalTransport()
        val connection = UsbPedalConnection(transport)

        connection.disconnect()

        assertTrue(transport.closed)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha (classe ainda não existe)**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.opentonex.controller.connection.UsbPedalConnectionTest"`
Expected: FAIL — erro de compilação `unresolved reference: UsbPedalConnection`.

- [ ] **Step 3: Implementar `UsbPedalConnection`**

Crie `app/src/main/java/com/opentonex/controller/connection/UsbPedalConnection.kt`:

```kotlin
package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.protocol.HdlcCodec
import com.opentonex.controller.protocol.HdlcFrame
import com.opentonex.controller.protocol.TonexMessages

class PedalProtocolException(message: String) : Exception(message)

/** Implementacao real de [PedalConnection], falando HDLC/ToneX sobre um [PedalTransport]. */
class UsbPedalConnection(
    private val transport: PedalTransport,
    private val fieldsOffset: Int = STATE_FIELDS_OFFSET
) : PedalConnection {

    override suspend fun connect() {
        transport.open()
    }

    override suspend fun sendHello(): FirmwareInfo =
        TonexMessages.parseFirmware(roundTrip(TonexMessages.helloPayload()))

    override suspend fun requestState(): PedalState =
        TonexMessages.parseState(roundTrip(TonexMessages.requestStatePayload()), fieldsOffset)

    override suspend fun writeState(state: PedalState) {
        val payload = TonexMessages.buildSetStatePayload(state.rawState, fieldsOffset, state.activeSlot)
        transport.write(HdlcCodec.encode(payload))
    }

    override suspend fun disconnect() {
        transport.close()
    }

    private suspend fun roundTrip(payload: ByteArray): ByteArray {
        transport.write(HdlcCodec.encode(payload))
        val frame = transport.readFrame(RESPONSE_TIMEOUT_MS)
        return when (val decoded = HdlcCodec.decode(frame)) {
            is HdlcFrame.Valid -> decoded.payload
            HdlcFrame.CrcError -> throw PedalProtocolException("CRC invalido na resposta do pedal")
            HdlcFrame.Incomplete -> throw PedalProtocolException("frame incompleto recebido do pedal")
        }
    }

    companion object {
        /** Offset estimado do 1o campo do StateResponse - ver nota de calibracao no topo do plano. */
        const val STATE_FIELDS_OFFSET = 13
        const val RESPONSE_TIMEOUT_MS = 2000L
    }
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.opentonex.controller.connection.*"`
Expected: `BUILD SUCCESSFUL`, todos os testes de `FakePedalConnectionTest` e `UsbPedalConnectionTest` em verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/connection/UsbPedalConnection.kt app/src/test/java/com/opentonex/controller/connection/UsbPedalConnectionTest.kt
git commit -m "feat(connection): UsbPedalConnection sobre PedalTransport, testado com fake"
```

---

### Task 6: `UsbSerialTransport` (camada Android real)

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/usb/UsbSerialTransport.kt`

> Esta classe usa `android.hardware.usb.UsbManager` e a lib `usb-serial-for-android` — não roda em teste JVM puro. É verificada manualmente na Tarefa 8.

- [ ] **Step 1: Implementar o transporte real**

Crie `app/src/main/java/com/opentonex/controller/usb/UsbSerialTransport.kt`:

```kotlin
package com.opentonex.controller.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.opentonex.controller.connection.PedalTransport
import com.opentonex.controller.connection.PedalTransportTimeoutException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val ACTION_USB_PERMISSION = "com.opentonex.controller.USB_PERMISSION"
private const val TONEX_VENDOR_ID = 0x1963
private const val TONEX_PRODUCT_ID = 0x00D1
private const val BAUD_RATE = 115200
private const val IO_TIMEOUT_MS = 500
private const val READ_CHUNK_SIZE = 4096
private const val FLAG = 0x7E

/** [PedalTransport] real sobre um [UsbSerialPort] ja aberto (ver [connect]). */
class UsbSerialTransport(private val port: UsbSerialPort) : PedalTransport {

    override suspend fun open() {
        // Conexao/permissao/abertura ja feitas em connect(); nada a fazer aqui.
    }

    override suspend fun write(bytes: ByteArray) {
        port.write(bytes, IO_TIMEOUT_MS)
    }

    override suspend fun readFrame(timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(READ_CHUNK_SIZE)
        val frame = ArrayList<Byte>()
        var sawStartFlag = false
        while (System.currentTimeMillis() < deadline) {
            val read = port.read(buffer, IO_TIMEOUT_MS)
            for (i in 0 until read) {
                val b = buffer[i]
                val isFlag = (b.toInt() and 0xFF) == FLAG
                if (isFlag && !sawStartFlag) {
                    sawStartFlag = true
                    frame.add(b)
                } else if (isFlag && sawStartFlag) {
                    frame.add(b)
                    return frame.toByteArray()
                } else if (sawStartFlag) {
                    frame.add(b)
                }
            }
        }
        throw PedalTransportTimeoutException("sem resposta do pedal em ${timeoutMs}ms")
    }

    override suspend fun close() {
        port.close()
    }

    companion object {
        /** Localiza o ToneX One, pede permissao se preciso, abre a porta serial. Null se nao encontrado. */
        suspend fun connect(context: Context, manager: UsbManager): UsbSerialTransport? {
            val device = manager.deviceList.values.firstOrNull {
                it.vendorId == TONEX_VENDOR_ID && it.productId == TONEX_PRODUCT_ID
            } ?: return null

            if (!manager.hasPermission(device) && !requestPermission(context, manager, device)) {
                return null
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return null
            val connection = manager.openDevice(driver.device) ?: return null
            val port = driver.ports.firstOrNull() ?: return null
            port.open(connection)
            port.setParameters(BAUD_RATE, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            return UsbSerialTransport(port)
        }

        private suspend fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean =
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        receiverContext.unregisterReceiver(this)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(ACTION_USB_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE
                )
                manager.requestPermission(device, pendingIntent)
            }
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/usb/UsbSerialTransport.kt
git commit -m "feat(usb): UsbSerialTransport real sobre usb-serial-for-android (CDC ACM)"
```

---

### Task 7: Wiring de debug na `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/opentonex/controller/MainActivity.kt`

> Wiring temporário só para permitir a verificação manual da Tarefa 8. A Fase 3 substitui isso pela tela de Conexão real.

- [ ] **Step 1: Adicionar botão de conexão de debug**

Substitua o conteúdo de `app/src/main/java/com/opentonex/controller/MainActivity.kt`:

```kotlin
package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import com.opentonex.controller.usb.UsbSerialTransport
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by remember { mutableStateOf("Desconectado") }
            val scope = rememberCoroutineScope()

            Column(modifier = Modifier.padding(16.dp)) {
                Text("ToneX Controller")
                Text(status)
                Button(onClick = {
                    scope.launch {
                        status = "Conectando..."
                        status = connectToRealPedal()
                    }
                }) {
                    Text("Conectar pedal (debug)")
                }
            }
        }
    }

    private suspend fun connectToRealPedal(): String = try {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager)
            ?: return "Pedal nao encontrado via USB"
        val repository = PedalRepository(UsbPedalConnection(transport))
        repository.connect()
        when (val state = repository.state.value) {
            is ConnectionState.Connected ->
                "Conectado: firmware ${state.firmware.version} - slot ${state.pedal.activeSlot}"
            ConnectionState.Disconnected -> "Falha ao conectar"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }
}
```

- [ ] **Step 2: Verificar que compila e instala**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/MainActivity.kt
git commit -m "feat(debug): botao manual para conectar ao pedal real via USB"
```

---

### Task 8: Verificação e calibração manual com o pedal real

**Files:** nenhum criado por padrão — ajustes pontuais em `TonexMessages.kt` / `UsbPedalConnection.kt` conforme o que for observado.

> Esta tarefa não pode ser automatizada — precisa do celular com o pedal físico conectado via USB-C/OTG. É o roteiro de "verificação manual (pedal real)" previsto no spec da Fase 1 (seção 8).

- [ ] **Step 1: Instalar e abrir o app**

Run: `./gradlew.bat :app:installDebug` com o celular conectado por ADB e o pedal já plugado nele via USB-OTG.
Expected: app abre mostrando "Desconectado" e o botão "Conectar pedal (debug)".

- [ ] **Step 2: Conceder a permissão USB e conectar**

Toque em "Conectar pedal (debug)". O Android deve mostrar o diálogo de permissão USB para o app — conceda.
Expected: status muda para `Conectando...` e depois para `Conectado: firmware X.Y.Z - slot <A|B|C>` (ou uma mensagem de `Erro:` — capture a mensagem exata se isso ocorrer).

- [ ] **Step 3: Validar o slot ativo decodificado**

No pedal físico, anote visualmente qual slot (A, B ou C) está ativo (LED/cor do footswitch).
Compare com o slot mostrado no app.
- Se bateram: os offsets de `STATE_FIELDS_OFFSET`/sequência de campos em `TonexMessages.parseState` estão corretos para o slot ativo — siga para o Step 4.
- Se não bateram: capture via `adb logcat` os bytes brutos (adicione temporariamente um `android.util.Log.d("ToneX", payload.joinToString(","))` em `UsbPedalConnection.requestState()` antes do `parseState`, reinstale, repita o teste) e ajuste `STATE_FIELDS_OFFSET` e/ou a ordem de campos em `walkFields` até o slot decodificado bater com o slot físico real. Repita até bater.

- [ ] **Step 4: Validar a troca de slot (escrita)**

No pedal físico, troque manualmente de slot (ex: vá de A para B apertando o footswitch). Toque em "Conectar pedal (debug)" de novo para reler o estado e confirme que o app mostra o novo slot.
Em seguida, seria necessário um botão de troca de slot pelo app para validar o `writeState` fim a fim — isso é construído na Fase 3 (tela Home com seleção de slot). Por ora, registre no commit de calibração que `writeState`/`buildSetStatePayload` ainda não foi validado contra o pedal real (apenas via teste com payload sintético na Task 5) e que isso será confirmado quando a Fase 3 expuser o botão de troca de slot na UI.

- [ ] **Step 5: Validar desconexão**

Desconecte o cabo USB do pedal com o app aberto.
Expected: o app não trava (mesmo que o status fique parado na última leitura — tratamento de erro de desconexão em runtime é trabalho da Fase 3/Fase 4, não desta tarefa).

- [ ] **Step 6: Commit da calibração (se houve ajuste de offsets)**

Se algum offset/ordem de campo foi corrigido no Step 3:

```bash
git add app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt app/src/main/java/com/opentonex/controller/connection/UsbPedalConnection.kt
git commit -m "fix(protocol): calibra offsets do StateResponse contra o pedal ToneX One real"
```

Se nenhum ajuste foi necessário, nenhum commit extra é preciso — a Fase 2 está concluída.

---

## Self-Review (cobertura do spec)

- USB Host/OTG + CDC ACM real (spec §2, §9): Tasks 1, 2, 6.
- Camadas isoladas preservando `connection` testável sem hardware (spec §3): Tasks 3, 5 (FakePedalTransport) vs Task 6 (Android real).
- Regra de ouro ler→modificar→escrever preservando bytes raw (spec §2): `buildSetStatePayload`/`activeSlotOffset` na Task 4, reaproveitando `buildSlotChangePayload` da Fase 1.
- Troca de slot A/B/C com baixa latência (spec §4 Home): write-path implementado na Task 4/5; validação fim a fim com UI fica para a Fase 3 (anotado explicitamente na Task 8).
- Tratamento de erro de desconexão/CRC/timeout (spec §7): `PedalProtocolException`/`PedalTransportTimeoutException` na Task 3/5; comportamento de UI ao desconectar fica para fases futuras (fora do escopo desta fase, que é só a camada de dados).
- Verificação manual com pedal real (spec §8): Task 8.
