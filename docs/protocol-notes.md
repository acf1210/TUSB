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

## Próximos passos sugeridos
1. Re-rodar o diagnóstico capturando ESPECIFICAMENTE qual byte do 0x0306 muda quando o
   preset troca (achar o offset real do slot ativo).
2. Capturar o app oficial no PC trocando preset para obter o comando de escrita real.
3. Decodificar o campo de versão de firmware (spec IK ou comparar com versão mostrada
   pelo app oficial).
