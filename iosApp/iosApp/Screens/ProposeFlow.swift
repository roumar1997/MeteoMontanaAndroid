import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Flujo de proponer PIEDRA (BOULDER) en iOS — espejo de BoulderFormDialog +
// ContributionTopoDialog de Android. Nombre + sector opcional + lista de bloques
// (grado + tipo de inicio) + foto + editor de líneas (arrastrar sobre la foto).
// Envía POST /contributions con photoUrl + bloquesJson.

/// Selector de modalidad de la piedra: BLOQUE (BOULDER) o VÍA (ROUTE).
/// Reutilizado al proponer/crear piedra y al editarla (admin). Espejo del de Android.
struct DisciplineSelector: View {
    @Binding var selected: String
    var body: some View {
        HStack(spacing: 8) {
            ForEach([("BOULDER", "BLOQUE"), ("ROUTE", "VÍA")], id: \.0) { value, label in
                let on = selected == value
                Button { selected = value } label: {
                    Text(label).font(Cumbre.mono(12, .bold)).tracking(0.6)
                        .foregroundStyle(on ? .white : Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(on ? Cumbre.terra : Color.clear)
                        .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
    }
}

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
    @State private var discipline = "BOULDER"   // BOULDER (bloque) / ROUTE (vía)
    // Una piedra grande no cabe en una foto → varias CARAS (foto + sus vías).
    @State private var faces: [BoulderFaceForm] = [BoulderFaceForm()]
    @State private var selectedFace = 0
    @State private var pickerItem: PhotosPickerItem?
    @State private var showEditor = false
    @State private var sending = false
    // Error de envío: al fallar NO se cierra el sheet — se muestra el error y
    // el botón pasa a REINTENTAR (la foto y las vías siguen ahí).
    @State private var sendError: String? = nil
    // Geometría/sentido + trazado del muro (PUNTO por defecto).
    @State private var geometry = "POINT"
    @State private var direction = "LTR"
    @State private var wallPath: [CLLocationCoordinate2D] = []
    @State private var showTrace = false

    private var faceIdx: Int { min(max(selectedFace, 0), faces.count - 1) }
    private var isWall: Bool { geometry == "LINE" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Rellena los datos de la piedra. Podrás añadir fotos y dibujar las líneas de cada vía sobre ellas.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)

                    FirstTimeHint(
                        hintKey: "boulder_form_guide",
                        text: "Pasos: 1) Elige modalidad y geometría, 2) Añade una foto de la piedra, 3) Dibuja las líneas de las vías sobre la foto, 4) Envía."
                    )

                    // ── Modalidad: BLOQUE o VÍA ────────────────────────────────────
                    VStack(alignment: .leading, spacing: 6) {
                        Text("MODALIDAD").eyebrow()
                        Text("¿Es una piedra de boulder (sentadas, bloques cortos) o de vía (escalada deportiva, más larga)?")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        DisciplineSelector(selected: $discipline)
                    }

                    // ── Geometría: PUNTO o MURO ────────────────────────────────────
                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("propose_geometry", comment: "")).eyebrow()
                        Text("Punto = una piedra suelta. Muro = una pared larga que se traza en el mapa.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        WallSeg(options: [("POINT", "PUNTO"), ("LINE", "MURO")], selected: $geometry)
                    }
                    if isWall {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("SENTIDO DE NUMERACIÓN").eyebrow()
                            WallSeg(options: [("LTR", "IZQ → DER"), ("RTL", "DER → IZQ")], selected: $direction)
                        }
                        Button { showTrace = true } label: {
                            Text(wallPath.isEmpty ? "✎ TRAZAR EL MURO EN EL MAPA" : "✓ MURO TRAZADO (\(wallPath.count) PUNTOS) · RE-TRAZAR")
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

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
                                Text(NSLocalizedString("propose_add_photo", comment: "")).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
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

                    if let sendError {
                        Text(sendError).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }
                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text(sendError != nil ? "REINTENTAR" : NSLocalizedString("propose_submit", comment: "")).font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Nueva piedra")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
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
        .sheet(isPresented: $showTrace) {
            WallTraceSheet(center: coord, initial: wallPath) { wallPath = $0 }
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
            photoUrl: coverPhoto, bloquesJson: buildFacesBloquesJson(faces, photoByFace: photoByFace), topoLinesJson: nil,
            discipline: discipline,
            geometry: geometry,
            path: isWall && !wallPath.isEmpty ? buildPathJson(wallPath) : nil,
            direction: direction)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok {
            dismiss()
            onDone(true)
        } else {
            sendError = "No se pudo enviar. Revisa la conexión — la foto y las vías siguen aquí."
        }
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
                    Button(NSLocalizedString("propose_save_lines", comment: "")) { dismiss() }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                        .padding(.vertical, 12).frame(maxWidth: .infinity).background(Cumbre.ink)
                }
                .buttonStyle(.plain).padding(.horizontal, 12).padding(.bottom, 12)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Dibujar líneas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
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
    @State private var sendError: String? = nil

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
                    if let sendError {
                        Text(sendError + " Toca un sector para reintentar.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }
                    if sending { ProgressView().frame(maxWidth: .infinity) }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Asignar sector")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
    }

    private func assign(_ sectorId: String) async {
        sending = true
        let req = ContributionRequest(
            type: "ASSIGN_SECTOR", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: sectorId,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok { dismiss(); onDone(true) }
        else { sendError = "No se pudo enviar. Revisa la conexión." }
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
    @State private var sendError: String? = nil
    @State private var loaded = false
    // Foto nueva elegida para una cara (mejorar la imagen). Al cambiarla, TODAS
    // las vías de esa cara se mueven a la foto nueva y se redibujan sobre ella.
    @State private var facePicked: [Int: UIImage] = [:]
    @State private var pickerItem: PhotosPickerItem?
    // Geometría/sentido del muro (editables). El bloque ya creado los trae.
    @State private var geometry = "POINT"
    @State private var direction = "LTR"
    @State private var showReorder = false
    @State private var showTrace = false
    @State private var tracedPath: [CLLocationCoordinate2D] = []

    private var faceIdx: Int { min(max(selectedFace, 0), max(0, faceBlocks.count - 1)) }
    private var isWall: Bool { geometry == "LINE" }
    /// Reordena las caras (foto + sus vías + foto nueva pendiente) en bloque.
    private func swapFaces(_ a: Int, _ b: Int) {
        guard facePhotos.indices.contains(a), facePhotos.indices.contains(b) else { return }
        facePhotos.swapAt(a, b); faceBlocks.swapAt(a, b)
        let pa = facePicked[a], pb = facePicked[b]
        facePicked[a] = pb; facePicked[b] = pa
    }
    /// Quita una cara y reindexa las fotos nuevas pendientes (dict por índice).
    private func removeFace(_ idx: Int) {
        guard facePhotos.indices.contains(idx) else { return }
        facePhotos.remove(at: idx); faceBlocks.remove(at: idx)
        var np: [Int: UIImage] = [:]
        for (k, v) in facePicked { if k < idx { np[k] = v } else if k > idx { np[k - 1] = v } }
        facePicked = np
        selectedFace = max(0, idx - 1)
    }
    /// Número global de la vía en el muro (cruza todas las fotos). nil si PUNTO.
    private func wallNumber(_ idx: Int) -> Int? {
        guard isWall else { return nil }
        let preceding = faceBlocks.prefix(faceIdx).reduce(0) { $0 + $1.count }
        let total = faceBlocks.reduce(0) { $0 + $1.count }
        let pos = preceding + idx
        return direction == "LTR" ? pos + 1 : total - pos
    }
    private var currentPhoto: String? { facePhotos.indices.contains(faceIdx) ? facePhotos[faceIdx] : nil }
    private var hasPhoto: Bool { !(currentPhoto ?? "").isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Edita «\(block.name)»: corrige o añade vías, añade más fotos, reordénalas y ajusta el muro. Un admin lo revisará.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)

                    // ── Geometría / sentido ───────────────────────────────────────
                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("propose_geometry", comment: "")).eyebrow()
                        WallSeg(options: [("POINT", "PUNTO"), ("LINE", "MURO")], selected: $geometry)
                    }
                    if isWall {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("SENTIDO DE NUMERACIÓN").eyebrow()
                            WallSeg(options: [("LTR", "IZQ → DER"), ("RTL", "DER → IZQ")], selected: $direction)
                        }
                        Button { showTrace = true } label: {
                            Text(tracedPath.isEmpty
                                 ? (parseWallPath(block.path).isEmpty ? "✎ TRAZAR EL MURO EN EL MAPA" : "✎ RE-TRAZAR EL MURO EN EL MAPA")
                                 : "✓ MURO TRAZADO (\(tracedPath.count) PUNTOS) · RE-TRAZAR")
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                        Text(tracedPath.isEmpty ? "Se conserva el trazado actual si no lo re-trazas." : "Se enviará el trazado nuevo.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    // ── Caras (fotos): pestañas + añadir ──────────────────────────
                    Text("FOTOS DE LA PIEDRA").eyebrow()
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
                            Button {
                                facePhotos.append(nil); faceBlocks.append([]); selectedFace = facePhotos.count - 1
                            } label: {
                                Text(NSLocalizedString("propose_add_photo", comment: "")).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }
                    if facePhotos.count > 1 {
                        HStack(spacing: 12) {
                            Button { showReorder = true } label: {
                                Text("↕ REORDENAR FOTOS").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                                    .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                            }.buttonStyle(.plain)
                            Button { removeFace(faceIdx) } label: {
                                Text("✕ QUITAR FOTO \(faceIdx + 1)").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.bad)
                                    .padding(.horizontal, 10).padding(.vertical, 8)
                                    .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }

                    if faceBlocks.indices.contains(faceIdx) {
                        ForEach(Array(faceBlocks[faceIdx].enumerated()), id: \.element.id) { idx, _ in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(faceBlocks[faceIdx][idx].existingLineId != nil ? "VÍA EXISTENTE" : "NUEVA")
                                    .font(Cumbre.mono(9, .bold))
                                    .foregroundStyle(faceBlocks[faceIdx][idx].existingLineId != nil ? Cumbre.ink3 : Cumbre.terra)
                                BoulderBlockRow(block: $faceBlocks[faceIdx][idx], index: idx,
                                                number: wallNumber(idx),
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

                    if let sendError {
                        Text(sendError).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }
                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text(sendError != nil ? "REINTENTAR" : "ENVIAR CAMBIOS").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Editar vías")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            geometry = block.geometry.isEmpty ? "POINT" : block.geometry
            direction = block.direction.isEmpty ? "LTR" : block.direction
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
        .sheet(isPresented: $showReorder) {
            ReorderFacesSheet(facePhotos: $facePhotos, faceBlocks: $faceBlocks,
                              facePicked: $facePicked, isWall: isWall, direction: direction)
        }
        .sheet(isPresented: $showTrace) {
            let seed = tracedPath.isEmpty ? parseWallPath(block.path) : tracedPath
            WallTraceSheet(center: CLLocationCoordinate2D(latitude: block.lat, longitude: block.lon),
                           initial: seed) { tracedPath = $0 }
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
        // Estado COMPLETO: todas las vías en su orden, cada una con la foto de su
        // cara (la nueva si la cara cambió). El backend (reconcileWall) reconcilia
        // por lineId preservando el diario, reaplica el orden y borra las omitidas.
        var payload: [BoulderBlockForm] = []
        for (i, faceVias) in faceBlocks.enumerated() {
            let movedPhoto = newFacePhoto[i]
            for b in faceVias {
                var v = b
                if let p = movedPhoto { v.facePhoto = p }   // mover a la foto nueva
                if v.existingLineId != nil {
                    payload.append(v)                        // existentes SIEMPRE
                } else if v.grade != nil || !v.name.isEmpty || !v.line.isEmpty {
                    payload.append(v)                        // nuevas con contenido
                }
            }
        }
        guard !payload.isEmpty else { sending = false; dismiss(); onDone(false); return }

        let req = ContributionRequest(
            type: "BOULDER", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: buildBloquesJson(payload), topoLinesJson: nil, discipline: nil,
            geometry: geometry,
            path: isWall ? (tracedPath.isEmpty ? block.path : buildPathJson(tracedPath)) : nil,
            direction: direction)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok { dismiss(); onDone(true) }
        else { sendError = "No se pudo enviar. Revisa la conexión — tus cambios siguen aquí." }
    }
}

/// Parsea el `path` de un muro ("[[lat,lon],...]") a coordenadas.
func parseWallPath(_ json: String?) -> [CLLocationCoordinate2D] {
    guard let json, !json.isEmpty, let data = json.data(using: .utf8),
          let arr = try? JSONSerialization.jsonObject(with: data) as? [[Double]] else { return [] }
    return arr.compactMap { $0.count >= 2 ? CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) : nil }
}

/// Serializa la polilínea del muro a "[[lat,lon],...]" (formato de Block.path).
func buildPathJson(_ pts: [CLLocationCoordinate2D]) -> String {
    let arr = pts.map { [$0.latitude, $0.longitude] }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}

/// Sheet para trazar/re-trazar el muro en su propio mapa (encima del editor, así
/// no se pierde lo editado). Cada tap añade un punto; DESHACER/LISTO. Devuelve la
/// polilínea por `onDone`. Espejo del modo "traza el muro" de Android.
struct WallTraceSheet: View {
    let center: CLLocationCoordinate2D
    let initial: [CLLocationCoordinate2D]
    let onDone: ([CLLocationCoordinate2D]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var points: [CLLocationCoordinate2D] = []
    @State private var started = false

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                MapLibreView(
                    center: center, zoom: 17, markers: [], style: .topo,
                    onMapTap: { points.append($0) },
                    polylines: points.count >= 2
                        ? [CumbrePolyline(id: "trace", coordinates: points, color: UIColor(Cumbre.terra), width: 5)]
                        : []
                )
                .ignoresSafeArea(edges: .bottom)

                VStack(spacing: 8) {
                    Text("✎ TRAZA EL MURO · \(points.count) PUNTOS · TOCA LA BASE DEL MURO")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10).background(Cumbre.terra)
                    HStack(spacing: 10) {
                        Button { if !points.isEmpty { points.removeLast() } } label: {
                            Text("↶ DESHACER").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 10).background(.white)
                        }.buttonStyle(.plain).opacity(points.isEmpty ? 0.4 : 1).disabled(points.isEmpty)
                        Button { onDone(points); dismiss() } label: {
                            Text("✓ LISTO").font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 10).background(Cumbre.terra)
                        }.buttonStyle(.plain).opacity(points.count < 2 ? 0.4 : 1).disabled(points.count < 2)
                    }
                    .padding(.horizontal, 10)
                }
                .padding(.top, 8)
            }
            .navigationTitle("Trazar muro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
        .onAppear { if !started { started = true; points = initial } }
    }
}

/// Selector segmentado genérico (dos+ opciones) — geometría PUNTO/MURO y sentido.
struct WallSeg: View {
    let options: [(String, String)]
    @Binding var selected: String
    var body: some View {
        HStack(spacing: 8) {
            ForEach(options, id: \.0) { value, label in
                let on = selected == value
                Button { selected = value } label: {
                    Text(label).font(Cumbre.mono(12, .bold)).tracking(0.6)
                        .foregroundStyle(on ? .white : Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(on ? Cumbre.terra : Color.clear)
                        .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
    }
}

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
                    Text(isWall ? "Las fotos se recorren en este orden (\(direction == "LTR" ? "izq→der" : "der→izq")) para numerar las vías del muro."
                         : "Ordena las fotos de la piedra.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(.bottom, 4)
                    ForEach(0..<facePhotos.count, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 10) {
                                Text("\(i + 1)").font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                                    .frame(width: 28, height: 28).background(Circle().fill(Cumbre.terra))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("FOTO \(i + 1) · \(faceBlocks[i].count) vías")
                                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    Text(expanded == i ? "▾ ocultar" : "▸ ver vías")
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
