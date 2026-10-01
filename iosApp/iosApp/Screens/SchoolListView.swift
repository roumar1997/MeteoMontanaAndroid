import SwiftUI
import Shared
import CoreLocation
import UIKit

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,
// buscador inline, filtros, y la fila rica (badge tintado + rank + nombre serif
// + estrella + subtítulo + heatmap 10 celdas + tag ● SECA/MOJADA).
// Datos reales del backend vía use cases compartidos (KMP). Filtrado local.

@MainActor
final class SchoolListViewModel: ObservableObject {
    @Published var schools: [School] = []
    @Published var scores: [String: SchoolScore] = [:]
    @Published var loading = true
    @Published var errorText: String?

    enum SortMode: String, CaseIterable {
        case score = "Mejor score", distance = "Más cercanos"
        var label: String { L(rawValue) }   // rawValue = clave en español; L() da el texto del idioma activo
    }
    // Filtro rápido: todas / solo favoritas / solo guardadas offline.
    enum ShowMode: String, CaseIterable {
        case all = "Todas", favorites = "Favoritos", saved = "Guardados"
        var label: String { L(rawValue) }
    }
    static let distanceOptions: [Double?] = [nil, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500]

    @Published var query = ""
    @Published var style: String?
    @Published var rock: String?
    @Published var maxDistanceKm: Double? = 50 { didSet { dispatchExplore() } }   // 50 km por defecto (como Android/PWA)
    @Published var showMode: ShowMode = .all
    @Published var savedIds: Set<String> = []    // escuelas guardadas offline (observeSaved)
    @Published var savedSchoolsList: [SavedSchool] = []  // datos de las guardadas (para verlas sin red)
    @Published var sortBy: SortMode = .score
    @Published var favoriteIds: Set<String> = []
    @Published var userLat: Double? { didSet { dispatchExplore() } }
    @Published var userLon: Double? { didSet { dispatchExplore() } }
    @Published var compareSelection: Set<String> = []  // long-press para comparar (máx 3)
    @Published var unreadNotifications: Int = 0
    @Published var unreadChats: Int = 0          // badge en el icono de mensajes
    private var chatTask: Task<Void, Never>?
    /// Lo que ACABAS de tocar en la ficha de una escuela manda sobre lo que
    /// trajo el catálogo — sin esto la lista seguiría sin marcar hasta la
    /// próxima recarga completa (Álvaro, 2026-09-05: "que no haga falta
    /// estar recargando").
    @Published var processionaryOverrides: [String: Bool] = [:]
    private var processionaryTask: Task<Void, Never>?

    func startObservingProcessionaryOverrides() {
        guard processionaryTask == nil else { return }
        processionaryTask = Task { [weak self] in
            for await map in ProcessionaryOverrideStore.shared.observe() {
                self?.processionaryOverrides = map.mapValues { $0.boolValue }
            }
        }
    }

    func processionaryAlertActive(for school: School) -> Bool {
        processionaryOverrides[school.id] ?? school.processionaryAlertActive
    }

    // ── Selector de días (tramo) ──
    // Fechas ISO (yyyy-MM-dd) elegidas, máx 5. Vacío = modo "hoy".
    @Published var selectedDates: Set<String> = []
    @Published var rangeScores: [String: RangeScore] = [:]
    var rangeMode: Bool { !selectedDates.isEmpty }

    // ── Modo "explorar por grado" — sin pestaña, modo implícito
    // (BLOCK_SEARCH_DESIGN.md §4.1/§8): en cuanto hay gradeMin/gradeMax la
    // MISMA lista pasa a mostrar vías en vez de escuelas. Fase 3 (espejo de
    // Android, pendiente). Se valida primero en iOS vía TestFlight antes de
    // portar (Álvaro, 2026-10-01).
    enum ExploreSort: String, CaseIterable {
        case distance = "Cercanía", grade = "Grado"
        var label: String { L(rawValue) }
    }
    @Published var gradeMin: String? { didSet { dispatchExplore() } }
    @Published var gradeMax: String? { didSet { dispatchExplore() } }
    @Published var exploreSort: ExploreSort = .distance { didSet { dispatchExplore() } }
    @Published var exploreGrouped = true
    @Published var exploreHits: [LineSearchHit] = []
    @Published var exploreLoading = false
    private var exploreTask: Task<Void, Never>?
    var exploreActive: Bool { gradeMin != nil || gradeMax != nil }

    func clearExplore() { gradeMin = nil; gradeMax = nil }

    /// §8.2: resultado en vivo, sin botón "ver N vías" — debounce de ~300ms
    /// tras el último cambio (grado, radio o ubicación), mismo patrón que
    /// dispatchViaSearch.
    func dispatchExplore() {
        exploreTask?.cancel()
        guard exploreActive else { exploreHits = []; exploreLoading = false; return }
        exploreLoading = true
        exploreTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            let criteria = LineExploreCriteria(
                gradeMin: self.gradeMin, gradeMax: self.gradeMax,
                discipline: nil, rockTypes: nil, schoolIds: nil, orientations: nil,
                lat: self.userLat, lon: self.userLon, maxDistanceKm: self.maxDistanceKm,
                sort: self.exploreSort == .distance ? "DISTANCE" : "GRADE_ASC", offset: 0)
            let hits = (try? await AppDependencies.shared.container.exploreLines.invoke(criteria: criteria)) ?? []
            guard !Task.isCancelled else { return }
            self.exploreHits = hits
            self.exploreLoading = false
        }
    }

    /// Agrupados por escuela, respetando el orden que ya trae el backend
    /// (cercanía o grado) — §8.3.
    var exploreGroups: [(schoolId: String, schoolName: String, hits: [LineSearchHit])] {
        var order: [String] = []
        var byId: [String: [LineSearchHit]] = [:]
        for h in exploreHits {
            if byId[h.schoolId] == nil { byId[h.schoolId] = []; order.append(h.schoolId) }
            byId[h.schoolId]!.append(h)
        }
        return order.map { id in (id, byId[id]?.first?.schoolName ?? "", byId[id] ?? []) }
    }

    private let getSchools: GetSchoolsUseCase
    private let getTodayScores: GetTodayScoresUseCase
    private let getRangeScores: GetRangeScoresUseCase
    private let getMyFavorites: GetMyFavoritesUseCase
    private let addFavorite: AddFavoriteUseCase
    private let removeFavorite: RemoveFavoriteUseCase
    private let locationBridge = AppDependencies.shared.locationBridge
    private let locationProvider = AppDependencies.shared.container.locationProvider
    private let cachedSchools = AppDependencies.shared.container.cachedSchools
    private let savedSchools = AppDependencies.shared.container.savedSchools
    private var savedTask: Task<Void, Never>?

    init(
        getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools,
        getTodayScores: GetTodayScoresUseCase = AppDependencies.shared.container.getTodayScores,
        getRangeScores: GetRangeScoresUseCase = AppDependencies.shared.container.getRangeScores,
        getMyFavorites: GetMyFavoritesUseCase = AppDependencies.shared.container.getMyFavorites,
        addFavorite: AddFavoriteUseCase = AppDependencies.shared.container.addFavorite,
        removeFavorite: RemoveFavoriteUseCase = AppDependencies.shared.container.removeFavorite
    ) {
        self.getSchools = getSchools
        self.getTodayScores = getTodayScores
        self.getRangeScores = getRangeScores
        self.getMyFavorites = getMyFavorites
        self.addFavorite = addFavorite
        self.removeFavorite = removeFavorite
    }

    /// Marca/desmarca una fecha (ISO yyyy-MM-dd), máximo 5. Recalcula el tramo.
    func toggleDate(_ iso: String) {
        if selectedDates.contains(iso) { selectedDates.remove(iso) }
        else if selectedDates.count < 5 { selectedDates.insert(iso) }
        rangeScores = [:]
        Task { await loadRangeScores() }
    }

    /// Carga los scores del tramo por lotes (≤60 ids/call), para TODAS las
    /// escuelas (así cambiar filtros no deja huecos). El backend cachea.
    private func loadRangeScores() async {
        guard !selectedDates.isEmpty else { return }
        let dates = selectedDates.sorted()
        let ids = schools.map { $0.id }
        for chunk in stride(from: 0, to: ids.count, by: 60) {
            let slice = Array(ids[chunk..<min(chunk + 60, ids.count)])
            guard let batch = try? await getRangeScores.invoke(ids: slice, dates: dates) else { continue }
            for r in batch { rangeScores[r.id] = r }
        }
    }

    // Estilos combinados ("Bloque,Vía") se descomponen en sus valores
    // individuales — el filtro no debe ofrecer la combinación como su propia
    // opción, solo Vía / Bloque, cada una recogiendo también las escuelas
    // mixtas (ver `matchesStyle`).
    var styles: [String] { uniqueValues(schools.flatMap { ($0.style ?? "").split(separator: ",").map { String($0) } }) }
    // Granito, Caliza y Arenisca primero (las mas buscadas); el resto alfabetico.
    var rocks: [String] {
        let all = uniqueValues(schools.map { $0.rockType })
        let priority = ["Granito", "Caliza", "Arenisca"]
        let first = priority.compactMap { p in all.first { $0.caseInsensitiveCompare(p) == .orderedSame } }
        return first + all.filter { r in !first.contains(r) }
    }
    var activeFilters: Bool { style != nil || rock != nil || maxDistanceKm != nil || showMode != .all }
    func clearFilters() { style = nil; rock = nil; query = ""; maxDistanceKm = nil; showMode = .all }

    func toggleCompare(_ id: String) {
        if compareSelection.contains(id) { compareSelection.remove(id) }
        else if compareSelection.count < 3 { compareSelection.insert(id) }
    }
    func clearCompare() { compareSelection.removeAll() }

    // MEMOIZADO: es propiedad calculada y SwiftUI la evalúa en cada pasada de
    // layout (prefetch de la lazy list incluido) — cada pasada recorría las
    // 191 escuelas KOTLIN (puentes ObjC + GC). Con la firma de entradas solo
    // se recalcula cuando cambia algo de verdad.
    private var filteredCache: (sig: String, list: [School])? = nil
    var filtered: [School] {
        // En pasos: 14 elementos en una expresión saturan el type-checker.
        var parts: [String] = []
        parts.append(query)
        parts.append(style ?? "")
        parts.append(rock ?? "")
        parts.append(String(maxDistanceKm ?? -1))
        parts.append(String(describing: showMode))
        parts.append(String(describing: sortBy))
        parts.append(String(rangeMode))
        parts.append(String(schools.count))
        parts.append(String(scores.count))
        parts.append(String(rangeScores.count))
        parts.append(String(favoriteIds.count))
        parts.append(String(savedSchoolsList.count))
        parts.append(String(userLat ?? 0))
        parts.append(String(userLon ?? 0))
        let sig = parts.joined(separator: "|")
        if let c = filteredCache, c.sig == sig { return c.list }
        let list = computeFiltered()
        filteredCache = (sig, list)
        return list
    }

    private func computeFiltered() -> [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        // En modo GUARDADOS partimos de las escuelas guardadas offline: si el
        // catálogo no está en caché (sin red, primera vez), las sintetizamos
        // desde el snapshot guardado para que SÍ se vean.
        let base: [School]
        if showMode == .saved {
            base = savedSchoolsList.map { sv in
                schools.first { $0.id == sv.id }
                    ?? School(id: sv.id, name: sv.name, location: nil, region: sv.region,
                              style: nil, rockType: sv.rockType, lat: sv.lat, lon: sv.lon, source: nil,
                              country: "ES", hasKnownProcessionary: false, processionaryAlertActive: false, processionaryActiveNowSet: false)
            }
        } else {
            base = schools
        }
        var list: [School]
        if !q.isEmpty {
            // BÚSQUEDA por nombre: manda sobre TODO. Ignora distancia/estilo/roca/
            // favoritas para que "Albarracín" salga aunque esté fuera del radio.
            list = base.filter {
                $0.name.lowercased().contains(q) || ($0.location?.lowercased().contains(q) ?? false)
            }
        } else {
            list = base.filter { s in
                (style == nil || matchesStyle(s.style, style!))
                && (rock == nil || s.rockType?.caseInsensitiveCompare(rock!) == .orderedSame)
            }
            // Distancia (solo si hay ubicación y límite elegido). En modo
            // GUARDADOS NO se aplica: quiero ver todas mis guardadas aunque estén
            // lejos (igual que la búsqueda por nombre ignora el radio).
            if showMode != .saved, let max = maxDistanceKm, let la = userLat, let lo = userLon {
                list = list.filter { Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: $0.lat, lon2: $0.lon) <= max }
            }
            // Solo favoritas / solo guardadas offline.
            switch showMode {
            case .all: break
            case .favorites: list = list.filter { favoriteIds.contains($0.id) }
            case .saved: break   // `base` ya está restringido a las guardadas
            }
        }
        // Orden DECORATE-SORT: la clave se calcula UNA vez por escuela (cada
        // acceso a un objeto Kotlin cruza el puente ObjC; hacerlo dentro del
        // comparador eran ~3.000 cruces por orden).
        switch sortBy {
        case .score:
            if rangeMode {
                let keyed = list.map { ($0, rangeScores[$0.id]?.combinedScore ?? -1) }
                return keyed.sorted { $0.1 > $1.1 }.map { $0.0 }
            }
            let keyed = list.map { ($0, scores[$0.id]?.todayScore ?? -1) }
            return keyed.sorted { $0.1 > $1.1 }.map { $0.0 }
        case .distance:
            guard let la = userLat, let lo = userLon else {
                let keyed = list.map { ($0, scores[$0.id]?.todayScore ?? -1) }
                return keyed.sorted { $0.1 > $1.1 }.map { $0.0 }
            }
            let keyed = list.map { s -> (School, Double) in
                (s, Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: s.lat, lon2: s.lon))
            }
            return keyed.sorted { $0.1 < $1.1 }.map { $0.0 }
        }
    }

    /// Observa mis conversaciones para el badge de chats sin leer (suma de unread).
    func startObservingChats() {
        guard chatTask == nil, let chat = AppDependencies.shared.container.chatService else { return }
        chatTask = Task { [weak self] in
            for await convs in chat.observeMyConversations() {
                guard let self else { return }
                self.unreadChats = convs
                    .filter { !chatIsHiddenForMe($0) }
                    .reduce(0) { $0 + Int(truncatingIfNeeded: $1.unreadCount) }
                self.warmChatProfiles(convs)
            }
        }
    }

    /// uids cuyo perfil ya cacheé esta sesión (no repetir la llamada).
    private var warmedProfileUids = Set<String>()

    /// Pre-cachea (en disco) los perfiles de todos los participantes de mis
    /// conversaciones con solo tener la app abierta online — sin entrar a Chats.
    /// Así offline el chat ya muestra nombres/avatares (estilo Instagram).
    /// getPublicProfile escribe el caché; AvatarCircle persiste el avatar en disco.
    private func warmChatProfiles(_ convs: [ChatServiceConversation]) {
        let uids = Set(convs.flatMap { $0.participants.compactMap { $0 as? String } })
            .subtracting(warmedProfileUids)
        guard !uids.isEmpty else { return }
        let getProfile = AppDependencies.shared.container.getPublicProfile
        for uid in uids {
            Task { [weak self] in
                // Cachea el perfil (nombre/usuario) y PRE-DESCARGA el avatar a
                // disco, para que el chat se vea offline sin haber abierto Chats.
                // Solo marcamos el uid como cacheado si la llamada tuvo éxito; si
                // falla (token aún no listo al arrancar) se reintenta en la
                // siguiente emisión del observador.
                guard let p = try? await getProfile.invoke(uid: uid) else { return }
                self?.warmedProfileUids.insert(uid)
                if let photo = p.photoUrl, !photo.isEmpty {
                    await ImageCache.prefetch([photo])
                }
            }
        }
    }

    func load() async {
        errorText = nil
        startObservingSaved()
        startObservingChats()
        startObservingProcessionaryOverrides()
        // 1. Pinta desde la caché local al instante (si la hay).
        if let cached = try? await cachedSchools?.load(), !cached.isEmpty {
            schools = cached
            loading = false
        } else {
            loading = true
        }
        // 2. Revalida desde red y actualiza la caché.
        do {
            let fresh = try await getSchools.invoke(
                region: nil, style: nil, rockType: nil, lat: nil, lon: nil, radioKm: nil
            )
            schools = fresh
            try? await cachedSchools?.replaceAll(schools: fresh)
        } catch {
            // Sin red: si no había caché, error; si había, seguimos offline.
            if schools.isEmpty { errorText = error.localizedDescription }
        }
        loading = false
        await loadLocation()
        await loadFavorites()
        await loadUnread()
        await loadScores()
    }

    func refresh() async { await load() }

    /// Observa las escuelas guardadas offline (Flow de SQLDelight) para el filtro
    /// GUARDADOS. Idempotente: solo arranca un task.
    func startObservingSaved() {
        guard savedTask == nil, let savedSchools else { return }
        savedTask = Task { [weak self] in
            for await list in savedSchools.observeSaved() {
                guard let self else { return }
                self.savedIds = Set(list.map { $0.id })
                self.savedSchoolsList = list
            }
        }
    }

    private func loadUnread() async {
        if let inbox = try? await AppDependencies.shared.container.getMyNotifications.invoke(limit: 50) {
            unreadNotifications = Int(inbox.unreadCount)
        }
    }

    /// Recarga el contador de no leídas (al cerrar la bandeja de notificaciones).
    func refreshUnread() async { await loadUnread() }

    /// Reintenta cargar la ubicación si aún no la tenemos (p. ej. al primer
    /// arranque, cuando el permiso se concede DESPUÉS de cargar la lista → sin
    /// esto salían todas las escuelas ignorando el filtro de 50 km hasta
    /// reabrir la app; espeja el onLocationGranted() de Android).
    func refreshLocationIfNeeded() async {
        if userLat == nil { await loadLocation() }
    }

    private func loadLocation() async {
        guard locationBridge.hasPermission() else { return }
        if let loc = try? await locationProvider?.current() {
            userLat = loc.lat; userLon = loc.lon
        }
    }

    /// "Activar ubicación" del aviso en DISTANCIA (Álvaro, 2026-10-01): a
    /// diferencia de Tiempo, este mapa no ofrecía ninguna forma de arreglarlo
    /// si el permiso quedó denegado — solo mostraba el punto azul en silencio
    /// si ya estaba concedido, sin más. Denegado → Ajustes (reabrir el diálogo
    /// del sistema no hace nada ahí); sin decidir todavía → pide permiso.
    func requestLocation() {
        if locationBridge.isDeniedOrRestricted() {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        } else {
            locationBridge.requestPermission()
        }
    }

    /// Distancia en km del usuario a la escuela (Haversine compartido). nil si
    /// no hay ubicación.
    func distanceKm(_ school: School) -> Int? {
        guard let la = userLat, let lo = userLon else { return nil }
        let km = Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: school.lat, lon2: school.lon)
        return Int(km.rounded())
    }

    private func loadFavorites() async {
        // Requiere sesión (el login es obligatorio al arrancar). Si falla (offline),
        // partimos de lo que ya hay en pantalla para no perder el estado.
        let container = AppDependencies.shared.container
        let server = try? await getMyFavorites.invoke()
        var base = server.map { Set($0.map { $0.id }) } ?? favoriteIds
        // Reconciliar con la cola offline: suma marcadas y resta desmarcadas.
        if let add = try? await container.pendingFavoriteIds() { base.formUnion(add) }
        if let del = try? await container.pendingFavoriteDeleteIds() { base.subtract(del) }
        favoriteIds = base
    }

    /// Toggle optimista: actualiza la estrella al instante. Si la red falla
    /// (offline), NO revierte: encola la acción para sincronizarla al reconectar
    /// (mismo comportamiento que SchoolListViewModel.kt de Android).
    func toggleFavorite(_ schoolId: String) {
        let wasFavorite = favoriteIds.contains(schoolId)
        let nowFavorite = !wasFavorite
        if wasFavorite { favoriteIds.remove(schoolId) } else { favoriteIds.insert(schoolId) }
        Task {
            do {
                if wasFavorite { try await removeFavorite.invoke(schoolId: schoolId) }
                else { try await addFavorite.invoke(schoolId: schoolId) }
            } catch {
                // Sin red: mantener el estado optimista y encolar para más tarde.
                try? await AppDependencies.shared.container.enqueueFavorite(schoolId: schoolId, favorite: nowFavorite)
            }
        }
    }

    private func loadScores() async {
        let ids = schools.map { $0.id }
        for chunk in stride(from: 0, to: ids.count, by: 50) {
            let slice = Array(ids[chunk..<min(chunk + 50, ids.count)])
            guard let batch = try? await getTodayScores.invoke(ids: slice) else { continue }
            // UNA publicación por lote (no por escuela): cada escritura de un
            // @Published reordena y re-difea la lista ENTERA de 191 filas —
            // ~200 seguidas atascaban el hilo principal >5s → watchdog
            // 0x8BADF00D (los cierres de jul-2026).
            var acc = scores
            for s in batch { acc[s.id] = s }
            scores = acc
        }
        // Offline (o ids que la red no devolvió): rellenar con el forecast
        // cacheado de cada escuela guardada/visitada, para que la lista pinte el
        // score guardado en vez de "—". El detalle ya lo mostraba (imagen 2).
        let container = AppDependencies.shared.container
        var acc = scores
        for id in ids where acc[id] == nil {
            if let s = try? await container.cachedTodayScore(schoolId: id) { acc[id] = s }
        }
        if acc.count != scores.count { scores = acc }
    }

    private func uniqueValues(_ raw: [String?]) -> [String] {
        Array(Set(raw.compactMap { $0 }.filter { !$0.isEmpty })).sorted()
    }

    // Una escuela "Bloque,Vía" tiene que salir al filtrar por Vía Y al
    // filtrar por Bloque — mismo criterio que `hasStyle` en el backend
    // (GetSchoolsUseCase.java).
    private func matchesStyle(_ schoolStyle: String?, _ wanted: String) -> Bool {
        guard let schoolStyle else { return false }
        return schoolStyle.split(separator: ",").contains {
            $0.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(wanted) == .orderedSame
        }
    }
}

struct SchoolListView: View {
    @StateObject private var vm = SchoolListViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            ScrollView {
                // VStack NO perezoso a propósito: las 6 muertes por watchdog
                // (jul-2026) ocurrieron DENTRO de la maquinaria de LazyVStack
                // (prefetch, placement, motionVectors) recolocando estas ~191
                // filas al reordenar/filtrar. Componerlas todas cuesta ~decenas
                // de ms en Release y elimina esa maquinaria entera (mismo
                // movimiento «piedras fluidas» que en Android).
                VStack(spacing: 0) {
                    TopIconsRow(unreadCount: vm.unreadNotifications,
                                chatUnread: vm.unreadChats,
                                onNotificationsClosed: { Task { await vm.refreshUnread() } })
                    HeaderEscuelas(count: vm.loading ? nil : vm.schools.count,
                                   onSubmitBlockPhoto: { eligiendoFoto = true })
                    SearchField(text: $vm.query)
                    if vm.query.trimmingCharacters(in: .whitespaces).count >= 2 {
                        viaHitsSection
                    }

                    // Hint del mapa — justo antes del toggle "VER MAPA"
                    FirstTimeHint(
                        hintKey: "schools_map",
                        text: L("Toca \"VER MAPA\" para ver todas las escuelas en el mapa, coloreadas por su índice del día.")
                    )
                    MapToggleAndPanel(vm: vm, onOpen: { navTarget = SchoolNavTarget(school: $0, via: nil) })

                    // Hint de filtros — justo antes de la barra de filtros
                    FirstTimeHint(
                        hintKey: "schools_filters",
                        text: L("Usa los filtros de abajo para encontrar escuelas por distancia, tipo de roca o estilo (bloque/vía).")
                    )
                    FilterChips(vm: vm)
                    DaySelectorRow(vm: vm)
                    Divider().overlay(Cumbre.rule)

                    // Hint de comparar — justo antes de la lista
                    FirstTimeHint(
                        hintKey: "schools_compare",
                        text: L("Mantén pulsada una escuela para compararla con otras (hasta 3). También puedes tocar los días de arriba para ver un tramo de varios días.")
                    )

                    if vm.loading {
                        ForEach(0..<6, id: \.self) { _ in SkeletonRow(); Divider().overlay(Cumbre.rule) }
                    } else if let err = vm.errorText {
                        ErrorRow(message: err) { Task { await vm.refresh() } }
                    } else if vm.exploreActive {
                        exploreResultsSection
                    } else {
                        let items = vm.filtered
                        if items.isEmpty {
                            EmptyRow(canClear: vm.activeFilters || !vm.query.isEmpty) { vm.clearFilters() }
                        } else {
                            ForEach(Array(items.enumerated()), id: \.element.id) { idx, school in
                                // Tap: si hay selección de comparar activa, togglea;
                                // si no, navega al detalle. Mantener pulsado: entra en
                                // modo comparar. Sin Button/NavigationLink: dentro de un
                                // ScrollView el Button se "come" el long-press (no fiable).
                                // Usamos tap + long-press directos sobre la fila.
                                SchoolListItemView(
                                    rank: idx + 1,
                                    school: school,
                                    score: vm.scores[school.id],
                                    range: vm.rangeMode ? vm.rangeScores[school.id] : nil,
                                    distanceKm: vm.distanceKm(school),
                                    isFavorite: vm.favoriteIds.contains(school.id),
                                    isSelected: vm.compareSelection.contains(school.id),
                                    processionaryAlertActive: vm.processionaryAlertActive(for: school),
                                    onToggleFavorite: { vm.toggleFavorite(school.id) }
                                )
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if vm.compareSelection.isEmpty { navTarget = SchoolNavTarget(school: school, via: nil) }
                                    else { vm.toggleCompare(school.id) }
                                }
                                .onLongPressGesture(minimumDuration: 0.35) { vm.toggleCompare(school.id) }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(item: $navTarget) { SchoolDetailView(school: $0.school, openVia: $0.via) }
            .overlay {
                if eligiendoFoto {
                    SubmitBlockPhotoFlow(
                        schools: vm.schools,
                        onOpenSchool: { id in
                            eligiendoFoto = false
                            if let s = vm.schools.first(where: { $0.id == id }) {
                                navTarget = SchoolNavTarget(school: s, via: nil)
                            }
                        },
                        onDismiss: { eligiendoFoto = false })
                }
            }
            .onChange(of: vm.query) { _, _ in dispatchViaSearch() }
            .overlay(alignment: .bottom) {
                if vm.compareSelection.count >= 1 {
                    CompareBar(count: vm.compareSelection.count,
                               canCompare: vm.compareSelection.count >= 2,
                               onClear: { vm.clearCompare() },
                               onCompare: { showCompare = true })
                }
            }
            .sheet(isPresented: $showCompare, onDismiss: { vm.clearCompare() }) {
                CompareView(schools: vm.filtered.filter { vm.compareSelection.contains($0.id) })
            }
            .task { await vm.load() }
            // Al volver a activo (p. ej. tras aceptar el permiso de ubicación en
            // el primer arranque) reintenta cargar la ubicación si falta, para
            // que el filtro de 50 km se aplique sin tener que reabrir la app.
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await vm.refreshLocationIfNeeded() } }
            }
            .refreshable { await vm.refresh() }
        }
    }

    @State private var showCompare = false
    /// Destino de navegación: escuela + vía EN EL MISMO valor. Antes eran dos
    /// @State sueltos y `navigationDestination(item:)` captura su closure ANTES
    /// de que el body se reevalúe: el primer toque construía el detalle con la
    /// vía TODAVÍA nil (abría la escuela a secas) y el segundo funcionaba
    /// porque navVia conservaba el valor del intento anterior. Con un único
    /// item la carrera desaparece.
    /// Hashable A MANO por `id`: navigationDestination(item:) exige Hashable y
    /// la síntesis automática no es fiable con `School` (clase de Kotlin).
    struct SchoolNavTarget: Identifiable, Hashable {
        let school: School
        let via: String?
        var id: String { school.id + "|" + (via ?? "") }
        static func == (a: SchoolNavTarget, b: SchoolNavTarget) -> Bool { a.id == b.id }
        func hash(into hasher: inout Hasher) { hasher.combine(id) }
    }
    @State private var navTarget: SchoolNavTarget?
    /// "Enviar piedra": el selector de fotos está abierto.
    @State private var eligiendoFoto = false
    // Buscador global de vías/bloques: vía a abrir al navegar + resultados.
    @State private var viaHits: [LineSearchHit] = []   // modelo de DOMINIO (via use case)
    @State private var viaSearchTask: Task<Void, Never>?

    /// Relanza la busqueda global (solo en modo vias/bloques), con debounce.
    private func dispatchViaSearch() {
        viaSearchTask?.cancel()
        let trimmed = vm.query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { viaHits = []; return }
        viaSearchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            // Regla DI: por el use case del container, no la API directa.
            let hits = (try? await AppDependencies.shared.container.searchLines.invoke(query: trimmed)) ?? []
            if !Task.isCancelled { viaHits = hits }
        }
    }

    /// Resultados del buscador UNICO en DOS secciones (estilo Spotlight):
    /// ESCUELAS (top 5, acceso directo) y VIAS Y BLOQUES (global + mini-topo).
    /// Las cabeceras salen SIEMPRE al escribir: se aprende que busca ambas.
    private var viaHitsSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            VStack(alignment: .leading, spacing: 0) {
                Text("ESCUELAS").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                let schoolMatches = Array(vm.filtered.prefix(5))
                if schoolMatches.isEmpty {
                    Text("Sin resultados").font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 12).padding(.bottom, 8)
                } else {
                    ForEach(schoolMatches, id: \.id) { school in
                        Button { navTarget = SchoolNavTarget(school: school, via: nil) } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(school.name).font(.system(size: 14))
                                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    if let r = school.region, !r.isEmpty {
                                        Text(regionLabel(r)).font(.system(size: 12))
                                            .foregroundStyle(Cumbre.ink3).lineLimit(1)
                                    }
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                Divider().overlay(Cumbre.rule)
                Text("VÍAS Y BLOQUES").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                if viaHits.isEmpty {
                    Text("Sin resultados").font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 12).padding(.bottom, 8)
                }
                ForEach(viaHits, id: \.stableId) { h in
                    Button {
                        if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                            navTarget = SchoolNavTarget(
                                school: school, via: h.lineId ?? h.lineName ?? h.blockName)
                        }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text((h.lineName ?? h.blockName) + (h.grade.map { " · \($0)" } ?? ""))
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
                                Text([h.lineName != nil ? h.blockName : nil, h.sectorName, h.schoolName]
                                        .compactMap { $0 }.filter { !$0.isEmpty }
                                        .joined(separator: " · "))
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 9)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    // Mini-topo: foto de la cara con la linea dibujada (si el
                    // backend mando foto; las piedras salen sin trazo).
                    if let photo = h.photoPath, !photo.isEmpty {
                        // P8: dedup de puntos casi identicos (trazos antiguos
                        // fusionaban los guiones -> linea continua) y la FOTO
                        // tambien abre la piedra (paridad Android).
                        let pts = dedupPoints(TopoParse.points(h.linePath))
                        Button {
                            if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                                navTarget = SchoolNavTarget(
                                    school: school, via: h.lineId ?? h.lineName ?? h.blockName)
                            }
                        } label: {
                            TopoPhotoView(photoUrl: photo, lines: pts.count >= 2 ? [
                                TopoLineVM(id: h.lineId ?? "hit", name: h.lineName,
                                           grade: h.grade, startType: h.startType, points: pts)
                            ] : [])
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 12).padding(.bottom, 10)
                    }
                }
            }
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
    }

    // MARK: - Modo "explorar por grado" (§4.1/§8) — resultados

    private var exploreResultsSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(vm.exploreLoading ? L("Buscando…") : L("%@ vías encontradas", vm.exploreHits.count))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                Spacer()
                Button { vm.exploreGrouped.toggle() } label: {
                    Image(systemName: vm.exploreGrouped ? "rectangle.grid.1x2" : "square.grid.2x2")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            if vm.exploreLoading && vm.exploreHits.isEmpty {
                ForEach(0..<4, id: \.self) { _ in SkeletonRow(); Divider().overlay(Cumbre.rule) }
            } else if vm.exploreHits.isEmpty {
                EmptyRow(canClear: true) { vm.clearExplore() }
            } else if vm.exploreGrouped {
                ForEach(vm.exploreGroups, id: \.schoolId) { group in
                    exploreGroupHeader(group)
                    ForEach(group.hits, id: \.stableId) { h in
                        exploreLineRow(h)
                        Divider().overlay(Cumbre.rule)
                    }
                }
            } else {
                ForEach(vm.exploreHits, id: \.stableId) { h in
                    exploreLineRow(h, showSchool: true)
                    Divider().overlay(Cumbre.rule)
                }
            }
        }
    }

    /// Cabecera de grupo: nombre de la escuela + su índice de escalabilidad de
    /// HOY (el mismo score que en la lista de Escuelas) + aviso MOJADA — una
    /// sola vez por grupo en vez de repetirlo vía a vía (§8.3).
    private func exploreGroupHeader(_ g: (schoolId: String, schoolName: String, hits: [LineSearchHit])) -> some View {
        let score = vm.scores[g.schoolId]
        let scoreInt = score.map { Int($0.todayScore) }
        let color = scoreInt.map { Cumbre.score($0) } ?? Cumbre.ink3
        return HStack(spacing: 10) {
            Text(scoreInt.map(String.init) ?? "—")
                .font(Cumbre.serif(17, .bold))
                .foregroundStyle(color)
                .frame(width: 34, height: 30)
                .background(color.opacity(0.12))
                .overlay(Rectangle().stroke(color, lineWidth: 1))
            VStack(alignment: .leading, spacing: 1) {
                Text(g.schoolName).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                if score?.dryRock == false {
                    Text(L("● MOJADA")).font(.system(size: 10, weight: .semibold)).tracking(0.6)
                        .foregroundStyle(Cumbre.bad)
                }
            }
            Spacer()
            Text(g.hits.count == 1 ? L("1 vía") : L("%@ vías", g.hits.count))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(Cumbre.paper)
    }

    /// Fila de vía: miniatura (§8.4), nombre + grado coloreado, piedra/escuela,
    /// orientación votada o "SIN ORIENTACIÓN ASIGNADA" (§8.1), distancia.
    private func exploreLineRow(_ h: LineSearchHit, showSchool: Bool = false) -> some View {
        Button {
            if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                navTarget = SchoolNavTarget(school: school, via: h.lineId ?? h.lineName ?? h.blockName)
            }
        } label: {
            HStack(spacing: 10) {
                if let photo = h.photoPath, !photo.isEmpty, let url = URL(string: photo) {
                    AsyncImage(url: url) { phase in
                        if let img = phase.image { img.resizable().scaledToFill() }
                        else { Cumbre.rule.opacity(0.15) }
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(h.lineName ?? h.blockName).font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                        if let g = h.grade {
                            Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(GradeColor.color(g))
                        }
                    }
                    let subtitle = [h.lineName != nil ? h.blockName : nil, showSchool ? h.schoolName : nil]
                        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                    if !subtitle.isEmpty {
                        Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                    }
                    if let o = h.orientation, !o.isEmpty {
                        Text(o).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    } else {
                        Text(L("SIN ORIENTACIÓN ASIGNADA"))
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3.opacity(0.7))
                    }
                }
                Spacer()
                if let la = vm.userLat, let lo = vm.userLon,
                   let hlat = h.lat?.doubleValue, let hlon = h.lon?.doubleValue {
                    let km = Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: hlat, lon2: hlon)
                    Text("\(Int(km.rounded())) km").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 9)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Id estable de un hit del buscador (id: \.offset invalidaba las filas y
/// sus TopoPhotoView en cada tecla).
extension LineSearchHit {
    var stableId: String { (lineId ?? "") + "|" + blockName + "|" + (schoolId ?? "") }
}

// School (clase Kotlin) Identifiable por su id — para navigationDestination(item:).
extension School: Identifiable {}


/// P8: quita puntos consecutivos casi identicos (trazos antiguos fusionaban
/// los guiones y la linea salia continua en el buscador).
fileprivate func dedupPoints(_ raw: [CGPoint]) -> [CGPoint] {
    var pts: [CGPoint] = []
    for pt in raw {
        if let last = pts.last, abs(pt.x - last.x) + abs(pt.y - last.y) < 0.004 { continue }
        pts.append(pt)
    }
    return pts
}
