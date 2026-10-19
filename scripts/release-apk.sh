#!/usr/bin/env bash
# Gera o APK de release assinado a partir de um commit, sem depender de copiar a chave.
#
# Uso: scripts/release-apk.sh <git-ref> <versao>
#   ex.: scripts/release-apk.sh HEAD 1.0.9
#
# A chave fica FORA do repositorio (padrao: ~/.config/tusb/release.keystore, modo 600).
# O script recusa assinar se o certificado nao for o das releases publicadas, para que
# quem ja tem o TUSB instalado consiga atualizar por cima.
#
# Variaveis opcionais:
#   TUSB_KEYSTORE   caminho da chave (padrao acima)
#   TUSB_SDK        Android SDK (padrao ~/.cache/tusb-android-sdk)
#   TUSB_BUILD_DIR  pasta de build (padrao ~/tusb-release-<versao>)
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "uso: $0 <git-ref> <versao>" >&2
  exit 2
fi
REF="$1"
VERSION="$2"
if [[ ! "$VERSION" =~ ^[0-9]+(\.[0-9]+)+$ ]]; then
  echo "versao invalida: $VERSION" >&2
  exit 2
fi

KEYSTORE="${TUSB_KEYSTORE:-$HOME/.config/tusb/release.keystore}"
SDK="${TUSB_SDK:-$HOME/.cache/tusb-android-sdk}"
BUILD_DIR="${TUSB_BUILD_DIR:-$HOME/tusb-release-$VERSION}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
# Certificado das releases publicadas (v1.0.0 em diante).
EXPECTED_CERT="615c21457dc75785769f748ace7a506d3cf1191b61f6fb0ce5588ae43717168c"
KEY_ALIAS="androiddebugkey"
KEY_PASS="android"

[[ -r "$KEYSTORE" ]] || { echo "chave nao encontrada: $KEYSTORE" >&2; exit 1; }
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[[ -n "$BUILD_TOOLS" ]] || { echo "build-tools nao encontrado em $SDK" >&2; exit 1; }

COMMIT="$(git -C "$REPO" rev-parse --verify "$REF^{commit}")"
echo "commit $COMMIT -> $BUILD_DIR"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
git -C "$REPO" archive "$COMMIT" | tar -x -C "$BUILD_DIR"
echo "sdk.dir=$SDK" > "$BUILD_DIR/local.properties"

BUILT_VERSION="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$BUILD_DIR/app/build.gradle.kts")"
if [[ "$BUILT_VERSION" != "$VERSION" ]]; then
  echo "o commit tem versionName $BUILT_VERSION, esperado $VERSION" >&2
  exit 1
fi

(cd "$BUILD_DIR" && bash ./gradlew --no-daemon -q testDebugUnitTest assembleDebug)

OUT="$BUILD_DIR/TUSB-v$VERSION.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/app/build/outputs/apk/debug/app-debug.apk" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEY_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" --out "$OUT" "$BUILD_DIR/aligned.apk"

CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [[ "$CERT" != "$EXPECTED_CERT" ]]; then
  rm -f "$OUT"
  echo "certificado inesperado ($CERT): o APK nao atualizaria as instalacoes existentes" >&2
  exit 1
fi

echo "APK: $OUT"
echo "SHA-256: $(sha256sum "$OUT" | cut -d' ' -f1 | tr '[:lower:]' '[:upper:]')"
echo "Size: $(stat -c %s "$OUT") bytes"
