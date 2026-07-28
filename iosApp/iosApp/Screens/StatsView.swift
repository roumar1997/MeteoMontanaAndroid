import SwiftUI
import Shared

/// MIS ESTADÍSTICAS (C4) — espejo de StatsScreen.kt. Los números salen del
/// MISMO cerebro compartido (JournalStatsCalculator en shared, con tests);
/// aquí solo se pinta. Selector de año desplegable (Todo/2026/2025…) + meses.
struct StatsView: View {
    @StateObject private var store = StatsStore()

    private let monthShort = ["ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // ── Filtros ─────────────────────────────────────────────────
                HStack(spacing: 8) {
                    filterChip("BLOQUE", selected: store.discipline == "BOULDER") {
                        store.discipline = "BOULDER"; store.recompute()
                    }
                    filterChip("VÍA", selected: store.discipline == "ROUTE") {
                        store.discipline = "ROUTE"; store.recompute()
                    }
                    Menu {
                        Button("Todo") { store.year = nil; store.month = nil; store.recompute() }
                        ForEach(store.availableYears, id: \.self) { y in
                            Button(y) { store.year = y; store.month = nil; store.recompute() }
                        }
                    } label: {
                        VotableChip(text: store.year ?? "TODO") {}
                            .allowsHitTesting(false)
                    }
                }
                if store.year != nil {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            filterChip("AÑO ENTERO", selected: store.month == nil) {
                                store.month = nil; store.recompute()
                            }
                            ForEach(0..<12, id: \.self) { i in
                                let m = String(format: "%02d", i + 1)
                                filterChip(monthShort[i], selected: store.month == m) {
                                    store.month = m; store.recompute()
                                }
                            }
                        }
                    }
                }

                // ── Métricas ────────────────────────────────────────────────
                if let s = store.summary {
                    HStack(spacing: 8) {
                        metric("\(s.daysOut)", "DÍAS DE ROCA")
                        metric("\(s.currentStreakWeeks) sem", "RACHA", terra: true)
                    }
                    HStack(spacing: 8) {
                        metric("\(s.projectsFallen)", "PROYECTOS CAÍDOS")
                        metric(String(format: "%.1f", s.avgPerDay), "MEDIA/DÍA")
                    }

                    // ── Pirámide ────────────────────────────────────────────
                    Text("PIRÁMIDE DE GRADOS").eyebrow().padding(.top, 6)
                    let pyramid = s.pyramid.prefix(10)
                    let maxCount = pyramid.map { $0.second!.intValue }.max() ?? 1
                    ForEach(Array(pyramid.enumerated()), id: \.offset) { i, pair in
                        HStack(spacing: 8) {
                            Text(pair.first! as String).font(Cumbre.mono(11, .bold))
                                .foregroundStyle(i == 0 ? Cumbre.terra : Cumbre.ink)
                                .frame(width: 36, alignment: .leading)
                            GeometryReader { geo in
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Cumbre.terra.opacity(1.0 - Double(i) * 0.08))
                                    .frame(width: geo.size.width
                                        * CGFloat(pair.second!.intValue) / CGFloat(maxCount))
                            }
                            .frame(height: 14)
                            Text("\(pair.second!.intValue)").font(.system(size: 11))
                                .foregroundStyle(Cumbre.ink3)
                        }
                    }
                    if let bm = s.bestMonth {
                        infoCard("Tu mejor mes: \(formatMonth(bm)) (\(s.bestMonthCount) ascensos).")
                    }
                }

                // ── Progresión ──────────────────────────────────────────────
                if let p = store.progression {
                    Text("ASCENSOS POR MES · ÚLT. 12").eyebrow().padding(.top, 6)
                    monthBars(p.monthlyCounts)

                    if !p.maxGradePerQuarter.isEmpty {
                        Text("GRADO MÁXIMO POR TRIMESTRE").eyebrow().padding(.top, 6)
                        HStack {
                            ForEach(Array(p.maxGradePerQuarter.suffix(6).enumerated()),
                                    id: \.offset) { _, pair in
                                Spacer()
                                VStack(spacing: 2) {
                                    Circle().fill(Cumbre.terra).frame(width: 10, height: 10)
                                    Text(pair.second! as String).font(Cumbre.serif(14, .bold))
                                        .foregroundStyle(Cumbre.ink)
                                    Text((pair.first! as String).components(separatedBy: "-").last ?? "")
                                        .font(.system(size: 9)).foregroundStyle(Cumbre.ink3)
                                }
                                Spacer()
                            }
                        }
                    }

                    Text("ÚLTIMAS 12 SEMANAS").eyebrow().padding(.top, 6)
                    HStack(spacing: 3) {
                        ForEach(Array(p.weeksOut.enumerated()), id: \.offset) { _, out in
                            RoundedRectangle(cornerRadius: 4)
                                .fill(out.boolValue ? Cumbre.terra : Cumbre.paper2)
                                .frame(height: 18).frame(maxWidth: .infinity)
                        }
                    }
                    Text("Cada casilla = 1 semana · terra = saliste")
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)

                    Text("TUS ESCUELAS").eyebrow().padding(.top, 6)
                    ForEach(Array(p.perSchool.prefix(8).enumerated()), id: \.offset) { _, t in
                        HStack {
                            Text(t.first! as String).font(.system(size: 14))
                                .foregroundStyle(Cumbre.ink)
                            Spacer()
                            Text("\(t.second!.intValue)"
                                 + ((t.third as? String).map { " · máx \($0)" } ?? ""))
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                        }
                        .padding(.vertical, 4)
                    }
                }

                if store.summary == nil && !store.loading {
                    infoCard("Marca vías como hechas y aquí verás tu pirámide, tu racha y tu progresión.")
                }
            }
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mis estadísticas")
        .navigationBarTitleDisplayMode(.inline)
        .task { await store.load() }
    }

    private func formatMonth(_ yyyyMm: String) -> String {
        let parts = yyyyMm.split(separator: "-")
        guard parts.count == 2, let m = Int(parts[1]) else { return yyyyMm }
        return monthShort[m - 1].lowercased() + " " + parts[0]
    }

    private func filterChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(10, .bold))
                .foregroundStyle(selected ? .white : Cumbre.ink3)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 6)
                    .fill(selected ? Cumbre.terra : Cumbre.paper))
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(selected ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func metric(_ value: String, _ label: String, terra: Bool = false) -> some View {
        VStack(spacing: 3) {
            Text(value).font(Cumbre.serif(20, .bold))
                .foregroundStyle(terra ? Cumbre.terra : Cumbre.ink)
            Text(label).font(Cumbre.mono(8, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func infoCard(_ text: String) -> some View {
        Text(text).font(.system(size: 12)).foregroundStyle(Cumbre.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func monthBars(_ counts: [KotlinPair<NSString, KotlinInt>]) -> some View {
        let maxCount = max(counts.map { $0.second!.intValue }.max() ?? 1, 1)
        return HStack(alignment: .bottom, spacing: 4) {
            ForEach(Array(counts.enumerated()), id: \.offset) { _, pair in
                let count = pair.second!.intValue
                VStack(spacing: 2) {
                    if count > 0 {
                        Text("\(count)").font(.system(size: 8)).foregroundStyle(Cumbre.ink3)
                    }
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Cumbre.terra.opacity(0.4 + 0.6 * Double(count) / Double(maxCount)))
                        .frame(height: max(CGFloat(70 * count / maxCount), count > 0 ? 4 : 1))
                    let monthNum = Int((pair.first! as String).components(separatedBy: "-").last ?? "1") ?? 1
                    Text(String(monthShort[monthNum - 1].prefix(1)))
                        .font(.system(size: 8)).foregroundStyle(Cumbre.ink3)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 96)
    }
}

/// Baja el diario una vez y calcula en local con el calculador compartido.
@MainActor
final class StatsStore: ObservableObject {
    @Published var loading = true
    @Published var discipline = "BOULDER"
    @Published var year: String? = nil
    @Published var month: String? = nil
    @Published var availableYears: [String] = []
    @Published var summary: JournalStatsCalculator.Summary? = nil
    @Published var progression: JournalStatsCalculator.Progression? = nil

    private var entries: [JournalSession] = []

    func load() async {
        guard entries.isEmpty else { return }
        if let list = try? await AppDependencies.shared.container.getMyJournal.invoke() {
            entries = list
        }
        loading = false
        recompute()
    }

    func recompute() {
        let calc = JournalStatsCalculator.shared
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let today = df.string(from: Date())
        var filtered = calc.filter(entries: entries, discipline: discipline, year: year)
        if let m = month {
            filtered = filtered.filter { entry in
                let d = entry.date
                guard d.count >= 7 else { return false }
                let start = d.index(d.startIndex, offsetBy: 5)
                let end = d.index(d.startIndex, offsetBy: 7)
                return String(d[start..<end]) == m
            }
        }
        availableYears = calc.availableYears(entries: entries)
        summary = calc.summary(entries: filtered, allEntries: entries, today: today)
        progression = calc.progression(entries: filtered, today: today)
    }
}
