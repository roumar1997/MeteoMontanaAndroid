#!/usr/bin/env node
// Mueve tramos de líneas de un .swift a un fichero NUEVO (para partir ficheros grandes), sin tocar su código.
// El nuevo lleva los mismos `import` que el original. Los tipos y funciones `private`/`fileprivate` de NIVEL SUPERIOR
// pasan a internos en los dos ficheros, para que se sigan viendo entre ellos (las `private extension` se dejan: suelen
// repetirse en varios ficheros con el mismo nombre y chocarían).
//
// Uso: node scripts/move-swift-blocks.js <origen.swift> <destino.swift> "<cabecera>" desde-hasta [desde-hasta ...]
const fs = require("fs");
const [src, dst, header, ...ranges] = process.argv.slice(2);
if (!src || !dst || !ranges.length) { console.error("uso: origen destino cabecera desde-hasta..."); process.exit(1); }
if (fs.existsSync(dst)) { console.error("ya existe " + dst); process.exit(1); }

const raw = fs.readFileSync(src, "utf8");
const eol = raw.includes("\r\n") ? "\r\n" : "\n";
const lines = raw.split(/\r?\n/);
const take = new Set();
const moved = [];
for (const r of ranges) {
  const [a, b] = r.split("-").map(Number);
  for (let i = a; i <= b; i++) take.add(i - 1);
  moved.push(lines.slice(a - 1, b).join(eol));
}
const toInternal = s => s.replace(/^(?:private|fileprivate) (final class|class|struct|enum|func|let|var) /gm, "$1 ");

const imports = lines.filter(l => /^import \w+/.test(l));
const out = [...imports, "", "// " + header, "", toInternal(moved.join(eol + eol)), ""].join(eol);
fs.writeFileSync(dst, out);

const kept = lines.filter((_, i) => !take.has(i)).join(eol).replace(/(\r?\n){3,}/g, eol + eol);
fs.writeFileSync(src, toInternal(kept));
console.log(`${src}: ${lines.length} → ${kept.split(/\r?\n/).length} · ${dst}: ${out.split(/\r?\n/).length}`);
