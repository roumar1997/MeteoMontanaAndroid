import SwiftUI
import Shared

// Búsqueda de usuarios + perfil público con seguir/dejar de seguir.
// Espejo de SearchUsersScreen.kt y PublicProfileScreen.kt de Android.

@MainActor
final class SearchUsersViewModel: ObservableObject {
    @Published var query = ""
    @Published var results: [PublicProfile] = []
    @Published var loading = false

    private let searchUsers: SearchUsersUseCase
    init(searchUsers: SearchUsersUseCase = AppDependencies.shared.container.searchUsers) {
        self.searchUsers = searchUsers
    }

    func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; return }
        loading = true
        results = (try? await searchUsers.invoke(query: q, limit: 20)) ?? []
        loading = false
    }
}

struct SearchUsersView: View {
    @StateObject private var vm = SearchUsersViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchField
                Divider().overlay(Cumbre.rule)
                if vm.loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 24)
                    Spacer()
                } else if vm.results.isEmpty {
                    Spacer()
                    Text(vm.query.count < 2 ? "Escribe al menos 2 letras" : "Sin resultados")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(vm.results, id: \.uid) { u in
                                NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                    UserRow(profile: u)
                                }
                                .buttonStyle(.plain)
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(NSLocalizedString("search_users_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
            TextField("Nombre o usuario…", text: $vm.query)
                .foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .onChange(of: vm.query) { _, _ in Task { await vm.search() } }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(16)
    }
}

private struct UserRow: View {
    let profile: PublicProfile
    var body: some View {
        HStack(spacing: 12) {
            AvatarCircle(url: profile.photoUrl, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(profile.displayName ?? profile.username ?? "Usuario")
                    .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                if let u = profile.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            if let g = profile.topGrade, !g.isEmpty {
                Text(g).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

// MARK: - Perfil público

@MainActor
final class PublicProfileViewModel: ObservableObject {
    @Published var profile: PublicProfile?
    @Published var status: FollowStatus?
    @Published var stats: JournalStats?
    @Published var entries: [JournalSession] = []
    @Published var viaInfo: [String: ViaCatalogInfo] = [:]
    @Published var loading = true

    private let getPublicProfile: GetPublicProfileUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getUserStats: GetUserStatsUseCase
    private let getUserJournal: GetUserJournalUseCase
    private let getJournalViaInfo: GetJournalViaInfoUseCase

    init(
        getPublicProfile: GetPublicProfileUseCase = AppDependencies.shared.container.getPublicProfile,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getUserStats: GetUserStatsUseCase = AppDependencies.shared.container.getUserStats,
        getUserJournal: GetUserJournalUseCase = AppDependencies.shared.container.getUserJournal,
        getJournalViaInfo: GetJournalViaInfoUseCase = AppDependencies.shared.container.getJournalViaInfo
    ) {
        self.getPublicProfile = getPublicProfile
        self.getFollowStatus = getFollowStatus
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getUserStats = getUserStats
        self.getUserJournal = getUserJournal
        self.getJournalViaInfo = getJournalViaInfo
    }

    func load(uid: String) async {
        loading = true
        profile = try? await getPublicProfile.invoke(uid: uid)
        status = try? await getFollowStatus.invoke(uid: uid)
        loading = false
        // Diario y stats del usuario (solo si su perfil no está bloqueado).
        await loadJournal(uid: uid)
    }

    /// Carga el diario/stats del usuario. Si su perfil es privado y no le sigues,
    /// el backend devuelve 403 → quedan vacíos (y la UI no muestra la sección).
    func loadJournal(uid: String) async {
        if profile?.locked == true { stats = nil; entries = []; viaInfo = [:]; return }
        stats = try? await getUserStats.invoke(uid: uid)
        // Solo HECHO: los PROYECTOS tienen su propia pantalla (ProjectsView).
        entries = ((try? await getUserJournal.invoke(uid: uid)) ?? []).filter { $0.status != "PROJECT" }
        // Nº de piedra + sector de cada vía, resueltos en vivo del catálogo.
        viaInfo = (try? await getJournalViaInfo.invoke(entries: entries)) ?? [:]
    }

    func toggleFollow(uid: String) {
        guard let s = status else { return }
        let wasFollowing = s.iFollowThem
        Task {
            do {
                if wasFollowing { try await unfollowUser.invoke(uid: uid) }
                else { try await followUser.invoke(uid: uid) }
                status = try? await getFollowStatus.invoke(uid: uid)
                profile = try? await getPublicProfile.invoke(uid: uid)
                await loadJournal(uid: uid)   // tras aceptar/seguir puede desbloquearse
            } catch {}
        }
    }
}

struct PublicProfileView: View {
    let uid: String
    @StateObject private var vm = PublicProfileViewModel()
    // Moderación: denunciar / bloquear a este usuario (menú superior).
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var showReport = false

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                AvatarCircle(url: vm.profile?.photoUrl, size: 88)
                Text(vm.profile?.displayName ?? vm.profile?.username ?? "Usuario")
                    .font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                if let u = vm.profile?.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink3)
                }
                if vm.profile?.locked == true {
                    // Perfil privado: bloqueado para quien no le sigue. Solo
                    // nombre/avatar + botón para solicitar seguir.
                    VStack(spacing: 8) {
                        Image(systemName: "lock.fill").font(.system(size: 22)).foregroundStyle(Cumbre.ink3)
                        Text("Este perfil es privado")
                            .font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink2)
                        Text("Sigue a este usuario para ver su diario y estadísticas.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                            .multilineTextAlignment(.center).padding(.horizontal, 32)
                    }.padding(.vertical, 8)
                    if let s = vm.status { followButton(s) }
                } else {
                    if let bio = vm.profile?.bio, !bio.isEmpty {
                        Text(bio).font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.center).padding(.horizontal, 24)
                    }
                    if let g = vm.profile?.topGrade, !g.isEmpty {
                        Text("TOPE \(g.uppercased())").font(Cumbre.mono(10, .bold)).tracking(1.0)
                            .foregroundStyle(Cumbre.terra)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }
                    if let s = vm.status {
                        HStack(spacing: 24) {
                            NavigationLink(destination: FollowListView(uid: uid, mode: .followers)) {
                                stat("\(s.followers)", "SEGUIDORES")
                            }.buttonStyle(.plain)
                            NavigationLink(destination: FollowListView(uid: uid, mode: .following)) {
                                stat("\(s.following)", "SIGUIENDO")
                            }.buttonStyle(.plain)
                        }
                        followButton(s)
                        // Chat 1-a-1 con este usuario (Firestore).
                        NavigationLink(destination: ChatView(
                            otherUid: uid,
                            otherName: vm.profile?.displayName ?? vm.profile?.username ?? "Usuario")) {
                            Text(NSLocalizedString("chat_message", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                    // Diario del usuario: BLOQUES / ESCUELAS / MÁXIMO navegables
                    // (igual que tu cuenta). Solo si su perfil es visible.
                    if let st = vm.stats {
                        Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                        Text("DIARIO").eyebrow().frame(maxWidth: .infinity, alignment: .leading)
                        JournalStatsNav(stats: st, entries: vm.entries, viaInfo: vm.viaInfo, projectsUid: uid)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 24).padding(.horizontal, 24)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Perfil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // Compartir el perfil: enlace /s/u/ que abre la app (o lleva a la store).
            ToolbarItem(placement: .topBarTrailing) {
                let handle = vm.profile?.username ?? uid
                let label = vm.profile?.username.map { "@" + $0 }
                    ?? vm.profile?.displayName ?? "este escalador"
                ShareLink(item: URL(string: "https://api.climbingteams.com/s/u/\(handle)")!,
                          subject: Text("Perfil de \(label) en Cumbre"),
                          message: Text("Perfil de \(label) en Cumbre:")) {
                    Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink2)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { showReport = true } label: {
                        Label("Denunciar usuario", systemImage: "flag")
                    }
                    if moderation.blocked.contains(uid) {
                        Button { moderation.unblock(uid) } label: {
                            Label("Desbloquear", systemImage: "hand.raised.slash")
                        }
                    } else {
                        Button(role: .destructive) { moderation.block(uid) } label: {
                            Label("Bloquear (no verás su contenido)", systemImage: "hand.raised")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(Cumbre.ink2)
                }
            }
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(title: "DENUNCIAR USUARIO",
                        authorLabel: vm.profile?.username.map { "@" + $0 } ?? "usuario") { reason, alsoBlock in
                moderation.report(targetType: "USER", targetId: uid, reason: reason,
                                  alsoBlockUid: alsoBlock ? uid : nil)
            }
        }
        .task { await vm.load(uid: uid); await moderation.loadBlocked() }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(label).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
    }

    @ViewBuilder private func followButton(_ s: FollowStatus) -> some View {
        let following = s.iFollowThem
        let pending = s.requestPending
        Button { vm.toggleFollow(uid: uid) } label: {
            Text(pending ? NSLocalizedString("profile_requested", comment: "") : (following ? NSLocalizedString("profile_unfollow", comment: "") : NSLocalizedString("profile_follow", comment: "")))
                .font(Cumbre.mono(12, .bold)).tracking(0.8)
                .foregroundStyle(following || pending ? Cumbre.ink : .white)
                .padding(.vertical, 12).padding(.horizontal, 28)
                .background(following || pending ? Color.clear : Cumbre.terra)
                .overlay(Rectangle().stroke(following || pending ? Cumbre.rule : Cumbre.terra, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(pending)
        .padding(.top, 4)
    }
}

// MARK: - Avatar reutilizable

struct AvatarCircle: View {
    let url: String?
    let size: CGFloat
    @State private var image: UIImage?
    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
        // Caché en disco (igual que las fotos de piedras): el avatar se ve SIN
        // conexión una vez descargado. AsyncImage no persistía en disco → offline
        // quedaba en el placeholder aunque tuviéramos la URL cacheada.
        .task(id: url) {
            image = nil
            guard let url, !url.isEmpty else { return }
            if let img = await ImageCache.image(url) { image = img }
        }
    }
}

// MARK: - Lista de seguidores / seguidos

enum FollowListMode { case followers, following }

@MainActor
final class FollowListViewModel: ObservableObject {
    @Published var items: [PublicProfile] = []
    // uids a los que YO sigo (aceptado) / con solicitud pendiente → botón por fila.
    @Published var following: Set<String> = []
    @Published var requested: Set<String> = []
    @Published var loading = true

    private let getFollowers: GetFollowersUseCase
    private let getFollowing: GetFollowingUseCase
    private let removeFollowerUseCase: RemoveFollowerUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    init(
        getFollowers: GetFollowersUseCase = AppDependencies.shared.container.getFollowers,
        getFollowing: GetFollowingUseCase = AppDependencies.shared.container.getFollowing,
        removeFollowerUseCase: RemoveFollowerUseCase = AppDependencies.shared.container.removeFollower,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus
    ) {
        self.getFollowers = getFollowers
        self.getFollowing = getFollowing
        self.removeFollowerUseCase = removeFollowerUseCase
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getFollowStatus = getFollowStatus
    }

    func load(uid: String, mode: FollowListMode) async {
        loading = true
        let myUid = AppDependencies.shared.authBridge.currentUid()
        switch mode {
        case .followers: items = (try? await getFollowers.invoke(uid: uid)) ?? []
        case .following: items = (try? await getFollowing.invoke(uid: uid)) ?? []
        }
        // Conjunto de a quién sigo YO (para los botones). Mi propia lista de
        // "Siguiendo" ya ES ese conjunto; si no, lo pido aparte.
        if uid == myUid && mode == .following {
            following = Set(items.map { $0.uid })
        } else if let me = myUid {
            following = Set(((try? await getFollowing.invoke(uid: me)) ?? []).map { $0.uid })
        }
        // Solicitudes pendientes: a un perfil privado al que ya pedí seguir hay que
        // pintarlo "Solicitado" también en la lista (no solo en su perfil). El
        // backend no devuelve el estado por fila → consulto el follow-status (en
        // paralelo) de cada usuario que NO sigo.
        if let me = myUid {
            let toCheck = items.filter { $0.uid != me && !following.contains($0.uid) }
            let getStatus = getFollowStatus
            var pending: Set<String> = []
            await withTaskGroup(of: (String, Bool).self) { group in
                for p in toCheck {
                    group.addTask {
                        let st = try? await getStatus.invoke(uid: p.uid)
                        return (p.uid, st?.requestPending ?? false)
                    }
                }
                for await (u, isPending) in group where isPending { pending.insert(u) }
            }
            requested = pending
        }
        loading = false
    }

    /// Elimina a un seguidor (optimista; si la red falla, recargo mi lista).
    func remove(followerUid: String) {
        let backup = items
        items = items.filter { $0.uid != followerUid }
        Task {
            do { try await removeFollowerUseCase.invoke(uid: followerUid) }
            catch { items = backup }
        }
    }

    /// Sigue / deja de seguir desde la fila (optimista, reconcilia con el backend).
    func toggleFollow(_ targetUid: String) {
        if following.contains(targetUid) {
            following.remove(targetUid)
            Task {
                do { try await unfollowUser.invoke(uid: targetUid) }
                catch { following.insert(targetUid) }
            }
        } else if requested.contains(targetUid) {
            return
        } else {
            following.insert(targetUid)
            Task {
                do {
                    try await followUser.invoke(uid: targetUid)
                    let st = try await getFollowStatus.invoke(uid: targetUid)
                    if st.iFollowThem {
                        following.insert(targetUid); requested.remove(targetUid)
                    } else if st.requestPending {
                        following.remove(targetUid); requested.insert(targetUid)
                    } else {
                        following.remove(targetUid)
                    }
                } catch { following.remove(targetUid) }
            }
        }
    }
}

struct FollowListView: View {
    let uid: String
    let mode: FollowListMode
    @StateObject private var vm = FollowListViewModel()

    private var myUid: String? { AppDependencies.shared.authBridge.currentUid() }
    // Solo puedo eliminar seguidores en MI propia lista de "Seguidores".
    private var canRemove: Bool { mode == .followers && uid == myUid }

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.items.isEmpty {
                Text(mode == .followers ? "Sin seguidores" : "No sigue a nadie")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(vm.items, id: \.uid) { u in
                            HStack(spacing: 8) {
                                NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                    UserRow(profile: u)
                                }.buttonStyle(.plain)
                                // Botón seguir/siguiendo/solicitado (también en listas
                                // de otros, para seguir sin entrar al perfil).
                                if u.uid != myUid {
                                    followButton(u.uid)
                                }
                                if canRemove {
                                    rowButton("ELIMINAR", filled: false) { vm.remove(followerUid: u.uid) }
                                }
                            }
                            .padding(.trailing, 12)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(mode == .followers ? NSLocalizedString("profile_followers", comment: "") : NSLocalizedString("profile_following", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load(uid: uid, mode: mode) }
    }

    @ViewBuilder private func followButton(_ targetUid: String) -> some View {
        let iFollow = vm.following.contains(targetUid)
        let pending = vm.requested.contains(targetUid)
        let label = iFollow ? NSLocalizedString("profile_unfollow", comment: "") : (pending ? NSLocalizedString("profile_requested", comment: "") : NSLocalizedString("profile_follow", comment: ""))
        rowButton(label, filled: !iFollow && !pending, enabled: !pending) {
            vm.toggleFollow(targetUid)
        }
    }

    private func rowButton(_ text: String, filled: Bool, enabled: Bool = true,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(Cumbre.mono(10, .bold)).tracking(0.6)
                .foregroundStyle(filled ? .white : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(filled ? Cumbre.terra : Color.clear)
                .overlay(Rectangle().stroke(filled ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
