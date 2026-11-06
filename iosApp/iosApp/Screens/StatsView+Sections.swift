import SwiftUI
import Shared

// Secciones de Estadísticas: progresión, trimestres, escuelas, semanas, filas, mejor mes, chips, métricas y barras.

extension StatsView {
    /// Progresión (ascensos por mes, trimestres, semanas, escuelas). Aparte del body para que el compilador respire.
    @ViewBuilder
    func progressionSection(_ p: JournalStatsCalculator.Progression) -> some View {
        VStack(alignment: .leading, spacing: 14) {
                    Text("ASCENSOS POR MES · ÚLT. 12").eyebrow().padding(.top, 6)
                    monthBars(p.monthlyCounts)

                    if !p.maxGradePerQuarter.isEmpty {
                        Text("GRADO MÁXIMO POR TRIMESTRE").eyebrow().padding(.top, 6)
                        quartersRow(p)
                    }

                    weeksSection(p)
                    schoolsSection(p)
        }
    }

    /// De poco a mucho: azul → verde → naranja. Cada mes tiene su color según cuánto subiste (no todo el mismo tono).
    func heat(_ t: Double) -> Color {
        let stops: [(Double, Double, Double)] = [(0.16, 0.45, 0.75), (0.20, 0.58, 0.40), (0.85, 0.48, 0.10)]
        let x = min(max(t, 0), 1) * 2
        let i = min(Int(x), 1)
        let f = x - Double(i)
        let a = stops[i], b = stops[i + 1]
        return Color(red: a.0 + (b.0 - a.0) * f, green: a.1 + (b.1 - a.1) * f, blue: a.2 + (b.2 - a.2) * f)
    }

    /// Verde si saliste esa semana; terracota las de la racha actual (las últimas seguidas); gris si no.
    func weekColor(_ i: Int, _ flags: [Bool]) -> Color {
        guard flags[i] else { return Cumbre.paper2 }
        var streak = 0
        for f in flags.reversed() { if f { streak += 1 } else { break } }
        return i >= flags.count - streak ? Cumbre.terra : Cumbre.ok
    }

    /// Etiqueta bajo cada casilla de semana: «ahora» en la última, «-N» cada tres.
    func weekTick(_ i: Int, total: Int) -> String {
        if i == total - 1 { return L("ahora") }
        if i % 3 == 0 { return "-" + String(total - 1 - i) }
        return " "
    }

    func quarterLabel(_ key: String) -> String {
        let parts: [String] = key.components(separatedBy: "-")
        let q: String = parts.last ?? ""
        let y: String = String((parts.first ?? "").suffix(2))
        return q + " '" + y
    }

    func quartersRow(_ p: JournalStatsCalculator.Progression) -> some View {
        let quarters = Array(p.maxGradePerQuarter.suffix(6))
        return HStack(spacing: 6) {
            ForEach(Array(quarters.enumerated()), id: \.offset) { _, pair in
                let g: String = pair.second! as String
                let c: Color = gradeAccent(g)
                VStack(spacing: 6) {
                    Text(g).font(Cumbre.serif(17, .bold)).foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.6)
                    Text(quarterLabel(pair.first! as String)).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .background(c.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(c.opacity(0.5), lineWidth: 1))
            }
        }
    }

    @ViewBuilder
    func schoolsSection(_ p: JournalStatsCalculator.Progression) -> some View {
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
    func weeksSection(_ p: JournalStatsCalculator.Progression) -> some View {
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
                                        .fill(weekColor(i, p.weeksOut.map { $0.boolValue }))
                                        .frame(height: 26)
                                    Text(weekTick(i, total: p.weeksOut.count))
                                        .font(Cumbre.mono(8)).foregroundStyle(Cumbre.ink3).lineLimit(1).minimumScaleFactor(0.6)
                                }.frame(maxWidth: .infinity)
                            }
                        }
                        HStack(spacing: 14) {
                            legendDot(Cumbre.ok, L("saliste esa semana"))
                            legendDot(Cumbre.terra, L("racha actual"))
                            legendDot(Cumbre.paper2, L("no saliste"))
                        }
                    }
                    .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))

        }
    }

    /// Desplegado inline de una escuela: vías pulsables → abren su piedra.
    @ViewBuilder
    func schoolEntriesInline(_ school: String) -> some View {
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

    func gradeEntryRow(_ e: JournalSession, navigable: Bool) -> some View {
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

    func dayEntryRow(_ e: JournalSession, navigable: Bool) -> some View {
        HStack {
            Text(e.blockName).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                .padding(.leading, 16)
            Spacer()
            gradePill(e.grade ?? "—")
        }
        .padding(.vertical, 5)
        .contentShape(Rectangle())
    }

    func entryRow(_ e: JournalSession, navigable: Bool) -> some View {
        HStack {
            Text(e.blockName).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
            Spacer()
            gradePill(e.grade ?? "—")
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    /// Grado en una píldora con SU color (el de los topos); legible sobre el papel.
    func gradePill(_ grade: String) -> some View {
        let c = gradeAccent(grade)
        return Text(grade).font(Cumbre.mono(11, .bold)).foregroundStyle(c)
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(c.opacity(0.14), in: Capsule())
            .overlay(Capsule().stroke(c.opacity(0.6), lineWidth: 1))
    }

    func legendDot(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 12, height: 12)
            Text(text).font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
        }
    }

    /// El mejor mes, destacado: calendario, nombre y número grande; al tocarlo filtra por ese mes.
    func bestMonthCard(_ month: String, _ count: Int) -> some View {
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
    func gradeAccent(_ grade: String) -> Color {
        let st = GradeColor.chipStyle(grade)
        return st.dark ? Cumbre.ink : st.stroke
    }

    func formatMonth(_ yyyyMm: String) -> String {
        let parts = yyyyMm.split(separator: "-")
        guard parts.count == 2, let m = Int(parts[1]) else { return yyyyMm }
        return monthShort[m - 1].lowercased() + " " + parts[0]
    }

    func filterChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
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

    func metric(_ value: String, _ label: String, terra: Bool = false) -> some View {
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

    func infoCard(_ text: String) -> some View {
        Text(text).font(.system(size: 12)).foregroundStyle(Cumbre.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    /// Ascensos por mes (últimos 12): número sobre cada barra, el mejor mes en terracota sólido, el último (este mes)
    /// con la inicial en negrita, una línea discontinua con la MEDIA y una frase con el total, la media y el mejor mes.
    func monthBars(_ counts: [KotlinPair<NSString, KotlinInt>]) -> some View {
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
                                    .fill(count == 0 ? Cumbre.paper2 : heat(Double(count) / Double(maxCount)))
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
}
