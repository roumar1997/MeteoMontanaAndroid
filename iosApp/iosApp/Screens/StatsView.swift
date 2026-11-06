import SwiftUI
import Shared

/// MIS ESTADÍSTICAS (C4) — espejo de StatsScreen.kt. Los números salen del
/// MISMO cerebro compartido (JournalStatsCalculator en shared, con tests);
/// aquí solo se pinta. Selector de año desplegable (Todo/2026/2025…) + meses.
struct StatsView: View {
    /// G: nil = mis estadísticas; uid = las de OTRO usuario (perfil público
    /// o seguimiento aceptado — la privacidad la impone el backend).
    var uid: String? = nil
    @StateObject var store = StatsStore()
    @State var showDaysList = false
    // Fila de la pirámide tocada → hoja con las vías de ese grado.
    struct GradeSel: Identifiable { let id = UUID(); let grade: String }
    @State var gradeDetail: GradeSel? = nil
    // Destino de navegación en la PILA PRINCIPAL: la hoja se cierra antes de
    // navegar. Empujar pantallas DENTRO de la hoja dejaba una transición de
    // sheet a medias → watchdog 0x8BADF00D al ir a segundo plano.
    struct StatsNav: Identifiable, Hashable {
        let schoolId: String; let via: String
        var id: String { schoolId + "|" + via }
    }
    @State var statsNav: StatsNav? = nil
    // Día desplegado en la hoja de días de roca.
    @State var expandedDay: String? = nil
    // Escuela desplegada inline en TUS ESCUELAS (nil = ninguna).
    @State var expandedSchool: String? = nil
    @State var showGoalSheet = false

    var monthShort: [String] { CalendarLabels.monthsShort().map { $0.uppercased() } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // ── Objetivo del año (solo en las tuyas) ────────────────────
                if store.isOwn && store.goalLoaded {
                    GoalSection(goal: store.goal, year: store.currentYear) { showGoalSheet = true }
                }
                // ── Logros: plegados bajo un botón, arriba (de todo el diario, no del filtro) ──
                if !store.loading {
                    AchievementsSection(items: store.achievements, rarity: store.rarity)
                }
                // ── Filtros ─────────────────────────────────────────────────
                HStack(spacing: 8) {
                    filterChip(L("BLOQUE"), selected: store.discipline == "BOULDER") {
                        store.discipline = "BOULDER"; store.recompute()
                    }
                    filterChip(L("VÍA"), selected: store.discipline == "ROUTE") {
                        store.discipline = "ROUTE"; store.recompute()
                    }
                    Menu {
                        Button("Todos") { store.grade = nil; store.recompute() }
                        ForEach(store.availableGrades, id: \.self) { g in
                            Button(g) { store.grade = g; store.recompute() }
                        }
                    } label: {
                        VotableChip(text: store.grade?.uppercased() ?? L("GRADO")) {}
                            .allowsHitTesting(false)
                    }
                    Menu {
                        Button("Todo") { store.year = nil; store.month = nil; store.day = nil; store.recompute() }
                        ForEach(store.availableYears, id: \.self) { y in
                            Button(y) { store.year = y; store.month = nil; store.day = nil; store.recompute() }
                        }
                    } label: {
                        VotableChip(text: store.year ?? L("TODO")) {}
                            .allowsHitTesting(false)
                    }
                }
                if let d = store.day {
                    filterChip(L("DÍA %@  ✕", d), selected: true) {
                        store.day = nil; store.recompute()
                    }
                }
                if store.year != nil {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            filterChip(L("AÑO ENTERO"), selected: store.month == nil) {
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
                            metric("\(s.daysOut)", L("DÍAS DE ROCA ▾"))
                        }.buttonStyle(.plain)
                        metric(L("%@ sem", s.currentStreakWeeks), L("RACHA"), terra: true)
                    }
                    HStack(spacing: 8) {
                        metric("\(s.projectsFallen)", L("PROYECTOS CAÍDOS"))
                        metric(String(format: "%.1f", s.avgPerDay), L("MEDIA/DÍA"))
                    }
                    // % flash / a vista y, con un año elegido, comparado con el anterior.
                    StyleAndYearSection(share: store.styleShare, comparison: store.yearComparison)

                    // ── Pirámide ────────────────────────────────────────────
                    Text("PIRÁMIDE DE GRADOS").eyebrow().padding(.top, 6)
                    let pyramid = s.pyramid.prefix(10)
                    let maxCount = pyramid.map { $0.second!.intValue }.max() ?? 1
                    ForEach(Array(pyramid.enumerated()), id: \.offset) { _, pair in
                        // G (opción B): cada barra con el color de SU grado
                        // (misma paleta que las líneas de los topos).
                        let accent = gradeAccent(pair.first! as String)
                        Button { gradeDetail = GradeSel(grade: pair.first! as String) } label: {
                            HStack(spacing: 8) {
                                Text(pair.first! as String).font(Cumbre.mono(11, .bold))
                                    .foregroundStyle(accent)
                                    .frame(width: 36, alignment: .leading)
                                GeometryReader { geo in
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(accent)
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
                            bestMonthCard(formatMonth(bm), Int(s.bestMonthCount))
                        }.buttonStyle(.plain)
                    }
                }

                // ── Progresión ──────────────────────────────────────────────
                if let p = store.progression { progressionSection(p) }

                if store.summary == nil && !store.loading {
                    infoCard(L("Marca vías como hechas y aquí verás tu pirámide, tu racha y tu progresión."))
                }

            }
            .padding(16)
        }
        .sheet(isPresented: $showGoalSheet) {
            GoalSheet(year: store.currentYear, initial: store.yearGoal,
                      onSave: { d, g, a, days in await store.saveGoal(discipline: d, grade: g, ascents: a, days: days) },
                      onRemove: { Task { await store.removeGoal() } })
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mis estadísticas")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // C6: compartir como imagen (formato historia, estilo Wrapped).
            if let sum = store.summary, uid == nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task {
                            await ShareStatsImage.share(
                                periodLabel: store.year.map { L("MI %@ EN ROCA", $0) } ?? L("MI DIARIO EN ROCA"),
                                disciplineLabel: store.discipline == "ROUTE" ? L("VÍA") : L("BLOQUE"),
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
        .task { await store.load(uid: uid) }
        .sheet(item: $gradeDetail) { sel in
            let grade = sel.grade
            // Tus vías de ESE grado. OJO: NO se navega dentro de la hoja — se
            // CIERRA y se empuja en la pila principal (navegar dentro dejaba
            // el sheet a medio cerrar y el watchdog mataba la app).
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("TUS %@", grade.uppercased()))
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
                                Text(L("%@ ascensos", count) + (expandedDay == day ? " ▴" : " ▾"))
                                    .font(Cumbre.mono(10, .bold))
                                    .foregroundStyle(Cumbre.terra)
                            }
                            .padding(.vertical, 6)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if expandedDay == day {
                            // S4: cargar TODA la pantalla con ese día.
                            Button {
                                showDaysList = false
                                store.day = day
                                store.recompute()
                            } label: {
                                Text("VER ESTADÍSTICAS DE ESTE DÍA ▸")
                                    .font(Cumbre.mono(9, .bold)).tracking(1)
                                    .foregroundStyle(Cumbre.terra)
                                    .padding(.leading, 16).padding(.vertical, 4)
                            }
                            .buttonStyle(.plain)
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

    func monthTile(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(Cumbre.mono(8, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 9)
        .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 9))
    }
}

