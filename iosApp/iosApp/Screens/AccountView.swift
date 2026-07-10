import SwiftUI
import Shared

@MainActor
final class AccountViewModel: ObservableObject {
    @Published var profile: PrivateProfile?
    @Published var stats: JournalStats?
    @Published var entries: [JournalSession] = []
    @Published var viaInfo: [String: ViaCatalogInfo] = [:]
    @Published var follow: FollowStatus?
    @Published var loading = true
    /// true cuando los datos vienen de la caché local (sin conexión).
    @Published var offline = false
    /// Propuestas + contribuciones pendientes de revisar (solo admin).
    @Published var pendingReview = 0

    private let getPendingSubmissions = AppDependencies.shared.container.getPendingSubmissions
    private let getPendingContributions = AppDependencies.shared.container.getPendingContributions

    private let deleteAccount: DeleteMyAccountUseCase = AppDependencies.shared.container.deleteMyAccount
    private let getMyProfile: GetMyProfileUseCase
    private let getMyStats: GetMyJournalStatsUseCase
    private let getMyJournal: GetMyJournalUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    private let createEntry: CreateJournalEntryUseCase
    private let deleteEntry: DeleteJournalEntryUseCase
    private let getJournalViaInfo: GetJournalViaInfoUseCase
    private let authBridge = AppDependencies.shared.authBridge

    init(
        getMyProfile: GetMyProfileUseCase = AppDependencies.shared.container.getMyProfile,
        getMyStats: GetMyJournalStatsUseCase = AppDependencies.shared.container.getMyJournalStats,
        getMyJournal: GetMyJournalUseCase = AppDependencies.shared.container.getMyJournal,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus,
        createEntry: CreateJournalEntryUseCase = AppDependencies.shared.container.createJournalEntry,
        deleteEntry: DeleteJournalEntryUseCase = AppDependencies.shared.container.deleteJournalEntry,
        getJournalViaInfo: GetJournalViaInfoUseCase = AppDependencies.shared.container.getJournalViaInfo
    ) {
        self.getMyProfile = getMyProfile
        self.getMyStats = getMyStats
        self.getMyJournal = getMyJournal
        self.getFollowStatus = getFollowStatus
        self.createEntry = createEntry
        self.deleteEntry = deleteEntry
        self.getJournalViaInfo = getJournalViaInfo
    }

    /// Resuelve nº de piedra + sector de las vías actuales (catálogo en vivo).
    private func refreshViaInfo() async {
        viaInfo = (try? await getJournalViaInfo.invoke(entries: entries)) ?? [:]
    }

    /// Recarga solo el diario (tras añadir/borrar un bloque). Si no hay red,
    /// deja la lista/stats como estaban (no las vacía con un fetch fallido).
    func reloadJournal() async {
        guard let fresh = await loadEntries() else { return }
        entries = fresh
        await refreshViaInfo()
        stats = (try? await getMyStats.invoke()) ?? stats
        if let p = profile {
            ProfileCache.shared.save(profile: p, stats: stats, entries: entries,
                                     followers: follow?.followers ?? 0, following: follow?.following ?? 0)
        }
    }

    /// Diario del servidor menos las entradas con BORRADO pendiente en la cola
    /// offline (por clave "escuela|vía" del tic, y por uid de las borradas en el
    /// perfil). Devuelve nil si no hay red (para no vaciar la lista cacheada).
    private func loadEntries() async -> [JournalSession]? {
        guard let all = try? await getMyJournal.invoke() else { return nil }
        let filtered = await filterPendingDeletes(all)
        // Solo HECHO: los PROYECTOS tienen su propia pantalla (ProjectsView), no
        // se mezclan aquí (Bloques/Vías/Escuelas del perfil).
        return filtered.filter { $0.status != "PROJECT" }
    }

    /// Quita de [list] las entradas con borrado pendiente (clave o uid) y
    /// colapsa duplicados (misma escuela|sector|vía → una sola, la más reciente).
    private func filterPendingDeletes(_ list: [JournalSession]) async -> [JournalSession] {
        let c = AppDependencies.shared.container
        let keys = (try? await c.pendingJournalDeleteKeys()) ?? []
        let ids = (try? await c.pendingJournalDeleteIds()) ?? []
        var seen = Set<String>()
        return list.filter { e in
            if ids.contains(e.id) { return false }
            let key = "\(e.schoolId ?? "")|\(e.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
            if keys.contains(key) { return false }
            let dedupKey = "\(e.schoolId ?? "")|\((e.sector ?? "").trimmingCharacters(in: .whitespaces).lowercased())|\(e.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
            return seen.insert(dedupKey).inserted
        }
    }

    func addBlock(blockName: String, grade: String, schoolId: String?, schoolName: String, sector: String, notes: String) async {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let req = CreateJournalRequest(
            schoolId: schoolId, schoolName: schoolName.nilIfBlank, sector: sector.nilIfBlank,
            blockName: blockName.trimmingCharacters(in: .whitespaces), grade: grade.nilIfBlank,
            notes: notes.nilIfBlank, date: df.string(from: Date()), discipline: nil, lineId: nil, status: nil)
        _ = try? await createEntry.invoke(req: req)
        await reloadJournal()
    }

    /// Borra la cuenta (datos + Firebase Auth) y cierra sesión. El gate de login
    /// (RootView) vuelve a la pantalla de acceso al desaparecer la sesión.
    func deleteMyAccount() async {
        _ = try? await deleteAccount.invoke()
        authBridge.signOut {}
    }

    func deleteBlock(_ id: String) {
        entries.removeAll { $0.id == id }   // optimista
        // Refresca la caché ya sin el bloque → offline no reaparece al reabrir.
        if let p = profile {
            ProfileCache.shared.save(profile: p, stats: stats, entries: entries,
                                     followers: follow?.followers ?? 0, following: follow?.following ?? 0)
        }
        Task {
            do {
                try await deleteEntry.invoke(id: id)   // con red: borra ya
                await reloadJournal()
            } catch {
                // Sin red: encola el borrado por uid; se aplica al volver internet.
                // El flusher (flushJournalOutbox) lo resuelve por id exacto.
                try? await AppDependencies.shared.container.enqueueJournalDeleteById(id: id)
            }
        }
    }

    func load() async {
        loading = true
        if let p = try? await getMyProfile.invoke() {   // JIT provisioning en el backend
            profile = p
            stats = try? await getMyStats.invoke()
            entries = (await loadEntries()) ?? []
            await refreshViaInfo()
            // Contadores de seguidores/seguidos: igual que Android (getFollowStatus
            // del propio uid devuelve followers/following).
            follow = try? await getFollowStatus.invoke(uid: p.uid)
            // Si soy admin, cuento propuestas/contribuciones pendientes para
            // marcar el acceso al panel con un aviso.
            if p.isAdmin {
                let subs = (try? await getPendingSubmissions.invoke())?.count ?? 0
                let contribs = (try? await getPendingContributions.invoke())?.count ?? 0
                pendingReview = subs + contribs
            } else {
                pendingReview = 0
            }
            offline = false
            // Cachea para poder ver el perfil SIN conexión la próxima vez.
            ProfileCache.shared.save(profile: p, stats: stats, entries: entries,
                                     followers: follow?.followers ?? 0, following: follow?.following ?? 0)
        } else if let cached = ProfileCache.shared.load() {
            // Sin conexión: última copia cacheada en vez de pantalla vacía,
            // descontando lo que ya borraste offline (aún sin sincronizar).
            profile = cached.profile
            stats = cached.stats
            entries = (await filterPendingDeletes(cached.entries)).filter { $0.status != "PROJECT" }
            await refreshViaInfo()   // sin red devuelve vacío → solo escuela
            follow = FollowStatus(followers: cached.followers, following: cached.following,
                                  iFollowThem: false, theyFollowMe: false, requestPending: false)
            offline = true
        }
        loading = false
    }
}

/// Pantalla de cuenta/perfil — se abre desde el icono de persona del header.
/// Muestra el perfil privado (avatar, nombre, usuario, bio, grado, badges) y
/// permite cerrar sesión. Espejo parcial de ProfileScreen.kt; el diario,
/// seguidores y edición de foto llegarán con sus bridges.
struct AccountView: View {
    /// false cuando Perfil vive como pestaña (no hay sheet que cerrar).
    var showClose: Bool = true
    @StateObject private var vm = AccountViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var showAddBlock = false
    @State private var showDeleteConfirm = false
    @State private var showHintsReset = false

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if vm.offline {
                        Text(NSLocalizedString("common_offline_data", comment: ""))
                            .font(Cumbre.mono(10, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.ink2)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(Cumbre.terraBg)
                    }
                    avatar
                    names
                    if let p = vm.profile, let bio = p.bio, !bio.isEmpty {
                        Text(bio)
                            .font(.system(size: 15))
                            .foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    badges
                    followCounters
                    diarySection
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    menuLinks
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    signOutButton
                    deleteAccountButton
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 24)
                .padding(.horizontal, 24)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(NSLocalizedString("profile_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { HelpButton(topicKey: "profile") }
                ToolbarItem(placement: .topBarTrailing) {
                    if showClose {
                        Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                    }
                }
            }
            .alert("¿Eliminar tu cuenta?", isPresented: $showDeleteConfirm) {
                Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
                Button("Eliminar", role: .destructive) {
                    Task { await vm.deleteMyAccount(); dismiss() }
                }
            } message: {
                Text("Se borrarán tu perfil, diario, favoritas, seguimientos y propuestas de forma permanente. Esta acción no se puede deshacer.")
            }
            .sheet(isPresented: $showAddBlock) {
                AddBlockSheet { block, grade, schoolId, school, sector, notes in
                    Task { await vm.addBlock(blockName: block, grade: grade, schoolId: schoolId,
                                             schoolName: school, sector: sector, notes: notes) }
                }
            }
            .task { await vm.load() }
        }
    }

    /// Diario: stats PULSABLES (BLOQUES/ESCUELAS abren su lista) + AÑADIR BLOQUE.
    /// El listado completo ya no se vuelca aquí: se ve entrando en BLOQUES o
    /// ESCUELAS, y desde dentro se puede borrar.
    @ViewBuilder private var diarySection: some View {
        AccountJournalStatsNav(vm: vm)
        Button { showAddBlock = true } label: {
            // Terracota: Cumbre.ink se invierte a crema en oscuro (deslumbraba).
            Text(NSLocalizedString("profile_add_block", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                .background(Cumbre.terra)
        }.buttonStyle(.plain).padding(.top, 4)
    }

    private var avatar: some View {
        // Si el backend no tiene foto, usa la de la cuenta de Google (Firebase Auth).
        // AvatarCircle cachea en disco → se ve offline (igual que el resto de avatares).
        let photo = vm.profile?.photoUrl?.isEmpty == false ? vm.profile?.photoUrl : authBridge.currentPhotoUrl()
        return AvatarCircle(url: photo, size: 88)
    }

    private var names: some View {
        VStack(spacing: 2) {
            Text(displayName).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            if let u = vm.profile?.username, !u.isEmpty {
                Text("@\(u)").font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink3)
            }
            if let e = email { Text(e).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
        }
    }

    private var badges: some View {
        HStack(spacing: 8) {
            // TOPE = grado máximo REAL del diario (no el topGrade editable del
            // perfil). Espejo de la stat "MÁXIMO" de Android.
            if let g = vm.stats?.maxGrade, !g.isEmpty { badge("TOPE \(g.uppercased())", Cumbre.terra) }
            if vm.profile?.isAdmin == true { badge("ADMIN", Cumbre.ink) }
            if vm.profile?.isPremium == true { badge("PREMIUM", Cumbre.ok) }
        }
    }

    /// Seguidores / seguidos tappables (→ FollowListView del propio uid).
    @ViewBuilder private var followCounters: some View {
        if let f = vm.follow, let uid = vm.profile?.uid {
            HStack(spacing: 24) {
                NavigationLink(destination: FollowListView(uid: uid, mode: .followers)) {
                    counter("\(f.followers)", "SEGUIDORES")
                }.buttonStyle(.plain)
                NavigationLink(destination: FollowListView(uid: uid, mode: .following)) {
                    counter("\(f.following)", "SIGUIENDO")
                }.buttonStyle(.plain)
            }
        }
    }

    private func counter(_ v: String, _ l: String) -> some View {
        VStack(spacing: 2) {
            Text(v).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
    }

    private func badge(_ t: String, _ c: Color) -> some View {
        Text(t).font(Cumbre.mono(10, .bold)).tracking(1.0)
            .foregroundStyle(c)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .overlay(Rectangle().stroke(c, lineWidth: 1))
    }

    private var menuLinks: some View {
        VStack(spacing: 0) {
            // Compartir mi perfil: imagen 1080×1920 (formato historia) →
            // Instagram Stories, WhatsApp... + enlace /s/u/ (paridad Android).
            if let p = vm.profile {
                let handle = (p.username?.isEmpty == false ? p.username! : p.uid)
                let label = p.username.map { "@" + $0 } ?? (p.displayName ?? "mi perfil")
                Button {
                    Task {
                        let st = vm.stats
                        await ShareProfileImage.share(
                            handle: handle, displayLabel: label,
                            username: p.username, photoUrl: p.photoUrl,
                            topGrade: p.topGrade, bio: p.bio,
                            boulders: st.map { Int($0.boulderCount) },
                            routes: st.map { Int($0.routeCount) },
                            schools: st.map { Int($0.schoolCount) })
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "square.and.arrow.up").font(.system(size: 16))
                            .foregroundStyle(Cumbre.terra).frame(width: 24)
                        Text("Compartir mi perfil").font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12))
                            .foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.vertical, 13).padding(.horizontal, 16)
                }
                Divider().overlay(Cumbre.rule)
            }
            menuRow("Comunidad", "trophy", CommunityView())
            menuRow(NSLocalizedString("profile_edit", comment: ""), "pencil", EditProfileView())
            menuRow("Escuelas guardadas (offline)", "arrow.down.circle", SavedSchoolsView())
            menuRow(NSLocalizedString("profile_weather_alert", comment: ""), "bell.badge", WeekendAlertView())
            menuRow(NSLocalizedString("profile_my_proposals", comment: ""), "mappin.and.ellipse", MySubmissionsView())
            menuRow("Mis contribuciones", "square.and.pencil", MyContributionsView())
            menuRow("Solicitudes de seguimiento", "person.badge.plus", FollowRequestsView())
            // Panel de admin: solo si el perfil es admin. Muestra un aviso con el
            // nº de propuestas/contribuciones pendientes de revisar.
            if vm.profile?.isAdmin == true {
                NavigationLink(destination: AdminView()) {
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.shield").font(.system(size: 16))
                            .foregroundStyle(Cumbre.terra).frame(width: 24)
                        Text("Panel de admin").font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        if vm.pendingReview > 0 {
                            Text("\(vm.pendingReview) PENDIENTE\(vm.pendingReview == 1 ? "" : "S")")
                                .font(Cumbre.mono(10, .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(Cumbre.terra).clipShape(Capsule())
                        }
                        Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            Button {
                FirstTimeHint.resetAll()
                showHintsReset = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "questionmark.circle").font(.system(size: 16))
                        .foregroundStyle(Cumbre.terra).frame(width: 24)
                    Text(NSLocalizedString("profile_show_hints", comment: "")).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    Spacer()
                }
                .padding(.vertical, 12).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .alert("Pistas reactivadas", isPresented: $showHintsReset) {
                Button("Vale", role: .cancel) {}
            } message: {
                Text("Entra de nuevo en cada pantalla para volver a ver las pistas.")
            }
        }
    }

    private func menuRow<Dest: View>(_ title: String, _ icon: String, _ dest: Dest) -> some View {
        NavigationLink(destination: dest) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 16)).foregroundStyle(Cumbre.terra).frame(width: 24)
                Text(title).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var signOutButton: some View {
        Button {
            authBridge.signOut {}
            dismiss()
        } label: {
            Text(NSLocalizedString("profile_logout", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).frame(maxWidth: .infinity)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var deleteAccountButton: some View {
        Button { showDeleteConfirm = true } label: {
            Text(NSLocalizedString("profile_delete_account", comment: ""))
                .font(.system(size: 14))
                .foregroundStyle(Cumbre.ink3)
                .padding(.vertical, 8).frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    private var displayName: String {
        vm.profile?.displayName ?? authBridge.currentDisplayName() ?? "Sin nombre"
    }
    private var email: String? {
        vm.profile?.email ?? authBridge.currentEmail()
    }
}

// MARK: - Diario propio: stats pulsables + listas con borrar (refresco en vivo)

/// Fila de stats del diario propio. BLOQUES y ESCUELAS son navegables; GRADO MÁX
/// es solo informativo. Observa el `AccountViewModel` para reflejar borrados.
private struct AccountJournalStatsNav: View {
    @ObservedObject var vm: AccountViewModel
    var body: some View {
        if let s = vm.stats {
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    NavigationLink(destination: AccountBlocksList(vm: vm, routeOnly: false)) {
                        cell("\(s.boulderCount)", "BLOQUES")
                    }.buttonStyle(.plain)
                    NavigationLink(destination: AccountBlocksList(vm: vm, routeOnly: true)) {
                        cell("\(s.routeCount)", "VÍAS")
                    }.buttonStyle(.plain)
                    NavigationLink(destination: AccountSchoolsList(vm: vm)) {
                        cell("\(s.schoolCount)", "ESCUELAS")
                    }.buttonStyle(.plain)
                }
                HStack(spacing: 8) {
                    cell(s.maxBoulderGrade ?? "—", "MÁX BLOQUE")
                    cell(s.maxRouteGrade ?? "—", "MÁX VÍA")
                }
                // Proyectos: misma celda pulsable que el resto, mismo caché
                // offline (viene en la misma llamada de stats).
                NavigationLink(destination: ProjectsView()) {
                    cell("\(s.projectCount)", "PROYECTOS")
                }.buttonStyle(.plain)
            }
        }
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(v).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Todos los bloques del diario propio, con borrar. Observa el vm → al borrar la
/// lista se refresca sola.
private struct AccountBlocksList: View {
    @ObservedObject var vm: AccountViewModel
    /// nil = todos · false = solo bloques (BOULDER) · true = solo vías (ROUTE).
    /// Las entradas viejas sin modalidad cuentan como BOULDER (igual que stats).
    var routeOnly: Bool? = nil
    private var entries: [JournalSession] {
        guard let r = routeOnly else { return vm.entries }
        return vm.entries.filter { (($0.discipline).uppercased() == "ROUTE") == r }
    }
    var body: some View {
        Group {
            if entries.isEmpty {
                EmptyStateView(
                    icon: "book",
                    title: routeOnly == true ? "Aún no has registrado vías" : "Aún no has registrado bloques",
                    message: "Marca el ✓ de una vía dentro de su piedra (en el detalle de una escuela) y aparecerá aquí."
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        FirstTimeHint(
                            hintKey: "journal_tap_via",
                            text: "Toca una vía para ir directamente a su piedra en la escuela."
                        )
                        ForEach(entries, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id]) { vm.deleteBlock(e.id) }
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(routeOnly == true ? "Vías" : "Bloques")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Desglose por escuela del diario propio. Cada escuela abre sus bloques (con
/// borrar). Observa el vm para reflejar borrados en los contadores.
private struct AccountSchoolsList: View {
    @ObservedObject var vm: AccountViewModel
    var body: some View {
        let schools = vm.stats?.bySchool ?? []
        Group {
            if schools.isEmpty {
                Text("Aún no hay bloques en ninguna escuela.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(schools, id: \.schoolName) { s in
                            // Antes iba directo a la lista plana; ahora pasa por
                            // "sectores" para no mezclar todos los bloques/vías de
                            // la escuela de golpe (paridad con Android).
                            NavigationLink(destination: AccountSchoolSectorsList(vm: vm, schoolName: s.schoolName)) {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                        let blocks = "\(s.blockCount) \(s.blockCount == 1 ? "bloque" : "bloques")"
                                        let grade = s.maxGrade.map { " · máx \($0)" } ?? ""
                                        Text(blocks + grade).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 12)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Escuelas")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Sectores de una escuela concreta del diario propio: agrupa por sector (nº
/// de bloques) y deja sueltas (sin subcarpeta) las entradas sin sector resuelto
/// o con un sector aún no catalogado. Pulsar un sector entra a sus bloques.
private struct AccountSchoolSectorsList: View {
    @ObservedObject var vm: AccountViewModel
    let schoolName: String

    private var schoolEntries: [JournalSession] {
        vm.entries.filter { $0.schoolName?.caseInsensitiveCompare(schoolName) == .orderedSame }
    }
    private var sectors: [(name: String, count: Int)] {
        var counts: [String: Int] = [:]
        for e in schoolEntries {
            if let s = vm.viaInfo[e.id]?.sector, !s.isEmpty {
                counts[s, default: 0] += 1
            }
        }
        return counts.map { (name: $0.key, count: $0.value) }.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }
    private var loose: [JournalSession] {
        schoolEntries.filter { (vm.viaInfo[$0.id]?.sector ?? "").isEmpty }
    }

    var body: some View {
        Group {
            if sectors.isEmpty && loose.isEmpty {
                Text("Sin bloques registrados aquí.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(sectors, id: \.name) { sec in
                            NavigationLink(destination: AccountSchoolBlocksList(vm: vm, schoolName: schoolName, sectorName: sec.name)) {
                                HStack(spacing: 12) {
                                    Image(systemName: "folder").font(.system(size: 16)).foregroundStyle(Cumbre.terra)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(sec.name).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                        Text(sec.count == 1 ? "1 bloque" : "\(sec.count) bloques")
                                            .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 12)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(loose, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id])
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(schoolName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Bloques de una escuela concreta del diario propio, con borrar. Si
/// `sectorName` viene indicado, solo los de ese sector (llegado desde
/// AccountSchoolSectorsList); si no, todos (compatibilidad).
private struct AccountSchoolBlocksList: View {
    @ObservedObject var vm: AccountViewModel
    let schoolName: String
    var sectorName: String? = nil
    var body: some View {
        let entries = vm.entries.filter {
            $0.schoolName?.caseInsensitiveCompare(schoolName) == .orderedSame &&
            (sectorName == nil || vm.viaInfo[$0.id]?.sector?.caseInsensitiveCompare(sectorName!) == .orderedSame)
        }
        Group {
            if entries.isEmpty {
                Text("Sin bloques registrados aquí.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        // Nombre de la escuela → abre la escuela (sin piedra).
                        if let sid = entries.first?.schoolId, !sid.isEmpty {
                            NavigationLink(destination: SchoolLoaderView(schoolId: sid)) {
                                HStack(spacing: 8) {
                                    Image(systemName: "mountain.2").font(.system(size: 14)).foregroundStyle(Cumbre.terra)
                                    Text("VER ESCUELA").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 12)
                                .contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(entries, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id]) { vm.deleteBlock(e.id) }
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(sectorName ?? schoolName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private extension String { var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self } }
