import SwiftUI
import Shared

// Bloques de las pantallas de estadísticas de rocódromo (maqueta 4C): por rocódromo, por grado, por mes, desglose por
// rocódromo y por tablero, filtro por grado y bloques de board con nombre. Espejo de GymStatsParts.kt,
// GymBreakdownParts.kt, AscentGradeFilter.kt y BoardProblemsList.kt. Los cálculos salen de shared.

struct GymSectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased()).eyebrow(Cumbre.ink2).padding(.top, 6).padding(.bottom, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct GymCardBox<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content() }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading).cardChrome(18)
    }
}

/// Barra con relleno proporcional (0 a 1) sobre una pista gris.
struct GymBar: View {
    let fraction: Double
    let color: Color
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Cumbre.rule.opacity(0.45))
                Capsule().fill(color).frame(width: geo.size.width * min(1, max(0.03, fraction)))
            }
        }
        .frame(height: 9)
    }
}

/// Inicial del rocódromo en un círculo terracota.
struct GymInitial: View {
    let name: String?
    var size: CGFloat = 28
    var body: some View {
        Circle().fill(Cumbre.terraFill).frame(width: size, height: size)
            .overlay(Text(String((name ?? "?").prefix(1)).uppercased()).font(Cumbre.serif(size * 0.46, .bold)).foregroundStyle(.white))
    }
}

/// Rocódromos de la persona: inicial, nombre, barra por ascensiones, cuántas y su grado máximo con color.
struct ByGymSection: View {
    let gyms: [GymSummaryGym]
    var onOpenGym: ((Int64) -> Void)?
    var body: some View {
        if !gyms.isEmpty {
            let top = Double(gyms.map { max(1, Int($0.ascents)) }.max() ?? 1)
            GymSectionTitle(text: L("gym_by_gym"))
            GymCardBox {
                ForEach(Array(gyms.prefix(8)), id: \.gymId) { g in
                    Button { onOpenGym?(g.gymId) } label: {
                        HStack(spacing: 10) {
                            GymInitial(name: g.name)
                            Text(g.name ?? "—").font(.system(size: 13)).foregroundStyle(Cumbre.ink).lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            GymBar(fraction: Double(g.ascents) / top, color: Cumbre.terra).frame(maxWidth: .infinity)
                            Text("\(g.ascents)").font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink)
                            MaxGradePill(grade: g.maxGrade)
                        }
                    }
                    .buttonStyle(.plain).disabled(onOpenGym == nil)
                }
            }
        }
    }
}

/// Ascensiones por grado (paleta de grados) y, aparte, por color de escala.
struct ByGradeSection: View {
    let items: [GymAscent]
    let colorHex: (String) -> String?
    var titled = true

    var body: some View {
        let grades = GymBreakdown.shared.byGrade(items: items).compactMap { p -> (String, Int)? in
            guard let g = p.first as? String, let n = p.second else { return nil }
            return (g, Int(truncating: n))
        }
        let colors = GymBreakdown.shared.byColor(items: items)
        if !grades.isEmpty || !colors.isEmpty {
            let top = Double(max(1, (grades.map { $0.1 } + colors.map { Int($0.count) }).max() ?? 1))
            if titled { GymSectionTitle(text: L("gym_by_grade")) }
            GymCardBox {
                ForEach(grades, id: \.0) { g, n in
                    let st = GradeColor.chipStyle(g)
                    row(g.uppercased(), st.stroke, st.dark ? Color(hex: 0x1B1B1B) : .white, n, top)
                }
                ForEach(colors, id: \.key) { a in
                    let hex = colorHex(a.key)
                    let c = hex.map { GymPresentation.color(hex: $0) } ?? Cumbre.ink2
                    row(a.key.capitalized, c, ["#FFFFFF", "#F2C21A"].contains(hex?.uppercased() ?? "") ? Color(hex: 0x1B1B1B) : .white,
                        Int(a.count), top)
                }
            }
        }
    }

    private func row(_ label: String, _ color: Color, _ on: Color, _ n: Int, _ top: Double) -> some View {
        HStack(spacing: 10) {
            Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(on).lineLimit(1).minimumScaleFactor(0.7)
                .frame(width: 64).padding(.vertical, 3).background(color, in: RoundedRectangle(cornerRadius: 9))
            GymBar(fraction: Double(n) / top, color: color)
            Text("\(n)").font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink).frame(minWidth: 28, alignment: .leading)
        }
    }
}

/// Barras de los últimos meses con el nombre corto del mes debajo.
struct ByMonthSection: View {
    let summary: GymSummary
    var body: some View {
        let months = summary.byMonth
        if months.contains(where: { $0.ascents > 0 }) {
            let top = Double(max(1, months.map { Int($0.ascents) }.max() ?? 1))
            GymSectionTitle(text: L("gym_by_month"))
            GymCardBox {
                HStack(alignment: .bottom, spacing: 5) {
                    ForEach(months, id: \.month) { m in
                        GeometryReader { geo in
                            VStack {
                                Spacer(minLength: 0)
                                UnevenRoundedRectangle(topLeadingRadius: 6, topTrailingRadius: 6)
                                    .fill(m.ascents > 0 ? Cumbre.terraFill : Cumbre.rule.opacity(0.4))
                                    .frame(height: geo.size.height * max(0.03, Double(m.ascents) / top))
                            }
                        }
                    }
                }
                .frame(height: 110)
                HStack(spacing: 5) {
                    ForEach(months, id: \.month) { m in
                        Text(Self.monthLabel(m.month)).font(.system(size: 8)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    static func monthLabel(_ ym: String) -> String {
        guard let n = Int(ym.suffix(2)), (1...12).contains(n) else { return "" }
        let f = DateFormatter()
        f.locale = LanguageManager.shared.locale
        return String(f.shortMonthSymbols[n - 1].replacingOccurrences(of: ".", with: "").prefix(3)).uppercased()
    }
}

/// Una sección por rocódromo: cabecera (inicial, nombre, cuántas, flecha) y su reparto con SU escala.
struct PerGymSections: View {
    let gyms: [GymSummaryGym]
    let colorHexFor: (Int64, String) -> String?
    let onOpenGym: (Int64) -> Void
    var body: some View {
        ForEach(gyms, id: \.gymId) { g in
            VStack(alignment: .leading, spacing: 6) {
                Button { onOpenGym(g.gymId) } label: {
                    HStack(spacing: 10) {
                        GymInitial(name: g.name)
                        Text(g.name ?? "—").font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(g.ascents)").font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
                        Text("›").font(.system(size: 20)).foregroundStyle(Cumbre.ink2)
                    }
                    .padding(.vertical, 4).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                ByGradeSection(items: g.items, colorHex: { colorHexFor(g.gymId, $0) }, titled: false)
            }
            .padding(.top, 6)
        }
    }
}

struct EmptyCategory: View {
    var body: some View {
        Text(L("gym_asc_empty_category")).foregroundStyle(Cumbre.ink2).frame(maxWidth: .infinity).padding(.top, 32)
    }
}

/// Boards agrupados por tablero (logo, nombre, grado máximo y total), plegables; dentro, nombres y reparto por grado.
struct BoardsSections: View {
    let summary: GymSummary
    let items: [GymAscent]
    @ObservedObject var climb: ProfileClimbModel
    let onOpenGym: (Int64) -> Void
    /// Perfil propio: cada tablero lleva un «+» y salen los 4 aunque estén vacíos.
    var onAdd: ((BoardKind) -> Void)?
    @State private var angles: [String: Int] = [:]

    var body: some View {
        let groups: [(BoardKind, [GymAscent])] = BoardStyle.all.compactMap { k in
            let list = GymAscentKeys.shared.boards(list: items, board: k)
            return list.isEmpty && onAdd == nil ? nil : (k, list)
        }
        if groups.isEmpty { EmptyCategory() }
        ForEach(groups, id: \.0) { kind, all in
            Group {
                let angle = angles[kind.name]
                let list = BoardAngles.shared.filter(items: all, board: kind, angle: angle.map { KotlinInt(int: Int32($0)) })
                let open = climb.expandedBoards.contains(kind.name)
                VStack(alignment: .leading, spacing: 6) {
                    Button { climb.toggleBoard(kind) } label: {
                        HStack(spacing: 10) {
                            BoardLogo(board: kind)
                            Text(BoardStyle.name(kind)).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            if let max = GymBreakdown.shared.maxGrade(items: list) { MaxGradePill(grade: max) }
                            Text("\(list.reduce(0) { $0 + Int($1.count) })").font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
                            if let onAdd {
                                Button { onAdd(kind) } label: {
                                    Image(systemName: "plus").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                                        .frame(width: 30, height: 30).background(Cumbre.terraFill, in: Circle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(L("gym_board_add_cd", BoardStyle.name(kind)))
                            }
                            Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Cumbre.ink2)
                        }
                        .padding(.vertical, 4).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("gym_board_toggle_cd"))
                    if open {
                        BoardAngleFilter(kind: kind, list: all, selected: Binding(get: { angles[kind.name] }, set: { angles[kind.name] = $0 }))
                        BoardNamedSection(kind: kind, summary: summary, onOpenGym: onOpenGym, boardItems: all, angle: angle)
                        ByGradeSection(items: list, colorHex: { _ in nil }, titled: false)
                    }
                }
                .padding(.top, 6)
            }
        }
    }
}

/// Buscador + lista de bloques con nombre de UN tablero (la búsqueda sale de shared: BoardProblems.search).
struct BoardNamedSection: View {
    let kind: BoardKind
    let summary: GymSummary
    let onOpenGym: (Int64) -> Void
    var boardItems: [GymAscent] = []
    var angle: Int?
    @State private var query = ""

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink2)
            TextField(L("gym_board_search_hint", BoardStyle.name(kind)), text: $query).font(.system(size: 14))
        }
        .padding(12)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        let pool = BoardAngles.shared.problemsAt(problems: summary.boardProblems, items: boardItems, board: kind,
                                                 angle: angle.map { KotlinInt(int: Int32($0)) })
        let found = BoardProblems.shared.search(all: pool, board: kind, query: query)
        if found.isEmpty {
            if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(L("gym_board_search_empty")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(.vertical, 6)
            }
        } else {
            Text(L("gym_board_named_title", found.count).uppercased()).eyebrow(Cumbre.ink2).padding(.top, 4)
            ForEach(Array(found.enumerated()), id: \.offset) { _, p in
                let gymName = summary.byGym.first { $0.gymId == p.gymId }?.name
                Button { onOpenGym(p.gymId) } label: {
                    HStack(spacing: 12) {
                        GymGradeBadge(grade: p.problem.grade)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(p.problem.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                            Text([gymName, GymDayText.long(p.date)].compactMap { $0 }.joined(separator: " · "))
                                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10).cardChrome(14)
                }
                .buttonStyle(.plain)
                .disabled(p.gymId == GymSession.Companion.shared.NO_GYM)
            }
        }
    }
}

/// Grado en píldora con su color (dobles por el primero).
struct GymGradeBadge: View {
    let grade: String
    var body: some View {
        let st = GradeColor.chipStyle(String(grade.split(separator: "/").first ?? Substring(grade)))
        Text(grade.uppercased()).font(.system(size: 13, weight: .bold)).lineLimit(1)
            .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white)
            .padding(.horizontal, 10).padding(.vertical, 4).background(st.stroke, in: RoundedRectangle(cornerRadius: 9))
    }
}

/// «mié 18 nov 2026» en el idioma activo; si no se entiende, el texto tal cual.
enum GymDayText {
    static func long(_ iso: String) -> String {
        let p = DateFormatter()
        p.dateFormat = "yyyy-MM-dd"
        guard let d = p.date(from: String(iso.prefix(10))) else { return iso }
        let f = DateFormatter()
        f.locale = LanguageManager.shared.locale
        f.dateFormat = "EEE d MMM yyyy"
        return f.string(from: d).replacingOccurrences(of: ".", with: "")
    }
}

/// Fila de chips de grado (varios a la vez); «Todos» limpia la elección.
struct GradeChipRow: View {
    let grades: [String]
    @Binding var selected: Set<String>
    var body: some View {
        if !grades.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip(L("gym_grade_all"), selected.isEmpty, Cumbre.terraFill, .white) { selected = [] }
                    ForEach(grades, id: \.self) { g in
                        let st = GradeColor.chipStyle(g)
                        chip(g.uppercased(), selected.contains(g), st.stroke, st.dark ? Color(hex: 0x1B1B1B) : .white) {
                            if selected.contains(g) { selected.remove(g) } else { selected.insert(g) }
                        }
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }

    private func chip(_ label: String, _ active: Bool, _ color: Color, _ on: Color, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 13, weight: .bold)).lineLimit(1).foregroundStyle(active ? on : Cumbre.ink)
                .padding(.horizontal, 14).padding(.vertical, 6)
                .background(active ? color : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(color, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

/// Las ascensiones sueltas de los grados elegidos: grado, qué fue, dónde y cuándo (o «Antes»).
struct GradeEntriesList: View {
    let entries: [AscentEntry]
    let onOpenGym: (Int64) -> Void
    var body: some View {
        if entries.isEmpty { EmptyCategory() }
        ForEach(Array(entries.enumerated()), id: \.offset) { _, e in
            Button { onOpenGym(e.gymId) } label: {
                HStack(spacing: 12) {
                    GymGradeBadge(grade: GymAscentKeys.shared.gradeOfKey(key: e.key) ?? "")
                    VStack(alignment: .leading, spacing: 1) {
                        let kind = Self.kind(e.key)
                        Text(e.names.isEmpty ? kind : kind + " · " + e.names.joined(separator: ", "))
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                        Text([e.gymName, e.date.map { GymDayText.long($0) } ?? L("gym_entry_before")].compactMap { $0 }
                            .joined(separator: " · ")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if e.count > 1 { Text("×\(e.count)").font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink) }
                }
                .padding(.horizontal, 12).padding(.vertical, 10).cardChrome(14)
            }
            .buttonStyle(.plain)
        }
    }

    static func kind(_ key: String) -> String {
        let k = GymAscentKeys.shared.parse(key: key)
        if let b = k as? AscentKeyBoard { return BoardStyle.name(b.board) }
        if k is AscentKeyRoute { return L("gym_route_single") }
        return L("gym_entry_block")
    }
}
