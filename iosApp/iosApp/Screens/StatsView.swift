import SwiftUI
import Shared

/// MIS ESTADÍSTICAS (C4) — espejo de StatsScreen.kt. Los números salen del
/// MISMO cerebro compartido (JournalStatsCalculator en shared, con tests);
/// aquí solo se pinta. Selector de año desplegable (Todo/2026/2025…) + meses.
struct StatsView: View {
    @StateObject private var store = StatsStore()
    @State private var showDaysList = false
    // Fila de la pirámide tocada → hoja con las vías de ese grado.
    private struct GradeSel: Identifiable { let id = UUID(); let grade: String }
    @State private var gradeDetail: GradeSel? = nil
    // Destino de navegación en la PILA PRINCIPAL: la hoja se cierra antes de
    // navegar. Empujar pantallas DENTRO de la hoja dejaba una transición de
    // sheet a medias → watchdog 0x8BADF00D al ir a segundo plano.
    private struct StatsNav: Identifiable, Hashable {
        let schoolId: String; let via: String
        var id: String { schoolId + "|" + via }
    }
    @State private var statsNav: StatsNav? = nil
    // Día desplegado en la hoja de días de roca.
    @State private var expandedDay: String? = nil
    // Escuela desplegada inline en TUS ESCUELAS (nil = ninguna).
    @State private var expandedSchool: String? = nil

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
                        Button { showDaysList = true } label: {
                            metric("\(s.daysOut)", "DÍAS DE ROCA ▾")
                        }.buttonStyle(.plain)
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
                        // Fila pulsable: abre la lista de vías de ESE grado.
                        Button { gradeDetail = GradeSel(grade: pair.first! as String) } label: {
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
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    if let bm = s.bestMonth {
                        Button {
                            store.year = String(bm.prefix(4))
                            store.month = String(bm.suffix(2))
                            store.recompute()
                        } label: {
                            infoCard("Tu mejor mes: \(formatMonth(bm)) (\(s.bestMonthCount) ascensos). Toca para verlo ▾")
                        }.buttonStyle(.plain)
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
                        let school = t.first! as String
                        let isOpen = expandedSchool == school
                        // Fila desplegable: los nombres de las vías inline.
                        Button { expandedSchool = isOpen ? nil : school } label: {
                            HStack {
                                Text(school).font(.system(size: 14))
                                    .foregroundStyle(Cumbre.ink)
                                Spacer()
                                Text("\(t.second!.intValue)"
                                     + ((t.third as? String).map { " · máx \($0)" } ?? "")
                                     + (isOpen ? " ▴" : " ▾"))
                                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                            }
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if isOpen {
                            schoolEntriesInline(school)
                        }
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
        .toolbar {
            // C6: compartir como imagen (formato historia, estilo Wrapped).
            if let sum = store.summary {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task {
                            await ShareStatsImage.share(
                                periodLabel: store.year.map { "MI \($0) EN ROCA" } ?? "MI DIARIO EN ROCA",
                                disciplineLabel: store.discipline == "ROUTE" ? "VÍA" : "BLOQUE",
                                summary: sum,
                                maxGrade: (sum.pyramid.first?.first).map { $0 as String },
                                progression: store.progression)
                        }
                    } label: {
                        Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.terra)
                    }
                }
            }
        }
        .task { await store.load() }
        .sheet(item: $gradeDetail) { sel in
            let grade = sel.grade
            // Tus vías de ESE grado. OJO: NO se navega dentro de la hoja — se
            // CIERRA y se empuja en la pila principal (navegar dentro dejaba
            // el sheet a medio cerrar y el watchdog mataba la app).
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("TUS \(grade.uppercased())")
                        .font(Cumbre.mono(11, .bold)).tracking(1.5)
                        .foregroundStyle(Cumbre.terra).padding(.bottom, 8)
                    ForEach(Array(store.entriesForGrade(grade).enumerated()),
                            id: \.offset) { _, e in
                        if let sid = e.schoolId {
                            Button {
                                gradeDetail = nil
                                statsNav = StatsNav(schoolId: sid, via: e.blockName)
                            } label: {
                                gradeEntryRow(e, navigable: true)
                            }
                            .buttonStyle(.plain)
                        } else {
                            gradeEntryRow(e, navigable: false)
                        }
                        Divider().overlay(Cumbre.rule)
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .presentationDetents([.medium, .large])
        }
        .navigationDestination(item: $statsNav) { nav in
            SchoolLoaderView(schoolId: nav.schoolId, openVia: nav.via)
        }
        .sheet(isPresented: $showDaysList) {
            // Día pulsable → sus ascensos (pulsables → su piedra, cerrando
            // la hoja y navegando en la pila principal, como la pirámide).
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("TUS DÍAS DE ROCA").font(Cumbre.mono(11, .bold)).tracking(1)
                        .foregroundStyle(Cumbre.terra).padding(.bottom, 8)
                    ForEach(store.daysWithCounts(), id: \.0) { day, count in
                        Button { expandedDay = expandedDay == day ? nil : day } label: {
                            HStack {
                                Text(day).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Text("\(count) ascensos" + (expandedDay == day ? " ▴" : " ▾"))
                                    .font(Cumbre.mono(10, .bold))
                                    .foregroundStyle(Cumbre.terra)
                            }
                            .padding(.vertical, 6)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if expandedDay == day {
                            ForEach(Array(store.entriesForDay(day).enumerated()),
                                    id: \.offset) { _, e in
                                if let sid = e.schoolId {
                                    Button {
                                        showDaysList = false
                                        statsNav = StatsNav(schoolId: sid, via: e.blockName)
                                    } label: {
                                        dayEntryRow(e, navigable: true)
                                    }
                                    .buttonStyle(.plain)
                                } else {
                                    dayEntryRow(e, navigable: false)
                                }
                            }
                        }
                        Divider().overlay(Cumbre.rule)
                    }
                }
                .padding(16)
            }
            .presentationDetents([.medium, .large])
        }
    }

    /// Desplegado inline de una escuela: vías pulsables → abren su piedra.
    @ViewBuilder
    private func schoolEntriesInline(_ school: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(store.entriesForSchool(school).prefix(30).enumerated()),
                    id: \.offset) { _, e in
                if let sid = e.schoolId {
                    NavigationLink(destination: SchoolLoaderView(schoolId: sid, openVia: e.blockName)) {
                        entryRow(e, navigable: true)
                    }
                    .buttonStyle(.plain)
                } else {
                    entryRow(e, navigable: false)
                }
            }
            NavigationLink(destination: SchoolJournalSectorsView(
                schoolName: school, entries: store.entriesForSchool(school))) {
                Text("VER EN EL DIARIO ▸").font(Cumbre.mono(9, .bold)).tracking(1)
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func gradeEntryRow(_ e: JournalSession, navigable: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(e.blockName).font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Cumbre.ink)
            Text((e.schoolName ?? "—") + (navigable ? "  ·  VER ▸" : ""))
                .font(Cumbre.mono(9, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.terra)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private func dayEntryRow(_ e: JournalSession, navigable: Bool) -> some View {
        HStack {
            Text(e.blockName).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                .padding(.leading, 16)
            Spacer()
            Text((e.grade ?? "—") + (navigable ? " ▸" : ""))
                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
        }
        .padding(.vertical, 5)
        .contentShape(Rectangle())
    }

    private func entryRow(_ e: JournalSession, navigable: Bool) -> some View {
        HStack {
            Text(e.blockName).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
            Spacer()
            Text((e.grade ?? "—") + (navigable ? " ▸" : ""))
                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
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

    /** N7: dias del filtro actual con nº de ascensos, recientes primero. */
    func daysWithCounts() -> [(String, Int)] {
        var counts: [String: Int] = [:]
        for e in filteredEntries() { counts[e.date, default: 0] += 1 }
        return counts.sorted { $0.key > $1.key }.map { ($0.key, $0.value) }
    }

    /** N7: entradas del filtro actual de UNA escuela (únicas, más duras primero). */
    func entriesForSchool(_ school: String) -> [JournalSession] {
        JournalStatsCalculator.shared.entriesForSchool(
            entries: filteredEntries(), school: school)
    }

    /** Un día de roca pulsado: qué ascensos hiciste ESE día. */
    func entriesForDay(_ day: String) -> [JournalSession] {
        filteredEntries().filter { $0.date == day }
    }

    /** Fila de la pirámide: vías de ESE grado (únicas, recientes primero). */
    func entriesForGrade(_ grade: String) -> [JournalSession] {
        JournalStatsCalculator.shared.entriesForGrade(
            entries: filteredEntries(), grade: grade)
    }

    private func filteredEntries() -> [JournalSession] {
        let calc = JournalStatsCalculator.shared
        var filtered = calc.filter(entries: entries, discipline: discipline, year: year)
        if let m = month {
            filtered = filtered.filter { e in
                guard e.date.count >= 7 else { return false }
                let start = e.date.index(e.date.startIndex, offsetBy: 5)
                let end = e.date.index(e.date.startIndex, offsetBy: 7)
                return String(e.date[start..<end]) == m
            }
        }
        return filtered
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
