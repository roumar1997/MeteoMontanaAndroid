import SwiftUI
import Shared

// Apuntar un bloque en el diario: buscador de escuela, sugerencias de vías reales y el formulario.

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
                SkeletonSchoolDetail()
            }
        }
        .task {
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: schoolId) {
                school = s
            } else { failed = true }
        }
    }
}

let JOURNAL_GRADES = ["4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
    "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c", "8c+", "9a", "9a+"]

/// Sugerencia de vía real (de un bloque catalogado de la escuela).
struct LineSuggestion: Identifiable {
    let id = UUID()
    let blockName: String
    let name: String
    let grade: String?
    let startType: String?
    var discipline: String = "BOULDER"   // BOULDER (bloque) / ROUTE (vía) — de la piedra
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
                               grade: l.grade, startType: l.startType,
                               discipline: b.discipline)
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
    // (block, grade, schoolId, school, sector, notes, discipline)
    let onSave: (String, String, String?, String, String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm = AddBlockViewModel()
    // Los sheets no heredan el modo claro/oscuro forzado por el tema → forzarlo
    // aquí o se ve blanco brillante en modo oscuro.
    @ObservedObject private var theme = ThemeManager.shared

    @State private var block = ""
    // Modalidad: BOULDER (bloque) / ROUTE (vía). Antes se mandaba nil → la entrada
    // caía siempre en "Bloques" y nunca en "Vías" (el diario separa por discipline).
    @State private var discipline = "BOULDER"
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
                    modalityField
                    blockField
                    gradeField
                    field(L("NOTAS"), $notes, L("Comentarios"))
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
                        onSave(block, grade, selectedSchool?.id, schoolName, sector, notes, discipline); dismiss()
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
                .dismissKeyboardOnSubmit()
                .onChange(of: schoolQuery) { _, newVal in
                    if selectedSchool?.name != newVal {
                        selectedSchool = nil; vm.reset()
                        Task { await vm.search(newVal) }
                    }
                }
            if selectedSchool == nil, !vm.schoolResults.isEmpty {
                suggestionsBox {
                    ForEach(vm.schoolResults.prefix(5), id: \.id) { s in
                        suggestionRow(s.region.map { "\(s.name) · \(regionLabel($0))" } ?? s.name) {
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
                        suggestionRow(sug.blockId != nil ? L("%@ · catalogado", sug.name) : sug.name) {
                            sector = sug.name; selectedSectorId = sug.blockId
                        }
                    }
                }
            }
        }
    }

    // ─── MODALIDAD: bloque o vía (decide en qué lista del diario cae) ───
    private var modalityField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("MODALIDAD").eyebrow()
            HStack(spacing: 8) {
                modalityOption(L("BLOQUE"), active: discipline == "BOULDER") { discipline = "BOULDER" }
                modalityOption(L("VÍA"), active: discipline == "ROUTE") { discipline = "ROUTE" }
            }
        }
    }

    private func modalityOption(_ text: String, active: Bool, _ tap: @escaping () -> Void) -> some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(active ? Color.white : Cumbre.ink)
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(active ? Cumbre.terra : Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .contentShape(Rectangle())
            .onTapGesture(perform: tap)
    }

    // ─── BLOQUE / VÍA con autocomplete ───
    private var blockField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(discipline == "ROUTE" ? L("VÍA") : L("BLOQUE")).eyebrow()
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
                            discipline = l.discipline   // hereda la modalidad de la vía catalogada
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
