import SwiftUI
import Shared

// Comparar tus favoritas desde Escuelas (Álvaro, 2026-10-28): la estrella «FAVS» de la fila de FILTROS enciende esta
// tarjeta encima de la lista. Antes era la tabla «TUS FAVORITAS» del Tiempo, que se deslizaba de lado y cortaba días.
// Mismo lenguaje que las tarjetas de Escuelas: anillo con la nota de hoy y una tira de barras por día con el mejor día
// recuadrado y dicho con palabras («Mejor día: viernes (90)», como «Mejor hora: …»).

/// Clave de la estrella: se recuerda encendida entre aperturas.
let favoritesCompareKey = "schools.favoritesCompare"

@MainActor
final class FavoritesCompareModel: ObservableObject {
    @Published private(set) var grid: FavoritesGrid?
    @Published private(set) var loading = true

    func load() async {
        loading = grid == nil
        if let g = try? await AppDependencies.shared.container.getFavoritesGrid.invoke() { grid = g }
        loading = false
    }
}

struct FavoritesCompareCard: View {
    /// Toca una escuela de la comparación → su ficha.
    let onOpen: (String) -> Void
    @StateObject private var model = FavoritesCompareModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "star.fill").font(.system(size: 10, weight: .bold))
                Text(L("TUS FAVORITAS · %@ DÍAS", model.grid?.days.count ?? 7)).font(Cumbre.mono(10.5, .bold)).tracking(1.6)
            }
            .foregroundStyle(Cumbre.terra)

            if let grid = model.grid, !grid.rows.isEmpty {
                ForEach(Array(grid.rows.enumerated()), id: \.element.schoolId) { i, row in
                    if i > 0 { Divider().overlay(Cumbre.rule) }
                    FavoriteCompareRow(row: row, days: grid.days)
                        .contentShape(Rectangle())
                        .onTapGesture { onOpen(row.schoolId) }
                }
            } else if model.loading {
                VStack(spacing: 14) { ForEach(0..<2, id: \.self) { _ in
                    HStack(spacing: 12) { Bone(height: 52, shape: .circle); VStack(alignment: .leading, spacing: 8) { Bone(width: 140, height: 16); Bone(height: 30) } }
                } }
                .shimmer()
            } else {
                Text(L("Marca escuelas con la estrella ☆ para compararlas aquí día a día."))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.dyn(0xFBFAF6, 0x211F19), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
        .task { await model.load() }
    }
}

/// Una favorita: anillo con la nota de hoy, nombre, barras por día (la de hoy en negrita) y su mejor día.
private struct FavoriteCompareRow: View {
    let row: FavoriteRow
    let days: [String]

    var body: some View {
        let scores = row.cells.map { Int($0.avgScore) }
        let best = scores.indices.max { scores[$0] < scores[$1] } ?? 0
        HStack(alignment: .center, spacing: 12) {
            ScoreRing(score: scores.first, size: 52)
            VStack(alignment: .leading, spacing: 6) {
                Text(row.schoolName)
                    .font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
                    .lineLimit(1).minimumScaleFactor(0.8)
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(Array(scores.enumerated()), id: \.offset) { i, v in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Cumbre.score(v))
                            .frame(height: max(6, 26 * CGFloat(max(12, v)) / 100))
                            .overlay(i == best ? RoundedRectangle(cornerRadius: 3).stroke(Cumbre.ink, lineWidth: 1.5).padding(-2) : nil)
                    }
                }
                .frame(height: 26, alignment: .bottom)
                HStack(spacing: 4) {
                    ForEach(scores.indices, id: \.self) { i in
                        Text(i == 0 ? L("HOY") : dayShort(i < days.count ? days[i] : ""))
                            .font(Cumbre.mono(9.5, i == 0 ? .bold : .regular))
                            .foregroundStyle(i == 0 ? Cumbre.ink : Cumbre.ink3)
                            .minimumScaleFactor(0.6).lineLimit(1)
                            .frame(maxWidth: .infinity)
                    }
                }
                if !scores.isEmpty {
                    Text(best == 0 ? L("Mejor día: hoy (%@)", scores[best])
                                   : L("Mejor día: %@ (%@)", dayLong(best < days.count ? days[best] : ""), scores[best]))
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func date(_ day: String) -> Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.date(from: day)
    }

    /// "2026-10-30" → "VIE" (mismas etiquetas que el resto de la app).
    private func dayShort(_ day: String) -> String {
        guard let d = date(day) else { return "" }
        let weekday = Calendar(identifier: .gregorian).component(.weekday, from: d)
        return CalendarLabels.weekdaysShortSunFirst()[(weekday - 1) % 7].uppercased()
    }

    /// "2026-10-30" → "viernes" en el idioma de la app.
    private func dayLong(_ day: String) -> String {
        guard let d = date(day) else { return day }
        let f = DateFormatter()
        f.locale = Locale(identifier: LanguageManager.shared.effectiveCode)
        f.dateFormat = "EEEE"
        return f.string(from: d)
    }
}
