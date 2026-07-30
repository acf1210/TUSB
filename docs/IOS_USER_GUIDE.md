# Guia de uso do TUSB no iOS

O app iOS permite explorar a interface e o fluxo do ToneX One com um pedal
simulado. Os controles abaixo podem refletir o estado simulado, mas **não são
enviados por USB a um ToneX One conectado ao iPhone**.

## Antes de começar

- use iOS/iPadOS 17 ou superior;
- instale por Xcode, TestFlight ou App Store conforme
  [o guia de instalação](IOS_INSTALL.md);
- para avaliar sem hardware, abra o app no modo simulado;
- não presuma compatibilidade física: esta versão não foi validada em iPhone,
  iPad, ToneX One, controlador MIDI ou microfone reais.

## Conectar

1. abra a aba **Conectar**;
2. escolha **Modo simulado**;
3. aguarde o estado **Conectado**;
4. navegue para **Editor**, **Presets**, **Ferramentas** ou **Menu**;
5. use **Desconectar** ao terminar.

Se a tela mostrar uma opção USB, ela não contorna as restrições do iOS:

- iPhone não expõe API pública para CDC-ACM genérico;
- `ExternalAccessory` requer acessório/protocolo MFi autorizado;
- `USBDriverKit` não está disponível no iPhone e só atende iPad com chip M,
  mediante driver e entitlements.

## Editor

- ajuste **Bass**, **Mid**, **Treble**, **Gain** e **Volume**;
- toque em um bloco para abrir seus parâmetros;
- ligue ou desligue Gate, Compressor, Mod, Delay, Reverb e Cab;
- observe a atualização do estado simulado.

No modo simulado, alterações servem para demonstração e testes. Elas não alteram
um pedal físico.

## Presets

- selecione os slots **A**, **B** e **C**;
- alterne presets e os estados de **Bypass** e **IR/Cab**;
- use o estado simulado para conferir a navegação e o feedback visual.

Nenhuma alteração deve ser considerada salva no ToneX One sem confirmação em
hardware suportado.

## Ferramentas

### Metrônomo

1. abra **Ferramentas > Metrônomo**;
2. ajuste o BPM;
3. use **Play/Parar**;

Áudio e latência do Simulator não representam o aparelho real.

### Afinador

1. abra **Ferramentas > Afinador**;
2. permita o acesso ao microfone quando solicitado;
3. ajuste a referência A4 entre 430 e 450 Hz em **Menu**, se necessário;
4. toque uma corda e acompanhe nota e desvio;
5. pare o afinador quando terminar.

O Simulator pode não fornecer uma entrada de áudio representativa. Teste
afinação e latência em um aparelho físico antes de uso musical.

## MIDI

Na tela MIDI:

1. conecte um controlador class-compliant reconhecido pelo CoreMIDI;
2. toque em **Atualizar MIDI**;
3. selecione explicitamente uma única entrada;
4. escolha o canal MIDI de 1 a 16;
5. confirme o evento recebido antes de depender do mapa fixo abaixo.

Mapa funcional esperado:

| Mensagem | Ação |
|---|---|
| Program Change 0–19 | Presets 1–20 |
| CC 20 / 21 / 22 | Slot A / B / C |
| CC 23 / 24 | Próximo / preset anterior |
| CC 25 / 26 | Bypass geral / Cab |
| CC 27–32 | Gate, Comp, EQ, Mod, Delay e Reverb |
| CC 102–106 | Bass, Mid, Treble, Gain e Volume |

Toggles usam valor CC igual ou maior que 64; knobs usam 0–127.

MIDI via Bluetooth ou USB class-compliant é uma função diferente do transporte
USB CDC-ACM usado para controlar o ToneX One. Reconhecer um footswitch MIDI não
significa que o app possa abrir o ToneX One por USB.

## Diagnóstico

Ao relatar um problema, inclua:

- versão do app e origem da instalação;
- modelo do iPhone/iPad e versão do iOS/iPadOS;
- modo simulado ou hardware;
- controlador, cabo, hub e adaptadores usados;
- passos exatos, resultado esperado e observado;
- logs ou captura JSONL sem dados pessoais.

Não publique certificados, provisioning profiles, chaves, tokens, Apple ID ou
logs que contenham dados pessoais.

## Solução de problemas

| Sintoma | O que fazer |
|---|---|
| Não conecta no modo simulado | Feche e abra o app; reinstale o build se o problema persistir. |
| ToneX One não aparece no iPhone | Comportamento esperado: iPhone não oferece CDC-ACM genérico para apps. |
| `ExternalAccessory` não lista o pedal | O framework exige protocolo MFi autorizado pelo fabricante. |
| iPad M não abre o USB | Chip M sozinho não basta; esta versão não inclui uma solução DriverKit validada. |
| Controlador MIDI não aparece | Confirme que é class-compliant, energia, pareamento e permissões; teste em hardware, pois o Simulator é limitado. |
| Afinador sem sinal | Confira permissão de microfone, entrada selecionada e outro app usando o áudio. |
| Controles voltam ao estado inicial | Confirme que está no modo simulado; persistência no pedal físico não é suportada. |
| App fecha ou trava | Reabra, reproduza com poucos passos e envie diagnóstico com modelo e versão do sistema. |

## O que o Simulator comprova

O Simulator pode comprovar navegação, renderização, regras de estado, codec,
testes do núcleo e fluxos com o pedal simulado. Ele não comprova USB físico,
MFi, DriverKit, CoreMIDI físico, Bluetooth, microfone, latência, consumo de
energia, cabo, hub nem comportamento do ToneX One real.

Veja também a [matriz técnica e instruções de build](IOS.md).
