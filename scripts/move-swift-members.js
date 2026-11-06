#!/usr/bin/env node
// Lleva miembros de un tipo grande (funciones, propiedades calculadas, `static let`) a una `extension` del mismo tipo
// en un fichero NUEVO, para partirlo por áreas sin cambiar su comportamiento. Como una extensión en otro fichero no ve
// lo `private`, se quita `private`/`fileprivate` de los miembros del tipo (y de los tipos de nivel superior) en los
// dos ficheros: pasan a internos del módulo, que en una app es lo mismo a efectos prácticos.
//
// Uso: node scripts/move-swift-members.js <origen.swift> <Tipo> <destino.swift> "<cabecera>" desde-hasta [...]
const fs = require("fs");
const [src, type, dst, header, ...ranges] = process.argv.slice(2);
if (!src || !type || !dst || !ranges.length) { console.error("uso: origen Tipo destino cabecera desde-hasta..."); process.exit(1); }
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
const open = s => s
  .replace(/^(\s{4}(?:@\w+(?:\([^)]*\))?\s+)*)(?:private|fileprivate) /gm, "$1")
  .replace(/^(?:private|fileprivate) (final class|class|struct|enum|func|let|var) /gm, "$1 ");

const imports = lines.filter(l => /^import \w+/.test(l));
const out = [...imports, "", "// " + header, "", `extension ${type} {`, open(moved.join(eol + eol)), "}", ""].join(eol);
fs.writeFileSync(dst, out);

const kept = lines.filter((_, i) => !take.has(i)).join(eol).replace(/(\r?\n){3,}/g, eol + eol);
fs.writeFileSync(src, open(kept));
console.log(`${src}: ${lines.length} → ${kept.split(/\r?\n/).length} · ${dst}: ${out.split(/\r?\n/).length}`);
