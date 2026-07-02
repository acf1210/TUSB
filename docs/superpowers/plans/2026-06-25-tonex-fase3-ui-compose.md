# ToneX Fase 3 â€” UI Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir a interface completa do app (telas Connect/Home/Editor/Settings, tema escuro, navegaÃ§Ã£o, responsividade phone/tablet) consumindo o `PedalRepository` jÃ¡ existente, substituindo o botÃ£o de debug temporÃ¡rio do `MainActivity`.

**Architecture:** `PedalViewModel` (AndroidX ViewModel) encapsula um `PedalRepository` e expÃµe `StateFlow<ConnectionState>` para a UI via `collectAsStateWithLifecycle`. NavegaÃ§Ã£o via `NavHost` (Compose Navigation): `connect` â†’ `home` (com navegaÃ§Ã£o interna entre Home/Editor/Settings, sÃ³ acessÃ­vel quando `Connected`). Layout responsivo decide entre navegaÃ§Ã£o inferior (phone) e `NavigationRail` lateral (tablet/tela larga) com base na largura da janela (`calculateWindowSizeClass`). Apenas a troca de slot (`selectSlot`) tem escrita real ponta-a-ponta hoje â€” a tela Editor exibe `inputTrim`/`a4Reference`/`tempo` como somente leitura, jÃ¡ que `UsbPedalConnection.writeState` ainda sÃ³ regrava o byte do slot ativo (campos adicionais de escrita ficam para uma fase futura de protocolo).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Compose Navigation (`androidx.navigation:navigation-compose`), `androidx.lifecycle:lifecycle-viewmodel-compose` + `lifecycle-runtime-compose`, `androidx.compose.material3:material3-window-size-class`, `androidx.compose.material:material-icons-extended`, JUnit4 + `kotlinx-coroutines-test` (para o ViewModel, testÃ¡vel em JVM puro via `FakePedalConnection`).

---

## Contexto importante

- `PedalRepository` (Fase 1) jÃ¡ expÃµe `StateFlow<ConnectionState>` e os mÃ©todos `connect()`/`selectSlot()`/`disconnect()` â€” esta fase sÃ³ consome essa API, nÃ£o a modifica.
- `UsbPedalConnection`/`UsbSerialTransport` (Fase 2) jÃ¡ sabem conectar ao pedal real via USB. `FakePedalConnection` (Fase 1) permite o modo demo sem hardware.
- **LimitaÃ§Ã£o real conhecida:** `TonexMessages.buildSetStatePayload` (usado por `UsbPedalConnection.writeState`) sÃ³ regrava o byte de slot ativo. Editar `inputTrim`/`a4Reference`/`tempo` na UI nÃ£o teria efeito no pedal real hoje â€” por isso a tela Editor Ã© somente leitura para esses campos nesta fase (decisÃ£o confirmada com o usuÃ¡rio).
- O `MainActivity.kt` atual tem um botÃ£o de debug temporÃ¡rio (`"Conectar pedal (debug)"`) da Fase 2 â€” esta fase o substitui completamente pela navegaÃ§Ã£o real do app.
- Paleta de cores: tema escuro inspirado em controladores de audio (fundo quase preto, superficies cinza-escuro, destaque quente em laranja/vermelho). Nao sao cores oficiais verificadas, apenas uma aproximacao visual consistente.

---

## Estrutura de arquivos (Fase 3)

```
app/src/main/java/com/opentonex/controller/ui/theme/Color.kt        cores do tema escuro
app/src/main/java/com/opentonex/controller/ui/theme/Theme.kt         ToneXTheme (Material3 darkColorScheme)
app/src/main/java/com/opentonex/controller/ui/PedalViewModel.kt      ViewModel sobre PedalRepository
app/src/main/java/com/opentonex/controller/ui/connect/ConnectScreen.kt
app/src/main/java/com/opentonex/controller/ui/home/HomeScreen.kt
app/src/main/java/com/opentonex/controller/ui/editor/EditorScreen.kt
app/src/main/java/com/opentonex/controller/ui/settings/SettingsScreen.kt
app/src/main/java/com/opentonex/controller/ui/ToneXApp.kt            NavHost + layout responsivo
app/src/main/java/com/opentonex/controller/MainActivity.kt           modificado: so chama ToneXApp()
app/src/test/java/com/opentonex/controller/ui/PedalViewModelTest.kt
```

Responsabilidades:
- `theme/` â€” sÃ³ cores/tipografia/tema Material3, sem lÃ³gica.
- `PedalViewModel` â€” ponte entre `PedalRepository` (corrotinas) e Compose (`StateFlow`), sem nenhuma lÃ³gica de protocolo.
- Cada `*Screen.kt` â€” um composable de tela, recebe estado e callbacks via parÃ¢metros (sem acessar o ViewModel diretamente, para serem testÃ¡veis/previsualizÃ¡veis isoladamente).
- `ToneXApp.kt` â€” Ãºnico lugar que conhece a Ã¡rvore de navegaÃ§Ã£o e decide o layout responsivo.

---

## Task 1: DependÃªncias de Compose (Navigation, ViewModel, WindowSizeClass, Icons)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Adicionar versÃµes e bibliotecas ao catÃ¡logo**

Em `gradle/libs.versions.toml`, na seÃ§Ã£o `[versions]`, adicione (mantendo as linhas existentes):

```toml
navigationCompose = "2.8.0"
lifecycleCompose = "2.8.4"
```

Na seÃ§Ã£o `[libraries]`, adicione (mantendo as linhas existentes):

```toml
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleCompose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycleCompose" }
compose-material3-window-size = { module = "androidx.compose.material3:material3-window-size-class" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
```

- [ ] **Step 2: Adicionar as dependÃªncias ao mÃ³dulo app**

Em `app/build.gradle.kts`, dentro do bloco `dependencies { ... }`, adicione (apÃ³s `implementation(libs.usb.serial.android)`):

```kotlin
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
```

- [ ] **Step 3: Sincronizar e compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (baixa as novas dependÃªncias do Google Maven).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: adiciona dependencias de Navigation, ViewModel Compose, WindowSizeClass e icones"
```

---

## Task 2: Tema escuro (Color + Theme)

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/theme/Color.kt`
- Create: `app/src/main/java/com/opentonex/controller/ui/theme/Theme.kt`

- [ ] **Step 1: Criar as cores**

```kotlin
package com.opentonex.controller.ui.theme

import androidx.compose.ui.graphics.Color

val ToneXBackground = Color(0xFF0E0E10)
val ToneXSurface = Color(0xFF1C1C1E)
val ToneXSurfaceVariant = Color(0xFF2A2A2D)
val ToneXOnSurface = Color(0xFFEDEDED)
val ToneXOnSurfaceMuted = Color(0xFF9A9A9E)
val ToneXAccent = Color(0xFFFF5A36)
val ToneXAccentVariant = Color(0xFFFF8A5C)
val ToneXError = Color(0xFFFF5252)
val ToneXSlotA = Color(0xFFE74C3C)
val ToneXSlotB = Color(0xFF2ECC71)
val ToneXSlotC = Color(0xFF3498DB)
```

- [ ] **Step 2: Criar o tema Material3**

```kotlin
package com.opentonex.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ToneXDarkColorScheme = darkColorScheme(
    primary = ToneXAccent,
    onPrimary = ToneXBackground,
    secondary = ToneXAccentVariant,
    background = ToneXBackground,
    onBackground = ToneXOnSurface,
    surface = ToneXSurface,
    onSurface = ToneXOnSurface,
    surfaceVariant = ToneXSurfaceVariant,
    onSurfaceVariant = ToneXOnSurfaceMuted,
    error = ToneXError
)

@Composable
fun ToneXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ToneXDarkColorScheme,
        content = content
    )
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/theme/
git commit -m "feat(ui): tema escuro Material3 inspirado no ToneX Control"
```

---

## Task 3: PedalViewModel (TDD)

`PedalViewModel` expÃµe o `StateFlow` do `PedalRepository` e dois modos de conexÃ£o: real (USB, via `PedalConnection` jÃ¡ aberto pelo chamador) e simulado (`FakePedalConnection`, criado internamente). Como `ViewModel` puro do AndroidX nÃ£o depende de `Context`/Android framework alÃ©m de `androidx.lifecycle:lifecycle-viewmodel` (jÃ¡ transitivo via `lifecycle-viewmodel-compose`), Ã© testÃ¡vel 100% em JVM.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/PedalViewModel.kt`
- Test: `app/src/test/java/com/opentonex/controller/ui/PedalViewModelTest.kt`

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.opentonex.controller.ui

import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalViewModelTest {
    @Test fun `connectWith emits Connected state from the given connection`() = runTest {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        val state = viewModel.state.value
        assertTrue(state is ConnectionState.Connected)
        assertEquals(Slot.A, (state as ConnectionState.Connected).pedal.activeSlot)
    }

    @Test fun `selectSlot updates active slot after connecting`() = runTest {
        val viewModel = PedalViewModel()
        viewModel.connectWith(FakePedalConnection())
        viewModel.selectSlot(Slot.B)
        val state = viewModel.state.value as ConnectionState.Connected
        assertEquals(Slot.B, state.pedal.activeSlot)
    }

    @Test fun `initial state is Disconnected`() {
        val viewModel = PedalViewModel()
        assertEquals(ConnectionState.Disconnected, viewModel.state.value)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*PedalViewModelTest"`
Expected: FAIL (`PedalViewModel` nÃ£o existe).

- [ ] **Step 3: Implementar**

```kotlin
package com.opentonex.controller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.repository.PedalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PedalViewModel : ViewModel() {
    private var repository: PedalRepository? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connectWith(connection: PedalConnection) {
        val repo = PedalRepository(connection)
        repository = repo
        viewModelScope.launch {
            try {
                _error.value = null
                repo.connect()
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao conectar ao pedal"
            }
        }
    }

    fun selectSlot(slot: Slot) {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                repo.selectSlot(slot)
                _state.value = repo.state.value
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao trocar de slot"
            }
        }
    }

    fun disconnect() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.disconnect()
            _state.value = repo.state.value
            repository = null
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*PedalViewModelTest"`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/PedalViewModel.kt
git add app/src/test/java/com/opentonex/controller/ui/PedalViewModelTest.kt
git commit -m "feat(ui): PedalViewModel sobre PedalRepository, testado com FakePedal"
```

---

## Task 4: ConnectScreen

Tela exibida quando `ConnectionState.Disconnected`. Dois botÃµes: conectar ao pedal real (USB) ou usar o pedal simulado. A obtenÃ§Ã£o do `UsbManager`/permissÃ£o fica no `MainActivity` (que tem `Context`); `ConnectScreen` sÃ³ recebe callbacks.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/connect/ConnectScreen.kt`

- [ ] **Step 1: Implementar a tela**

```kotlin
package com.opentonex.controller.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ConnectScreen(
    statusMessage: String,
    errorMessage: String?,
    onConnectReal: () -> Unit,
    onConnectFake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ToneX Controller",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(onClick = onConnectReal) {
            Text("Conectar pedal via USB-C")
        }
        OutlinedButton(
            onClick = onConnectFake,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Usar pedal simulado")
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/connect/ConnectScreen.kt
git commit -m "feat(ui): tela ConnectScreen (USB real ou pedal simulado)"
```

---

## Task 5: HomeScreen

Tela principal pÃ³s-conexÃ£o: firmware, seletor de slot A/B/C (escrita real via `onSelectSlot`), lista dos 3 presets com cor.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/home/HomeScreen.kt`

- [ ] **Step 1: Implementar a tela**

```kotlin
package com.opentonex.controller.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opentonex.controller.domain.PresetSlot
import com.opentonex.controller.domain.Rgb
import com.opentonex.controller.domain.Slot

@Composable
fun HomeScreen(
    firmwareVersion: String,
    activeSlot: Slot,
    presets: List<PresetSlot>,
    onSelectSlot: (Slot) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Firmware: $firmwareVersion",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slot.entries.forEach { slot ->
                FilterChip(
                    selected = slot == activeSlot,
                    onClick = { onSelectSlot(slot) },
                    label = { Text(slot.name) }
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.index }) { preset ->
                PresetRow(
                    preset = preset,
                    isActive = preset.index == activeSlot.ordinal,
                    onClick = { onSelectSlot(Slot.entries[preset.index]) }
                )
            }
        }
    }
}

@Composable
private fun PresetRow(preset: PresetSlot, isActive: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ColorSwatch(preset.color)
            Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
            if (isActive) {
                Text(
                    text = "ativo",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Rgb) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(color.r, color.g, color.b))
    )
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/home/HomeScreen.kt
git commit -m "feat(ui): tela HomeScreen (firmware, slots A/B/C, lista de presets)"
```

---

## Task 6: EditorScreen (somente leitura)

Mostra `inputTrim`, `a4Reference` e `tempo` como informaÃ§Ã£o, com nota indicando que a ediÃ§Ã£o depende de trabalho futuro no protocolo. Estrutura em seÃ§Ãµes para facilitar adicionar parÃ¢metros editÃ¡veis depois.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/editor/EditorScreen.kt`

- [ ] **Step 1: Implementar a tela**

```kotlin
package com.opentonex.controller.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opentonex.controller.domain.PedalState

@Composable
fun EditorScreen(pedal: PedalState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Parametros globais (somente leitura)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Edicao de parametros sera habilitada quando o protocolo de escrita " +
                "desses campos for decifrado em uma fase futura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ReadOnlyField(label = "Input trim", value = "%.2f dB".format(pedal.inputTrim))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReadOnlyField(label = "Referencia A4", value = "${pedal.a4Reference} Hz")
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReadOnlyField(label = "Tempo", value = "${pedal.tempo} BPM")
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/editor/EditorScreen.kt
git commit -m "feat(ui): tela EditorScreen somente leitura (trim/A4/tempo)"
```

---

## Task 7: SettingsScreen

Reconectar, alternar modo (info do modo atual), sobre o app.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Implementar a tela**

```kotlin
package com.opentonex.controller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    firmwareVersion: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Configuracoes", style = MaterialTheme.typography.titleLarge)
        Text(text = "Firmware do pedal: $firmwareVersion")
        Text(
            text = "ToneX Controller - controle nao-oficial via USB-C para o " +
                "ToneX One (versao sem Bluetooth).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onDisconnect) {
            Text("Desconectar")
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opentonex/controller/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): tela SettingsScreen (desconectar, info de firmware)"
```

---

## Task 8: ToneXApp â€” navegaÃ§Ã£o e layout responsivo

Une as 4 telas: `connect` Ã© a tela inicial; ao conectar, mostra a Ã¡rvore Home/Editor/Settings â€” em **phone**, por abas inferiores (`NavigationBar`); em **tablet** (largura nÃ£o-compacta), em duas colunas (`NavigationRail` lateral + conteÃºdo). A decisÃ£o de largura usa `calculateWindowSizeClass`.

**Files:**
- Create: `app/src/main/java/com/opentonex/controller/ui/ToneXApp.kt`
- Modify: `app/src/main/java/com/opentonex/controller/MainActivity.kt`

- [ ] **Step 1: Implementar `ToneXApp.kt`**

```kotlin
package com.opentonex.controller.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.connect.ConnectScreen
import com.opentonex.controller.ui.editor.EditorScreen
import com.opentonex.controller.ui.home.HomeScreen
import com.opentonex.controller.ui.settings.SettingsScreen

private enum class TopLevelDestination(val route: String, val label: String) {
    HOME("home", "Presets"),
    EDITOR("editor", "Editor"),
    SETTINGS("settings", "Config")
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.ViewList
    TopLevelDestination.EDITOR -> Icons.Filled.Tune
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun ToneXApp(
    windowSizeClass: WindowSizeClass,
    onCreateRealConnection: () -> PedalConnection?,
    onCreateFakeConnection: () -> PedalConnection,
    viewModel: PedalViewModel = viewModel()
) {
    val connectionState by viewModel.state.collectAsStateWithLifecycle()
    val errorMessage by viewModel.error.collectAsStateWithLifecycle()

    when (val current = connectionState) {
        ConnectionState.Disconnected -> ConnectScreen(
            statusMessage = "Aguardando pedal via USB-C...",
            errorMessage = errorMessage,
            onConnectReal = {
                onCreateRealConnection()?.let { viewModel.connectWith(it) }
            },
            onConnectFake = { viewModel.connectWith(onCreateFakeConnection()) }
        )
        is ConnectionState.Connected -> ConnectedApp(
            firmwareVersion = current.firmware.version,
            pedal = current.pedal,
            isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
            onSelectSlot = viewModel::selectSlot,
            onDisconnect = viewModel::disconnect
        )
    }
}

@Composable
private fun ConnectedApp(
    firmwareVersion: String,
    pedal: PedalState,
    isTablet: Boolean,
    onSelectSlot: (Slot) -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val destinations = TopLevelDestination.entries

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                destinations.forEach { destination ->
                    RailOrBarItem(navController, destination, useRail = true)
                }
            }
            ConnectedNavHost(
                navController, firmwareVersion, pedal, onSelectSlot, onDisconnect,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        RailOrBarItem(navController, destination, useRail = false)
                    }
                }
            }
        ) { padding ->
            ConnectedNavHost(
                navController, firmwareVersion, pedal, onSelectSlot, onDisconnect,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun ConnectedNavHost(
    navController: NavHostController,
    firmwareVersion: String,
    pedal: PedalState,
    onSelectSlot: (Slot) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                firmwareVersion = firmwareVersion,
                activeSlot = pedal.activeSlot,
                presets = pedal.slots,
                onSelectSlot = onSelectSlot
            )
        }
        composable(TopLevelDestination.EDITOR.route) {
            EditorScreen(pedal = pedal)
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(firmwareVersion = firmwareVersion, onDisconnect = onDisconnect)
        }
    }
}

@Composable
private fun RailOrBarItem(
    navController: NavHostController,
    destination: TopLevelDestination,
    useRail: Boolean
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isSelected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    val icon: @Composable () -> Unit = {
        Icon(imageVector = destination.icon(), contentDescription = destination.label)
    }
    val label: @Composable () -> Unit = { androidx.compose.material3.Text(destination.label) }
    val onClick = {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (useRail) {
        NavigationRailItem(selected = isSelected, onClick = onClick, icon = icon, label = label)
    } else {
        NavigationBarItem(selected = isSelected, onClick = onClick, icon = icon, label = label)
    }
}
```

- [ ] **Step 2: Atualizar `MainActivity.kt`**

Substitua TODO o conteÃºdo do arquivo por:

```kotlin
package com.opentonex.controller

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.opentonex.controller.connection.FakePedalConnection
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.connection.UsbPedalConnection
import com.opentonex.controller.ui.ToneXApp
import com.opentonex.controller.ui.theme.ToneXTheme
import com.opentonex.controller.usb.UsbSerialTransport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ToneXTheme {
                ToneXApp(
                    windowSizeClass = windowSizeClass,
                    onCreateRealConnection = { createRealConnection() },
                    onCreateFakeConnection = { FakePedalConnection() }
                )
            }
        }
    }

    private fun createRealConnection(): PedalConnection? {
        val manager = getSystemService(UsbManager::class.java)
        val transport = UsbSerialTransport.connect(this, manager) ?: return null
        return UsbPedalConnection(transport)
    }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Rodar a suite completa de testes**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL â€” todos os testes de todas as fases (Fase 1 + Fase 2 + os 3 novos do `PedalViewModelTest`) continuam passando.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git add app/src/main/java/com/opentonex/controller/ui/ToneXApp.kt
git add app/src/main/java/com/opentonex/controller/MainActivity.kt
git commit -m "feat(ui): navegacao ToneXApp com layout responsivo phone/tablet"
```

---

## Task 9: VerificaÃ§Ã£o manual no dispositivo

Sem testes instrumentados de UI nesta fase (exigiriam emulador/dispositivo conectado e configuraÃ§Ã£o adicional de `androidTest`, fora do escopo combinado). A verificaÃ§Ã£o Ã© manual, com o pedal fÃ­sico e em modo simulado.

**Files:** nenhum (apenas execuÃ§Ã£o).

- [ ] **Step 1: Gerar o APK de debug**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL, gera `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Instalar em um dispositivo Android conectado (phone)**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 3: Roteiro de verificaÃ§Ã£o â€” modo simulado**

1. Abrir o app â†’ deve mostrar a tela Connect.
2. Tocar "Usar pedal simulado" â†’ deve navegar para Home, mostrando firmware `SIM-1.0.0`, slot ativo A, 3 presets coloridos.
3. Tocar slot B â†’ o chip B fica selecionado e o preset B aparece marcado "ativo".
4. Navegar para Editor (aba inferior) â†’ mostra Input trim/A4/Tempo somente leitura.
5. Navegar para Settings â†’ mostra firmware e botÃ£o Desconectar; tocar Desconectar volta para Connect.

- [ ] **Step 4: Roteiro de verificaÃ§Ã£o â€” pedal real**

1. Conectar o ToneX One V1 ao celular via cabo USB-C OTG.
2. Abrir o app, tocar "Conectar pedal via USB-C", conceder a permissÃ£o USB quando solicitado pelo Android.
3. Deve navegar para Home mostrando a versÃ£o de firmware real e o slot ativo real do pedal.
4. Tocar em outro slot na Home â†’ o pedal fÃ­sico deve trocar de preset (LED/display do pedal muda).
5. Reabrir Home (ou voltar) â†’ o slot ativo exibido deve refletir a troca persistida no pedal.

- [ ] **Step 5: Testar em tablet (ou emulador com tela grande)**

1. Repetir o passo 3 em um dispositivo com largura de tela >= medium (`WindowWidthSizeClass.Medium`/`Expanded`).
2. Confirmar que a navegaÃ§Ã£o aparece como `NavigationRail` lateral (duas colunas) em vez de abas inferiores.

- [ ] **Step 6: Registrar resultado**

Se todos os passos passarem, comente no PR/commit final mencionando "VerificaÃ§Ã£o manual Fase 3: OK (simulado + pedal real + tablet)". Se algo falhar, anote o passo exato e o comportamento observado antes de prosseguir.

---

## CritÃ©rio de conclusÃ£o da Fase 3

- `./gradlew.bat :app:testDebugUnitTest` passa com todos os testes (Fases 1+2+3) verdes.
- `./gradlew.bat :app:assembleDebug` gera um APK instalÃ¡vel.
- NavegaÃ§Ã£o completa Connect â†’ Home â†’ Editor â†’ Settings funcional em modo simulado.
- VerificaÃ§Ã£o manual com o pedal real confirma troca de slot ponta-a-ponta.
- Layout responsivo confirmado (abas no phone, rail no tablet/tela larga).

## Follow-ups para fases futuras (fora do escopo desta fase)

- Decifrar e implementar escrita real de `inputTrim`/`a4Reference`/`tempo` no protocolo (`TonexMessages`/`UsbPedalConnection`), depois tornar `EditorScreen` editÃ¡vel.
- Decifrar parÃ¢metros de amp/cab/FX por preset (`PresetSlot.parameters`) e expandir `EditorScreen` com os knobs correspondentes, conforme a visÃ£o original de "controle completo".
- Testes instrumentados de UI (Compose `androidTest`) quando houver um dispositivo/emulador dedicado para CI.
