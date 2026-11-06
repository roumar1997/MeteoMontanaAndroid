import SwiftUI
import Shared

// Diario por escuelas, sectores y piedras (tarjetas y listas de solo lectura), también para el perfil de otra persona.

/// Desglose del diario por escuela — espejo de la navegación "ESCUELAS" del
/// perfil de Android (`onOpenAllSchools`). Cada escuela es pulsable y abre la
/// lista de bloques registrados en esa escuela.
/// Una escuela del diario como tarjeta (igual que «Tus escuelas» de Estadísticas): ascensos en grande, nombre, barra
/// proporcional a la escuela con más y el grado máximo en su color (Álvaro, 2026-10-19).
struct JournalSchoolCard: View {
    let name: String
    let count: Int
    let maxGrade: String?
    let top: Int

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text("\(count)").font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.terra)
                Text(L("ascensos")).font(Cumbre.mono(8, .bold)).foregroundStyle(Cumbre.ink3)
            }
            .frame(width: 62)
            VStack(alignment: .leading, spacing: 7) {
                Text(name).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .lineLimit(2).multilineTextAlignment(.leading)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Cumbre.paper2)
                        Capsule().fill(Cumbre.terra)
                            .frame(width: max(8, geo.size.width * CGFloat(count) / CGFloat(max(top, 1))))
                    }
                }.frame(height: 6)
            }
            VStack(alignment: .trailing, spacing: 6) {
                if let g = maxGrade, !g.isEmpty { GradePill(grade: g, small: true) }
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        .contentShape(Rectangle())
    }
}

/// Lo que enseña la tarjeta de un sector del diario: cuántos, el grado más duro y una foto de una de sus piedras.
struct JournalSectorSummary {
    let name: String
    let count: Int
    let maxGrade: String?
    let photo: String?
    let linePath: String?
}

/// Agrupa las entradas por sector (del catálogo). La foto sale de la vía más dura que tenga foto.
func journalSectors(_ entries: [JournalSession], _ info: [String: ViaCatalogInfo]) -> [JournalSectorSummary] {
    let calc = JournalStatsCalculator.shared
    var groups: [String: [JournalSession]] = [:]
    for e in entries {
        if let s = info[e.id]?.sector, !s.isEmpty { groups[s, default: []].append(e) }
    }
    return groups.map { name, list in
        let gradeOf: (JournalSession) -> String? = { info[$0.id]?.grade ?? $0.grade }
        let sorted = list.sorted { calc.gradeRank(grade: gradeOf($0) ?? "") > calc.gradeRank(grade: gradeOf($1) ?? "") }
        let withPhoto = sorted.first { !(info[$0.id]?.photoUrl ?? "").isEmpty }
        return JournalSectorSummary(name: name, count: list.count, maxGrade: sorted.first.flatMap(gradeOf),
                                    photo: withPhoto.flatMap { info[$0.id]?.photoUrl },
                                    linePath: withPhoto.flatMap { info[$0.id]?.linePath })
    }
    .sorted { $0.name.lowercased() < $1.name.lowercased() }
}

/// Un sector del diario como tarjeta: foto de una de sus piedras, nombre, cuántos, barra y grado más duro.
struct JournalSectorCard: View {
    let summary: JournalSectorSummary
    let top: Int

    var body: some View {
        HStack(spacing: 12) {
            if let photo = summary.photo {
                MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(summary.linePath)), grade: summary.maxGrade)
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 9))
            } else {
                Image(systemName: "square.stack.3d.up.fill").font(.system(size: 20)).foregroundStyle(Cumbre.terra)
                    .frame(width: 56, height: 56)
                    .background(Cumbre.terra.opacity(0.10), in: RoundedRectangle(cornerRadius: 9))
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(summary.name).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .lineLimit(2).multilineTextAlignment(.leading)
                HStack(spacing: 8) {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Cumbre.paper2)
                            Capsule().fill(Cumbre.terra)
                                .frame(width: max(8, geo.size.width * CGFloat(summary.count) / CGFloat(max(top, 1))))
                        }
                    }.frame(height: 6)
                    Text(summary.count == 1 ? L("1 bloque") : L("%@ bloques", summary.count))
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra).fixedSize()
                }
            }
            VStack(alignment: .trailing, spacing: 6) {
                if let g = summary.maxGrade, !g.isEmpty { GradePill(grade: g, small: true) }
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(10)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        .contentShape(Rectangle())
    }
}

struct JournalSchoolsView: View {
    let schools: [SchoolStats]
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    var body: some View {
        Group {
            if schools.isEmpty {
                Text("Aún no hay bloques en ninguna escuela.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    let top = max(schools.map { Int($0.blockCount) }.max() ?? 1, 1)
                    LazyVStack(spacing: 8) {
                        ForEach(schools, id: \.schoolName) { s in
                            // Antes iba directo a la lista plana; ahora pasa por
                            // "sectores" (paridad con Android).
                            NavigationLink(destination: SchoolJournalSectorsView(
                                schoolName: s.schoolName,
                                entries: entriesFor(s.schoolName),
                                viaInfo: viaInfo
                            )) {
                                JournalSchoolCard(name: s.schoolName, count: Int(s.blockCount), maxGrade: s.maxGrade, top: top)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Escuelas")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func entriesFor(_ schoolName: String) -> [JournalSession] {
        entries.filter { $0.schoolName?.caseInsensitiveCompare(schoolName) == .orderedSame }
    }
}

/// Sectores de una escuela concreta (solo lectura): agrupa por sector; las
/// entradas sin sector resuelto (o con uno aún no catalogado) se muestran
/// sueltas, sin subcarpeta. Sirve tanto para tu diario como para el de otro.
struct SchoolJournalSectorsView: View {
    let schoolName: String
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]

    private var sectors: [(name: String, count: Int)] {
        var counts: [String: Int] = [:]
        for e in entries {
            if let s = viaInfo[e.id]?.sector, !s.isEmpty {
                counts[s, default: 0] += 1
            }
        }
        return counts.map { (name: $0.key, count: $0.value) }.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }
    private var loose: [JournalSession] {
        entries.filter { (viaInfo[$0.id]?.sector ?? "").isEmpty }
    }

    var body: some View {
        Group {
            if sectors.isEmpty && loose.isEmpty {
                Text("Sin bloques registrados aquí.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    let summaries = journalSectors(entries, viaInfo)
                    let top = max(summaries.map(\.count).max() ?? 1, 1)
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(summaries, id: \.name) { sec in
                            NavigationLink(destination: SchoolJournalBlocksView(
                                schoolName: sec.name,
                                entries: entries.filter { viaInfo[$0.id]?.sector?.caseInsensitiveCompare(sec.name) == .orderedSame },
                                viaInfo: viaInfo
                            )) {
                                JournalSectorCard(summary: sec, top: top)
                            }
                            .buttonStyle(.plain)
                        }
                        if !loose.isEmpty {
                            Text(L("SIN SECTOR")).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                                .padding(.top, 8)
                            VStack(spacing: 0) {
                                ForEach(loose, id: \.id) { e in
                                    JournalRow(entry: e, schoolId: e.schoolId, info: viaInfo[e.id])
                                    Divider().overlay(Cumbre.rule)
                                }
                            }
                            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(schoolName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Bloques registrados en una escuela (o sector) concreta (solo lectura). Se
/// llega desde el desglose por sectores; sirve tanto para tu diario como para
/// el de otro. `schoolName` hace de título — al venir de un sector, ya trae el
/// nombre del sector (las entries vienen pre-filtradas por SchoolJournalSectorsView).
struct SchoolJournalBlocksView: View {
    let schoolName: String
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    var body: some View {
        Group {
            if entries.isEmpty {
                Text("Sin bloques registrados aquí.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        // Nombre de la escuela → abre la escuela (sin piedra).
                        if let sid = entries.first?.schoolId, !sid.isEmpty {
                            NavigationLink(destination: SchoolLoaderView(schoolId: sid)) {
                                HStack(spacing: 8) {
                                    Image(systemName: "mountain.2").font(.system(size: 14)).foregroundStyle(Cumbre.terra)
                                    Text("VER ESCUELA").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 12)
                                .contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(entries, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: viaInfo[e.id])   // toca la vía → su piedra
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(schoolName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Lista de bloques (solo lectura) del diario de un usuario, reutilizable.
/// ¿Esta entrada del diario casa con lo que se busca? Nombre de la vía, escuela, sector o nº de piedra; sin tildes ni
/// mayúsculas. Vacío = todas.
func journalMatches(_ e: JournalSession, _ info: ViaCatalogInfo?, _ query: String) -> Bool {
    let q = query.trimmingCharacters(in: .whitespaces).folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
    if q.isEmpty { return true }
    let hay = [e.blockName, e.schoolName ?? "", info?.sector ?? e.sector ?? "", info?.boulderNumber ?? ""]
        .joined(separator: " ").folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
    return hay.contains(q)
}

struct JournalBlocksListView: View {
    let title: String
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    /// nil = todos · false = solo bloques (BOULDER) · true = solo vías (ROUTE).
    var routeOnly: Bool? = nil
    /// G: filtro por grado (chips con la paleta de topos).
    @State private var gradeFilters: Set<String> = []
    /// Buscar por nombre de vía, escuela, sector o nº de piedra (Álvaro, 2026-10-19).
    @State private var query = ""
    private var byDiscipline: [JournalSession] {
        guard let r = routeOnly else { return entries }
        return entries.filter { (($0.discipline).uppercased() == "ROUTE") == r }
    }
    private var shown: [JournalSession] {
        byDiscipline.filter { gradeMatches($0.grade, anyOf: gradeFilters) && journalMatches($0, viaInfo[$0.id], query) }
    }
    private var availableGrades: [String] {
        let calc = JournalStatsCalculator.shared
        return Set(byDiscipline.flatMap { gradeParts($0.grade) })
            .sorted { calc.gradeRank(grade: $0) > calc.gradeRank(grade: $1) }
    }
    var body: some View {
        VStack(spacing: 0) {
        if availableGrades.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(availableGrades, id: \.self) { g in
                        let st = GradeColor.chipStyle(g)
                        let accent = st.dark ? Cumbre.ink : st.stroke
                        let active = gradeFilters.contains(g)
                        Button { if active { gradeFilters.remove(g) } else { gradeFilters.insert(g) } } label: {
                            Text(g).font(.system(size: 12, weight: .bold))
                                .foregroundStyle(active ? .white : accent)
                                .padding(.horizontal, 10).padding(.vertical, 5)
                                .background(RoundedRectangle(cornerRadius: 6)
                                    .fill(active ? accent : Cumbre.paper))
                                .overlay(RoundedRectangle(cornerRadius: 6)
                                    .stroke(accent, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 8)
        }
        Group {
            if shown.isEmpty {
                Text(routeOnly == true ? L("Sin vías registradas.") : L("Sin bloques registrados."))
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(shown, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: viaInfo[e.id])
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(title)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L("Buscar por nombre, escuela o sector"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Stats del diario (BLOQUES/ESCUELAS/MÁXIMO) tappables — componente reutilizable
/// para tu cuenta y para el perfil público de quien sigues.
struct JournalStatsNav: View {
    let stats: JournalStats
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    /// Si se indica, "PROYECTOS" navega a los proyectos de ESE usuario (perfil
    /// público); si es nil, a los proyectos propios.
    var projectsUid: String? = nil
    /// H: en el perfil AJENO se añaden las celdas ESTADÍSTICAS y
    /// PUBLICACIONES (en el propio ya viven en su pantalla de cuenta).
    var showStatsAndPosts: Bool = false
    var body: some View {
        VStack(spacing: 8) {
            // ESTADÍSTICAS arriba, como en tu perfil: que se vea sin bajar (Álvaro, 2026-10-19).
            if showStatsAndPosts {
                NavigationLink(destination: StatsView(uid: projectsUid)) {
                    StatsEntryLabel()
                }.buttonStyle(.plain)
            }
            HStack(spacing: 8) {
                NavigationLink(destination: JournalBlocksListView(title: L("Bloques"), entries: entries, viaInfo: viaInfo, routeOnly: false)) {
                    cell("\(stats.boulderCount)", L("BLOQUES"))
                }.buttonStyle(.plain)
                NavigationLink(destination: JournalBlocksListView(title: L("Vías"), entries: entries, viaInfo: viaInfo, routeOnly: true)) {
                    cell("\(stats.routeCount)", L("VÍAS"))
                }.buttonStyle(.plain)
                NavigationLink(destination: JournalSchoolsView(schools: stats.bySchool, entries: entries, viaInfo: viaInfo)) {
                    cell("\(stats.schoolCount)", L("ESCUELAS"))
                }.buttonStyle(.plain)
            }
            HStack(spacing: 8) {
                cell(stats.maxBoulderGrade ?? "—", L("MÁX BLOQUE"))
                cell(stats.maxRouteGrade ?? "—", L("MÁX VÍA"))
                // Proyectos: misma celda pulsable que el resto, mismo caché offline
                // (viene en la misma llamada de stats).
                NavigationLink(destination: ProjectsView(uid: projectsUid)) {
                    cell("\(stats.projectCount)", L("PROYECTOS"))
                }.buttonStyle(.plain)
            }
            if showStatsAndPosts {
                // Publicaciones: una fila fina en vez de otra celda grande.
                NavigationLink(destination: MyPostsView(uid: projectsUid)) {
                    HStack {
                        Text(L("PUBLICACIONES")).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink2)
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 2) {
            Text(v).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                .lineLimit(1).minimumScaleFactor(0.6)
            Text(l).font(Cumbre.mono(8.5, .bold)).tracking(0.6).foregroundStyle(Cumbre.ink3)
                .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 9).padding(.horizontal, 4)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
    }
}
