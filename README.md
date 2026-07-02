<p align="center">
  <img src="docs/assets/logo.png" width="120" alt="TUSB logo" />
</p>

<h1 align="center">TUSB - ToneX One USB Controller</h1>

<p align="center">
  <strong>Controle Android nativo via USB-C para ToneX One.</strong><br/>
  <strong>Native Android USB-C controller for ToneX One.</strong><br/>
  <strong>Controlador Android nativo por USB-C para ToneX One.</strong>
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-1.0.2-brightgreen" />
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-blue" />
  <img alt="status" src="https://img.shields.io/badge/status-community-orange" />
</p>

<p align="center">
  🇧🇷 <a href="#-português-brasil">Português Brasil</a> &nbsp;|&nbsp;
  🇺🇸 <a href="#-english-us">English US</a> &nbsp;|&nbsp;
  🇪🇸 <a href="#-español">Español</a>
</p>

---

## 📦 APK V1.0.2 / VirusTotal

### 🇧🇷 Português Brasil

**Baixe o APK oficial da release:**  
👉 [TUSB-v1.0.2.apk](https://github.com/acf1210/TUSB/releases/download/v1.0.2/TUSB-v1.0.2.apk)

**Verificação VirusTotal:** `0 malicioso`, `0 suspeito`, `64 não detectado`, `6 não suportado`  
**SHA-256:** `48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649`  
**Relatório:** [VirusTotal](https://www.virustotal.com/gui/file/48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649) · [VIRUSTOTAL.md](https://github.com/acf1210/TUSB/releases/download/v1.0.2/VIRUSTOTAL.md)

**Regra para novas versões:** toda nova release com APK deve atualizar o VirusTotal automaticamente via [release automation](docs/RELEASE_AUTOMATION.md).

### 🇺🇸 English US

**Download the official release APK:**  
👉 [TUSB-v1.0.2.apk](https://github.com/acf1210/TUSB/releases/download/v1.0.2/TUSB-v1.0.2.apk)

**VirusTotal scan:** `0 malicious`, `0 suspicious`, `64 undetected`, `6 unsupported`  
**SHA-256:** `48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649`  
**Report:** [VirusTotal](https://www.virustotal.com/gui/file/48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649) · [VIRUSTOTAL.md](https://github.com/acf1210/TUSB/releases/download/v1.0.2/VIRUSTOTAL.md)

**Rule for new versions:** every new release with an APK must update VirusTotal automatically via [release automation](docs/RELEASE_AUTOMATION.md).

### 🇪🇸 Español

**Descarga el APK oficial de la release:**  
👉 [TUSB-v1.0.2.apk](https://github.com/acf1210/TUSB/releases/download/v1.0.2/TUSB-v1.0.2.apk)

**Verificación VirusTotal:** `0 malicioso`, `0 sospechoso`, `64 no detectado`, `6 no soportado`  
**SHA-256:** `48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649`  
**Informe:** [VirusTotal](https://www.virustotal.com/gui/file/48A1269AFDD97E4E7711CA281AAC8ED46C160C17BD819C771C0176DC3DEEB649) · [VIRUSTOTAL.md](https://github.com/acf1210/TUSB/releases/download/v1.0.2/VIRUSTOTAL.md)

**Regla para nuevas versiones:** cada nueva release con APK debe actualizar VirusTotal automáticamente mediante [release automation](docs/RELEASE_AUTOMATION.md).

---

<p align="center">
  <img src="docs/assets/screenshots/00-connect.png" width="180" alt="Connection screen" />
  <img src="docs/assets/screenshots/01-editor.png" width="180" alt="Editor" />
  <img src="docs/assets/screenshots/02-presets.png" width="180" alt="Presets" />
  <img src="docs/assets/screenshots/03-effect-detail.png" width="180" alt="Effect detail" />
  <img src="docs/assets/screenshots/04-menu.png" width="180" alt="Menu" />
</p>

---

## 🇧🇷 Português Brasil

### Destaques da V1.0.2

- Suporte a footswitches MIDI por Bluetooth LE (M-Vave Chocolate e similares) e USB MIDI
  (via hub OTG).
- Mapa padrão pronto para uso: Program Change 0–19 carrega os presets 1–20 e CCs controlam
  slots, bypass, efeitos da cadeia e knobs do amp.
- MIDI Learn para remapear qualquer ação direto no app (Menu → MIDI).

### Destaques da V1.0.1

- Idioma automático para Português Brasil, Inglês EUA e Espanhol.
- Afinador completo por microfone, com leitura de cents e atalhos para Standard, Drop D,
  meio tom abaixo, D Standard, Open G e DADGAD.
- Nota de conexão inicial destacada no app: coloque o ToneX One em modo Stomp e pressione o
  pedal três vezes para conectar.
- Nota em destaque: versão para iOS em desenvolvimento. Em breve.
- Documentação de instalação e uso em três idiomas.

### O que é

O **TUSB** é um app Android comunitário que conecta diretamente ao **ToneX One** por cabo
USB-C OTG. Ele usa comunicação serial USB nativa para editar presets, alternar slots,
controlar efeitos e manter o app sincronizado com o pedal em tempo real.

### Recursos

- Conexão USB-C direta, sem Bluetooth e sem computador.
- Troca de preset e slot A/B/C.
- Alternância entre modo A/B e modo Stomp.
- Bypass geral e bypass IR/Cab.
- Knobs de amplificador em tempo real.
- Cadeia de efeitos editável: Gate, Compressor, EQ, Mod, Delay, Reverb e Cab.
- Metrônomo local.
- Afinador por microfone com afinações comuns.
- Controle por footswitch MIDI (BLE e USB) com MIDI Learn.
- Captura JSONL para diagnóstico.

### Instalação e uso

- Instalação: [docs/INSTALL.md](docs/INSTALL.md)
- Guia de uso: [docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- Resultado VirusTotal: [docs/VIRUSTOTAL.md](docs/VIRUSTOTAL.md)

### Build local

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
./gradlew test
```

O APK debug fica em `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🇺🇸 English US

### V1.0.2 highlights

- MIDI footswitch support over Bluetooth LE (M-Vave Chocolate and similar) and USB MIDI
  (through an OTG hub).
- Ready-to-use default map: Program Change 0–19 loads presets 1–20 and CCs control slots,
  bypass, effect chain blocks, and amp knobs.
- MIDI Learn to remap any action right in the app (Menu → MIDI).

### V1.0.1 highlights

- Automatic language support for Brazilian Portuguese, US English, and Spanish.
- Full microphone tuner with cents reading and shortcuts for Standard, Drop D, half-step
  down, D Standard, Open G, and DADGAD.
- Highlighted initial connection note in the app: put ToneX One in Stomp mode and press the
  pedal three times to connect.
- Highlighted note: iOS version in development. Coming soon.
- Install and user guides in three languages.

### What it is

**TUSB** is a community Android app that connects directly to **ToneX One** over a USB-C OTG
cable. It uses native USB serial communication to edit presets, switch slots, control
effects, and keep the app synchronized with the pedal in real time.

### Features

- Direct USB-C connection, no Bluetooth and no computer required.
- Preset and A/B/C slot switching.
- A/B and Stomp mode switching.
- Global bypass and IR/Cab bypass.
- Real-time amp knobs.
- Editable effect chain: Gate, Compressor, EQ, Mod, Delay, Reverb, and Cab.
- Local metronome.
- Microphone tuner with common tunings.
- MIDI footswitch control (BLE and USB) with MIDI Learn.
- JSONL capture for diagnostics.

### Install and use

- Installation: [docs/INSTALL.md](docs/INSTALL.md)
- User guide: [docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- VirusTotal result: [docs/VIRUSTOTAL.md](docs/VIRUSTOTAL.md)

### Local build

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
./gradlew test
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🇪🇸 Español

### Novedades de la V1.0.2

- Soporte de footswitches MIDI por Bluetooth LE (M-Vave Chocolate y similares) y USB MIDI
  (mediante hub OTG).
- Mapa por defecto listo para usar: Program Change 0–19 carga los presets 1–20 y los CCs
  controlan slots, bypass, bloques de efectos y knobs del amplificador.
- MIDI Learn para reasignar cualquier acción directamente en la app (Menú → MIDI).

### Novedades de la V1.0.1

- Idioma automático para Portugués Brasil, Inglés EUA y Español.
- Afinador completo por micrófono, con lectura de cents y accesos rápidos para Standard,
  Drop D, medio tono abajo, D Standard, Open G y DADGAD.
- Nota destacada de conexión inicial en la app: pon el ToneX One en modo Stomp y presiona el
  pedal tres veces para conectar.
- Nota destacada: versión para iOS en desarrollo. Próximamente.
- Guías de instalación y uso en tres idiomas.

### Qué es

**TUSB** es una app Android comunitaria que se conecta directamente al **ToneX One** con un
cable USB-C OTG. Usa comunicación serial USB nativa para editar presets, cambiar slots,
controlar efectos y mantener la app sincronizada con el pedal en tiempo real.

### Funciones

- Conexión USB-C directa, sin Bluetooth y sin ordenador.
- Cambio de preset y slots A/B/C.
- Cambio entre modo A/B y modo Stomp.
- Bypass general y bypass IR/Cab.
- Knobs de amplificador en tiempo real.
- Cadena de efectos editable: Gate, Compressor, EQ, Mod, Delay, Reverb y Cab.
- Metrónomo local.
- Afinador por micrófono con afinaciones comunes.
- Control por footswitch MIDI (BLE y USB) con MIDI Learn.
- Captura JSONL para diagnóstico.

### Instalación y uso

- Instalación: [docs/INSTALL.md](docs/INSTALL.md)
- Guía de uso: [docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- Resultado VirusTotal: [docs/VIRUSTOTAL.md](docs/VIRUSTOTAL.md)

### Build local

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
./gradlew test
```

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`.

---

## Créditos e fontes / Credits and sources / Créditos y fuentes

### 🇧🇷 Português Brasil

Este projeto foi construído de forma independente e validado de maneira cruzada contra
projetos open-source da comunidade. Agradecemos às seguintes fontes:

- [`vit3k/tonex_controller`](https://github.com/vit3k/tonex_controller) — firmware ESP32
  open-source usado como referência para validar o framing HDLC/CRC e os offsets de
  slot/preset.
- [`Builty/TonexOneController`](https://github.com/Builty/TonexOneController) — firmware
  ESP32 open-source; segunda validação independente do protocolo (Hello/wake, RequestState,
  baud 115200) e tabela de ranges de parâmetros (`tonex_params.c`).
- [Android MIDI API](https://developer.android.com/reference/android/media/midi/package-summary)
  (`android.media.midi`) e a especificação **BLE MIDI** da MIDI Association para o suporte a
  footswitches na V1.0.2.

### 🇺🇸 English US

This project was built independently and cross-validated against community open-source
projects. We thank the following sources:

- [`vit3k/tonex_controller`](https://github.com/vit3k/tonex_controller) — open-source ESP32
  firmware used as a reference to validate HDLC/CRC framing and slot/preset offsets.
- [`Builty/TonexOneController`](https://github.com/Builty/TonexOneController) — open-source
  ESP32 firmware; a second independent validation of the protocol (Hello/wake, RequestState,
  baud 115200) and parameter-range table (`tonex_params.c`).
- [Android MIDI API](https://developer.android.com/reference/android/media/midi/package-summary)
  (`android.media.midi`) and the MIDI Association **BLE MIDI** specification for the V1.0.2
  footswitch support.

### 🇪🇸 Español

Este proyecto se construyó de forma independiente y se validó de forma cruzada contra
proyectos open-source de la comunidad. Agradecemos a las siguientes fuentes:

- [`vit3k/tonex_controller`](https://github.com/vit3k/tonex_controller) — firmware ESP32
  open-source usado como referencia para validar el framing HDLC/CRC y los offsets de
  slot/preset.
- [`Builty/TonexOneController`](https://github.com/Builty/TonexOneController) — firmware
  ESP32 open-source; segunda validación independiente del protocolo (Hello/wake,
  RequestState, baud 115200) y tabla de rangos de parámetros (`tonex_params.c`).
- [Android MIDI API](https://developer.android.com/reference/android/media/midi/package-summary)
  (`android.media.midi`) y la especificación **BLE MIDI** de la MIDI Association para el
  soporte de footswitches en la V1.0.2.

---

## Legal

This is an independent community interoperability project. It is not affiliated with,
endorsed by, or sponsored by any trademark owner. The original source code in this
repository is distributed under the [MIT license](LICENSE).
