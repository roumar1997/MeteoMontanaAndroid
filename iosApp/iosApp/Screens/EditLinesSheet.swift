import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Editor unificado de vias de una piedra existente. Reparto de ProposeFlow.swift.

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
                                                onDelete: { faceBlocks[faceIdx].remove(at: idx) })
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
                                         facePhoto: f.photoPath ?? block.photoPath,
                                         descriptionText: l.lineDescription ?? "",
                                         variant: l.variant ?? "")
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
                // loadTransferable a veces devuelve nil (foto en iCloud aún sin
                // descargar, fallo transitorio): antes fallaba EN SILENCIO y la
                // miniatura no aparecía. Ahora avisamos para reintentar.
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    facePicked[idx] = img
                    sendError = nil
                } else {
                    sendError = "No se pudo cargar la foto elegida (¿está en iCloud?). Elígela otra vez."
                }
                pickerItem = nil   // permite volver a elegir la MISMA foto
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
            // No seguir si una foto cambiada no sube (evita mezclar caras).
            guard let url = try? await StorageUploader.uploadBoulderPhoto(img, schoolId: schoolId, index: i) else {
                sending = false
                sendError = "No se pudo subir la foto \(i + 1). Revisa la conexión y reinténtalo."
                return
            }
            newFacePhoto[i] = url
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
                // Vía con ALGÚN dato (nombre, grado o trazo) → se conserva/corrige.
                // Completamente vacía → se OMITE: si era existente el backend la
                // borra (reconcilia omitidas), evitando vías fantasma imborrables.
                let hasData = v.grade != nil || !v.name.isEmpty || !v.line.isEmpty
                if hasData { payload.append(v) }
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
