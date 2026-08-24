import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Editor unificado de vias de una piedra existente. Reparto de ProposeFlow.swift.

struct EditLinesSheet: View {
    let block: Block
    let schoolId: String
    /// Cara que se abre primero (deep-link "corregir esta vía"): la de esa vía.
    var focusVia: String? = nil
    /// Otras piedras/sectores + mi ubicación, para orientarme al re-trazar el
    /// muro (contexto de solo lectura). Los pasa SchoolMapSection.
    var contextMarkers: [CumbreMarker] = []
    /// Declarado el ÚLTIMO para que el closure final del caller enlace con él
    /// sin ambigüedad (focusVia/contextMarkers tienen default y van antes).
    let onDone: (Bool) -> Void
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
    /// El envío falló: se ofrece guardarlo para mandarlo con cobertura.
    @State private var ofrecerGuardarOffline = false
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
    // "Guardar y terminar luego" al EDITAR (ya existía al crear una piedra
    // nueva) — Rodrigo, 2026-08-21.
    @State private var preguntandoGuardarEdicion = false
    @State private var hayBorrador = false
    @State private var borradorPendiente: EditBlockDraftStore.Draft?
    /// Vía cuya ficha está ABIERTA. Solo una a la vez: el resto se pliegan a
    /// una fila para no tener que scrollear formularios ya rellenados.
    @State private var expandedVia: UUID? = nil
    // ORIENTACIÓN desde el editor (Álvaro, 2026-08-24: "al editar molaría que
    // saliera lo de la orientación, poder orientar cada foto"). Se reutiliza la
    // VOTACIÓN comunitaria que ya existe, no el payload de la propuesta: el
    // voto entra al momento en vez de esperar a que un admin apruebe, y es
    // además la única vía que el backend aplica sobre una piedra ya creada
    // (orientationsJson solo se lee al materializar una piedra NUEVA).
    @StateObject private var community = CommunityVoteStore()
    @State private var orientationTarget: OrientationTarget? = nil

    /// Al editar una piedra ya existente siempre hay al menos una vía con
    /// nombre/grado (viene del servidor) — no vale mirar solo si hay foto
    /// nueva, si no cancelar tras editar SOLO el nombre o el grado de una vía
    /// no ofrecía guardar nada (Rodrigo, 2026-08-22: "no sale nada de
    /// guardar editando"). Foto nueva O cualquier vía con datos cuenta.
    private var hayContenidoSinEnviar: Bool {
        !facePicked.isEmpty || faceBlocks.contains {
            $0.contains { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty
                || $0.grade != nil || !$0.line.isEmpty }
        }
    }

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

    /// Añade una vía vacía a la cara actual y abre SU ficha (las demás se pliegan).
    private func nuevaVia() {
        guard faceBlocks.indices.contains(faceIdx) else { return }
        let v = BoulderBlockForm(facePhoto: currentPhoto)
        faceBlocks[faceIdx].append(v)
        expandedVia = v.id
    }

    /// Miniatura de una cara: la foto nueva sin enviar si la hay, si no la del
    /// servidor. Sin foto → marcador gris (una cara recién añadida).
    @ViewBuilder private func faceThumb(_ i: Int) -> some View {
        if let img = facePicked[i] {
            Image(uiImage: img).resizable().scaledToFill()
        } else if let p = facePhotos.indices.contains(i) ? facePhotos[i] : nil,
                  !p.isEmpty, let u = URL(string: p) {
            AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: {
                Cumbre.paper.overlay(ProgressView().scaleEffect(0.6))
            }
        } else {
            Cumbre.paper.overlay(
                Image(systemName: "photo").font(.system(size: 16)).foregroundStyle(Cumbre.ink3))
        }
    }

    /// Vía plegada: una sola línea con lo justo para reconocerla.
    @ViewBuilder private func viaCompacta(idx: Int, via: BoulderBlockForm) -> some View {
        let titulo = via.name.trimmingCharacters(in: .whitespaces).isEmpty
            ? "Sin nombre" : via.name
        HStack(spacing: 8) {
            Text("\(wallNumber(idx) ?? idx + 1)").font(Cumbre.mono(11, .bold))
                .foregroundStyle(GradeColor.style(via.grade).dark ? .black : .white)
                .frame(width: 22, height: 22)
                .background(Circle().fill(GradeColor.color(via.grade)))
            Text(via.grade.map { "\(titulo) · \($0)" } ?? titulo)
                .font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                .lineLimit(1)
            if !via.line.isEmpty {
                Image(systemName: "scribble").font(.system(size: 11)).foregroundStyle(Cumbre.ok)
            }
            Spacer()
            Button { expandedVia = via.id } label: {
                Image(systemName: "pencil").font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .frame(width: 32, height: 32)
            }.buttonStyle(.plain)
            Button { faceBlocks[faceIdx].removeAll { $0.id == via.id } } label: {
                Image(systemName: "xmark").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    .frame(width: 32, height: 32)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture { expandedVia = via.id }
    }

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
                                .lineLimit(1).minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                    .stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                        Text(tracedPath.isEmpty ? "Se conserva el trazado actual si no lo re-trazas." : "Se enviará el trazado nuevo.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    // ── Caras (fotos): pestañas + añadir ──────────────────────────
                    Text("FOTOS DE LA PIEDRA").eyebrow()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            // Pestañas CON MINIATURA: solo "FOTO 1 / FOTO 2" no
                            // dice cuál es cuál — en Android sí se ve la foto
                            // (Álvaro, 2026-08-24). Ahora se elige la cara
                            // mirándola, no adivinando por el número.
                            ForEach(0..<facePhotos.count, id: \.self) { i in
                                let on = i == faceIdx
                                Button { selectedFace = i } label: {
                                    VStack(spacing: 4) {
                                        faceThumb(i)
                                            .frame(width: 52, height: 52)
                                            .clipShape(RoundedRectangle(cornerRadius: 12))
                                            .overlay(RoundedRectangle(cornerRadius: 12)
                                                .stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: on ? 2 : 1))
                                        Text("FOTO \(i + 1)").font(Cumbre.mono(10, .bold))
                                            .foregroundStyle(on ? Cumbre.terra : Cumbre.ink2)
                                    }
                                }.buttonStyle(.plain)
                            }
                            Button {
                                facePhotos.append(nil); faceBlocks.append([]); selectedFace = facePhotos.count - 1
                            } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: "plus").font(.system(size: 18)).foregroundStyle(Cumbre.terra)
                                        .frame(width: 52, height: 52)
                                        .overlay(RoundedRectangle(cornerRadius: 12)
                                            .stroke(Cumbre.rule, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                                    Text(NSLocalizedString("propose_add_photo", comment: ""))
                                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                                        .lineLimit(1)
                                }
                            }.buttonStyle(.plain)
                        }
                    }
                    if facePhotos.count > 1 {
                        HStack(spacing: 12) {
                            Button { showReorder = true } label: {
                                Text("↕ REORDENAR FOTOS").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                                    .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                        .stroke(Cumbre.terra, lineWidth: 1))
                            }.buttonStyle(.plain)
                            Button { removeFace(faceIdx) } label: {
                                // No es solo la foto: se va la foto Y sus vías. "Quitar
                                // foto" sonaba a que solo tocaba la imagen (para eso ya
                                // está "CAMBIAR FOTO DE ESTA CARA" arriba) — Rodrigo,
                                // 2026-08-20.
                                Text("✕ ELIMINAR CARA \(faceIdx + 1)").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.bad)
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                    .padding(.horizontal, 10).padding(.vertical, 8)
                                    .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                        .stroke(Cumbre.bad, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }

                    // ── Orientación (voto inmediato, no pasa por el admin) ────────
                    HStack(spacing: 8) {
                        VotableChip(text: community.summaryFor(nil)?.consensus
                                    .map { "PIEDRA MIRA AL " + $0 } ?? "ORIENTAR LA PIEDRA") {
                            orientationTarget = OrientationTarget(photoIndex: nil)
                        }
                        if facePhotos.count > 1 {
                            VotableChip(text: community.summaryFor(faceIdx)?.consensus
                                        .map { "CARA \(faceIdx + 1): " + $0 } ?? "ORIENTAR ESTA CARA") {
                                orientationTarget = OrientationTarget(photoIndex: faceIdx)
                            }
                        }
                    }

                    // VÍAS DE LA CARA: una sola FICHA ABIERTA a la vez. Las ya
                    // rellenadas se pliegan a una fila de una línea, así que
                    // añadir la quinta vía no obliga a scrollear los cuatro
                    // formularios anteriores (Álvaro, 2026-08-24).
                    if faceBlocks.indices.contains(faceIdx) {
                        Text("VÍAS EN ESTA FOTO (\(faceBlocks[faceIdx].count))").eyebrow()
                        ForEach(Array(faceBlocks[faceIdx].enumerated()), id: \.element.id) { idx, via in
                            if via.id == expandedVia {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(faceBlocks[faceIdx][idx].existingLineId != nil ? "VÍA EXISTENTE" : "NUEVA VÍA")
                                        .font(Cumbre.mono(9, .bold))
                                        .foregroundStyle(faceBlocks[faceIdx][idx].existingLineId != nil ? Cumbre.ink3 : Cumbre.terra)
                                    BoulderBlockRow(block: $faceBlocks[faceIdx][idx], index: idx,
                                                    number: wallNumber(idx),
                                                    onDelete: {
                                                        faceBlocks[faceIdx].remove(at: idx)
                                                        expandedVia = nil
                                                    })
                                    HStack(spacing: 8) {
                                        Button { expandedVia = nil } label: {
                                            Text("LISTO").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                                .foregroundStyle(Cumbre.ink2)
                                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                                    .stroke(Cumbre.rule, lineWidth: 1))
                                        }.buttonStyle(.plain)
                                        Button { nuevaVia() } label: {
                                            Text("AÑADIR OTRA +").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                                .foregroundStyle(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                                .background(Cumbre.terraFill,
                                                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                                        }.buttonStyle(.plain)
                                    }
                                }
                            } else {
                                viaCompacta(idx: idx, via: faceBlocks[faceIdx][idx])
                            }
                        }
                    }

                    if expandedVia == nil {
                        Button { nuevaVia() } label: {
                            Text("+ NUEVA VÍA EN ESTA FOTO").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                    .stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Cambiar la foto de esta cara (mejorarla). Si eliges una nueva,
                    // todas las vías de la cara se moverán a ella y conviene
                    // redibujarlas. Si no eres admin, el admin la revisará.
                    // Va JUNTO a la vista previa de la cara para que se vea qué
                    // foto se está sustituyendo, en vez de un botón suelto.
                    HStack(spacing: 10) {
                        faceThumb(faceIdx)
                            .frame(width: 44, height: 44)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(facePicked[faceIdx] != nil ? "Foto nueva sin enviar"
                                 : (hasPhoto ? "Foto actual de la cara \(faceIdx + 1)" : "Esta cara no tiene foto"))
                                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                            if facePicked[faceIdx] != nil {
                                Text("Redibuja las líneas sobre ella.")
                                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            }
                        }
                        Spacer()
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            Text(facePicked[faceIdx] == nil ? "CAMBIAR" : "OTRA")
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                    .stroke(Cumbre.terra, lineWidth: 1))
                        }
                    }
                    .padding(10)
                    .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))

                    if hasPhoto || facePicked[faceIdx] != nil {
                        Button { showEditor = true } label: {
                            Text("✎ DIBUJAR / EDITAR SOBRE ESTA FOTO")
                                .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(.white)
                                .lineLimit(1).minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                        }.buttonStyle(.plain)
                    } else {
                        Text("Esta cara no tiene foto, no puedes dibujar líneas.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    if let sendError {
                        Text(sendError).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Editar vías")
            .navigationBarTitleDisplayMode(.inline)
            // Cancelar y Enviar viven ARRIBA y no se mueven con el scroll: el
            // formulario es largo y buscar el botón de enviar al fondo era un
            // viaje (Álvaro, 2026-08-24). Mismo patrón que BlockInfoSheet.
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_cancel", comment: "")) {
                        if hayContenidoSinEnviar { preguntandoGuardarEdicion = true }
                        else { dismiss(); onDone(false) }
                    }.foregroundStyle(Cumbre.ink3)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await send() } } label: {
                        if sending {
                            ProgressView().tint(.white)
                                .padding(.horizontal, 18).padding(.vertical, 6)
                                .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                        } else {
                            Text(sendError != nil ? "REINTENTAR" : "ENVIAR")
                                .font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                        }
                    }.buttonStyle(.plain).disabled(sending)
                }
            }
            // Deslizar hacia abajo NO puede tirar el trabajo en silencio: con
            // algo editado, el gesto se desactiva y hay que usar "Cancelar",
            // que es quien pregunta. Mismo patrón que BoulderFormSheet
            // (Rodrigo, 2026-08-22: "si cierro la pestaña bajando... no sale
            // nada de guardar editando").
            .interactiveDismissDisabled(hayContenidoSinEnviar)
            .alert("¿Guardar para terminar luego?", isPresented: $preguntandoGuardarEdicion) {
                Button("GUARDAR") {
                    EditBlockDraftStore.save(.init(
                        blockId: block.id, faceBlocks: faceBlocks, facePicked: facePicked,
                        savedAt: Date().timeIntervalSince1970))
                    dismiss(); onDone(false)
                }
                Button("DESCARTAR", role: .destructive) {
                    EditBlockDraftStore.clear(blockId: block.id)
                    dismiss(); onDone(false)
                }
            } message: {
                Text("Se queda guardado en este móvil. No se envía a nadie hasta que lo termines.")
            }
            .alert("Tienes cambios sin enviar", isPresented: $hayBorrador) {
                Button("CONTINUAR EDITANDO") {
                    if let d = borradorPendiente {
                        faceBlocks = d.faceBlocks
                        for (idx, img) in d.facePicked { facePicked[idx] = img }
                    }
                }
                Button("DESCARTAR", role: .destructive) {
                    EditBlockDraftStore.clear(blockId: block.id)
                }
            } message: {
                Text("Dejaste esta piedra a medias de editar. ¿Sigues donde lo dejaste o empiezas de cero?")
            }
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
                // Por ID primero, por nombre si no (mismo motivo que
                // scrollFaceIndex en BlockInfoSheet: focusVia = openVia, que
                // desde 2026-08-17 puede llegar como id de vía, no nombre).
                if let v = focusVia?.trimmingCharacters(in: .whitespaces), !v.isEmpty {
                    let hit = faces.firstIndex(where: { f in f.lines.contains { $0.id == v } })
                        ?? faces.firstIndex(where: { f in
                            f.lines.contains { $0.name.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(v) == .orderedSame }
                        })
                    if let hit {
                        selectedFace = hit
                        // Con las fichas plegadas, el deep-link "corregir esta
                        // vía" tiene que abrir SU ficha, no dejarlas todas
                        // cerradas.
                        if let j = faces[hit].lines.firstIndex(where: {
                            $0.id == v || $0.name.trimmingCharacters(in: .whitespaces)
                                .caseInsensitiveCompare(v) == .orderedSame
                        }), faceBlocks.indices.contains(hit),
                           faceBlocks[hit].indices.contains(j) {
                            expandedVia = faceBlocks[hit][j].id
                        }
                    }
                }
            }
            // Consenso de orientación actual, para que los chips digan hacia
            // dónde mira ya la piedra en vez de un genérico "ORIENTAR".
            Task { await community.loadOrientation(blockId: block.id) }
            // ¿Había algo a medias de la última vez que se cerró sin enviar?
            if let borrador = EditBlockDraftStore.load(blockId: block.id) {
                hayBorrador = true
                borradorPendiente = borrador
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            let idx = faceIdx
            // @MainActor OBLIGATORIO: facePicked es @State y se pinta con él.
            // Sin esto se escribía fuera del hilo de la interfaz y SwiftUI no se
            // enteraba: al añadir una cara NUEVA (sin foto previa en el
            // servidor) la imagen no aparecía y el botón de dibujar se quedaba
            // apagado, sin ningún mensaje. Las caras que YA tenían foto
            // disimulaban el fallo porque se pintan desde su URL remota
            // (reportado por Rodrigo con la 3ª foto, build 142).
            Task { @MainActor in
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
        // No se pudo enviar: se ofrece guardarlo en vez de perder el trabajo.
        .alert("No se pudo enviar", isPresented: $ofrecerGuardarOffline) {
            Button("Guardar y enviar luego") { Task { await guardarSinCobertura() } }
            Button("Reintentar ahora") { Task { await send() } }
            Button("Cancelar", role: .cancel) { }
        } message: {
            Text("Puedes guardarlo en este móvil y se enviará solo en cuanto recuperes cobertura. "
                 + "Tus vías y fotos no se pierden.")
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
        .sheet(item: $orientationTarget) { t in
            OrientationVoteSheet(store: community, blockId: block.id, photoIndex: t.photoIndex)
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showReorder) {
            ReorderFacesSheet(facePhotos: $facePhotos, faceBlocks: $faceBlocks,
                              facePicked: $facePicked, isWall: isWall, direction: direction)
        }
        .sheet(isPresented: $showTrace) {
            let seed = tracedPath.isEmpty ? parseWallPath(block.path) : tracedPath
            WallTraceSheet(center: CLLocationCoordinate2D(latitude: block.lat, longitude: block.lon),
                           initial: seed,
                           contextMarkers: contextMarkers.filter { $0.id != block.id }) { tracedPath = $0 }
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
                // Ofrecer guardarlo TAMBIÉN aquí. Sin cobertura este es el
                // primer sitio donde se falla —subir la foto— y hacer `return`
                // sin más se saltaba el ofrecimiento: el usuario solo veía
                // "reintentar" y no podía dejarlo guardado (Rodrigo, build 144).
                ofrecerGuardarOffline = true
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
            direction: direction, orientationsJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok {
            EditBlockDraftStore.clear(blockId: block.id)   // enviado: el borrador ya no aplica
            dismiss(); onDone(true)
        }
        else {
            // No se pudo enviar: en vez de dejarlo en "reinténtalo" (y que el
            // usuario tenga que repetirlo todo en casa), se ofrece GUARDARLO
            // para mandarlo al recuperar cobertura. Se PREGUNTA en vez de
            // encolar solo: si el servidor rechazó por otra cosa, encolar a
            // ciegas lo reintentaría eternamente.
            ofrecerGuardarOffline = true
        }
    }

    /// Guarda la edición para enviarla al recuperar cobertura. Las fotos nuevas
    /// se copian a disco (las de las caras sin tocar conservan su URL), igual
    /// que al proponer una piedra nueva sin red.
    private func guardarSinCobertura() async {
        let fm = FileManager.default
        let dir = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("outbox-photos", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)

        var facesArr: [[String: Any]] = []
        for (i, faceVias) in faceBlocks.enumerated() {
            var localPath: Any = NSNull()
            if let img = facePicked[i], let data = img.jpegData(compressionQuality: 0.85) {
                let f = dir.appendingPathComponent(UUID().uuidString + ".jpg")
                if (try? data.write(to: f)) != nil { localPath = f.path }
            }
            let vias: [[String: Any]] = faceVias.compactMap { b in
                guard b.grade != nil || !b.name.isEmpty || !b.line.isEmpty else { return nil }
                return [
                    "name": b.name,
                    "grade": b.grade ?? NSNull(),
                    "startType": b.startType ?? NSNull(),
                    "points": b.line.map { [Double($0.x), Double($0.y)] },
                    "targetLineId": b.existingLineId ?? NSNull()
                ]
            }
            facesArr.append([
                "localPhotoPath": localPath,
                "existingPhotoPath": b_existingPhoto(i) ?? NSNull(),
                "vias": vias
            ])
        }
        let payload: [String: Any] = [
            "schoolId": schoolId,
            "targetBlockId": block.id,
            "lat": block.lat, "lon": block.lon,
            "geometry": geometry,
            "pathJson": isWall ? (tracedPath.isEmpty ? (block.path ?? NSNull()) : buildPathJson(tracedPath)) : NSNull(),
            "direction": direction,
            "faces": facesArr
        ]
        guard let d = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: d, encoding: .utf8) else { return }
        try? await AppDependencies.shared.container
            .enqueueBlockEditContribution(schoolId: schoolId, payloadJson: json)
        EditBlockDraftStore.clear(blockId: block.id)   // encolado: el borrador ya no aplica
        dismiss(); onDone(false)
    }

    /// Foto que ya tenía esa cara en el servidor (nil si es cara nueva).
    private func b_existingPhoto(_ i: Int) -> String? {
        let caras = block.facesOrDerived()
        guard i < caras.count else { return nil }
        let p = caras[i].photoPath
        return (p?.isEmpty == false) ? p : nil
    }
}
