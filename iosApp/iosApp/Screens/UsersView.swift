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
            .navigationTitle("Buscar usuarios")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
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
    @Published var loading = true

    private let getPublicProfile: GetPublicProfileUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getUserStats: GetUserStatsUseCase
    private let getUserJournal: GetUserJournalUseCase

    init(
        getPublicProfile: GetPublicProfileUseCase = AppDependencies.shared.container.getPublicProfile,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getUserStats: GetUserStatsUseCase = AppDependencies.shared.container.getUserStats,
        getUserJournal: GetUserJournalUseCase = AppDependencies.shared.container.getUserJournal
    ) {
        self.getPublicProfile = getPublicProfile
        self.getFollowStatus = getFollowStatus
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getUserStats = getUserStats
        self.getUserJournal = getUserJournal
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
        if profile?.locked == true { stats = nil; entries = []; return }
        stats = try? await getUserStats.invoke(uid: uid)
        entries = (try? await getUserJournal.invoke(uid: uid)) ?? []
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
                    }
                    // Diario del usuario: BLOQUES / ESCUELAS / MÁXIMO navegables
                    // (igual que tu cuenta). Solo si su perfil es visible.
                    if let st = vm.stats {
                        Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                        Text("DIARIO").eyebrow().frame(maxWidth: .infinity, alignment: .leading)
                        JournalStatsNav(stats: st, entries: vm.entries)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 24).padding(.horizontal, 24)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Perfil")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load(uid: uid) }
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
            Text(pending ? "SOLICITADO" : (following ? "SIGUIENDO" : "SEGUIR"))
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
    var body: some View {
        Group {
            if let s = url, let u = URL(string: s) {
                AsyncImage(url: u) { img in img.resizable().scaledToFill() }
                placeholder: { Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3) }
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

// MARK: - Lista de seguidores / seguidos

enum FollowListMode { case followers, following }

@MainActor
final class FollowListViewModel: ObservableObject {
    @Published var items: [PublicProfile] = []
    @Published var loading = true

    private let getFollowers: GetFollowersUseCase
    private let getFollowing: GetFollowingUseCase
    init(
        getFollowers: GetFollowersUseCase = AppDependencies.shared.container.getFollowers,
        getFollowing: GetFollowingUseCase = AppDependencies.shared.container.getFollowing
    ) {
        self.getFollowers = getFollowers
        self.getFollowing = getFollowing
    }

    func load(uid: String, mode: FollowListMode) async {
        loading = true
        switch mode {
        case .followers: items = (try? await getFollowers.invoke(uid: uid)) ?? []
        case .following: items = (try? await getFollowing.invoke(uid: uid)) ?? []
        }
        loading = false
    }
}

struct FollowListView: View {
    let uid: String
    let mode: FollowListMode
    @StateObject private var vm = FollowListViewModel()

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
                            NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                UserRow(profile: u)
                            }.buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(mode == .followers ? "Seguidores" : "Siguiendo")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load(uid: uid, mode: mode) }
    }
}
