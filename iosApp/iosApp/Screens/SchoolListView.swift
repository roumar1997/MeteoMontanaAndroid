import SwiftUI
import Shared

// Lista de escuelas desde el backend (use case compartido) con el score de hoy
// de cada una, buscador y filtros por estilo/roca. Pipeline: framework KMP →
// DI Kotlin → use case (SKIE async) → SwiftUI. Los scores se cargan en lotes
// tras la lista (igual que Android). El filtrado es local (sin red extra).

@MainActor
final class SchoolListViewModel: ObservableObject {
    @Published var schools: [School] = []
    @Published var scores: [String: SchoolScore] = [:]
    @Published var loading = true
    @Published var errorText: String?

    // Filtros (aplicados en local sobre la lista cargada).
    @Published var query = ""
    @Published var style: String?
    @Published var rock: String?

    private let getSchools: GetSchoolsUseCase
    private let getTodayScores: GetTodayScoresUseCase

    init(
        getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools,
        getTodayScores: GetTodayScoresUseCase = AppDependencies.shared.container.getTodayScores
    ) {
        self.getSchools = getSchools
        self.getTodayScores = getTodayScores
    }

    /// Valores únicos de estilo/roca presentes en el catálogo, para los menús.
    var styles: [String] { uniqueValues(schools.map { $0.style }) }
    var rocks: [String] { uniqueValues(schools.map { $0.rockType }) }

    /// Lista filtrada por texto + estilo + roca, ordenada por score desc.
    var filtered: [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return schools.filter { s in
            (q.isEmpty || s.name.lowercased().contains(q) || (s.location?.lowercased().contains(q) ?? false))
            && (style == nil || s.style?.caseInsensitiveCompare(style!) == .orderedSame)
            && (rock == nil || s.rockType?.caseInsensitiveCompare(rock!) == .orderedSame)
        }.sorted { (scores[$0.id]?.todayScore ?? -1) > (scores[$1.id]?.todayScore ?? -1) }
    }

    var activeFilters: Bool { style != nil || rock != nil }

    func clearFilters() { style = nil; rock = nil; query = "" }

    func load() async {
        loading = true
        errorText = nil
        do {
            schools = try await getSchools.invoke(
                region: nil, style: nil, rockType: nil,
                lat: nil, lon: nil, radioKm: nil
            )
            loading = false
            await loadScores()
        } catch {
            errorText = error.localizedDescription
            loading = false
        }
    }

    /// Scores en lotes de 50 (mismo enfoque que Android para no saturar el back).
    private func loadScores() async {
        let ids = schools.map { $0.id }
        for chunk in stride(from: 0, to: ids.count, by: 50) {
            let slice = Array(ids[chunk..<min(chunk + 50, ids.count)])
            guard let batch = try? await getTodayScores.invoke(ids: slice) else { continue }
            for s in batch { scores[s.id] = s }
        }
    }

    private func uniqueValues(_ raw: [String?]) -> [String] {
        Array(Set(raw.compactMap { $0 }.filter { !$0.isEmpty })).sorted()
    }
}

struct SchoolListView: View {
    @StateObject private var vm = SchoolListViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if vm.loading {
                    ProgressView()
                } else if let err = vm.errorText {
                    ContentUnavailableView("Sin conexión", systemImage: "wifi.slash", description: Text(err))
                } else {
                    listContent
                }
            }
            .navigationTitle("Escuelas")
            .searchable(text: $vm.query, prompt: "Buscar escuela o lugar")
            .toolbar { filterMenu }
            .task { await vm.load() }
        }
    }

    @ViewBuilder private var listContent: some View {
        let items = vm.filtered
        if items.isEmpty {
            ContentUnavailableView {
                Label("Sin resultados", systemImage: "magnifyingglass")
            } description: {
                Text("Prueba a quitar filtros o cambiar la búsqueda.")
            } actions: {
                if vm.activeFilters || !vm.query.isEmpty {
                    Button("Quitar filtros") { vm.clearFilters() }
                }
            }
        } else {
            List(items, id: \.id) { school in
                NavigationLink(destination: SchoolDetailView(school: school)) {
                    SchoolRow(school: school, score: vm.scores[school.id])
                }
            }
        }
    }

    private var filterMenu: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Picker("Estilo", selection: $vm.style) {
                    Text("Todos los estilos").tag(String?.none)
                    ForEach(vm.styles, id: \.self) { Text($0).tag(String?.some($0)) }
                }
                Picker("Roca", selection: $vm.rock) {
                    Text("Todas las rocas").tag(String?.none)
                    ForEach(vm.rocks, id: \.self) { Text($0).tag(String?.some($0)) }
                }
                if vm.activeFilters {
                    Divider()
                    Button(role: .destructive) { vm.clearFilters() } label: {
                        Label("Quitar filtros", systemImage: "xmark.circle")
                    }
                }
            } label: {
                Image(systemName: vm.activeFilters ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    .foregroundStyle(Cumbre.terra)
            }
        }
    }
}

/// Fila de escuela: badge de score (color por valor) + nombre + lugar + roca.
private struct SchoolRow: View {
    let school: School
    let score: SchoolScore?

    var body: some View {
        HStack(spacing: 12) {
            scoreBadge
            VStack(alignment: .leading, spacing: 2) {
                Text(school.name)
                    .font(.system(size: 16, weight: .semibold, design: .serif))
                    .foregroundStyle(Cumbre.ink)
                HStack(spacing: 6) {
                    if let loc = school.location {
                        Text(loc).font(.caption).foregroundStyle(Cumbre.ink3)
                    }
                    if let s = score, !s.dryRock {
                        Text("· MOJADA")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundStyle(Cumbre.rain)
                    }
                }
            }
        }
    }

    @ViewBuilder private var scoreBadge: some View {
        if let s = score {
            Text("\(s.todayScore)")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(Cumbre.score(Int(s.todayScore)))
                .clipShape(RoundedRectangle(cornerRadius: 2))
        } else {
            RoundedRectangle(cornerRadius: 2)
                .fill(Cumbre.rule)
                .frame(width: 40, height: 40)
                .overlay(ProgressView().scaleEffect(0.5))
        }
    }
}

#Preview {
    SchoolListView()
}
