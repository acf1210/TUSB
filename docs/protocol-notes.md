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

## Bug 2 — troca de slot pela UI do app não muda o preset do pedal

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

## Status da captura USB (2026-06-25) — BLOQUEADO

Tentativas de USBPcap nesta maquina falharam (5+ vezes). Diagnostico final:
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
