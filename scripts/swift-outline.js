#!/usr/bin/env node
// Enseña las declaraciones de nivel superior de un .swift con su tramo de líneas (incluidos sus /// y atributos),
// para decidir cómo partir un fichero grande con move-swift-blocks.js.
// Uso: node scripts/swift-outline.js <fichero.swift>
const fs = require("fs");
const L = fs.readFileSync(process.argv[2], "utf8").split(/\r?\n/);
const isDecl = l => /^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|fileprivate|public|final|internal)\s+)*(struct|class|enum|extension|func|let|var|protocol|typealias)\b/.test(l);
const starts = [];
for (let i = 0; i < L.length; i++) {
  if (!isDecl(L[i])) continue;
  let s = i;
  while (s > 0 && /^(\/\/\/|\/\/|@)/.test(L[s - 1])) s--;
  starts.push({ s, name: L[i].replace(/\{.*$/, "").slice(0, 80) });
}
starts.forEach((d, k) => {
  let e = (k + 1 < starts.length ? starts[k + 1].s : L.length) - 1;
  while (e > d.s && L[e].trim() === "") e--;
  console.log(`${String(d.s + 1).padStart(5)}-${String(e + 1).padEnd(5)} ${String(e - d.s + 1).padStart(4)}  ${d.name}`);
});
