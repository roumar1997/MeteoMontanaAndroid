import SwiftUI
import Shared

// Lista de seguidores y seguidos.

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
    @State private var query = ""

    private var myUid: String? { AppDependencies.shared.authBridge.currentUid() }
    // Solo puedo eliminar seguidores en MI propia lista de "Seguidores".
    private var canRemove: Bool { mode == .followers && uid == myUid }

    /// Lista filtrada por el buscador (nombre o @usuario).
    private var filtered: [PublicProfile] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return vm.items }
        return vm.items.filter {
            ($0.displayName?.lowercased().contains(q) ?? false) ||
            ($0.username?.lowercased().contains(q) ?? false)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Buscador dentro de la lista (por nombre o @usuario).
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
                TextField("Buscar en la lista…", text: $query)
                    .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .dismissKeyboardOnSubmit()
            }
            .padding(.horizontal, 12).padding(.vertical, 9)
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .padding(.horizontal, 12).padding(.vertical, 8)
            Divider().overlay(Cumbre.rule)

            Group {
            if vm.loading {
                SkeletonList(count: 8) { SkeletonPersonRow(trailing: true) }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if filtered.isEmpty {
                Text(vm.items.isEmpty
                     ? (mode == .followers ? L("Sin seguidores") : L("No sigue a nadie"))
                     : L("Nadie coincide con la búsqueda"))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered, id: \.uid) { u in
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
                                    rowButton(L("ELIMINAR"), filled: false) { vm.remove(followerUid: u.uid) }
                                }
                            }
                            .padding(.trailing, 12)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
            }   // cierra el Group
        }       // cierra el VStack (buscador + contenido)
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
