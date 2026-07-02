<p align="center">🇧🇷 <a href="#-guia-de-uso-português">Português</a> &nbsp;|&nbsp; 🇺🇸 <a href="#-user-guide-english">English</a></p>

---

## 🇧🇷 Guia de uso (Português)

### Visão geral das telas

O app tem 4 seções principais, acessíveis pela barra inferior (ou lateral, em tablets):

- **Editor** — amplificador, cadeia de efeitos e knobs do preset ativo.
- **Presets** — troca de slot (A/B/C), biblioteca de presets e modo A/B ⇄ Stomp.
- **Tools** — ferramentas auxiliares.
- **Menu** — captura de eventos, ajustes globais e informações de conexão.

A barra superior mostra a marca do app e, quando conectado, a **versão de firmware** do
pedal (`FW x.x.x`).

### Editor

- **Knobs do amplificador** (Bass, Mid, Treble, Gain, Volume): arraste verticalmente para
  ajustar. O valor escrito no pedal é em tempo real — não precisa confirmar. Se você girar o
  knob **físico** no próprio pedal, o knob virtual acompanha sozinho.
- **Cadeia de efeitos**: cada bloco (Gate, Comp, EQ, Mod, Delay, Reverb, Cab) tem um indicador
  verde/apagado mostrando se está ativo. Toque no bloco para abrir o detalhe.
- **Detalhe de um bloco de efeito**: switch **"Bloco ativo"** liga/desliga o efeito no pedal
  na hora; os 3 sliders controlam os parâmetros reais daquele bloco (os nomes mudam conforme
  o efeito — ex.: Threshold/Depth/Release no Gate, Rate/Depth/Mix na Modulação); chaves
  PRE/POST definem a posição do efeito na cadeia; o Delay tem ainda SYNC e NORMAL/PING-PONG.
  Para reverb, modulação e delay, os parâmetros mostrados dependem do **modelo ativo no
  preset** (ex.: Spring vs. Room no reverb) — o app resolve isso automaticamente.

### Presets

- Toque em um dos **slots A/B/C** para trocar o preset ativo do pedal.
- A **biblioteca de presets** lista os presets salvos no pedal; toque em um para carregá-lo no
  slot ativo.
- **A/B ⇄ Stomp**: alterna o modo de operação do pedal (2 presets rápidos vs. 3 footswitches).
- **Bypass**: desliga o processamento do pedal (sinal passa direto). **IR/Cab**: liga/desliga
  só a simulação de gabinete, mantendo o resto da cadeia ativo.

### Menu

- **Iniciar/Parar captura**: grava um log (`.jsonl`) de todos os comandos e respostas trocados
  com o pedal — útil para diagnóstico ou para reportar um problema.
- **Atualizar estado**: força uma releitura do estado completo do pedal.
- Ajustes de referência de afinação (A4) e volume mestre (ainda locais ao app; a escrita
  desses campos específicos no pedal está em mapeamento).

### Dicas

- O app reflete o pedal em tempo real nos dois sentidos: qualquer ajuste feito diretamente no
  hardware (knob físico, footswitch) aparece no app sem precisar atualizar manualmente.
- Se a conexão cair, toque em **Conectar** novamente — o app tenta o handshake automaticamente
  várias vezes antes de desistir.

---

## 🇺🇸 User Guide (English)

### Screen overview

The app has 4 main sections, reachable from the bottom bar (or side rail, on tablets):

- **Editor** — amp, effect chain, and knobs for the active preset.
- **Presets** — slot switching (A/B/C), preset library, and A/B ⇄ Stomp mode.
- **Tools** — auxiliary tools.
- **Menu** — event capture, global settings, and connection info.

The top bar shows the app brand and, once connected, the pedal's **firmware version**
(`FW x.x.x`).

### Editor

- **Amp knobs** (Bass, Mid, Treble, Gain, Volume): drag vertically to adjust. The value is
  written to the pedal in real time — no confirmation needed. If you turn the **physical**
  knob on the pedal itself, the virtual knob follows automatically.
- **Effect chain**: each block (Gate, Comp, EQ, Mod, Delay, Reverb, Cab) shows a green/off
  indicator for whether it's active. Tap a block to open its detail screen.
- **Effect block detail**: the **"Block active"** switch toggles the effect on the pedal
  instantly; the 3 sliders control that block's real parameters (labels change per effect —
  e.g. Threshold/Depth/Release for Gate, Rate/Depth/Mix for Modulation); PRE/POST chips set
  the effect's position in the chain; Delay also has SYNC and NORMAL/PING-PONG. For reverb,
  modulation, and delay, the parameters shown depend on the **active model in the preset**
  (e.g. Spring vs. Room reverb) — the app resolves this automatically.

### Presets

- Tap one of the **A/B/C slots** to switch the pedal's active preset.
- The **preset library** lists presets saved on the pedal; tap one to load it into the active
  slot.
- **A/B ⇄ Stomp**: switches the pedal's operating mode (2 quick presets vs. 3 footswitches).
- **Bypass**: turns off pedal processing (signal passes through unaffected). **IR/Cab**:
  toggles only the cabinet simulation, keeping the rest of the chain active.

### Menu

- **Start/Stop capture**: records a log (`.jsonl`) of every command and response exchanged
  with the pedal — useful for diagnostics or bug reports.
- **Refresh state**: forces a full re-read of the pedal's state.
- Tuning reference (A4) and master volume adjustments (still local to the app; writing these
  specific fields to the pedal is still being mapped).

### Tips

- The app mirrors the pedal in real time in both directions: any adjustment made directly on
  the hardware (physical knob, footswitch) shows up in the app without a manual refresh.
- If the connection drops, tap **Connect** again — the app automatically retries the
  handshake several times before giving up.
