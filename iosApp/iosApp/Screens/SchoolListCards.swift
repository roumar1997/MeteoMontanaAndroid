import SwiftUI
import Shared

// Lista de Escuelas moderna (Álvaro, 2026-10-19, propuesta A): cada escuela es una tarjeta con la nota en un
// ANILLO que se llena con su color, pastillas suaves (nota, roca · km, seca/mojada) y las próximas horas como
// barras con la hora escrita y la mejor marcada. Solo cambia cómo se ve: mismos datos y mismos gestos (tocar
// abre, mantener compara, estrella = favorita).

/// Fondo de las tarjetas: un punto más claro que el fondo de la pantalla.
private let schoolCardBg = Cumbre.dyn(0xFBFAF6, 0x211F19)

/// «BUENO» → «Bueno», «MUY BUENO» → «Muy bueno».
private func niceScoreLabel(_ s: Int?) -> String {
    let l = Cumbre.scoreLabel(s).lowercased()
    return l.prefix(1).uppercased() + l.dropFirst()
}

/// «Granito · 26 km» (o la comunidad si no hay distancia).
private func schoolMeta(_ school: School, distanceKm: Int?) -> String {
    var parts: [String] = []
    if let r = school.rockType, !r.isEmpty { parts.append(rockLabel(r)) }
    if let km = distanceKm { parts.append("\(km) km") }
    else if let reg = school.region, !reg.isEmpty { parts.append(regionLabel(reg)) }
    return parts.joined(separator: " · ")
}

struct SchoolCardView: View {
    let school: School
    let score: SchoolScore?
    var range: RangeScore? = nil
    var distanceKm: Int? = nil
    var isFavorite = false
    var isSelected = false
    var processionaryAlertActive = false
    var onToggleFavorite: () -> Void = {}
    /// Primera tarjeta: la que señala la guía («La nota de hoy»).
    var isFirst = false

    private var value: Int? { range.map { Int($0.combinedScore) } ?? score.map { Int($0.todayScore) } }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                ScoreRing(score: value)
                    .guideTarget("schools.score", when: isFirst)
                VStack(alignment: .leading, spacing: 5) {
                    titleRow
                    pills
                }
            }
            if let range {
                HStack(alignment: .center, spacing: 8) {
                    DayRangeRow(range: range)
                    Spacer(minLength: 4)
                    RainSummaryTag(range: range)
                }
            } else if let h = score?.hourlyScores.prefix(10).map({ $0.intValue }), !h.isEmpty {
                HourBars(scores: Array(h))
            }
        }
        .padding(12)
        .background(schoolCardBg, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .stroke(isSelected ? Cumbre.terra : Cumbre.rule, lineWidth: isSelected ? 2 : 1))
    }

    private var titleRow: some View {
        HStack(alignment: .center, spacing: 6) {
            Text(school.name)
                .font(Cumbre.serif(18, .bold))
                .foregroundStyle(Cumbre.ink)
                .lineLimit(2).minimumScaleFactor(0.85)
                .layoutPriority(1)
            let kind = SchoolKind(style: school.style)
            if !kind.isEmpty { SchoolKindIcons(kind: kind, height: 15) }
            Spacer(minLength: 0)
            // Procesionaria: solo si ya se sabe Y estamos en su temporada (espejo de Android).
            if processionaryAlertActive {
                ProcessionaryIcon(tint: .white)
                    .frame(width: 12, height: 12)
                    .frame(width: 20, height: 20)
                    .background(Cumbre.bad, in: Circle())
            }
            // BorderlessButtonStyle: recibe el toque sin disparar la navegación de la tarjeta.
            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "star.fill" : "star")
                    .font(.system(size: 17))
                    .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink3)
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .padding(.vertical, -6)
        }
    }

    /// Nota en palabras · roca y distancia · seca/mojada. Se parte en dos líneas si no cabe.
    private var pills: some View {
        let meta = schoolMeta(school, distanceKm: distanceKm)
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 6) { pillContent(meta) }
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    if value != nil { SoftPill(text: niceScoreLabel(value), fg: Cumbre.ink2, bg: Cumbre.paper) }
                    if !meta.isEmpty { Text(meta).font(.system(size: 12.5)).foregroundStyle(Cumbre.ink2).lineLimit(1) }
                }
                rockPill
            }
        }
    }

    @ViewBuilder private func pillContent(_ meta: String) -> some View {
        if value != nil { SoftPill(text: niceScoreLabel(value), fg: Cumbre.ink2, bg: Cumbre.paper) }
        if !meta.isEmpty { Text(meta).font(.system(size: 12.5)).foregroundStyle(Cumbre.ink2).lineLimit(1) }
        rockPill
    }

    @ViewBuilder private var rockPill: some View {
        if range == nil, let dry = score?.dryRock {
            SoftPill(text: dry ? L("Roca seca") : L("Roca mojada"),
                     fg: dry ? Cumbre.ok : Cumbre.bad,
                     bg: (dry ? Cumbre.ok : Cumbre.bad).opacity(0.14))
        }
    }
}

/// Pastilla suave (fondo tintado, sin borde).
private struct SoftPill: View {
    let text: String
    let fg: Color
    let bg: Color
    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(fg)
            .lineLimit(1)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(bg, in: Capsule())
    }
}

/// La nota en un anillo que se llena con su color (sin nota: anillo vacío y «—»).
struct ScoreRing: View {
    let score: Int?
    var size: CGFloat = 58
    var body: some View {
        let color = score.map { Cumbre.score($0) } ?? Cumbre.ink3
        ZStack {
            Circle().stroke(Cumbre.paper, lineWidth: 6)
            if let s = score {
                Circle()
                    .trim(from: 0, to: CGFloat(max(2, min(100, s))) / 100)
                    .stroke(color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            Text(score.map(String.init) ?? "—")
                .font(Cumbre.serif(21, .bold))
                .foregroundStyle(color)
                .minimumScaleFactor(0.7).lineLimit(1)
        }
        .padding(3)
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(score.map { L("Nota %@, %@", $0, Cumbre.scoreLabel($0)) } ?? L("Sin nota"))
    }
}

/// Las próximas horas como barras con su altura y color; debajo la primera y última hora y la mejor marcada.
struct HourBars: View {
    let scores: [Int]

    var body: some View {
        let now = Calendar.current.component(.hour, from: Date())
        let best = scores.indices.max { scores[$0] < scores[$1] } ?? 0
        VStack(spacing: 4) {
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(Array(scores.enumerated()), id: \.offset) { i, v in
                    UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 2,
                                           bottomTrailingRadius: 2, topTrailingRadius: 3)
                        .fill(Cumbre.score(v))
                        .frame(height: max(4, 30 * CGFloat(max(12, v)) / 100))
                        .overlay(i == best
                                 ? UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 2,
                                                          bottomTrailingRadius: 2, topTrailingRadius: 3)
                                    .stroke(Cumbre.ink, lineWidth: 1.5).padding(-2)
                                 : nil)
                }
            }
            .frame(height: 30, alignment: .bottom)
            HStack {
                Text(L("%@ h", now))
                Spacer()
                Text(L("Mejor: %@ h · %@", (now + best) % 24, scores[best]))
                    .fontWeight(.bold).foregroundStyle(Cumbre.ink)
                Spacer()
                Text(L("%@ h", (now + scores.count - 1) % 24))
            }
            .font(Cumbre.mono(10))
            .foregroundStyle(Cumbre.ink3)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Mejor hora: %@ h", (now + best) % 24))
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
