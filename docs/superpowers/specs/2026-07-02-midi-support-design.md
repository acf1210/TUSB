# Suporte MIDI (V1.0.2) — Design

**Data:** 2026-07-02
**Status:** Aprovado pelo usuário (escopo completo, mapa fixo + MIDI Learn, cliente BLE + USB MIDI)

## Objetivo

Permitir que footswitches MIDI — M-Vave Chocolate (BLE) e controladores USB MIDI comuns
(Morningstar, Hotone, etc.) — comandem o app TUSB, que repassa as ações ao ToneX One pela
conexão USB serial existente. O celular atua como *host* MIDI (cliente BLE central + host
USB MIDI); modo peripheral (anunciar o celular como dispositivo MIDI) fica fora do escopo
desta versão.

## Decisões de escopo

| Decisão | Escolha |
|---|---|
| Ações controláveis | Escopo completo: presets, slots, bypass, toggles de efeito e knobs de amp |
| Mapeamento | Mapa padrão de fábrica + MIDI Learn por ação, persistido localmente |
| Bluetooth | Somente cliente/central BLE MIDI (conectar a footswitches) |
| Transportes | BLE MIDI + USB MIDI via `android.media.midi` (API nativa, sem dependências novas) |

## Arquitetura

Novo pacote `com.opentonex.controller.midi` com unidades isoladas:

```
Footswitch (BLE/USB)
  → MidiInputManager   (MidiManager do Android; descoberta + conexão + bytes crus)
  → MidiParser         (bytes → mensagens tipadas; running status; fragmentação BLE)
  → MidiCommandDispatcher (mensagem + MidiMapping → ação; modo Learn)
  → PedalViewModel     (métodos existentes: selectSlot, loadPresetToActiveSlot,
                        toggleBypass, toggleCabSimBypass, toggleEffectEnabled, updateAmpKnob)
  → PedalRepository → USB serial → ToneX One
```

### 1. `MidiParser` (puro Kotlin, testável em JVM)

- Entrada: `ByteArray` cru vindo de `MidiReceiver.onSend`.
- Saída: lista de `MidiMessage` tipadas: `ProgramChange(channel, program)` e
  `ControlChange(channel, controller, value)`.
- Suporta *running status* e mensagens divididas entre pacotes (estado interno por porta).
- Ignora silenciosamente: SysEx, realtime (0xF8+), Note On/Off e demais mensagens.
- Canal MIDI é ignorado no dispatch (OMNI) — footswitches de fábrica usam canais variados.

### 2. `MidiAction` (domínio)

Enum das ações mapeáveis:

- `SELECT_SLOT_A`, `SELECT_SLOT_B`, `SELECT_SLOT_C`
- `NEXT_PRESET`, `PREV_PRESET`
- `TOGGLE_BYPASS`, `TOGGLE_CAB`
- `TOGGLE_GATE`, `TOGGLE_COMP`, `TOGGLE_EQ`, `TOGGLE_MOD`, `TOGGLE_DELAY`, `TOGGLE_REVERB`
- `AMP_BASS`, `AMP_MID`, `AMP_TREBLE`, `AMP_GAIN`, `AMP_VOLUME` (contínuos)

Program Change não é uma ação mapeável: PC *n* sempre carrega o preset *n* (0–19) no slot
ativo. Isso casa com o padrão de fábrica do M-Vave Chocolate (PC 0–3).

### 3. `MidiMapping` + `MidiMappingStore`

- `MidiMapping`: mapa imutável `CC número → MidiAction`.
- `MidiMappingStore`: persiste em `SharedPreferences` como JSON; expõe `StateFlow<MidiMapping>`.
- JSON corrompido ou ausente → mapa padrão.

**Mapa padrão:**

| MIDI | Ação |
|---|---|
| PC 0–19 | Carregar preset 1–20 no slot ativo (fixo, não remapeável) |
| CC 20 / 21 / 22 | Slot A / B / C |
| CC 23 / 24 | Próximo / anterior preset |
| CC 25 | Bypass geral |
| CC 26 | Bypass Cab (IR) |
| CC 27–32 | Toggle Gate, Comp, EQ, Mod, Delay, Reverb |
| CC 102–106 | Knobs Bass, Mid, Treble, Gain, Volume (0–127 → 0..1) |

Semântica de valor: ações de toggle/seleção disparam somente com `value >= 64`
(borda de subida; footswitches momentâneos mandam 127 no press e 0 no release).
Ações contínuas (knobs) usam `value / 127f` em qualquer valor.

### 4. `MidiInputManager` (única camada que toca APIs Android)

- Envolve `android.media.midi.MidiManager` (API 23+; minSdk do app é 24 — OK).
- **USB MIDI:** lista `midiManager.devices` e observa plug/unplug via
  `registerDeviceCallback`; dispositivos USB MIDI aparecem automaticamente
  (requer hub OTG, pois a porta única é usada pelo pedal).
- **BLE MIDI:** scan BLE filtrado pelo service UUID MIDI
  (`03B80E5A-EDE8-4B33-A751-6CE34EC4C700`), com timeout de 15 s;
  conexão via `midiManager.openBluetoothDevice`.
- Abre a *output port* do dispositivo e conecta um `MidiReceiver` que entrega bytes ao parser.
- Expõe `StateFlow`: lista de dispositivos descobertos, dispositivo conectado, estado
  (Idle / Scanning / Connecting / Connected / Error).
- Reconexão: manual (botão na tela MIDI). Desconexão inesperada → estado `Idle` + aviso.

### 5. `MidiCommandDispatcher`

- Recebe `MidiMessage` do parser + `MidiMapping` corrente.
- **Modo normal:** resolve a ação e chama o método correspondente do `PedalViewModel`.
  Callbacks MIDI chegam em thread do framework; o dispatcher posta no main dispatcher.
  As ações caem no gate `busy` existente do ViewModel (rajadas não corrompem operações).
- **Modo Learn:** quando armado para uma ação, o próximo CC recebido é gravado no
  `MidiMappingStore` para aquela ação (removendo o CC de qualquer ação anterior) e o modo
  desarma. PC recebido em modo Learn é ignorado (PC não é remapeável).
- `NEXT_PRESET`/`PREV_PRESET`: calcula a partir do preset ativo do estado conectado,
  com wrap-around 0↔19.

### 6. UI — tela "MIDI"

Nova rota acessível pelo Menu:

- **Dispositivos:** botão "Buscar dispositivos BLE" (pede permissão em runtime),
  lista de dispositivos BLE encontrados + USB MIDI plugados, conectar/desconectar,
  indicador do dispositivo conectado.
- **Mapeamento:** tabela ação → CC atual, botão "Learn" por linha (aguarda próximo CC,
  com indicação visual e cancelamento), botão "Restaurar padrão".
- Textos em PT-BR/EN/ES seguindo o padrão `localText` existente.
- Indicador de última mensagem MIDI recebida (debug rápido de mapeamento).

### 7. Manifest e permissões

- `BLUETOOTH_SCAN` com `neverForLocation` + `BLUETOOTH_CONNECT` (Android 12+).
- `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion 30`) e `ACCESS_FINE_LOCATION`
  (`maxSdkVersion 30`) para scan BLE em Android 6–11.
- `<uses-feature android:name="android.software.midi" android:required="false" />` e
  `<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />`
  — o app continua instalável e funcional sem MIDI/BLE.
- Permissões pedidas em runtime somente ao entrar no fluxo de scan BLE.

## Tratamento de erros

- Desconexão BLE inesperada → estado visível na tela MIDI; reconexão manual.
- Mensagem MIDI malformada → descartada sem crash (parser tolerante).
- Mapping JSON inválido → volta ao padrão silenciosamente.
- Ação MIDI com pedal desconectado → no-op (mesmo comportamento dos botões da UI).
- Permissão Bluetooth negada → mensagem explicativa na tela MIDI; USB MIDI segue funcionando.

## Testes

Unit tests JVM (sem device), seguindo o padrão dos testes existentes (JUnit + coroutines-test):

- `MidiParserTest`: PC/CC básicos, running status, fragmentação entre pacotes,
  mensagens ignoradas (SysEx, realtime, Note), lixo no meio do stream.
- `MidiMappingStoreTest`: mapa padrão, round-trip JSON, learn substitui CC antigo,
  JSON corrompido → padrão.
- `MidiCommandDispatcherTest`: dispatch de cada categoria de ação, threshold ≥64,
  knob contínuo, wrap-around de next/prev preset, modo Learn captura e desarma.

`MidiInputManager` e a tela MIDI são validados manualmente com M-Vave Chocolate e um
controlador USB MIDI.

## Release V1.0.2

- `versionName 1.0.2`, `versionCode 11`.
- README (3 idiomas): seção de destaques V1.0.2 + tabela do mapa padrão.
- `docs/USER_GUIDE.md`: instruções de pareamento do M-Vave Chocolate e uso do MIDI Learn.
- Release com APK segue a automação VirusTotal existente (`docs/RELEASE_AUTOMATION.md`).

## Fora do escopo (YAGNI)

- Modo peripheral BLE (celular anunciando-se como dispositivo MIDI).
- Envio de MIDI (o app só recebe).
- Mapeamento por canal MIDI, velocity, Note On/Off.
- Múltiplos dispositivos MIDI simultâneos (um por vez).
