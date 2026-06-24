# ToneX One V1 — Fase 1: Fundação & Protocolo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o núcleo testável do app — scaffold Android, codec HDLC/CRC, serialização das mensagens ToneX, modelo de domínio e pedal simulado — tudo verificável por testes JVM sem hardware.

**Architecture:** Camadas isoladas em Kotlin puro (sem dependências Android no núcleo): `protocol` (HDLC + mensagens), `domain` (tipos imutáveis do estado) e `connection` (interface + FakePedal). A UI e o USB real ficam para as Fases 2 e 3. O núcleo é 100% testável com JUnit na JVM.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL), Android Gradle Plugin, Jetpack Compose (mínimo nesta fase), JUnit4, kotlinx-coroutines (StateFlow/Turbine para testes).

---

## Roadmap geral (contexto)

- **Fase 1 (este plano):** scaffold + protocolo + domínio + FakePedal + repositório. Sem hardware.
- **Fase 2:** `UsbPedalConnection` (Android USB Host / CDC ACM), permissão USB, detecção de device, captura de fixtures reais. Requer pedal.
- **Fase 3:** UI Compose (telas Connect / Home / Editor / Settings), responsividade phone+tablet, tema escuro estilo ToneX Control.

A Fase 1 não depende de hardware. As capturas reais de bytes do pedal são feitas no início da Fase 2 e usadas para refinar offsets do parser de estado (que aqui é construído sobre a estrutura documentada).

---

## Estrutura de arquivos (Fase 1)

```
settings.gradle.kts                      raiz do projeto Gradle
build.gradle.kts                         config raiz (plugins, versões)
gradle/libs.versions.toml                catálogo de versões
gradle.properties                        flags Gradle/AndroidX
app/build.gradle.kts                     módulo app (Android + Compose + testes)
app/src/main/AndroidManifest.xml         manifest mínimo
app/src/main/java/com/opentonex/controller/MainActivity.kt   Activity placeholder
app/src/main/java/com/opentonex/controller/protocol/Crc16Ccitt.kt
app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt
app/src/main/java/com/opentonex/controller/protocol/TaggedValue.kt
app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt
app/src/main/java/com/opentonex/controller/domain/PedalState.kt
app/src/main/java/com/opentonex/controller/connection/PedalConnection.kt
app/src/main/java/com/opentonex/controller/connection/FakePedalConnection.kt
app/src/main/java/com/opentonex/controller/repository/PedalRepository.kt
app/src/test/java/com/opentonex/controller/protocol/Crc16CcittTest.kt
app/src/test/java/com/opentonex/controller/protocol/HdlcCodecTest.kt
app/src/test/java/com/opentonex/controller/protocol/TaggedValueTest.kt
app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt
app/src/test/java/com/opentonex/controller/connection/FakePedalConnectionTest.kt
app/src/test/java/com/opentonex/controller/repository/PedalRepositoryTest.kt
```

Responsabilidades:
- `Crc16Ccitt` — só calcula o CRC-CCITT.
- `HdlcCodec` — só framing/deframing (flags `0x7E`, byte-stuffing, anexa/valida CRC).
- `TaggedValue` — só (de)serialização dos tipos primitivos tagueados (número 2B LE, float, byte).
- `TonexMessages` — monta/parseia Hello, RequestState, StateResponse, StateUpdate.
- `PedalState` etc. — tipos imutáveis do domínio.
- `PedalConnection` — interface; `FakePedalConnection` — implementação em memória.
- `PedalRepository` — orquestra ler→modificar→escrever e expõe `StateFlow`.

---

## Task 1: Scaffold do projeto Android

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/opentonex/controller/MainActivity.kt`

- [ ] **Step 1: Criar o catálogo de versões** `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
coroutines = "1.8.1"
composeBom = "2024.09.02"
activityCompose = "1.9.2"
coreKtx = "1.13.1"
junit = "4.13.2"
turbine = "1.1.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Criar `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ToneXController"
include(":app")
```

- [ ] **Step 3: Criar `build.gradle.kts` (raiz)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 4: Criar `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Criar `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.opentonex.controller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.opentonex.controller"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 6: Criar `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
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
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Criar `MainActivity.kt` (placeholder mínimo)**

```kotlin
package com.opentonex.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("ToneX Controller") }
    }
}
```

- [ ] **Step 8: Gerar o Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.9` (ou usar um wrapper existente).
Expected: cria `gradlew`, `gradlew.bat`, `gradle/wrapper/`.

- [ ] **Step 9: Verificar que os testes unitários rodam (vazio ainda)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (nenhum teste ainda, mas compila).

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ app/ gradlew gradlew.bat
git commit -m "chore: scaffold do projeto Android (Kotlin + Compose + testes JVM)"
```

---

## Task 2: CRC-CCITT

CRC-CCITT (XModem): polinômio `0x1021`, valor inicial `0x0000`, sem reflexão. Será validado contra fixtures reais na Fase 2; nesta fase usamos vetores conhecidos do padrão XModem.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/protocol/Crc16Ccitt.kt`
- Test: `app/src/test/java/com/opentonex/controller/protocol/Crc16CcittTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16CcittTest {
    @Test fun `crc of empty is zero`() {
        assertEquals(0x0000, Crc16Ccitt.compute(byteArrayOf()))
    }

    @Test fun `crc of ASCII 123456789 matches XModem vector`() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x31C3, Crc16Ccitt.compute(data))
    }

    @Test fun `crc of single byte A`() {
        assertEquals(0x58E5, Crc16Ccitt.compute(byteArrayOf(0x41)))
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*Crc16CcittTest"`
Expected: FAIL (Crc16Ccitt não existe).

- [ ] **Step 3: Implementar**

```kotlin
package com.opentonex.controller.protocol

object Crc16Ccitt {
    fun compute(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*Crc16CcittTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/Crc16Ccitt.kt app/src/test/java/com/opentonex/controller/protocol/Crc16CcittTest.kt
git commit -m "feat(protocol): CRC-CCITT (XModem)"
```

---

## Task 3: HDLC — encode (framing + byte-stuffing + CRC)

Regra de stuffing assíncrono: bytes `0x7E` e `0x7D` no payload/CRC viram `0x7D` seguido do byte XOR `0x20`. A moldura final é `0x7E [stuffed(payload + crcLE)] 0x7E`. O CRC é calculado sobre o payload cru (antes do stuffing), 2 bytes little-endian.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt`
- Test: `app/src/test/java/com/opentonex/controller/protocol/HdlcCodecTest.kt`

- [ ] **Step 1: Escrever o teste que falha (encode)**

```kotlin
package com.opentonex.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HdlcCodecTest {
    @Test fun `encode wraps with flags and appends crc little-endian`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val crc = Crc16Ccitt.compute(payload)
        val expected = byteArrayOf(
            0x7E, 0x01, 0x02, 0x03,
            (crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(),
            0x7E
        )
        assertArrayEquals(expected, HdlcCodec.encode(payload))
    }

    @Test fun `encode stuffs flag bytes in payload`() {
        val payload = byteArrayOf(0x7E, 0x7D)
        val out = HdlcCodec.encode(payload)
        assertArrayEquals(byteArrayOf(0x7E), byteArrayOf(out.first()))
        assertArrayEquals(byteArrayOf(0x7E), byteArrayOf(out.last()))
        // 0x7E -> 0x7D 0x5E ; 0x7D -> 0x7D 0x5D
        assertArrayEquals(
            byteArrayOf(0x7D, 0x5E, 0x7D, 0x5D),
            out.copyOfRange(1, 5)
        )
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*HdlcCodecTest"`
Expected: FAIL (HdlcCodec não existe).

- [ ] **Step 3: Implementar encode (e esqueleto da classe)**

```kotlin
package com.opentonex.controller.protocol

object HdlcCodec {
    private const val FLAG = 0x7E
    private const val ESC = 0x7D
    private const val XOR = 0x20

    fun encode(payload: ByteArray): ByteArray {
        val crc = Crc16Ccitt.compute(payload)
        val body = payload + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        val out = ArrayList<Byte>(body.size + 4)
        out.add(FLAG.toByte())
        for (b in body) {
            val v = b.toInt() and 0xFF
            if (v == FLAG || v == ESC) {
                out.add(ESC.toByte())
                out.add((v xor XOR).toByte())
            } else {
                out.add(b)
            }
        }
        out.add(FLAG.toByte())
        return out.toByteArray()
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*HdlcCodecTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt app/src/test/java/com/opentonex/controller/protocol/HdlcCodecTest.kt
git commit -m "feat(protocol): HDLC encode com byte-stuffing e CRC"
```

---

## Task 4: HDLC — decode (deframing + unstuff + valida CRC)

`decode` recebe um stream de bytes (pode conter ruído antes do primeiro flag), extrai o primeiro frame completo entre flags, desfaz o stuffing, separa os 2 bytes de CRC e valida. Retorna `HdlcFrame.Valid(payload)`, `HdlcFrame.CrcError` ou `HdlcFrame.Incomplete`.

**Files:**
- Modify: `app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt`
- Modify: `app/src/test/java/com/opentonex/controller/protocol/HdlcCodecTest.kt`

- [ ] **Step 1: Adicionar testes que falham (decode)**

```kotlin
    @Test fun `decode round-trips an encoded payload`() {
        val payload = byteArrayOf(0x10, 0x7E, 0x7D, 0x20)
        val encoded = HdlcCodec.encode(payload)
        val result = HdlcCodec.decode(encoded)
        org.junit.Assert.assertTrue(result is HdlcFrame.Valid)
        assertArrayEquals(payload, (result as HdlcFrame.Valid).payload)
    }

    @Test fun `decode reports crc error when crc bytes are corrupted`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = HdlcCodec.encode(payload).copyOf()
        encoded[encoded.size - 2] = (encoded[encoded.size - 2] + 1).toByte()
        org.junit.Assert.assertTrue(HdlcCodec.decode(encoded) is HdlcFrame.CrcError)
    }

    @Test fun `decode returns incomplete when no closing flag`() {
        val partial = byteArrayOf(0x7E, 0x01, 0x02)
        org.junit.Assert.assertTrue(HdlcCodec.decode(partial) is HdlcFrame.Incomplete)
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*HdlcCodecTest"`
Expected: FAIL (HdlcFrame / decode não existem).

- [ ] **Step 3: Implementar `HdlcFrame` e `decode`**

Adicionar no topo do arquivo (fora do `object`):

```kotlin
sealed interface HdlcFrame {
    data class Valid(val payload: ByteArray) : HdlcFrame
    data object CrcError : HdlcFrame
    data object Incomplete : HdlcFrame
}
```

Adicionar dentro de `object HdlcCodec`:

```kotlin
    fun decode(stream: ByteArray): HdlcFrame {
        val start = stream.indexOfFirst { (it.toInt() and 0xFF) == FLAG }
        if (start < 0) return HdlcFrame.Incomplete
        var end = -1
        for (i in (start + 1) until stream.size) {
            if ((stream[i].toInt() and 0xFF) == FLAG) { end = i; break }
        }
        if (end < 0) return HdlcFrame.Incomplete

        val unstuffed = ArrayList<Byte>(end - start)
        var i = start + 1
        while (i < end) {
            val v = stream[i].toInt() and 0xFF
            if (v == ESC) {
                i++
                if (i >= end) return HdlcFrame.Incomplete
                unstuffed.add(((stream[i].toInt() and 0xFF) xor XOR).toByte())
            } else {
                unstuffed.add(stream[i])
            }
            i++
        }
        if (unstuffed.size < 2) return HdlcFrame.CrcError
        val bytes = unstuffed.toByteArray()
        val payload = bytes.copyOfRange(0, bytes.size - 2)
        val gotCrc = (bytes[bytes.size - 2].toInt() and 0xFF) or
            ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
        return if (gotCrc == Crc16Ccitt.compute(payload)) HdlcFrame.Valid(payload)
        else HdlcFrame.CrcError
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*HdlcCodecTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt app/src/test/java/com/opentonex/controller/protocol/HdlcCodecTest.kt
git commit -m "feat(protocol): HDLC decode com unstuff e validacao de CRC"
```

---

## Task 5: Tipos primitivos tagueados

Codifica/decodifica os tipos da camada de objetos: número 2 bytes LE (tag `0x81`), float IEEE-754 (tag `0x88`) e byte cru (`0x00`–`0x7D`).

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/protocol/TaggedValue.kt`
- Test: `app/src/test/java/com/opentonex/controller/protocol/TaggedValueTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TaggedValueTest {
    @Test fun `encode 2-byte number is tag then little-endian`() {
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x06, 0x00),
            TaggedValue.encodeU16(0x0006, tag = 0x81)
        )
    }

    @Test fun `decode 2-byte number reads little-endian`() {
        val r = TaggedValue.decodeU16(byteArrayOf(0x81.toByte(), 0x06, 0x00), offset = 0)
        assertEquals(0x0006, r.value)
        assertEquals(3, r.nextOffset)
    }

    @Test fun `encode then decode float round-trips`() {
        val encoded = TaggedValue.encodeFloat(0.75f)
        assertEquals(0x88, encoded[0].toInt() and 0xFF)
        val r = TaggedValue.decodeFloat(encoded, offset = 0)
        assertEquals(0.75f, r.value, 0.0f)
        assertEquals(5, r.nextOffset)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TaggedValueTest"`
Expected: FAIL (TaggedValue não existe).

- [ ] **Step 3: Implementar**

```kotlin
package com.opentonex.controller.protocol

object TaggedValue {
    data class IntResult(val value: Int, val nextOffset: Int)
    data class FloatResult(val value: Float, val nextOffset: Int)

    fun encodeU16(value: Int, tag: Int): ByteArray =
        byteArrayOf(tag.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    fun decodeU16(data: ByteArray, offset: Int): IntResult {
        val lo = data[offset + 1].toInt() and 0xFF
        val hi = data[offset + 2].toInt() and 0xFF
        return IntResult(lo or (hi shl 8), offset + 3)
    }

    fun encodeFloat(value: Float): ByteArray {
        val bits = java.lang.Float.floatToIntBits(value)
        return byteArrayOf(
            0x88.toByte(),
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte()
        )
    }

    fun decodeFloat(data: ByteArray, offset: Int): FloatResult {
        val bits = (data[offset + 1].toInt() and 0xFF) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            ((data[offset + 3].toInt() and 0xFF) shl 16) or
            ((data[offset + 4].toInt() and 0xFF) shl 24)
        return FloatResult(java.lang.Float.intBitsToFloat(bits), offset + 5)
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TaggedValueTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/TaggedValue.kt app/src/test/java/com/opentonex/controller/protocol/TaggedValueTest.kt
git commit -m "feat(protocol): tipos primitivos tagueados (u16 LE, float)"
```

---

## Task 6: Modelo de domínio

Tipos imutáveis do estado do pedal. `rawState` preserva bytes ainda não decifrados para o ciclo ler→modificar→escrever nunca corromper o estado.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/domain/PedalState.kt`

- [ ] **Step 1: Criar os tipos (exercitados nas Tasks 8–10)**

```kotlin
package com.opentonex.controller.domain

enum class Slot { A, B, C }

data class Rgb(val r: Int, val g: Int, val b: Int)

enum class ParamType { FLOAT, INT, BYTE }

data class Parameter(
    val id: String,
    val label: String,
    val type: ParamType,
    val value: Float,
    val min: Float,
    val max: Float
)

data class PresetSlot(
    val index: Int,
    val name: String,
    val color: Rgb,
    val parameters: Map<String, Parameter> = emptyMap()
)

data class PedalState(
    val activeSlot: Slot,
    val inputTrim: Float,
    val a4Reference: Int,
    val tempo: Int,
    val slots: List<PresetSlot>,
    /** Bytes do estado completo recebidos do pedal, preservados para regravacao fiel. */
    val rawState: ByteArray = ByteArray(0)
) {
    fun withActiveSlot(slot: Slot): PedalState = copy(activeSlot = slot)
}

data class FirmwareInfo(val version: String)
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/domain/PedalState.kt
git commit -m "feat(domain): tipos imutaveis do estado do pedal"
```

---

## Task 7: Mensagens Hello e RequestState

Constrói os bytes de saída (payload, antes do HDLC) para `Hello` e `RequestState` (`0x81 0x06 0x03` conforme protocolo) e parseia a `FirmwareInfo` da resposta de Hello. O layout exato da resposta depende do device; o parser de firmware é construído sobre uma fixture sintética aqui e revalidado com captura real na Fase 2.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt`
- Test: `app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TonexMessagesTest {
    @Test fun `requestState payload starts with documented header`() {
        val payload = TonexMessages.requestStatePayload()
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x06, 0x03), payload.copyOfRange(0, 3))
    }

    @Test fun `hello payload is non-empty`() {
        org.junit.Assert.assertTrue(TonexMessages.helloPayload().isNotEmpty())
    }

    @Test fun `parse firmware reads ascii version from response`() {
        val resp = byteArrayOf(0x81.toByte(), 0x0A, 0x00) +
            "1.2.3".toByteArray(Charsets.US_ASCII)
        assertEquals("1.2.3", TonexMessages.parseFirmware(resp).version)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TonexMessagesTest"`
Expected: FAIL (TonexMessages não existe).

- [ ] **Step 3: Implementar**

```kotlin
package com.opentonex.controller.protocol

import com.opentonex.controller.domain.FirmwareInfo

object TonexMessages {
    /** Header documentado do request de estado: 0x81 0x06 0x03. */
    fun requestStatePayload(): ByteArray = byteArrayOf(0x81.toByte(), 0x06, 0x03)

    /** Mensagem inicial de handshake. Bytes refinados contra captura real na Fase 2. */
    fun helloPayload(): ByteArray = byteArrayOf(0xB9.toByte(), 0x03, 0x81.toByte(), 0x03, 0x00)

    /** Extrai a versao ASCII imprimivel da resposta de Hello. */
    fun parseFirmware(response: ByteArray): FirmwareInfo {
        val version = response
            .filter { it.toInt() in 0x20..0x7E }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
        return FirmwareInfo(version = version.ifEmpty { "desconhecida" })
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TonexMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt
git commit -m "feat(protocol): mensagens Hello e RequestState + parse de firmware"
```

---

## Task 8: StateUpdate — ler→modificar→escrever preservando raw

A regra de ouro: para mudar o slot ativo, parte-se do `rawState` recebido, altera-se apenas o byte do slot ativo (0=A,1=B,2=C) e regrava-se o resto intacto. Esta task implementa `buildSlotChangePayload`, que prova a preservação dos bytes desconhecidos. O offset exato do byte de slot ativo é uma constante refinada com captura real na Fase 2; aqui validamos a mecânica de preservação com um offset injetável.

**Files:**
- Modify: `app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt`
- Modify: `app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt`

- [ ] **Step 1: Adicionar teste que falha**

```kotlin
    @Test fun `slot change preserves all bytes except active slot byte`() {
        val raw = byteArrayOf(0x10, 0x20, 0x00 /*slot byte @2*/, 0x30, 0x40)
        val out = TonexMessages.buildSlotChangePayload(
            rawState = raw, activeSlotOffset = 2, newSlotValue = 2
        )
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x02, 0x30, 0x40), out)
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TonexMessagesTest"`
Expected: FAIL (buildSlotChangePayload não existe).

- [ ] **Step 3: Implementar (adicionar ao object TonexMessages)**

```kotlin
    /**
     * Regrava o estado completo mudando somente o byte do slot ativo.
     * Preserva todos os demais bytes (campos ainda nao decifrados).
     */
    fun buildSlotChangePayload(rawState: ByteArray, activeSlotOffset: Int, newSlotValue: Int): ByteArray {
        require(activeSlotOffset in rawState.indices) { "offset de slot fora do estado" }
        val copy = rawState.copyOf()
        copy[activeSlotOffset] = newSlotValue.toByte()
        return copy
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*TonexMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt
git commit -m "feat(protocol): StateUpdate de troca de slot preservando bytes raw"
```

---

## Task 9: PedalConnection (interface) + FakePedalConnection

Interface comum às conexões e um pedal simulado em memória que responde a Hello/RequestState e aplica troca de slot, permitindo rodar o app inteiro sem hardware.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/connection/PedalConnection.kt`
- Create: `app/src/main/java/com/opentonex/controller/connection/FakePedalConnection.kt`
- Test: `app/src/test/java/com/opentonex/controller/connection/FakePedalConnectionTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.connection

import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePedalConnectionTest {
    @Test fun `hello returns a firmware version`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        assertEquals("SIM-1.0.0", conn.sendHello().version)
    }

    @Test fun `request state returns three slots and default active A`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        val state = conn.requestState()
        assertEquals(3, state.slots.size)
        assertEquals(Slot.A, state.activeSlot)
    }

    @Test fun `writing a slot change updates active slot on next read`() = runTest {
        val conn = FakePedalConnection()
        conn.connect()
        val state = conn.requestState()
        conn.writeState(state.withActiveSlot(Slot.B))
        assertEquals(Slot.B, conn.requestState().activeSlot)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*FakePedalConnectionTest"`
Expected: FAIL (tipos não existem).

- [ ] **Step 3: Implementar a interface**

```kotlin
package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState

interface PedalConnection {
    suspend fun connect()
    suspend fun sendHello(): FirmwareInfo
    suspend fun requestState(): PedalState
    suspend fun writeState(state: PedalState)
    suspend fun disconnect()
}
```

- [ ] **Step 4: Implementar o FakePedal**

```kotlin
package com.opentonex.controller.connection

import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

class FakePedalConnection : PedalConnection {
    private var state = PedalState(
        activeSlot = Slot.A,
        inputTrim = 0.0f,
        a4Reference = 440,
        tempo = 120,
        slots = listOf(
            PresetSlot(0, "Preset A", Rgb(255, 0, 0)),
            PresetSlot(1, "Preset B", Rgb(0, 255, 0)),
            PresetSlot(2, "Preset C", Rgb(0, 0, 255))
        )
    )

    override suspend fun connect() { /* no-op no simulador */ }
    override suspend fun sendHello(): FirmwareInfo = FirmwareInfo("SIM-1.0.0")
    override suspend fun requestState(): PedalState = state
    override suspend fun writeState(state: PedalState) { this.state = state }
    override suspend fun disconnect() { /* no-op */ }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*FakePedalConnectionTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/connection/
git add app/src/test/java/com/opentonex/controller/connection/FakePedalConnectionTest.kt
git commit -m "feat(connection): interface PedalConnection + FakePedal em memoria"
```

---

## Task 10: PedalRepository com StateFlow

Orquestra a conexão e expõe o estado para a futura UI via `StateFlow`. Encapsula o ciclo ler→modificar→escrever (ex.: `selectSlot`).

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/repository/PedalRepository.kt`
- Test: `app/src/test/java/com/opentonex/controller/repository/PedalRepositoryTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.repository

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalRepositoryTest {
    @Test fun `connect emits Connected with state`() = runTest {
        val repo = PedalRepository(FakePedalConnection())
        repo.connect()
        val s = repo.state.value
        assertTrue(s is ConnectionState.Connected)
        assertEquals(Slot.A, (s as ConnectionState.Connected).pedal.activeSlot)
    }

    @Test fun `selectSlot updates active slot in emitted state`() = runTest {
        val repo = PedalRepository(FakePedalConnection())
        repo.connect()
        repo.selectSlot(Slot.C)
        val s = repo.state.value as ConnectionState.Connected
        assertEquals(Slot.C, s.pedal.activeSlot)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew :app:testDebugUnitTest --tests "*PedalRepositoryTest"`
Expected: FAIL (tipos não existem).

- [ ] **Step 3: Implementar**

```kotlin
package com.opentonex.controller.repository

import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.FirmwareInfo
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connected(val firmware: FirmwareInfo, val pedal: PedalState) : ConnectionState
}

class PedalRepository(private val connection: PedalConnection) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    suspend fun connect() {
        connection.connect()
        val fw = connection.sendHello()
        val pedal = connection.requestState()
        _state.value = ConnectionState.Connected(fw, pedal)
    }

    suspend fun selectSlot(slot: Slot) {
        val current = _state.value as? ConnectionState.Connected ?: return
        val updated = current.pedal.withActiveSlot(slot)
        connection.writeState(updated)
        _state.value = current.copy(pedal = connection.requestState())
    }

    suspend fun disconnect() {
        connection.disconnect()
        _state.value = ConnectionState.Disconnected
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew :app:testDebugUnitTest --tests "*PedalRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos os testes passam.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/repository/PedalRepository.kt
git add app/src/test/java/com/opentonex/controller/repository/PedalRepositoryTest.kt
git commit -m "feat(repository): PedalRepository com StateFlow e selecao de slot"
```

---

## Critério de conclusão da Fase 1

- `./gradlew :app:testDebugUnitTest` passa com todos os testes verdes.
- Núcleo de protocolo (CRC, HDLC encode/decode, tipos tagueados, mensagens) coberto por testes.
- `FakePedalConnection` permite exercitar o app sem hardware.
- `PedalRepository` expõe `StateFlow` pronto para a UI da Fase 3.

## Próximas fases (planos futuros)

- **Fase 2 — USB real:** `UsbPedalConnection` (UsbManager, permissão, bulk transfer CDC ACM), detecção do device `0x1963:0x00D1`, e **captura de fixtures reais** (Hello, StateResponse) para refinar offsets do parser de estado e a constante `activeSlotOffset`.
- **Fase 3 — UI Compose:** telas Connect/Home/Editor/Settings, tema escuro estilo ToneX Control, layout responsivo phone (abas) + tablet (duas colunas).

> Nota: a nota da Turbine foi removida do teste do repositório porque a verificação via `state.value` é suficiente e evita dependência desnecessária; a dependência `turbine` permanece no catálogo para uso em testes de fluxo nas fases seguintes.
