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
    var rocks: [String] { uniqueValues(schools.map { $0.rockType }) }
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

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    TopIconsRow(unreadCount: vm.unreadNotifications,
                                chatUnread: vm.unreadChats,
                                onNotificationsClosed: { Task { await vm.refreshUnread() } })
                    HeaderEscuelas(count: vm.loading ? nil : vm.schools.count)
                    SearchField(text: $vm.query)
                    MapToggleAndPanel(vm: vm, onOpen: { navSchool = $0 })
                    FilterChips(vm: vm)
                    DaySelectorRow(vm: vm)
                    Divider().overlay(Cumbre.rule)

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
            .navigationDestination(item: $navSchool) { SchoolDetailView(school: $0) }
            .overlay(alignment: .bottom) {
                if vm.compareSelection.count >= 1 {
                    CompareBar(count: vm.compareSelection.count,
                               canCompare: vm.compareSelection.count >= 2,
                               onClear: { vm.clearCompare() },
                               onCompare: { showCompare = true })
                }
            }
            .sheet(isPresented: $showCompare) {
                CompareView(schools: vm.filtered.filter { vm.compareSelection.contains($0.id) })
            }
            .task { await vm.load() }
            .refreshable { await vm.refresh() }
        }
    }

    @State private var showCompare = false
    @State private var navSchool: School?
}

// School (clase Kotlin) Identifiable por su id — para navigationDestination(item:).
extension School: Identifiable {}

private struct CompareBar: View {
    let count: Int
    let canCompare: Bool
    let onClear: () -> Void
    let onCompare: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Button(action: onClear) {
                Image(systemName: "xmark").foregroundStyle(.white).frame(width: 32, height: 32)
            }
            Text("\(count) SELECCIONADA\(count == 1 ? "" : "S")")
                .font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(.white)
            Spacer()
            Button(action: onCompare) {
                Text("COMPARAR ▸").font(Cumbre.mono(12, .bold)).tracking(0.8)
                    .foregroundStyle(canCompare ? .white : .white.opacity(0.4))
            }
            .disabled(!canCompare)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background(Cumbre.ink)
        .padding(.horizontal, 12).padding(.bottom, 8)
    }
}

// MARK: - Header

private struct TopIconsRow: View {
    var unreadCount: Int = 0
    var chatUnread: Int = 0
    var onNotificationsClosed: () -> Void = {}
    @State private var showAccount = false
    @State private var showNotifications = false
    @State private var showSearch = false
    @State private var showChats = false
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        HStack(spacing: 4) {
            Spacer()
            HelpButton(topicKey: "schools")
            iconButton("magnifyingglass") { showSearch = true }
            chatButton
            iconButton(theme.iconName) { theme.cycle() }
            bellButton
            iconButton("person") { showAccount = true }
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
        .sheet(isPresented: $showAccount) { AccountView() }
        .sheet(isPresented: $showNotifications, onDismiss: onNotificationsClosed) { NotificationsView() }
        .sheet(isPresented: $showSearch) { SearchUsersView() }
        .sheet(isPresented: $showChats) { NavigationStack { ChatListView() } }
    }

    private func iconButton(_ name: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 18))
                .foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // Icono de mensajes con badge de chats sin leer (número o "9+").
    private var chatButton: some View {
        Button { showChats = true } label: {
            Image(systemName: "bubble.left")
                .font(.system(size: 18)).foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40).contentShape(Rectangle())
                .overlay(alignment: .topTrailing) {
                    if chatUnread > 0 {
                        Text(chatUnread > 9 ? "9+" : "\(chatUnread)")
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Capsule().fill(Cumbre.bad))
                            .offset(x: -4, y: 4)
                    }
                }
        }
        .buttonStyle(.plain)
    }

    // Campana con badge rojo de no leídas (número o "9+").
    private var bellButton: some View {
        Button { showNotifications = true } label: {
            Image(systemName: "bell")
                .font(.system(size: 18)).foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40).contentShape(Rectangle())
                .overlay(alignment: .topTrailing) {
                    if unreadCount > 0 {
                        Text(unreadCount > 9 ? "9+" : "\(unreadCount)")
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Capsule().fill(Cumbre.bad))
                            .offset(x: -4, y: 4)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

private struct HeaderEscuelas: View {
    let count: Int?
    @State private var showSubmit = false
    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Escuelas")
                    .font(Cumbre.serif(34, .bold))
                    .foregroundStyle(Cumbre.ink)
                if let count {
                    Text("\(count) escuelas")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            Button { showSubmit = true } label: {
                OutlinedCumbreButton(text: "+ Enviar escuela", tint: Cumbre.terra)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showSubmit) { SubmitSchoolView() }
    }
}

private struct CoffeeBanner: View {
    @State private var showDonate = false
    var body: some View {
        HStack(spacing: 8) {
            Text("☕").font(.system(size: 30))
            VStack(alignment: .leading, spacing: 1) {
                Text("¿Te ayuda la app?")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Text("Mantenida con amor por la comunidad escaladora")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2.opacity(0.8))
            }
            Spacer()
            Button { showDonate = true } label: { OutlinedCumbreButton(text: "Apóyanos", tint: Cumbre.ink) }
                .buttonStyle(.plain)
        }
        .padding(12)
        .background(Cumbre.terraBg)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showDonate) { DonateView() }
    }
}

/// Diálogo "Apóyanos" — espejo del DonateDialog de Android.
private struct DonateView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    var body: some View {
        VStack(spacing: 16) {
            Text("☕").font(.system(size: 56)).padding(.top, 24)
            Text("¿Te ayuda la app?").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text("MeteoMontana es gratis y sin anuncios, mantenida por la comunidad escaladora. Si te resulta útil, invítame a un café.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            VStack(alignment: .leading, spacing: 6) {
                feature("Previsión de escalada por hora")
                feature("Mapas, bloques y vías de cada escuela")
                feature("Notas y fotos de la comunidad")
                feature("Sin anuncios, sin rastreadores")
            }.padding(.horizontal, 24).padding(.top, 4)
            Button {
                openURL(URL(string: "https://ko-fi.com/climbingteams")!)
            } label: {
                Text("☕ INVÍTAME A UN CAFÉ").font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                    .background(Cumbre.terra)
            }
            .buttonStyle(.plain).padding(.horizontal, 24).padding(.top, 8)
            Button("Ahora no") { dismiss() }.foregroundStyle(Cumbre.ink3).padding(.top, 4)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }
    private func feature(_ t: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Cumbre.ok).font(.system(size: 14))
            Text(t).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
        }
    }
}

private struct SearchField: View {
    @Binding var text: String
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
            TextField("Buscar escuela…", text: $text)
                .foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

/// Toggle "VER MAPA" + panel con todas las escuelas filtradas como marcadores
/// coloreados por score (tap → detalle). Espejo de SchoolsMapPanel.kt.
private struct MapToggleAndPanel: View {
    @ObservedObject var vm: SchoolListViewModel
    let onOpen: (School) -> Void
    @State private var show = false
    @State private var popup: School?
    @State private var mapStyle: MapStyleKind = .topo
    @State private var zoom: Double = 8

    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation { show.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "map").font(.system(size: 13))
                    Text(show ? "OCULTAR MAPA" : "VER MAPA").font(Cumbre.mono(11, .bold)).tracking(0.8)
                    Spacer()
                    Image(systemName: show ? "chevron.up" : "chevron.down").font(.system(size: 11))
                }
                .foregroundStyle(Cumbre.ink2)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if show {
                ZStack(alignment: .topLeading) {
                    MapLibreView(center: center, zoom: vm.userLat != nil ? 8 : 6,
                                 markers: markers, style: mapStyle,
                                 autoFitToMarkers: true,
                                 onZoomChange: { zoom = $0 },
                                 onTapMarker: { id in
                                     popup = vm.filtered.first { $0.id == id }
                                 })
                    .frame(height: 300)
                    MapStyleChips(selection: $mapStyle)
                }
                Divider().overlay(Cumbre.rule)
            }
        }
        // Popup al tocar un marcador: nombre, score, tags, CÓMO LLEGAR + VER DETALLE
        // (espejo de SchoolsMapPanel.kt).
        .sheet(item: $popup) { s in
            SchoolMapPopup(school: s, score: vm.scores[s.id].map { Int($0.todayScore) }) {
                popup = nil; onOpen(s)
            }
            .presentationDetents([.height(280)])
        }
    }

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        // Punto azul de mi ubicación (confirma que se cogió la ubicación).
        if let la = vm.userLat, let lo = vm.userLon {
            ms.append(CumbreMarker(
                id: "__USER__",
                coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                title: "", kind: .user))
        }
        for s in vm.filtered.prefix(200) {
            let score = vm.scores[s.id].map { Int($0.todayScore) }
            ms.append(CumbreMarker(
                id: s.id,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                title: s.name,
                subtitle: score.map { "\($0)/100" },
                kind: .score,
                color: UIColor(score.map { Cumbre.score($0) } ?? Cumbre.rule),
                score: score,
                name: s.name,
                showName: zoom >= 8.5))
        }
        return ms
    }

    private var center: CLLocationCoordinate2D {
        if let la = vm.userLat, let lo = vm.userLon {
            return CLLocationCoordinate2D(latitude: la, longitude: lo)
        }
        let pts = vm.filtered
        if pts.isEmpty { return CLLocationCoordinate2D(latitude: 40.2, longitude: -3.7) }
        let lat = pts.map { $0.lat }.reduce(0, +) / Double(pts.count)
        let lon = pts.map { $0.lon }.reduce(0, +) / Double(pts.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

/// Popup de una escuela al tocar su marcador en el panel de mapa de la lista.
/// Nombre + score + tags + "CÓMO LLEGAR" y "VER DETALLE" (espejo de SchoolsMapPanel).
private struct SchoolMapPopup: View {
    let school: School
    let score: Int?
    let onDetail: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                if let s = score {
                    VStack(spacing: 0) {
                        Text("\(s)").font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.score(s))
                        Text(Cumbre.scoreLabel(s)).font(.system(size: 8, weight: .bold)).foregroundStyle(Cumbre.score(s))
                    }
                    .frame(width: 60, height: 60)
                    .background(Cumbre.score(s).opacity(0.12))
                    .overlay(Rectangle().stroke(Cumbre.score(s), lineWidth: 1.5))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(school.name).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                    Text(tags).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            HStack(spacing: 10) {
                DirectionsButton(lat: school.lat, lon: school.lon, label: school.name)
                Button(action: onDetail) {
                    Text("VER DETALLE ▸").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private var tags: String {
        [school.rockType?.uppercased(), school.region, school.style]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
    }
}

/// Barra de filtros — réplica de SchoolFiltersBar.kt: secciones apiladas
/// (DISTANCIA, ESTILO, TIPO DE ROCA, FAVORITOS, ORDENAR POR), cada una con su
/// eyebrow y una fila horizontal de chips seleccionables.
private struct FilterChips: View {
    @ObservedObject var vm: SchoolListViewModel
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            section("DISTANCIA") {
                chipRow(SchoolListViewModel.distanceOptions, id: { $0.map { String(Int($0)) } ?? "all" },
                        isSel: { $0 == vm.maxDistanceKm },
                        label: { $0 == nil ? "Todas" : "\(Int($0!)) km" }) { vm.maxDistanceKm = $0 }
            }
            section("ESTILO") {
                chipRow([String?.none] + vm.styles.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.style },
                        label: { $0 ?? "Todas" }) { vm.style = $0 }
            }
            section("TIPO DE ROCA") {
                chipRow([String?.none] + vm.rocks.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.rock },
                        label: { $0 ?? "Todas" }) { vm.rock = $0 }
            }
            section("MOSTRAR") {
                chipRow(SchoolListViewModel.ShowMode.allCases, id: { $0.rawValue },
                        isSel: { $0 == vm.showMode },
                        label: { $0.rawValue }) { vm.showMode = $0 }
            }
            section("ORDENAR POR") {
                chipRow(SchoolListViewModel.SortMode.allCases, id: { $0.rawValue },
                        isSel: { $0 == vm.sortBy },
                        label: { $0.rawValue }) { vm.sortBy = $0 }
            }
        }
        .padding(.vertical, 8)
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).eyebrow().padding(.horizontal, 12)
            content()
        }
    }

    private func chipRow<T>(_ items: [T], id: @escaping (T) -> String,
                            isSel: @escaping (T) -> Bool, label: @escaping (T) -> String,
                            onPick: @escaping (T) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button { onPick(item) } label: { chip(label(item), active: isSel(item)) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func chip(_ t: String, active: Bool) -> some View {
        Text(t)
            .font(Cumbre.mono(11, .bold))
            .tracking(0.8)
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? Cumbre.terra : Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Selector de días: próximos 7 días (hoy incluido) como chips "LUN 17". Toca
/// para elegir hasta 5; con ≥1 elegido la lista pasa a modo tramo. Espejo de
/// DaySelectorRow de Android.
private struct DaySelectorRow: View {
    @ObservedObject var vm: SchoolListViewModel

    private static let isoFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX"); return f
    }()
    private let dayLetters = ["DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"]  // weekday 1=domingo

    private var next7: [Date] {
        let cal = Calendar(identifier: .gregorian)
        let today = cal.startOfDay(for: Date())
        return (0..<7).compactMap { cal.date(byAdding: .day, value: $0, to: today) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(vm.selectedDates.isEmpty ? "DÍAS · elige hasta 5 para comparar el tramo"
                 : "DÍAS · \(vm.selectedDates.count) elegido\(vm.selectedDates.count > 1 ? "s" : "")")
                .eyebrow().padding(.horizontal, 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(next7, id: \.self) { d in
                        let iso = Self.isoFmt.string(from: d)
                        let cal = Calendar(identifier: .gregorian)
                        let weekday = cal.component(.weekday, from: d)
                        let dayNum = cal.component(.day, from: d)
                        let selected = vm.selectedDates.contains(iso)
                        Button { vm.toggleDate(iso) } label: {
                            VStack(spacing: 1) {
                                Text(dayLetters[weekday - 1]).font(Cumbre.mono(11, .bold)).tracking(0.6)
                                    .foregroundStyle(selected ? .white : Cumbre.ink2)
                                Text("\(dayNum)").font(.system(size: 10))
                                    .foregroundStyle(selected ? .white.opacity(0.85) : Cumbre.ink3)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(selected ? Cumbre.terra : Cumbre.paper)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
            }
        }
        .padding(.vertical, 8)
    }
}

/// Fila por día del tramo: una celda por día con su score (color) y la inicial
/// del día debajo. Los días con lluvia llevan la inicial y el borde en rojo.
private struct DayRangeRow: View {
    let range: RangeScore
    var body: some View {
        HStack(spacing: 3) {
            ForEach(Array(range.days.enumerated()), id: \.offset) { _, d in
                let score = Int(d.score)
                VStack(spacing: 2) {
                    Text("\(score)")
                        .font(Cumbre.mono(11, .bold))
                        .foregroundStyle(Cumbre.score(score))
                        .frame(width: 26, height: 22)
                        .background(Cumbre.score(score).opacity(0.18))
                        .overlay(Rectangle().stroke(d.rainy ? Cumbre.bad : Cumbre.score(score), lineWidth: 1))
                    Text(weekdayLetter(d.date))
                        .font(.system(size: 9))
                        .foregroundStyle(d.rainy ? Cumbre.bad : Cumbre.ink3)
                }
            }
        }
    }
}

/// Resumen de lluvia del tramo: qué días llueve (iniciales) + máximo de mm.
private struct RainSummaryTag: View {
    let range: RangeScore
    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if range.rainDays == 0 {
                Text("● SIN LLUVIA").font(.system(size: 11, weight: .semibold)).tracking(0.8)
                    .foregroundStyle(Cumbre.ok)
            } else {
                let rainy = range.days.filter { $0.rainy }.map { weekdayLetter($0.date) }.joined(separator: " ")
                Text("LLUEVE \(rainy)").font(.system(size: 11, weight: .semibold)).tracking(0.8)
                    .foregroundStyle(Cumbre.bad)
                if range.maxRainMm > 0 {
                    Text(String(format: "máx %.1f mm", range.maxRainMm))
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
    }
}

/// "2026-06-17" → "L"/"M"/"X"/"J"/"V"/"S"/"D".
private func weekdayLetter(_ iso: String) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX")
    guard let d = f.date(from: iso) else { return String(iso.suffix(2)) }
    let weekday = Calendar(identifier: .gregorian).component(.weekday, from: d) // 1=domingo
    let labels = ["D", "L", "M", "X", "J", "V", "S"]
    return labels[weekday - 1]
}

private struct OutlinedCumbreButton: View {
    let text: String
    var tint: Color = Cumbre.ink
    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .overlay(Rectangle().stroke(Cumbre.ink, lineWidth: 1))
    }
}

// MARK: - Fila rica (réplica de SchoolListItem.kt)

private struct SchoolListItemView: View {
    let rank: Int
    let school: School
    let score: SchoolScore?
    var range: RangeScore? = nil
    var distanceKm: Int? = nil
    var isFavorite: Bool = false
    var isSelected: Bool = false
    var onToggleFavorite: () -> Void = {}

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            // Modo tramo → el badge muestra el score combinado de los días.
            ScoreBadge(score: range.map { Int($0.combinedScore) } ?? score.map { Int($0.todayScore) })
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 0) {
                    Text(String(format: "%02d", rank))
                        .font(Cumbre.mono(11, .semibold))
                        .tracking(1.4)
                        .foregroundStyle(Cumbre.ink3)
                        .frame(width: 24, alignment: .leading)
                    Text(school.name)
                        .font(Cumbre.serif(19, .bold))
                        .foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    // Estrella tocable con update optimista. BorderlessButtonStyle
                    // para que reciba el tap sin disparar la navegación de la fila.
                    Button(action: onToggleFavorite) {
                        Image(systemName: isFavorite ? "star.fill" : "star")
                            .font(.system(size: 18))
                            .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink3)
                            .frame(width: 36, height: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)
                }
                Text(subtitle)
                    .font(Cumbre.mono(12))
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.top, 4)
                HStack(alignment: .center, spacing: 8) {
                    if let range {
                        DayRangeRow(range: range)
                        Spacer(minLength: 4)
                        RainSummaryTag(range: range)
                    } else {
                        HeatmapBar(scores: score?.hourlyScores.map { $0.intValue })
                        DryWetTag(dry: score?.dryRock, rainProb: score.map { Int($0.rainProb) }, rainMm: score?.rainMm)
                    }
                }
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
        .overlay(isSelected ? Rectangle().stroke(Cumbre.terra, lineWidth: 2) : nil)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        var parts: [String] = []
        if let r = school.rockType, !r.isEmpty { parts.append(r.uppercased()) }
        if let reg = school.region, !reg.isEmpty { parts.append(reg) }
        if let km = distanceKm { parts.append("\(km) KM") }
        return parts.joined(separator: "  ·  ")
    }
}

private struct ScoreBadge: View {
    let score: Int?
    var body: some View {
        let color = score.map { Cumbre.score($0) } ?? Cumbre.rule
        VStack(spacing: 1) {
            Text(score.map(String.init) ?? "—")
                .font(Cumbre.serif(28, .bold))
                .foregroundStyle(score != nil ? color : Cumbre.ink2)
            Text(Cumbre.scoreLabel(score))
                .font(.system(size: 8, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(score != nil ? color : Cumbre.ink3)
        }
        .frame(width: 64, height: 72)
        .background((score != nil ? color : Cumbre.paper).opacity(score != nil ? 0.12 : 1))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(color, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

private struct HeatmapBar: View {
    let scores: [Int]?
    var body: some View {
        let cells: [Int?] = {
            if let s = scores, !s.isEmpty { return Array(s.prefix(10)).map { Optional($0) } }
            return Array(repeating: nil, count: 10)
        }()
        HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, s in
                Rectangle().fill(s.map { Cumbre.score($0) } ?? Cumbre.rule.opacity(0.35))
            }
        }
        .frame(height: 16)
        .frame(maxWidth: .infinity)
    }
}

private struct DryWetTag: View {
    let dry: Bool?
    let rainProb: Int?
    let rainMm: Double?
    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if let dry {
                Text(dry ? "● SECA" : "● MOJADA")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.8)
                    .foregroundStyle(dry ? Cumbre.ok : Cumbre.bad)
            }
            if dry == false, let p = rainProb, p > 0 {
                Text("\(p)%").font(.system(size: 10)).foregroundStyle(Cumbre.bad)
            }
        }
    }
}

// MARK: - Estados

private struct SkeletonRow: View {
    var body: some View {
        let tone = Cumbre.ink3.opacity(0.12)
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 64, height: 72)
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 160, height: 16)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 110, height: 12)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(height: 14)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
    }
}

private struct EmptyRow: View {
    let canClear: Bool
    let onClear: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("No hay escuelas con esos filtros")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            if canClear {
                Button(action: onClear) { OutlinedCumbreButton(text: "QUITAR FILTROS") }
            }
        }
        .frame(maxWidth: .infinity).padding(32)
    }
}

private struct ErrorRow: View {
    let message: String
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("Error: \(message)").font(.system(size: 15)).foregroundStyle(Cumbre.bad)
            Button(action: onRetry) { OutlinedCumbreButton(text: "REINTENTAR") }
        }
        .frame(maxWidth: .infinity).padding(40)
    }
}

#Preview { SchoolListView() }
