import SwiftUI
import Shared

/// Destinos del perfil propio (celdas, fichas, tarjeta).
enum OwnProfileRoute: Hashable {
    case blocks, routes, schools, projects, posts, boards
    case stats(Int)
    case gym(Int64)
}

/// Cuerpo del perfil propio (maqueta 4, ronda 7): 4 celdas, fichas Publicaciones · Escuelas · Proyectos · Boards, tarjeta
/// de degradado (mitad Roca → Estadísticas/Roca, mitad Rocódromo → Estadísticas/Rocódromo) y fila «Tu rocódromo» +
/// «semanas seguidas» + «+ Entreno». Espejo de `Content` en ProfileScreen.kt.
struct AccountProfileBody: View {
    @ObservedObject var vm: AccountViewModel
    @ObservedObject var climb: ProfileClimbModel
    @ObservedObject private var flags = FeatureFlagsModel.shared
    @State private var route: OwnProfileRoute?
    @State private var postCount: Int?
    @State private var pickingHome = false

    var body: some View {
        let s = vm.stats
        let gymsOn = flags.flags.isGymsEnabled
        VStack(spacing: 8) {
            ProfileCells(boulders: Int(s?.boulderCount ?? 0), routes: Int(s?.routeCount ?? 0),
                         maxBoulder: s?.maxBoulderGrade, maxRoute: s?.maxRouteGrade,
                         onBoulders: { route = .blocks }, onRoutes: { route = .routes }, onMax: { route = .stats(1) })
            ProfileMiniTiles(posts: postCount, onPosts: { route = .posts }, schools: Int(s?.schoolCount ?? 0),
                             onSchools: { route = .schools }, projects: Int(s?.projectCount ?? 0), onProjects: { route = .projects },
                             boards: gymsOn ? Int(GymBreakdown.shared.boardCount(items: climb.summary.items)) : nil,
                             onBoards: gymsOn ? { route = .boards } : nil)
            if gymsOn {
                ClimbGradientCard(climb: climb, onStats: { route = .stats(0) }, onRock: { route = .stats(1) }, onGym: { route = .stats(2) })
                    .guideTarget("profile.stats")
                HomeGymAndStreak(home: climb.homeGym, streakWeeks: climb.streakWeeks, own: true,
                                 onOpenGym: { route = .gym($0) }, onPick: { pickingHome = true }, trailing: {
                    LogWorkoutPill { Task { await climb.load(uid: nil) } }
                })
            } else {
                Button { route = .stats(1) } label: { StatsEntryLabel() }.buttonStyle(.plain).guideTarget("profile.stats")
            }
        }
        .guideTarget("profile.diary")
        .task { postCount = (try? await AppDependencies.shared.container.getFeedPostCount.invoke(uid: nil)).map { Int(truncating: $0) } }
        .sheet(isPresented: $pickingHome) {
            GymPickerSheet { g, _ in
                pickingHome = false
                climb.setHomeGym(g)
            }
        }
        .navigationDestination(item: $route) { r in destination(r) }
    }

    @ViewBuilder private func destination(_ r: OwnProfileRoute) -> some View {
        switch r {
        case .blocks: AccountBlocksList(vm: vm, routeOnly: false)
        case .routes: AccountBlocksList(vm: vm, routeOnly: true)
        case .schools: AccountSchoolsList(vm: vm)
        case .projects: ProjectsView()
        case .posts: MyPostsView()
        case .boards: GymBoardsView(uid: nil)
        case .stats(let tab): StatsHubView(uid: nil, initialTab: tab)
        case .gym(let id): GymDetailView(gymId: id)
        }
    }
}
