#!/usr/bin/env node
// Lista los miembros (a 4 espacios) de un tipo grande de un .swift con su tramo de líneas (incluidos sus /// y @),
// marcando cuáles se pueden llevar a una `extension` en otro fichero (funciones y propiedades calculadas: las
// almacenadas, incluidos @State, no pueden vivir en una extensión).
// Uso: node scripts/swift-members.js <fichero.swift>
const fs = require("fs");
const L = fs.readFileSync(process.argv[2], "utf8").split(/\r?\n/);
const decl = /^    (?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|fileprivate|static|final|override|mutating|nonisolated)\s+)*(func|var|let|init|struct|enum|class|typealias)\b/;
const rows = [];
for (let i = 0; i < L.length; i++) {
  if (!decl.test(L[i])) continue;
  let s = i;
  while (s > 0 && /^    (\/\/\/|\/\/|@)/.test(L[s - 1])) s--;
  rows.push({ s, i, text: L[i].trim() });
}
rows.forEach((r, k) => {
  // fin: la línea antes del siguiente miembro (o del cierre del tipo), sin huecos al final
  let e = (k + 1 < rows.length ? rows[k + 1].s : L.findIndex((l, j) => j > r.i && /^\}/.test(l))) - 1;
  while (e > r.s && L[e].trim() === "") e--;
  const isFunc = /\bfunc\b|\binit\b/.test(r.text);
  const computed = /\bvar\b/.test(r.text) && /\{\s*$/.test(r.text) && !/=/.test(r.text.split("{")[0]);
  const movable = isFunc || computed ? "M" : " ";
  console.log(`${movable} ${String(r.s + 1).padStart(5)}-${String(e + 1).padEnd(5)} ${String(e - r.s + 1).padStart(4)}  ${r.text.slice(0, 90)}`);
});
