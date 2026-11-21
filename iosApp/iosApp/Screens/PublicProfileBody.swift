import SwiftUI
import Shared

/// Destinos del perfil ajeno.
enum OtherProfileRoute: Hashable {
    case blocks, routes, schools, posts, boards, followers, following
    case stats(Int)
    case gym(Int64)
}

/// Perfil de otra persona (rondas 6 a 9): «Te sigue» a la izquierda y «Seguir / Siguiendo» compacto a la derecha en la
/// MISMA fila; 4 celdas; fichas Publicaciones · Escuelas · Boards; tarjeta de degradado (mitades a su pestaña de
/// Estadísticas) y «Su rocódromo». Espejo de PublicProfileScreen.kt.
struct PublicProfileBody: View {
    let realUid: String
    let status: FollowStatus?
    let stats: JournalStats?
    let entries: [JournalSession]
    let viaInfo: [String: ViaCatalogInfo]
    let displayName: String
    let onToggleFollow: () -> Void
    @StateObject private var climb = ProfileClimbModel()
    @ObservedObject private var flags = FeatureFlagsModel.shared
    @State private var route: OtherProfileRoute?
    @State private var postCount: Int?
    @State private var confirmUnfollow = false

    var body: some View {
        let gymsOn = flags.flags.isGymsEnabled
        VStack(spacing: 8) {
            if let s = status { followRow(s) }
            if let st = stats {
                ProfileCells(boulders: Int(st.boulderCount), routes: Int(st.routeCount), maxBoulder: st.maxBoulderGrade,
                             maxRoute: st.maxRouteGrade, onBoulders: { route = .blocks }, onRoutes: { route = .routes },
                             onMax: { route = .stats(1) })
                ProfileMiniTiles(posts: postCount, onPosts: { route = .posts }, schools: Int(st.schoolCount),
                                 onSchools: { route = .schools }, projects: 0, onProjects: nil,
                                 boards: gymsOn ? Int(GymBreakdown.shared.boardCount(items: climb.summary.items)) : nil,
                                 onBoards: gymsOn ? { route = .boards } : nil)
            }
            if gymsOn {
                ClimbGradientCard(climb: climb, onStats: { route = .stats(0) }, onRock: { route = .stats(1) }, onGym: { route = .stats(2) })
                HomeGymAndStreak(home: climb.homeGym, streakWeeks: 0, own: false, onOpenGym: { route = .gym($0) }) { EmptyView() }
            }
        }
        .task(id: realUid) {
            if gymsOn { await climb.load(uid: realUid) }
            postCount = (try? await AppDependencies.shared.container.getFeedPostCount.invoke(uid: realUid)).map { Int(truncating: $0) }
        }
        .navigationDestination(item: $route) { r in destination(r) }
        .confirmationDialog(L("public_profile_unfollow_title", displayName), isPresented: $confirmUnfollow, titleVisibility: .visible) {
            Button(L("public_profile_unfollow_confirm"), role: .destructive) { onToggleFollow() }
            Button(L("common_cancel"), role: .cancel) {}
        }
    }

    /// «Te sigue» (izquierda) y la píldora compacta «Siguiendo» / «Seguir» (derecha), en la misma fila de 32 pt.
    private func followRow(_ s: FollowStatus) -> some View {
        HStack {
            if s.theyFollowMe {
                Label(L("TE SIGUE"), systemImage: "person.fill.checkmark")
                    .font(Cumbre.mono(9.5, .bold)).foregroundStyle(Cumbre.ok)
                    .padding(.horizontal, 9).padding(.vertical, 5)
                    .background(Cumbre.ok.opacity(0.10), in: Capsule())
            }
            Spacer()
            let following = s.iFollowThem
            let pending = s.requestPending
            Button { if following { confirmUnfollow = true } else { onToggleFollow() } } label: {
                Text(pending ? L("profile_requested") : (following ? L("SIGUIENDO") : L("profile_follow")))
                    .font(Cumbre.mono(11, .bold)).lineLimit(1)
                    .foregroundStyle(following || pending ? Cumbre.ink2 : .white)
                    .padding(.horizontal, 16).frame(height: 32)
                    .background(following || pending ? Cumbre.paper : Cumbre.terraFill, in: Capsule())
                    .overlay(Capsule().stroke(following || pending ? Cumbre.rule : Color.clear, lineWidth: 1))
            }
            .buttonStyle(.plain).disabled(pending)
        }
    }

    @ViewBuilder private func destination(_ r: OtherProfileRoute) -> some View {
        switch r {
        case .blocks: JournalBlocksListView(title: L("Bloques"), entries: entries, viaInfo: viaInfo, routeOnly: false)
        case .routes: JournalBlocksListView(title: L("Vías"), entries: entries, viaInfo: viaInfo, routeOnly: true)
        case .schools: JournalSchoolsView(schools: stats?.bySchool ?? [], entries: entries, viaInfo: viaInfo)
        case .posts: MyPostsView(uid: realUid)
        case .boards: GymBoardsView(uid: realUid)
        case .followers: FollowListView(uid: realUid, mode: .followers)
        case .following: FollowListView(uid: realUid, mode: .following)
        case .stats(let tab):
            if tab == 1 || !flags.flags.isGymsEnabled { StatsView(uid: realUid) } else { StatsHubView(uid: realUid, initialTab: tab) }
        case .gym(let id): GymDetailView(gymId: id)
        }
    }
}
