import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Fila de cada vía del formulario de piedra y la hoja para reordenar las caras.

/// Fila de un bloque/vía (nombre + grado + tipo de inicio) compartida por el
/// formulario de piedra nueva y el de añadir vías. Espejo de AddLineRow.
struct BoulderBlockRow: View {
    @Binding var block: BoulderBlockForm
    let index: Int
    /// Número a mostrar (numeración global del muro). nil = index+1 local.
    var number: Int? = nil
    var onDelete: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("\(number ?? index + 1)").font(Cumbre.mono(11, .bold))
                    .foregroundStyle(GradeColor.style(block.grade).dark ? .black : .white)
                    .frame(width: 24, height: 24)
                    .background(Circle().fill(GradeColor.color(block.grade)))
                TextField("Nombre (opcional)", text: $block.name).font(.system(size: 14))
                    .padding(8).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                if let onDelete {
                    Button(action: onDelete) {
                        Image(systemName: "xmark").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            // Grado: GRID de chips (todos visibles, un toque) — espejo de
            // GradeChipsGrid de Android. Colores por dificultad (GradeColor).
            HStack(spacing: 4) {
                Text("Grado").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                Text("· toca 2 para un rango (7a/7a+)").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 5)], spacing: 5) {
                ForEach(BOULDER_GRADES, id: \.self) { g in
                    let on = GradeRangeUI.contains(block.grade, g)
                    // chip (no color): fuera de la foto, el negro de ≥8a y el
                    // blanco de ≤5c+ no se ven en modo oscuro.
                    let c = GradeColor.chip(g)
                    Button { block.grade = GradeRangeUI.toggle(block.grade, g) } label: {
                        Text(g).font(.system(size: 13, weight: .medium))
                            .foregroundStyle(on ? (GradeColor.style(g).dark ? Color.black : .white) : Cumbre.ink)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 7)
                            .background(on ? c : c.opacity(0.16))
                            .clipShape(RoundedRectangle(cornerRadius: 9))
                            .overlay(RoundedRectangle(cornerRadius: 9)
                                .stroke(on ? Cumbre.ink : c.opacity(0.55), lineWidth: on ? 2 : 1))
                    }.buttonStyle(.plain)
                }
            }
            // Tipo de inicio con nombre completo (antes: siglas sin explicar).
            // REJILLA, no HStack: con 5 chips en una fila fija, "Travesía" y
            // "Sentado" se cortaban en los iPhone estrechos (13/14 mini y
            // similares) — el ancho disponible no daba y SwiftUI truncaba
            // (Álvaro, 2026-08-24). Misma rejilla adaptativa que el grado, así
            // que fluye a 2 o 3 columnas según la pantalla y nunca corta.
            Text("Tipo de inicio").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 5)], spacing: 5) {
                ForEach(START_TYPE_LABELS, id: \.0) { code, label in
                    let on = block.startType == code
                    Button { block.startType = on ? nil : code } label: {
                        Text(label).font(.system(size: 12, weight: .medium))
                            .lineLimit(1).minimumScaleFactor(0.8)
                            .foregroundStyle(on ? Cumbre.bg : Cumbre.ink)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 6).padding(.vertical, 7)
                            .background(on ? Cumbre.ink : Cumbre.paper)
                            .clipShape(RoundedRectangle(cornerRadius: 9))
                            .overlay(RoundedRectangle(cornerRadius: 9).stroke(Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain)
                }
            }
            // Descripción opcional (beta, salida, detalle a especificar).
            // Caja visible (borde) para que se vea que es un campo editable.
            TextField("Variante (opcional)", text: $block.variant)
                .onChange(of: block.variant) { _, new in
                    if new.count > 60 { block.variant = String(new.prefix(60)) }
                }
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
            Text("Si esta vía es una variante de otra con el mismo nombre (directa, extensión, desde el pie…). Se mostrará como «Nombre (variante)».")
                .font(.system(size: 12))
                .foregroundStyle(Cumbre.ink3)
            TextField("Descripción (opcional)", text: $block.descriptionText, axis: .vertical)
                .font(.system(size: 13))
                .lineLimit(1...3)
                .padding(8).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            if !block.line.isEmpty {
                Text("✓ línea dibujada").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ok)
            }
        }
        .padding(10)
        .background(Cumbre.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Códigos de tipo de inicio → etiqueta legible (espejo de GradePickers.kt).

/// Panel para reordenar las fotos viéndolas: cada fila = posición + nº de vías +
/// subir/bajar; tocar despliega la foto con sus líneas. En muro el orden define
/// la numeración global. Espejo del ReorderFacesDialog de Android.
struct ReorderFacesSheet: View {
    @Binding var facePhotos: [String?]
    @Binding var faceBlocks: [[BoulderBlockForm]]
    @Binding var facePicked: [Int: UIImage]
    let isWall: Bool
    let direction: String
    @Environment(\.dismiss) private var dismiss
    @State private var expanded: Int? = nil

    private func move(_ a: Int, _ b: Int) {
        guard facePhotos.indices.contains(a), facePhotos.indices.contains(b) else { return }
        facePhotos.swapAt(a, b); faceBlocks.swapAt(a, b)
        let pa = facePicked[a], pb = facePicked[b]
        facePicked[a] = pb; facePicked[b] = pa
        expanded = nil
    }
    private func lines(_ i: Int) -> [TopoLineVM] {
        faceBlocks[i].filter { !$0.line.isEmpty }.map {
            TopoLineVM(id: $0.id.uuidString, name: $0.name, grade: $0.grade, startType: $0.startType, points: $0.line)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text(isWall ? L("Las fotos se recorren en este orden (%@) para numerar las vías del muro.", direction == "LTR" ? L("izq→der") : L("der→izq"))
                         : L("Ordena las fotos de la piedra."))
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(.bottom, 4)
                    ForEach(0..<facePhotos.count, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 10) {
                                Text("\(i + 1)").font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                                    .frame(width: 28, height: 28).background(Circle().fill(Cumbre.terraFill))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L("FOTO %@ · %@ vías", i + 1, faceBlocks[i].count))
                                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    Text(expanded == i ? L("▾ ocultar") : L("▸ ver vías"))
                                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                                }
                                Spacer()
                                Button { move(i, i - 1) } label: {
                                    Image(systemName: "chevron.up").foregroundStyle(i > 0 ? Cumbre.terra : Cumbre.ink3)
                                }.buttonStyle(.plain).disabled(i == 0)
                                Button { move(i, i + 1) } label: {
                                    Image(systemName: "chevron.down").foregroundStyle(i < facePhotos.count - 1 ? Cumbre.terra : Cumbre.ink3)
                                }.buttonStyle(.plain).disabled(i == facePhotos.count - 1)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { expanded = (expanded == i) ? nil : i }
                            if expanded == i {
                                if let img = facePicked[i] {
                                    Image(uiImage: img).resizable().scaledToFit()
                                        .frame(maxHeight: 220).clipShape(RoundedRectangle(cornerRadius: 2))
                                } else if let url = facePhotos[i], !url.isEmpty {
                                    TopoPhotoView(photoUrl: url, lines: lines(i))
                                } else {
                                    Text("Esta foto aún no tiene imagen.")
                                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                }
                            }
                        }
                        .padding(10)
                        .overlay(Rectangle().stroke(expanded == i ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Reordenar fotos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) {
                Button(NSLocalizedString("common_done", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
    }
}
