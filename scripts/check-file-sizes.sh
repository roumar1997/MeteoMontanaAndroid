#!/usr/bin/env bash
# Freno de tamaño (ARCHITECTURE.md §2): ningún fichero de las apps pasa de 600 líneas.
# Los que YA se pasaban están en config/oversized-baseline.txt con su tamaño de hoy: pueden ENCOGER, nunca crecer.
# Cuando uno baja del límite hay que quitarlo de la lista (el script lo exige), así la lista solo se acorta.
# Uso: scripts/check-file-sizes.sh            (comprueba)
#      scripts/check-file-sizes.sh --list     (enseña los que se pasan, para rehacer la lista)
set -euo pipefail
cd "$(dirname "$0")/.."
LIMIT=600
BASELINE=config/oversized-baseline.txt

files() { git ls-files 'app/src/main/*.kt' 'shared/src/*.kt' 'iosApp/*.swift'; }
count() { wc -l < "$1" | tr -d ' '; }

if [[ "${1:-}" == "--list" ]]; then
  files | while read -r f; do n=$(count "$f"); (( n > LIMIT )) && echo "$n $f"; done | sort -rn
  exit 0
fi

declare -A allowed=()
while read -r n f; do [[ -z "${n:-}" || "$n" == \#* ]] && continue; allowed["$f"]=$n; done < "$BASELINE"

fail=0
while read -r f; do
  n=$(count "$f")
  if [[ -n "${allowed[$f]:-}" ]]; then
    if (( n > allowed[$f] )); then
      echo "❌ $f ha CRECIDO: $n líneas (antes ${allowed[$f]}). Pártelo en vez de añadirle más."; fail=1
    elif (( n <= LIMIT )); then
      echo "❌ $f ya baja del límite ($n). Quítalo de $BASELINE para que no vuelva a crecer."; fail=1
    fi
  elif (( n > LIMIT )); then
    echo "❌ $f tiene $n líneas (límite $LIMIT). Pártelo por responsabilidades (ARCHITECTURE.md §2)."; fail=1
  fi
done < <(files)

for f in "${!allowed[@]}"; do
  [[ -f "$f" ]] || { echo "❌ $f ya no existe: quítalo de $BASELINE."; fail=1; }
done

(( fail == 0 )) && echo "✅ Tamaños bien: nada nuevo por encima de $LIMIT líneas y nada ha crecido (${#allowed[@]} pendientes de partir)."
exit $fail
