import SwiftUI
import Shared

// Lista de escuelas desde el backend (use case compartido) con el score de hoy
// de cada una. Pipeline: framework KMP → DI Kotlin → use case (SKIE async) →
// SwiftUI. Los scores se cargan en lotes tras la lista (igual que Android).

@MainActor
final class SchoolListViewModel: ObservableObject {
    @Published var schools: [School] = []
    @Published var scores: [String: SchoolScore] = [:]
    @Published var loading = true
    @Published var errorText: String?

    private let getSchools: GetSchoolsUseCase
    private let getTodayScores: GetTodayScoresUseCase

    init(
        getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools,
        getTodayScores: GetTodayScoresUseCase = AppDependencies.shared.container.getTodayScores
    ) {
        self.getSchools = getSchools
        self.getTodayScores = getTodayScores
    }

    func load() async {
        loading = true
        errorText = nil
        do {
            // SKIE expone el `suspend operator fun invoke` como `async throws`.
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
                    List(vm.schools, id: \.id) { school in
                        NavigationLink(destination: SchoolDetailView(school: school)) {
                            SchoolRow(school: school, score: vm.scores[school.id])
                        }
                    }
                }
            }
            .navigationTitle("Escuelas")
            .task { await vm.load() }
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
            // Placeholder mientras llega el score del lote.
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
