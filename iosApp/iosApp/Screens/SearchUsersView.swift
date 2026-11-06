import SwiftUI
import Shared

// Buscar personas, con las recientes guardadas en el móvil.

@MainActor
final class SearchUsersViewModel: ObservableObject {
    @Published var query = ""
    @Published var results: [PublicProfile] = []
    @Published var loading = false
    /// A quién sigo yo y a quién he pedido seguir (perfil privado), para el botón de cada tarjeta.
    @Published var following: Set<String> = []
    @Published var requested: Set<String> = []
    /// Últimas personas abiertas desde el buscador (se guardan en el móvil).
    @Published var recents: [RecentUser] = RecentUser.load()

    private let searchUsers: SearchUsersUseCase
    /// Gente que te sigue y a la que tú no sigues: se enseña antes de escribir, como «sugeridos».
    @Published var suggested: [PublicProfile] = []

    private let getFollowing: GetFollowingUseCase
    private let getFollowers: GetFollowersUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    private var followingLoaded = false

    var myUid: String? { AppDependencies.shared.authBridge.currentUid() }

    init(
        searchUsers: SearchUsersUseCase = AppDependencies.shared.container.searchUsers,
        getFollowers: GetFollowersUseCase = AppDependencies.shared.container.getFollowers,
        getFollowing: GetFollowingUseCase = AppDependencies.shared.container.getFollowing,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus
    ) {
        self.searchUsers = searchUsers
        self.getFollowers = getFollowers
        self.getFollowing = getFollowing
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getFollowStatus = getFollowStatus
    }

    /// Sugeridos: quienes te siguen y tú no (máx. 5). Se pide una vez al abrir el buscador.
    func loadSuggested() async {
        guard suggested.isEmpty, let me = myUid else { return }
        await loadFollowingOnce()
        let followers = (try? await getFollowers.invoke(uid: me)) ?? []
        suggested = Array(followers.filter { !following.contains($0.uid) && $0.uid != me }.prefix(5))
    }

    func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { results = []; return }
        loading = true
        results = (try? await searchUsers.invoke(query: q, limit: 20)) ?? []
        loading = false
        await loadFollowingOnce()
    }

    private func loadFollowingOnce() async {
        guard !followingLoaded, let me = myUid else { return }
        followingLoaded = true
        following = Set(((try? await getFollowing.invoke(uid: me)) ?? []).map { $0.uid })
    }

    /// Sigue / deja de seguir desde la tarjeta (optimista; si el perfil es privado queda «Solicitado»).
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

    func remember(_ p: PublicProfile) {
        recents = RecentUser.remember(RecentUser(uid: p.uid, name: p.displayName ?? p.username ?? "",
                                                  username: p.username, photoUrl: p.photoUrl))
    }
}

/// Persona abierta desde el buscador, guardada para enseñarla al abrir sin haber escrito nada.
struct RecentUser: Codable, Identifiable {
    let uid: String
    let name: String
    let username: String?
    let photoUrl: String?
    var id: String { uid }

    private static let key = "recent_user_searches_v1"
    private static let limit = 6

    static func load() -> [RecentUser] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let list = try? JSONDecoder().decode([RecentUser].self, from: data) else { return [] }
        return list
    }

    static func remember(_ u: RecentUser) -> [RecentUser] {
        var list = load().filter { $0.uid != u.uid }
        list.insert(u, at: 0)
        list = Array(list.prefix(limit))
        if let data = try? JSONEncoder().encode(list) { UserDefaults.standard.set(data, forKey: key) }
        return list
    }
}

struct SearchUsersView: View {
    @StateObject private var vm = SearchUsersViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchField
                if vm.loading {
                    SkeletonList(count: 6) { SkeletonPersonRow(trailing: true) }
                    Spacer()
                } else if vm.results.isEmpty {
                    if vm.query.trimmingCharacters(in: .whitespaces).count < 2 {
                        recentsList
                    } else {
                        Spacer()
                        Text(L("Sin resultados"))
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        Spacer()
                    }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8, pinnedViews: []) {
                            Text(L("%@ PERSONAS", vm.results.count))
                                .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 20).padding(.top, 4)
                            ForEach(vm.results, id: \.uid) { u in
                                HStack(spacing: 8) {
                                    NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                        UserRow(profile: u)
                                    }
                                    .buttonStyle(.plain)
                                    .simultaneousGesture(TapGesture().onEnded { vm.remember(u) })
                                    followButton(u)
                                }
                                .padding(.leading, 6).padding(.trailing, 12)
                                .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                                .padding(.horizontal, 16)
                            }
                        }
                        .padding(.bottom, 16)
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .task { await vm.loadSuggested() }
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
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        .padding(16)
    }

    /// Botón Seguir / Siguiendo / Solicitado de cada tarjeta (no sale en tu propia cuenta).
    @ViewBuilder private func followButton(_ u: PublicProfile) -> some View {
        if u.uid != vm.myUid {
            let isFollowing = vm.following.contains(u.uid)
            let isRequested = vm.requested.contains(u.uid)
            Button { vm.toggleFollow(u.uid) } label: {
                Text(isFollowing ? L("Siguiendo") : (isRequested ? L("Solicitado") : L("Seguir")))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(isFollowing || isRequested ? Cumbre.terra : Color.white)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(isFollowing || isRequested ? Color.clear : Cumbre.terraFill, in: Capsule())
                    .overlay(Capsule().stroke(Cumbre.terra, lineWidth: isFollowing || isRequested ? 1.5 : 0))
            }
            .buttonStyle(.plain)
        }
    }

    /// Antes de escribir: las últimas personas que abriste, en vez de un texto vacío.
    @ViewBuilder private var recentsList: some View {
        if vm.recents.isEmpty && vm.suggested.isEmpty {
            Spacer()
            Text(L("Escribe al menos 2 letras"))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
            Spacer()
        } else {
            ScrollView {
                VStack(spacing: 8) {
                    if !vm.suggested.isEmpty {
                        Text(L("TE SIGUEN Y NO LES SIGUES"))
                            .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20)
                        ForEach(vm.suggested, id: \.uid) { u in
                            HStack(spacing: 8) {
                                NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                                    UserRow(profile: u)
                                }
                                .buttonStyle(.plain)
                                .simultaneousGesture(TapGesture().onEnded { vm.remember(u) })
                                followButton(u)
                            }
                            .padding(.leading, 6).padding(.trailing, 12)
                            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                            .padding(.horizontal, 16)
                        }
                    }
                    if !vm.recents.isEmpty {
                    Text(L("RECIENTES"))
                        .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20).padding(.top, 8)
                    }
                    ForEach(vm.recents) { r in
                        NavigationLink(destination: PublicProfileView(uid: r.uid)) {
                            HStack(spacing: 12) {
                                AvatarCircle(url: r.photoUrl, size: 40, uid: r.uid)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.name.isEmpty ? L("Usuario") : r.name)
                                        .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                    if let u = r.username, !u.isEmpty {
                                        Text("@\(u)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                            .padding(.horizontal, 16)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

struct UserRow: View {
    let profile: PublicProfile
    var body: some View {
        HStack(spacing: 12) {
            AvatarCircle(url: profile.photoUrl, size: 40, uid: profile.uid)
            VStack(alignment: .leading, spacing: 2) {
                Text(profile.displayName ?? profile.username ?? L("Usuario"))
                    .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                if let u = profile.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            if let g = profile.topGrade, !g.isEmpty {
                Text(g).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Cumbre.terra.opacity(0.12), in: Capsule())
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

// MARK: - Perfil público
