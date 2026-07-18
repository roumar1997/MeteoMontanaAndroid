import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).
extension Block: Identifiable {}

/// "hace 3 h" / "hace 2 d" / "el 14/06/26" a partir de un epoch en ms.
func relativeUpdated(_ millis: Int64) -> String {
    guard millis > 0 else { return "" }
    let date = Date(timeIntervalSince1970: Double(millis) / 1000)
    let secs = Date().timeIntervalSince(date)
    if secs < 90 { return "hace un momento" }
    let mins = Int(secs / 60); if mins < 60 { return "hace \(mins) min" }
    let hours = Int(secs / 3600); if hours < 24 { return "hace \(hours) h" }
    let days = Int(secs / 86400); if days < 30 { return "hace \(days) d" }
    let f = DateFormatter(); f.dateFormat = "dd/MM/yy"
    return "el \(f.string(from: date))"
}

// Detalle de escuela — réplica fiel de ForecastBody.kt de Android:
// veredicto SÍ/NO "¿PUEDO ESCALAR HOY?" + ÍNDICE, banda de roca, heatmap,
// desglose de factores, tiempo actual, próximas 16 h, condiciones (8 celdas),
// próximos 7 días y mejor día. Datos del GetForecastUseCase compartido.

@MainActor
final class SchoolDetailViewModel: ObservableObject {
    @Published var forecast: Forecast?
    @Published var loading = true
    @Published var errorText: String?
    @Published var isFavorite = false
    @Published var notes: [Note] = []
    @Published var publishing = false
    @Published var monthlyScores: [Int] = []
    @Published var monthlyBestRange: String?
    @Published var isSaved = false          // guardada para offline
    @Published var savingOffline = false
    @Published var offlineForecast = false  // previsión mostrada desde caché (sin red)
    @Published var offlineSince: Int64?      // epoch ms de la última actualización cacheada

    private let savedSchools = AppDependencies.shared.container.savedSchools
    private let getBlocks = AppDependencies.shared.container.getBlocks
    private let getForecast: GetForecastUseCase
    private let getMyFavorites: GetMyFavoritesUseCase
    private let addFavorite: AddFavoriteUseCase
    private let removeFavorite: RemoveFavoriteUseCase
    private let getNotes: GetNotesUseCase
    private let createNote: CreateNoteUseCase

    init(
        getForecast: GetForecastUseCase = AppDependencies.shared.container.getForecast,
        getMyFavorites: GetMyFavoritesUseCase = AppDependencies.shared.container.getMyFavorites,
        addFavorite: AddFavoriteUseCase = AppDependencies.shared.container.addFavorite,
        removeFavorite: RemoveFavoriteUseCase = AppDependencies.shared.container.removeFavorite,
        getNotes: GetNotesUseCase = AppDependencies.shared.container.getNotes,
        createNote: CreateNoteUseCase = AppDependencies.shared.container.createNote
    ) {
        self.getForecast = getForecast
        self.getMyFavorites = getMyFavorites
        self.addFavorite = addFavorite
        self.removeFavorite = removeFavorite
        self.getNotes = getNotes
        self.createNote = createNote
    }

    func load(school: School) async {
        let schoolId = school.id
        loading = true; errorText = nil; offlineForecast = false; offlineSince = nil
        do {
            let f = try await getForecast.invoke(schoolId: schoolId)
            forecast = f
            // Cachea para verlo offline más tarde (stale-while-revalidate, como Android).
            try? await savedSchools?.cacheForecast(schoolId: schoolId, forecast: f)
        } catch {
            // Sin red: tira de la última previsión guardada/cacheada de esta escuela.
            if let cached = try? await savedSchools?.cachedForecast(schoolId: schoolId) {
                forecast = cached.forecast
                offlineSince = cached.fetchedAtMillis
                offlineForecast = true
            } else {
                errorText = "Sin conexión. Conéctate a internet para ver la previsión (esta escuela no tiene previsión guardada)."
            }
        }
        loading = false
        let favs = try? await getMyFavorites.invoke()
        isFavorite = (favs ?? []).contains { $0.id == schoolId }
        await loadNotes(schoolId: schoolId)
        await loadMonthly(schoolId: schoolId, lat: school.lat, lon: school.lon, rockType: school.rockType)
        await checkSaved(schoolId: schoolId)
        // Si está guardada offline y hemos cargado CON red, refresca el snapshot
        // (sectores + previsión + fotos) para que el offline esté lo más al día.
        if isSaved && !offlineForecast {
            await refreshOffline(school: school)
        }
    }

    /// Re-guarda en silencio el snapshot offline con los datos frescos ya cargados.
    func refreshOffline(school: School) async {
        guard let repo = savedSchools, let f = forecast else { return }
        let blocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
        try? await repo.saveOffline(school: school, blocks: blocks, forecast: f)
        await ImageCache.prefetch(blocks.compactMap { $0.photoPath })
    }

    func checkSaved(schoolId: String) async {
        guard let repo = savedSchools else { return }
        isSaved = (try? await repo.loadOffline(id: schoolId)) != nil
    }

    /// Guarda la escuela para OFFLINE: detalle + bloques + vías + forecast, y
    /// pre-descarga las fotos de las piedras a la caché de imágenes.
    func saveOffline(school: School) async {
        guard let repo = savedSchools else { return }
        savingOffline = true
        let blocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
        try? await repo.saveOffline(school: school, blocks: blocks, forecast: forecast)
        await ImageCache.prefetch(blocks.compactMap { $0.photoPath })
        // Tiles del mapa offline (mismo punto que Android): sin esto, offline el
        // mapa solo mostraba los marcadores, no el mapa de fondo, si no se había
        // visitado antes esa zona con red.
        OfflineTileManager.downloadFor(schoolId: school.id, lat: school.lat, lon: school.lon)
        isSaved = true; savingOffline = false
    }

    func removeOffline(schoolId: String) async {
        guard let repo = savedSchools else { return }
        try? await repo.remove(id: schoolId)
        OfflineTileManager.removeFor(schoolId: schoolId)
        isSaved = false
    }

    /// Stats mensuales (mejores meses del año). Cache-backed; null si no hay BD.
    func loadMonthly(schoolId: String, lat: Double, lon: Double, rockType: String?) async {
        guard let repo = AppDependencies.shared.container.monthlyStats else { return }
        if let m = try? await repo.get(schoolId: schoolId, lat: lat, lon: lon, rockType: rockType) {
            monthlyScores = m.scores.map { $0.intValue }
            monthlyBestRange = m.bestRange
        }
    }

    func loadNotes(schoolId: String) async {
        notes = (try? await getNotes.invoke(schoolId: schoolId)) ?? []
    }

    /** Voto de utilidad (1/-1; repetir retira) y recarga: vuelve ordenado por utilidad. */
    func voteNote(_ note: Note, value: Int) async {
        _ = try? await AppDependencies.shared.container.noteApi
            .voteNote(noteId: note.id, value: Int32(value))
        notes = (try? await getNotes.invoke(schoolId: note.schoolId)) ?? notes
    }

    /// Publica una nota (texto + foto opcional) y refresca la lista. Si hay
    /// imagen, la sube a Firebase Storage y adjunta su URL.
    func publishNote(schoolId: String, text: String, image: UIImage?) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        publishing = true
        var photoUrl: String?
        if let img = image {
            photoUrl = try? await StorageUploader.uploadNotePhoto(img, schoolId: schoolId)
        }
        _ = try? await createNote.invoke(schoolId: schoolId, text: trimmed, photoUrl: photoUrl)
        await loadNotes(schoolId: schoolId)
        publishing = false
    }

    /// Toggle optimista con revert si falla (espejo de Android).
    func toggleFavorite(schoolId: String) {
        let was = isFavorite
        isFavorite = !was
        Task {
            do {
                if was { try await removeFavorite.invoke(schoolId: schoolId) }
                else { try await addFavorite.invoke(schoolId: schoolId) }
            } catch { isFavorite = was }
        }
    }
}

struct SchoolDetailView: View {
    let school: School
    /// Si se indica, al abrir se despliega el mapa y se abre la piedra que
    /// contiene esa vía (deep-link desde el diario).
    var openVia: String? = nil
    @StateObject private var vm = SchoolDetailViewModel()
    @State private var factorsExpanded = false
    @State private var selectedDay: DayForecast?

    var body: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 60)
            } else if let err = vm.errorText {
                ContentUnavailableView("Sin previsión", systemImage: "cloud.slash", description: Text(err))
                    .padding(.top, 60)
            } else if let f = vm.forecast {
                FirstTimeHint(
                    hintKey: "detail_offline",
                    text: "Toca ↓ (arriba) para guardar esta escuela y verla sin conexión, incluyendo el mapa y las piedras."
                )
                FirstTimeHint(
                    hintKey: "detail_propose",
                    text: "Despliega el mapa de abajo y usa + PROPONER para añadir piedras, parkings o sectores que falten. Un admin lo revisa."
                )
                FirstTimeHint(
                    hintKey: "detail_tick",
                    text: "Toca una piedra en el mapa para ver sus vías. El círculo ○ marca una vía como hecha y la guarda en tu diario."
                )
                if vm.offlineForecast {
                    HStack(spacing: 6) {
                        Image(systemName: "wifi.slash").font(.system(size: 11))
                        Text(vm.offlineSince.map { "SIN CONEXIÓN · ACTUALIZADO \(relativeUpdated($0).uppercased())" }
                             ?? "SIN CONEXIÓN · PREVISIÓN GUARDADA").eyebrow()
                    }
                    .foregroundStyle(Cumbre.terra)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 8)
                    .background(Cumbre.terraBg)
                }
                ForecastBodyView(
                    forecast: f,
                    directions: (lat: school.lat, lon: school.lon, label: school.name),
                    factorsExpanded: $factorsExpanded,
                    onSelectDay: { selectedDay = $0 },
                    // Boletín de montaña ENCIMA del mapa (paridad con Android).
                    mapSlot: AnyView(VStack(spacing: 0) {
                        MountainBulletinSection(lat: school.lat, lon: school.lon)
                        SchoolMapSection(school: school, openVia: openVia)
                    })
                )
            } else {
                // Sin previsión: el mapa (y el boletín) van igualmente.
                MountainBulletinSection(lat: school.lat, lon: school.lon)
                SchoolMapSection(school: school, openVia: openVia)
            }
            // Notas comunitarias — ahora ENCIMA de "mejores meses".
            NotesSectionView(
                notes: vm.notes,
                publishing: vm.publishing,
                onPublish: { text, image in Task { await vm.publishNote(schoolId: school.id, text: text, image: image) } },
                onVote: { note, v in Task { await vm.voteNote(note, value: v) } }
            )
            // Mejores meses del año (stats mensuales del backend, cacheadas).
            if !vm.monthlyScores.isEmpty {
                MonthlyStatsSection(scores: vm.monthlyScores, bestRange: vm.monthlyBestRange)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(school.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { HelpButton(topicKey: "detail") }
            // Compartir (icono, mismo estilo que la estrella) — solo si hay
            // forecast cargado para resumir las condiciones.
            ToolbarItem(placement: .topBarTrailing) {
                if let f = vm.forecast {
                    ShareLink(item: conditionsShareSummary(f)) {
                        Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            // Cómo llegar (Google Maps, cae a Apple Maps) — ruta directa a la escuela.
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: {
                    Image(systemName: "arrow.triangle.turn.up.right.diamond").foregroundStyle(Cumbre.ink3)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { vm.toggleFavorite(schoolId: school.id) } label: {
                    Image(systemName: vm.isFavorite ? "star.fill" : "star")
                        .foregroundStyle(vm.isFavorite ? Cumbre.terra : Cumbre.ink3)
                }
            }
            // Guardar para offline (detalle + mapa + piedras + fotos).
            ToolbarItem(placement: .topBarTrailing) {
                if vm.savingOffline {
                    ProgressView()
                } else {
                    Button {
                        Task {
                            if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                            else { await vm.saveOffline(school: school) }
                        }
                    } label: {
                        Image(systemName: vm.isSaved ? "arrow.down.circle.fill" : "arrow.down.circle")
                            .foregroundStyle(vm.isSaved ? Cumbre.terra : Cumbre.ink3)
                    }
                }
            }
        }
        .sheet(item: $selectedDay) { d in
            DayDetailView(day: d, allHours: vm.forecast?.hours ?? [])
        }
        .task { await vm.load(school: school) }
    }
}

/// "Mejores meses del año" — barras de score medio por mes (3 años de histórico,
/// calculado en el backend). Espejo de la sección mensual de Android.
private struct MonthlyStatsSection: View {
    let scores: [Int]
    let bestRange: String?
    private let months = ["E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle("MEJORES MESES")
            if let r = bestRange, !r.isEmpty {
                Text("Mejor época: \(r)")
                    .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 16)
            }
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(scores.prefix(12).enumerated()), id: \.offset) { i, s in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Cumbre.score(s))
                            .frame(height: max(4, CGFloat(s) * 0.7))
                        Text(months[i % 12]).font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 90, alignment: .bottom)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
    }
}

/// Sección de mapa de la escuela (plegable). Muestra un MapLibre con tiles
/// topográficos, un marcador en la escuela y el botón "CÓMO LLEGAR". Los bloques
/// (parking/piedras/zonas) se añadirán como marcadores en una iteración siguiente.
private struct SchoolMapSection: View {
    let school: School
    var openVia: String? = nil
    @State private var didAutoOpen = false
    @State private var isAdmin = false
    @State private var expanded = false
    @State private var blocks: [Block] = []
    @State private var selectedBlock: Block?
    // Proponer mejora: tap en el mapa fija coords → formulario → envío.
    @State private var waitingTap = false
    @State private var showTypePicker = false
    @State private var formCoord: CLLocationCoordinate2D?
    @State private var proposeType = "PARKING"
    @State private var showSuccess = false
    @State private var mapStyle: MapStyleKind = .satellite  // paridad con Android
    @State private var boulderCoord: CLLocationCoordinate2D?
    // Corregir posición: seleccionar un marcador y fijar su nueva posición.
    @State private var correctionMode = false
    @State private var corrActive = false        // ya hay un target elegido
    @State private var corrTargetId: String?     // nil + corrActive ⇒ la escuela
    @State private var corrTargetName = ""
    @State private var corrOld: CLLocationCoordinate2D?
    @State private var corrNew: CLLocationCoordinate2D?
    @State private var userCoord: CLLocationCoordinate2D?   // mi ubicación en el sector
    @State private var collapsedSectors: Set<String> = []   // zonas con piedras ocultas
    // Mini-ficha flotante de PARKING/ZONA sobre el mapa (la ficha grande queda
    // solo para PIEDRAS). confirmDeleteMini = confirmación de borrado (admin).
    @State private var miniBlock: Block?
    @State private var confirmDeleteMini = false
    @State private var editLinesBlock: Block?
    @State private var assignSectorBlock: Block?
    @State private var mapZoom: Double = 14   // para mostrar el nombre del sector al acercar
    // Cámara conservada al recrear el MapView (entrar/salir de fullscreen).
    @State private var savedCenter: CLLocationCoordinate2D?
    @State private var savedZoom: Double?
    // Foco explícito al pulsar un parking en la lista (recentra el mapa ahí).
    @State private var focusCoord: CLLocationCoordinate2D?
    @State private var focusToken = 0
    // Encuadre de bounds (parking + su zona) — tiene prioridad sobre focusCoord.
    @State private var focusFit: [CLLocationCoordinate2D] = []
    // Capas ocultas por el usuario (leyenda pulsable): PARKING/BLOCK/ZONE.
    @State private var hiddenTypes: Set<String> = []
    // Mapa a pantalla completa (estilo Radar).
    @State private var fullscreenMap = false
    // Buscador de vías/bloques de la escuela.
    @State private var searchQuery = ""
    @State private var searchHighlight: String?
    // Brújula → cono de dirección en el punto azul.
    @StateObject private var headingProvider = HeadingProvider()

    // Colores de marcador por tipo (espejo de Android: parking azul, piedra
    // terra, zona verde; la escuela en tinta oscura).
    private let parkingColor = UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    private let blockColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    private let zoneColor = UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    private let schoolColor = UIColor(red: 0.11, green: 0.11, blue: 0.10, alpha: 1)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "map").font(.system(size: 13))
                    Text(expanded ? NSLocalizedString("schools_hide_map", comment: "") : NSLocalizedString("schools_view_map", comment: ""))
                        .font(Cumbre.mono(11, .bold)).tracking(0.8)
                    if !blocks.isEmpty {
                        Text("· \(blocks.count)").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12))
                }
                .foregroundStyle(Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 11)
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .padding(.horizontal, 16).padding(.vertical, 4)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                searchBar
                if !fullscreenMap {
                    mapArea(height: 280)
                }
                // "CÓMO LLEGAR" de la escuela quitado: las indicaciones salen al
                // tocar cada parking/piedra en el mapa (BlockInfoSheet).
                parkingsList
            }
        }
        .task(id: expanded) {
            guard expanded else { return }
            if blocks.isEmpty {
                blocks = await loadBlocksOnlineOrOffline()
            }
            maybeAutoOpen()
            headingProvider.start()
            // Mi ubicación en CONTINUO mientras el mapa está abierto: el GPS se
            // mantiene caliente y afina en segundos (los fixes sueltos cada 5 s
            // salían "perdidísimos" en montaña).
            if AppDependencies.shared.locationBridge.hasPermission() {
                AppDependencies.shared.locationBridge.startStream { loc in
                    DispatchQueue.main.async {
                        userCoord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
                    }
                }
            }
            // Mantener el task vivo hasta plegar el mapa; al salir, se apaga.
            while expanded, !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
            AppDependencies.shared.locationBridge.stopStream()
        }
        // Deep-link desde el diario: despliega el mapa al entrar.
        .onAppear {
            if let v = openVia, !v.isEmpty, !didAutoOpen, !expanded { expanded = true }
        }
        // ¿Admin? → puede eliminar bloques desde su ficha.
        .task {
            isAdmin = ((try? await AppDependencies.shared.container.getMyProfile.invoke())?.isAdmin) ?? false
        }
        .alert("¿Eliminar «\(miniBlock?.name ?? "")»?", isPresented: $confirmDeleteMini) {
            Button("ELIMINAR", role: .destructive) {
                guard let mb = miniBlock else { return }
                miniBlock = nil
                Task {
                    try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: mb.id)
                    await reloadBlocks()
                }
            }
            Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
        } message: {
            Text("Esta acción no se puede deshacer.")
        }
    }

    /// Mapa con todos sus overlays. Compartido entre el modo tarjeta (280 pt)
    /// y pantalla completa (estilo Radar).
    @ViewBuilder
    private func mapArea(height: CGFloat) -> some View {
        ZStack(alignment: .bottomTrailing) {
                    MapLibreView(
                        center: savedCenter ?? CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                        zoom: savedZoom ?? 14,
                        markers: markers,
                        style: mapStyle,
                        // Encuadre inicial con TODOS los elementos (parkings/sectores/
                        // piedras), por muy separados que estén — antes el mapa abría
                        // fijo en zoom 14 sobre la escuela, dejando fuera sectores a
                        // km. Mi ubicación solo entra en el encuadre si ya está cerca
                        // (ver userMarkerIfNearby en MapLibreView.swift).
                        autoFitToMarkers: savedZoom == nil,
                        onZoomChange: { mapZoom = $0 },
                        onCameraChange: { c, z in savedCenter = c; savedZoom = z },
                        onTapMarker: { id in
                            if correctionMode && !corrActive {
                                selectCorrectionTarget(id)
                            } else if !waitingTap && !correctionMode {
                                // Parking/zona → mini-ficha flotante (el mapa se
                                // sigue viendo); piedras → su ficha completa.
                                if let b = blocks.first(where: { $0.id == id }) {
                                    switch b.type.uppercased() {
                                    case "PARKING":
                                        miniBlock = b
                                        flyToParkingZone(b)
                                    case "ZONE":
                                        // Encuadra el sector con sus piedras
                                        // (expandiéndolas), como el parking.
                                        miniBlock = b
                                        // Zoom FIJO centrado entre sector y piedras
                                        // (los encuadres calculados se iban a extremos).
                                        let stones = blocks.filter { $0.sectorBlockId == b.id }
                                        collapsedSectors.remove(b.id)
                                        let pts = [CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)]
                                            + stones.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
                                        let cLat = pts.map { $0.latitude }.reduce(0, +) / Double(pts.count)
                                        let cLon = pts.map { $0.longitude }.reduce(0, +) / Double(pts.count)
                                        focusFit = []
                                        focusCoord = CLLocationCoordinate2D(latitude: cLat, longitude: cLon)
                                        focusToken += 1
                                    default:
                                        // Piedra: centra suave y abre su ficha.
                                        focusFit = []
                                        focusCoord = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
                                        focusToken += 1
                                        openBlockSheet(b)
                                    }
                                }
                            }
                        },
                        onMapTap: (waitingTap || (correctionMode && corrActive)) ? { coord in
                            if waitingTap {
                                waitingTap = false
                                if proposeType == "BOULDER" { boulderCoord = coord } else { formCoord = coord }
                            } else {
                                corrNew = coord
                            }
                        } : nil,
                        polylines: wallPolylines,
                        // Foco explícito al pulsar un parking en la lista (recentra
                        // el mapa ahí). Debe ir DESPUÉS de polylines: el init
                        // memberwise de Swift exige el mismo orden que la
                        // declaración del struct aunque los args vayan con nombre.
                        focusCoordinate: focusCoord,
                        focusToken: focusToken,
                        focusFitCoordinates: focusFit
                    )
                    .frame(height: height)


                    if waitingTap {
                        // Banner "PULSA EN EL MAPA" (parking/sector/piedra).
                        mapBanner("PULSA EN EL MAPA PARA FIJAR LA POSICIÓN",
                                  cancel: { waitingTap = false })
                    } else if correctionMode {
                        mapBanner(correctionBannerText,
                                  accept: (corrActive && corrNew != nil) ? { Task { await submitCorrection() } } : nil,
                                  cancel: cancelCorrection)
                    } else {
                        // Botón "+ PROPONER": pill flotante ARRIBA a la derecha —
                        // abajo lo tapaba la mini-ficha de parking/sector.
                        VStack {
                            if fullscreenMap { Spacer().frame(height: 54) }
                            HStack {
                                Spacer()
                                Button {
                                    // Ya no salimos de pantalla completa: el selector
                                    // se presenta desde mapArea (dentro del cover).
                                    showTypePicker = true
                                } label: {
                                    Text(NSLocalizedString("detail_propose", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.6)
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 9)
                                        .background(Cumbre.terra)
                                        .clipShape(RoundedRectangle(cornerRadius: 11))
                                }
                                .buttonStyle(.plain)
                            }
                            Spacer()
                        }
                        .padding(10)
                        .frame(height: height)
                    }

                    // Ampliar / salir de pantalla completa — ARRIBA a la izquierda
                    // (debajo de los chips de estilo) + botonera lateral en fullscreen.
                    if !waitingTap && !correctionMode {
                        VStack {
                            HStack {
                                Button { fullscreenMap.toggle() } label: {
                                    Image(systemName: fullscreenMap
                                          ? "arrow.down.right.and.arrow.up.left"
                                          : "arrow.up.left.and.arrow.down.right")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(Cumbre.ink)
                                        .frame(width: 34, height: 34)
                                        .background(Cumbre.bg)
                                        .clipShape(Circle())
                                        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                Spacer()
                            }
                            .padding(.top, fullscreenMap ? 54 : 0)
                            Spacer()
                        }
                        .padding(10)
                        .frame(height: height)
                    }
                    if !waitingTap && !correctionMode {
                        // Botonera lateral (maqueta B): topo↔satélite de un toque
                        // + capas con la FORMA real del marcador.
                        VStack {
                            HStack {
                                Spacer()
                                VStack(spacing: 8) {
                                sideButton(active: true) {
                                    recenterOnSchool()
                                } content: {
                                    Image(systemName: "scope")
                                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                                }
                                sideButton(active: true) {
                                    mapStyle = (mapStyle == .satellite) ? .topo : .satellite
                                } content: {
                                    Image(systemName: "square.3.layers.3d")
                                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                                }
                                sideButton(active: !hiddenTypes.contains("PARKING")) {
                                    toggleLayer("PARKING")
                                } content: { parkingShape }
                                sideButton(active: !hiddenTypes.contains("BLOCK")) {
                                    toggleLayer("BLOCK")
                                } content: { stoneShape }
                                sideButton(active: !hiddenTypes.contains("ZONE")) {
                                    toggleLayer("ZONE")
                                } content: { zoneShape }
                                if let u = userCoord {
                                    sideButton(active: true) {
                                        focusFit = []
                                        focusCoord = u
                                        focusToken += 1
                                    } content: {
                                        Image(systemName: "location.fill")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color(parkingColor))
                                    }
                                }
                                }
                            }
                            Spacer()
                        }
                        .padding(.top, fullscreenMap ? 104 : 50)
                        .padding(.trailing, 10)
                        .frame(height: height)
                    }

                    // Mini-ficha flotante de parking/sector: informa y da CÓMO
                    // LLEGAR sin tapar el mapa.
                    if let mb = miniBlock, !waitingTap, !correctionMode {
                        VStack { Spacer(); miniBlockCard(mb) }
                            .frame(height: height)
                            .frame(maxWidth: .infinity)
                    }
                }
                // Sheets del flujo de PROPONER anclados al mapa: como mapArea se
                // usa igual en tarjeta y en pantalla completa (solo hay una
                // instancia activa a la vez), el selector/formularios se
                // presentan en el contexto correcto SIN salir de pantalla completa.
                .sheet(isPresented: $showTypePicker) {
                    ContributionTypePicker { type in
                        showTypePicker = false
                        switch type {
                        case "PARKING", "SECTOR", "BOULDER": proposeType = type; waitingTap = true
                        case "CORRECTION": correctionMode = true; corrActive = false; corrNew = nil
                        default: break
                        }
                    }
                }
                .sheet(item: coordItem) { item in
                    ContributionFormSheet(type: proposeType, schoolId: school.id, coord: item.coord) { ok in
                        formCoord = nil
                        if ok { afterSubmit() }
                    }
                }
                .sheet(item: boulderCoordItem) { item in
                    BoulderFormSheet(schoolId: school.id, coord: item.coord,
                                     sectors: blocks.filter { $0.type.uppercased() == "ZONE" }) { ok in
                        boulderCoord = nil
                        if ok { afterSubmit() }
                    }
                }
                .sheet(isPresented: $showSuccess) { ContributionSuccessSheet(isAdmin: isAdmin) }
                // Ficha de piedra y sus acciones — también ancladas al mapa para
                // que se presenten sobre la pantalla completa sin tener que salir.
                .sheet(item: $selectedBlock) { b in
                    BlockInfoSheet(
                        block: b,
                        sectors: blocks.filter { $0.type.uppercased() == "ZONE" },
                        schoolName: school.name,
                        highlightVia: searchHighlight ?? openVia,
                        onEditLines: { editLinesBlock = b },
                        onAssignSector: { assignSectorBlock = b },
                        onDelete: isAdmin ? {
                            let id = b.id
                            selectedBlock = nil
                            Task {
                                try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: id)
                                await reloadBlocks()
                            }
                        } : nil,
                        onRateLine: { lineId, stars in
                            Task {
                                let rateLine = AppDependencies.shared.container.rateLine
                                if stars > 0 {
                                    _ = try? await rateLine.rate(blockId: b.id, lineId: lineId, stars: Int32(stars))
                                } else {
                                    _ = try? await rateLine.unrate(blockId: b.id, lineId: lineId)
                                }
                                await reloadBlocks()
                            }
                        })
                }
                .sheet(item: $editLinesBlock) { b in
                    EditLinesSheet(block: b, schoolId: school.id, focusVia: openVia) { ok in
                        editLinesBlock = nil
                        if ok { afterSubmit() }
                    }
                }
                .sheet(item: $assignSectorBlock) { b in
                    AssignSectorSheet(block: b, schoolId: school.id,
                                      sectors: blocks.filter { $0.type.uppercased() == "ZONE" }) { ok in
                        assignSectorBlock = nil
                        if ok { afterSubmit() }
                    }
                }
    }

    /// Buscador de vías/bloques (como el buscador de escuelas de la lista).
    @ViewBuilder
    private var searchBar: some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField("Buscar vías/bloques…", text: $searchQuery)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            let q = searchQuery.trimmingCharacters(in: .whitespaces)
            if q.count >= 2 {
                let hits = searchHits(q)
                if hits.isEmpty {
                    Text("Sin resultados en esta escuela")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                } else {
                    VStack(spacing: 0) {
                        ForEach(hits, id: \.id) { h in
                            Button {
                                searchQuery = ""
                                searchHighlight = h.viaName
                                selectedBlock = h.block
                            } label: {
                                HStack {
                                    Text(h.label).font(.system(size: 14))
                                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    Spacer()
                                    Text(h.sub).font(.system(size: 12))
                                        .foregroundStyle(Cumbre.ink3).lineLimit(1)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 9)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
        .fullScreenCover(isPresented: $fullscreenMap) {
            ZStack(alignment: .bottomTrailing) {
                Color.black.ignoresSafeArea()
                mapArea(height: UIScreen.main.bounds.height)
                    .ignoresSafeArea()
            }
        }
    }

    private struct SearchHit { let id: String; let label: String; let sub: String; let viaName: String?; let block: Block }
    private func searchHits(_ q: String) -> [SearchHit] {
        var out: [SearchHit] = []
        for b in blocks where b.type.uppercased() == "BLOCK" {
            for l in b.lines where l.name.localizedCaseInsensitiveContains(q) {
                let grade = (l.grade?.isEmpty == false) ? " · \(l.grade!)" : ""
                out.append(SearchHit(id: l.id, label: l.name + grade, sub: b.name, viaName: l.name, block: b))
            }
            if b.name.localizedCaseInsensitiveContains(q) {
                out.append(SearchHit(id: b.id, label: b.name, sub: "\(b.lines.count) vías", viaName: nil, block: b))
            }
        }
        return Array(out.prefix(8))
    }

    /// Vuelve al encuadre inicial de la escuela (todos los marcadores).
    private func recenterOnSchool() {
        var coords = [CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)]
        coords += blocks.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        if coords.count >= 2 { focusFit = coords } else {
            focusFit = []
            focusCoord = coords[0]
        }
        focusToken += 1
    }

    private func toggleLayer(_ type: String) {
        if hiddenTypes.contains(type) { hiddenTypes.remove(type) } else { hiddenTypes.insert(type) }
    }

    /// Botón circular de la botonera lateral; apagado = capa oculta.
    private func sideButton<C: View>(active: Bool, action: @escaping () -> Void,
                                     @ViewBuilder content: () -> C) -> some View {
        Button(action: action) {
            content()
                .frame(width: 34, height: 34)
                .background(Cumbre.bg)
                .clipShape(Circle())
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                .opacity(active ? 1 : 0.4)
        }
        .buttonStyle(.plain)
    }

    /// P cuadrada azul — la forma real del marcador de parking.
    private var parkingShape: some View {
        Text("P").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(parkingColor))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    /// Polígono irregular terra — la forma real del marcador de piedra.
    private var stoneShape: some View {
        StonePolygon().fill(Color(blockColor))
            .overlay(StonePolygon().stroke(.white, lineWidth: 1.2))
            .frame(width: 24, height: 22)
    }

    /// Círculo verde con Z — la forma real del marcador de zona.
    private var zoneShape: some View {
        Text("Z").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(zoneColor))
            .clipShape(Circle())
    }

    /// Abre la ficha de una piedra. Ya NO sale de pantalla completa: el sheet
    /// cuelga de mapArea, así que se presenta sobre el cover.
    private func openBlockSheet(_ b: Block) {
        selectedBlock = b
    }

    private func afterSubmit() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            showSuccess = true
            await reloadBlocks()
        }
    }

    // Wrapper Identifiable para presentar el formulario de piedra.
    private var boulderCoordItem: Binding<CoordItem?> {
        Binding(get: { boulderCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { boulderCoord = nil } })
    }

    // MARK: - Corrección de posición

    private func selectCorrectionTarget(_ id: String) {
        if let b = blocks.first(where: { $0.id == id }) {
            corrTargetId = b.id
            corrTargetName = b.name.isEmpty ? typeLabel(b.type) : b.name
            corrOld = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
        } else if id == school.id {
            corrTargetId = nil
            corrTargetName = "la escuela"
            corrOld = CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)
        } else { return }
        corrActive = true
        corrNew = nil
    }

    private func cancelCorrection() {
        correctionMode = false; corrActive = false; corrTargetId = nil; corrNew = nil
    }

    private func submitCorrection() async {
        guard let old = corrOld, let nw = corrNew else { return }
        let req = ContributionRequest(
            type: "POSITION_CORRECTION", name: corrTargetName,
            lat: old.latitude, lon: old.longitude,
            notes: nil, description: nil,
            proposedLat: KotlinDouble(double: nw.latitude),
            proposedLon: KotlinDouble(double: nw.longitude), correctionReason: nil,
            targetBlockId: corrTargetId, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: school.id, req: req)) != nil
        cancelCorrection()
        if ok { afterSubmit() }
    }

    private var correctionBannerText: String {
        if !corrActive { return "PULSA EL MARCADOR QUE QUIERES MOVER" }
        if corrNew == nil { return "MOVIENDO «\(corrTargetName)» · PULSA LA NUEVA POSICIÓN" }
        return "POSICIÓN FIJADA · PULSA OTRA VEZ PARA RECORREGIR O ACEPTA"
    }

    /// Banner superior + botón cancelar (y aceptar opcional) sobre el mapa.
    private func mapBanner(_ text: String, accept: (() -> Void)? = nil,
                           cancel: @escaping () -> Void) -> some View {
        VStack {
            Text(text)
                .font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .frame(maxWidth: .infinity).background(Cumbre.terra)
            Spacer()
            HStack(spacing: 8) {
                Button("CANCELAR", action: cancel)
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(Cumbre.ink)
                if let accept {
                    Button("✓ ACEPTAR", action: accept)
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                        .padding(.horizontal, 12).padding(.vertical, 6).background(.white)
                }
            }.padding(.bottom, 8)
        }
        .frame(height: 280)
    }

    // Wrapper Identifiable para presentar el formulario con la coordenada fijada.
    private var coordItem: Binding<CoordItem?> {
        Binding(get: { formCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { formCoord = nil } })
    }

    /// Abre la piedra que contiene la vía indicada (deep-link del diario): busca
    /// el bloque cuya vía coincide por nombre (o el bloque con ese nombre).
    private func maybeAutoOpen() {
        guard !didAutoOpen, let via = openVia, !via.isEmpty, !blocks.isEmpty else { return }
        // Por id ESTABLE primero (enlaces compartidos /s/v/{...}/{lineId});
        // si no, por nombre (deep-link clásico del diario).
        let target = blocks.first { b in
            b.lines.contains { $0.id == via }
        } ?? blocks.first { b in
            b.lines.contains { $0.name.caseInsensitiveCompare(via) == .orderedSame }
        } ?? blocks.first { $0.name.caseInsensitiveCompare(via) == .orderedSame }
            // Post "piedra nueva" del feed: openVia trae el ID de la piedra.
            ?? blocks.first { $0.id == via }
        if let target {
            didAutoOpen = true
            selectedBlock = target
        }
    }

    private func reloadBlocks() async {
        blocks = await loadBlocksOnlineOrOffline()
        // Si hay una ficha de bloque abierta, refresca sus datos (p.ej. nueva foto)
        if let sel = selectedBlock, let fresh = blocks.first(where: { $0.id == sel.id }) {
            selectedBlock = fresh
        }
    }

    /// Carga los bloques (piedras/parkings/zonas) por red; si la red falla o no
    /// devuelve nada (offline), cae al snapshot guardado `loadOffline` para que
    /// el mapa, las piedras y sus vías salgan igual sin internet. Las fotos las
    /// resuelve `TopoPhotoView` desde `ImageCache`.
    private func loadBlocksOnlineOrOffline() async -> [Block] {
        if let online = try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id),
           !online.isEmpty {
            // Si el sitio está guardado offline, refresca su snapshot con lo recién
            // bajado (bloques + fotos) para que SIN conexión no se vea lo viejo tras
            // una modificación. Forecast nil = no se toca el ya cacheado.
            if let repo = AppDependencies.shared.container.savedSchools,
               (try? await repo.loadOffline(id: school.id)) != nil {
                try? await repo.saveOffline(school: school, blocks: online, forecast: nil)
                await ImageCache.prefetch(online.compactMap { $0.photoPath })
            }
            return online
        }
        // Sin red (o sin bloques en la respuesta): tira del snapshot offline.
        if let repo = AppDependencies.shared.container.savedSchools,
           let snap = try? await repo.loadOffline(id: school.id) {
            return snap.blocks.map { repo.toBlock(entity: $0, lines: snap.lines) }
        }
        return []
    }

    // Muros (geometry=LINE) dibujados como polilínea terra en el mapa. Se ocultan
    // si la piedra pertenece a un sector colapsado (igual que su marcador).
    private var wallPolylines: [CumbrePolyline] {
        blocks.compactMap { b in
            // Solo PIEDRAS: un parking/zona con path corrupto no debe estirarse
            // en una línea (se veía azul, color del tipo).
            guard b.type.uppercased() == "BLOCK", b.geometry.uppercased() == "LINE" else { return nil }
            if hiddenTypes.contains("BLOCK") { return nil }
            if let sid = b.sectorBlockId, collapsedSectors.contains(sid) { return nil }
            let pts = parseWallPath(b.path)
            guard pts.count >= 2 else { return nil }
            return CumbrePolyline(id: "wall-\(b.id)", coordinates: pts, color: blockColor, width: 5)
        }
    }

    /// Coordenada del marcador de una piedra: si es MURO, el centro de la
    /// polilínea (no la esquina donde se fijó al crearla → salía en un lateral).
    private func blockMarkerCoord(_ b: Block) -> CLLocationCoordinate2D {
        if b.geometry.uppercased() == "LINE" {
            let pts = parseWallPath(b.path)
            if pts.count >= 2 { return pts[pts.count / 2] }
        }
        return CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
    }

    private var markers: [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in blocks {
            // Capa apagada (botonera lateral) → no se pinta.
            if hiddenTypes.contains(b.type.uppercased()) { continue }
            // Piedra oculta si pertenece a un sector colapsado.
            if b.type.uppercased() == "BLOCK", let sid = b.sectorBlockId, collapsedSectors.contains(sid) {
                continue
            }
            // Zona colapsada: muestra cuántas piedras tiene ocultas.
            let collapsed = b.type.uppercased() == "ZONE" && collapsedSectors.contains(b.id)
            let hidden = collapsed ? blocks.filter { $0.sectorBlockId == b.id }.count : 0
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: blockMarkerCoord(b),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type),
                color: color(for: b.type),
                name: collapsed && hidden > 0 ? "\(b.name) (+\(hidden))" : b.name,
                // Nombre del sector visible al acercar (sin tener que pulsarlo).
                showName: b.type.uppercased() == "ZONE" && !b.name.isEmpty && mapZoom >= 13.5))
        }
        // Orden de pintado: piedras primero → sectores/parkings/escuela ENCIMA
        // (no quedan tapados por los pines de piedra).
        ms.sort { a, b in
            func rank(_ k: MarkerKind) -> Int {
                switch k { case .block: return 0; case .parking: return 1
                case .zone: return 2; case .school: return 3; default: return 4 }
            }
            return rank(a.kind) < rank(b.kind)
        }
        // Mi ubicación (punto azul) para orientarme en el sector.
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user,
                                   score: headingProvider.heading))
        }
        // Fantasma de la nueva posición al corregir.
        if let nw = corrNew {
            ms.append(CumbreMarker(
                id: "__GHOST__", coordinate: nw, title: "Nueva posición",
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        return ms
    }

    private func markerKind(for type: String) -> MarkerKind {
        switch type.uppercased() {
        case "PARKING": return .parking
        case "ZONE": return .zone
        default: return .block
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem("Parking", Color(parkingColor))
            legendItem("Piedra", Color(blockColor))
            legendItem("Zona", Color(zoneColor))
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }
    private func legendItem(_ t: String, _ c: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(c).frame(width: 9, height: 9).overlay(Circle().stroke(.white, lineWidth: 1))
            Text(t).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
        }
    }

    // Lo importante para llegar: desde qué parking se va andando. Orden
    // alfabético (NO por cercanía — debe tener sentido también viéndolo desde
    // casa, lejos de la escuela); la distancia es solo dato informativo. Tocar
    // uno centra el mapa en esa zona (para ver qué sectores/piedras hay
    // alrededor) y abre su ficha, que ya tiene "CÓMO LLEGAR".
    /// Vuela a la "zona" de un parking: encuadra parking + sectores/piedras a
    /// ≤800 m (expandiendo colapsados) — vista de ~1,5-2 km. Sin nada cerca,
    /// zoom equivalente. Espejo de flyToParkingZone de Android.
    private func flyToParkingZone(_ p: Block) {
        let near = blocks.filter { b in
            b.id != p.id &&
            Geo.shared.haversineKm(lat1: p.lat, lon1: p.lon, lat2: b.lat, lon2: b.lon) <= 0.8
        }
        for b in near {
            if b.type.uppercased() == "ZONE" { collapsedSectors.remove(b.id) }
            if let sid = b.sectorBlockId { collapsedSectors.remove(sid) }
        }
        // Centrar EN el parking pulsado (no en el centroide del parking + cercanos,
        // que dejaba el parking descentrado). Paridad con Android.
        focusFit = []
        focusCoord = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        focusToken += 1
    }

    /// Mini-ficha flotante de parking/sector: una línea de alto, el mapa se
    /// sigue viendo. Admin: ✎ (abre la ficha completa) y 🗑 a la izquierda de
    /// CÓMO LLEGAR. Sectores con piedras: toggle VER/OCULTAR explícito.
    private func miniBlockCard(_ mb: Block) -> some View {
        let isParking = mb.type.uppercased() == "PARKING"
        let stoneCount = blocks.filter { $0.sectorBlockId == mb.id }.count
        let collapsed = collapsedSectors.contains(mb.id)
        var subtitle = isParking ? "Parking" : "Sector"
        if !isParking && stoneCount > 0 { subtitle += " · \(stoneCount) piedra\(stoneCount == 1 ? "" : "s")" }
        if let u = userCoord {
            let km = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: mb.lat, lon2: mb.lon)
            subtitle += km < 1 ? " · \(Int(km * 1000)) m" : String(format: " · %.1f km", km)
        }
        return VStack(spacing: 6) {
            HStack(spacing: 8) {
                Text(isParking ? "P" : "Z")
                    .font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 24, height: 24)
                    .background(isParking ? Color(parkingColor) : Color(zoneColor))
                    .clipShape(RoundedRectangle(cornerRadius: isParking ? 6 : 12))
                VStack(alignment: .leading, spacing: 0) {
                    Text(mb.name.isEmpty ? (isParking ? "Parking" : "Sector") : mb.name)
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    Text(subtitle).font(.system(size: 11)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
                Spacer(minLength: 4)
                if isAdmin {
                    Button { miniBlock = nil; openBlockSheet(mb) } label: {
                        Image(systemName: "pencil").font(.system(size: 15)).foregroundStyle(Cumbre.ink2).padding(4)
                    }.buttonStyle(.plain)
                    Button { confirmDeleteMini = true } label: {
                        Image(systemName: "trash").font(.system(size: 14)).foregroundStyle(Cumbre.bad).padding(4)
                    }.buttonStyle(.plain)
                }
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(mb.lat),\(mb.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(mb.lat),\(mb.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: {
                    Text("CÓMO LLEGAR").font(Cumbre.mono(10, .bold)).tracking(0.8)
                        .foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Cumbre.terra).clipShape(RoundedRectangle(cornerRadius: 9))
                }.buttonStyle(.plain)
                Button { miniBlock = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 13)).foregroundStyle(Cumbre.ink3).padding(4)
                }.buttonStyle(.plain)
            }
            if !isParking && stoneCount > 0 {
                Button {
                    if collapsed { collapsedSectors.remove(mb.id) } else { collapsedSectors.insert(mb.id) }
                } label: {
                    Text(collapsed ? "VER PIEDRAS" : "OCULTAR PIEDRAS")
                        .font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 7)
                        .overlay(RoundedRectangle(cornerRadius: 9).stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .background(Cumbre.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 8).padding(.bottom, 8)
    }

    private var parkingsList: some View {
        let parkings = blocks.filter { $0.type.uppercased() == "PARKING" }.sorted { $0.name < $1.name }
        return Group {
            if !parkings.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("PARKINGS").eyebrow()
                        .padding(.horizontal, 16).padding(.top, 10)
                    // Chips horizontales (una línea): cada uno lleva el mapa a SU
                    // zona (mini-ficha + encuadre parking + cercanos).
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(parkings, id: \.id) { p in
                                Button {
                                    miniBlock = p
                                    flyToParkingZone(p)
                                } label: {
                                    HStack(spacing: 6) {
                                        Text("P")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundStyle(.white)
                                            .frame(width: 20, height: 20)
                                            .background(Color(parkingColor))
                                            .clipShape(RoundedRectangle(cornerRadius: 5))
                                        Text(chipLabel(p))
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    }
                                    .padding(.horizontal, 10).padding(.vertical, 7)
                                    .background(Cumbre.paper)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 6)
                    }
                }
            }
        }
    }

    /// Etiqueta del chip de parking: nombre + distancia si hay ubicación.
    private func chipLabel(_ p: Block) -> String {
        var label = p.name.isEmpty ? "Parking" : p.name
        if let u = userCoord {
            let km = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: p.lat, lon2: p.lon)
            label += km < 1 ? " · \(Int(km * 1000)) m" : String(format: " · %.1f km", km)
        }
        return label
    }

    private func color(for type: String) -> UIColor {
        switch type.uppercased() {
        case "PARKING": return parkingColor
        case "ZONE": return zoneColor
        default: return blockColor
        }
    }
    private func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }
}

/// Coordenada Identifiable para presentar el formulario por sheet(item:).
private struct CoordItem: Identifiable {
    let coord: CLLocationCoordinate2D
    var id: String { "\(coord.latitude),\(coord.longitude)" }
}

/// Selector de tipo de propuesta — espejo de TypePickerDialog.kt. PARKING está
/// activo; el resto (piedra/sector/corregir) llegará en próximas iteraciones.
private struct ContributionTypePicker: View {
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("¿Falta algo en esta escuela? Propón una mejora y un admin la revisará (24-48 h).")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // Mini-guía del flujo
                Text("Cómo funciona: elige qué añadir → toca el mapa para fijar la posición → rellena los datos → enviar. ¡Así de fácil!")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Cumbre.terra.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))

                row("PIEDRA", "Una roca con sus vías. Podrás añadir fotos y dibujar las líneas.", "mountain.2.fill", enabled: true) { onPick("BOULDER") }
                row("SECTOR", "Una zona que agrupa piedras (ej: \"La Isla\"). Después asignarás piedras al sector.", "square.dashed", enabled: true) { onPick("SECTOR") }
                row("PARKING", "El punto de aparcamiento. Otros escaladores verán \"Cómo llegar\".", "car.fill", enabled: true) { onPick("PARKING") }
                row("CORREGIR", "¿Algo está mal colocado en el mapa? Tócalo y muévelo al sitio correcto.", "mappin.and.ellipse", enabled: true) { onPick("CORRECTION") }
                Spacer()
            }
            .padding(16)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Proponer mejora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
    }
    private func row(_ t: String, _ sub: String, _ icon: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(enabled ? Cumbre.terra : Cumbre.ink3).frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(t).font(Cumbre.mono(13, .bold)).tracking(0.6).foregroundStyle(enabled ? Cumbre.ink : Cumbre.ink3)
                    Text(enabled ? sub : "\(sub) · próximamente").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Cumbre.paper)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// Formulario de propuesta PARKING/SECTOR — espejo de ParkingFormDialog.kt.
/// Coords fijadas por el tap en el mapa. Envía POST /contributions y devuelve ok.
private struct ContributionFormSheet: View {
    let type: String           // "PARKING" | "SECTOR"
    let schoolId: String
    let coord: CLLocationCoordinate2D
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var notes = ""
    @State private var sending = false
    @State private var sendError: String? = nil
    @State private var queued = false

    private var isSector: Bool { type == "SECTOR" }
    private var title: String { isSector ? "Nueva zona" : "Nuevo parking" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    field("NOMBRE (opcional)", $name, isSector ? "ej: Sector Bajo" : "ej: Parking de arriba")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }
                    field("NOTAS (opcional)", $notes, isSector ? "Descripción de la zona" : "Cómo es el acceso, etc.")
                    if let sendError {
                        Text(sendError).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                        // Cola offline: guarda la propuesta y el flusher la envía
                        // solo al recuperar cobertura (espejo de Android).
                        Button { Task { await saveOffline() } } label: {
                            Text("GUARDAR Y ENVIAR CON COBERTURA").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.ink)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
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
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
            .alert("Guardada en tu móvil", isPresented: $queued) {
                Button("CERRAR") { dismiss(); onDone(false) }
            } message: {
                Text("Se enviará automáticamente en cuanto haya cobertura. No tienes que hacer nada.")
            }
        }
    }

    private func send() async {
        sending = true
        let req = ContributionRequest(
            type: type,
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? nil : name,
            lat: coord.latitude, lon: coord.longitude,
            notes: notes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : notes,
            description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok { dismiss(); onDone(true) }
        else { sendError = "No se pudo enviar. Revisa la conexión — tus datos siguen aquí." }
    }

    /// Encola la propuesta para que ContributionOutboxFlusher la envíe al
    /// recuperar cobertura. El JSON usa las MISMAS claves que ContributionRequest
    /// (lo decodifica kotlinx en el contenedor).
    private func saveOffline() async {
        var dict: [String: Any] = ["type": type, "lat": coord.latitude, "lon": coord.longitude]
        let nm = name.trimmingCharacters(in: .whitespaces)
        if !nm.isEmpty { dict["name"] = nm }
        let nt = notes.trimmingCharacters(in: .whitespaces)
        if !nt.isEmpty { dict["notes"] = nt }
        guard let d = try? JSONSerialization.data(withJSONObject: dict),
              let json = String(data: d, encoding: .utf8) else { return }
        try? await AppDependencies.shared.container.enqueueContribution(schoolId: schoolId, requestJson: json)
        queued = true
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Confirmación tras enviar una propuesta — espejo de SuccessDialog.kt.
private struct ContributionSuccessSheet: View {
    // Admin publica directo (auto-aprobado) → el mensaje lo refleja en vez de
    // decir "la revisaremos en 24-48 h".
    var isAdmin: Bool = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.seal.fill").font(.system(size: 56)).foregroundStyle(Cumbre.ok)
            Text(isAdmin ? "¡Publicado!" : "¡Propuesta enviada!").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text(isAdmin
                 ? "Ya está en el mapa para toda la comunidad."
                 : "La revisaremos en 24-48 h. Gracias por mejorar el mapa de la comunidad.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).padding(.horizontal, 32)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1)).padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
        .background(Cumbre.bg.ignoresSafeArea())
    }
}

/// Hoja con la info de un bloque tocado en el mapa (nombre, tipo, descripción,
/// vías y "CÓMO LLEGAR"). Espejo simplificado de BlockDetailDialog.kt.
struct BlockInfoSheet: View {
    let block: Block
    var sectors: [Block] = []
    var schoolName: String? = nil
    /// Vía objetivo (deep-link del diario): su cara/foto se muestra la primera.
    var highlightVia: String? = nil
    var onEditLines: (() -> Void)? = nil
    var onAssignSector: (() -> Void)? = nil
    /// Admin: borrar este bloque (piedra/zona/parking) directamente.
    var onDelete: (() -> Void)? = nil
    /// Valorar una vía. nil = no mostrar estrellas.
    var onRateLine: ((String, Int) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    /// Caras de la piedra, SIEMPRE en el orden en que se introdujeron (FOTO 1,
    /// FOTO 2…). El deep-link del diario NO reordena: solo hace scroll a la cara
    /// que contiene la vía pulsada (ver `scrollFaceIndex`).
    private var orderedFaces: [BlockFace] { block.facesOrDerived() }

    /// Índice de la cara que contiene la vía del deep-link (para hacer scroll a
    /// ella al abrir). Nil si no hay deep-link o no se encuentra.
    private var scrollFaceIndex: Int? {
        guard let via = highlightVia?.trimmingCharacters(in: .whitespaces), !via.isEmpty else { return nil }
        return orderedFaces.firstIndex { f in
            f.lines.contains { $0.name.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(via) == .orderedSame }
        }
    }
    @State private var tickedLines: Set<String> = []   // vías marcadas como hechas en esta sesión
    @State private var tickingLine: String?            // vía guardándose ahora
    @State private var projectLines: Set<String> = []  // vías marcadas como PROYECTO
    @State private var togglingProject: String?         // vía de proyecto guardándose ahora
    @State private var showDeleteConfirm = false
    // Tick pendiente de confirmar: hoja "Publicar en el feed" (desmarcar sigue
    // siendo toggle directo, sin hoja). Espejo del flujo de SchoolMap.kt.
    @State private var pendingTick: PendingFeedTick? = nil
    // Comentarios de la piedra/vías (un fetch por piedra; los hilos filtran).
    @StateObject private var commentsStore = LineCommentsStore()
    // Desplegable OPCIONES: agrupa editar vías / sector / eliminar.
    @State private var optionsOpen = false

    private var sectorName: String? {
        guard let sid = block.sectorBlockId else { return nil }
        return sectors.first(where: { $0.id == sid })?.name
    }
    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    Text(block.name.isEmpty ? typeLabel : block.name)
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)

                    // Sector al que pertenece (si lo tiene).
                    if let sn = sectorName, !sn.isEmpty {
                        Text("SECTOR · \(sn.uppercased())").font(Cumbre.mono(10, .bold))
                            .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Cumbre.ok)
                    }

                    // CARAS: una piedra grande se enseña con varias fotos. Cada cara
                    // es una foto con sus vías dibujadas y, debajo, sus vías
                    // marcables. Una piedra de una sola foto tiene una única cara.
                    if block.type.uppercased() == "BLOCK" {
                        if !block.lines.isEmpty {
                            FirstTimeHint(
                                hintKey: "via_tick",
                                text: "Toca el círculo de una vía para apuntarla como hecha en tu diario."
                            )
                            FirstTimeHint(
                                hintKey: "via_project",
                                text: "Toca la P de una vía para marcarla como PROYECTO (la estás probando, aún no te ha salido)."
                            )
                        }
                        ForEach(Array(orderedFaces.enumerated()), id: \.offset) { faceIdx, face in
                          VStack(alignment: .leading, spacing: 12) {
                            if let photo = face.photoPath, !photo.isEmpty {
                                if orderedFaces.count > 1 {
                                    Text("FOTO \(faceIdx + 1)").eyebrow().padding(.top, 4)
                                }
                                TopoPhotoView(photoUrl: photo, lines: face.lines.map { TopoLineVM($0) })
                                    .padding(.top, 4)
                            }
                            if !face.lines.isEmpty {
                                Text("VÍAS (\(face.lines.count))").eyebrow().padding(.top, 4)
                                ForEach(Array(face.lines.enumerated()), id: \.element.id) { idx, l in
                                    VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 10) {
                                        Text("\(idx + 1)").font(Cumbre.mono(11, .bold))
                                            .foregroundStyle(GradeColor.style(l.grade).dark ? .black : .white)
                                            .frame(width: 24, height: 24)
                                            .background(Circle().fill(GradeColor.color(l.grade)))
                                        if let g = l.grade, !g.isEmpty {
                                            Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                                                .frame(width: 38, alignment: .leading)
                                        }
                                        Text(l.name.isEmpty ? "Vía \(idx + 1)" : l.displayName)
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        Spacer()
                                        if let st = l.startType, !st.isEmpty {
                                            Text(st).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                        }
                                        // Compartir esta vía como IMAGEN (foto + líneas,
                                        // formato historia) → Instagram/WhatsApp; si no tiene
                                        // foto/dibujo cae al texto (espejo de Android).
                                        Button {
                                            Task { await ShareLineImage.share(block: block, line: l, schoolName: schoolName, tickedIds: tickedLines, projectIds: projectLines, sectorName: sectorName) }
                                        } label: {
                                            Image(systemName: "square.and.arrow.up")
                                                .font(.system(size: 16, weight: .medium))
                                                .foregroundStyle(Cumbre.ink2)
                                                .frame(width: 28, height: 28)
                                        }
                                        .buttonStyle(.plain)
                                        // Proyecto: la estás probando, aún no te ha salido. Oculto
                                        // si ya está hecha (no tiene sentido marcarla como proyecto).
                                        if !tickedLines.contains(l.id) {
                                            Button { Task { await toggleProject(l, index: idx) } } label: {
                                                if togglingProject == l.id {
                                                    ProgressView().scaleEffect(0.7).frame(width: 24, height: 24)
                                                } else {
                                                    let isProject = projectLines.contains(l.id)
                                                    Text("P")
                                                        .font(.system(size: 13, weight: .bold))
                                                        .foregroundStyle(isProject ? .white : Cumbre.ink3.opacity(0.4))
                                                        .frame(width: 24, height: 24)
                                                        .background(
                                                            Circle().fill(isProject ? Cumbre.terra : Color.clear)
                                                        )
                                                        .overlay(
                                                            Circle().stroke(isProject ? Color.clear : Cumbre.ink3.opacity(0.4), lineWidth: 1)
                                                        )
                                                }
                                            }
                                            .buttonStyle(.plain)
                                            .disabled(tickingLine != nil || togglingProject != nil)
                                        }
                                        // Tic: marca/desmarca la vía en tu diario (toggle).
                                        // Al MARCAR, según la preferencia "Publicar
                                        // ascensos en el feed": ASK → hoja de publicar;
                                        // ALWAYS → publica directo; NEVER → solo diario.
                                        Button { onTickTapped(l, index: idx) } label: {
                                            if tickingLine == l.id {
                                                ProgressView().scaleEffect(0.7).frame(width: 28, height: 28)
                                            } else {
                                                Image(systemName: tickedLines.contains(l.id) ? "checkmark.circle.fill" : "checkmark.circle")
                                                    .font(.system(size: 20))
                                                    .foregroundStyle(tickedLines.contains(l.id) ? Cumbre.ok : Cumbre.ink3)
                                                    .frame(width: 28, height: 28)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(tickingLine != nil || togglingProject != nil)
                                    }
                                    // Estrellas de valoración
                                    if onRateLine != nil {
                                        LineStarsRow(
                                            lineId: l.id,
                                            avgStars: l.avgStars.map { Float($0) },
                                            myStars: Int(l.myStars?.int32Value ?? 0)
                                        ) { stars in onRateLine?(l.id, stars) }
                                    }
                                    // Descripción/beta de la vía (si la tiene).
                                    if let d = l.lineDescription, !d.isEmpty {
                                        Text(d).font(.system(size: 12))
                                            .foregroundStyle(Cumbre.ink3)
                                    }
                                    // Comentarios de ESTA vía (desplegable).
                                    LineCommentsThreadView(store: commentsStore,
                                                           blockId: block.id, lineId: l.id)
                                    } // VStack
                                }
                            }
                          }
                          .id(faceIdx)
                        }
                    }

                    // (Comentarios solo en cada vía, no en la piedra entera.)

                    // Coordenadas (espejo de BlockDetailDialog).
                    Text(String(format: "%.5f, %.5f", block.lat, block.lon))
                        .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3).padding(.top, 2)

                    DirectionsButton(lat: block.lat, lon: block.lon, label: block.name).padding(.top, 8)

                    // Desplegable OPCIONES (editar vías / sector / eliminar).
                    if onEditLines != nil || onAssignSector != nil || onDelete != nil {
                        Button { withAnimation { optionsOpen.toggle() } } label: {
                            HStack {
                                Text("OPCIONES").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                    .foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: optionsOpen ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(.vertical, 12).padding(.horizontal, 12)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                    }

                    // Editor unificado de vías (corregir existentes + añadir nuevas).
                    if optionsOpen, block.type.uppercased() == "BLOCK", let onEditLines {
                        Button { dismiss(); onEditLines() } label: {
                            Text(block.lines.isEmpty ? NSLocalizedString("block_add_routes", comment: "") : NSLocalizedString("block_edit_routes", comment: ""))
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Asignar / cambiar sector (piedra, si la escuela tiene algún
                    // sector). Si ya tiene sector → "CAMBIAR SECTOR" (el picker
                    // muestra los demás; si no hay otro, lo avisa).
                    if optionsOpen, block.type.uppercased() == "BLOCK", let onAssignSector, !sectors.isEmpty {
                        Button { dismiss(); onAssignSector() } label: {
                            Text(block.sectorBlockId == nil ? NSLocalizedString("propose_assign_sector", comment: "") : NSLocalizedString("propose_change_sector", comment: ""))
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Admin: eliminar este bloque (piedra/zona/parking).
                    if optionsOpen, let onDelete {
                        Button(role: .destructive) { showDeleteConfirm = true } label: {
                            Text("ELIMINAR").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.bad).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                        }.buttonStyle(.plain)
                        .alert("¿Eliminar \(typeLabel.lowercased())?", isPresented: $showDeleteConfirm) {
                            Button("Cancelar", role: .cancel) {}
                            Button("Eliminar", role: .destructive) { dismiss(); onDelete() }
                        } message: {
                            Text("Se borrará del mapa para todos. No se puede deshacer.")
                        }
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .task { await commentsStore.load(blockId: block.id) }
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) }
            }
            .task { await loadDone() }
            // Hoja de publicar el tick en el feed Comunidad (estilo Cumbre).
            // Cerrar la hoja = no marcar nada.
            .sheet(item: $pendingTick) { pt in
                FeedPublishSheet(
                    lineLabel: feedTickLabel(pt.line, index: pt.index),
                    wasProject: pt.wasProject,
                    onPublish: { always, caption, photo in
                        if always { FeedPublishPrefs.mode = .always }
                        pendingTick = nil
                        Task {
                            await toggle(pt.line, index: pt.index)
                            publishTickToFeed(pt.line, wasProject: pt.wasProject,
                                              caption: caption, photo: photo)
                        }
                    },
                    onDiaryOnly: {
                        pendingTick = nil
                        Task { await toggle(pt.line, index: pt.index) }
                    })
            }
            // Deep-link del diario: hace scroll a la cara que contiene la vía
            // pulsada (sin reordenar las caras → FOTO 1, FOTO 2… en su orden).
            .onAppear {
                guard let i = scrollFaceIndex, i > 0 else { return }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    withAnimation { proxy.scrollTo(i, anchor: .top) }
                }
            }
            }
        }
    }

    /// Al abrir, marca como HECHAS (✓) las vías que ya están en tu diario, para
    /// que el tic quede persistente entre sesiones. Match por escuela + nombre de
    /// la vía (mismo nombre que se guardó al dar el tic).
    private func loadDone() async {
        let container = AppDependencies.shared.container
        // Claves pendientes en la cola offline, separadas por estado (la cola
        // JOURNAL guarda tanto "hechas" como "proyecto" bajo el mismo tipo).
        let pendingDoneKeys: Set<String> = (try? await container.pendingJournalKeysByStatus(status: "DONE")) ?? []
        let pendingProjectKeys: Set<String> = (try? await container.pendingJournalKeysByStatus(status: "PROJECT")) ?? []
        let pendingDeletes: Set<String> = (try? await container.pendingJournalDeleteKeys()) ?? []
        // Con red: sincroniza el registro local con la verdad del servidor
        // (descontando las que tienen borrado pendiente). Separamos por status:
        // solo DONE cuenta como "hecha"; solo PROJECT cuenta como "proyecto".
        if let journal = try? await container.getMyJournal.invoke() {
            var serverDoneKeys = Set<String>()
            var serverProjectKeys = Set<String>()
            for j in journal {
                guard let sid = j.schoolId else { continue }
                // Clave por lineId (aguanta homónimas — fix "La ola"); por
                // nombre solo para entradas antiguas sin lineId. Mismo formato
                // que journalViaKey de Android y los helpers del container.
                let key: String
                if let lid = j.lineId, !lid.isEmpty {
                    key = "\(sid)|#\(lid)"
                } else {
                    key = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                }
                if j.status == "PROJECT" { serverProjectKeys.insert(key) } else { serverDoneKeys.insert(key) }
            }
            JournalDoneStore.shared.sync(server: serverDoneKeys.subtracting(pendingDeletes), pending: pendingDoneKeys)
            JournalProjectStore.shared.sync(server: serverProjectKeys.subtracting(pendingDeletes), pending: pendingProjectKeys)
        }
        // El registro local (UserDefaults) funciona también SIN conexión → evita
        // duplicar al volver a entrar offline en la misma piedra.
        let storeKeys = JournalDoneStore.shared.all
        let projectKeys = JournalProjectStore.shared.all
        var done = Set<String>()
        var projects = Set<String>()
        for (idx, l) in block.lines.enumerated() {
            let viaName = l.name.isEmpty ? "Vía \(idx + 1)" : l.name
            // Clave por id + clave por nombre (LEGADO: entradas sin lineId).
            let idKey = "\(block.schoolId)|#\(l.id)"
            let nameKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"
            let isDone = (storeKeys.contains(idKey) || storeKeys.contains(nameKey)
                          || pendingDoneKeys.contains(idKey) || pendingDoneKeys.contains(nameKey))
                && !pendingDeletes.contains(idKey) && !pendingDeletes.contains(nameKey)
            if isDone { done.insert(l.id) }
            let isProject = (projectKeys.contains(idKey) || projectKeys.contains(nameKey)
                             || pendingProjectKeys.contains(idKey) || pendingProjectKeys.contains(nameKey))
                && !pendingDeletes.contains(idKey) && !pendingDeletes.contains(nameKey)
            if isProject && !done.contains(l.id) { projects.insert(l.id) }
        }
        tickedLines = done
        projectLines = projects
    }

    private var typeLabel: String {
        switch block.type.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }


    /// Toque en el tic: desmarcar va directo; marcar pasa por la preferencia
    /// "Publicar ascensos en el feed" (ASK/ALWAYS/NEVER) — espejo de Android.
    private func onTickTapped(_ line: BlockLine, index: Int) {
        if tickedLines.contains(line.id) {
            Task { await toggle(line, index: index) }
            return
        }
        // wasProject ANTES del toggle (marcar la quita de proyectos).
        let wasProject = projectLines.contains(line.id)
        switch FeedPublishPrefs.mode {
        case .ask:
            pendingTick = PendingFeedTick(line: line, index: index, wasProject: wasProject)
        case .always:
            Task {
                await toggle(line, index: index)
                publishTickToFeed(line, wasProject: wasProject)
            }
        case .never:
            Task { await toggle(line, index: index) }
        }
    }

    /// Etiqueta "vía · grado" de la hoja de publicar.
    private func feedTickLabel(_ line: BlockLine, index: Int) -> String {
        var label = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        if let g = line.grade, !g.isEmpty { label += " · \(g)" }
        return label
    }

    /// Publica el tick en el feed Comunidad (fire-and-forget: si falla no
    /// bloquea ni deshace el diario). kind = PROJECT_DONE si la vía estaba en
    /// proyectos; TICK en el resto. Ids del backend = String (UUID) tal cual.
    private func publishTickToFeed(_ line: BlockLine, wasProject: Bool,
                                   caption: String? = nil, photo: UIImage? = nil) {
        let kind = wasProject ? "PROJECT_DONE" : "TICK"
        let discipline = block.discipline.uppercased() == "ROUTE" ? "ROUTE" : "BOULDER"
        let lineId: String? = line.id.isEmpty ? nil : line.id
        Task {
            let container = AppDependencies.shared.container
            guard let postId = try? await container.publishFeedPost.invoke(
                blockId: block.id, lineId: lineId, kind: kind, discipline: discipline,
                caption: caption)
            else { return }
            // Foto de celebración (opcional): comprimir (máx 1024 px, JPEG 0.8,
            // mismo pipeline que StorageUploader) y subirla como multipart. Si
            // falla, el post queda publicado sin foto (aviso discreto).
            guard let photo else { return }
            guard let data = feedPhotoJPEGData(photo) else {
                await showFeedPhotoUploadFailedAlert()
                return
            }
            do {
                _ = try await container.uploadFeedPhoto.invoke(
                    postId: postId.int64Value, bytes: data.toKotlinByteArray(),
                    contentType: "image/jpeg")
            } catch {
                await showFeedPhotoUploadFailedAlert()
            }
        }
    }

    /// Marca/DESMARCA la vía en tu diario (toggle). Si no estaba hecha la añade
    /// (POST, o cola sin red); si ya estaba, la quita (borra la subida y/o la
    /// pendiente). No se puede añadir dos veces. Espejo del toggle de Android.
    private func toggle(_ line: BlockLine, index: Int) async {
        tickingLine = line.id
        let container = AppDependencies.shared.container
        let viaName = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        // Clave por lineId (fix homónimas "La ola") + legado por nombre.
        let key = "\(block.schoolId)|#\(line.id)"
        let legacyKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"

        if tickedLines.contains(line.id) {
            // DESMARCAR (quita también la clave legado, por si el ✓ venía de
            // una entrada antigua sin lineId).
            tickedLines.remove(line.id)
            JournalDoneStore.shared.remove(key)
            JournalDoneStore.shared.remove(legacyKey)
            // 1) Si solo estaba ENCOLADA (sin subir) → cancela la creación y listo.
            let hadPending = ((try? await container.dequeueJournal(key: key))?.boolValue) ?? false
            if !hadPending {
                // 2) Está (o estará) en el servidor: borra ya si hay red; si no,
                //    ENCOLA el borrado. La entrada se localiza POR lineId; solo
                //    si no hay ninguna con ese id, por nombre entre las SIN
                //    lineId — nunca borra la entrada de una homónima distinta.
                var deleted = false
                let journal = (try? await container.getMyJournal.invoke()) ?? []
                let j = journal.first(where: { $0.lineId == line.id })
                    ?? journal.first(where: {
                        $0.lineId == nil && $0.schoolId == block.schoolId &&
                        $0.blockName.caseInsensitiveCompare(viaName) == .orderedSame
                    })
                if let j {
                    deleted = ((try? await container.deleteJournalEntry.invoke(id: j.id)) != nil)
                }
                if !deleted {
                    // Payload del borrado = la clave de LA ENTRADA encontrada
                    // (id o legado) para que el filtrado offline case.
                    let delKey: String
                    if let j, j.lineId == nil, let sid = j.schoolId {
                        delKey = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                    } else { delKey = key }
                    try? await container.enqueueJournalDelete(key: delKey)
                }
            }
        } else {
            // Si era un PROYECTO, primero lo quitamos (local + servidor/cola): al
            // conseguirla, desaparece de Proyectos y pasa a Vías/Bloques.
            if projectLines.contains(line.id) {
                await removeProjectEntry(key: key, legacyKey: legacyKey, line: line, viaName: viaName)
                projectLines.remove(line.id)
            }
            // MARCAR HECHA (dedup: no estaba hecha)
            tickedLines.insert(line.id)
            JournalDoneStore.shared.add(key)
            try? await container.dequeueJournalDelete(key: key)   // cancela borrado pendiente
            let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
            // No guardamos "Piedra: N" (el número se recicla/borra → quedaría obsoleto).
            let req = CreateJournalRequest(
                schoolId: block.schoolId, schoolName: schoolName, sector: sectorName,
                blockName: viaName, grade: line.grade,
                notes: nil, date: df.string(from: Date()),
                discipline: block.discipline,   // la vía hereda la modalidad de su piedra
                lineId: line.id,                // id estable → enganche del diario por muro
                status: "DONE")
            let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
            if !ok { try? await container.enqueueJournal(req: req) }   // sin red → cola
        }
        tickingLine = nil
    }

    /// Marca/DESMARCA la vía como PROYECTO (la estás probando, aún no te ha
    /// salido). Espejo de [toggle], pero con status="PROJECT". No hace nada si
    /// la vía ya está HECHA (la UI ya oculta el botón en ese caso).
    private func toggleProject(_ line: BlockLine, index: Int) async {
        guard !tickedLines.contains(line.id) else { return }
        togglingProject = line.id
        let container = AppDependencies.shared.container
        let viaName = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        // Clave por lineId + legado por nombre (ver toggle).
        let key = "\(block.schoolId)|#\(line.id)"
        let legacyKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"

        if projectLines.contains(line.id) {
            // DESMARCAR proyecto
            projectLines.remove(line.id)
            await removeProjectEntry(key: key, legacyKey: legacyKey, line: line, viaName: viaName)
        } else {
            // MARCAR proyecto
            projectLines.insert(line.id)
            JournalProjectStore.shared.add(key)
            try? await container.dequeueJournalDelete(key: key)
            let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
            let req = CreateJournalRequest(
                schoolId: block.schoolId, schoolName: schoolName, sector: sectorName,
                blockName: viaName, grade: line.grade,
                notes: nil, date: df.string(from: Date()),
                discipline: block.discipline, lineId: line.id, status: "PROJECT")
            let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
            if !ok { try? await container.enqueueJournal(req: req) }
        }
        togglingProject = nil
    }

    /// Cancela/borra la entrada PROYECTO de [key] (cola pendiente o ya subida al
    /// servidor). Compartido por toggleProject (desmarcar) y toggle (promoción
    /// proyecto→hecha).
    private func removeProjectEntry(key: String, legacyKey: String,
                                    line: BlockLine, viaName: String) async {
        let container = AppDependencies.shared.container
        JournalProjectStore.shared.remove(key)
        JournalProjectStore.shared.remove(legacyKey)
        let hadPending = ((try? await container.dequeueJournal(key: key))?.boolValue) ?? false
        if !hadPending {
            var deleted = false
            let journal = (try? await container.getMyJournal.invoke()) ?? []
            // Por lineId; fallback por nombre SOLO entre entradas sin lineId.
            let j = journal.first(where: { $0.status == "PROJECT" && $0.lineId == line.id })
                ?? journal.first(where: {
                    $0.status == "PROJECT" && $0.lineId == nil &&
                    $0.schoolId == block.schoolId &&
                    $0.blockName.caseInsensitiveCompare(viaName) == .orderedSame
                })
            if let j {
                deleted = ((try? await container.deleteJournalEntry.invoke(id: j.id)) != nil)
            }
            if !deleted {
                let delKey: String
                if let j, j.lineId == nil, let sid = j.schoolId {
                    delKey = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                } else { delKey = key }
                try? await container.enqueueJournalDelete(key: delKey)
            }
        }
    }
}

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el
/// schoolId (p. ej. al tocar una notificación con targetType "school").
struct SchoolDetailLoaderView: View {
    let schoolId: String
    @State private var school: School?
    @State private var failed = false

    var body: some View {
        Group {
            if let s = school {
                SchoolDetailView(school: s)
            } else if failed {
                ContentUnavailableView("Escuela no encontrada", systemImage: "mappin.slash")
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: schoolId) {
                school = s
            } else { failed = true }
        }
    }
}

/// Cuerpo del forecast (réplica de ForecastBody.kt). Reutilizado por el detalle
/// de escuela y por el tab Tiempo (en tu ubicación). `directions` es opcional:
/// el tab Tiempo no muestra "CÓMO LLEGAR" (no hay escuela destino).
struct ForecastBodyView: View {
    let forecast: Forecast
    var directions: (lat: Double, lon: Double, label: String)? = nil
    @Binding var factorsExpanded: Bool
    var onSelectDay: ((DayForecast) -> Void)? = nil
    /// Mapa de la escuela, insertado entre el tiempo actual y "Próximas 16 h"
    /// (el tab Tiempo no lo pasa). AnyView para no acoplar el tipo.
    var mapSlot: AnyView? = nil

    var body: some View {
        let f = forecast
        VStack(alignment: .leading, spacing: 0) {
            HeroSection(forecast: f)
            // El compartir vive ahora como icono junto a la estrella del toolbar
            // (el detalle ya tiene muchos datos). "CÓMO LLEGAR" volverá con mapas.
            RockStatusBand(current: f.current).padding(.horizontal, 16).padding(.bottom, 8)
            HeatmapStrip(hours: upcomingHours(f.hours, 24)).padding(16)
            FactorsAccordion(current: f.current, expanded: $factorsExpanded)
            rule
            CurrentWeather(current: f.current)
            rule
            if let mapSlot {
                mapSlot
                rule
            }
            SectionTitle("PRÓXIMAS 16 HORAS")
            HoursGrid(hours: upcomingHours(f.hours, 16)).padding(.vertical, 8)
            ConditionsGrid(current: f.current)
            rule
            SectionTitle("PRÓXIMOS 7 DÍAS")
            ForEach(Array(f.days.prefix(7).enumerated()), id: \.offset) { _, d in
                Button { onSelectDay?(d) } label: { DayRow(day: d) }.buttonStyle(.plain)
                rule
            }
            // "MEJOR DÍA" quitado a petición.
        }
    }

    private var rule: some View { Divider().overlay(Cumbre.rule) }
}

// MARK: - Hero (¿PUEDO ESCALAR HOY?)

private struct HeroSection: View {
    let forecast: Forecast
    var body: some View {
        let c = forecast.current
        let score = Int(c.score)
        let yes = score >= 55
        HStack(alignment: .top, spacing: 12) {
            Text(yes ? "SÍ" : "NO")
                .font(Cumbre.serif(56, .bold))
                .foregroundStyle(yes ? Cumbre.ok : Cumbre.bad)
            VStack(alignment: .leading, spacing: 4) {
                Text("¿PUEDO ESCALAR HOY?").eyebrow()
                if let w = forecast.bestWindow {
                    Text("Óptimo entre \(short(w.start))–\(short(w.end))")
                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("ÍNDICE").eyebrow()
                HStack(alignment: .bottom, spacing: 2) {
                    Text("\(score)")
                        .font(Cumbre.serif(40, .bold))
                        .foregroundStyle(Cumbre.score(score))
                    Text("/100").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        .padding(.bottom, 6)
                }
            }
        }
        .padding(16)
    }
}

// MARK: - Banda de roca

private struct RockStatusBand: View {
    let current: Current
    var body: some View {
        let dry = current.dryRock
        let accent = dry ? Cumbre.ok : Cumbre.bad
        let subtitle = current.drying?.message ?? (dry ? "Lista para escalar" : "Mejor esperar a que seque")
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(dry ? "● ROCA SECA" : "● ROCA HÚMEDA")
                    .font(Cumbre.mono(13, .bold)).foregroundStyle(accent)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
            Spacer()
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(accent.opacity(0.12))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.45), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Heatmap (tira ancha)

private struct HeatmapStrip: View {
    let hours: [HourForecast]
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                Rectangle().fill(Cumbre.score(Int(h.score)))
            }
        }
        .frame(height: 18)
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

// MARK: - Desglose de factores

private struct FactorsAccordion: View {
    let current: Current
    @Binding var expanded: Bool
    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("¿POR QUÉ ESTE ÍNDICE?").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if expanded {
                VStack(spacing: 0) {
                    ForEach(Array(current.factors.enumerated()), id: \.offset) { _, f in
                        HStack(spacing: 10) {
                            Image(systemName: f.passes ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(f.passes ? Cumbre.ok : Cumbre.bad)
                            Text(f.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            Spacer()
                            Text(f.display).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
                .padding(.bottom, 8)
            }
        }
    }
}

// MARK: - Tiempo actual

private struct CurrentWeather: View {
    let current: Current
    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            Text("\(Int(current.temperature))°")
                .font(Cumbre.serif(56, .bold)).foregroundStyle(Cumbre.ink)
            VStack(alignment: .leading, spacing: 2) {
                Text(cloudLabel(Int(current.cloudCover)))
                    .font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("VIENTO \(Int(current.windSpeed)) km/h  ·  HUM \(Int(current.humidity))%")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(16)
    }
}

// MARK: - Próximas 16 h

private struct HoursGrid: View {
    let hours: [HourForecast]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                    VStack(spacing: 6) {
                        Text(short(h.time)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        WmoIcon(code: Int(h.weatherCode), size: 22, tint: Cumbre.ink2)
                        RoundedRectangle(cornerRadius: 2).fill(Cumbre.score(Int(h.score)))
                            .frame(width: 30, height: 30)
                            .overlay(Text("\(Int(h.score))").font(Cumbre.mono(11, .bold)).foregroundStyle(.white))
                        Text("\(Int(h.temperature))°").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink2)
                        // Lluvia en mm (solo si la hay) — paridad con ForecastBody.
                        if h.precipitation > 0 {
                            Text("\(String(format: "%.1f", h.precipitation)) mm")
                                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.rain)
                        } else {
                            Text(" ").font(Cumbre.mono(9))
                        }
                        // Viento km/h (siempre) — debajo de la lluvia, como Android.
                        Text("\(Int(h.windSpeed)) km/h")
                            .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Condiciones ahora (8 celdas)

private struct ConditionsGrid: View {
    let current: Current
    var body: some View {
        let c = current
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle("CONDICIONES AHORA").padding(.horizontal, 0)
            HStack(spacing: 8) {
                cell("HUMEDAD", "\(Int(c.humidity))", "%")
                cell("VIENTO", "\(Int(c.windSpeed))", "km/h")
                cell("LLUVIA 24H", trim(c.precip24h), "mm")
                cell("NUBES", "\(Int(c.cloudCover))", "%")
            }
            HStack(spacing: 8) {
                cell("LLUVIA 72H", trim(c.precip72h), "mm")
                cell("ROCÍO", c.dewPoint.map { "\(Int(truncating: $0))" } ?? "—", "°")
                cell("PROB LLUVIA", "\(Int(c.precipitationProbability))", "%")
                cell("ROCA", c.dryRock ? "SECA" : "HÚM", "")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func cell(_ label: String, _ value: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Cumbre.mono(9, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
            HStack(alignment: .bottom, spacing: 2) {
                Text(value).font(.system(size: 20, weight: .semibold)).foregroundStyle(Cumbre.ink)
                if !unit.isEmpty { Text(unit).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3).padding(.bottom, 3) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }
}

// MARK: - Día y mejor día

private struct DayRow: View {
    let day: DayForecast
    var body: some View {
        HStack(spacing: 12) {
            Text("\(Int(day.avgScore))")
                .font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.score(Int(day.avgScore)))
                .frame(width: 40, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(dayLabel(day.date)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("MÁX \(Int(day.tempMax))°  ·  MÍN \(Int(day.tempMin))°  ·  \(trim(day.precipitationTotal)) mm")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

private struct BestDayBar: View {
    let forecast: Forecast
    var body: some View {
        if let b = forecast.bestDay {
            VStack(alignment: .leading, spacing: 2) {
                Text("★ MEJOR DÍA").eyebrow()
                Text("En \(Int(b.daysFromToday))d (\(Int(b.score)))")
                    .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
            }
            .padding(16)
        }
    }
}

/// Botón "CÓMO LLEGAR" → abre Google Maps con la ruta al destino (mismo
/// patrón que Android: dir/?api=1&destination=lat,lon). Cae a Apple Maps si
/// Google Maps no está instalado.
struct DirectionsButton: View {
    let lat: Double
    let lon: Double
    let label: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            let g = URL(string: "comgooglemaps://?daddr=\(lat),\(lon)&directionsmode=driving")!
            let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)")!
            openURL(UIApplication.shared.canOpenURL(g) ? g : web)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.turn.up.right.diamond")
                Text("CÓMO LLEGAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.terra)
        }
        .buttonStyle(.plain)
    }
}

/// Botón COMPARTIR — comparte un resumen de condiciones por el share sheet del
/// sistema (paridad con Android, que comparte una tarjeta). De momento texto;
/// la imagen-tarjeta es una mejora posterior.
/// Resumen de condiciones para compartir (texto). Reutilizado por el icono del
/// toolbar del detalle.
func conditionsShareSummary(_ forecast: Forecast) -> String {
    // Formato WhatsApp (los *asteriscos* son negrita allí) + nuestro enlace
    // inteligente: abre la app si la tienes, o la página con las stores si no.
    // Espejo exacto de shareSchool() en Android.
    let c = forecast.current
    var text = "🧗 *\(forecast.schoolName)*\n"
    text += "📊 Índice *\(Int(c.score))/100* (\(c.scoreLabel))\n"
    if let w = forecast.bestWindow {
        text += "🕐 Óptimo *\(w.start)–\(w.end)*\n"
    }
    text += c.dryRock ? "🪨 Roca seca" : "💧 Roca mojada"
    text += " · \(Int(c.temperature))° · viento \(Int(c.windSpeed)) km/h\n"
    let base = AppConfig.apiBaseUrl.replacingOccurrences(of: "api/", with: "")
    text += "\n👉 Ábrela en Cumbre:\n\(base)s/e/\(forecast.schoolId)"
    return text
}

struct ShareConditionsButton: View {
    let forecast: Forecast

    private var summary: String { conditionsShareSummary(forecast) }

    var body: some View {
        ShareLink(item: summary) {
            HStack(spacing: 8) {
                Image(systemName: "square.and.arrow.up")
                Text("COMPARTIR").font(Cumbre.mono(12, .bold)).tracking(0.8)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.terra)
        }
        .buttonStyle(.plain)
    }
}

private struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Helpers

private func short(_ iso: String) -> String {
    if let t = iso.firstIndex(of: "T") { return String(iso[iso.index(after: t)...].prefix(5)) }
    return iso
}

/// Devuelve las próximas `count` horas A PARTIR DE LA HORA ACTUAL (no desde el
/// inicio del día). Espejo de "PRÓXIMAS 16 HORAS desde ahora" de Android.
private func upcomingHours(_ hours: [HourForecast], _ count: Int) -> [HourForecast] {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm"
    let now = Date()
    let startIdx = hours.firstIndex { h in
        guard let d = f.date(from: String(h.time.prefix(16))) else { return false }
        return d >= now.addingTimeInterval(-3600) // incluye la hora en curso
    } ?? 0
    return Array(hours[startIdx...].prefix(count))
}

private func trim(_ d: Double) -> String {
    d == d.rounded() ? "\(Int(d))" : String(format: "%.1f", d)
}

private func cloudLabel(_ c: Int) -> String {
    switch c {
    case ..<20: return "Despejado"
    case ..<50: return "Parcialmente nublado"
    case ..<80: return "Mayormente nublado"
    default:    return "Cubierto"
    }
}

private func dayLabel(_ iso: String) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
    guard let d = f.date(from: String(iso.prefix(10))) else { return iso }
    let out = DateFormatter(); out.locale = Locale(identifier: "es_ES"); out.dateFormat = "EEE d MMM"
    return out.string(from: d).capitalized
}

// `Note` (clase Kotlin vía SKIE) ya tiene `id: String`; con esto vale para
// `.sheet(item:)` y `ForEach(id:)` sin envoltorios.
extension Note: Identifiable {}

// MARK: - Notas comunitarias (réplica de NotesSection.kt)

/// Sección de notas del detalle de escuela. Leer es público; publicar requiere
/// sesión (garantizada por el gate de login). La foto se muestra con AsyncImage
/// nativo; subir foto necesita el bridge de Firebase Storage (pendiente).
struct NotesSectionView: View {
    let notes: [Note]
    let publishing: Bool
    let onPublish: (String, UIImage?) -> Void
    var onVote: (Note, Int) -> Void = { _, _ in }

    @State private var draft = ""
    @State private var photoNote: Note?
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedImage: UIImage?
    // Plegada por defecto: con muchas notas la pantalla se hacía eterna.
    @State private var expanded = false
    // Moderación: denunciar notas ajenas + ocultar al instante.
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var reportNote: Note? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 8) {
                    Text("NOTAS COMUNITARIAS").eyebrow()
                    if !notes.isEmpty {
                        Text("\(notes.count)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(Cumbre.paper2)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Cumbre.ink2)
                }
            }
            .buttonStyle(.plain)
            .padding(.top, 8)

            if expanded {
            if notes.isEmpty {
                Text("Sin notas aún. ¡Sé el primero!")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(.vertical, 4)
            } else {
                // Llegan del backend ordenadas por utilidad (▲ − ▼).
                ForEach(notes.filter { !moderation.hiddenIds.contains("NOTE:\($0.id)") }, id: \.id) { n in
                    NoteRowView(note: n, onPhotoTap: { photoNote = n },
                                onVote: { v in onVote(n, v) },
                                canReport: Auth.auth().currentUser?.uid != n.uid,
                                onReport: { reportNote = n })
                    Divider().overlay(Cumbre.rule)
                }
            }

            // Composer
            VStack(alignment: .trailing, spacing: 8) {
                TextField(NSLocalizedString("detail_write_note", comment: ""), text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.system(size: 15))
                    .foregroundStyle(Cumbre.ink)
                    .padding(10)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))

                // Vista previa de la foto elegida (con opción de quitarla).
                if let img = pickedImage {
                    HStack(spacing: 8) {
                        Image(uiImage: img).resizable().scaledToFill()
                            .frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 4))
                        Button { pickedImage = nil; pickerItem = nil } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                        Spacer()
                    }
                }

                HStack(spacing: 10) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "camera").font(.system(size: 16)).foregroundStyle(Cumbre.terra)
                            .frame(width: 34, height: 34).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    Spacer()
                    Button {
                        let text = draft; let img = pickedImage
                        draft = ""; pickedImage = nil; pickerItem = nil
                        onPublish(text, img)
                    } label: {
                        HStack(spacing: 6) {
                            if publishing { ProgressView().tint(.white) }
                            Text("PUBLICAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(canPublish ? Cumbre.terra : Cumbre.ink3)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canPublish)
                }
            }
            .padding(.top, 8)
            }   // fin if expanded
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) { pickedImage = img }
            }
        }
        .sheet(item: $photoNote) { n in
            NotePhotoSheet(note: n)
        }
        .sheet(item: $reportNote) { n in
            ReportSheet(title: "DENUNCIAR NOTA", authorLabel: n.author ?? "usuario") { reason, alsoBlock in
                moderation.report(targetType: "NOTE", targetId: n.id, reason: reason,
                                  alsoBlockUid: alsoBlock ? n.uid : nil)
            }
        }
    }

    private var canPublish: Bool {
        !publishing && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

private struct NoteRowView: View {
    let note: Note
    let onPhotoTap: () -> Void
    var onVote: (Int) -> Void = { _ in }
    var canReport: Bool = false
    var onReport: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(note.author ?? "Anónimo")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Spacer()
                Text(shortDate(note.createdAt))
                    .font(Cumbre.mono(11))
                    .foregroundStyle(Cumbre.ink3)
            }
            Text(note.text)
                .font(.system(size: 15))
                .foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let url = note.photoUrl, let u = URL(string: url) {
                Button(action: onPhotoTap) {
                    AsyncImage(url: u) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(Cumbre.paper)
                    }
                    .frame(height: 120)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            // Voto de utilidad: tocar de nuevo tu voto lo retira.
            HStack(spacing: 6) {
                if canReport {
                    Button(action: onReport) {
                        Image(systemName: "flag")
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink3.opacity(0.7))
                            .padding(10)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                voteChip("▲ \(note.upvotesCount)", active: note.myVote == 1) { onVote(1) }
                voteChip("▼ \(note.downvotesCount)", active: note.myVote == -1) { onVote(-1) }
            }
        }
        .padding(.vertical, 8)
    }

    private func voteChip(_ label: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(active ? Cumbre.terra : Cumbre.ink2)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(active ? Cumbre.terraBg : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8)
                    .stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Foto de la nota a pantalla completa. `Note` es Identifiable vía su `id`.
private struct NotePhotoSheet: View {
    let note: Note
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let url = note.photoUrl, let u = URL(string: url) {
                        AsyncImage(url: u) { img in
                            img.resizable().scaledToFit()
                        } placeholder: { ProgressView() }
                    }
                    Text(note.text).font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                        .padding(.horizontal, 16)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }
}

/// Fila de 5 estrellas tocables para valorar una vía.
struct LineStarsRow: View {
    let lineId: String
    let avgStars: Float?
    let myStars: Int
    let onRate: (Int) -> Void

    // Estilo Google Play: las estrellas muestran la MEDIA (amarillo) y son
    // tocables para votar; el toque se ve al instante y luego la media se
    // recalcula con el dato refrescado.
    @State private var pending: Int? = nil
    private let amber = Color(red: 0.96, green: 0.62, blue: 0.04)

    private var shown: Int { pending ?? Int((avgStars ?? 0).rounded()) }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                Button {
                    let newStars = myStars == i ? 0 : i   // re-tocar tu voto → quitarlo
                    pending = newStars > 0 ? newStars : nil
                    onRate(newStars)
                } label: {
                    Image(systemName: i <= shown ? "star.fill" : "star")
                        .font(.system(size: 13))
                        .foregroundStyle(i <= shown ? amber : Cumbre.ink3)
                }
                .buttonStyle(.plain)
            }
            if let avg = avgStars, avg > 0 {
                Text(String(format: "%.1f", avg))
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    .padding(.leading, 4)
            }
            if myStars > 0 {
                Text("· tu voto \(myStars)★")
                    .font(Cumbre.mono(11)).foregroundStyle(amber)
                    .padding(.leading, 4)
            }
        }
        .padding(.leading, 34)
        .onChange(of: avgStars) { _, _ in pending = nil }
        .onChange(of: myStars) { _, _ in pending = nil }
    }
}

/// Extrae la fecha (YYYY-MM-DD) de un timestamp ISO; si no encaja, devuelve tal cual.
private func shortDate(_ iso: String) -> String {
    if iso.count >= 10 { return String(iso.prefix(10)) }
    return iso
}

#Preview {
    NavigationStack {
        SchoolDetailView(school: School(id: "x", name: "Demo", location: "Demo", region: "Aragón",
                                        style: "Boulder", rockType: "Caliza", lat: 0, lon: 0, source: nil))
    }
}

/// Polígono irregular con la silueta del marcador de piedra (botonera).
struct StonePolygon: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width, h = rect.height
        p.move(to: CGPoint(x: 0.16*w, y: 0.78*h))
        p.addLine(to: CGPoint(x: 0.07*w, y: 0.42*h))
        p.addLine(to: CGPoint(x: 0.30*w, y: 0.13*h))
        p.addLine(to: CGPoint(x: 0.72*w, y: 0.08*h))
        p.addLine(to: CGPoint(x: 0.94*w, y: 0.38*h))
        p.addLine(to: CGPoint(x: 0.86*w, y: 0.74*h))
        p.addLine(to: CGPoint(x: 0.54*w, y: 0.92*h))
        p.closeSubpath()
        return p
    }
}
