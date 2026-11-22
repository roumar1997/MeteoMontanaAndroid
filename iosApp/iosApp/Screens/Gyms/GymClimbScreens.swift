import SwiftUI
import Shared

/// Pantallas a las que se llega desde el perfil (propio o ajeno) y Estadísticas.
enum ClimbRoute: Hashable {
    case ascents
    case boards
    case visit(Int64)
    case gym(Int64)
}

/// Cabecera de las pantallas del perfil: volver y título serif, con algo a la derecha.
struct ClimbHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: () -> Trailing
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        HStack(spacing: 4) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold)).foregroundStyle(Cumbre.ink).frame(width: 44, height: 44)
            }
            .buttonStyle(.plain).accessibilityLabel(L("common_back"))
            Text(title).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .padding(.horizontal, 8).padding(.vertical, 6)
    }
}

/// Estadísticas con pestañas Todo · Roca · Rocódromo. Roca es la pantalla de siempre (StatsView).
struct StatsHubView: View {
    let uid: String?
    var initialTab = 0
    @StateObject private var climb = ProfileClimbModel()
    @ObservedObject private var flags = FeatureFlagsModel.shared
    @State private var tab = 0
    @State private var route: ClimbRoute?

    var body: some View {
        VStack(spacing: 0) {
            ClimbHeader(title: L("stats_hub_title")) { EmptyView() }
            if flags.flags.isGymsEnabled {
                GymSegmented(options: [L("stats_hub_tab_all"), L("stats_hub_tab_rock"), L("stats_hub_tab_gym")], selected: tab) { tab = $0 }
                    .padding(.horizontal, 16).padding(.vertical, 4)
            }
            if !flags.flags.isGymsEnabled || tab == 1 {
                StatsView(uid: uid)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        if tab == 0 { allTab } else { gymTab }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8).padding(.bottom, 24)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $route) { r in ClimbRouteView(route: r, uid: uid) }
        .onAppear { tab = initialTab }
        .task { await climb.load(uid: uid) }
    }

    @ViewBuilder private var allTab: some View {
        ClimbGradientCard(climb: climb, onStats: {}, onRock: { tab = 1 }, onGym: { tab = 2 })
        if uid == nil {
            NavigationLink(destination: StatsSchoolsDestination()) {
                ProfileLinkRowFace(symbol: "mountain.2", tint: Cumbre.ok, label: L("stats_hub_open_schools"),
                                   count: Int(climb.stats?.schoolCount ?? 0) > 0 ? Int(climb.stats?.schoolCount ?? 0) : nil)
            }
            .buttonStyle(.plain)
            NavigationLink(destination: ProjectsView()) {
                ProfileLinkRowFace(symbol: "flag", tint: Cumbre.terraFill, label: L("stats_hub_open_projects"), count: nil)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder private var gymTab: some View {
        if climb.summary.isEmpty {
            Text(L("stats_hub_gym_empty")).foregroundStyle(Cumbre.ink2).frame(maxWidth: .infinity).padding(.top, 40)
        } else {
            ClimbGradientCard(climb: climb, onStats: {}, onRock: { tab = 1 }, onGym: {})
            ProfileLinkRow(symbol: "arrow.up.arrow.down", tint: ProfileTint.blue, label: L("gym_asc_see_all"),
                           count: Int(climb.summary.ascents)) { route = .ascents }
            ByGymSection(gyms: climb.byGym) { route = .visit($0) }
            ByGradeSection(items: climb.summary.items, colorHex: { climb.colorHexFor(gymId: -1, key: $0) })
            ByMonthSection(summary: climb.summary)
        }
    }
}

/// La lista de escuelas del diario propio (la de siempre).
private struct StatsSchoolsDestination: View {
    @StateObject private var vm = AccountViewModel()
    var body: some View { AccountSchoolsList(vm: vm).task { await vm.load() } }
}

/// Lo de `ProfileLinkRow` sin botón, para usarlo dentro de un NavigationLink.
struct ProfileLinkRowFace: View {
    let symbol: String
    let tint: Color
    let label: String
    let count: Int?
    var body: some View {
        ProfileLinkRow(symbol: symbol, tint: tint, label: label, count: count) {}.allowsHitTesting(false)
    }
}

/// «Ascensiones · N»: Todo · Bloques · Vías · Boards (en el orden elegido), agrupado por rocódromo con SU escala.
struct GymAscentsView: View {
    let uid: String?
    @StateObject private var climb = ProfileClimbModel()
    @State private var filter: AscentFilter = .all
    @State private var grades = Set<String>()
    @State private var reordering = false
    @State private var route: ClimbRoute?

    var body: some View {
        let s = climb.summary
        let order = AscentTabOrder.shared.parse(saved: climb.ascentOrder)
        let tabs = AscentTabOrder.shared.tabs(order: order)
        let items = filter == .all ? GymBreakdown.shared.withoutBoards(items: s.items) : GymBreakdown.shared.filter(items: s.items, filter: filter)
        VStack(spacing: 0) {
            ClimbHeader(title: L("gym_asc_title", s.ascents)) {
                if uid == nil { OrderPill { reordering = true }.padding(.trailing, 8) }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    GymSegmented(options: tabs.map { Self.label($0) }, selected: tabs.firstIndex(of: filter) ?? 0) {
                        filter = tabs[$0]
                        grades = []
                    }
                    if s.isEmpty {
                        Text(L("gym_asc_empty")).foregroundStyle(Cumbre.ink2).frame(maxWidth: .infinity).padding(.top, 40)
                    } else {
                        GradeChipRow(grades: AscentEntries.shared.grades(items: items), selected: $grades)
                        if !grades.isEmpty {
                            GradeEntriesList(entries: AscentEntries.shared.entries(summary: s, filter: filter, grades: grades, includeBoards: false)) {
                                route = .visit($0)
                            }
                        } else {
                            content(s, items)
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 6).padding(.bottom, 24)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $route) { r in ClimbRouteView(route: r, uid: uid) }
        .task { await climb.load(uid: uid) }
        .sheet(isPresented: $reordering) { AscentOrderSheet(climb: climb) }
    }

    @ViewBuilder private func content(_ s: GymSummary, _ items: [GymAscent]) -> some View {
        if filter == .boards {
            BoardsSections(summary: s, items: items, climb: climb) { route = .visit($0) }
        } else if GymBreakdown.shared.hasGymBreakdown(summary: s) {
            let gyms = GymOrder.shared.sort(gyms: GymBreakdown.shared.gymsFilteredWithoutBoards(summary: s, filter: filter),
                                            saved: GymOrder.shared.parse(saved: climb.gymOrder),
                                            favorites: Set(climb.favorites.map { KotlinLong(value: $0) }))
            let boards = Int(GymBreakdown.shared.boardCount(items: s.items))
            if gyms.isEmpty && !(filter == .all && boards > 0) { EmptyCategory() } else {
                PerGymSections(gyms: gyms, colorHexFor: { climb.colorHexFor(gymId: $0, key: $1) }, onOpenGym: { route = .visit($0) })
            }
            if filter == .all && boards > 0 {
                Button { route = .boards } label: {
                    HStack(spacing: 10) {
                        Text(L("gym_boards_link")).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(boards)").font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
                        Text("›").font(.system(size: 20)).foregroundStyle(Cumbre.ink2)
                    }
                    .padding(.vertical, 8).contentShape(Rectangle())
                }
                .buttonStyle(.plain).padding(.top, 10)
            }
            if filter == .all { ByMonthSection(summary: s) }
        } else {
            if filter == .all { ByGymSection(gyms: climb.byGym) { route = .visit($0) } }
            ByGradeSection(items: items, colorHex: { climb.colorHexFor(gymId: -1, key: $0) })
            if filter == .all { ByMonthSection(summary: s) }
        }
    }

    static func label(_ f: AscentFilter) -> String {
        switch f {
        case .blocks: return L("gym_asc_tab_blocks")
        case .routes: return L("gym_asc_tab_routes")
        case .boards: return L("gym_asc_tab_boards")
        default: return L("gym_asc_tab_all")
        }
    }
}

/// Destino de una ruta empujada desde una pantalla con `navigationDestination(item:)`.
struct ClimbRouteView: View {
    let route: ClimbRoute
    let uid: String?
    var body: some View {
        switch route {
        case .ascents: GymAscentsView(uid: uid)
        case .boards: GymBoardsView(uid: uid)
        case .visit(let id): GymVisitView(uid: uid, gymId: id)
        case .gym(let id): GymDetailView(gymId: id)
        }
    }
}

/// Pastilla «Ordenar» de la cabecera de Ascensiones.
struct OrderPill: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: "arrow.up.arrow.down").font(.system(size: 13, weight: .semibold))
                Text(L("gym_asc_order_btn")).font(.system(size: 13, weight: .bold)).lineLimit(1)
            }
            .foregroundStyle(Cumbre.terraFill).padding(.horizontal, 12).padding(.vertical, 7)
            .overlay(Capsule().stroke(Cumbre.terraFill, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// «Ordenar»: categorías de Ascensiones y rocódromos, con «Subir» / «Bajar» (grises al no poder más).
struct AscentOrderSheet: View {
    @ObservedObject var climb: ProfileClimbModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let order = AscentTabOrder.shared.parse(saved: climb.ascentOrder)
        let gyms = climb.byGym
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    label(L("gym_order_cats"))
                    Text(L("gym_asc_order_hint")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    ForEach(Array(order.enumerated()), id: \.offset) { i, f in
                        row(i, GymAscentsView.label(f), order.count - 1) { d in
                            climb.setAscentOrder(AscentTabOrder.shared.serialize(order: AscentTabOrder.shared.move(order: order, index: Int32(i), delta: Int32(d))))
                        }
                    }
                    if gyms.count > 1 {
                        label(L("gym_order_gyms"))
                        Text(L("gym_order_gyms_hint")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                        let ids = gyms.map { KotlinLong(value: $0.gymId) }
                        ForEach(Array(gyms.enumerated()), id: \.offset) { i, g in
                            row(i, g.name ?? "-", gyms.count - 1) { d in
                                climb.setGymOrder(GymOrder.shared.move(ids: ids, index: Int32(i), delta: Int32(d)).map { $0.int64Value })
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(L("gym_asc_order_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("common_done")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func label(_ t: String) -> some View {
        Text(t).font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink2).padding(.top, 6)
    }

    private func row(_ i: Int, _ text: String, _ last: Int, _ move: @escaping (Int) -> Void) -> some View {
        HStack(spacing: 0) {
            Text("\(i + 1)").font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.terraFill).padding(.trailing, 10)
            Text(text).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            moveButton("arrow.up", L("gym_asc_order_up"), i > 0) { move(-1) }
            moveButton("arrow.down", L("gym_asc_order_down"), i < last) { move(1) }
        }
        .padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 8).cardChrome(14)
    }

    private func moveButton(_ symbol: String, _ text: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        let c = enabled ? Cumbre.terraFill : Cumbre.ink2.opacity(0.4)
        return Button(action: action) {
            VStack(spacing: 0) {
                Image(systemName: symbol).font(.system(size: 16, weight: .semibold))
                Text(text).font(.system(size: 10, weight: .semibold)).lineLimit(1)
            }
            .foregroundStyle(c).padding(.horizontal, 6).padding(.vertical, 2)
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// «Boards · N»: todos los boards de todos los rocódromos juntos, plegables, con buscador y filtro por grado.
struct GymBoardsView: View {
    let uid: String?
    @StateObject private var climb = ProfileClimbModel()
    @State private var addFor: BoardPick?
    @State private var editFor: BoardEditPick?
    @State private var deleteFor: BoardEditPick?
    @StateObject private var boardEdit = GymTrainSheetViewModel()
    @State private var grades = Set<String>()
    @State private var route: ClimbRoute?

    var body: some View {
        let s = climb.summary
        let items = GymBreakdown.shared.filter(items: s.items, filter: .boards)
        VStack(spacing: 0) {
            ClimbHeader(title: L("gym_boards_title", GymBreakdown.shared.boardCount(items: items))) { EmptyView() }
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    if items.isEmpty && uid != nil {
                        Text(L("gym_boards_empty")).foregroundStyle(Cumbre.ink2).frame(maxWidth: .infinity).padding(.top, 40)
                    } else {
                        if !items.isEmpty { GradeChipRow(grades: AscentEntries.shared.grades(items: items), selected: $grades) }
                        if !grades.isEmpty {
                            GradeEntriesList(entries: AscentEntries.shared.entries(summary: s, filter: .boards, grades: grades, includeBoards: true)) {
                                route = .visit($0)
                            }
                        } else {
                            BoardsSections(summary: s, items: items, climb: climb, onOpenGym: { route = .visit($0) },
                                           onAdd: uid == nil ? { addFor = BoardPick(kind: $0) } : nil,
                                           onEdit: uid == nil ? { editFor = BoardEditPick(entry: $0) } : nil,
                                           onDelete: uid == nil ? { deleteFor = BoardEditPick(entry: $0) } : nil)
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 6).padding(.bottom, 24)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $addFor, onDismiss: { Task { await climb.load(uid: uid) } }, content: { k in
            SoloBoardSheet(dateLabel: GymDates.todayLong(), date: GymDates.local(Date()), board: k.kind)
        })
        .alert(L("gym_board_delete_title"), isPresented: Binding(get: { deleteFor != nil }, set: { if !$0 { deleteFor = nil } })) {
            Button(L("gym_board_delete"), role: .destructive) {
                if let p = deleteFor { boardEdit.deleteEntry(p.entry) { Task { await climb.load(uid: uid) } } }
                deleteFor = nil
            }
            Button(L("common_cancel"), role: .cancel) { deleteFor = nil }
        } message: {
            Text(L("gym_board_delete_body"))
        }
        .sheet(item: $editFor, onDismiss: { Task { await climb.load(uid: uid) } }, content: { p in
            SoloBoardSheet(dateLabel: GymDates.long(GymDates.date(p.entry.day)), date: p.entry.day, board: p.entry.board, edit: p.entry)
        })
        .navigationDestination(item: $route) { r in ClimbRouteView(route: r, uid: uid) }
        .task { await climb.load(uid: uid) }
    }
}

/// Logged block opened to edit or delete (§26).
struct BoardEditPick: Identifiable {
    let entry: BoardEntry
    var id: String { "\(entry.gymId)|\(entry.date)|\(entry.board.name)|\(entry.grade)|\(entry.name ?? "")" }
}

/// Lo que una persona ha hecho EN un rocódromo: totales, reparto con SU escala y días de entreno.
struct GymVisitView: View {
    let uid: String?
    let gymId: Int64
    @StateObject private var climb = ProfileClimbModel()
    @State private var openGym = false

    var body: some View {
        let g = climb.summary.byGym.first { $0.gymId == gymId }
        VStack(spacing: 0) {
            ClimbHeader(title: g?.name ?? climb.gym(gymId)?.name ?? "—") { EmptyView() }
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let g {
                        visit(GymBreakdown.shared.visitWithoutBoards(g: g))
                    } else if climb.loaded {
                        Text(L("gym_visit_empty")).foregroundStyle(Cumbre.ink2).multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity).padding(.top, 40)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 6).padding(.bottom, 24)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $openGym) { GymDetailView(gymId: gymId) }
        .task { await climb.load(uid: uid) }
    }

    @ViewBuilder private func visit(_ g: GymSummaryGym) -> some View {
        HStack(spacing: 8) {
            cell("\(g.sessions)", L("gym_visit_sessions"))
            cell("\(g.ascents)", L("gym_visit_ascents"))
            cell(g.maxGrade?.uppercased() ?? "—", L("gym_visit_max"))
        }
        Button { openGym = true } label: {
            Text(L("gym_visit_open_gym")).font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.terraFill)
                .frame(maxWidth: .infinity).padding(.vertical, 11).overlay(Capsule().stroke(Cumbre.terraFill, lineWidth: 1))
        }
        .buttonStyle(.plain)
        ByGradeSection(items: g.items, colorHex: { climb.colorHexFor(gymId: gymId, key: $0) })
        if !g.days.isEmpty {
            GymSectionTitle(text: L("gym_visit_days"))
            GymCardBox {
                ForEach(g.days, id: \.date) { d in
                    HStack {
                        Text(GymDayText.long(d.date)).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(d.ascents > 0 ? LP("gym_visit_day_count", Int(d.ascents)) : L("gym_visit_day_none"))
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                    }
                }
            }
        }
    }

    private func cell(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1).minimumScaleFactor(0.6)
            Text(label.uppercased()).font(.system(size: 10)).foregroundStyle(Cumbre.ink2).lineLimit(1)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10).padding(.horizontal, 4).cardChrome(16)
    }
}
