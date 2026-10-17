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
- Em Reverb, Modulação e Delay, escolha o modelo na faixa do topo do bloco (Spring 1–4,
  Room, Plate; Chorus, Tremolo, Phaser, Flanger, Rotary; Digital, Tape). Os knobs passam a
  controlar o modelo escolhido.

### Presets

- Use os slots A/B/C para trocar o preset ativo.
- Toque na estrela para marcar um preset como favorito.
- Ao editar o preset, classifique-o por instrumento, gênero e caráter.
- O botão de filtros, ao lado da busca, filtra por favoritos e classificações e ordena por
  número, nome ou favoritos. Favoritos e classificações ficam salvos só no celular.
- Em modo Stomp, A, B e C ficam disponíveis como cenas no pedal.
- Em modo A/B, A e B alternam presets e o terceiro switch controla bypass.
- Use Bypass para desligar o processamento e IR/Cab para alternar apenas a simulação de
  gabinete.

### Ferramentas

#### Metrônomo

- Use Play/Parar para iniciar ou interromper o clique.
- Ajuste o BPM (40–240) girando a roda, pelos botões − e + ou tocando TAP no ritmo.
- Escolha o compasso (2/4, 3/4, 4/4, 5/4, 6/8, 7/8), o timbre, o volume e se o primeiro
  tempo é acentuado.

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
- Desconecte o pedal quando terminar.

Os ajustes abaixo são gravados no próprio pedal e continuam valendo sem o app:

- **Volume:** volume master (0–10) e trim de entrada (−15 a +15 dB). Os sliders gravam ao
  soltar o dedo.
- **Afinador:** referência A4 (415–465 Hz) e o que a saída faz com o afinador do pedal
  ligado (mudo ou passar som). O afinador do pedal é ligado no próprio pedal.
- **Geral:** BPM global (40–240) e a fonte do tempo (global ou o BPM de cada preset).

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
- In Reverb, Modulation, and Delay, pick the model from the strip at the top of the block
  (Spring 1–4, Room, Plate; Chorus, Tremolo, Phaser, Flanger, Rotary; Digital, Tape). The
  knobs then control the chosen model.

### Presets

- Use A/B/C slots to switch the active preset.
- Tap the star to mark a preset as a favorite.
- In the preset editor, tag it by instrument, genre, and character.
- The filter button next to search filters by favorites and tags and sorts by number, name,
  or favorites. Favorites and tags are stored on the phone only.
- In Stomp mode, A, B, and C are available as pedal scenes.
- In A/B mode, A and B switch presets and the third footswitch controls bypass.
- Use Bypass to turn processing off and IR/Cab to toggle only cabinet simulation.

### Tools

#### Metronome

- Use Play/Stop to start or stop the click.
- Set the BPM (40–240) by turning the wheel, with the − and + buttons, or by tapping TAP in time.
- Choose the time signature (2/4, 3/4, 4/4, 5/4, 6/8, 7/8), the tone, the volume, and
  whether the first beat is accented.

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
- Disconnect the pedal when done.

The settings below are written to the pedal itself and keep working without the app:

- **Volume:** master volume (0–10) and input trim (−15 to +15 dB). The sliders write when
  you lift your finger.
- **Tuner:** A4 reference (415–465 Hz) and what the output does while the pedal tuner is on
  (mute or thru). The pedal tuner is turned on at the pedal.
- **General:** global BPM (40–240) and the tempo source (global or each preset's BPM).

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
- En Reverb, Modulación y Delay, elige el modelo en la franja superior del bloque (Spring
  1–4, Room, Plate; Chorus, Tremolo, Phaser, Flanger, Rotary; Digital, Tape). Los knobs
  pasan a controlar el modelo elegido.

### Presets

- Usa los slots A/B/C para cambiar el preset activo.
- Toca la estrella para marcar un preset como favorito.
- En la edición del preset, clasifícalo por instrumento, género y carácter.
- El botón de filtros, junto a la búsqueda, filtra por favoritos y clasificaciones y ordena
  por número, nombre o favoritos. Favoritos y clasificaciones se guardan solo en el teléfono.
- En modo Stomp, A, B y C quedan disponibles como escenas del pedal.
- En modo A/B, A y B alternan presets y el tercer footswitch controla bypass.
- Usa Bypass para apagar el procesamiento e IR/Cab para alternar solo la simulación de
  gabinete.

### Herramientas

#### Metrónomo

- Usa Play/Detener para iniciar o detener el click.
- Ajusta el BPM (40–240) girando la rueda, con los botones − y + o tocando TAP al ritmo.
- Elige el compás (2/4, 3/4, 4/4, 5/4, 6/8, 7/8), el timbre, el volumen y si el primer
  tiempo va acentuado.

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
- Desconecta el pedal al terminar.

Los ajustes siguientes se guardan en el propio pedal y siguen valiendo sin la app:

- **Volumen:** volumen master (0–10) y trim de entrada (−15 a +15 dB). Los sliders guardan
  al soltar el dedo.
- **Afinador:** referencia A4 (415–465 Hz) y qué hace la salida con el afinador del pedal
  activo (silencio o pasar sonido). El afinador del pedal se activa en el propio pedal.
- **General:** BPM global (40–240) y la fuente del tempo (global o el BPM de cada preset).
