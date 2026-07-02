<p align="center">🇧🇷 <a href="#-instalação-português">Português</a> &nbsp;|&nbsp; 🇺🇸 <a href="#-installation-english">English</a></p>

---

## 🇧🇷 Instalação (Português)

### Opção A — Instalar o APK pronto (mais fácil)

1. Baixe o APK mais recente na página de
   [Releases](../../releases) do repositório (`TUSB-vX.Y.Z.apk`).
2. No celular Android, abra o arquivo baixado. Se aparecer um aviso de
   "instalar apps de fontes desconhecidas", toque em **Permitir** — isso é
   esperado, pois o app não vem da Play Store.
3. Toque em **Instalar** e aguarde.

> **Nota:** este APK é assinado com a chave de debug padrão do Android (prática comum em
> projetos de comunidade sem infraestrutura de assinatura de release). Ele funciona
> normalmente, mas o Android pode mostrar um aviso genérico de "app não verificado" — isso
> não indica um problema de segurança, apenas que o app não passou pela revisão da Play
> Store.

### Opção B — Compilar a partir do código-fonte

Pré-requisitos: [Android Studio](https://developer.android.com/studio) (ou JDK 17 + Android
SDK com `ANDROID_HOME` configurado) e o pedal **não** precisa estar conectado durante a
compilação.

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`. Instale no celular com:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(`adb` funciona tanto por cabo USB quanto por Wi-Fi — veja `adb pair`/`adb connect` se
preferir sem cabo.)

### Conectando o pedal

1. Conecte o **ToneX One** ao celular usando um **cabo USB-C OTG** (o celular precisa
   suportar modo host USB — a maioria dos Android modernos suporta).
2. Abra o app TUSB e toque em **Conectar**.
3. O Android vai pedir permissão para o app acessar o dispositivo USB — toque em
   **Permitir**. Marque "usar sempre para este dispositivo" para não precisar confirmar
   de novo.
4. O app faz o handshake automaticamente (pode levar alguns segundos na primeira conexão)
   e mostra o firmware do pedal na barra superior quando conectar.

### Problemas comuns

| Sintoma | Solução |
|---|---|
| App não pede permissão USB | Verifique se o cabo é OTG (dados), não só de carga. |
| Conexão demora ou falha na 1ª tentativa | Normal — o app tenta reconectar automaticamente várias vezes. Toque em Conectar de novo se falhar. |
| "App não verificado" ao instalar | Esperado para APKs fora da Play Store; veja a nota acima. |

---

## 🇺🇸 Installation (English)

### Option A — Install the prebuilt APK (easiest)

1. Download the latest APK from the repository's
   [Releases](../../releases) page (`TUSB-vX.Y.Z.apk`).
2. On your Android phone, open the downloaded file. If you see a
   "install apps from unknown sources" warning, tap **Allow** — this is
   expected since the app isn't distributed through the Play Store.
3. Tap **Install** and wait.

> **Note:** this APK is signed with Android's default debug key (common practice for
> community projects without release-signing infrastructure). It works normally, but
> Android may show a generic "app not verified" warning — that doesn't indicate a security
> problem, just that the app hasn't gone through Play Store review.

### Option B — Build from source

Prerequisites: [Android Studio](https://developer.android.com/studio) (or JDK 17 + Android
SDK with `ANDROID_HOME` set). The pedal does **not** need to be connected while building.

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
```

The generated APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(`adb` works over USB cable or Wi-Fi — see `adb pair`/`adb connect` for a cable-free setup.)

### Connecting the pedal

1. Connect the **ToneX One** to your phone using a **USB-C OTG cable** (your phone needs to
   support USB host mode — most modern Android phones do).
2. Open the TUSB app and tap **Connect**.
3. Android will ask for permission for the app to access the USB device — tap **Allow**.
   Check "always use for this device" so you don't have to confirm again.
4. The app performs the handshake automatically (may take a few seconds on the first
   connection) and shows the pedal's firmware version in the top bar once connected.

### Common issues

| Symptom | Fix |
|---|---|
| App doesn't ask for USB permission | Make sure the cable is OTG (data), not charge-only. |
| Connection is slow or fails on the 1st try | Normal — the app retries automatically several times. Tap Connect again if it fails. |
| "App not verified" on install | Expected for APKs outside the Play Store; see note above. |
