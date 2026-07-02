<p align="center">
  🇧🇷 <a href="#-guia-de-uso-português-brasil">Português Brasil</a> &nbsp;|&nbsp;
  🇺🇸 <a href="#-user-guide-english-us">English US</a> &nbsp;|&nbsp;
  🇪🇸 <a href="#-guía-de-uso-español">Español</a>
</p>

---

## 🇧🇷 Guia de uso (Português Brasil)

> **iOS:** versão para iOS em desenvolvimento. Em breve.

### Conectar

Para a primeira conexão, coloque o ToneX One em **modo Stomp** e pressione o pedal
**três vezes** antes de tocar em **Conectar pedal via USB-C** no app. Depois conceda a
permissão USB do Android.

### Editor

- Ajuste Bass, Mid, Treble, Gain e Volume arrastando os knobs.
- O app envia os valores ao pedal em tempo real.
- Toque em um bloco da cadeia de efeitos para abrir seus parâmetros.
- Toque no LED do bloco para ligar/desligar Gate, Compressor, Mod, Delay, Reverb ou Cab.

### Presets

- Use os slots A/B/C para trocar o preset ativo.
- Em modo Stomp, A, B e C ficam disponíveis como cenas no pedal.
- Em modo A/B, A e B alternam presets e o terceiro switch controla bypass.
- Use Bypass para desligar o processamento e IR/Cab para alternar apenas a simulação de
  gabinete.

### Ferramentas

#### Metrônomo

- Use Play/Parar para iniciar ou interromper o clique.
- Ajuste BPM pelos botões, slider ou botão TAP.

#### Afinador

1. Abra **Ferramentas > Afinador**.
2. Toque em **Iniciar afinador** e permita o microfone.
3. Escolha a afinação: Standard, Drop D, meio tom abaixo, D Standard, Open G ou DADGAD.
4. Use **Auto** para o app escolher a corda mais próxima ou escolha a corda manualmente.
5. Toque a corda e ajuste até aparecer **Afinado**.

### MIDI

Controle o app (e o pedal) com um footswitch MIDI em **Menu > MIDI**.

#### M-Vave Chocolate (Bluetooth)

1. Ligue o Chocolate.
2. Toque em **Buscar dispositivos BLE** e conceda a permissão de Bluetooth.
3. Toque em **Conectar** no dispositivo encontrado.
4. De fábrica, o Chocolate envia Program Change 0–3, que carregam os presets 1–4.

#### Controlador USB MIDI

Conecte pedal e controlador ao celular pelo mesmo hub USB-C OTG e toque em
**Atualizar lista USB**.

#### Mapa padrão

| MIDI | Ação |
|---|---|
| PC 0–19 | Carrega preset 1–20 no slot ativo (fixo) |
| CC 20 / 21 / 22 | Slot A / B / C |
| CC 23 / 24 | Próximo / anterior preset |
| CC 25 | Bypass geral |
| CC 26 | Bypass Cab (IR) |
| CC 27–32 | Liga/desliga Gate, Comp, EQ, Mod, Delay, Reverb |
| CC 102–106 | Knobs Bass, Mid, Treble, Gain, Volume |

Toggles disparam com valor CC ≥ 64; os knobs usam a faixa completa 0–127.

#### MIDI Learn

1. Toque em **Learn** na ação desejada.
2. Pise no switch do footswitch: o CC recebido fica gravado para aquela ação.
3. **Restaurar mapa padrão** desfaz todas as personalizações.

### Menu

- Atualize o estado do pedal.
- Inicie ou pare captura JSONL para diagnóstico.
- Ajuste a referência A4 local entre 430 e 450 Hz.
- Desconecte o pedal quando terminar.

---

## 🇺🇸 User Guide (English US)

> **iOS:** iOS version in development. Coming soon.

### Connect

For the first connection, put ToneX One in **Stomp mode** and press the pedal **three
times** before tapping **Connect pedal via USB-C** in the app. Then allow Android USB
permission.

### Editor

- Adjust Bass, Mid, Treble, Gain, and Volume by dragging the knobs.
- The app sends values to the pedal in real time.
- Tap an effect-chain block to open its parameters.
- Tap the block LED to enable/disable Gate, Compressor, Mod, Delay, Reverb, or Cab.

### Presets

- Use A/B/C slots to switch the active preset.
- In Stomp mode, A, B, and C are available as pedal scenes.
- In A/B mode, A and B switch presets and the third footswitch controls bypass.
- Use Bypass to turn processing off and IR/Cab to toggle only cabinet simulation.

### Tools

#### Metronome

- Use Play/Stop to start or stop the click.
- Adjust BPM with buttons, slider, or TAP.

#### Tuner

1. Open **Tools > Tuner**.
2. Tap **Start tuner** and allow microphone access.
3. Choose a tuning: Standard, Drop D, half-step down, D Standard, Open G, or DADGAD.
4. Use **Auto** so the app chooses the nearest string, or pick a string manually.
5. Play the string and adjust until **In tune** appears.

### MIDI

Control the app (and the pedal) with a MIDI footswitch in **Menu > MIDI**.

#### M-Vave Chocolate (Bluetooth)

1. Turn the Chocolate on.
2. Tap **Scan BLE devices** and grant the Bluetooth permission.
3. Tap **Connect** on the discovered device.
4. From the factory, the Chocolate sends Program Change 0–3, which load presets 1–4.

#### USB MIDI controller

Connect the pedal and the controller to the phone through the same USB-C OTG hub and tap
**Refresh USB list**.

#### Default map

| MIDI | Action |
|---|---|
| PC 0–19 | Loads preset 1–20 into the active slot (fixed) |
| CC 20 / 21 / 22 | Slot A / B / C |
| CC 23 / 24 | Next / previous preset |
| CC 25 | Global bypass |
| CC 26 | Cab (IR) bypass |
| CC 27–32 | Toggle Gate, Comp, EQ, Mod, Delay, Reverb |
| CC 102–106 | Bass, Mid, Treble, Gain, Volume knobs |

Toggles fire with CC value ≥ 64; knobs use the full 0–127 range.

#### MIDI Learn

1. Tap **Learn** on the desired action.
2. Press the footswitch: the received CC is stored for that action.
3. **Restore default mapping** undoes all customizations.

### Menu

- Refresh the pedal state.
- Start or stop JSONL capture for diagnostics.
- Adjust the local A4 reference between 430 and 450 Hz.
- Disconnect the pedal when done.

---

## 🇪🇸 Guía de uso (Español)

> **iOS:** versión para iOS en desarrollo. Próximamente.

### Conectar

Para la primera conexión, pon ToneX One en **modo Stomp** y presiona el pedal **tres
veces** antes de tocar **Conectar pedal por USB-C** en la app. Luego permite el acceso USB
de Android.

### Editor

- Ajusta Bass, Mid, Treble, Gain y Volume arrastrando los knobs.
- La app envía los valores al pedal en tiempo real.
- Toca un bloque de la cadena de efectos para abrir sus parámetros.
- Toca el LED del bloque para activar/desactivar Gate, Compressor, Mod, Delay, Reverb o Cab.

### Presets

- Usa los slots A/B/C para cambiar el preset activo.
- En modo Stomp, A, B y C quedan disponibles como escenas del pedal.
- En modo A/B, A y B alternan presets y el tercer footswitch controla bypass.
- Usa Bypass para apagar el procesamiento e IR/Cab para alternar solo la simulación de
  gabinete.

### Herramientas

#### Metrónomo

- Usa Play/Detener para iniciar o detener el click.
- Ajusta BPM con botones, slider o TAP.

#### Afinador

1. Abre **Herramientas > Afinador**.
2. Toca **Iniciar afinador** y permite el micrófono.
3. Elige una afinación: Standard, Drop D, medio tono abajo, D Standard, Open G o DADGAD.
4. Usa **Auto** para que la app elija la cuerda más cercana, o elige una cuerda manualmente.
5. Toca la cuerda y ajusta hasta que aparezca **Afinado**.

### MIDI

Controla la app (y el pedal) con un footswitch MIDI en **Menú > MIDI**.

#### M-Vave Chocolate (Bluetooth)

1. Enciende el Chocolate.
2. Toca **Buscar dispositivos BLE** y concede el permiso de Bluetooth.
3. Toca **Conectar** en el dispositivo encontrado.
4. De fábrica, el Chocolate envía Program Change 0–3, que cargan los presets 1–4.

#### Controlador USB MIDI

Conecta el pedal y el controlador al teléfono mediante el mismo hub USB-C OTG y toca
**Actualizar lista USB**.

#### Mapa por defecto

| MIDI | Acción |
|---|---|
| PC 0–19 | Carga el preset 1–20 en el slot activo (fijo) |
| CC 20 / 21 / 22 | Slot A / B / C |
| CC 23 / 24 | Siguiente / anterior preset |
| CC 25 | Bypass general |
| CC 26 | Bypass Cab (IR) |
| CC 27–32 | Activa/desactiva Gate, Comp, EQ, Mod, Delay, Reverb |
| CC 102–106 | Knobs Bass, Mid, Treble, Gain, Volume |

Los toggles se disparan con valor CC ≥ 64; los knobs usan el rango completo 0–127.

#### MIDI Learn

1. Toca **Learn** en la acción deseada.
2. Pisa el switch del footswitch: el CC recibido queda guardado para esa acción.
3. **Restaurar mapa por defecto** deshace todas las personalizaciones.

### Menú

- Actualiza el estado del pedal.
- Inicia o detén la captura JSONL para diagnóstico.
- Ajusta la referencia A4 local entre 430 y 450 Hz.
- Desconecta el pedal al terminar.
