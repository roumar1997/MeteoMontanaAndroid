import SwiftUI
import Shared

/// MIS ESTADÍSTICAS (C4) — espejo de StatsScreen.kt. Los números salen del
/// MISMO cerebro compartido (JournalStatsCalculator en shared, con tests);
/// aquí solo se pinta. Selector de año desplegable (Todo/2026/2025…) + meses.
struct StatsView: View {
    /// G: nil = mis estadísticas; uid = las de OTRO usuario (perfil público
    /// o seguimiento aceptado — la privacidad la impone el backend).
    var uid: String? = nil
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
    @State private var showGoalSheet = false

    private var monthShort: [String] { CalendarLabels.monthsShort().map { $0.uppercased() } }

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

    /// Progresión (ascensos por mes, trimestres, semanas, escuelas). Aparte del body para que el compilador respire.
    @ViewBuilder
    private func progressionSection(_ p: JournalStatsCalculator.Progression) -> some View {
        VStack(alignment: .leading, spacing: 14) {
                    Text("ASCENSOS POR MES · ÚLT. 12").eyebrow().padding(.top, 6)
                    monthBars(p.monthlyCounts)

                    if !p.maxGradePerQuarter.isEmpty {
                        Text("GRADO MÁXIMO POR TRIMESTRE").eyebrow().padding(.top, 6)
                        let quarters = Array(p.maxGradePerQuarter.suffix(6))
                        HStack(spacing: 6) {
                            ForEach(Array(quarters.enumerated()), id: \.offset) { _, pair in
                                let g = pair.second! as String
                                let c = gradeAccent(g)
                                let parts = (pair.first! as String).components(separatedBy: "-")
                                VStack(spacing: 6) {
                                    Text(g).font(Cumbre.serif(17, .bold)).foregroundStyle(c)
                                        .lineLimit(1).minimumScaleFactor(0.6)
                                    Text((parts.last ?? "") + (parts.first.map { " '" + $0.suffix(2) } ?? ""))
                                        .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                                }
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(c.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(c.opacity(0.5), lineWidth: 1))
                            }
                        }
                    }

                    weeksSection(p)
                    schoolsSection(p)
        }
    }

    @ViewBuilder
    private func schoolsSection(_ p: JournalStatsCalculator.Progression) -> some View {
        VStack(alignment: .leading, spacing: 8) {
                    Text("TUS ESCUELAS").eyebrow().padding(.top, 6)
                    let topSchools = Array(p.perSchool.prefix(8))
                    let maxSchool = max(topSchools.map { $0.second!.intValue }.max() ?? 1, 1)
                    ForEach(Array(topSchools.enumerated()), id: \.offset) { _, t in
                        let school = t.first! as String
                        let isOpen = expandedSchool == school
                        let count = t.second!.intValue
                        // Tarjeta desplegable: nombre, barra proporcional, nº de ascensos y grado máximo en su color.
                        VStack(spacing: 6) {
                            Button { withAnimation { expandedSchool = isOpen ? nil : school } } label: {
                                HStack(spacing: 12) {
                                    VStack(spacing: 0) {
                                        Text("\(count)").font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.terra)
                                        Text(L("ascensos")).font(Cumbre.mono(8, .bold)).foregroundStyle(Cumbre.ink3)
                                    }
                                    .frame(width: 62)
                                    VStack(alignment: .leading, spacing: 7) {
                                        Text(school).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                            .lineLimit(2).multilineTextAlignment(.leading)
                                        GeometryReader { geo in
                                            ZStack(alignment: .leading) {
                                                Capsule().fill(Cumbre.paper2)
                                                Capsule().fill(Cumbre.terra)
                                                    .frame(width: max(8, geo.size.width * CGFloat(count) / CGFloat(maxSchool)))
                                            }
                                        }.frame(height: 6)
                                    }
                                    VStack(alignment: .trailing, spacing: 6) {
                                        if let g = t.third as? String { gradePill(g) }
                                        Image(systemName: isOpen ? "chevron.up" : "chevron.down")
                                            .font(.system(size: 11, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if isOpen {
                                schoolEntriesInline(school)
                            }
                        }
                        .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
                    }
        }
    }

    @ViewBuilder
    private func weeksSection(_ p: JournalStatsCalculator.Progression) -> some View {
        VStack(alignment: .leading, spacing: 8) {
                    let outWeeks = p.weeksOut.filter { $0.boolValue }.count
                    HStack {
                        Text("ÚLTIMAS 12 SEMANAS").eyebrow()
                        Spacer()
                        AdminChip(text: L("SALISTE %@ DE %@", outWeeks, p.weeksOut.count), color: Cumbre.terra, filled: true)
                    }.padding(.top, 6)
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 4) {
                            ForEach(Array(p.weeksOut.enumerated()), id: \.offset) { i, out in
                                VStack(spacing: 3) {
                                    RoundedRectangle(cornerRadius: 5)
                                        .fill(out.boolValue ? Cumbre.terra : Cumbre.paper2)
                                        .frame(height: 26)
                                    Text(i == p.weeksOut.count - 1 ? L("ahora") : (i % 3 == 0 ? "-\(p.weeksOut.count - 1 - i)" : " "))
                                        .font(Cumbre.mono(8)).foregroundStyle(Cumbre.ink3).lineLimit(1).minimumScaleFactor(0.6)
                                }.frame(maxWidth: .infinity)
                            }
                        }
                        HStack(spacing: 14) {
                            legendDot(Cumbre.terra, L("saliste esa semana"))
                            legendDot(Cumbre.paper2, L("no saliste"))
                        }
                    }
                    .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))

        }
    }

    /// Desplegado inline de una escuela: vías pulsables → abren su piedra.
    @ViewBuilder
    private func schoolEntriesInline(_ school: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(store.entriesForSchool(school).prefix(30).enumerated()),
                    id: \.offset) { _, e in
                if let sid = e.schoolId {
                    // lineId antes que blockName — mismo motivo que en JournalView.
                    NavigationLink(destination: SchoolLoaderView(schoolId: sid, openVia: e.lineId ?? e.blockName)) {
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
            Text((e.schoolName ?? "—") + (navigable ? L("  ·  VER ▸") : ""))
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
            gradePill(e.grade ?? "—")
        }
        .padding(.vertical, 5)
        .contentShape(Rectangle())
    }

    private func entryRow(_ e: JournalSession, navigable: Bool) -> some View {
        HStack {
            Text(e.blockName).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
            Spacer()
            gradePill(e.grade ?? "—")
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    /// Grado en una píldora con SU color (el de los topos); legible sobre el papel.
    private func gradePill(_ grade: String) -> some View {
        let c = gradeAccent(grade)
        return Text(grade).font(Cumbre.mono(11, .bold)).foregroundStyle(c)
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(c.opacity(0.14), in: Capsule())
            .overlay(Capsule().stroke(c.opacity(0.6), lineWidth: 1))
    }

    private func legendDot(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12)
            Text(text).font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
        }
    }

    /// El mejor mes, destacado: calendario, nombre y número grande; al tocarlo filtra por ese mes.
    private func bestMonthCard(_ month: String, _ count: Int) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "calendar.badge.checkmark").font(.system(size: 26)).foregroundStyle(.white)
                .frame(width: 52, height: 52).background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(L("TU MEJOR MES")).font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.terra)
                Text(month.capitalized).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            }
            Spacer()
            VStack(spacing: 0) {
                Text("\(count)").font(Cumbre.serif(30, .bold)).foregroundStyle(Cumbre.terra)
                Text(L("ascensos")).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(14)
        .background(Cumbre.terraBg, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.terra.opacity(0.5), lineWidth: 1))
    }

    /// G: color por grado (paleta de topos); los grados blancos → tinta.
    private func gradeAccent(_ grade: String) -> Color {
        let st = GradeColor.chipStyle(grade)
        return st.dark ? Cumbre.ink : st.stroke
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

    /// Ascensos por mes (últimos 12): número sobre cada barra, el mejor mes en terracota sólido, el último (este mes)
    /// con la inicial en negrita, una línea discontinua con la MEDIA y una frase con el total, la media y el mejor mes.
    private func monthBars(_ counts: [KotlinPair<NSString, KotlinInt>]) -> some View {
        let values = counts.map { $0.second!.intValue }
        let maxCount = max(values.max() ?? 1, 1)
        let total = values.reduce(0, +)
        let avg = values.isEmpty ? 0 : Double(total) / Double(values.count)
        let bestIndex = values.enumerated().max { $0.element < $1.element }.flatMap { $0.element > 0 ? $0.offset : nil }
        let barLane: CGFloat = 96, numberLane: CGFloat = 16
        func monthName(_ key: String) -> String {
            let n = Int(key.components(separatedBy: "-").last ?? "1") ?? 1
            return monthShort[max(0, min(11, n - 1))]
        }
        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                monthTile("\(total)", L("EN 12 MESES"), Cumbre.ink)
                monthTile(String(format: "%.1f", avg), L("MEDIA AL MES"), Cumbre.ink)
                monthTile(bestIndex.map { monthName(counts[$0].first! as String) } ?? "—",
                          L("MEJOR MES") + (bestIndex.map { " · \(values[$0])" } ?? ""), Cumbre.terra)
            }
            ZStack(alignment: .topLeading) {
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(Array(counts.enumerated()), id: \.offset) { i, pair in
                        let count = values[i]
                        let isBest = bestIndex == i
                        let isNow = i == counts.count - 1
                        VStack(spacing: 0) {
                            Text(count > 0 ? "\(count)" : " ")
                                .font(.system(size: 10, weight: isBest ? .bold : .regular))
                                .foregroundStyle(isBest ? Cumbre.terra : Cumbre.ink3).frame(height: numberLane)
                            ZStack(alignment: .bottom) {
                                Color.clear.frame(height: barLane)
                                UnevenRoundedRectangle(topLeadingRadius: 4, topTrailingRadius: 4)
                                    .fill(isBest ? Cumbre.terra : Cumbre.terra.opacity(0.30 + 0.45 * Double(count) / Double(maxCount)))
                                    .frame(height: max(barLane * CGFloat(count) / CGFloat(maxCount), count > 0 ? 4 : 1))
                            }
                            Text(String(monthName(pair.first! as String).prefix(3)))
                                .font(.system(size: 9, weight: isNow ? .bold : .regular))
                                .foregroundStyle(isNow ? Cumbre.terra : Cumbre.ink3).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                // Línea discontinua de la media, a su altura dentro del carril de las barras.
                GeometryReader { geo in
                    Path { p in
                        let y = numberLane + barLane * (1 - CGFloat(min(max(avg / Double(maxCount), 0), 1)))
                        p.move(to: CGPoint(x: 0, y: y)); p.addLine(to: CGPoint(x: geo.size.width, y: y))
                    }
                    .stroke(Cumbre.ink3.opacity(0.6), style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                }
                .allowsHitTesting(false)
            }
            HStack(spacing: 6) {
                Rectangle().fill(Cumbre.ink3.opacity(0.6)).frame(width: 14, height: 1)
                Text(L("línea discontinua = tu media")).font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func monthTile(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(Cumbre.mono(8, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 9)
        .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 9))
    }
}

/// Baja el diario una vez y calcula en local con el calculador compartido.
@MainActor
final class StatsStore: ObservableObject {
    @Published var loading = true
    @Published var discipline = "BOULDER"
    @Published var year: String? = nil
    @Published var month: String? = nil
    @Published var day: String? = nil    // "yyyy-MM-dd": estadísticas de UN día
    @Published var grade: String? = nil  // G: filtrar por UN grado
    @Published var availableYears: [String] = []
    @Published var summary: JournalStatsCalculator.Summary? = nil
    @Published var progression: JournalStatsCalculator.Progression? = nil
    // Objetivo del año, extras y logros (2026-10-18), espejo de StatsViewModel.kt.
    let currentYear = Calendar.current.component(.year, from: Date())
    @Published var goal: GoalProgress? = nil
    @Published var goalLoaded = false
    @Published var styleShare: StyleShare? = nil
    @Published var yearComparison: YearComparison? = nil
    @Published var achievements: [Achievement] = []
    /// Cuánta gente tiene cada logro (nil = aún no se sabe o sin red).
    @Published var rarity: AchievementRarity? = nil
    private var lastSyncedIds: Set<String> = []
    @Published private(set) var isOwn = true

    private var entries: [JournalSession] = []
    private var places: [String: SchoolPlace] = [:]
    private(set) var yearGoal: YearGoal? = nil

    func load(uid: String? = nil) async {
        guard entries.isEmpty else { return }
        isOwn = uid == nil
        let container = AppDependencies.shared.container
        if let uid {
            if let list = try? await container.getUserJournal.invoke(uid: uid) { entries = list }
        } else if let list = try? await container.getMyJournal.invoke() {
            entries = list
        }
        loading = false
        recompute()
        // Cuánta gente tiene cada logro: si falla, simplemente no se enseña el porcentaje.
        rarity = try? await container.getAchievementRarity.invoke()
        // Región y país de cada escuela (logros de explorar). Si falla, esos logros quedan sin conseguir.
        if let schools = try? await container.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                                 lat: nil, lon: nil, radioKm: nil) {
            places = Dictionary(schools.map { ($0.id, SchoolPlace(region: $0.region, country: $0.country)) },
                                uniquingKeysWith: { a, _ in a })
        }
        if uid == nil {
            yearGoal = try? await container.getYearGoal.invoke(year: Int32(currentYear))
            goalLoaded = true
        }
        recompute()
    }

    /// Guarda el objetivo del año en curso. false si no se pudo (sin red…).
    func saveGoal(discipline: String?, grade: String?, ascents: Int?, days: Int?) async -> Bool {
        let g = YearGoal(year: Int32(currentYear), discipline: discipline, grade: grade,
                         ascents: ascents.map { KotlinInt(int: Int32($0)) }, days: days.map { KotlinInt(int: Int32($0)) })
        guard let saved = try? await AppDependencies.shared.container.saveYearGoal.invoke(goal: g) else { return false }
        yearGoal = saved
        recompute()
        return true
    }

    func removeGoal() async {
        if (try? await AppDependencies.shared.container.deleteYearGoal.invoke(year: Int32(currentYear))) != nil {
            yearGoal = nil
            recompute()
        }
    }

    /** G: grados presentes, del más duro al más fácil (para el filtro). */
    var availableGrades: [String] {
        let calc = JournalStatsCalculator.shared
        let gs = Set(entries.compactMap { $0.grade?.trimmingCharacters(in: .whitespaces).lowercased() }
            .filter { !$0.isEmpty })
        return gs.sorted { calc.gradeRank(grade: $0) > calc.gradeRank(grade: $1) }
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
        if let d = day { filtered = filtered.filter { $0.date == d } }
        if let g = grade { filtered = filtered.filter { $0.grade?.lowercased() == g } }
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
        if let dd = day { filtered = filtered.filter { $0.date == dd } }
        if let g = grade { filtered = filtered.filter { $0.grade?.lowercased() == g } }
        availableYears = calc.availableYears(entries: entries)
        summary = calc.summary(entries: filtered, allEntries: entries, today: today)
        progression = calc.progression(entries: filtered, today: today)
        let goals = GoalCalculator.shared
        goal = yearGoal.map { goals.progress(goal: $0, all: entries) }
        styleShare = goals.styleShare(entries: filtered)
        yearComparison = year.map { goals.compareYears(entries: calc.filter(entries: entries, discipline: discipline, year: nil),
                                                       year: $0) }
        achievements = AchievementsCalculator.shared.compute(all: entries, places: places, today: today)
        syncAchievementsIfChanged()
    }

    /// Solo en las mías y solo si cambió el conjunto de logros desde la última vez. Silencioso.
    private func syncAchievementsIfChanged() {
        guard isOwn, !entries.isEmpty else { return }
        let unlocked = achievements.filter { $0.unlocked }
        let ids = Set(unlocked.map { $0.id })
        guard ids != lastSyncedIds else { return }
        lastSyncedIds = ids
        Task {
            do { try await AppDependencies.shared.container.syncMyAchievements.invoke(achievements: unlocked) }
            catch { lastSyncedIds = [] }
        }
    }
}
