import SwiftUI
import Shared

/// "Proyectos": bloques/vías que estás probando pero aún no te han salido.
/// Igual que Vías/Bloques hechos, con su propio split BLOQUES/VÍAS. Espejo de
/// ProjectsScreen.kt (Android).
@MainActor
final class ProjectsViewModel: ObservableObject {
    @Published var loading = true
    @Published var boulderCount = 0
    @Published var routeCount = 0
    @Published var projectEntries: [JournalSession] = []
    @Published var error: String?

    private let getMyJournal = AppDependencies.shared.container.getMyJournal
    private let getUserJournal: GetUserJournalUseCase = AppDependencies.shared.container.getUserJournal
    let uid: String?

    init(uid: String? = nil) { self.uid = uid }

    func load() async {
        loading = true
        do {
            let all: [JournalSession]
            if let uid { all = try await getUserJournal.invoke(uid: uid) }
            else { all = try await getMyJournal.invoke() }
            let projects = all.filter { $0.status == "PROJECT" }
            projectEntries = projects
            routeCount = projects.filter { $0.discipline.caseInsensitiveCompare("ROUTE") == .orderedSame }.count
            boulderCount = projects.count - routeCount
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

struct ProjectsView: View {
    let uid: String?
    @StateObject private var vm: ProjectsViewModel
    @State private var filterDiscipline: String?   // nil=todos · "BOULDER" · "ROUTE"

    init(uid: String? = nil) {
        self.uid = uid
        _vm = StateObject(wrappedValue: ProjectsViewModel(uid: uid))
    }

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let err = vm.error {
                Text(err).foregroundStyle(Cumbre.bad)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.boulderCount == 0 && vm.routeCount == 0 {
                EmptyStateView(
                    icon: "flag",
                    title: "Sin proyectos todavía",
                    message: "Marca la P de una vía dentro de su piedra para probarla como proyecto."
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if filterDiscipline == nil {
                // Vista de stats: pulsar BLOQUES/VÍAS entra al listado filtrado.
                HStack(spacing: 12) {
                    statCell("BLOQUES", vm.boulderCount) { filterDiscipline = "BOULDER" }
                    statCell("VÍAS", vm.routeCount) { filterDiscipline = "ROUTE" }
                }
                .padding(16)
                Spacer()
            } else {
                let shown = vm.projectEntries.filter {
                    ($0.discipline.caseInsensitiveCompare("ROUTE") == .orderedSame) == (filterDiscipline == "ROUTE")
                }
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(shown, id: \.id) { e in
                            JournalRow(entry: e, schoolId: e.schoolId, info: nil)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(filterDiscipline == "BOULDER" ? "Proyectos · bloques"
                          : filterDiscipline == "ROUTE" ? "Proyectos · vías" : "Proyectos")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if filterDiscipline != nil {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { filterDiscipline = nil } label: {
                        Image(systemName: "chevron.left")
                    }
                }
            }
        }
        .task { await vm.load() }
    }

    private func statCell(_ label: String, _ count: Int, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            VStack(spacing: 3) {
                Text("\(count)").font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                Text(label).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
