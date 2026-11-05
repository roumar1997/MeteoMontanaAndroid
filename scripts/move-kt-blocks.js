#!/usr/bin/env node
// Mueve bloques de un .kt a un fichero NUEVO del mismo paquete, sin tocar su código (para partir ficheros grandes).
// Cada bloque = una declaración de nivel superior que empieza en `desde` (incluye su KDoc y anotaciones si
// `desde` apunta a ellas) y acaba justo antes del siguiente bloque o en `hasta`.
// El fichero nuevo lleva el mismo `package` y TODOS los imports del original (los que sobren son solo avisos).
// `private` de nivel superior pasa a `internal` en los dos ficheros, para que se sigan viendo entre ellos.
//
// Uso: node scripts/move-kt-blocks.js <origen.kt> <destino.kt> "<cabecera>" desde-hasta [desde-hasta ...]
const fs = require("fs");
const [src, dst, header, ...ranges] = process.argv.slice(2);
if (!src || !dst || !ranges.length) { console.error("uso: origen destino cabecera desde-hasta..."); process.exit(1); }
if (fs.existsSync(dst)) { console.error("ya existe " + dst); process.exit(1); }

const eol = fs.readFileSync(src, "utf8").includes("\r\n") ? "\r\n" : "\n";
const lines = fs.readFileSync(src, "utf8").split(/\r?\n/);
const take = new Set();
const moved = [];
for (const r of ranges) {
  const [a, b] = r.split("-").map(Number);
  for (let i = a; i <= b; i++) take.add(i - 1);
  moved.push(lines.slice(a - 1, b).join(eol));
}
const toInternal = s => s.replace(/^private (fun|class|val|const val|object|data class|enum class|sealed class|interface|var) /gm, "internal $1 ");

const pkg = lines.find(l => l.startsWith("package "));
const imports = lines.filter(l => l.startsWith("import "));
const out = [pkg, "", ...imports, "", "// " + header, "", toInternal(moved.join(eol + eol)), ""].join(eol);
fs.writeFileSync(dst, out);

const kept = lines.filter((_, i) => !take.has(i)).join(eol).replace(/(\r?\n){3,}/g, eol + eol);
fs.writeFileSync(src, toInternal(kept));
console.log(`${src}: ${lines.length} → ${kept.split(/\r?\n/).length} líneas · ${dst}: ${out.split(/\r?\n/).length}`);
