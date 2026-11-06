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

    @Environment(\.dismiss) var dismiss
    @State var name = ""
    @State var sectorId: String? = nil
    @State var discipline = "BOULDER"   // BOULDER (bloque) / ROUTE (vía)
    // Una piedra grande no cabe en una foto → varias CARAS (foto + sus vías).
    @State var faces: [BoulderFaceForm] = [BoulderFaceForm()]
    /// La foto de la semilla se coloca UNA vez: repetirlo en cada refresco
    /// borraría lo que el usuario vaya escribiendo.
    @State var semillaPuesta = false
    /// Paso fino (2º): aquí se lee el número de grados.
    @StateObject var brujula = HeadingProvider(stepDegrees: 2)
    @State var selectedFace = 0
    @State var pickerItem: PhotosPickerItem?
    // Elegir cámara o galería para la foto de una cara (paridad Android
    // BoulderFormDialog.kt) — antes solo se podía elegir de galería.
    @State var eligiendoOrigenFotoCara = false
    @State var showEditor = false
    @State var sending = false
    // Error de envío: al fallar NO se cierra el sheet — se muestra el error y
    // el botón pasa a REINTENTAR (la foto y las vías siguen ahí).
    @State var sendError: String? = nil
    @State var queued = false
    // Muro/sector plegados (opciones avanzadas del formulario).
    @State var advancedOpen = false
    // F: orientación de la piedra según el autor (opcional; su primer voto).
    @State var blockOrientation: String? = nil
    // Geometría/sentido + trazado del muro (PUNTO por defecto).
    @State var geometry = "POINT"
    @State var direction = "LTR"
    @State var wallPath: [CLLocationCoordinate2D] = []
    @State var showTrace = false
    /// Cerrar una piedra a medias: preguntar, no tirar el trabajo.
    @State var preguntandoGuardar = false
    /// Vía cuya ficha está ABIERTA. Solo una a la vez (ver viaCompacta).
    @State var expandedVia: UUID? = nil

    var faceIdx: Int { min(max(selectedFace, 0), faces.count - 1) }

    /// ¿Hay una vía ABIERTA en la foto que se está viendo? (el botón de añadir vía no puede depender de una abierta en OTRA foto)
    var expandedHere: Bool {
        faces.indices.contains(faceIdx) && faces[faceIdx].blocks.contains { $0.id == expandedVia }
    }

    static let aspects = Aspect.shared.ALL

    /// F: chips de orientación (mismo lenguaje discontinuo-terra de votable).
    @ViewBuilder
    func orientationChips(label: String, selected: String?,
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
                        Text(L("Estás mirando al %@ · %@", aspectLabel(Aspect.shared.fromDegrees(degrees: Float(rumbo))),
                               Aspect.shared.degreesLabel(degrees: Float(rumbo))))
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
    func buildOrientationsJson() -> String? {
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
    func borradorActual() -> BoulderDraftStore.Draft {
        BoulderDraftStore.Draft(
            schoolId: schoolId, lat: coord.latitude, lon: coord.longitude,
            name: name, discipline: discipline, geometry: geometry,
            direction: direction, sectorId: sectorId, orientation: blockOrientation,
            path: wallPath.map { [$0.latitude, $0.longitude] },
            faces: faces, savedAt: Date().timeIntervalSince1970
        )
    }

    /// Deja el formulario como estaba al guardarlo.
    func aplicarBorrador(_ b: BoulderDraftStore.Draft) {
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

    var isWall: Bool { geometry == "LINE" }

    /// Elegir de galería (mismo selector que "aportar desde una foto": lee
    /// EXIF para sugerir orientación por la dirección de la cámara). Extraído
    /// para poder ofrecerlo junto a "hacer foto ahora" en un confirmationDialog.
    func elegirFotoDeGaleria() {
        let idx = faceIdx
        presentPhotoPickerResult { result in
            guard let result else {
                // ANTES: return MUDO. Si el selector no lograba abrirse (con
                // varias hojas apiladas se reintenta 15 veces y luego se
                // rinde), el usuario se quedaba sin foto, sin aviso, y con el
                // botón de dibujar apagado sin saber por qué — reportado por
                // Rodrigo al añadir la 3ª foto (build 142).
                Task { @MainActor in
                    sendError = L("No se pudo abrir el selector de fotos. Vuelve a intentarlo.")
                }
                return
            }
            Task { @MainActor in
                // readImagen y NO read: aquí la piedra ya está colocada en el
                // mapa, así que la foto solo aporta la IMAGEN. Exigirle
                // ubicación rechazaba fotos válidas —las que te pasa otra
                // persona no la llevan— con un "no se pudo cargar la foto"
                // que además despistaba (Rodrigo, build 143).
                guard let donde = await PhotoExifReader.readImagen(result) else {
                    sendError = L("No se pudo cargar la foto elegida. Inténtalo otra vez.")
                    return
                }
                faces[idx].photo = donde.image
                // La orientación sale de ESTA foto, si la trae: cada cara
                // mira a donde mire su pared. Una foto sin rumbo simplemente
                // no la sugiere.
                if let sugerida = PhotoPlacement.shared.aspectFromCameraDirection(
                    cameraDegrees: donde.cameraDegrees.map { KotlinFloat(float: $0) }) {
                    if faces[idx].orientation == nil { faces[idx].orientation = sugerida }
                    // Y la de la PIEDRA, que es el chip que se ve con una
                    // sola foto.
                    if blockOrientation == nil { blockOrientation = sugerida }
                }
                sendError = nil
            }
        }
    }

    /// Cada cara nace con una vía vacía: si esa es la única, se abre sola — que
    /// haya que tocar "Sin nombre" para empezar sería un paso de más.
    func abrirViaInicialSiProcede() {
        let vias = faces[faceIdx].blocks
        if vias.count == 1, let v = vias.first, v.name.isEmpty, v.grade == nil, v.line.isEmpty {
            expandedVia = v.id
        }
    }

    /// Añade una vía vacía a la cara actual y abre SU ficha (las demás se pliegan).
    func nuevaVia() {
        let v = BoulderBlockForm()
        faces[faceIdx].blocks.append(v)
        expandedVia = v.id
    }

    /// Vía plegada: una sola línea con lo justo para reconocerla. Espejo del
    /// mismo componente de EditLinesSheet.
    @ViewBuilder func viaCompacta(idx: Int, via: BoulderBlockForm) -> some View {
        let titulo = via.name.trimmingCharacters(in: .whitespaces).isEmpty
            ? L("Sin nombre") : via.name
        HStack(spacing: 8) {
            Text("\(idx + 1)").font(Cumbre.mono(11, .bold))
                .foregroundStyle(GradeColor.style(via.grade).dark ? .black : .white)
                .frame(width: 22, height: 22)
                .background(Circle().fill(GradeColor.chip(via.grade)))
            Text(via.grade.map { "\(titulo) · \($0)" } ?? titulo)
                .font(.system(size: 13)).foregroundStyle(Cumbre.ink).lineLimit(1)
            if !via.line.isEmpty {
                Image(systemName: "scribble").font(.system(size: 11)).foregroundStyle(Cumbre.ok)
            }
            Spacer()
            Button { expandedVia = via.id } label: {
                Image(systemName: "pencil").font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .frame(width: 32, height: 32)
            }.buttonStyle(.plain)
            if faces[faceIdx].blocks.count > 1 {
                Button { faces[faceIdx].blocks.removeAll { $0.id == via.id } } label: {
                    Image(systemName: "xmark").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 32, height: 32)
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture { expandedVia = via.id }
    }

    var body: some View {
        contenido
            .onAppear {
                brujula.start()
                defer { abrirViaInicialSiProcede() }
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
            // Al cambiar de cara, abre su vía inicial si esa cara está en blanco.
            .onChange(of: selectedFace) { _, _ in
                expandedVia = nil
                abrirViaInicialSiProcede()
            }
            // El sensor gasta batería: solo mientras el formulario está abierto.
            .onDisappear { brujula.stop() }
    }

    var sectorName: String {
        guard let sectorId, let s = sectors.first(where: { $0.id == sectorId }) else { return L("Sin sector") }
        return s.name.isEmpty ? L("Zona") : s.name
    }

    func send() async {
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
                    sendError = L("No se pudo subir la foto %@. Revisa la conexión y reinténtalo (si no, las caras se mezclarían en una sola).", i + 1)
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
            sendError = L("No se pudo enviar. Revisa la conexión — la foto y las vías siguen aquí.")
        }
    }

    /// Encola la propuesta entera (fotos copiadas a Documents/outbox-photos) para
    /// que ContributionOutboxFlusher.swift la suba al recuperar cobertura. El
    /// payload usa las mismas claves que QueuedBoulder de Android.
    func saveOffline() async {
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

