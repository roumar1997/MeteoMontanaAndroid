import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Flujo de proponer PIEDRA (BOULDER) en iOS — espejo de BoulderFormDialog +
// ContributionTopoDialog de Android. Nombre + sector opcional + lista de bloques
// (grado + tipo de inicio) + foto + editor de líneas (arrastrar sobre la foto).
// Envía POST /contributions con photoUrl + bloquesJson.

let BOULDER_GRADES = ["3", "4", "5", "5+",
                      "6a", "6a+", "6b", "6b+", "6c", "6c+",
                      "7a", "7a+", "7b", "7b+", "7c", "7c+",
                      "8a", "8a+", "8b", "8b+", "8c", "8c+",
                      "9a", "PROY"]

let START_TYPES = ["PIE", "SIT", "LANCE", "TRAV"]

/// Un bloque/vía de la piedra propuesta.
struct BoulderBlockForm: Identifiable {
    let id = UUID()
    var name = ""
    var grade: String? = nil
    var startType: String? = nil
    var line: [CGPoint] = []   // puntos normalizados 0..1
}

/// Serializa los bloques al formato que espera el backend (espejo de
/// List<BoulderBloqueForm>.toBloquesJson de Android): linePath es un STRING JSON.
func buildBloquesJson(_ blocks: [BoulderBlockForm]) -> String {
    let arr: [[String: Any]] = blocks.enumerated().map { idx, b in
        let pts = b.line.map { ["x": $0.x, "y": $0.y] }
        let linePath = (try? JSONSerialization.data(withJSONObject: pts))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
        return ["idx": idx,
                "name": b.name,
                "grade": b.grade as Any? ?? NSNull(),
                "startType": b.startType as Any? ?? NSNull(),
                "linePath": linePath]
    }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}

struct BoulderFormSheet: View {
    let schoolId: String
    let coord: CLLocationCoordinate2D
    let sectors: [Block]              // ZONEs de la escuela (sector opcional)
    let onDone: (Bool) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var sectorId: String? = nil
    @State private var blocks: [BoulderBlockForm] = [BoulderBlockForm()]
    @State private var pickerItem: PhotosPickerItem?
    @State private var photo: UIImage?
    @State private var showEditor = false
    @State private var sending = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    field("NOMBRE", $name, "ej: Bloque del Pulpo")

                    if !sectors.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("SECTOR (opcional)").eyebrow()
                            Menu {
                                Button("Sin sector") { sectorId = nil }
                                ForEach(sectors, id: \.id) { s in
                                    Button(s.name.isEmpty ? "Zona" : s.name) { sectorId = s.id }
                                }
                            } label: {
                                HStack {
                                    Text(sectorName).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                    Spacer(); Image(systemName: "chevron.down").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }

                    // Bloques de la piedra.
                    Text("BLOQUES EN ESTA PIEDRA").eyebrow()
                    ForEach(Array(blocks.enumerated()), id: \.element.id) { idx, _ in
                        BoulderBlockRow(block: $blocks[idx], index: idx,
                                        onDelete: blocks.count > 1 ? { blocks.remove(at: idx) } : nil)
                    }
                    Button { blocks.append(BoulderBlockForm()) } label: {
                        Text("+ AÑADIR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)

                    // Foto + dibujar líneas.
                    Text("FOTO").eyebrow().padding(.top, 4)
                    if let photo {
                        Image(uiImage: photo).resizable().scaledToFit()
                            .frame(maxHeight: 200).clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Text(photo == nil ? "SELECCIONAR FOTO" : "CAMBIAR FOTO")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    Button { showEditor = true } label: {
                        Text(blocks.contains { !$0.line.isEmpty } ? "✎ EDITAR LÍNEAS" : "✎ DIBUJAR LÍNEAS")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(photo == nil ? Cumbre.ink3 : .white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(photo == nil ? Color.clear : Cumbre.terra)
                            .overlay(Rectangle().stroke(photo == nil ? Cumbre.rule : Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(photo == nil)
                    if photo == nil {
                        Text("Añade una foto para poder dibujar las líneas.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text("ENVIAR PROPUESTA").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Nueva piedra")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cancelar") { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) { photo = img }
            }
        }
        .sheet(isPresented: $showEditor) {
            if let photo { TopoEditorView(photo: photo, blocks: $blocks) }
        }
    }

    private var sectorName: String {
        guard let sectorId, let s = sectors.first(where: { $0.id == sectorId }) else { return "Sin sector" }
        return s.name.isEmpty ? "Zona" : s.name
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    private func send() async {
        sending = true
        var photoUrl: String? = nil
        if let photo {
            let path = "contribution-photos/\(schoolId)-\(Int(Date().timeIntervalSince1970)).jpg"
            photoUrl = try? await StorageUploader.uploadJPEG(photo, path: path)
        }
        let req = ContributionRequest(
            type: "BOULDER",
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? nil : name,
            lat: coord.latitude, lon: coord.longitude,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil, sectorBlockId: sectorId,
            photoUrl: photoUrl, bloquesJson: buildBloquesJson(blocks), topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        dismiss()
        onDone(ok)
    }
}

/// Editor de líneas: arrastra sobre la foto para trazar la línea del bloque
/// seleccionado (espejo de ContributionTopoDialog de Android).
struct TopoEditorView: View {
    var photo: UIImage? = nil
    var photoUrl: String? = nil               // alternativa: cargar foto remota
    var existingLines: [TopoLineVM] = []      // vías existentes (referencia, no editables)
    @Binding var blocks: [BoulderBlockForm]
    @Environment(\.dismiss) private var dismiss
    @State private var selected = 0
    @State private var loaded: UIImage?

    private var image: UIImage? { photo ?? loaded }
    private var ratio: CGFloat {
        guard let img = image else { return 4.0 / 3.0 }
        let w = img.size.width, h = img.size.height
        return (w > 0 && h > 0) ? min(max(w / h, 0.55), 2.2) : 4.0 / 3.0
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                // Selector de bloque (chips por grado).
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(blocks.enumerated()), id: \.element.id) { idx, b in
                            let on = idx == selected
                            Button { selected = idx } label: {
                                Text("\(idx + 1)\(b.grade.map { " · \($0)" } ?? "")")
                                    .font(Cumbre.mono(11, .bold))
                                    .foregroundStyle(on ? .white : Cumbre.ink2)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(on ? GradeColor.color(b.grade) : Color.clear)
                                    .overlay(Rectangle().stroke(GradeColor.color(b.grade), lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 12)
                }

                GeometryReader { geo in
                    ZStack {
                        Color.black
                        if let img = image {
                            Image(uiImage: img).resizable().scaledToFill()
                        } else { ProgressView().tint(.white) }
                        Canvas { ctx, size in drawLines(ctx, size) }
                    }
                    .frame(width: geo.size.width, height: geo.size.width / ratio)
                    .clipped()
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                        let h = geo.size.width / ratio
                        let nx = max(0, min(1, v.location.x / geo.size.width))
                        let ny = max(0, min(1, v.location.y / h))
                        if blocks.indices.contains(selected) {
                            blocks[selected].line.append(CGPoint(x: nx, y: ny))
                        }
                    })
                }
                .aspectRatio(ratio, contentMode: .fit)
                .padding(.horizontal, 12)

                Text("Arrastra sobre la foto para trazar la línea del bloque seleccionado.")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).padding(.horizontal, 16)
                Spacer()

                HStack(spacing: 10) {
                    Button("✕ BORRAR") {
                        if blocks.indices.contains(selected) { blocks[selected].line = [] }
                    }
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.bad)
                    .padding(.vertical, 12).frame(maxWidth: .infinity)
                    .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                    Button("GUARDAR LÍNEAS") { dismiss() }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                        .padding(.vertical, 12).frame(maxWidth: .infinity).background(Cumbre.ink)
                }
                .buttonStyle(.plain).padding(.horizontal, 12).padding(.bottom, 12)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Dibujar líneas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task {
            if photo == nil, let photoUrl, let url = URL(string: photoUrl),
               let (data, _) = try? await URLSession.shared.data(from: url) {
                loaded = UIImage(data: data)
            }
        }
    }

    private func drawLines(_ ctx: GraphicsContext, _ size: CGSize) {
        // Vías existentes como referencia (semitransparentes, no editables).
        for line in existingLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.45)),
                       style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        }
        for (idx, b) in blocks.enumerated() where !b.line.isEmpty {
            let style = GradeColor.style(b.grade)
            let pts = b.line.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            let w: CGFloat = idx == selected ? 8 : 5
            if style.dark {
                ctx.stroke(path, with: .color(.black.opacity(0.8)),
                           style: StrokeStyle(lineWidth: w + 4, lineCap: .round, lineJoin: .round))
            }
            ctx.stroke(path, with: .color(style.stroke),
                       style: StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round))
            // Badge número en el inicio.
            let fill: Color = .white
            ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 12, y: pts[0].y - 12, width: 24, height: 24)), with: .color(fill))
            ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 9, y: pts[0].y - 9, width: 18, height: 18)), with: .color(style.stroke))
            ctx.draw(Text("\(idx + 1)").font(.system(size: 12, weight: .bold))
                .foregroundColor(style.dark ? .black : .white), at: pts[0], anchor: .center)
        }
    }
}

/// Fila de un bloque/vía (nombre + grado + tipo de inicio) compartida por el
/// formulario de piedra nueva y el de añadir vías. Espejo de AddLineRow.
struct BoulderBlockRow: View {
    @Binding var block: BoulderBlockForm
    let index: Int
    var onDelete: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("\(index + 1)").font(Cumbre.mono(11, .bold))
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
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(BOULDER_GRADES, id: \.self) { g in
                        let on = block.grade == g
                        Button { block.grade = on ? nil : g } label: {
                            Text(g).font(Cumbre.mono(11, .bold))
                                .foregroundStyle(on ? .white : Cumbre.ink2)
                                .padding(.horizontal, 9).padding(.vertical, 5)
                                .background(on ? GradeColor.color(g) : Color.clear)
                                .overlay(Rectangle().stroke(on ? GradeColor.color(g) : Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }
            }
            HStack(spacing: 6) {
                ForEach(START_TYPES, id: \.self) { st in
                    let on = block.startType == st
                    Button { block.startType = on ? nil : st } label: {
                        Text(st).font(Cumbre.mono(10, .bold))
                            .foregroundStyle(on ? .white : Cumbre.ink2)
                            .padding(.horizontal, 9).padding(.vertical, 5)
                            .background(on ? Cumbre.ink : Color.clear)
                            .overlay(Rectangle().stroke(on ? Cumbre.ink : Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain)
                }
                if !block.line.isEmpty {
                    Text("✓ línea (\(block.line.count))").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ok)
                }
            }
        }
        .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Flujo "+ AÑADIR VÍAS" a una piedra existente — espejo de AddLinesFlow.kt.
/// Reutiliza la foto de la piedra, muestra las vías existentes de referencia y
/// envía una contribución BOULDER con targetBlockId (el backend las añade al
/// aprobar; no toca las existentes).
struct AddLinesSheet: View {
    let block: Block
    let schoolId: String
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var blocks: [BoulderBlockForm] = [BoulderBlockForm()]
    @State private var showEditor = false
    @State private var sending = false

    private var hasPhoto: Bool { !(block.photoPath ?? "").isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Estás añadiendo bloques nuevos a «\(block.name)». Las vías existentes no se tocan.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)

                    Text("NUEVAS VÍAS").eyebrow()
                    ForEach(Array(blocks.enumerated()), id: \.element.id) { idx, _ in
                        BoulderBlockRow(block: $blocks[idx], index: idx,
                                        onDelete: blocks.count > 1 ? { blocks.remove(at: idx) } : nil)
                    }
                    Button { blocks.append(BoulderBlockForm()) } label: {
                        Text("+ AÑADIR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)

                    if hasPhoto {
                        Button { showEditor = true } label: {
                            Text(blocks.contains { !$0.line.isEmpty } ? "✎ EDITAR LÍNEAS SOBRE LA FOTO" : "✎ DIBUJAR LÍNEAS SOBRE LA FOTO")
                                .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(Cumbre.terra)
                        }.buttonStyle(.plain)
                    } else {
                        Text("Esta piedra no tiene foto, no puedes dibujar líneas.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text("ENVIAR PROPUESTA").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Añadir vías")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cancelar") { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
        .sheet(isPresented: $showEditor) {
            TopoEditorView(photoUrl: block.photoPath,
                           existingLines: block.lines.map { TopoLineVM($0) },
                           blocks: $blocks)
        }
    }

    private func send() async {
        sending = true
        let useful = blocks.filter { $0.grade != nil || !$0.name.isEmpty || !$0.line.isEmpty }
        let req = ContributionRequest(
            type: "BOULDER", name: nil,
            lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: buildBloquesJson(useful), topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        dismiss()
        onDone(ok)
    }
}
