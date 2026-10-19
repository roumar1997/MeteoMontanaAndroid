import SwiftUI
import Shared

// Lista de Escuelas moderna (Álvaro, 2026-10-19, propuesta B): arriba LA MEJOR DE HOY en grande con la curva de
// las horas y, debajo, el resto en filas compactas dentro de un bloque redondeado. Solo cambia cómo se ve: mismos
// datos, mismos gestos (tocar abre, mantener compara, estrella = favorita).

/// Fondo de las tarjetas de la lista: un punto más claro que el fondo de la pantalla.
private let schoolCardBg = Cumbre.dyn(0xFBFAF6, 0x211F19)

/// «BUENO» → «Bueno», «MUY BUENO» → «Muy bueno».
private func niceScoreLabel(_ s: Int?) -> String {
    let l = Cumbre.scoreLabel(s).lowercased()
    return l.prefix(1).uppercased() + l.dropFirst()
}

/// La hora con mejor nota de las próximas horas (índice).
private func bestHourIndex(_ h: [Int]) -> Int? {
    guard !h.isEmpty else { return nil }
    return h.indices.max { h[$0] < h[$1] }
}

/// «Granito · 26 km» (o la comunidad si no hay distancia), como la fila de antes.
private func schoolMeta(_ school: School, distanceKm: Int?) -> String {
    var parts: [String] = []
    if let r = school.rockType, !r.isEmpty { parts.append(rockLabel(r)) }
    if let km = distanceKm { parts.append("\(km) km") }
    else if let reg = school.region, !reg.isEmpty { parts.append(regionLabel(reg)) }
    return parts.joined(separator: " · ")
}

/// «roca seca» / «roca mojada» con su color, si se sabe.
private struct RockStateText: View {
    let dry: Bool?
    var short = false
    var body: some View {
        if let dry {
            Text(short ? (dry ? L("seca") : L("mojada")) : (dry ? L("roca seca") : L("roca mojada")))
                .fontWeight(.semibold)
                .foregroundStyle(dry ? Cumbre.ok : Cumbre.bad)
        }
    }
}

/// Estrella de favorita (no dispara la navegación de la fila).
private struct FavoriteStar: View {
    let isFavorite: Bool
    let onToggle: () -> Void
    var size: CGFloat = 17
    var body: some View {
        Button(action: onToggle) {
            Image(systemName: isFavorite ? "star.fill" : "star")
                .font(.system(size: size))
                .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink3)
                .frame(width: 34, height: 34)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
    }
}

private struct ProcessionaryBadge: View {
    var body: some View {
        ProcessionaryIcon(tint: .white)
            .frame(width: 12, height: 12)
            .frame(width: 20, height: 20)
            .background(Cumbre.bad, in: Circle())
    }
}

// MARK: - La mejor de hoy

struct SchoolHeroCard: View {
    let school: School
    let score: SchoolScore?
    var range: RangeScore? = nil
    var distanceKm: Int? = nil
    var isFavorite = false
    var isSelected = false
    var processionaryAlertActive = false
    var onToggleFavorite: () -> Void = {}

    private var value: Int? { range.map { Int($0.combinedScore) } ?? score.map { Int($0.todayScore) } }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Text(range != nil ? L("LA MEJOR DEL TRAMO") : L("LA MEJOR DE HOY"))
                    .font(Cumbre.mono(10, .bold)).tracking(1.8)
                    .foregroundStyle(Cumbre.terra)
                Spacer(minLength: 0)
                if processionaryAlertActive { ProcessionaryBadge() }
                FavoriteStar(isFavorite: isFavorite, onToggle: onToggleFavorite, size: 18)
            }
            .padding(.bottom, -8)
            HStack(alignment: .center, spacing: 12) {
                Text(value.map(String.init) ?? "—")
                    .font(Cumbre.serif(46, .bold))
                    .foregroundStyle(value.map { Cumbre.score($0) } ?? Cumbre.ink3)
                    .minimumScaleFactor(0.7).lineLimit(1)
                    .guideTarget("schools.score", when: true)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 7) {
                        Text(school.name).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                            .lineLimit(2).minimumScaleFactor(0.85)
                        let kind = SchoolKind(style: school.style)
                        if !kind.isEmpty { SchoolKindIcons(kind: kind, height: 16) }
                    }
                    metaLine
                }
                Spacer(minLength: 0)
            }
            if let range {
                HStack(alignment: .center, spacing: 8) {
                    DayRangeRow(range: range)
                    Spacer(minLength: 4)
                    RainSummaryTag(range: range)
                }
            } else if let h = score?.hourlyScores.map({ $0.intValue }), h.count >= 2 {
                HourlyCurve(scores: Array(h.prefix(10)))
            }
        }
        .padding(14)
        .background(schoolCardBg, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20)
            .stroke(isSelected ? Cumbre.terra : Cumbre.rule, lineWidth: isSelected ? 2 : 1))
        .padding(.horizontal, 12).padding(.top, 12)
    }

    private var metaLine: some View {
        let parts = [niceScoreLabel(value), schoolMeta(school, distanceKm: distanceKm)].filter { !$0.isEmpty }
        return HStack(spacing: 0) {
            Text(parts.joined(separator: " · "))
            if range == nil, let dry = score?.dryRock {
                Text(" · ")
                RockStateText(dry: dry)
            }
        }
        .font(.system(size: 12.5))
        .foregroundStyle(Cumbre.ink2)
        .lineLimit(2)
    }
}

/// Curva de la nota de las próximas horas, con relleno suave y la mejor hora marcada.
struct HourlyCurve: View {
    let scores: [Int]

    private var hourLabels: (first: Int, last: Int) {
        let now = Calendar.current.component(.hour, from: Date())
        return (now, (now + scores.count - 1) % 24)
    }

    var body: some View {
        let best = bestHourIndex(scores) ?? 0
        let color = Cumbre.score(scores[best])
        VStack(spacing: 4) {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                let step = w / CGFloat(max(scores.count - 1, 1))
                let pts: [CGPoint] = scores.enumerated().map { i, v in
                    CGPoint(x: CGFloat(i) * step, y: h - 4 - CGFloat(v) / 100 * (h - 10))
                }
                ZStack(alignment: .topLeading) {
                    Path { p in
                        p.move(to: CGPoint(x: 0, y: h))
                        pts.forEach { p.addLine(to: $0) }
                        p.addLine(to: CGPoint(x: w, y: h))
                        p.closeSubpath()
                    }
                    .fill(LinearGradient(colors: [color.opacity(0.28), color.opacity(0)], startPoint: .top, endPoint: .bottom))
                    Path { p in
                        p.move(to: pts[0])
                        pts.dropFirst().forEach { p.addLine(to: $0) }
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                    Circle().fill(color).frame(width: 8, height: 8)
                        .position(pts[best])
                }
            }
            .frame(height: 56)
            HStack {
                Text(L("%@ h", hourLabels.first))
                Spacer()
                Text(L("Mejor: %@ h · %@", (hourLabels.first + best) % 24, scores[best]))
                    .fontWeight(.bold).foregroundStyle(Cumbre.ink)
                Spacer()
                Text(L("%@ h", hourLabels.last))
            }
            .font(Cumbre.mono(10))
            .foregroundStyle(Cumbre.ink3)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Mejor hora: %@ h", (hourLabels.first + best) % 24))
    }
}

// MARK: - El resto, compacto

struct SchoolCompactRow: View {
    let school: School
    let score: SchoolScore?
    var range: RangeScore? = nil
    var distanceKm: Int? = nil
    var isFavorite = false
    var isSelected = false
    var processionaryAlertActive = false
    var onToggleFavorite: () -> Void = {}
    /// Sin tarjeta destacada (orden por distancia o búsqueda): la guía señala la nota de la primera fila.
    var isGuideTarget = false

    private var value: Int? { range.map { Int($0.combinedScore) } ?? score.map { Int($0.todayScore) } }

    var body: some View {
        HStack(spacing: 10) {
            Text(value.map(String.init) ?? "—")
                .font(Cumbre.serif(18, .bold))
                .foregroundStyle(value != nil ? Color.white : Cumbre.ink3)
                .minimumScaleFactor(0.7).lineLimit(1)
                .frame(width: 44, height: 44)
                .background(value.map { Cumbre.score($0) } ?? Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                .guideTarget("schools.score", when: isGuideTarget)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(school.name).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink)
                        .lineLimit(1).layoutPriority(1)
                    let kind = SchoolKind(style: school.style)
                    if !kind.isEmpty { SchoolKindIcons(kind: kind, height: 14) }
                }
                HStack(spacing: 0) {
                    Text(schoolMeta(school, distanceKm: distanceKm))
                    if range == nil, let dry = score?.dryRock {
                        Text(" · ")
                        RockStateText(dry: dry, short: true)
                    } else if let range, range.rainDays > 0 {
                        Text(" · ")
                        Text(L("lluvia %@ d", range.rainDays)).fontWeight(.semibold).foregroundStyle(Cumbre.rain)
                    }
                }
                .font(.system(size: 12))
                .foregroundStyle(Cumbre.ink2)
                .lineLimit(1)
            }
            Spacer(minLength: 4)
            if processionaryAlertActive { ProcessionaryBadge() }
            MiniBars(values: range.map { $0.days.map { Int($0.score) } }
                     ?? score?.hourlyScores.prefix(10).map { $0.intValue } ?? [])
            FavoriteStar(isFavorite: isFavorite, onToggle: onToggleFavorite)
        }
        .padding(.leading, 12).padding(.trailing, 4).padding(.vertical, 9)
        .background(isSelected ? Cumbre.terraBg : Color.clear)
        .contentShape(Rectangle())
    }
}

/// Mini-barras de nota (horas de hoy o días del tramo), cada una con su color.
struct MiniBars: View {
    let values: [Int]
    var body: some View {
        if !values.isEmpty {
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Array(values.enumerated()), id: \.offset) { _, v in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Cumbre.score(v))
                        .frame(height: max(3, 16 * CGFloat(max(15, v)) / 100))
                }
            }
            .frame(width: values.count <= 5 ? 36 : 58, height: 16, alignment: .bottom)
            .accessibilityHidden(true)
        }
    }
}

/// Tocar abre (o cambia la selección de comparar) y mantener pulsado compara. Sin Button/NavigationLink: dentro de
/// un ScrollView el Button se "come" el long-press.
struct SchoolTapGestures: ViewModifier {
    let open: () -> Void
    let compare: () -> Void
    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture(perform: open)
            .onLongPressGesture(minimumDuration: 0.35, perform: compare)
    }
}

/// Cabecera del bloque de filas: «EL RESTO · POR NOTA» bajo la destacada.
struct SchoolListSectionLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Cumbre.mono(10, .bold)).tracking(1.8)
            .foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
    }
}

/// El bloque redondeado que agrupa las filas compactas.
struct SchoolRowsBlock<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        VStack(spacing: 0) { content }
            .background(schoolCardBg, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 12)
    }
}
