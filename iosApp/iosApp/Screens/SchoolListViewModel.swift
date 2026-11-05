import SwiftUI
import Shared
import CoreLocation
import UIKit

// Estado de la lista de Escuelas: catálogo, filtros, notas, favoritas, comparar. La pestaña «Vías/Bloques» vive en
// ExploreLinesViewModel (separados el 2026-11-05).

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
    @Published var maxDistanceKm: Double? = 50   // 50 km por defecto (como Android/PWA)
    @Published var showMode: ShowMode = .all
    @Published var savedIds: Set<String> = []    // escuelas guardadas offline (observeSaved)
    @Published var savedSchoolsList: [SavedSchool] = []  // datos de las guardadas (para verlas sin red)
    @Published var sortBy: SortMode = .score
    @Published var favoriteIds: Set<String> = []
    /// Falta el PERMISO de ubicación (se sabe al instante, sin esperar al GPS).
    /// El aviso de Filtros y el botón del mapa usan esto y no `userLat == nil`,
    /// que durante unos instantes tras abrir la app es nil aunque haya permiso.
    @Published var needsLocationPermission: Bool = !AppDependencies.shared.locationBridge.hasPermission()
    @Published var userLat: Double?
    @Published var userLon: Double?
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
    /// Mapa de la lista desplegado (botón del mapa en la fila Escuelas / Vías-Bloques).
    @Published var showMap = false
    /// Panel de días desplegado (chip HOY ⌄): plegado por defecto para que la lista se vea antes.
    @Published var daysOpen = false
    @Published var rangeScores: [String: RangeScore] = [:]
    var rangeMode: Bool { !selectedDates.isEmpty }

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

    /// Vuelve a "HOY": quita los días elegidos.
    func clearDates() {
        selectedDates = []
        rangeScores = [:]
    }

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
        needsLocationPermission = !locationBridge.hasPermission()
        if userLat == nil { await loadLocation() }
    }

    private func loadLocation() async {
        needsLocationPermission = !locationBridge.hasPermission()
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
