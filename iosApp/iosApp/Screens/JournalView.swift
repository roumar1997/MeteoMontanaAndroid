import SwiftUI
import Shared

// Diario de escalada — espejo de JournalEntriesScreen.kt + stats del perfil.

@MainActor
final class JournalViewModel: ObservableObject {
    @Published var entries: [JournalSession] = []
    @Published var stats: JournalStats?
    @Published var loading = true

    private let getMyJournal: GetMyJournalUseCase
    private let getMyStats: GetMyJournalStatsUseCase
    private let createEntry: CreateJournalEntryUseCase
    private let deleteEntry: DeleteJournalEntryUseCase

    init(
        getMyJournal: GetMyJournalUseCase = AppDependencies.shared.container.getMyJournal,
        getMyStats: GetMyJournalStatsUseCase = AppDependencies.shared.container.getMyJournalStats,
        createEntry: CreateJournalEntryUseCase = AppDependencies.shared.container.createJournalEntry,
        deleteEntry: DeleteJournalEntryUseCase = AppDependencies.shared.container.deleteJournalEntry
    ) {
        self.getMyJournal = getMyJournal
        self.getMyStats = getMyStats
        self.createEntry = createEntry
        self.deleteEntry = deleteEntry
    }

    func load() async {
        loading = true
        entries = (try? await getMyJournal.invoke()) ?? []
        stats = try? await getMyStats.invoke()
        loading = false
    }

    func add(blockName: String, grade: String, schoolId: String?, schoolName: String, sector: String, notes: String) async {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let req = CreateJournalRequest(
            schoolId: schoolId,
            schoolName: schoolName.nilIfBlank,
            sector: sector.nilIfBlank,
            blockName: blockName.trimmingCharacters(in: .whitespaces),
            grade: grade.nilIfBlank,
            notes: notes.nilIfBlank,
            date: df.string(from: Date()),
            discipline: nil
        )
        _ = try? await createEntry.invoke(req: req)
        await load()
    }

    func delete(_ id: String) {
        entries.removeAll { $0.id == id }
        Task { try? await deleteEntry.invoke(id: id); await load() }
    }
}

struct JournalView: View {
    @StateObject private var vm = JournalViewModel()
    @State private var showAdd = false

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        if let s = vm.stats { JournalStatsRow(stats: s) }
                        Button { showAdd = true } label: {
                            Text("+ AÑADIR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.8)
                                .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                                .background(Cumbre.terra)
                        }
                        .buttonStyle(.plain).padding(16)
                        Divider().overlay(Cumbre.rule)
                        if vm.entries.isEmpty {
                            Text("Aún no has registrado bloques.")
                                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(32)
                        } else {
                            ForEach(vm.entries, id: \.id) { e in
                                JournalRow(entry: e, schoolId: e.schoolId) { vm.delete(e.id) }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mi diario")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAdd) {
            AddBlockSheet { block, grade, schoolId, school, sector, notes in
                Task { await vm.add(blockName: block, grade: grade, schoolId: schoolId, schoolName: school, sector: sector, notes: notes) }
            }
        }
        .task { await vm.load() }
    }
}

struct JournalStatsRow: View {
    let stats: JournalStats
    var body: some View {
        HStack(spacing: 0) {
            cell("\(stats.blockCount)", "BLOQUES")
            cell("\(stats.schoolCount)", "ESCUELAS")
            cell(stats.maxGrade ?? "—", "GRADO MÁX")
        }
        .padding(.vertical, 16)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(16)
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(v).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }.frame(maxWidth: .infinity)
    }
}

struct JournalRow: View {
    let entry: JournalSession
    /// Si se indica, la fila es pulsable y abre esa escuela (ver la piedra).
    var schoolId: String? = nil
    /// nº de piedra + sector resueltos en vivo del catálogo (no se guardan).
    var info: ViaCatalogInfo? = nil
    /// nil → fila de solo lectura (diario de otro usuario, no se puede borrar).
    var onDelete: (() -> Void)? = nil

    /// "Escuela · Piedra N · Sector" — lo que se pueda resolver del catálogo.
    private var subtitle: String {
        var parts: [String] = []
        if let sn = entry.schoolName, !sn.isEmpty { parts.append(sn) }
        if let n = info?.boulderNumber, !n.isEmpty { parts.append("Piedra \(n)") }
        if let s = info?.sector, !s.isEmpty { parts.append(s) }
        return parts.joined(separator: " · ")
    }

    private var leading: some View {
        HStack(spacing: 12) {
            // Grado ACTUAL del catálogo (refleja correcciones) o el guardado.
            if let g = (info?.grade ?? entry.grade), !g.isEmpty {
                // Texto negro sobre grados claros (≤5c son blancos) para que se lea.
                Text(g).font(Cumbre.mono(12, .bold))
                    .foregroundStyle(GradeColor.style(g).dark ? .black : .white)
                    .frame(width: 44, height: 32).background(GradeColor.color(g))
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: GradeColor.style(g).dark ? 1 : 0))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.blockName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                // Escuela + nº de piedra + sector (resueltos del catálogo en vivo).
                if !subtitle.isEmpty {
                    Text(subtitle).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
                // Ocultamos la nota auto "Piedra: N" (obsoleta: el número se recicla).
                if let n = entry.notes, !n.isEmpty, !n.hasPrefix("Piedra: ") {
                    Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                }
            }
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            // Si hay escuela, la parte izquierda navega a su detalle y abre la
            // piedra que contiene esta vía (deep-link del diario).
            if let sid = schoolId, !sid.isEmpty {
                NavigationLink(destination: SchoolLoaderView(schoolId: sid, openVia: entry.blockName)) { leading }
                    .buttonStyle(.plain)
            } else {
                leading
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(String(entry.date.prefix(10))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                if let onDelete {
                    Button(action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 14)).foregroundStyle(Cumbre.bad)
                    }.buttonStyle(.plain)
                }
                if schoolId != nil, !(schoolId ?? "").isEmpty {
                    Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Carga una escuela por id y muestra su detalle (para abrir desde el diario).
/// Si `openVia` se indica, el detalle abre la piedra que contiene esa vía.
struct SchoolLoaderView: View {
    let schoolId: String
    var openVia: String? = nil
    @State private var school: School?
    @State private var failed = false
    var body: some View {
        Group {
            if let school {
                SchoolDetailView(school: school, openVia: openVia)
            } else if failed {
                Text("No se pudo abrir la escuela.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: schoolId) {
                school = s
            } else { failed = true }
        }
    }
}

private let JOURNAL_GRADES = ["4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
    "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c", "8c+", "9a", "9a+"]

/// Sugerencia de vía real (de un bloque catalogado de la escuela).
struct LineSuggestion: Identifiable {
    let id = UUID()
    let blockName: String
    let name: String
    let grade: String?
    let startType: String?
    var label: String {
        let extras = [grade, startType].compactMap { $0 }.joined(separator: " · ")
        return (extras.isEmpty ? name : "\(name) · \(extras)") + " — \(blockName)"
    }
}

/// ViewModel del formulario: busca escuelas, carga bloques/sectores reales y el
/// historial del diario para autocompletar. Espejo de SchoolSearchViewModel.kt.
@MainActor
final class AddBlockViewModel: ObservableObject {
    @Published var schoolResults: [School] = []
    @Published var schoolBlocks: [Block] = []      // bloques reales de la escuela elegida
    @Published var historySectors: [String] = []   // sectores que ya usé en esa escuela
    @Published var historyBlocks: [String] = []     // nombres de bloque previos

    private var allJournal: [JournalSession] = []

    private let searchSchools: SearchSchoolsUseCase
    private let getMyJournal: GetMyJournalUseCase
    private let getBlocks: GetBlocksUseCase

    init(
        searchSchools: SearchSchoolsUseCase = AppDependencies.shared.container.searchSchools,
        getMyJournal: GetMyJournalUseCase = AppDependencies.shared.container.getMyJournal,
        getBlocks: GetBlocksUseCase = AppDependencies.shared.container.getBlocks
    ) {
        self.searchSchools = searchSchools
        self.getMyJournal = getMyJournal
        self.getBlocks = getBlocks
    }

    func loadJournal() async {
        allJournal = (try? await getMyJournal.invoke()) ?? []
    }

    func search(_ q: String) async {
        let query = q.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { schoolResults = []; return }
        schoolResults = (try? await searchSchools.invoke(query: query, limit: 10)) ?? []
    }

    func onSchoolSelected(_ school: School) async {
        // Historial del diario en esta escuela.
        let mine = allJournal.filter {
            $0.schoolId == school.id ||
            ($0.schoolName?.caseInsensitiveCompare(school.name) == .orderedSame)
        }
        historySectors = Array(Set(mine.compactMap { $0.sector })).sorted()
        historyBlocks = Array(Set(mine.map { $0.blockName })).sorted()
        // Bloques reales catalogados.
        schoolBlocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
    }

    func reset() {
        schoolBlocks = []; historySectors = []; historyBlocks = []
    }

    /// Sectores sugeridos: ZONE catalogados + historial del usuario.
    func sectorSuggestions(filter: String, selectedSectorId: String?) -> [(name: String, blockId: String?)] {
        let real = schoolBlocks.filter { $0.type == "ZONE" }.map { (name: $0.name, blockId: $0.id as String?) }
        let historical = historySectors.map { (name: $0, blockId: String?.none) }
        var seen = Set<String>()
        let combined = (real + historical).filter { seen.insert($0.name).inserted }
        let f = filter.trimmingCharacters(in: .whitespaces)
        let result = f.isEmpty ? combined
            : combined.filter { $0.name.localizedCaseInsensitiveContains(f) && $0.name != f }
        return Array(result.prefix(6))
    }

    /// Vías reales sugeridas (filtradas por sector si hay uno catalogado elegido).
    func lineSuggestions(filter: String, selectedSectorId: String?) -> [LineSuggestion] {
        var blocks = schoolBlocks.filter { $0.type == "BLOCK" }
        if let sid = selectedSectorId {
            blocks = blocks.filter { $0.sectorBlockId == sid }
        }
        let all = blocks.flatMap { b in
            b.lines.map { l in
                LineSuggestion(blockName: b.name,
                               name: l.name.isEmpty ? "L\(l.sortOrder + 1)" : l.name,
                               grade: l.grade, startType: l.startType)
            }
        }
        let f = filter.trimmingCharacters(in: .whitespaces)
        let result = f.isEmpty ? all
            : all.filter { $0.name.localizedCaseInsensitiveContains(f) || $0.blockName.localizedCaseInsensitiveContains(f) }
        return Array(result.prefix(6))
    }

    /// Fallback cuando la escuela no tiene vías catalogadas: nombres de bloque.
    func blockNameSuggestions(filter: String) -> [String] {
        let fromSchool = schoolBlocks.filter { $0.type == "BLOCK" }.map { $0.name }
        var seen = Set<String>()
        let combined = (historyBlocks + fromSchool).filter { seen.insert($0).inserted }
        let f = filter.trimmingCharacters(in: .whitespaces)
        let result = f.isEmpty ? combined
            : combined.filter { $0.localizedCaseInsensitiveContains(f) && $0 != f }
        return Array(result.prefix(5))
    }
}

/// Formulario para registrar un bloque escalado, con autocompletado de escuela,
/// sector y vías reales — espejo de AddBlockSheet.kt de Android.
struct AddBlockSheet: View {
    let onSave: (String, String, String?, String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm = AddBlockViewModel()
    // Los sheets no heredan el modo claro/oscuro forzado por el tema → forzarlo
    // aquí o se ve blanco brillante en modo oscuro.
    @ObservedObject private var theme = ThemeManager.shared

    @State private var block = ""
    @State private var grade = ""
    @State private var schoolQuery = ""
    @State private var selectedSchool: School?
    @State private var sector = ""
    @State private var selectedSectorId: String?
    @State private var notes = ""
    @State private var gradeMenu = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    schoolField
                    sectorField
                    blockField
                    gradeField
                    field("NOTAS", $notes, "Comentarios")
                }.padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Añadir bloque")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Cumbre.ink3) }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Guardar") {
                        let schoolName = selectedSchool?.name ?? schoolQuery
                        onSave(block, grade, selectedSchool?.id, schoolName, sector, notes); dismiss()
                    }.foregroundStyle(Cumbre.terra)
                        .disabled(block.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .task { await vm.loadJournal() }
        }
        .preferredColorScheme(theme.colorScheme)
    }

    // ─── ESCUELA con autocomplete ───
    private var schoolField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("ESCUELA").eyebrow()
            TextField("Buscar escuela…", text: $schoolQuery)
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: schoolQuery) { _, newVal in
                    if selectedSchool?.name != newVal {
                        selectedSchool = nil; vm.reset()
                        Task { await vm.search(newVal) }
                    }
                }
            if selectedSchool == nil, !vm.schoolResults.isEmpty {
                suggestionsBox {
                    ForEach(vm.schoolResults.prefix(5), id: \.id) { s in
                        suggestionRow(s.region.map { "\(s.name) · \($0)" } ?? s.name) {
                            selectedSchool = s; schoolQuery = s.name; vm.schoolResults = []
                            Task { await vm.onSchoolSelected(s) }
                        }
                    }
                }
            }
        }
    }

    // ─── SECTOR con autocomplete ───
    private var sectorField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SECTOR (opcional)").eyebrow()
            TextField("ej: Sector Bajo", text: $sector)
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: sector) { _, _ in selectedSectorId = nil }
            let sugs = vm.sectorSuggestions(filter: sector, selectedSectorId: selectedSectorId)
            if !sugs.isEmpty {
                suggestionsBox {
                    ForEach(sugs, id: \.name) { sug in
                        suggestionRow(sug.blockId != nil ? "\(sug.name) · catalogado" : sug.name) {
                            sector = sug.name; selectedSectorId = sug.blockId
                        }
                    }
                }
            }
        }
    }

    // ─── BLOQUE / VÍA con autocomplete ───
    private var blockField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("BLOQUE / VÍA").eyebrow()
            TextField("ej: El Pollito", text: $block)
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            let lines = vm.lineSuggestions(filter: block, selectedSectorId: selectedSectorId)
            if !lines.isEmpty {
                suggestionsBox {
                    ForEach(lines) { l in
                        suggestionRow(l.label) {
                            block = l.name
                            if let g = l.grade, !g.isEmpty { grade = g }
                        }
                    }
                }
            } else {
                let names = vm.blockNameSuggestions(filter: block)
                if !names.isEmpty {
                    suggestionsBox {
                        ForEach(names, id: \.self) { n in
                            suggestionRow(n) { block = n }
                        }
                    }
                }
            }
        }
    }

    // ─── GRADO (menú) ───
    private var gradeField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("GRADO").eyebrow()
            Menu {
                ForEach(JOURNAL_GRADES, id: \.self) { g in
                    Button(g) { grade = g }
                }
            } label: {
                HStack {
                    Text(grade.isEmpty ? "—" : grade).foregroundStyle(grade.isEmpty ? Cumbre.ink3 : Cumbre.ink)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .font(.system(size: 15))
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    private func suggestionsBox<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func suggestionRow(_ text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

/// Desglose del diario por escuela — espejo de la navegación "ESCUELAS" del
/// perfil de Android (`onOpenAllSchools`). Cada escuela es pulsable y abre la
/// lista de bloques registrados en esa escuela.
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
                    LazyVStack(spacing: 0) {
                        ForEach(schools, id: \.schoolName) { s in
                            NavigationLink(destination: SchoolJournalBlocksView(
                                schoolName: s.schoolName,
                                entries: entriesFor(s.schoolName),
                                viaInfo: viaInfo
                            )) {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                        let blocks = "\(s.blockCount) \(s.blockCount == 1 ? "bloque" : "bloques")"
                                        let grade = s.maxGrade.map { " · máx \($0)" } ?? ""
                                        Text(blocks + grade).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 12)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
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

/// Bloques registrados en una escuela concreta (solo lectura). Se llega desde
/// el desglose por escuela; sirve tanto para tu diario como para el de otro.
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
struct JournalBlocksListView: View {
    let title: String
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    var body: some View {
        Group {
            if entries.isEmpty {
                Text("Sin bloques registrados.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(entries, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: viaInfo[e.id])
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Stats del diario (BLOQUES/ESCUELAS/MÁXIMO) tappables — componente reutilizable
/// para tu cuenta y para el perfil público de quien sigues.
struct JournalStatsNav: View {
    let stats: JournalStats
    let entries: [JournalSession]
    var viaInfo: [String: ViaCatalogInfo] = [:]
    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                NavigationLink(destination: JournalBlocksListView(title: "Bloques", entries: entries, viaInfo: viaInfo)) {
                    cell("\(stats.boulderCount)", "BLOQUES")
                }.buttonStyle(.plain)
                NavigationLink(destination: JournalBlocksListView(title: "Vías", entries: entries, viaInfo: viaInfo)) {
                    cell("\(stats.routeCount)", "VÍAS")
                }.buttonStyle(.plain)
                NavigationLink(destination: JournalSchoolsView(schools: stats.bySchool, entries: entries, viaInfo: viaInfo)) {
                    cell("\(stats.schoolCount)", "ESCUELAS")
                }.buttonStyle(.plain)
            }
            HStack(spacing: 8) {
                cell(stats.maxBoulderGrade ?? "—", "MÁX BLOQUE")
                cell(stats.maxRouteGrade ?? "—", "MÁX VÍA")
            }
        }
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(v).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

private extension String { var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self } }
