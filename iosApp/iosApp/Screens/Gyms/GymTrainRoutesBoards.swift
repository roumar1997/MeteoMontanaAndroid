import SwiftUI
import Shared

/// Colores de marca de los boards (los mismos de Android).
enum BoardStyle {
    static let kilterBlack = Color(hex: 0x1B1B1B)
    static let kilterBlue = Color(hex: 0x2B7FFF)
    static let tensionWood = Color(hex: 0xC79A63)
    static let moonYellow = Color(hex: 0xF2C21A)
    static let systemGreen = Color(hex: 0x3E8E6B)

    static func color(_ b: BoardKind) -> Color {
        switch b {
        case .kilter: return kilterBlue
        case .tension: return tensionWood
        case .moonboard: return moonYellow
        default: return systemGreen
        }
    }

    static func name(_ b: BoardKind) -> String {
        switch b {
        case .kilter: return L("gym_board_kilter")
        case .tension: return L("gym_board_tension")
        case .moonboard: return L("gym_board_moon")
        default: return L("gym_board_system")
        }
    }

    static let all: [BoardKind] = [.kilter, .tension, .moonboard, .system]
}

/// Logo-color de cada board: Kilter negro con celeste, Tension madera, Moonboard amarillo, System verde.
struct BoardLogo: View {
    let board: BoardKind
    var size: CGFloat = 34
    var body: some View {
        let base: Color = board == .kilter ? BoardStyle.kilterBlack : BoardStyle.color(board)
        RoundedRectangle(cornerRadius: size * 0.26).fill(base).frame(width: size, height: size)
            .overlay { if board == .kilter { Circle().fill(Color(hex: 0x58B7FF)).frame(width: size * 0.41, height: size * 0.41) } }
    }
}

/// Eyebrow de las hojas de entreno.
struct GymEyebrow: View {
    let text: String
    var body: some View { Text(text.uppercased()).eyebrow(Cumbre.ink2).padding(.top, 4) }
}

/// Pestaña «Vías»: lo añadido, la rejilla de grados (uno, o dos contiguos = doble), cómo se hizo y «Añadir».
struct GymTrainRoutesTab: View {
    let ascents: [GymAscent]
    let adjust: (String, Int) -> Void
    @State private var selected: [String] = []
    @State private var partner = true
    @State private var project = false

    var body: some View {
        GymAddedList(list: GymAscentKeys.shared.routes(list: ascents), names: [:], adjust: adjust)
        GymEyebrow(text: L("gym_sheet_grade_pick"))
        GymGradeGrid(selected: selected) { selected = GymAscentKeys.shared.toggleGrade(selected: selected, tapped: $0) }
        HStack(spacing: 8) {
            GymStyleCard(icon: "gym_ic_style_partner", title: L("gym_style_partner"), sub: L("gym_style_partner_sub"),
                         selected: partner) { partner = true }
            GymStyleCard(icon: "gym_ic_autobelay", title: L("gym_style_solo"), sub: L("gym_style_solo_sub"),
                         selected: !partner) { partner = false }
        }
        GymSegmented(options: [L("gym_flow_sent"), L("gym_flow_project")], selected: project ? 1 : 0) { project = $0 == 1 }
        let grade = GymAscentKeys.shared.gradeOf(selected: selected)
        GymAddButton(grade: grade) {
            guard let grade else { return }
            adjust(GymAscentKeys.shared.route(style: partner ? .partner : .solo, project: project, grade: grade), 1)
            selected = []
        }
    }
}

/// Pestaña «Boards»: el board, la inclinación, el grado, el nombre opcional y lo ya añadido.
struct GymTrainBoardsTab: View {
    let ascents: [GymAscent]
    let problems: [BoardProblem]
    let adjust: (String, Int) -> Void
    let addBoard: (BoardKind, String, Int?, String) -> Void
    /// Tablero fijado (el «+» de la pantalla Boards): sin selector de marca.
    var fixedBoard: BoardKind?
    @State private var board: BoardKind = .kilter
    @State private var angle: Int?
    @State private var started = false
    @State private var selected: [String] = []
    @State private var name = ""

    var body: some View {
        let items = ascents.filter { GymAscentKeys.shared.parse(key: $0.key) is AscentKeyBoard }
        GymAddedList(list: items, names: BoardProblems.shared.namesByKey(items: items, problems: problems), adjust: adjust)
        if fixedBoard == nil {
        HStack(spacing: 8) {
            ForEach(BoardStyle.all, id: \.self) { b in
                let sel = b == board
                Button { board = b; angle = BoardAngleStore.memory.initial(board: b)?.intValue } label: {
                    VStack(spacing: 5) {
                        BoardLogo(board: b)
                        Text(BoardStyle.name(b)).font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(sel ? BoardStyle.color(b).opacity(0.16) : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(sel ? BoardStyle.color(b) : Cumbre.rule, lineWidth: sel ? 2 : 1))
                }
                .buttonStyle(.plain)
            }
        }
        }
        let angles = BoardAngles.shared.anglesFor(board: board).map { Int(truncating: $0) }
        if !angles.isEmpty { GymEyebrow(text: L("gym_sheet_incline")) }
        FlowLayoutView(spacing: 6) {
            ForEach(angles, id: \.self) { a in
                let sel = angle == a
                Button { angle = sel ? nil : a; BoardAngleStore.remember(board, angle) } label: {
                    Text("\(a)°").font(.system(size: 12, weight: .bold)).foregroundStyle(sel ? .white : Cumbre.ink)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(sel ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        GymEyebrow(text: L("gym_sheet_grade_pick"))
        GymGradeGrid(selected: selected) { selected = GymAscentKeys.shared.toggleGrade(selected: selected, tapped: $0) }
        VStack(alignment: .leading, spacing: 3) {
            TextField(L("gym_board_name_hint"), text: $name)
                .font(.system(size: 15)).padding(12)
                .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: name) { _, new in
                    let max = Int(BoardProblems.shared.MAX_NAME)
                    if new.count > max { name = String(new.prefix(max)) }
                }
            Text("\(name.count)/\(BoardProblems.shared.MAX_NAME)").font(.system(size: 11)).foregroundStyle(Cumbre.ink2).padding(.leading, 12)
        }
        let grade = GymAscentKeys.shared.gradeOf(selected: selected)
        GymAddButton(grade: grade) {
            guard let grade else { return }
            addBoard(board, grade, angle, name)
            BoardAngleStore.remember(board, angle)
            selected = []
            name = ""
        }
        Text(L("gym_sheet_board_note")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            .onAppear {
                guard !started else { return }
                started = true
                if let fixedBoard { board = fixedBoard }
                angle = BoardAngleStore.memory.initial(board: board)?.intValue
            }
    }
}

/// Rejilla de todos los grados con su color; los elegidos (uno o dos contiguos) llevan aro terracota.
struct GymGradeGrid: View {
    let selected: [String]
    let onTap: (String) -> Void
    var body: some View {
        FlowLayoutView(spacing: 6) {
            ForEach(GymAscentKeys.shared.GRID_GRADES, id: \.self) { g in
                let st = GradeColor.chipStyle(g)
                let sel = selected.contains(g)
                Button { onTap(g) } label: {
                    Text(g.uppercased()).font(.system(size: 13, weight: .bold)).lineLimit(1)
                        .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white)
                        .frame(width: 46, height: 36)
                        .background(st.stroke, in: RoundedRectangle(cornerRadius: 11))
                        .overlay(RoundedRectangle(cornerRadius: 11).stroke(sel ? Cumbre.terra : Color.clear, lineWidth: 3))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Botón discontinuo «+ AÑADIR 6B+/6C»; sin grado elegido queda apagado.
struct GymAddButton: View {
    let grade: String?
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text("+  " + (grade.map { L("gym_add_grade", $0.uppercased()) } ?? L("gym_add_pick")).uppercased())
                .font(.system(size: 12, weight: .bold)).multilineTextAlignment(.center)
                .foregroundStyle(Cumbre.terra.opacity(grade != nil ? 1 : 0.5))
                .frame(maxWidth: .infinity).padding(.vertical, 13)
                .overlay(RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Cumbre.terra.opacity(grade != nil ? 1 : 0.5), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4])))
        }
        .buttonStyle(.plain)
        .disabled(grade == nil)
    }
}

/// Tarjeta de elección (con compañero / autoaseguro).
struct GymStyleCard: View {
    let icon: String
    let title: String
    let sub: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 24, height: 24)
                    .foregroundStyle(selected ? Cumbre.terraFill : Cumbre.ink2)
                Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink).multilineTextAlignment(.center)
                Text(sub).font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 10).padding(.horizontal, 6)
            .background(selected ? Cumbre.terraFill.opacity(0.14) : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Cumbre.terraFill : Cumbre.rule, lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

/// Lo añadido en esta pestaña: grado con su color, qué es, cuántas veces y la papelera que quita la fila entera.
struct GymAddedList: View {
    let list: [GymAscent]
    let names: [String: [String]]
    let adjust: (String, Int) -> Void

    var body: some View {
        if !list.isEmpty {
            GymEyebrow(text: L("gym_added_today", list.reduce(0) { $0 + Int($1.count) }))
            ForEach(list, id: \.key) { a in
                if let d = describe(a) { row(a, d) }
            }
        }
    }

    private func row(_ a: GymAscent, _ d: (grade: String, title: String, sub: String)) -> some View {
        let st = GradeColor.chipStyle(GymAscentKeys.shared.baseGrade(grade: d.grade))
        return HStack(spacing: 10) {
            Text(d.grade.uppercased()).font(.system(size: 13, weight: .bold)).lineLimit(1)
                .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white)
                .padding(.horizontal, 10).padding(.vertical, 6).background(st.stroke, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 1) {
                Text(d.title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                Text(([d.sub] + (names[a.key] ?? [])).joined(separator: " · ")).font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text("×\(a.count)").font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
            Button { adjust(a.key, -Int(a.count)) } label: {
                Image(systemName: "trash").font(.system(size: 15)).foregroundStyle(Cumbre.ink2).frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("gym_remove"))
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func describe(_ a: GymAscent) -> (grade: String, title: String, sub: String)? {
        let k = GymAscentKeys.shared.parse(key: a.key)
        if let r = k as? AscentKeyRoute {
            let style = L(r.style == .partner ? "gym_style_partner" : "gym_style_solo")
            let flow = L(r.project ? "gym_flow_project" : "gym_flow_sent")
            return (r.grade, L(r.grade.contains("/") ? "gym_route_double" : "gym_route_single"), "\(flow) · \(style)")
        }
        if let b = k as? AscentKeyBoard {
            return (b.grade, L("gym_board_row", BoardStyle.name(b.board)), b.angle.map { "\($0)°" } ?? L("gym_board_no_angle"))
        }
        return nil
    }
}
