#!/usr/bin/env bash
# Lanza los recorridos de Maestro y, además, vigila los registros de Android: si la app se ha
# cerrado de golpe (crash) o se ha quedado colgada (ANR, «Cumbre no responde»), falla aunque
# los pasos hayan pasado, y guarda el informe en build/maestro/.
# Uso: scripts/run-maestro.sh [fichero.yaml | carpeta]   (por defecto, todo .maestro/)
set -uo pipefail
cd "$(dirname "$0")/.."

PKG=com.meteomontana.android
SDK="${ANDROID_SDK_ROOT:-/c/Users/rouma/AppData/Local/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"; [ -x "$ADB" ] || ADB=adb
MAESTRO="${MAESTRO:-/c/Users/rouma/tools/maestro/bin/maestro}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
export PATH="$JAVA_HOME/bin:$SDK/platform-tools:$PATH"
OUT=build/maestro; mkdir -p "$OUT"

"$ADB" logcat -c; "$ADB" logcat -b events -c 2>/dev/null
"$MAESTRO" test --format junit --output "$OUT/report.xml" "${1:-.maestro/}"
flows=$?

MSYS_NO_PATHCONV=1 "$ADB" logcat -d -b events | grep -E "am_anr|am_crash" | grep "$PKG" > "$OUT/problemas.txt"
MSYS_NO_PATHCONV=1 "$ADB" logcat -d | grep -A30 "FATAL EXCEPTION" | grep -B2 -A30 "$PKG" >> "$OUT/problemas.txt"

if [ -s "$OUT/problemas.txt" ]; then
  MSYS_NO_PATHCONV=1 "$ADB" shell dumpsys dropbox --print data_app_anr > "$OUT/anr-detalle.txt" 2>/dev/null
  echo "❌ La app se cerró o se quedó colgada durante las pruebas (detalle en $OUT/):"
  cat "$OUT/problemas.txt" | head -20
  exit 1
fi
[ $flows -eq 0 ] && echo "✅ Recorridos en verde y sin cierres ni cuelgues." || echo "❌ Algún recorrido ha fallado (informe en $OUT/report.xml)."
exit $flows
