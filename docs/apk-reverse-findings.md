# ToneX Control APK — Engenharia Reversa (2026-06-30)

APK: `com.ikmultimediaus.android.tonexcontrol` (100 MB base + 25 MB arm64)
Método: jadx (decompilação Java) + análise de strings em `libjuce_jni.so`

---

## Arquitetura do App

O app é uma casca Android mínima sobre uma engine C++/JUCE:

```
Java (Android)
  └── BLEManager.java          ← gerencia GATT BLE, delega tudo ao C++
  └── ToneXControl_MainActivity ← apenas inicia JUCE
Native (C++ / JUCE)
  └── libjuce_jni.so (25 MB)   ← TODA a lógica de protocolo e UI
        ├── IK::HWLink::BLE::IK202::BleService  (ToneX One V1)
        ├── IK::HWLink::BLE::IK222::BleService  (ToneX Pedal)
        └── IK::HWLink::BLE::IK129::BleService  (modelo antigo)
```

**Conclusão crítica:** O protocolo de comunicação (mensagens, framing, parsing) está 100% em C++. É o **mesmo namespace `ik::shared::ik2023::msg`** usado no protocolo USB. BT e USB compartilham os mesmos tipos de mensagem.

---

## Modelos de Dispositivo

| Código | Produto              | Notas                          |
|--------|----------------------|--------------------------------|
| IK202  | ToneX One V1         | Suporte BLE + USB CDC          |
| IK222  | ToneX Pedal / One+   | `IK222Send`, `GlobalSettingsV1` |
| IK129  | Modelo mais antigo   | `HWLink_IK129_Helpers.hpp`     |
| IK199  | Desconhecido         | referenciado no código         |

---

## BLE — Arquitetura de Comunicação

### Handshake de Conexão (BLEManager.java)
1. `startScan()` → varre dispositivos BLE, filtra por nome
2. `connectToDevice(mac)` → `connectGatt()`
3. `onConnectionStateChange(CONNECTED)` → nativa `onDeviceConnected()`
4. `discoverServices()` → primeiro solicita MTU de **517 bytes**
5. `onMtuChanged()` → `discoverServices()` real
6. `onServicesDiscovered()` → nativa retorna `[writeUUID, notifyUUID]`
7. Habilita notificação CCCD `00002902-0000-1000-8000-00805f9b34fb`
8. `writeCharacteristic.setWriteType(1)` = **Write Without Response**

### UUIDs BLE (a confirmar com sniff real)
Os UUIDs de write/notify são retornados por `getWriteAndNotifyCharacteristics()` do C++.
Encontrados próximos ao contexto BLE no binário:
- `0acedb4d-1627-b32a-5a77-e2b32570df47`
- `bb9bbbe4-b265-8f6b-14c9-aeef17f4fd07`
- `958e78bb-bbc3-eada-7e42-bf8cc868c57e`
- `d52de844-1e11-c21e-abd5-fddb0b7726e4`
- `be73562c-a82c-4df7-a045-fabe6535cece`

**Para confirmar:** usar HCI snoop log do Android enquanto conectado ao pedal BT.

---

## Protocolo de Mensagens (ik2023::msg)

Mesmo protocolo do USB. Tipos de mensagem identificados no binário:

| Tipo                  | Descrição                                    |
|-----------------------|----------------------------------------------|
| `ActivePresetIndex`   | Índice do preset ativo (push do dispositivo) |
| `ActiveImageIndex`    | Índice de imagem (FOTA)                      |
| `GlobalSettings`      | Configurações globais (IK202/IK222)          |
| `DEVICE_STATE`        | Estado do dispositivo                        |
| `LedColor`            | Controle de LEDs (PURPLE, BLUE)              |

---

## Campos de Estado (GlobalSettings)

Variáveis de estado identificadas:
- `bt_ble_state` — estado da conexão BLE
- `bypass_mode` — modo bypass ativo
- `preset_dirty` — preset modificado não salvo
- `preset_index` — índice do preset ativo
- `tuner_active` — afinador ativo
- `usb_direct_mode_on` — modo direto USB
- `serial_number` — número de série do dispositivo

---

## Parâmetros por Slot (A e B)

Cada slot tem os mesmos parâmetros, prefixados com A ou B:

### Amplificador / Tone Model
- `ParameterXModelEnable`, `ParameterXModelGain`, `ParameterXModelMix`, `ParameterXModelVolume`

### Compressor
- `ParameterXCompEnable`, `ParameterXCompAttack`, `ParameterXCompThreshold`, `ParameterXCompMakeUp`, `ParameterXCompPost`

### Noise Gate
- `ParameterXNoiseGateEnable`, `ParameterXNoiseGateThreshold`, `ParameterXNoiseGateDepth`, `ParameterXNoiseGateRelease`, `ParameterXNoiseGatePost`

### EQ
- `ParameterXEqBass`, `ParameterXEqBassFreq`, `ParameterXEqMid`, `ParameterXEqMidFreq`, `ParameterXEqMidQ`, `ParameterXEqTreble`, `ParameterXEqTrebleFreq`, `ParameterXEqPost`

### Cab (Cabinet)
- `ParameterXCabEnable`, `ParameterXCabType`
- VIR: `ParameterXVIRCabModel`, `ParameterXVIRCabMicBlend`, `ParameterXVIRCabResonance`
- Mic 1/2: posição X/Z e modelo

### Power Amp EQ
- `ParameterXPwrAmpEqPresence`, `ParameterXPwrAmpEqDepth`

### Modulação
- Chorus, Flanger, Phaser, Tremolo, Rotary — cada um com Rate, Depth, Level, Sync, etc.
- `ParameterXModModel`, `ParameterXModEnable`, `ParameterXModPost`

### Delay
- Digital: Feedback, Mix, Mode, Sync, Time, TS
- Tape: Feedback, Mix, Mode, Sync, Time, TS
- `ParameterXDelayModel`, `ParameterXDelayEnable`, `ParameterXDelayPost`

### Reverb
- Modelos: Room, Plate, Spring 1-4
- Cada um: Mix, Time, PreDelay, Color
- `ParameterXReverbModel`, `ParameterXReverbEnable`, `ParameterXReverbPost`

---

## UI — Telas Identificadas

O app usa JUCE C++ renderizado em SurfaceView. Telas (XMLs do Paks):

| Arquivo XML               | Tela                          |
|--------------------------|-------------------------------|
| `Main.xml`               | Tela principal                |
| `Play.xml`               | Modo performance/play         |
| `HardwarePresetList.xml` | Lista de presets do hardware  |
| `HardwarePresetListOnePlus.xml` | Idem para ToneX Pedal  |
| `PresetList.xml`         | Lista de presets locais       |
| `Library.xml`            | Biblioteca de tone models     |
| `ToneModelList.xml`      | Lista de tone models          |
| `Gear.xml`               | Configuração de gear/efeitos  |
| `GearSlot.xml`           | Slot de efeito individual     |
| `EditAmp.xml`            | Editor de amplificador        |
| `EditComp.xml`           | Editor de compressor          |
| `EditDelay.xml`          | Editor de delay               |
| `EditReverb.xml`         | Editor de reverb              |
| `EditMod.xml`            | Editor de modulação           |
| `EditGate.xml`           | Editor de noise gate          |
| `EditGlobalEQ.xml`       | Editor de EQ global           |
| `EditCab.xml`            | Editor de cabinet             |
| `Tuner.xml`              | Afinador                      |
| `Settings.xml`           | Configurações                 |
| `SettingsGeneral.xml`    | Configurações gerais          |
| `SettingsMidi.xml`       | Configurações MIDI            |
| `SettingsVolume.xml`     | Configurações de volume       |
| `FirmwareUpdate.xml`     | Atualização de firmware       |
| `WelcomePage.xml`        | Tela de boas-vindas           |
| `DeviceList.xml`         | Lista de dispositivos BLE     |
| `ConnectingPanel.xml`    | Painel de conexão             |
| `Collections.xml`        | ToneNet collections           |
| `ColorPicker.xml`        | Seletor de cor                |
| `Metronome.xml`          | Metrônomo                     |

### Elementos de UI Identificados (strings)
```
PlayButton, ModeButton1, ModelButton, ActionButton,
BpmLabel, HearDemosButton, RestoreMessage,
Subtitle, TextOffColor, BackgroundColor,
CollectionSkin, ToneNetLogo
```

### Cores / Esquema Visual (do binário)
- Fundo geral: preto / muito escuro (`BackgroundColor`)
- LEDs do pedal: PURPLE (modo stomp?), BLUE (modo A/B?)
- Fontes: `Fonts/Roboto-Bold.ttf`

---

## Assets de Imagem

O arquivo `TONEX Control.pak` (103 MB) contém todos os recursos visuais JUCE.
Categorias de imagem encontradas:
- `Images/Collections/` — logos de coleções de tone models
- `Images/CustomAmps/` — imagens de amplificadores personalizados (5150, AC30, etc.)
- `Images/Gear/` — ícones de efeitos (Amp.png, Mod.png, Delay.png)

---

## Paks / Recursos Extraíveis

O arquivo `assets/Paks/TONEX Control.pak` é um arquivo JUCE binário.
Para extrair seus XML e imagens seria necessário um parser de .pak JUCE.

Assets disponíveis no APK (não no .pak):
- `assets/nativemenu/Menu/menu/Home.json` — menu principal (Account, Manual, Settings, Info)
- `assets/Presets/FactoryPresets/*.txp` — presets de fábrica (51 Solos, 80s Clean, etc.)
- `assets/drawable/splash_background.xml`

---

## Próximos Passos para Estética

1. **HCI Snoop Log**: habilitar em Opções do Desenvolvedor → "Ativar log HCI Bluetooth"
   Conectar pedal BT → copiar `/sdcard/btsnoop_hci.log` → abrir no Wireshark
   Isso revelará os UUIDs reais e o protocolo completo sobre BLE.

2. **Descompilar .pak**: usar `ue4pak` ou escrever parser JUCE para extrair XMLs e PNGs.

3. **Screenshots reais**: usar `adb exec-out screencap -p` ou Vysor para capturar UI JUCE.
