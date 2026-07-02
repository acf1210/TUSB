<p align="center">
  🇧🇷 <a href="#-instalação-português-brasil">Português Brasil</a> &nbsp;|&nbsp;
  🇺🇸 <a href="#-installation-english-us">English US</a> &nbsp;|&nbsp;
  🇪🇸 <a href="#-instalación-español">Español</a>
</p>

---

## 🇧🇷 Instalação (Português Brasil)

### Opção A - Instalar o APK pronto

1. Baixe o APK mais recente em [Releases](../../releases), arquivo `TUSB-v1.0.1.apk` ou mais novo.
2. Abra o APK no celular Android.
3. Se o Android pedir permissão para instalar apps de fontes desconhecidas, toque em
   **Permitir**.
4. Toque em **Instalar**.

> **iOS:** a versão para iOS está em desenvolvimento. Em breve.

### Opção B - Compilar a partir do código

Pré-requisitos: Android Studio ou JDK 17 + Android SDK com `ANDROID_HOME` configurado.

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Conexão inicial do ToneX One

1. Conecte o ToneX One ao celular com cabo USB-C OTG de dados.
2. Coloque o pedal em **modo Stomp**.
3. Pressione o pedal **três vezes** para preparar a conexão inicial.
4. Abra o TUSB e toque em **Conectar pedal via USB-C**.
5. Quando o Android pedir permissão USB, toque em **Permitir**.

### Problemas comuns

| Sintoma | Solução |
|---|---|
| O Android não pede permissão USB | Use cabo OTG de dados, não apenas cabo de carga. |
| A primeira conexão falha | Confirme modo Stomp, pressione o pedal três vezes e toque em conectar novamente. |
| "App não verificado" ao instalar | Esperado para APK fora da Play Store. Confira o resultado VirusTotal em `docs/VIRUSTOTAL.md`. |

---

## 🇺🇸 Installation (English US)

### Option A - Install the prebuilt APK

1. Download the latest APK from [Releases](../../releases), file `TUSB-v1.0.1.apk` or newer.
2. Open the APK on your Android phone.
3. If Android asks permission to install apps from unknown sources, tap **Allow**.
4. Tap **Install**.

> **iOS:** the iOS version is in development. Coming soon.

### Option B - Build from source

Requirements: Android Studio or JDK 17 + Android SDK with `ANDROID_HOME` configured.

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
```

The generated APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Initial ToneX One connection

1. Connect ToneX One to the phone with a data-capable USB-C OTG cable.
2. Put the pedal in **Stomp mode**.
3. Press the pedal **three times** to prepare the initial connection.
4. Open TUSB and tap **Connect pedal via USB-C**.
5. When Android asks for USB permission, tap **Allow**.

### Common issues

| Symptom | Fix |
|---|---|
| Android does not ask for USB permission | Use a data-capable OTG cable, not a charge-only cable. |
| First connection fails | Confirm Stomp mode, press the pedal three times, and tap connect again. |
| "App not verified" during install | Expected for APKs outside Play Store. Check the VirusTotal result in `docs/VIRUSTOTAL.md`. |

---

## 🇪🇸 Instalación (Español)

### Opción A - Instalar el APK listo

1. Descarga el APK más reciente en [Releases](../../releases), archivo `TUSB-v1.0.1.apk` o más nuevo.
2. Abre el APK en el teléfono Android.
3. Si Android pide permiso para instalar apps de fuentes desconocidas, toca **Permitir**.
4. Toca **Instalar**.

> **iOS:** la versión para iOS está en desarrollo. Próximamente.

### Opción B - Compilar desde el código

Requisitos: Android Studio o JDK 17 + Android SDK con `ANDROID_HOME` configurado.

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB
./gradlew assembleDebug
```

El APK generado queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Conexión inicial de ToneX One

1. Conecta ToneX One al teléfono con un cable USB-C OTG de datos.
2. Pon el pedal en **modo Stomp**.
3. Presiona el pedal **tres veces** para preparar la conexión inicial.
4. Abre TUSB y toca **Conectar pedal por USB-C**.
5. Cuando Android pida permiso USB, toca **Permitir**.

### Problemas comunes

| Síntoma | Solución |
|---|---|
| Android no pide permiso USB | Usa un cable OTG de datos, no solo de carga. |
| La primera conexión falla | Confirma modo Stomp, presiona el pedal tres veces y toca conectar de nuevo. |
| "App no verificada" al instalar | Esperado para APK fuera de Play Store. Revisa el resultado VirusTotal en `docs/VIRUSTOTAL.md`. |
