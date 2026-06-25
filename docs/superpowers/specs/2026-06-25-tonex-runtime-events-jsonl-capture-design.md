# Spec de Design - Runtime Events e Captura JSONL do ToneX

- **Data:** 2026-06-25
- **Autor:** Codex + usuário
- **Status:** Proposto para revisão

## 1. Objetivo

Evoluir o app Android do ToneX para acompanhar eventos assíncronos do pedal físico em tempo real e persistir essas evidências em captura local `JSONL` para análise posterior.

O foco desta fase é:

- sincronizar o estado do app com notificações runtime do pedal
- capturar eventos brutos e interpretados em arquivo local
- tornar o teste com **celular + pedal físico** o fluxo principal de validação

## 2. Escopo

### Em escopo

- adicionar um stream de eventos runtime na camada `PedalConnection`
- traduzir frames `0x0304` e `0x0306` em eventos tipados
- reconciliar esses eventos no `PedalRepository`
- persistir eventos em arquivo `JSONL`
- expor controle simples de captura para sessões reais com o pedal
- preservar a UI atual como observadora de `StateFlow`

### Fora de escopo

- escrita segura de parâmetros globais além de troca de preset
- parser completo de todos os campos ainda desconhecidos do protocolo
- exportação para nuvem, banco ou upload remoto
- automação de teste sem hardware físico
- instrumentação Android UI test como fonte principal de validação

## 3. Abordagem Escolhida

A solução adotada é **eventos tipados no `PedalConnection`**, em vez de polling periódico ou loop de leitura no repositório.

Essa abordagem mantém a arquitetura em camadas:

- `UsbPedalConnection` entende transporte + protocolo assíncrono
- `PedalRepository` entende reconciliação de estado
- `PedalViewModel` só espelha estado e erros
- Compose continua sem lógica de protocolo

Isso prepara o software para runtime real, reconnect futuro, monitoramento de saúde e investigações de protocolo, sem acoplar USB à UI.

## 4. Componentes

### 4.1 `PedalRuntimeEvent`

Criar um sealed type pequeno para representar eventos observáveis do pedal em runtime.

Eventos iniciais:

- `PresetDetailReceived(name: String, payloadHex: String)`
- `StateReceived(state: PedalState, payloadHex: String)`
- `TransportError(message: String, payloadHex: String? = null)`
- `Disconnected`

Opcionalmente, o schema interno pode preservar `messageType` bruto para facilitar diagnóstico.

### 4.2 `PedalConnection`

A interface atual ganha um stream observável:

- `val runtimeEvents: Flow<PedalRuntimeEvent>`

Os métodos síncronos existentes permanecem:

- `connect()`
- `sendHello()`
- `requestState()`
- `selectPreset()`
- `disconnect()`

`writeState()` continua desabilitado para hardware real enquanto o caminho não for comprovadamente seguro.

### 4.3 `UsbPedalConnection`

Depois do `connect()`, a implementação real sobe um loop de leitura controlado, com **um único consumidor do transporte**. Esse loop:

- lê frames HDLC continuamente
- decodifica o payload
- classifica por tipo
- emite eventos runtime

Regras:

- `0x0304` gera `PresetDetailReceived`
- `0x0306` gera `StateReceived`
- erros transitórios geram `TransportError`
- fechamento real de transporte gera `Disconnected`

O design evita dois leitores concorrendo no mesmo `UsbSerialTransport`.

### 4.4 `FakePedalConnection`

Também expõe `runtimeEvents`, permitindo testes JVM do fluxo de sincronização e da captura `JSONL` sem depender do Android runtime.

### 4.5 `PedalRepository`

Vira o reconciliador central do estado conectado.

Responsabilidades:

- executar handshake inicial em `connect()`
- iniciar coleta de `runtimeEvents`
- atualizar somente o nome do slot ativo quando chegar `PresetDetailReceived`
- substituir snapshot inteiro quando chegar `StateReceived`
- expor estado final via `StateFlow<ConnectionState>`
- encerrar coleta ao desconectar

### 4.6 `EventCaptureRecorder`

Novo componente de persistência local, desacoplado da UI.

Responsabilidades:

- abrir uma sessão de captura
- gravar uma linha JSON por evento
- fazer append-only no arquivo da sessão
- fechar a sessão com segurança
- nunca derrubar a conexão do pedal por falha de escrita

## 5. Captura JSONL

### 5.1 Local de armazenamento

Arquivos em diretório interno do app, com estrutura semelhante a:

`files/event-captures/tonex-session-<timestamp>.jsonl`

Cada sessão de teste físico gera um arquivo dedicado.

### 5.2 Schema por linha

Cada linha contém um objeto JSON independente.

Campos iniciais:

- `timestamp`
- `sessionId`
- `source`
  - `runtime`
  - `request`
  - `response`
  - `local_action`
- `messageType`
- `eventKind`
- `payloadHex`
- `parsed`
- `notes`

Exemplo de `parsed`:

```json
{
  "presetName": "John Mayer/NDSP Fat US Clean",
  "activeSlot": "B",
  "presetIds": [12, 8, 7]
}
```

### 5.3 Regras da captura

- append-only durante a sessão
- arquivo legível por humano e por script
- falha de captura não derruba a conexão
- timestamps sempre registrados
- payload bruto sempre preservado quando disponível

## 6. Fluxo de Dados

1. usuário conecta o pedal físico
2. `PedalRepository.connect()` faz handshake e primeiro snapshot
3. `UsbPedalConnection` inicia loop de leitura runtime
4. eventos assíncronos entram em `runtimeEvents`
5. `PedalRepository` reconcilia o estado
6. `EventCaptureRecorder` grava cada evento em `JSONL`
7. `PedalViewModel` reflete o estado final para a UI

## 7. Tratamento de Erros

- `CRC` inválido, frame incompleto e timeout de leitura não derrubam imediatamente a sessão
- esses casos geram `TransportError`
- `Disconnected` só é emitido quando o transporte realmente fecha ou a sessão é encerrada
- `disconnect()` precisa cancelar explicitamente o loop de leitura para evitar coroutines órfãs
- se a escrita do arquivo falhar, o app registra erro de captura, mas mantém o pedal conectado

## 8. UI e Operação

A UI não vira ferramenta de protocolo; ela continua simples.

Adições mínimas previstas:

- ação para iniciar captura
- ação para parar captura
- indicação simples de sessão ativa
- acesso ao caminho ou listagem do arquivo gerado para leitura posterior

Não é necessário criar uma interface complexa de inspeção nesta fase. O alvo é produzir evidência confiável para análise manual posterior.

## 9. Estratégia de Teste

### Validação principal

O teste principal desta fase é **sempre com celular + pedal físico**.

Fluxo esperado:

1. iniciar captura
2. conectar pedal real
3. trocar presets no pedal e pelo app
4. encerrar captura
5. ler o arquivo `JSONL` depois
6. comparar payload bruto e campos interpretados

### Testes automatizados de suporte

Continuam úteis como rede de segurança:

- repositório aplicando `PresetDetailReceived`
- repositório aplicando `StateReceived`
- interrupção correta do stream ao desconectar
- não regressão de `connect()`, `selectSlot()` e `disconnect()`
- gravação `JSONL` com schema esperado

Esses testes não substituem o teste físico; apenas reduzem regressões locais.

## 10. Riscos e Mitigações

### Concorrência no transporte

Risco:
- dois consumidores lendo o mesmo transporte

Mitigação:
- centralizar leitura em um único loop no `UsbPedalConnection`

### Eventos parciais divergindo do snapshot

Risco:
- `0x0304` trazer só parte da verdade

Mitigação:
- tratar `0x0306` como fonte mais forte e `0x0304` como atualização incremental

### Captura crescer demais

Risco:
- sessões longas gerarem arquivos muito grandes

Mitigação:
- sessão explícita de início/fim e um arquivo por sessão

### Erro de gravação impactar uso ao vivo

Risco:
- falha de IO quebrar a conexão

Mitigação:
- isolamento do recorder e tratamento defensivo de falha

## 11. Resultado Esperado

Ao final desta fase, o app deve:

- reagir a eventos runtime do pedal físico sem depender apenas de `requestState()`
- produzir arquivos `JSONL` reaproveitáveis para depuração e engenharia reversa
- manter a arquitetura atual limpa e testável
- tornar o teste com hardware real a principal fonte de validação operacional
