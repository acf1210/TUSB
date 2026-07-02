# Spec de Design — App Android de Controle do ToneX One V1

- **Data:** 2026-06-24
- **Autor:** Brainstorming colaborativo (superpowers)
- **Status:** Aprovado para planejamento

## 1. Visão geral

Aplicativo Android (phone + tablet) para controlar o pedal de guitarra **ToneX One V1**
diretamente via **USB-C**, preenchendo a lacuna deixada pelo app oficial.

O app oficial `com.ikmultimediaus.android.tonexcontrol` foi feito para o **ToneX One+**, que possui
Bluetooth (BLE). O **ToneX One V1 não tem Bluetooth** — apenas USB-C — e por isso não é compatível com
o app oficial. Este projeto entrega exatamente esse controle por USB.

### Objetivos
- Conectar ao pedal via USB Host (OTG) e ler/escrever seu estado.
- Trocar o slot/preset ativo (A/B/C) com baixa latência (uso ao vivo).
- Edição **completa** de parâmetros do preset (amp/tone model, cab/IR, FX) e parâmetros globais.
- UI espelhando o design do app oficial (tema escuro, foco em toque), responsiva para phone e tablet.

### Não-objetivos (V1)
- Gerenciamento de biblioteca de Tone Models / ToneNET / download de modelos.
- Captura de novos Tone Models.
- Conexão Bluetooth (não existe no hardware V1).
- Serviço em background dedicado (preparado na arquitetura, implementado em fase futura).

## 2. Base técnica (engenharia reversa)

Derivado dos projetos `Builty/TonexOneController`, `Pirate-MIDI/Polar` e `vit3k/tonex_controller`.

- **Enumeração USB:** o pedal é um **USB device** classe **CDC ACM** (serial-over-USB).
  `VID = 0x1963`, `PID = 0x00D1`. O controlador (celular) atua como **USB host**.
- **Android suporta USB Host / OTG** e consegue falar CDC ACM via `UsbManager` + bulk transfer.
- **Framing:** HDLC assíncrono.
  - Delimitador início/fim: `0x7E`.
  - Byte-stuffing aplicado a payload **e** CRC para escapar bytes de flag.
  - Estrutura: `0x7E [Payload] [CRC 2 bytes] 0x7E`.
  - CRC-CCITT calculado sobre todos os bytes entre as flags `0x7E`.
- **Mensagens (formato tagueado por objetos):**
  - Cabeçalho padrão: `0xB9 0x03` (lista de 3 elementos), `0x81 [2B type LE]`,
    `0x82 [2B size LE]`, `0x80 [2B desconhecido]`.
  - Tipos: byte cru `0x00–0x7D`; número 2 bytes `0x80/0x81/0x82 [2B LE]`;
    float IEEE-754 `0x88 [4B]`; coleções `0xB9/0xBA/0xBC [count] [elementos]`.
- **Fluxo de comunicação:**
  1. **Hello** → pedal responde com versão de firmware.
  2. **Request State** (`0x81 0x06 0x03`) → pedal responde com estado completo.
  3. Estado contém: input trim (float), modos de bypass, slots de preset, cores RGB por preset
     (atenção: `0xFF` às vezes escapa como `0x80 0xFF`), slot ativo (0=A, 1=B, 2=C),
     frequência de referência A4 (Hz) e tempo.
- **Regra de ouro para alteração:** **ler estado completo → modificar apenas o necessário →
  enviar estado completo de volta.** Campos ainda não decifrados devem ser preservados como bytes
  "raw" para nunca corromper o estado.

## 3. Arquitetura (Opção A — camadas isoladas)

Camadas independentes, cada uma com um propósito único e interface bem definida:

```
ui (Jetpack Compose)
  │  observa StateFlow / envia intents
repository (PedalRepository)
  │  orquestra ler→modificar→escrever; expõe StateFlow<ConnectionState>
domain (PedalState, PresetSlot, Parameter)  ← tipos imutáveis
protocol (HdlcCodec, TonexMessage)           ← testável 100% sem hardware
connection (PedalConnection interface)
  ├── UsbPedalConnection   (Android USB Host real)
  └── FakePedalConnection  (pedal simulado / mock do protocolo)
usb (wrapper UsbManager / bulk transfer CDC ACM)
```

- O **FakePedalConnection** implementa a mesma interface da conexão real, permitindo rodar e testar
  o app inteiro sem hardware, e testes automatizados do protocolo byte-a-byte.
- Fronteiras desenhadas para acomodar, numa fase futura, um `ForegroundService` que mantenha a
  conexão USB viva durante uso ao vivo (Opção C), sem reescrever as camadas.

## 4. Telas e UX

Tema escuro, foco em toque, espelhando o app oficial (ToneX Control mobile).

1. **Conexão (Connect)** — exibida quando não há pedal. Ilustração do ToneX One, status
   ("Aguardando pedal via USB-C…"), disparo da permissão USB do Android ao detectar o device,
   botão "Usar pedal simulado" (demo). Ao conectar: Hello → mostra firmware → vai para Home.
2. **Home / Presets** — preset ativo em destaque, indicador de slot **A/B/C** com toque grande para
   alternar (baixa latência), grid/lista de presets com a **cor RGB** de cada um, acesso rápido a
   bypass e tempo.
3. **Editor** — edição completa do preset ativo em seções colapsáveis na ordem da cadeia de sinal:
   **Amp / Tone Model**, **Cab / IR / VIR**, **FX**, **Global** (input trim, A4, tempo, bypass).
   Knobs realistas com arrastar vertical; cada alteração executa o ciclo ler→modificar→escrever.
4. **Configurações** — tema, info de firmware, reconectar, alternar real/simulado, sobre/licenças.

### Responsividade
- **Phone:** navegação por abas inferiores (Presets / Editor / Settings); knobs empilhados.
- **Tablet:** layout em duas colunas (lista de presets à esquerda + editor à direita).

## 5. Modelo de dados (domínio)

- `PedalState`: `activeSlot: Slot`, `inputTrim: Float`, `a4Reference: Int`, `tempo`,
  `bypassModes`, `slots: List<PresetSlot>` (A/B/C), `rawUnknown: ByteArray` (preservado).
- `PresetSlot`: `index`, `name`, `color: Rgb`, `parameters: Map<ParamId, Parameter>`, `raw: ByteArray`.
- `Parameter`: `id`, `label`, `type`, `value`, `range` — mapa tipado para acomodar controle completo
  mesmo onde o protocolo ainda está sendo mapeado.

## 6. Protocolo (componentes)

- `HdlcCodec`: framing `0x7E`, byte-stuffing, CRC-CCITT. Testado com vetores de bytes reais.
- `TonexMessage`: `Hello`, `RequestState`, `StateResponse`, `StateUpdate` — com (de)serialização
  dos tipos tagueados (número 2B LE, float, coleções).
- `PedalConnection` (interface): `connect()`, `sendHello()`, `requestState()`, `writeState()`,
  `observeIncoming()`. Implementações `UsbPedalConnection` e `FakePedalConnection`.

## 7. Tratamento de erros

- **Desconexão USB** no meio → estado `Disconnected`, retorna à tela de Conexão sem crashar.
- **CRC inválido / frame corrompido** → descarta frame, loga, re-solicita estado.
- **Timeout de resposta** → retry com backoff; após N falhas, avisa o usuário.
- **Permissão USB negada** → tela explicativa com botão de tentar novamente.

## 8. Estratégia de testes (TDD)

- **Unit (crítico):** `HdlcCodec` e (de)serialização de mensagens com vetores reais — cobertura alta.
- **Unit:** `PedalRepository` com `FakePedalConnection` (valida ciclo ler→modificar→escrever).
- **UI:** testes de Compose para os estados Connect / Home / Editor.
- **Verificação manual (pedal real):** roteiro — Hello, trocar slot A/B/C, alterar um parâmetro,
  reconectar após desplugar.

## 9. Plataforma e stack

- **Linguagem/UI:** Kotlin + Jetpack Compose.
- **Min SDK:** a definir no plano (USB Host disponível desde API 12; alvo moderno recomendado).
- **Dependências de USB:** API Android `UsbManager` nativa (avaliar `usb-serial-for-android` apenas
  se necessário para CDC ACM).
- **Sem dependência de rede** na V1.

## 10. Riscos e mitigações

- **Partes do protocolo não documentadas** (controle completo de parâmetros) → preservar bytes raw e
  mapear parâmetros incrementalmente com captura do pedal real; nunca sobrescrever campos desconhecidos.
- **Variações de cabo/OTG** → testar com hardware real desde cedo.
- **Latência ao vivo** → caminho de troca de slot otimizado e isolado do editor.

## Referências
- https://github.com/Builty/TonexOneController
- https://github.com/Builty/TonexOneController/blob/main/HardwarePlatforms.md
- https://builty.github.io/TonexOneController/
- https://github.com/Pirate-MIDI/Polar
- https://github.com/vit3k/tonex_controller (protocolo)
