import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Formulario de "Nueva piedra" (caras/vias) + fila de via + reordenar fotos.
// Reparto del antiguo ProposeFlow.swift de 1.345 lineas.

struct BoulderFormSheet: View {
    let schoolId: String
    let coord: CLLocationCoordinate2D
    let sectors: [Block]              // ZONEs de la escuela (sector opcional)
    /// Piedras/sectores/parkings + mi ubicación, para orientarme al trazar el
    /// muro (contexto de solo lectura). Los pasa SchoolMapSection.
    var contextMarkers: [CumbreMarker] = []
    /// Foto de "Enviar piedra": entra como primera cara, con la orientación que
    /// sugiere el rumbo con el que se hizo (si la foto lo traía).
    var seedPhoto: UIImage? = nil
    var seedAspect: String? = nil
    /// Piedra a medias guardada en este móvil, si la hay (la carga el mapa).
    var borrador: BoulderDraftStore.Draft? = nil
    let onDone: (Bool) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var sectorId: String? = nil
    @State private var discipline = "BOULDER"   // BOULDER (bloque) / ROUTE (vía)
    // Una piedra grande no cabe en una foto → varias CARAS (foto + sus vías).
    @State private var faces: [BoulderFaceForm] = [BoulderFaceForm()]
    /// La foto de la semilla se coloca UNA vez: repetirlo en cada refresco
    /// borraría lo que el usuario vaya escribiendo.
    @State private var semillaPuesta = false
    /// Paso fino (2º): aquí se lee el número de grados.
    @StateObject private var brujula = HeadingProvider(stepDegrees: 2)
    @State private var selectedFace = 0
    @State private var pickerItem: PhotosPickerItem?
    @State private var showEditor = false
    @State private var sending = false
    // Error de envío: al fallar NO se cierra el sheet — se muestra el error y
    // el botón pasa a REINTENTAR (la foto y las vías siguen ahí).
    @State private var sendError: String? = nil
    @State private var queued = false
    // Muro/sector plegados (opciones avanzadas del formulario).
    @State private var advancedOpen = false
    // F: orientación de la piedra según el autor (opcional; su primer voto).
    @State private var blockOrientation: String? = nil
    // Geometría/sentido + trazado del muro (PUNTO por defecto).
    @State private var geometry = "POINT"
    @State private var direction = "LTR"
    @State private var wallPath: [CLLocationCoordinate2D] = []
    @State private var showTrace = false
    /// Cerrar una piedra a medias: preguntar, no tirar el trabajo.
    @State private var preguntandoGuardar = false

    private var faceIdx: Int { min(max(selectedFace, 0), faces.count - 1) }

    private static let aspects = Aspect.shared.ALL

    /// F: chips de orientación (mismo lenguaje discontinuo-terra de votable).
    @ViewBuilder
    private func orientationChips(label: String, selected: String?,
                                  onPick: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(Cumbre.mono(9, .bold)).tracking(1.2)
                .foregroundStyle(Cumbre.ink3)
            // Brújula también AQUÍ, creando la piedra: es cuando estás delante
            // de ella y sabes hacia dónde mira. Informa; el chip lo eliges tú.
            if let rumbo = brujula.heading {
                HStack(spacing: 8) {
                    CompassDial(headingDegrees: Double(rumbo))
                        .frame(width: 64, height: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Estás mirando al " + Aspect.shared.fromDegrees(degrees: Float(rumbo)) +
                             " · " + Aspect.shared.degreesLabel(degrees: Float(rumbo)))
                        Text("Si estás mirando la pared, ella mira al contrario.")
                    }
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.bottom, 4)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Self.aspects, id: \.self) { a in
                        Button { onPick(a) } label: {
                            Text(a).font(Cumbre.mono(10, .bold))
                                .foregroundStyle(selected == a ? .white : Cumbre.terra)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(RoundedRectangle(cornerRadius: 6)
                                    .fill(selected == a ? Cumbre.terra : Cumbre.paper))
                                .overlay(RoundedRectangle(cornerRadius: 6)
                                    .stroke(Cumbre.terra.opacity(selected == a ? 1 : 0.55),
                                            style: StrokeStyle(lineWidth: 1,
                                                               dash: selected == a ? [] : [4, 3])))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    /// F: {"block":"NE","faces":{"0":"N"}} — la orientación que puso el autor
    /// se convierte en su PRIMER voto al aprobarse. nil si no marcó nada.
    private func buildOrientationsJson() -> String? {
        var parts: [String] = []
        if let b = blockOrientation { parts.append("\"block\":\"\(b)\"") }
        let facesDict = faces.enumerated().compactMap { idx, f in
            f.orientation.map { "\"\(idx)\":\"\($0)\"" }
        }
        if !facesDict.isEmpty {
            parts.append("\"faces\":{" + facesDict.joined(separator: ",") + "}")
        }
        return parts.isEmpty ? nil : "{" + parts.joined(separator: ",") + "}"
    }
    /// Foto del formulario tal y como está ahora mismo.
    private func borradorActual() -> BoulderDraftStore.Draft {
        BoulderDraftStore.Draft(
            schoolId: schoolId, lat: coord.latitude, lon: coord.longitude,
            name: name, discipline: discipline, geometry: geometry,
            direction: direction, sectorId: sectorId, orientation: blockOrientation,
            path: wallPath.map { [$0.latitude, $0.longitude] },
            faces: faces, savedAt: Date().timeIntervalSince1970
        )
    }

    /// Deja el formulario como estaba al guardarlo.
    private func aplicarBorrador(_ b: BoulderDraftStore.Draft) {
        name = b.name
        discipline = b.discipline
        geometry = b.geometry
        direction = b.direction
        sectorId = b.sectorId
        blockOrientation = b.orientation
        wallPath = b.path.compactMap {
            $0.count >= 2 ? CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) : nil
        }
        faces = b.faces
    }

    private var isWall: Bool { geometry == "LINE" }

    var body: some View {
        contenido
            .onAppear {
                brujula.start()
                // Continuar una piedra a medias: manda sobre todo lo demas, es
                // trabajo que el usuario ya habia hecho.
                if !semillaPuesta, let b = borrador {
                    semillaPuesta = true
                    aplicarBorrador(b)
                    return
                }
                guard !semillaPuesta, let foto = seedPhoto else { return }
                semillaPuesta = true
                faces = [BoulderFaceForm(photo: foto, orientation: seedAspect)]
                // Tambien la de la piedra: es la que se ve con una sola foto.
                if blockOrientation == nil { blockOrientation = seedAspect }
            }
            // El sensor gasta batería: solo mientras el formulario está abierto.
            .onDisappear { brujula.stop() }
    }

    private var contenido: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Rellena los datos de la piedra. Podrás añadir fotos y dibujar las líneas de cada vía sobre ellas.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)

                    FirstTimeHint(
                        hintKey: "boulder_form_guide",
                        text: "Pasos: 1) Añade una foto de la piedra, 2) marca sus vías con grado, 3) dibuja las líneas sobre la foto, 4) envía."
                    )

                    // ── Modalidad: BLOQUE o VÍA ────────────────────────────────────
                    VStack(alignment: .leading, spacing: 6) {
                        Text("MODALIDAD").eyebrow()
                        Text("¿Es una piedra de boulder (sentadas, bloques cortos) o de vía (escalada deportiva, más larga)?")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        DisciplineSelector(selected: $discipline)
                    }

                    // F: orientación de la piedra según el autor (opcional).
                    // Al aprobarse se convierte en su PRIMER voto; la
                    // comunidad puede seguir votando después.
                    orientationChips(label: "ORIENTACIÓN DE LA PIEDRA (opcional)",
                                     selected: blockOrientation) { a in
                        blockOrientation = blockOrientation == a ? nil : a
                    }

                    // ── Opciones avanzadas: muro, sector y numeración (plegado) ──
                    // El 90% de las propuestas son una piedra normal: esto vive
                    // plegado para no estorbar (espejo del formulario Android).
                    Button { withAnimation { advancedOpen.toggle() } } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Muro, sector y numeración").font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                Text(isWall ? "Muro de \(wallPath.count) puntos" + (sectorId != nil ? " · con sector" : "")
                                     : (sectorId != nil ? "Con sector asignado" : "Solo si es una pared larga o va en un sector"))
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            Spacer()
                            Image(systemName: advancedOpen ? "chevron.up" : "chevron.down")
                                .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        }
                        .padding(12)
                        .background(Cumbre.paper)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(advancedOpen ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain)
                    if advancedOpen {
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
                    // Mismo selector que "aportar desde una foto": el de
                    // SwiftUI entrega una copia SIN ubicación, y entonces la
                    // orientación no se podría sugerir nunca.
                    Button {
                        let idx = faceIdx
                        presentPhotoPickerResult { result in
                            guard let result else {
                                // ANTES: return MUDO. Si el selector no lograba
                                // abrirse (con varias hojas apiladas se reintenta
                                // 15 veces y luego se rinde), el usuario se
                                // quedaba sin foto, sin aviso, y con el botón de
                                // dibujar apagado sin saber por qué — reportado
                                // por Rodrigo al añadir la 3ª foto (build 142).
                                Task { @MainActor in
                                    sendError = "No se pudo abrir el selector de fotos. Vuelve a intentarlo."
                                }
                                return
                            }
                            Task { @MainActor in
                                // readImagen y NO read: aquí la piedra ya está
                                // colocada en el mapa, así que la foto solo
                                // aporta la IMAGEN. Exigirle ubicación rechazaba
                                // fotos válidas —las que te pasa otra persona no
                                // la llevan— con un "no se pudo cargar la foto"
                                // que además despistaba (Rodrigo, build 143).
                                guard let donde = await PhotoExifReader.readImagen(result) else {
                                    sendError = "No se pudo cargar la foto elegida. Inténtalo otra vez."
                                    return
                                }
                                faces[idx].photo = donde.image
                                // La orientación sale de ESTA foto, si la trae:
                                // cada cara mira a donde mire su pared. Una foto
                                // sin rumbo simplemente no la sugiere.
                                if let sugerida = PhotoPlacement.shared.aspectFromCameraDirection(
                                    cameraDegrees: donde.cameraDegrees.map { KotlinFloat(float: $0) }) {
                                    if faces[idx].orientation == nil { faces[idx].orientation = sugerida }
                                    // Y la de la PIEDRA, que es el chip que se
                                    // ve con una sola foto.
                                    if blockOrientation == nil { blockOrientation = sugerida }
                                }
                                sendError = nil
                            }
                        }
                    } label: {
                        Text(faces[faceIdx].photo == nil ? "SELECCIONAR FOTO" : "CAMBIAR FOTO")
                            .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)

                    // F: orientación de ESTA cara (opcional). Solo tiene
                    // sentido con varias fotos; con una sola vale la general.
                    if faces.count > 1 {
                        orientationChips(label: "ORIENTACIÓN DE ESTA CARA (opcional)",
                                         selected: faces[faceIdx].orientation) { a in
                            faces[faceIdx].orientation =
                                faces[faceIdx].orientation == a ? nil : a
                        }
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

                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }

                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text(NSLocalizedString("propose_submit", comment: "")).font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Nueva piedra")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) {
                    // Una piedra son varios campos, una foto por cara y una
                    // linea por via: cerrar no puede tirarlo sin preguntar.
                    if BoulderDraftStore.tieneContenido(borradorActual()) {
                        preguntandoGuardar = true
                    } else {
                        dismiss(); onDone(false)
                    }
                }.foregroundStyle(Cumbre.ink3) } }
            // Deslizar hacia abajo NO puede tirar el trabajo en silencio: con
            // algo escrito, el gesto se desactiva y hay que usar "Cancelar",
            // que es quien pregunta (lo cazo Rodrigo: cancelar preguntaba, el
            // gesto no).
            .interactiveDismissDisabled(BoulderDraftStore.tieneContenido(borradorActual()))
            .alert("¿Guardar para terminar luego?", isPresented: $preguntandoGuardar) {
                Button("GUARDAR") {
                    BoulderDraftStore.save(borradorActual())
                    dismiss(); onDone(false)
                }
                Button("DESCARTAR", role: .destructive) {
                    BoulderDraftStore.clear(schoolId: schoolId)
                    dismiss(); onDone(false)
                }
            } message: {
                Text("Se queda guardada en este móvil. No se envía a nadie hasta que la termines.")
            }
            .alert("Guardada en tu móvil", isPresented: $queued) {
                Button("CERRAR") { dismiss(); onDone(false) }
            } message: {
                Text("Se enviará automáticamente en cuanto haya cobertura. No tienes que hacer nada.")
            }
            // No se pudo enviar (sin cobertura, lo normal): diálogo emergente en
            // vez de un texto rojo colado en el formulario, mismo estilo que el
            // de "¿Guardar también las fotos?" al descargar una escuela. Repetible
            // — reintentar puede volver a fallar y el diálogo vuelve a salir.
            .alert("No se pudo enviar",
                   isPresented: Binding(get: { sendError != nil }, set: { if !$0 { sendError = nil } })) {
                Button("Guardar y enviar con cobertura") { Task { await saveOffline() } }
                Button("Reintentar") { Task { await send() } }
            } message: {
                Text(sendError ?? "")
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            let idx = faceIdx
            // @MainActor OBLIGATORIO: `faces` es @State y de él se pinta la
            // miniatura y depende el botón de dibujar. Sin esto se escribía
            // fuera del hilo de la interfaz y SwiftUI no se enteraba (mismo
            // fallo que en EditLinesSheet, build 142).
            Task { @MainActor in
                // loadTransferable a veces devuelve nil (foto en iCloud sin bajar
                // aún): antes fallaba en silencio y la foto no aparecía.
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    faces[idx].photo = img
                    sendError = nil
                } else {
                    sendError = "No se pudo cargar la foto elegida (¿está en iCloud?). Elígela otra vez."
                }
                pickerItem = nil
            }
        }
        .sheet(isPresented: $showEditor) {
            if faces[faceIdx].photo != nil {
                TopoEditorView(photo: faces[faceIdx].photo, blocks: $faces[faceIdx].blocks)
            }
        }
        .sheet(isPresented: $showTrace) {
            WallTraceSheet(center: coord, initial: wallPath,
                           contextMarkers: contextMarkers) { wallPath = $0 }
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
                // Si la subida falla NO seguimos: antes se ponía nil en silencio y
                // esa cara se colapsaba en la FOTO 1 con las demás. Mejor abortar
                // y que el usuario reintente, sin mezclar caras.
                guard let url = try? await StorageUploader.uploadBoulderPhoto(photo, schoolId: schoolId, index: i) else {
                    sending = false
                    sendError = "No se pudo subir la foto \(i + 1). Revisa la conexión y reinténtalo (si no, las caras se mezclarían en una sola)."
                    return
                }
                photoByFace[face.id] = url
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
            direction: direction,
            orientationsJson: buildOrientationsJson())
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok {
            dismiss()
            onDone(true)
        } else {
            sendError = "No se pudo enviar. Revisa la conexión — la foto y las vías siguen aquí."
        }
    }

    /// Encola la propuesta entera (fotos copiadas a Documents/outbox-photos) para
    /// que ContributionOutboxFlusher.swift la suba al recuperar cobertura. El
    /// payload usa las mismas claves que QueuedBoulder de Android.
    private func saveOffline() async {
        let fm = FileManager.default
        let dir = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("outbox-photos", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)

        var facesArr: [[String: Any]] = []
        for face in faces {
            var path: Any = NSNull()
            if let img = face.photo, let data = img.jpegData(compressionQuality: 0.85) {
                let f = dir.appendingPathComponent(UUID().uuidString + ".jpg")
                if (try? data.write(to: f)) != nil { path = f.path }
            }
            let vias: [[String: Any]] = face.blocks.map { b in [
                "name": b.name,
                "grade": b.grade ?? NSNull(),
                "startType": b.startType ?? NSNull(),
                "points": b.line.map { [Double($0.x), Double($0.y)] },
                "targetLineId": b.existingLineId ?? NSNull()
            ] }
            facesArr.append(["localPhotoPath": path, "vias": vias])
        }
        let payload: [String: Any] = [
            "schoolId": schoolId,
            "lat": coord.latitude, "lon": coord.longitude,
            "name": name.isEmpty ? NSNull() : name,
            "sectorBlockId": sectorId ?? NSNull(),
            "discipline": discipline,
            "geometry": geometry,
            "pathJson": (isWall && !wallPath.isEmpty) ? buildPathJson(wallPath) : NSNull(),
            "direction": direction,
            "faces": facesArr
        ]
        guard let d = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: d, encoding: .utf8) else { return }
        try? await AppDependencies.shared.container.enqueueBoulderContribution(schoolId: schoolId, payloadJson: json)
        queued = true
    }
}

/// Editor de líneas: arrastra sobre la foto para trazar la línea del bloque
/// seleccionado (espejo de ContributionTopoDialog de Android).

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
                    let c = GradeColor.color(g)
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

