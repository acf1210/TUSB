<p align="center">
  <img src="docs/assets/logo.png" width="120" alt="TUSB logo" />
</p>

<h1 align="center">TUSB — ToneX One USB Controller</h1>

<p align="center">
  <strong>Controle nativo Android, via USB, para o IK Multimedia ToneX One.</strong><br/>
  <strong>Native Android USB controller for the IK Multimedia ToneX One.</strong>
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-1.0.0%20GA-brightgreen" />
  <img alt="platform" src="https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-blue" />
  <img alt="status" src="https://img.shields.io/badge/status-unofficial%20%2F%20community-orange" />
</p>

<p align="center">
  🇧🇷 <a href="#-português">Português</a> &nbsp;|&nbsp; 🇺🇸 <a href="#-english">English</a>
</p>

---

<p align="center">
  <img src="docs/assets/screenshots/00-connect.png" width="180" alt="Tela de conexão" />
  <img src="docs/assets/screenshots/01-editor.png" width="180" alt="Editor" />
  <img src="docs/assets/screenshots/02-presets.png" width="180" alt="Presets" />
  <img src="docs/assets/screenshots/03-effect-detail.png" width="180" alt="Detalhe de efeito" />
  <img src="docs/assets/screenshots/04-menu.png" width="180" alt="Menu" />
</p>

---

## 🇧🇷 Português

### O que é

O **TUSB** é um app Android **não-oficial** que conecta diretamente ao pedal **IK Multimedia
ToneX One** por um cabo USB-C, sem precisar de Bluetooth, computador ou do app oficial. Ele
fala o protocolo serial nativo do pedal (o mesmo usado pelo app oficial) e oferece um editor
completo: troca de presets, cadeia de efeitos, knobs de amplificador e muito mais — em tempo
real, com o pedal físico e o app sincronizados nos dois sentidos.

### Por que existe

A IK Multimedia não oferece um app Android com controle **USB direto** do ToneX One (o app
oficial mobile depende de Bluetooth, e o suporte USB completo existe apenas no desktop). Este
projeto nasceu de uma engenharia reversa cuidadosa do protocolo USB do pedal — cruzada com
dois firmwares open-source de referência já publicados pela comunidade — para oferecer uma
alternativa Android gratuita, funcional e com paridade de recursos com o app oficial.

### Funcionalidades

- **Conexão USB direta** (CDC-ACM serial), sem Bluetooth, sem PC.
- **Handshake robusto**: reconecta automaticamente mesmo com a porta USB "fria".
- **Troca de preset e de slot** (A/B/C), com escrita fiel do estado completo do pedal.
- **Alternância de modo** A/B ⇄ Stomp.
- **Bypass geral** e **bypass do Cab Sim/IR**, com resposta imediata no pedal.
- **Knobs de amplificador em tempo real e bidirecionais**: gire o knob físico do pedal e veja
  o knob virtual se mover no app; ajuste o knob virtual e ouça a mudança no pedal na hora.
- **Cadeia de efeitos 100% operacional**: liga/desliga cada bloco (Noise Gate, Compressor, EQ,
  Modulação, Delay, Reverb, Cab) e ajusta os parâmetros reais de cada um (o app resolve
  automaticamente o modelo ativo — ex.: spring/room/plate no reverb, chorus/tremolo/phaser/
  flanger/rotary na modulação).
- **Captura de eventos** para diagnóstico (log de todos os comandos e respostas trocados com
  o pedal).

### Aviso legal

Este é um projeto de **interoperabilidade não-oficial**, criado pela comunidade e sem
qualquer vínculo, patrocínio ou endosso da IK Multimedia. "ToneX", "ToneX One" e marcas
relacionadas pertencem à IK Multimedia Production Srl. O código-fonte original deste
repositório é distribuído sob licença MIT (veja [LICENSE](LICENSE)); nenhuma marca de
terceiros é licenciada por este projeto.

### Requisitos

- Android 7.0 (API 24) ou superior.
- Cabo USB-C OTG (host) para conectar o celular ao ToneX One.
- Pedal IK Multimedia ToneX One.

### Instalação

Veja o guia completo em [docs/INSTALL.md](docs/INSTALL.md).

### Como usar

Veja o tutorial completo em [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

### Documentação técnica do protocolo

Este projeto documenta publicamente o protocolo USB do ToneX One (framing HDLC, CRC-16,
offsets de estado, comandos de parâmetro), validado byte a byte contra hardware real e
cruzado com dois firmwares open-source de referência:

- [docs/protocol-notes.md](docs/protocol-notes.md) — notas de protocolo, byte a byte.
- [docs/MASTER_TECHNICAL_BASELINE.md](docs/MASTER_TECHNICAL_BASELINE.md) — baseline técnico
  e status de cada funcionalidade.

### Créditos

- [Builty/TonexOneController](https://github.com/Builty/TonexOneController) — firmware
  ESP32 de referência (USB), fonte da tabela de parâmetros `tonex_params`.
- [vit3k/tonex_controller](https://github.com/vit3k/tonex_controller) — firmware ESP32 de
  referência (USB), validação cruzada de framing HDLC/CRC.

### Contribuindo / build local

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug   # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # roda a suite de testes
```

### Licença

[MIT](LICENSE) para o código-fonte original deste repositório.

---

## 🇺🇸 English

### What is this

**TUSB** is an **unofficial** Android app that connects directly to the **IK Multimedia
ToneX One** pedal over a USB-C cable — no Bluetooth, no computer, no official app required.
It speaks the pedal's native serial protocol (the same one the official app uses) and
provides a full editor: preset switching, effect chain, amp knobs, and more — in real time,
with the physical pedal and the app staying in sync in both directions.

### Why it exists

IK Multimedia does not offer an Android app with **direct USB control** of the ToneX One
(the official mobile app relies on Bluetooth; full USB support only exists on desktop). This
project grew out of careful reverse engineering of the pedal's USB protocol — cross-checked
against two community-published open-source reference firmwares — to provide a free,
fully-functional Android alternative with feature parity with the official app.

### Features

- **Direct USB connection** (CDC-ACM serial), no Bluetooth, no PC.
- **Robust handshake**: reconnects automatically even on a "cold" USB port.
- **Preset and slot switching** (A/B/C), with faithful full-state rewrites to the pedal.
- **Mode switching** A/B ⇄ Stomp.
- **Global bypass** and **Cab Sim/IR bypass**, with immediate pedal response.
- **Real-time, bidirectional amp knobs**: turn the physical knob on the pedal and watch the
  virtual knob move in the app; drag the virtual knob and hear the change on the pedal
  instantly.
- **Fully operational effect chain**: toggle each block (Noise Gate, Compressor, EQ,
  Modulation, Delay, Reverb, Cab) on/off and adjust each block's real parameters (the app
  automatically resolves the active model — e.g. spring/room/plate for reverb,
  chorus/tremolo/phaser/flanger/rotary for modulation).
- **Event capture** for diagnostics (logs every command and response exchanged with the
  pedal).

### Legal notice

This is an **unofficial interoperability project**, built by the community, with no
affiliation, sponsorship, or endorsement from IK Multimedia. "ToneX", "ToneX One" and
related marks belong to IK Multimedia Production Srl. The original source code in this
repository is distributed under the MIT license (see [LICENSE](LICENSE)); no third-party
trademark is licensed by this project.

### Requirements

- Android 7.0 (API 24) or higher.
- USB-C OTG (host) cable to connect the phone to the ToneX One.
- IK Multimedia ToneX One pedal.

### Installation

See the full guide at [docs/INSTALL.md](docs/INSTALL.md).

### How to use

See the full tutorial at [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

### Protocol technical documentation

This project publicly documents the ToneX One USB protocol (HDLC framing, CRC-16, state
offsets, parameter commands), validated byte-for-byte against real hardware and
cross-checked against two open-source reference firmwares:

- [docs/protocol-notes.md](docs/protocol-notes.md) — byte-level protocol notes.
- [docs/MASTER_TECHNICAL_BASELINE.md](docs/MASTER_TECHNICAL_BASELINE.md) — technical
  baseline and per-feature status.

### Credits

- [Builty/TonexOneController](https://github.com/Builty/TonexOneController) — reference
  ESP32 firmware (USB), source of the `tonex_params` parameter table.
- [vit3k/tonex_controller](https://github.com/vit3k/tonex_controller) — reference ESP32
  firmware (USB), cross-validation of HDLC/CRC framing.

### Contributing / building locally

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug   # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # runs the test suite
```

### License

[MIT](LICENSE) for the original source code in this repository.
