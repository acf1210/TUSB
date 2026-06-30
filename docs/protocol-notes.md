# ToneX One V1 — notas de protocolo (captura real, 2026-06-25)

Evidência capturada do pedal físico via diagnóstico on-device
(`app/.../diag/PedalDiagnostic.kt`), arquivo bruto: `tonex_diag.txt` (216 KB).
Hardware: ToneX One, VID 0x1963 / PID 0x00D1, CDC ACM @ 115200, framing HDLC (0x7E).

## Tipos de mensagem observados (header `B9 03 81 [tipo u16 LE] 03`)

| Tipo     | Significado observado                                              |
|----------|-------------------------------------------------------------------|
| `0x0306` | Estado global (StateResponse). Resposta ao Hello E ao RequestState. |
| `0x0304` | **Detalhe do preset ativo** (~2214 B). Pedal faz PUSH disto quando o footswitch troca o preset. Contém o NOME do preset em ASCII. |

## Bug 1 — firmware aparece como lixo ("LBLG@//''''o&C") — CAUSA RAIZ CONFIRMADA

- O Hello (`B9 03 81 03 00`) retorna um frame **tipo 0x0306** — o mesmo tipo do
  StateResponse. Ou seja, **não existe um frame separado de "versão de firmware"**;
  o Hello devolve um dump de estado binário.
- `TonexMessages.parseFirmware()` filtra todos os bytes imprimíveis (0x20–0x7E) do
  frame binário inteiro e concatena → produz o lixo. Os bytes `4C 42 4C 47` = "LBLG"
  mais `@ / " o & C` espalhados são exatamente o que aparece na UI.
- Início do payload do Hello (0x0306):
  `B9 03 81 06 03 80 A0 02 B9 01 B9 0E 82 4C 42 4C 47 B9 03 00 04 00 88 ...`
- "LBLG" parece um código de modelo/build, não uma versão de usuário. O campo de
  versão real (se existir) ainda NÃO foi identificado com certeza nestes bytes.

### Correção recomendada (Bug 1)
- `sendHello()` deveria filtrar por tipo igual a `requestState()` (usa `roundTrip`
  sem filtro hoje — pode pegar notificação assíncrona).
- `parseFirmware()` NÃO deve raspar imprimíveis de um frame binário. Sem um campo de
  versão confiável, retornar algo honesto (ex.: modelo "ToneX One" ou "—") em vez de
  lixo. Identificar o campo de versão real exige a spec da IK ou sniff do app oficial.

## Bug 2 — comando de troca de preset — RESOLVIDO de verdade (captura isolada, 2026-06-25)

**CORREÇÃO IMPORTANTE sobre a primeira hipótese (abaixo, mantida como histórico):** a
sequência `ARM`/`bridge`/`COMMIT`/`settle` com `PRESET ∈ {0x0C,0x08,0x07,...}` **NÃO é o
comando de troca de slot**. Uma captura mais completa (`tonex_full_session.pcap`, do
**zero da conexão**) mostrou que essa sequência é, na verdade, um **sweep sequencial de
TODOS os presetIds (0x00 a 0x13 = 0..19)**, repetido para fase ARM e depois para fase
COMMIT — ou seja, o app **sincronizando a biblioteca inteira** ao conectar (ARM = busca
metadados ~2.2KB; COMMIT = busca o modelo completo do preset, ~26KB em vários fragmentos
de 4096B). Enviar fragmentos dessa sequência ao pedal real deixou-o instável ("modo
stomp", parou de responder) — **não usar mais esse caminho para troca de slot.**
`TonexMessages.selectPreset*` / `selectPresetPayload*` ficam disponíveis apenas para uma
eventual função de "buscar dados de um preset da biblioteca", não para trocar o slot ativo.

### O comando REAL de troca de slot (confirmado, `tonex_isolated_switch.pcap`)

Captura isolada: conectar, esperar o sweep de sincronização terminar, trocar entre os
slots A→B→C→A **sem mais nenhuma ação**. Os comandos host→device correspondentes (175B,
endpoint `0x07`) são o **StateResponse completo (tipo `0x0306`) reenviado pelo host**,
com o byte do slot ativo mutado e um **header de comando diferente do header de resposta**:

```
RESPOSTA (device→host):  B9 03 81 06 03  80 A0 02  [corpo do estado...]
COMANDO  (host→device):  B9 03 81 06 03  82 A0 00 80 0B 03  [MESMO corpo, so o byte do slot ativo muda]
                          └── tipo 0x0306 ──┘  └── sufixo de 6B (era 3B na resposta) ──┘
```

- O **corpo** (campos de trim, cores, presetIds, slot ativo, A4, tempo) é **idêntico** ao
  de um StateResponse normal — é literalmente "pegue o último estado conhecido, mude o
  byte do slot ativo, troque o sufixo do header de `80 A0 02` (resposta) para
  `82 A0 00 80 0B 03` (comando), envie".
- Implementado em `TonexMessages.buildSetStatePayload` / `buildSlotChangePayload` e usado
  por `UsbPedalConnection.writeState()` → `PedalRepository.selectSlot()`.
- Validado byte a byte contra 3 comandos reais capturados (slot A/B/C) em
  `TonexMessagesTest.buildSetStatePayload reproduces official app slot-switch capture byte for byte`.
- **Ainda não testado no hardware físico** (próximo passo).

### Rotina fixa de "query" ao conectar (não confundir com troca de slot)

Logo após o sweep de sincronização, o app sempre envia uma sequência fixa, idêntica em
toda conexão, independente do que o usuário faz depois (provavelmente "buscar
estado/preset atual para exibir na UI"): `bridge(0x06)` → resposta state(172B);
`selectPreset(0x00,COMMIT)` → baixa preset 0 completo (~26KB); `bridge(0x01)` → resposta
14B; comando novo `B9 03 81 0D 03 82 05 00 80 0B 03 B9 03 03 00 00` (tipo `0x030D`,
20 bytes) → resposta 21B; `bridge(0x0A)` → resposta 828B; `bridge(0x01)` → rajada de
state+detail. Não é necessário reproduzir essa rotina para trocar de slot.

### Histórico — primeira hipótese (INCORRETA, captura parcial, mantida para referência)

Captura USB do **app oficial (PC)** obtida via USBPcap (`tonexfinal_official.pcap`,
367 KB, LinkType 249), só que **sem o início da conexão** — por isso o sweep de
sincronização da biblioteca foi confundido com "o usuário trocando 3 presets":

Payload (ANTES do framing HDLC), 17 bytes:
```
B9 03 81 00 03  82 06 00  80 0B 03  B9 04  0B 01  [PRESET]  [PHASE]
└── header ───┘  └─ envelope constante ──┘         └────── varia ──────┘
   tipo 0x0300
```
Isso é, na verdade, o comando "buscar preset N da biblioteca" (ver seção acima), não a
troca de slot.

### Histórico — diagnóstico original do Bug 2 (notificação device→host)

- O footswitch troca o preset e o pedal faz PUSH de um frame `0x0304` (notificação
  device→host) com o novo preset. Durante a captura alternou entre:
  - `"John Mayer/NDSP Fat US Clean"`
  - `"John Mayer NDSP Arch SSS/Klon"`
- O nome do preset no 0x0304 vem após `BC <len>`, ex.:
  `BC 21 4A 6F 68 6E 20 4D 61 79 65 72 2F 4E 44 53 50 20 46 61 74 20 55 53 20 43 6C 65 61 6E`
  (`0x21`=33 bytes ASCII = "John Mayer/NDSP Fat US Clean").
- No StateResponse 0x0306, a coleção de slots aparece como `BC 06 03 00 0B 00 0D 00 ...`.
  Durante TODOS os frames 0x0306 da fase do footswitch, o byte em `BC+8` ficou `00`,
  mesmo com o preset claramente mudando → **o offset de "slot ativo" assumido (BC+8,
  via `STATE_FIELDS_OFFSET=22`) provavelmente está ERRADO.**
- `writeState()` envia `buildSetStatePayload` (um StateResponse 0x0306 com 1 byte
  trocado). Isso é o formato de RESPOSTA, não de COMANDO — o pedal ignora.

### O que ainda falta (Bug 2)
- O **comando host→device para trocar o preset** NÃO aparece nesta captura, porque o
  footswitch é operado no próprio pedal (só vemos a notificação device→host).
- Para descobrir o comando real é preciso capturar o **app oficial (PC) trocando o
  preset** — via USBPcap (que falhou nesta sessão; precisa de `-I` para hub USB 3.0
  e/ou execução elevada) ou outro sniffer USB.

## Bug 3 — conexão lenta (só conectava após ~10 toques / footswitch) — CAUSA RAIZ CONFIRMADA (2026-06-30)

**Sintoma:** o app só conectava depois de ~10 toques em "Conectar", ou se o usuário
apertasse o footswitch do pedal. O app oficial conecta sem footswitch.

**Causa raiz:** a interface serial do pedal fica **dormente** e ignora o nosso Hello
(`B9 03 81 03 00`) até ser "acordada". O footswitch acorda (gera atividade); o app oficial
acorda enviando um **comando de init** como PRIMEIRO comando ao conectar — que o nosso app
nunca enviava. Extraído de `tonex_full_session.pcap` (OUT #0, conexão do zero):

```
WAKE (host→device): B9 03 00 82 04 00 80 0B 01 B9 02 02 0B   (tipo 0x0482, CRC 17 8C)
resp (device→host): B9 03 02 2B 0B ...  (52B, tipo 0x0B2B)   = pedal acordou
```

Só depois disso o app oficial faz o sweep da biblioteca. CRC do nosso wake validado byte a
byte contra a captura. Implementado em `TonexMessages.wakePayload()`; o handshake envia
**wake → (0x0B2B) → Hello → (0x0306)**.

**Evidência de timing (capturas JSONL on-device):** round-trips, quando o comando chega, são
de 4–120ms (instantâneo). Mas os comandos são **descartados ~50%** no nível do USB Android, e
**o 1º comando após abrir a porta quase sempre cai** (porta "fria"). Por isso o handshake faz
várias tentativas internas (reabrindo a porta) por toque — ver `UsbPedalConnection.HANDSHAKE_ATTEMPTS`.

**Correções relacionadas (mesma frente):**
- Connect passou de 2 round-trips para 1: o Hello já devolve o 0x0306 completo (firmware +
  estado), então não há `requestState()` separado ao conectar.
- Captura: o coletor de `runtimeEvents` agora aguarda a inscrição antes do handshake emitir
  (`SharedFlow` sem replay descartava os eventos de hello/estado — por isso "hello" não
  aparecia nas capturas). A captura também não para mais ao desconectar.

## Status da captura USB (2026-06-25) — DESBLOQUEADO ✅

Captura do app oficial **obtida** (`tonexfinal_official.pcap`, 367 KB). O comando de troca
de preset foi extraído e está documentado acima (Bug 2 RESOLVIDO). Ferramentas de análise
(sem depender de tshark/Wireshark) em `tools/`:
- `parse-usbpcap.ps1` — dump geral de transfers com payload.
- `parse-out.ps1` — só comandos host→device (bulk OUT).
- `timeline.ps1` / `types.ps1` — correlação temporal e histograma de tipos.

Alternativa nativa (caso USBPcap volte a falhar em xHCI): captura ETW via `wpr.exe` com o
perfil `tools/usb-trace.wprp` (providers UCX/USBXHCI/USBHUB3), decodificável por `tracerpt`.

### Histórico do bloqueio (USBPcap em xHCI/USB 3.0)

Tentativas anteriores de USBPcap falharam (5+ vezes). Diagnostico:
- O pedal enumera num **root hub USB 3.0 (xHCI / ROOT_HUB30)**, em `\\.\USBPcap2`.
- **Elevado:** USBPcapCMD abre o device mas captura **0 pacotes** (so o header de 24 B) —
  limitacao conhecida do USBPcap em xHCI (exige setup de `NonStandardHWIDs` + reconectar).
- **Nao elevado:** `Couldn't open device - 5` (acesso negado).
- Conclusao: nao adianta repetir o USBPcap CLI neste setup.

## Proximos passos sugeridos (Bug 2)

1. **[Maior chance] Plugar o pedal via porta/HUB USB 2.0** (forcar enumeracao Full Speed,
   fora do xHCI). Aí o USBPcap captura sem o setup de USB 3.0. Comando, em terminal
   **como administrador**, e identificar o novo `\\.\USBPcapN` do hub 2.0:
   `& "C:\Program Files\USBPcap\USBPcapCMD.exe" -d "\\.\USBPcapN" -A -o saida.pcap`
   Trocar preset no app oficial, Ctrl+C, ler com `tshark` e achar o comando host->device.
2. **MIDI Program Change**: confirmar se o ToneX One expoe interface USB-MIDI (na
   enumeracao aparecia so serial CDC + audio; checar device composto / manual IK). Se
   sim, trocar de preset via Program Change padrao dispensa engenharia reversa.
3. Achar o offset REAL do slot ativo no 0x0306 (no footswitch o byte em BC+8 ficou 00 —
   o offset assumido via `STATE_FIELDS_OFFSET=22` provavelmente esta errado).
4. Decodificar o campo de versao de firmware (spec IK ou comparar com o app oficial).
