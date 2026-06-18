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
    /// id de la vía existente que representa esta fila (nil = vía nueva). Usado por
    /// el editor unificado para distinguir "corregir existente" de "añadir nueva".
    var existingLineId: String? = nil
    /// Foto (cara) a la que pertenece esta vía. Al corregir una piedra multi-foto,
    /// mantiene cada vía en SU cara (no las mezcla todas en la portada).
    var facePhoto: String? = nil
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
                "linePath": linePath,
                // Si la fila representa una vía existente, el backend la CORRIGE
                // (en vez de añadir) — permite editar varias en una sola propuesta.
                "targetLineId": b.existingLineId as Any? ?? NSNull(),
                // Cara (foto) a la que pertenece → el backend la mantiene en su cara.
                "photoUrl": b.facePhoto as Any? ?? NSNull()]
    }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}

/// Una CARA de la piedra al proponer: una foto y las vías dibujadas sobre ella.
struct BoulderFaceForm: Identifiable {
    let id = UUID()
    var photo: UIImage? = nil
    var blocks: [BoulderBlockForm] = [BoulderBlockForm()]
}

/// Serializa VARIAS caras a un único `bloquesJson` donde cada vía lleva el
/// `photoUrl` de su cara (el backend agrupa por foto en caras). `photoByFace` =
/// URL ya subida de cada cara (por id).
func buildFacesBloquesJson(_ faces: [BoulderFaceForm], photoByFace: [UUID: String?]) -> String {
    var arr: [[String: Any]] = []
    var idx = 0
    for face in faces {
        let facePhoto = photoByFace[face.id] ?? nil
        for b in face.blocks {
            let pts = b.line.map { ["x": $0.x, "y": $0.y] }
            let linePath = (try? JSONSerialization.data(withJSONObject: pts))
                .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
            arr.append(["idx": idx,
                        "name": b.name,
                        "grade": b.grade as Any? ?? NSNull(),
                        "startType": b.startType as Any? ?? NSNull(),
                        "linePath": linePath,
                        "targetLineId": b.existingLineId as Any? ?? NSNull(),
                        "photoUrl": facePhoto as Any? ?? NSNull()])
            idx += 1
        }
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
    // Una piedra grande no cabe en una foto → varias CARAS (foto + sus vías).
    @State private var faces: [BoulderFaceForm] = [BoulderFaceForm()]
    @State private var selectedFace = 0
    @State private var pickerItem: PhotosPickerItem?
    @State private var showEditor = false
    @State private var sending = false

    private var faceIdx: Int { min(max(selectedFace, 0), faces.count - 1) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("A esta piedra se le asignará un número automático al publicarse.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)

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

                    // ── Caras (fotos) ──────────────────────────────────────────────
                    Text("FOTOS DE LA PIEDRA").eyebrow()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(faces.enumerated()), id: \.element.id) { idx, _ in
                                let on = idx == faceIdx
                                Button { selectedFace = idx } label: {
                                    Text("FOTO \(idx + 1)").font(Cumbre.mono(11, .bold))
                                        .foregroundStyle(on ? .white : Cumbre.ink2)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(on ? Cumbre.terra : Color.clear)
                                        .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                                }.buttonStyle(.plain)
                            }
                            Button { faces.append(BoulderFaceForm()); selectedFace = faces.count - 1 } label: {
                                Text("+ AÑADIR FOTO").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }
                    if faces.count > 1 {
                        Button {
                            faces.remove(at: faceIdx); selectedFace = max(0, faceIdx - 1)
                        } label: {
                            Text("✕ QUITAR ESTA FOTO").font(Cumbre.mono(10, .bold))
                                .foregroundStyle(Cumbre.bad)
                        }.buttonStyle(.plain)
                    }

                    // ── Foto de la cara seleccionada ───────────────────────────────
                    if let photo = faces[faceIdx].photo {
                        Image(uiImage: photo).resizable().scaledToFit()
                            .frame(maxHeight: 200).clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Text(faces[faceIdx].photo == nil ? "SELECCIONAR FOTO" : "CAMBIAR FOTO")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }

                    // ── Vías de esta foto ──────────────────────────────────────────
                    Text("VÍAS EN ESTA FOTO").eyebrow().padding(.top, 4)
                    ForEach(Array(faces[faceIdx].blocks.enumerated()), id: \.element.id) { idx, _ in
                        BoulderBlockRow(block: $faces[faceIdx].blocks[idx], index: idx,
                                        onDelete: faces[faceIdx].blocks.count > 1 ? { faces[faceIdx].blocks.remove(at: idx) } : nil)
                    }
                    Button { faces[faceIdx].blocks.append(BoulderBlockForm()) } label: {
                        Text("+ AÑADIR VÍA").font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)

                    // ── Dibujar líneas de esta foto ────────────────────────────────
                    let hasPhoto = faces[faceIdx].photo != nil
                    Button { showEditor = true } label: {
                        Text(faces[faceIdx].blocks.contains { !$0.line.isEmpty } ? "✎ EDITAR LÍNEAS" : "✎ DIBUJAR LÍNEAS")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(hasPhoto ? .white : Cumbre.ink3)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(hasPhoto ? Cumbre.terra : Color.clear)
                            .overlay(Rectangle().stroke(hasPhoto ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(!hasPhoto)
                    if !hasPhoto {
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
                   let img = UIImage(data: data) { faces[faceIdx].photo = img }
            }
        }
        .sheet(isPresented: $showEditor) {
            if faces[faceIdx].photo != nil {
                TopoEditorView(photo: faces[faceIdx].photo, blocks: $faces[faceIdx].blocks)
            }
        }
    }

    private var sectorName: String {
        guard let sectorId, let s = sectors.first(where: { $0.id == sectorId }) else { return "Sin sector" }
        return s.name.isEmpty ? "Zona" : s.name
    }

    private func send() async {
        sending = true
        // Sube la foto de cada cara → URL por cara. Ruta = la que permiten las
        // reglas de Firebase Storage (`piedra-photos-pending/{uid}_...`); subir a
        // otra ruta es denegado y la foto se pierde en silencio.
        var photoByFace: [UUID: String?] = [:]
        for (i, face) in faces.enumerated() {
            if let photo = face.photo {
                photoByFace[face.id] = try? await StorageUploader.uploadBoulderPhoto(photo, schoolId: schoolId, index: i)
            } else {
                photoByFace[face.id] = nil
            }
        }
        let coverPhoto = faces.compactMap { photoByFace[$0.id] ?? nil }.first
        let req = ContributionRequest(
            type: "BOULDER",
            name: nil,   // el número lo asigna el backend al materializar
            lat: coord.latitude, lon: coord.longitude,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil, sectorBlockId: sectorId,
            photoUrl: coverPhoto, bloquesJson: buildFacesBloquesJson(faces, photoByFace: photoByFace), topoLinesJson: nil)
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
    /// Vías existentes que NO cambian: se ven NORMALES (sólidas con número/tipo).
    var normalLines: [TopoLineVM] = []
    /// Vías difuminadas: SOLO la versión vieja de la que se corrige.
    var fadedLines: [TopoLineVM] = []
    @Binding var blocks: [BoulderBlockForm]
    @Environment(\.dismiss) private var dismiss
    @State private var selected = 0
    @State private var loaded: UIImage?
    @State private var drawingActive = false   // ¿estamos en mitad de un trazo?

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
                        guard blocks.indices.contains(selected) else { return }
                        // Cada pulsación nueva traza una línea NUEVA: no alarga la
                        // anterior (ni la precargada al corregir). Se redibuja limpio.
                        if !drawingActive {
                            drawingActive = true
                            blocks[selected].line = []
                        }
                        blocks[selected].line.append(CGPoint(x: nx, y: ny))
                    }.onEnded { _ in drawingActive = false })
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
        // 1. Existentes que NO cambian → NORMALES (sólidas, con número y tipo).
        for (i, line) in normalLines.enumerated() where !line.points.isEmpty {
            drawTopoLine(ctx, size, points: line.points, grade: line.grade,
                         startType: line.startType, number: i + 1, lineWidth: 5)
        }
        // 2. SOLO la vía vieja que se corrige, difuminada (para distinguirla).
        for line in fadedLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.4)),
                       style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round, dash: [6, 6]))
        }
        // 3. Las que se están dibujando (editable), resaltadas.
        for (idx, b) in blocks.enumerated() where !b.line.isEmpty {
            drawTopoLine(ctx, size, points: b.line, grade: b.grade, startType: b.startType,
                         number: idx + 1, lineWidth: idx == selected ? 8 : 5)
        }
    }

    private func drawTopoLine(_ ctx: GraphicsContext, _ size: CGSize, points: [CGPoint],
                              grade: String?, startType: String?, number: Int, lineWidth: CGFloat) {
        let style = GradeColor.style(grade)
        let pts = points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
        guard !pts.isEmpty else { return }
        var path = Path(); path.move(to: pts[0])
        for p in pts.dropFirst() { path.addLine(to: p) }
        if style.dark {
            ctx.stroke(path, with: .color(.black.opacity(0.8)),
                       style: StrokeStyle(lineWidth: lineWidth + 4, lineCap: .round, lineJoin: .round))
        }
        ctx.stroke(path, with: .color(style.stroke),
                   style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        // Badge número en el inicio.
        ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 12, y: pts[0].y - 12, width: 24, height: 24)), with: .color(.white))
        ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 9, y: pts[0].y - 9, width: 18, height: 18)), with: .color(style.stroke))
        ctx.draw(Text("\(number)").font(.system(size: 12, weight: .bold))
            .foregroundColor(style.dark ? .black : .white), at: pts[0], anchor: .center)
        // Badge de tipo de inicio (PIE/SIT/LAN/TRV) al final.
        if let label = startLabelShort(startType), let end = pts.last {
            ctx.fill(Path(ellipseIn: CGRect(x: end.x - 13, y: end.y - 13, width: 26, height: 26)),
                     with: .color(style.dark ? .black : .white))
            ctx.fill(Path(ellipseIn: CGRect(x: end.x - 10.5, y: end.y - 10.5, width: 21, height: 21)),
                     with: .color(style.stroke))
            ctx.draw(Text(label).font(.system(size: 9, weight: .bold))
                .foregroundColor(style.dark ? .black : .white), at: end, anchor: .center)
        }
    }

    private func startLabelShort(_ t: String?) -> String? {
        switch t?.uppercased() {
        case "PIE", "STAND": return "PIE"
        case "SIT": return "SIT"
        case "LANCE", "JUMP": return "LAN"
        case "TRAV": return "TRV"
        default: return nil
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


/// Mapea el tipo de inicio del backend (STAND/JUMP) a la UI (PIE/LANCE).
func startTypeForUi(_ t: String?) -> String? {
    switch t?.uppercased() {
    case "STAND", "PIE": return "PIE"
    case "SIT": return "SIT"
    case "JUMP", "LANCE": return "LANCE"
    case "TRAV": return "TRAV"
    default: return nil
    }
}


/// Flujo "+ ASIGNAR SECTOR" — espejo de la contribución ASSIGN_SECTOR. Elige una
/// zona y propone que esta piedra pertenezca a ese sector.
struct AssignSectorSheet: View {
    let block: Block
    let schoolId: String
    let sectors: [Block]
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var sending = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    let options = sectors.filter { $0.id != block.sectorBlockId }
                    Text(block.sectorBlockId == nil
                         ? "Elige el sector (zona) al que pertenece «\(block.name)». Un admin lo revisará."
                         : "Elige el nuevo sector (zona) de «\(block.name)». Un admin lo revisará.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    if options.isEmpty {
                        Text("Esta escuela solo tiene este sector. Crea otro con «+ PROPONER → SECTOR» para poder cambiarlo.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    }
                    ForEach(options, id: \.id) { s in
                        Button { Task { await assign(s.id) } } label: {
                            HStack {
                                Text(s.name.isEmpty ? "Zona" : s.name).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(12).frame(maxWidth: .infinity)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain).disabled(sending)
                    }
                    if sending { ProgressView().frame(maxWidth: .infinity) }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Asignar sector")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cancelar") { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
    }

    private func assign(_ sectorId: String) async {
        sending = true
        let req = ContributionRequest(
            type: "ASSIGN_SECTOR", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: sectorId,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false; dismiss(); onDone(ok)
    }
}

/// Editor UNIFICADO de vías ("✎ EDITAR / AÑADIR VÍAS"): muestra TODAS las vías de
/// la piedra (existentes precargadas + las nuevas que añadas). Tocas cualquiera
/// para cambiar nombre/grado/tipo o redibujarla sobre la foto, y al enviar manda
/// una corrección por cada vía existente modificada y una propuesta con las nuevas.
struct EditLinesSheet: View {
    let block: Block
    let schoolId: String
    let onDone: (Bool) -> Void
    /// Cara que se abre primero (deep-link "corregir esta vía"): la de esa vía.
    var focusVia: String? = nil
    @Environment(\.dismiss) private var dismiss
    // Una piedra puede tener VARIAS caras (fotos). Cada cara edita SOLO sus vías
    // sobre SU foto (antes se mezclaban todas en la portada). `faceBlocks[i]` =
    // vías editables de la cara i; `facePhotos[i]` = su foto.
    @State private var faceBlocks: [[BoulderBlockForm]] = []
    @State private var facePhotos: [String?] = []
    @State private var selectedFace = 0
    @State private var showEditor = false
    @State private var sending = false
    @State private var loaded = false
    // Foto nueva elegida para una cara (mejorar la imagen). Al cambiarla, TODAS
    // las vías de esa cara se mueven a la foto nueva y se redibujan sobre ella.
    @State private var facePicked: [Int: UIImage] = [:]
    @State private var pickerItem: PhotosPickerItem?

    private var faceIdx: Int { min(max(selectedFace, 0), max(0, faceBlocks.count - 1)) }
    private var currentPhoto: String? { facePhotos.indices.contains(faceIdx) ? facePhotos[faceIdx] : nil }
    private var hasPhoto: Bool { !(currentPhoto ?? "").isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Edita las vías de «\(block.name)». Cada foto edita sus propias vías: toca una para cambiar nombre/grado/tipo o redibujarla, añade nuevas, y envía todo junto. Un admin lo revisará.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)

                    // Selector de cara (solo si hay varias fotos).
                    if facePhotos.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(0..<facePhotos.count, id: \.self) { i in
                                    let on = i == faceIdx
                                    Button { selectedFace = i } label: {
                                        Text("FOTO \(i + 1)").font(Cumbre.mono(11, .bold))
                                            .foregroundStyle(on ? .white : Cumbre.ink2)
                                            .padding(.horizontal, 10).padding(.vertical, 6)
                                            .background(on ? Cumbre.terra : Color.clear)
                                            .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                                    }.buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    if faceBlocks.indices.contains(faceIdx) {
                        ForEach(Array(faceBlocks[faceIdx].enumerated()), id: \.element.id) { idx, _ in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(faceBlocks[faceIdx][idx].existingLineId != nil ? "VÍA EXISTENTE" : "NUEVA")
                                    .font(Cumbre.mono(9, .bold))
                                    .foregroundStyle(faceBlocks[faceIdx][idx].existingLineId != nil ? Cumbre.ink3 : Cumbre.terra)
                                BoulderBlockRow(block: $faceBlocks[faceIdx][idx], index: idx,
                                                onDelete: faceBlocks[faceIdx][idx].existingLineId == nil ? { faceBlocks[faceIdx].remove(at: idx) } : nil)
                            }
                        }
                    }

                    Button { faceBlocks[faceIdx].append(BoulderBlockForm(facePhoto: currentPhoto)) } label: {
                        Text("+ NUEVA VÍA EN ESTA FOTO").font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)

                    // Cambiar la foto de esta cara (mejorarla). Si eliges una nueva,
                    // todas las vías de la cara se moverán a ella y conviene
                    // redibujarlas. Si no eres admin, el admin la revisará.
                    if let img = facePicked[faceIdx] {
                        Image(uiImage: img).resizable().scaledToFit()
                            .frame(maxHeight: 160).clipShape(RoundedRectangle(cornerRadius: 2))
                        Text("Foto nueva — redibuja las líneas sobre ella.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Text(facePicked[faceIdx] == nil ? "CAMBIAR FOTO DE ESTA CARA" : "ELEGIR OTRA FOTO")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }

                    if hasPhoto || facePicked[faceIdx] != nil {
                        Button { showEditor = true } label: {
                            Text("✎ DIBUJAR / EDITAR SOBRE ESTA FOTO")
                                .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(Cumbre.terra)
                        }.buttonStyle(.plain)
                    } else {
                        Text("Esta cara no tiene foto, no puedes dibujar líneas.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text("ENVIAR CAMBIOS").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Editar vías")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cancelar") { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            let faces = block.facesOrDerived()
            if faces.isEmpty {
                // Piedra sin caras/vías → una cara con la portada y una vía nueva.
                facePhotos = [block.photoPath]
                faceBlocks = [[BoulderBlockForm(facePhoto: block.photoPath)]]
            } else {
                facePhotos = faces.map { $0.photoPath ?? block.photoPath }
                faceBlocks = faces.map { f in
                    f.lines.map { l in
                        BoulderBlockForm(name: l.name, grade: l.grade,
                                         startType: startTypeForUi(l.startType),
                                         line: TopoParse.points(l.linePath),
                                         existingLineId: l.id,
                                         facePhoto: f.photoPath ?? block.photoPath)
                    }
                }
                // Abre la cara que contiene la vía del deep-link, si la hay.
                if let v = focusVia?.trimmingCharacters(in: .whitespaces), !v.isEmpty,
                   let hit = faces.firstIndex(where: { f in
                       f.lines.contains { $0.name.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(v) == .orderedSame }
                   }) {
                    selectedFace = hit
                }
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            let idx = faceIdx
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) { facePicked[idx] = img }
            }
        }
        .sheet(isPresented: $showEditor) {
            // Solo las vías de ESTA cara, sobre SU foto (la nueva si la cambiaste).
            if faceBlocks.indices.contains(faceIdx) {
                if let img = facePicked[faceIdx] {
                    TopoEditorView(photo: img, blocks: $faceBlocks[faceIdx])
                } else {
                    TopoEditorView(photoUrl: currentPhoto, blocks: $faceBlocks[faceIdx])
                }
            }
        }
    }

    private func send() async {
        sending = true
        // 1) Sube las fotos nuevas (caras que el usuario cambió) → URL por cara, y
        //    marca esas caras como "foto cambiada".
        var newFacePhoto: [Int: String] = [:]
        for (i, img) in facePicked {
            if let url = try? await StorageUploader.uploadBoulderPhoto(img, schoolId: schoolId, index: i) {
                newFacePhoto[i] = url
            }
        }
        // 2) Construye el payload por cara. Si la cara cambió de foto, se envían
        //    TODAS sus vías (existentes como corrección + nuevas) con la foto nueva
        //    → la cara entera se mueve a la imagen nueva. Si no cambió, solo las
        //    vías modificadas + las nuevas.
        var payload: [BoulderBlockForm] = []
        for (i, faceVias) in faceBlocks.enumerated() {
            let movedPhoto = newFacePhoto[i]
            for b in faceVias {
                var v = b
                if let p = movedPhoto { v.facePhoto = p }   // mover a la foto nueva
                let isExisting = v.existingLineId != nil
                if isExisting {
                    let orig = block.lines.first { $0.id == v.existingLineId }
                    let changed = movedPhoto != nil || (orig.map { lineChanged(v, vs: $0) } ?? false)
                    if changed { payload.append(v) }
                } else if v.grade != nil || !v.name.isEmpty || !v.line.isEmpty {
                    payload.append(v)
                }
            }
        }
        guard !payload.isEmpty else { sending = false; dismiss(); onDone(false); return }

        let req = ContributionRequest(
            type: "BOULDER", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: buildBloquesJson(payload), topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false; dismiss(); onDone(ok)
    }

    private func lineChanged(_ b: BoulderBlockForm, vs orig: BlockLine) -> Bool {
        if b.name != orig.name { return true }
        if (b.grade ?? "") != (orig.grade ?? "") { return true }
        if (b.startType ?? "") != (startTypeForUi(orig.startType) ?? "") { return true }
        let origPts = TopoParse.points(orig.linePath)
        if b.line.count != origPts.count { return true }
        for (i, p) in b.line.enumerated() where abs(p.x - origPts[i].x) > 0.001 || abs(p.y - origPts[i].y) > 0.001 {
            return true
        }
        return false
    }
}
