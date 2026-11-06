import SwiftUI
import Shared
import PhotosUI

/// Foto elegida desde el perfil, pendiente de recortar.
struct AvatarCrop: Identifiable { let id = UUID(); let image: UIImage }

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
            // Dedup POR lineId. Con la clave por NOMBRE, dos vias homonimas
            // ("La ola" y "La ola (directa)") colapsaban en una sola y la
            // segunda desaparecia del diario aunque estuviera marcada. El
            // nombre solo se usa como LEGADO, entre entradas sin lineId.
            let dedupKey: String
            if let lid = e.lineId, !lid.isEmpty {
                dedupKey = "#\(lid)"
            } else {
                dedupKey = "\(e.schoolId ?? "")|\((e.sector ?? "").trimmingCharacters(in: .whitespaces).lowercased())|\(e.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
            }
            return seen.insert(dedupKey).inserted
        }
    }

    func addBlock(blockName: String, grade: String, schoolId: String?, schoolName: String, sector: String, notes: String, discipline: String) async {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let req = CreateJournalRequest(
            schoolId: schoolId, schoolName: schoolName.nilIfBlank, sector: sector.nilIfBlank,
            blockName: blockName.trimmingCharacters(in: .whitespaces), grade: grade.nilIfBlank,
            notes: notes.nilIfBlank, date: df.string(from: Date()), discipline: discipline, lineId: nil, status: nil,
            aVista: false, alFlash: false)
        _ = try? await createEntry.invoke(req: req)
        await reloadJournal()
    }

    /// Borra la cuenta (datos + Firebase Auth) y cierra sesión. El gate de login
    /// (RootView) vuelve a la pantalla de acceso al desaparecer la sesión.
    func deleteMyAccount() async {
        _ = try? await deleteAccount.invoke()
        AppDependencies.shared.container.localCacheCleaner?.clearServerCaches()
        authBridge.signOut {}
    }

    /** N6: cambiar la fecha de una entrada y recargar. */
    func changeDate(_ id: String, _ newDate: String) async {
        _ = await reporting(L("No se pudo cambiar la fecha"), {
            try await AppDependencies.shared.container.updateJournalDate.invoke(id: id, date: newDate)
        })
        await load()
    }

    /** Cambiar el estilo (a vista / al flash) de una entrada. Excluyentes (uno u otro). */
    func changeStyle(_ id: String, _ aVista: Bool, _ alFlash: Bool) async {
        _ = await reporting(L("No se pudo cambiar el estilo"), {
            try await AppDependencies.shared.container.updateJournalStyle.invoke(
                id: id, aVista: aVista, alFlash: alFlash)
        })
        await load()
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

    /// Cambiar la foto tocándola en el perfil (Álvaro, 2026-10-23), sin pasar por Editar perfil: se sube y se guarda
    /// SOLO la foto (el servidor deja igual los campos que no se mandan).
    @Published var uploadingPhoto = false

    func changePhoto(_ image: UIImage) async -> Bool {
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        guard let url = try? await StorageUploader.uploadProfilePhoto(image) else { return false }
        let req = UpdateProfileRequest(username: nil, displayName: nil, bio: nil, topGrade: nil, isPublic: nil,
                                       photoUrl: url, gender: nil, gearJson: nil)
        guard (try? await AppDependencies.shared.container.updateMyProfile.invoke(req: req)) != nil else { return false }
        await load()
        return true
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
                let subs = (try? await getPendingSubmissions.invoke(status: nil))?.count ?? 0
                let contribs = (try? await getPendingContributions.invoke(status: nil))?.count ?? 0
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
    @State private var showGear = false
    @State private var zoomUrl: String? = nil   // foto de perfil a pantalla completa
    // Tocar la foto: menú «Cambiar foto / Ver foto» → selector → recortador → se sube.
    @State private var avatarMenu = false
    @State private var showPhotoPicker = false
    @State private var photoItem: PhotosPickerItem?
    @State private var avatarCrop: AvatarCrop?
    @State private var photoError = false

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 10) {
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
                    // Todo más junto para que el diario y ESTADÍSTICAS se vean sin bajar (Álvaro, 2026-10-19).
                    followCounters
                        .guideTarget("profile.follows")
                    diarySection
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
                .padding(.horizontal, 20)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .guideHost("profile")
            // Sin titulo: debajo ya estan tu foto, tu nombre y tu @usuario.
            // Poner "Cuenta" encima solo repite donde estas y roba altura.
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    GuideHelpButton(topicKey: "profile")
                    // Acceso admin (solo admins) con nº de pendientes, junto al "?".
                    if vm.profile?.isAdmin == true {
                        NavigationLink(destination: AdminView()) {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.shield")
                                if vm.pendingReview > 0 {
                                    Text("\(vm.pendingReview)").font(Cumbre.mono(12, .bold))
                                }
                            }
                        }.foregroundStyle(Cumbre.terra)
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if vm.profile != nil {
                        // Mi material: vivia enterrado en Editar perfil y es de
                        // lo que mas se cambia (dos crashpads un finde, tres el
                        // siguiente). Aqui se llega en un toque.
                        Button { showGear = true } label: {
                            Image(systemName: "backpack")
                        }.foregroundStyle(Cumbre.terra)
                        Button { shareProfile() } label: {
                            Image(systemName: "square.and.arrow.up")
                        }.foregroundStyle(Cumbre.terra)
                        NavigationLink(destination: AccountSettingsView(vm: vm)) {
                            Image(systemName: "gearshape")
                        }.foregroundStyle(Cumbre.ink)
                    }
                    if showClose {
                        Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                    }
                }
            }
            .sheet(isPresented: $showGear) { MyGearSheet() }
            .task { await vm.load() }
            .fullScreenCover(isPresented: Binding(
                get: { zoomUrl != nil },
                set: { if !$0 { zoomUrl = nil } })) {
                if let url = zoomUrl {
                    FullScreenPhotoView(photoUrl: url) { zoomUrl = nil }
                }
            }
        }
    }

    /// Diario: stats PULSABLES (BLOQUES/ESCUELAS abren su lista) + AÑADIR BLOQUE.
    /// El listado completo ya no se vuelca aquí: se ve entrando en BLOQUES o
    /// ESCUELAS, y desde dentro se puede borrar.
    @ViewBuilder private var diarySection: some View {
        // Sin «AÑADIR BLOQUE/VÍA»: se apunta con el ✓ desde la piedra (Álvaro, 2026-10-19).
        AccountJournalStatsNav(vm: vm)
            .guideTarget("profile.diary")
    }

    private var avatar: some View {
        // Si el backend no tiene foto, usa la de la cuenta de Google (Firebase Auth).
        // AvatarCircle cachea en disco → se ve offline (igual que el resto de avatares).
        let photo = vm.profile?.photoUrl?.isEmpty == false ? vm.profile?.photoUrl : authBridge.currentPhotoUrl()
        let hasPhoto = photo?.isEmpty == false
        // Sin conexión o sin perfil cargado no se puede subir: el toque solo amplía la foto, como antes.
        let canChange = vm.profile != nil && !vm.offline
        return AvatarCircle(url: photo, size: 76, uid: vm.profile?.uid)
            .overlay {
                if vm.uploadingPhoto {
                    ZStack {
                        Circle().fill(Color.black.opacity(0.35))
                        ProgressView().tint(.white)
                    }
                    .frame(width: 76, height: 76)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if canChange {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(Cumbre.terraFill, in: Circle())
                        .overlay(Circle().stroke(Cumbre.bg, lineWidth: 2))
                        .accessibilityHidden(true)
                }
            }
            .onTapGesture {
                if canChange { avatarMenu = true } else if hasPhoto { zoomUrl = photo }
            }
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel(L("Foto de perfil"))
            .confirmationDialog(L("Foto de perfil"), isPresented: $avatarMenu, titleVisibility: .visible) {
                Button(L("Cambiar foto")) { showPhotoPicker = true }
                if hasPhoto, let p = photo {
                    Button(L("Ver foto")) { zoomUrl = p }
                }
                Button(L("Cancelar"), role: .cancel) {}
            }
            .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images)
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task { @MainActor in
                    if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                        avatarCrop = AvatarCrop(image: img)   // → abre el recortador, igual que en Editar perfil
                    }
                    photoItem = nil   // para poder volver a elegir la misma foto
                }
            }
            .fullScreenCover(item: $avatarCrop) { c in
                ImageCropView(
                    image: c.image,
                    onCancel: { avatarCrop = nil },
                    onDone: { cropped in
                        avatarCrop = nil
                        Task { if !(await vm.changePhoto(cropped)) { photoError = true } }
                    }
                )
            }
            .alert(L("No se pudo cambiar la foto."), isPresented: $photoError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(L("Comprueba la conexión e inténtalo de nuevo."))
            }
    }

    private var names: some View {
        VStack(spacing: 2) {
            HStack(spacing: 6) {
                Text(displayName).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                VerifiedCheck(uid: vm.profile?.uid, size: 19)
            }
            // @usuario y, al lado, ADMIN/PREMIUM (antes desplazaban los seguidores a la izquierda).
            HStack(spacing: 8) {
                if let u = vm.profile?.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink3)
                }
                badges
            }

            // Título, insignias y nivel por aportaciones (tocar = elegir título y viero/bloquero).
            RewardsBlock(uid: nil).padding(.top, 4)
        }
    }

    private var badges: some View {
        HStack(spacing: 8) {
            // El grado máx ya vive en las stats del diario (decisión de Rodrigo).
            if vm.profile?.isPremium == true { badge("PREMIUM", Cumbre.ok) }
        }
    }

    /// Comparte el perfil como imagen 1080×1920 (desde el icono de la barra).
    private func shareProfile() {
        guard let p = vm.profile else { return }
        let handle = (p.username?.isEmpty == false ? p.username! : p.uid)
        let label = p.username.map { "@" + $0 } ?? (p.displayName ?? L("mi perfil"))
        Task {
            let st = vm.stats
            await ShareProfileImage.share(
                handle: handle, displayLabel: label,
                username: p.username, photoUrl: p.photoUrl, topGrade: p.topGrade, bio: p.bio,
                boulders: st.map { Int($0.boulderCount) }, routes: st.map { Int($0.routeCount) },
                schools: st.map { Int($0.schoolCount) })
        }
    }

    /// Seguidores / seguidos tappables (→ FollowListView del propio uid).
    @ViewBuilder private var followCounters: some View {
        if let f = vm.follow, let uid = vm.profile?.uid {
            HStack(spacing: 24) {
                NavigationLink(destination: FollowListView(uid: uid, mode: .followers)) {
                    counter("\(f.followers)", L("SEGUIDORES"))
                }.buttonStyle(.plain)
                NavigationLink(destination: FollowListView(uid: uid, mode: .following)) {
                    counter("\(f.following)", L("SIGUIENDO"))
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

    private var displayName: String {
        vm.profile?.displayName ?? authBridge.currentDisplayName() ?? L("Sin nombre")
    }
    private var email: String? {
        vm.profile?.email ?? authBridge.currentEmail()
    }
}

// MARK: - Ajustes del perfil (pantalla empujada)

private extension String { var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self } }

/// Fila "Idioma" de Ajustes: Automático / Español / English. Al elegir, la app
/// entera se reconstruye en el idioma nuevo (ver LanguageManager).
struct LanguageSettingRow: View {
    @ObservedObject private var lang = LanguageManager.shared
    @State private var open = false

    private var currentLabel: String {
        switch lang.choice {
        case "es": return NSLocalizedString("language_es", comment: "")
        case "en": return NSLocalizedString("language_en", comment: "")
        default: return NSLocalizedString("language_system", comment: "")
        }
    }

    var body: some View {
        Button { open = true } label: {
            HStack(spacing: 12) {
                Image(systemName: "globe").font(.system(size: 16)).foregroundStyle(Cumbre.terra).frame(width: 24)
                Text(NSLocalizedString("settings_language", comment: "")).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Spacer()
                Text(currentLabel).font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }.padding(.vertical, 12).contentShape(Rectangle())
        }.buttonStyle(.plain)
        .confirmationDialog(NSLocalizedString("language_dialog_title", comment: ""), isPresented: $open, titleVisibility: .visible) {
            Button(NSLocalizedString("language_system", comment: "")) { lang.choice = "system" }
            Button(NSLocalizedString("language_es", comment: "")) { lang.choice = "es" }
            Button(NSLocalizedString("language_en", comment: "")) { lang.choice = "en" }
            Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
        }
    }
}
