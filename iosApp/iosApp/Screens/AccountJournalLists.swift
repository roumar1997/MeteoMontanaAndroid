import SwiftUI
import Shared
import PhotosUI

// Diario de tu cuenta: números del diario, bloques, escuelas y sectores.

/// Fila de stats del diario propio. BLOQUES y ESCUELAS son navegables; GRADO MÁX
/// es solo informativo. Observa el `AccountViewModel` para reflejar borrados.
struct AccountJournalStatsNav: View {
    @ObservedObject var vm: AccountViewModel
    var body: some View {
        if let s = vm.stats {
            VStack(spacing: 8) {
                // C4: MIS ESTADISTICAS arriba del todo, que se vea sin bajar.
                NavigationLink(destination: StatsView()) {
                    StatsEntryLabel()
                }.buttonStyle(.plain)
                    .guideTarget("profile.stats")
                HStack(spacing: 8) {
                    NavigationLink(destination: AccountBlocksList(vm: vm, routeOnly: false)) {
                        cell("\(s.boulderCount)", L("BLOQUES"))
                    }.buttonStyle(.plain)
                    NavigationLink(destination: AccountBlocksList(vm: vm, routeOnly: true)) {
                        cell("\(s.routeCount)", L("VÍAS"))
                    }.buttonStyle(.plain)
                    NavigationLink(destination: AccountSchoolsList(vm: vm)) {
                        cell("\(s.schoolCount)", L("ESCUELAS"))
                    }.buttonStyle(.plain)
                }
                HStack(spacing: 8) {
                    cell(s.maxBoulderGrade ?? "—", L("MÁX BLOQUE"))
                    cell(s.maxRouteGrade ?? "—", L("MÁX VÍA"))
                    NavigationLink(destination: ProjectsView()) {
                        cell("\(s.projectCount)", L("PROYECTOS"))
                    }.buttonStyle(.plain)
                }
                // Mis publicaciones: una fila fina en vez de otra celda grande.
                NavigationLink(destination: MyPostsView()) {
                    HStack {
                        Text(L("MIS PUBLICACIONES")).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink2)
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
                    .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
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
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
        .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Todos los bloques del diario propio, con borrar. Observa el vm → al borrar la
/// lista se refresca sola.
struct AccountBlocksList: View {
    @ObservedObject var vm: AccountViewModel
    /// nil = todos · false = solo bloques (BOULDER) · true = solo vías (ROUTE).
    /// Las entradas viejas sin modalidad cuentan como BOULDER (igual que stats).
    var routeOnly: Bool? = nil
    /// Grados por los que se filtra, VARIOS a la vez (vacío = todos).
    @State private var gradeFilters: Set<String> = []
    private var entries: [JournalSession] {
        guard let r = routeOnly else { return vm.entries }
        return vm.entries.filter { (($0.discipline).uppercased() == "ROUTE") == r }
    }
    /// Grado con el que se FILTRA: el del catalogo si se ha podido resolver,
    /// que es el que la fila ensena. Si aqui se mirara solo `entry.grade`, los
    /// chips ofrecerian grados distintos de los que se ven en pantalla.
    private func gradeOf(_ e: JournalSession) -> String? {
        let g = (vm.viaInfo[e.id]?.grade ?? e.grade)?
            .trimmingCharacters(in: .whitespaces).lowercased()
        return (g?.isEmpty ?? true) ? nil : g
    }

    /// Grados presentes, de mas duro a mas facil.
    private var availableGrades: [String] {
        let calc = JournalStatsCalculator.shared
        return Set(entries.flatMap { gradeParts(gradeOf($0)) })
            .sorted { calc.gradeRank(grade: $0) > calc.gradeRank(grade: $1) }
    }

    // Filtro por ESTILO: "aVista" | "alFlash" | nil (todos). Cliente, como
    // el de grado — no necesita ruta nueva (Rodrigo, 2026-08-21).
    @State private var styleFilters: Set<String> = []

    private func styleFilterChip(_ label: String, active: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(Cumbre.mono(11, .bold))
                .foregroundStyle(active ? .white : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(active ? Cumbre.terra : Color.clear,
                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                    .stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    /// Buscar por nombre de vía, escuela, sector o nº de piedra (Álvaro, 2026-10-19).
    @State private var query = ""

    private var visibles: [JournalSession] {
        let byGrade = entries.filter { gradeMatches(gradeOf($0), anyOf: gradeFilters) && journalMatches($0, vm.viaInfo[$0.id], query) }
        // Sin chips encendidos, todo; con uno o los dos, las de ese estilo (o de cualquiera de los dos).
        if styleFilters.isEmpty { return byGrade }
        return byGrade.filter { (styleFilters.contains("aVista") && $0.aVista) || (styleFilters.contains("alFlash") && $0.alFlash) }
    }

    var body: some View {
        Group {
            if entries.isEmpty {
                EmptyStateView(
                    icon: "book",
                    title: routeOnly == true ? L("Aún no has registrado vías") : L("Aún no has registrado bloques"),
                    message: L("Marca el ✓ de una vía dentro de su piedra (en el detalle de una escuela) y aparecerá aquí.")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        FirstTimeHint(
                            hintKey: "journal_tap_via",
                            text: L("Toca una vía para ir directamente a su piedra en la escuela.")
                        )
                        // Filtro por GRADO. Estaba escrito en JournalView, que
                        // es una pantalla a la que no se llega desde el perfil:
                        // el diario que se abre es ESTE.
                        if availableGrades.count > 1 {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(availableGrades, id: \.self) { g in
                                        let estilo = GradeColor.chipStyle(g)
                                        let color = estilo.dark ? Cumbre.ink : estilo.stroke
                                        let activo = gradeFilters.contains(g)
                                        Button { if activo { gradeFilters.remove(g) } else { gradeFilters.insert(g) } } label: {
                                            Text(g)
                                                .font(Cumbre.mono(11, .bold))
                                                .foregroundStyle(activo ? .white : color)
                                                .padding(.horizontal, 10).padding(.vertical, 5)
                                                .background(activo ? color : Color.clear)
                                                .overlay(RoundedRectangle(cornerRadius: 6)
                                                    .stroke(color, lineWidth: 1))
                                        }.buttonStyle(.plain)
                                    }
                                }
                                .padding(.horizontal, 16).padding(.vertical, 6)
                            }
                        }
                        // Filtro por ESTILO: solo si hay al menos una entrada marcada.
                        if entries.contains(where: { $0.aVista || $0.alFlash }) {
                            HStack(spacing: 6) {
                                styleFilterChip(L("A VISTA"), active: styleFilters.contains("aVista")) {
                                    if !styleFilters.insert("aVista").inserted { styleFilters.remove("aVista") }
                                }
                                styleFilterChip(L("AL FLASH"), active: styleFilters.contains("alFlash")) {
                                    if !styleFilters.insert("alFlash").inserted { styleFilters.remove("alFlash") }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.bottom, 6)
                        }
                        // N6: agrupado por MES + fecha editable (aqui es donde
                        // Rodrigo lo buscaba — la celda BLOQUES del perfil).
                        let byMonth = Dictionary(grouping: visibles.sorted { $0.date > $1.date },
                                                 by: { String($0.date.prefix(7)) })
                        ForEach(byMonth.keys.sorted(by: >), id: \.self) { month in
                            Text(JournalView.monthHeader(month) + " · \(byMonth[month]!.count)")
                                .font(Cumbre.mono(10, .bold)).tracking(1)
                                .foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 16).padding(.vertical, 8)
                            ForEach(byMonth[month]!, id: \.id) { e in
                                JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id],
                                           onChangeDate: { newDate in
                                               Task { await vm.changeDate(e.id, newDate) }
                                           },
                                           onChangeStyle: { aVista, alFlash in
                                               Task { await vm.changeStyle(e.id, aVista, alFlash) }
                                           }) { vm.deleteBlock(e.id) }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(routeOnly == true ? L("Vías") : L("Bloques"))
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L("Buscar por nombre, escuela o sector"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Desglose por escuela del diario propio. Cada escuela abre sus bloques (con
/// borrar). Observa el vm para reflejar borrados en los contadores.
struct AccountSchoolsList: View {
    @ObservedObject var vm: AccountViewModel
    var body: some View {
        let schools = vm.stats?.bySchool ?? []
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
                            // "sectores" para no mezclar todos los bloques/vías de
                            // la escuela de golpe (paridad con Android).
                            NavigationLink(destination: AccountSchoolSectorsList(vm: vm, schoolName: s.schoolName)) {
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
}

/// Sectores de una escuela concreta del diario propio: agrupa por sector (nº
/// de bloques) y deja sueltas (sin subcarpeta) las entradas sin sector resuelto
/// o con un sector aún no catalogado. Pulsar un sector entra a sus bloques.
struct AccountSchoolSectorsList: View {
    @ObservedObject var vm: AccountViewModel
    let schoolName: String

    private var schoolEntries: [JournalSession] {
        vm.entries.filter { $0.schoolName?.caseInsensitiveCompare(schoolName) == .orderedSame }
    }
    private var sectors: [(name: String, count: Int)] {
        var counts: [String: Int] = [:]
        for e in schoolEntries {
            if let s = vm.viaInfo[e.id]?.sector, !s.isEmpty {
                counts[s, default: 0] += 1
            }
        }
        return counts.map { (name: $0.key, count: $0.value) }.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }
    private var loose: [JournalSession] {
        schoolEntries.filter { (vm.viaInfo[$0.id]?.sector ?? "").isEmpty }
    }

    var body: some View {
        Group {
            if sectors.isEmpty && loose.isEmpty {
                Text("Sin bloques registrados aquí.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    let summaries = journalSectors(schoolEntries, vm.viaInfo)
                    let top = max(summaries.map(\.count).max() ?? 1, 1)
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(summaries, id: \.name) { sec in
                            NavigationLink(destination: AccountSchoolBlocksList(vm: vm, schoolName: schoolName, sectorName: sec.name)) {
                                JournalSectorCard(summary: sec, top: top)
                            }
                            .buttonStyle(.plain)
                        }
                        if !loose.isEmpty {
                            Text(L("SIN SECTOR")).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                                .padding(.top, 8)
                            VStack(spacing: 0) {
                                ForEach(loose, id: \.id) { e in
                                    JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id])
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

/// Bloques de una escuela concreta del diario propio, con borrar. Si
/// `sectorName` viene indicado, solo los de ese sector (llegado desde
/// AccountSchoolSectorsList); si no, todos (compatibilidad).
struct AccountSchoolBlocksList: View {
    @ObservedObject var vm: AccountViewModel
    let schoolName: String
    var sectorName: String? = nil
    var body: some View {
        let entries = vm.entries.filter {
            $0.schoolName?.caseInsensitiveCompare(schoolName) == .orderedSame &&
            (sectorName == nil || vm.viaInfo[$0.id]?.sector?.caseInsensitiveCompare(sectorName!) == .orderedSame)
        }
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
                            JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id]) { vm.deleteBlock(e.id) }
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(sectorName ?? schoolName)
        .navigationBarTitleDisplayMode(.inline)
    }
}
