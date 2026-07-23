import SwiftUI
import Shared
import CoreLocation

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

    enum SortMode: String, CaseIterable { case score = "Mejor score", distance = "Más cercanos" }
    // Filtro rápido: todas / solo favoritas / solo guardadas offline.
    enum ShowMode: String, CaseIterable { case all = "Todas", favorites = "Favoritos", saved = "Guardados" }
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
    @Published var userLat: Double?
    @Published var userLon: Double?
    @Published var compareSelection: Set<String> = []  // long-press para comparar (máx 3)
    @Published var unreadNotifications: Int = 0
    @Published var unreadChats: Int = 0          // badge en el icono de mensajes
    private var chatTask: Task<Void, Never>?

    // ── Selector de días (tramo) ──
    // Fechas ISO (yyyy-MM-dd) elegidas, máx 5. Vacío = modo "hoy".
    @Published var selectedDates: Set<String> = []
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

    var styles: [String] { uniqueValues(schools.map { $0.style }) }
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

    var filtered: [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        // En modo GUARDADOS partimos de las escuelas guardadas offline: si el
        // catálogo no está en caché (sin red, primera vez), las sintetizamos
        // desde el snapshot guardado para que SÍ se vean.
        let base: [School]
        if showMode == .saved {
            base = savedSchoolsList.map { sv in
                schools.first { $0.id == sv.id }
                    ?? School(id: sv.id, name: sv.name, location: nil, region: sv.region,
                              style: nil, rockType: sv.rockType, lat: sv.lat, lon: sv.lon, source: nil)
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
                (style == nil || s.style?.caseInsensitiveCompare(style!) == .orderedSame)
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
        // Orden.
        switch sortBy {
        case .score:
            // Modo tramo → ordena por score combinado de los días elegidos.
            if rangeMode {
                return list.sorted { (rangeScores[$0.id]?.combinedScore ?? -1) > (rangeScores[$1.id]?.combinedScore ?? -1) }
            }
            return list.sorted { (scores[$0.id]?.todayScore ?? -1) > (scores[$1.id]?.todayScore ?? -1) }
        case .distance:
            guard let la = userLat, let lo = userLon else {
                return list.sorted { (scores[$0.id]?.todayScore ?? -1) > (scores[$1.id]?.todayScore ?? -1) }
            }
            return list.sorted {
                Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: $0.lat, lon2: $0.lon)
                < Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: $1.lat, lon2: $1.lon)
            }
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
            for s in batch { scores[s.id] = s }
        }
        // Offline (o ids que la red no devolvió): rellenar con el forecast
        // cacheado de cada escuela guardada/visitada, para que la lista pinte el
        // score guardado en vez de "—". El detalle ya lo mostraba (imagen 2).
        let container = AppDependencies.shared.container
        for id in ids where scores[id] == nil {
            if let s = try? await container.cachedTodayScore(schoolId: id) { scores[id] = s }
        }
    }

    private func uniqueValues(_ raw: [String?]) -> [String] {
        Array(Set(raw.compactMap { $0 }.filter { !$0.isEmpty })).sorted()
    }
}

struct SchoolListView: View {
    @StateObject private var vm = SchoolListViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    TopIconsRow(unreadCount: vm.unreadNotifications,
                                chatUnread: vm.unreadChats,
                                onNotificationsClosed: { Task { await vm.refreshUnread() } })
                    HeaderEscuelas(count: vm.loading ? nil : vm.schools.count)
                    SearchField(text: $vm.query)
                    if !viaHits.isEmpty && vm.query.trimmingCharacters(in: .whitespaces).count >= 2 {
                        viaHitsSection
                    }

                    // Hint del mapa — justo antes del toggle "VER MAPA"
                    FirstTimeHint(
                        hintKey: "schools_map",
                        text: "Toca \"VER MAPA\" para ver todas las escuelas en el mapa, coloreadas por su índice del día."
                    )
                    MapToggleAndPanel(vm: vm, onOpen: { navSchool = $0 })

                    // Hint de filtros — justo antes de la barra de filtros
                    FirstTimeHint(
                        hintKey: "schools_filters",
                        text: "Usa los filtros de abajo para encontrar escuelas por distancia, tipo de roca o estilo (bloque/vía)."
                    )
                    FilterChips(vm: vm)
                    DaySelectorRow(vm: vm)
                    Divider().overlay(Cumbre.rule)

                    // Hint de comparar — justo antes de la lista
                    FirstTimeHint(
                        hintKey: "schools_compare",
                        text: "Mantén pulsada una escuela para compararla con otras (hasta 3). También puedes tocar los días de arriba para ver un tramo de varios días."
                    )

                    if vm.loading {
                        ForEach(0..<6, id: \.self) { _ in SkeletonRow(); Divider().overlay(Cumbre.rule) }
                    } else if let err = vm.errorText {
                        ErrorRow(message: err) { Task { await vm.refresh() } }
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
                                    onToggleFavorite: { vm.toggleFavorite(school.id) }
                                )
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if vm.compareSelection.isEmpty { navSchool = school }
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
            .navigationDestination(item: $navSchool) { SchoolDetailView(school: $0, openVia: navVia) }
            .onChange(of: vm.query) { _, q in
                viaSearchTask?.cancel()
                let trimmed = q.trimmingCharacters(in: .whitespaces)
                guard trimmed.count >= 2 else { viaHits = []; return }
                viaSearchTask = Task {
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    guard !Task.isCancelled else { return }
                    let hits = (try? await AppDependencies.shared.container.schoolApi.searchLines(query: trimmed)) ?? []
                    if !Task.isCancelled { viaHits = hits }
                }
            }
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
    @State private var navSchool: School?
    // Buscador global de vías/bloques: vía a abrir al navegar + resultados.
    @State private var navVia: String?
    @State private var viaHits: [LineSearchHitDto] = []
    @State private var viaSearchTask: Task<Void, Never>?

    /// Resultados del buscador global (vías/bloques de TODO el catálogo).
    private var viaHitsSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("VÍAS Y BLOQUES").font(Cumbre.mono(10, .bold)).tracking(1.2)
                .foregroundStyle(Cumbre.ink3)
            VStack(spacing: 0) {
                ForEach(Array(viaHits.enumerated()), id: \.offset) { _, h in
                    Button {
                        if let school = vm.schools.first(where: { $0.id == h.schoolId }) {
                            navVia = h.lineId ?? h.lineName ?? h.blockName
                            navSchool = school
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
                }
            }
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
    }
}

// School (clase Kotlin) Identifiable por su id — para navigationDestination(item:).
extension School: Identifiable {}
