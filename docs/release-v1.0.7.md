# TUSB v1.0.7

## Português Brasil

- A biblioteca do ToneX One deixa de mostrar nomes inventados a partir das cores dos slots.
- Na conexão, TUSB consulta os metadados dos 20 presets com pedidos ARM e associa cada nome ao ID correspondente.
- Presets sem resposta do pedal aparecem como não sincronizados; a consulta não envia COMMIT nem altera os slots ativos.
- Adicionada biblioteca local de snapshots completos com 109 parâmetros, busca, importação e exportação `.tusb-preset`.
- Corrigidos caminhos de nomes e validação dos parâmetros ao guardar e importar snapshots.

## English US

- The ToneX One library no longer derives fake preset names from slot colors.
- On connection, TUSB queries metadata for all 20 presets with ARM requests and maps each name to its preset ID.
- Presets without a pedal response are shown as not synchronized; the query sends no COMMIT and does not change active slots.
- Added a local library for full 109-parameter snapshots with search and `.tusb-preset` import/export.
- Corrected name handling and parameter validation when saving and importing snapshots.

## Español

- La biblioteca ToneX One ya no inventa nombres de presets a partir de los colores de los slots.
- Al conectarse, TUSB consulta los metadatos de los 20 presets con solicitudes ARM y asocia cada nombre con su ID.
- Los presets sin respuesta del pedal aparecen como no sincronizados; la consulta no envía COMMIT ni cambia los slots activos.
- Se agregó una biblioteca local de snapshots completos con 109 parámetros, búsqueda e importación/exportación `.tusb-preset`.
- Se corrigieron el manejo de nombres y la validación de parámetros al guardar e importar snapshots.

## Verification

- Android unit tests: `testDebugUnitTest` (135 tests).
- Physical validation on a Samsung SM-S911B connected to ToneX One (serial 200660): the app connects and shows the pedal slots, but the library still displays `nome não sincronizado` for presets 01 and 02. Metadata synchronization is not confirmed and remains a release-blocking defect.
- APK: `release/TUSB-v1.0.7-debug.apk` (20.82 MiB; 21,826,417 bytes).
- SHA-256: `c093cfacbeead94a2fb15f7bb5f63100bbb6e97a438ede42a6852ccc97a04935`.
- VirusTotal: [no security vendors flagged the file](https://www.virustotal.com/gui/file/c093cfacbeead94a2fb15f7bb5f63100bbb6e97a438ede42a6852ccc97a04935); some engines could not process the APK file type.

## Legal

TUSB is an independent community interoperability project and is not affiliated with, endorsed by, or sponsored by IK Multimedia. This repository is distributed under the MIT license.
