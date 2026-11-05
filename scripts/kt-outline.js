#!/usr/bin/env node
// Enseña las declaraciones de nivel superior de un .kt con su tramo de líneas (incluido su KDoc y anotaciones),
// para decidir cómo partir un fichero grande con move-kt-blocks.js.
// Uso: node scripts/kt-outline.js <fichero.kt>
const fs = require("fs");
const L = fs.readFileSync(process.argv[2], "utf8").split(/\r?\n/);
const isDecl = l => /^(?:(?:private|internal|public|data|sealed|enum|abstract|open|inline|suspend|const|override)\s+)*(fun|class|object|val|var|interface|typealias)\b/.test(l);
const starts = [];
for (let i = 0; i < L.length; i++) {
  if (!isDecl(L[i])) continue;
  let s = i;   // sube por anotaciones y comentarios pegados
  while (s > 0 && /^(@|\/\*\*| \*|\s*\*\/|\/\/)/.test(L[s - 1])) s--;
  starts.push({ s, i, name: L[i].replace(/\{.*$/, "").slice(0, 90) });
}
starts.forEach((d, k) => {
  const end = (k + 1 < starts.length ? starts[k + 1].s : L.length) - 1;
  let e = end; while (e > d.s && L[e].trim() === "") e--;
  console.log(`${String(d.s + 1).padStart(5)}-${String(e + 1).padEnd(5)} ${String(e - d.s + 1).padStart(4)}  ${d.name}`);
});
