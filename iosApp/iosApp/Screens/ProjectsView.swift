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
    /// Piedra, sector, grado y miniatura de cada proyecto, del catálogo.
    @Published var viaInfo: [String: ViaCatalogInfo] = [:]
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
            viaInfo = (try? await AppDependencies.shared.container.getJournalViaInfo.invoke(entries: projects)) ?? [:]
            routeCount = projects.filter { $0.discipline.caseInsensitiveCompare("ROUTE") == .orderedSame }.count
            boulderCount = projects.count - routeCount
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

/// Una sola pantalla (Álvaro, 2026-10-28): selector «Bloques N | Vías N» con el estilo del de Escuelas/Vías-Bloques y
/// la lista justo debajo. Antes eran dos cuadrados que llevaban a otra pantalla con un segundo botón de volver.
struct ProjectsView: View {
    let uid: String?
    @StateObject private var vm: ProjectsViewModel
    /// Última pestaña usada ("BOULDER" / "ROUTE"). Si esa está vacía y la otra no, se abre la otra.
    @AppStorage("projects.discipline") private var lastDiscipline = "BOULDER"
    @State private var discipline = "BOULDER"

    init(uid: String? = nil) {
        self.uid = uid
        _vm = StateObject(wrappedValue: ProjectsViewModel(uid: uid))
    }

    var body: some View {
        Group {
            if vm.loading {
                SkeletonList(count: 6) { SkeletonGradeRow() }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if let err = vm.error {
                Text(err).foregroundStyle(Cumbre.bad)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.boulderCount == 0 && vm.routeCount == 0 {
                EmptyStateView(
                    icon: "flag",
                    title: L("Sin proyectos todavía"),
                    message: L("Marca la P de una vía dentro de su piedra para probarla como proyecto.")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 0) {
                    switcher.padding(.vertical, 10)
                    list
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(L("Proyectos"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await vm.load()
            discipline = startingTab
        }
    }

    /// La última usada, salvo que esté vacía y la otra no.
    private var startingTab: String {
        let count = lastDiscipline == "ROUTE" ? vm.routeCount : vm.boulderCount
        if count > 0 { return lastDiscipline }
        return vm.boulderCount > 0 ? "BOULDER" : "ROUTE"
    }

    private var switcher: some View {
        HStack(spacing: 2) {
            tab(L("Bloques"), count: vm.boulderCount, value: "BOULDER")
            tab(L("Vías"), count: vm.routeCount, value: "ROUTE")
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.18), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        .padding(.horizontal, 12)
    }

    private func tab(_ title: String, count: Int, value: String) -> some View {
        let active = discipline == value
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) { discipline = value }
            lastDiscipline = value
        } label: {
            HStack(spacing: 6) {
                Text(title).font(Cumbre.mono(12, .bold))
                Text("\(count)").font(Cumbre.mono(12, .bold)).opacity(0.7)
            }
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .background(active ? Cumbre.terra : Color.clear, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius - 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    @ViewBuilder private var list: some View {
        let shown = vm.projectEntries.filter {
            ($0.discipline.caseInsensitiveCompare("ROUTE") == .orderedSame) == (discipline == "ROUTE")
        }
        if shown.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "flag").font(.system(size: 30)).foregroundStyle(Cumbre.ink3)
                Text(discipline == "ROUTE" ? L("Aún no tienes vías como proyecto") : L("Aún no tienes bloques como proyecto"))
                    .font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
                Text(L("Marca la P de una vía dentro de su piedra para probarla como proyecto."))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2).multilineTextAlignment(.center)
            }
            .padding(32)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(shown, id: \.id) { e in
                        JournalRow(entry: e, schoolId: e.schoolId, info: vm.viaInfo[e.id])
                        Divider().overlay(Cumbre.rule)
                    }
                }
            }
        }
    }
}
