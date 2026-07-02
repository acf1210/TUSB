# TUSB / ToneX One - Documento mestre tecnico

Versao do app: `0.4.0` (`versionCode 8`)  
Data de consolidacao: 2026-07-02  
Escopo: controlador Android TUSB para ToneX One via USB host / CDC ACM.

## 1. Resumo executivo

Esta versao consolida o que ja foi validado no hardware e separa claramente o que esta
implementado, o que esta apenas mapeado por captura e o que ainda falta implementar.

O framing HDLC/CRC e o envelope/offsets de troca de estado (slot ativo e presets A/B/C)
foram cross-validados byte a byte contra DOIS firmwares ESP32 open-source independentes:
`vit3k/tonex_controller` e `Builty/TonexOneController` (2026-07-02). O Hello/wake, os
offsets de slot/preset, o baud rate 115200 e a estrategia de reescrita completa do
StateData estao confirmados pelas tres fontes (captura propria + 2 firmwares) — ver
`docs/protocol-notes.md`.

Funcionando ate o momento:

- Identidade do app TUSB: label Android, icone launcher e instalacao via ADB Wi-Fi.
- Conexao real USB com ToneX One usando sequencia `wake -> hello`.
- Captura JSONL de eventos de runtime, requests, respostas de estado e frames brutos.
- Leitura do estado global `0x0306` no handshake.
- Troca de slot A/B/C por reenvio de StateData com header de comando.
- Alternancia de modo A/B e STOMP.
- UI principal com controles de presets, modo, bypass/cab e knobs virtuais.
- Knobs virtuais responsivos no aplicativo, com incremento e decremento por gesto vertical.
- Teste de bancada validado pelo usuario para A/B, STOMP e alteracao visual/local dos knobs virtuais.
- Captura real de notificacoes de knob fisico em frame `0x0309`, com valor float variando.
- Protocolo de parametros `0x0309` DECODIFICADO E VALIDADO EM BANCADA (2026-07-02, pedal
  fisico via USB no Android): knob fisico atualiza o knob virtual na UI em tempo real, e
  knob virtual escreve no pedal via comando de parametro unico (Gain=20, Bass=11, Mid=13,
  Treble=16, Volume=21). Indice na tabela tonex_params + float LE apos marcador 0x88.
- Recepcao de rajadas na serial corrigida (requisito do 0x0309 em bancada):
  `UsbSerialTransport.readFrame` preserva os bytes excedentes do chunk USB (varios frames
  HDLC chegam juntos quando o knob gira) e `UsbPedalConnection.drainPassiveFrames` drena
  ate 64 frames pendentes por poll em vez de 1 - sem isso as notificacoes de knob se
  perdiam/envelheciam no buffer e a UI nao reagia.
- Parse do bloco de parametros do detalhe de preset `0x0304` (marcador `BA 03 BA 6D`),
  ligado a UI: os knobs virtuais carregam os valores REAIS do preset ativo quando o
  detalhe chega do pedal.
- Cadeia de efeitos 100% funcional via 0x0309 (aguardando teste de bancada):
  - Toggles de bloco escrevem os enables reais (GATE=1, CMP=6, MOD=64, DLY=95, REV=37);
    CAB usa o cab_sim_bypass do StateData (ja validado); EQ nao tem enable no protocolo.
  - Sliders da tela de detalhe escrevem com ranges reais; reverb/modulacao/delay resolvem
    o indice DINAMICAMENTE pelo modelo ativo (spring1-4/room/plate = 39+modelo*4;
    chorus/tremolo/phaser/flanger/rotary; digital/tape = 97+modelo*6) - ver
    `domain/TonexParam.kt` (TonexEffectParams).
  - Chaves PRE/POST (POST: GATE=0, CMP=5, EQ=10, MOD=63, DLY=94, REV_POSITION=36) e
    SYNC/PING-PONG do delay tambem escrevem no pedal.
  - Estado inicial (toggles e sliders) carregado do bloco de parametros do 0x0304.

Ainda nao concluido:

- Persistir parametros alterados no preset do pedal (a escrita 0x0309 altera o preset
  ativo em memoria; o comando de "save" permanente ainda nao foi mapeado).
- Parsear o detalhe completo do preset `0x0304` (nome + parametros ja sao extraidos;
  demais campos ainda nao mapeados).

## 2. Ambiente e alvo

Hardware alvo:

- ToneX One.
- USB VID/PID observado: `0x1963 / 0x00D1`.
- Interface usada: CDC ACM serial via Android USB host.
- Baud rate usado pela camada serial: `115200`.
- Framing observado: HDLC delimitado por `0x7E`.

App Android:

- Package/applicationId: `com.opentonex.controller`.
- Nome exibido: `TUSB`.
- `minSdk`: 24.
- `targetSdk`: 34.
- `compileSdk`: 34.
- UI: Kotlin + Jetpack Compose + Material 3.
- Transporte USB: `usb-serial-for-android`.

## 3. Fontes de informacao usadas

Fontes locais do projeto:

- `app/src/main/java/com/opentonex/controller/protocol/TonexMessages.kt`
- `app/src/main/java/com/opentonex/controller/protocol/HdlcCodec.kt`
- `app/src/main/java/com/opentonex/controller/protocol/TaggedValue.kt`
- `app/src/main/java/com/opentonex/controller/connection/UsbPedalConnection.kt`
- `app/src/main/java/com/opentonex/controller/repository/PedalRepository.kt`
- `app/src/main/java/com/opentonex/controller/ui/PedalViewModel.kt`
- `app/src/main/java/com/opentonex/controller/ui/ToneXApp.kt`
- `app/src/main/java/com/opentonex/controller/capture/EventCaptureRecorder.kt`
- `app/src/test/java/com/opentonex/controller/protocol/TonexMessagesTest.kt`
- `app/src/test/java/com/opentonex/controller/connection/UsbPedalConnectionTest.kt`
- `app/src/test/java/com/opentonex/controller/repository/PedalRepositoryTest.kt`
- `app/src/test/java/com/opentonex/controller/ui/PedalViewModelTest.kt`

Documentacao e engenharia reversa:

- `docs/protocol-notes.md`
- `docs/apk-reverse-findings.md`
- [`vit3k/tonex_controller`](https://github.com/vit3k/tonex_controller) (firmware ESP32
  open-source de referencia, mesmo VID/PID) — usado para validar de forma independente
  o framing HDLC/CRC e os offsets de slot/preset; ver secao "Validacao cruzada com
  referencia externa" em `docs/protocol-notes.md`.
- [`Builty/TonexOneController`](https://github.com/Builty/TonexOneController) (firmware
  ESP32 open-source ativamente mantido, suporta firmware 1.2.x+ do pedal) — segunda
  validacao independente: confirma Hello/wake, RequestState canonico (15B), baud 115200,
  offsets de slot/preset e a estrategia de reescrita completa do StateData; a tabela de
  106 parametros de `tonex_params.c` serve de referencia de ranges para o editor. Ver
  secao "Validacao cruzada com segunda referencia" em `docs/protocol-notes.md`.
- `docs/superpowers/specs/2026-06-24-tonex-one-v1-android-controller-design.md`
- `docs/superpowers/specs/2026-06-25-tonex-runtime-events-jsonl-capture-design.md`
- `docs/superpowers/plans/2026-06-24-tonex-fase1-fundacao-protocolo.md`
- `docs/superpowers/plans/2026-06-24-tonex-fase2-usb-real.md`
- `docs/superpowers/plans/2026-06-25-tonex-fase3-ui-compose.md`

Capturas e evidencias:

- Capturas oficiais citadas em `docs/protocol-notes.md`: `tonex_full_session.pcap`,
  `tonex_isolated_switch.pcap`, `tonexfinal_official.pcap`.
- Capturas JSONL on-device em `captures/`, especialmente
  `captures/tonex-session-1782930773375.jsonl`, que registrou frames `0x0309`
  durante giro de knob fisico.
- Ferramentas de analise: `tools/map-knob-bytes.ps1`,
  `tools/compare-state-snapshots.ps1`, `tools/dump-state-snapshot.ps1`.

## 4. Arquitetura atual

Camadas principais:

- `PedalTransport`: abstracao de transporte serial/USB.
- `UsbPedalConnection`: implementa protocolo ToneX sobre HDLC.
- `TonexMessages`: monta e decodifica payloads do protocolo.
- `PedalRepository`: guarda o estado conectado, reconcilia snapshots e aplica eventos runtime.
- `PedalViewModel`: exponibiliza estado para Compose e controla a captura.
- `HomeScreen`: tela principal operacional.
- `EventCaptureRecorder`: grava eventos em JSONL para depuracao e mapeamento de protocolo.

Fluxo de conexao real:

1. Abrir transporte USB.
2. Enviar `wakePayload()`.
3. Aguardar resposta tipo `0x0B2B`.
4. Enviar `helloPayload()`.
5. Aguardar resposta tipo `0x0306`.
6. Decodificar firmware conhecido/estado inicial.
7. Iniciar leitura passiva periodica para frames assincronos.

## 5. Protocolo consolidado

### 5.1 Tipo de mensagem

O tipo de mensagem e lido em little-endian nos offsets 3 e 4 do payload decodificado:

```text
B9 03 81 [tipo_lo] [tipo_hi] ...
```

Exemplos:

- `B9 03 81 06 03` -> `0x0306`.
- `B9 03 81 04 03` -> `0x0304`.
- `B9 03 81 09 03` -> `0x0309`.

### 5.2 Wake / init

O pedal pode ignorar o Hello quando a porta esta "fria". A sequencia de wake foi extraida
da captura do app oficial:

```text
B9 03 00 82 04 00 80 0B 01 B9 02 02 0B
```

Resposta esperada observada: tipo `0x0B2B`.

Esse passo resolveu a conexao lenta que antes dependia de varios toques ou de atividade no
footswitch.

### 5.3 Hello e estado global

O Hello usado e:

```text
B9 03 81 03 00
```

O pedal responde com `0x0306`, contendo o estado global. Nao ha, ate agora, um frame
separado confiavel de versao de firmware. Por isso `parseFirmware()` nao deve raspar texto
solto do binario.

### 5.4 StateResponse `0x0306`

Campos atualmente decodificados:

- `inputTrim`
- modo STOMP/A-B
- bypass de Cab/IR
- cores dos presets/biblioteca
- IDs de preset atribuidos aos slots A/B/C
- slot ativo
- A4 reference
- tempo
- bypass mode

O app preserva o `rawState` completo para reenvio fiel ao pedal.

### 5.5 Troca de slot A/B/C

A captura oficial isolada mostrou que a troca de slot nao usa o sweep `0x0300`. O comando
real reenvia o corpo do StateResponse `0x0306`, altera somente o byte de slot ativo e troca
o sufixo do header:

```text
Resposta device->host: B9 03 81 06 03 80 A0 02 [StateData...]
Comando host->device:  B9 03 81 06 03 82 A0 00 80 0B 03 [StateData...]
```

Implementacao:

- `TonexMessages.buildSetStatePayload()`
- `TonexMessages.buildSlotChangePayload()`
- `UsbPedalConnection.writeState()`
- `PedalRepository.selectSlot()`

Validacao:

- Teste golden byte-a-byte contra captura oficial em `TonexMessagesTest`.
- Teste de bancada reportado pelo usuario validando A/B.

### 5.6 Modo STOMP

O modo STOMP usa o byte `StateData[19]`:

- `0`: A/B.
- `1`: STOMP.

Implementacao:

- `TonexMessages.buildSwitchModePayload()`
- `PedalRepository.switchMode()`
- `FakePedalConnection.switchMode()` para simulacao/testes.

Validacao:

- Testes unitarios de montagem de payload.
- Teste de bancada reportado pelo usuario validando STOMP.

### 5.7 Preset detail `0x0304`

O frame `0x0304` aparece como notificacao assincrona quando o preset ativo muda. O app
decodifica apenas o nome ASCII do preset, procurando `BC <len> <ascii...>`.

Implementado:

- `TonexMessages.parsePresetNameFromDetail()`
- `UsbPedalConnection.emitPresetDetail()`
- `PedalRepository.applyRuntimeEvent()`

Pendente:

- Decodificacao completa dos parametros do preset.

### 5.8 Parameter update `0x0309`

Captura real em `captures/tonex-session-1782930773375.jsonl` mostrou frames de runtime:

```text
B9 03 81 09 03 0A 02 B9 04 02 00 15 88 33 33 03 41
B9 03 81 09 03 0A 02 B9 04 02 00 15 88 9A 99 01 41
B9 03 81 09 03 0A 02 B9 04 02 00 15 88 33 33 F3 40
...
```

Interpretacao atual:

- Tipo: `0x0309`.
- Prefixo constante observado: `B9 03 81 09 03 0A 02 B9 04 02 00`.
- ID observado: `0x0015`.
- Valor: `0x88` seguido de float little-endian.
- Valores decodificados no trecho: aproximadamente `8.2 -> 8.1 -> 7.6 -> 7.2 -> 6.8
  -> 6.3 -> 5.6 -> 4.4 -> 3.8`.

Conclusao:

- Este e o primeiro caminho confirmado para ler movimento de knob fisico.
- O ID `0x0015` provavelmente corresponde ao knob fisico girado no teste, mas ainda falta
  isolar Gain, Bass, Mid, Treble e Volume individualmente.
- O app ainda captura esses frames como `FrameReceived`; aplicar na UI exige um evento
  semantico novo e uma tabela de IDs confirmados.

## 6. Funcionalidades implementadas

### 6.1 Identidade visual TUSB

Status: implementado.

- `AndroidManifest.xml` usa `android:label="TUSB"`.
- Icones launcher estao em `res/mipmap-*`.
- APK debug foi instalado via ADB Wi-Fi durante os testes.

### 6.2 Conexao real com pedal

Status: implementado e usado em bancada.

Recursos:

- Wake antes do Hello.
- Retry interno para porta fria.
- Reabertura do transporte entre tentativas.
- Um unico round-trip efetivo de estado apos wake.
- Captura de `hello_response` e `state_snapshot`.

Risco conhecido:

- A pilha USB Android ainda pode descartar comandos. O retry mitiga, mas nao elimina
  completamente falhas transientes.

### 6.3 Captura JSONL

Status: implementado.

Eventos capturados:

- `connect`
- requests enviados
- `hello_response`
- `state_snapshot`
- `preset_detail`
- `frame`
- `transport_error`
- `disconnect`

Uso:

- As capturas ficam em `Android/data/com.opentonex.controller/files/event-captures`.
- A coleta foi usada para descobrir o frame `0x0309`.

### 6.4 A/B e STOMP

Status: implementado e validado em teste de bancada reportado pelo usuario.

Base tecnica:

- A/B e slot ativo usam o StateData `0x0306` reescrito.
- STOMP usa `StateData[19]`.

Cobertura:

- Testes unitarios de payload.
- Testes de repositorio/ViewModel para fluxo de estado.
- Teste manual no hardware.

### 6.5 Knobs virtuais

Status: implementado como UI local, validado visualmente em bancada.

Comportamento:

- Knobs respondem a gesto vertical.
- Valor sobe e desce.
- Valor e limitado entre `0.0` e `1.0`.
- Estado atual vive em `AmpKnobUiState`.

Limite importante:

- Os knobs virtuais ainda nao escrevem parametros no pedal. Hoje eles alteram o estado da
  interface do app.

## 7. Funcionalidades parcialmente implementadas

### 7.1 Bypass e Cab/IR bypass

Status: implementado no codigo, pendente validacao formal completa.

Implementacao:

- `PedalRepository.toggleBypass()`
- `UsbPedalConnection.writeBypass()`
- `TonexMessages.buildSetBypassPayload()`
- `PedalRepository.toggleCabSimBypass()`
- `UsbPedalConnection.writeCabSimBypass()`
- `TonexMessages.buildSetCabSimBypassPayload()`

Pendencias:

- Registrar teste de bancada isolado.
- Confirmar comportamento de audio em todos os modos.

### 7.2 Nomes de presets

Status: parcialmente implementado.

Implementado:

- Nome vindo de `0x0304` para o preset ativo.
- Fallback `Preset A/B/C` e biblioteca sintetica `Preset 01..20`.

Pendente:

- Sincronizacao completa da biblioteca de presets.
- Nome real de todos os 20 presets.

## 8. Funcionalidades ainda nao implementadas

Prioridade alta:

- Decodificar semanticamente `0x0309` em `ParameterChanged`.
- Mapear IDs reais de `Gain`, `Bass`, `Mid`, `Treble`, `Volume`.
- Atualizar `AmpKnobUiState` automaticamente quando o knob fisico mudar.
- Enviar alteracao dos knobs virtuais para o pedal.
- Confirmar escala real dos parametros: UI `0..1` vs hardware `0..10` ou outra faixa.

Prioridade media:

- Parse completo do preset detail `0x0304`.
- Sincronizacao de biblioteca/presets a partir da rotina oficial.
- Persistencia/salvamento de preset modificado.
- Estado de `preset_dirty`.
- Versao real de firmware se existir campo confiavel.
- Tratamento visual de desconexao/reconexao durante operacao.

Prioridade baixa ou futura:

- Afinador.
- Medidores de entrada/saida.
- MIDI Program Change, se a interface expuser MIDI.
- Edicao completa de compressor, noise gate, cab, modulation, delay, reverb e power amp EQ.
- Testes E2E instrumentados no Android.

## 9. Mapa de testes

Testes automatizados existentes:

- `TonexMessagesTest`: payloads, parse de firmware, preset detail, troca de slot, STOMP,
  cab sim bypass.
- `UsbPedalConnectionTest`: connect, handshake, retry, requestState, writeState,
  notificacoes assincronas, preset detail, leitura passiva.
- `PedalRepositoryTest`: estado conectado, captura do handshake, selectSlot, refresh,
  presetIds confiaveis.
- `PedalViewModelTest`: conexao, selecao de slot, knobs virtuais, captura UI, busy state.

Teste de bancada consolidado nesta versao:

- A/B funcionando no hardware.
- STOMP funcionando no hardware.
- Knobs virtuais alteram valor visual/local no app.
- Captura de knob fisico produziu frames `0x0309` com float variando.

Comando recomendado de regressao:

```powershell
rtk cmd /c gradlew.bat testDebugUnitTest --tests com.opentonex.controller.connection.UsbPedalConnectionTest --tests com.opentonex.controller.repository.PedalRepositoryTest --tests com.opentonex.controller.ui.PedalViewModelTest --tests com.opentonex.controller.protocol.TonexMessagesTest
```

Comando recomendado de build:

```powershell
rtk cmd /c gradlew.bat assembleDebug
```

## 10. Regras tecnicas importantes

- Nao usar `0x0300` como troca de slot. Esse caminho foi identificado como sweep/sync de
  biblioteca e pode deixar o pedal instavel.
- Preservar `rawState` sempre que possivel. O comando de slot/mode depende de reenvio fiel
  do StateData.
- Capturar frames brutos antes de descartar tipos desconhecidos. Foi assim que o `0x0309`
  apareceu.
- Separar "funciona na UI" de "foi escrito no hardware". Os knobs virtuais estao no primeiro
  grupo ate que exista comando de escrita confirmado.
- Toda nova hipotese de protocolo deve ter captura JSONL/PCAP e teste unitario de regressao.

## 11. Plano tecnico recomendado

Proximo passo imediato:

1. Criar parser para `0x0309`, retornando `parameterId` e `value`.
2. Criar evento `PedalRuntimeEvent.ParameterChanged`.
3. Registrar `parameter_change` no JSONL com campos decodificados.
4. Aplicar `parameterId` confirmado ao `AmpKnobUiState`.
5. Fazer capturas isoladas: girar somente Gain, depois Bass, Mid, Treble e Volume.
6. Fechar tabela de IDs reais.
7. Implementar escrita de parametros virtuais no pedal apenas depois de capturar o comando
   host->device equivalente no app oficial ou confirmar por teste controlado.

Tabela provisoria:

| Parametro | ID real | Status |
|-----------|---------|--------|
| Gain      | `0x0015` provavel | Capturado em `0x0309`, precisa confirmacao isolada |
| Bass      | desconhecido | Pendente |
| Mid       | desconhecido | Pendente |
| Treble    | desconhecido | Pendente |
| Volume    | desconhecido | Pendente |

## 12. Historico de versoes

### 0.4.0 - Navegacao final, identidade visual e ferramentas reais

- Removida a aba ToneNET (sem infraestrutura de conta/nuvem pra sustentar). Navegacao final:
  Editor, Presets, Tools, Menu.
- Barra de identidade no topo (`TopBrandBar` em `ToneXApp.kt`) com o icone TUSB, presente em
  layout de celular e tablet.
- Menu > Volume: Master Volume agora e um slider funcional local (`MenuUiState.masterVolume`),
  mais Input Trim real do pedal.
- Menu > Tuner: Referencia A4 agora e ajustavel localmente (`MenuUiState.a4ReferenceOverride`,
  430-450 Hz), mostrando lado a lado o valor real reportado pelo pedal.
- Menu > MIDI: mantido desabilitado de proposito (sem transporte MIDI real no protocolo USB
  CDC atual).
- Tools agora e um metronomo 100% funcional: BPM ajustavel (slider/stepper), tap tempo, som de
  clique real via `ToneGenerator` (sem permissao necessaria), indicador visual de batida.
  Afinador com deteccao de pitch por microfone fica para uma proxima sessao (exige permissao
  RECORD_AUDIO e algoritmo de deteccao em tempo real).

### 0.3.4 - Fonte oficial e imagens de ampli/pedal reais

- Tipografia trocada para Roboto (Regular/Medium/Bold), extraida do `.pak` oficial, aplicada
  em `ToneXTheme` (`Theme.kt`) via `Typography` customizado.
- Slot "TONEX" da esteira principal (`RigGraphic`) agora usa `ToneXAmpBlack.png` real do app
  oficial em vez do placeholder generico `tnxablk`. Slot "STOMP" agora usa `ToneXPedalBlack.png`
  (o proprio ToneX One) em vez do placeholder de pedal Boss generico.
- Slot "CAB" mantido com os placeholders anteriores (Fender Twin/Silverface): nao ha um asset
  oficial dedicado de gabinete isolado no `.pak`, os assets de ampli ja incluem cab.

### 0.3.3 - Icones reais e cadeia de efeitos funcional

- Extraidos os icones oficiais de efeito (Gate, Comp, Mod, Delay, Reverb, Cab) do arquivo
  `assets/Paks/TONEX Control.pak` do APK oficial. Formato do pacote decifrado nesta versao:
  assinatura `IKMPAK`, header com versao/contagem/offset de tabela, entradas
  `[nome\0][offset u64 LE][tamanho u64 LE]`, 802 arquivos ao todo. Script de extracao em
  `tools/apk-reverse/extract_pak.py`. Nao existe icone oficial dedicado para "EQ Global"
  (nao e um bloco arrastavel no app oficial); usamos um icone Material (GraphicEq) como
  fallback so pra esse bloco.
- Icones copiados para `app/src/main/res/drawable-nodpi/gear_*.png` e usados em
  `EditorScreen.kt` na esteira de efeitos.
- Cadeia de efeitos agora e funcional: cada bloco tem um LED liga/desliga tocavel direto na
  esteira (`EffectChainUiState` em `PedalViewModel.kt`), que persiste ao navegar para a tela
  de detalhe e voltar (antes o estado era local e se perdia). Ainda sem escrita real no pedal.

### 0.3.2 - Chips de efeito por preset

- Lista "20 presets" (`PresetsScreen.kt`) ganhou chips AMP/CAB/CMP/MOD/DLY/REV por preset,
  como na lista do app oficial. Estado neutro/generico: ainda nao sabemos quais blocos cada
  preset usa de verdade (falta decodificar o preset detail `0x0304` por completo).

### 0.3.1 - Esteira de efeitos completa

- Esteira de efeitos do Editor expandida de 3 para 7 blocos: GATE, CMP, EQ, MOD, DLY, REV, CAB
  (antes so tinha MOD/DLY/REV). Lista agora e um `LazyRow` rolavel.
- `EffectDetailScreen` cobre os 7 tipos com labels proprios por bloco (ex.: GATE usa
  THRESHOLD/DEPTH/RELEASE, CAB usa MIC BLEND/RESONANCE/MIC POSITION).
- Revisado pelo subagente `ecc:kotlin-reviewer`: aprovado, 0 achados criticos/altos.
  Duas pendencias de debito tecnico registradas: duplicacao de `PanelShape`/`slotColor`/
  `toColor` entre `EditorScreen.kt` e `PresetsScreen.kt` (candidato a arquivo compartilhado),
  e fallback silencioso de `EffectSlotType.valueOf` na rota de navegacao (sem log).
- Escopo do Menu (MIDI Channel/Thru/Clock Mode) mantido como placeholder deliberadamente:
  exigiria descobrir offsets desses campos no StateData via captura isolada, que e trabalho
  do sub-projeto 1 (parametros fisicos), adiado para o final por decisao do usuario.

### 0.3.0 - Redesenho visual (paridade com o app oficial)

- Navegacao trocada de 3 para 5 abas: Editor, Presets, ToneNET, Tools, Menu (ordem e
  nomenclatura do ToneX Control oficial).
- Tela Editor reformulada: cabecote do ampli em destaque, knobs BASS/MID/TREBLE/GAIN/VOLUME,
  e nova esteira de efeitos clicavel (`EffectChainStrip`) com blocos MOD/DLY/REV.
- Nova tela `EffectDetailScreen` por bloco de efeito (NORMAL/PING PONG, TIME/FEEDBACK/MIX,
  PRE/POST, SYNC para Delay); estado 100% local, sem escrita no pedal ainda.
- Tela Presets extraida da antiga Home: seletor Dual Mode/Stomp Mode, footswitches, biblioteca
  de 20 presets e lista "Slots do pedal".
- Tela Config renomeada para Menu, com sub-abas Device/Volume/MIDI/Tuner/General; MIDI Channel,
  MIDI Thru, Clock Mode e Repeated PC aparecem como placeholders visuais (nao implementados).
- Telas ToneNET e Tools adicionadas como placeholders "em breve".
- Validado no hardware: instalado via ADB Wi-Fi no aparelho conectado ao pedal, sem crash no
  logcat, screenshots reais confirmando Editor e Presets.

### 0.2.0 - Consolidacao de bancada

- Consolidado status tecnico do controlador TUSB.
- Validado A/B, STOMP e knobs virtuais em teste manual.
- Registrado frame `0x0309` como fonte real de alteracao de knob fisico.
- Documentadas pendencias para mapeamento real de Gain/Bass/Mid/Treble/Volume.

### 0.1.0 - Base inicial

- Fundacao Android/Compose.
- Transporte USB/HDLC.
- Parser inicial de estado.
- Captura de eventos e fluxo basico de conexao.
