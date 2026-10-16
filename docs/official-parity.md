# TONEX Control feature parity

This document compares the official Android TONEX Control listing (checked 2026-10-14)
with TUSB's verified behavior for the ToneX One V1 over USB CDC. It is a working
implementation checklist, not a claim of full compatibility with TONEX ONE+ or TONEX Plug.

Official feature reference: [TONEX Control on Google Play](https://play.google.com/store/apps/details?id=com.ikmultimediaus.android.tonexcontrol).

## Feature status

| Official feature | TUSB status | Evidence / remaining work |
|---|---|---|
| Real-time amp, cab, IR and EQ editing | Implemented in Android UI and 0x0309 parameter writes | Editing paths and parameter bindings are in `PedalViewModel` and `TonexParam`; validate all controls on physical hardware, not only the already-tested amp knobs. |
| Noise gate, compressor, modulation, delay and reverb | Implemented with parameter/model bindings | Requires complete device-side bench coverage for each model and control. |
| Onboard preset and slot management | Partial | A/B/C switching and loading a library preset into a slot are implemented. Startup now requests metadata for all 20 library entries using ARM-only requests and labels entries without returned names as unknown. Physical validation of the name sweep and a permanent hardware save command are still missing. |
| Unlimited presets stored on mobile | Partial | TUSB saves and searches full 109-parameter snapshots in an app-private library and imports/exports `.tusb-preset` files. Folders and local rename/duplicate actions are still missing. |
| Organize, assign and back up tones | Partial | Slot assignment and local snapshot import/export are available. Hardware rename and permanent device backup/restore remain unavailable. |
| ToneNET search, demo and download | Partial | ToneNET site opens in a restricted WebView. Native search/download/import and transfer of a downloaded model to the ToneX One are not implemented. |
| Wireless TONEX ONE+ control | Unsupported target | TUSB currently implements the original ToneX One USB CDC transport. BLE control requires the ONE+ protocol and hardware validation. |
| TONEX Plug library integration | Unsupported target | No verified Plug transport or sync protocol exists in this project. |
| Built-in tuner | Implemented locally | Microphone pitch detection and alternate tunings are available; it does not use a pedal tuner protocol. |
| Built-in metronome | Implemented locally | BPM, tap tempo and audible click are available. |
| Fast sync across TONEX ecosystem | Partial | USB connection and state refresh work. Full ecosystem account/library sync is absent. |

## Confirmed boundaries

- The active preset's parameter block and parameter writes are supported by the observed
  `0x0304` and `0x0309` messages. A permanent save command has not been identified.
- The official APK artifacts include factory `.txp` files, but their contents are not yet
  decoded as a supported import format. Their presence alone does not establish that TUSB
  can install them on the ToneX One.
- Do not label synthetic preset names as pedal-provided names. Do not claim a preset is
  saved to hardware unless a confirmed save operation and hardware test exist.
- ToneNET browsing does not imply account login, downloading, importing, or pedal transfer.
- TUSB's 109-entry value validation follows the parameter order and published limits in
  [Builty/TonexOneController](https://github.com/Builty/TonexOneController), whose
  `tonex_params.h` and `tonex_params.c` are distributed under Apache License 2.0. The
  matching exact limits still need validation against each supported pedal firmware.

## Work sequence

1. Validate the 20-entry metadata sweep on physical ToneX One hardware and confirm it
   never changes the active slot or preset parameters.
2. Add local preset search, rename, duplicate and organization while keeping snapshots
   distinct from hardware slots.
3. Expand ToneNET only through documented public flows and compatible model formats.
4. Investigate permanent save, full library sync, ONE+ BLE and Plug sync from captures or
   official protocol documentation, then validate each hardware write before exposing it.

Android remains canonical. Do not begin an iOS port before the Android behavior is validated
and pushed.
