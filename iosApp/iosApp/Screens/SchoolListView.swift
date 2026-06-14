import SwiftUI
import Shared

// Pantalla MVP de referencia: lista de escuelas desde el backend, vía el use
// case compartido. Demuestra el pipeline completo: framework KMP → DI Kotlin →
// use case (SKIE async) → SwiftUI. El resto de pantallas se escriben igual.
//
// ⚠️ Escrito sin Mac (Fase C, plantilla). Al primer build en Xcode (Fase E)
// puede haber que ajustar la firma de `getSchools.invoke(...)` que genera SKIE
// (nombres de parámetros, opcionales boxed como KotlinDouble?).

@MainActor
final class SchoolListViewModel: ObservableObject {
    @Published var schools: [School] = []
    @Published var loading = true
    @Published var errorText: String?

    private let getSchools: GetSchoolsUseCase

    init(getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools) {
        self.getSchools = getSchools
    }

    func load() async {
        loading = true
        errorText = nil
        do {
            // SKIE expone el `suspend operator fun invoke` como `async throws`.
            let result = try await getSchools.invoke(
                region: nil, style: nil, rockType: nil,
                lat: nil, lon: nil, radioKm: nil
            )
            schools = result
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
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
                        VStack(alignment: .leading, spacing: 2) {
                            Text(school.name)
                                .font(.headline)
                            if let loc = school.location {
                                Text(loc)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Escuelas")
            .task { await vm.load() }
        }
    }
}

#Preview {
    SchoolListView()
}
