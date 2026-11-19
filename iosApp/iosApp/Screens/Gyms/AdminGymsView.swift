import SwiftUI
import Shared

/// Panel de admin → «Rocódromos» (solo iOS): cola de reportes pendientes (correcciones y altas, con foto) para
/// aplicar o rechazar, y edición directa de una ficha. Solo se ve con `gyms_enabled` encendido.
@MainActor
final class AdminGymsViewModel: ObservableObject {
    @Published var reports: [GymReport] = []
    @Published var gyms: [Gym] = []
    @Published var loading = true
    @Published var errorText: String?
    @Published var working: Set<Int64> = []
    private let c = AppDependencies.shared.container.gyms

    func load() async {
        do {
            reports = try await c.listPendingGymReports.execute()
            errorText = nil
        } catch {
            errorText = L("No se pudo cargar. Comprueba tu conexión.")
        }
        loading = false
    }

    func loadGyms() async {
        guard gyms.isEmpty else { return }
        gyms = c.getCachedGyms.invoke()
        if let fresh = try? await c.getGyms.execute() { gyms = fresh }
    }

    func resolve(_ report: GymReport, apply: Bool) {
        guard !working.contains(report.id) else { return }
        working.insert(report.id)
        Task {
            do {
                if apply { _ = try await c.applyGymReport.execute(id: report.id) } else { _ = try await c.rejectGymReport.execute(id: report.id) }
                reports.removeAll { $0.id == report.id }
                errorText = nil
            } catch {
                errorText = L("No se pudo completar la acción. Inténtalo de nuevo.")
            }
            working.remove(report.id)
        }
    }

    func edit(_ id: Int64, _ edit: GymAdminEdit) async -> Bool {
        guard let updated = try? await c.adminEditGym.execute(id: id, edit: edit) else { return false }
        gyms = gyms.map { $0.id == id ? updated : $0 }
        return true
    }
}

struct AdminGymsView: View {
    private enum Pane: String, CaseIterable { case reports = "REPORTES", edit = "FICHAS" }
    @StateObject private var vm = AdminGymsViewModel()
    @State private var section: Pane = .reports
    @State private var query = ""
    @State private var editing: GymEditTarget?

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $section) {
                ForEach(Pane.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented).padding(12)
            if section == .reports { reportsList } else { gymsList }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Rocódromos")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
        .task(id: section) { if section == .edit { await vm.loadGyms() } }
        .sheet(item: $editing) { target in
            GymAdminEditSheet(gym: target.gym) { edit in await vm.edit(target.gym.id, edit) }
        }
    }

    // MARK: - Reportes

    @ViewBuilder private var reportsList: some View {
        if vm.loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                VStack(spacing: 12) {
                    if let err = vm.errorText { Text(err).font(.system(size: 13)).foregroundStyle(Cumbre.bad) }
                    if vm.reports.isEmpty && vm.errorText == nil {
                        Text("No hay reportes pendientes.").foregroundStyle(Cumbre.ink3).padding(.top, 40)
                    }
                    ForEach(vm.reports, id: \.id) { report in reportCard(report) }
                }
                .padding(.horizontal, 16).padding(.bottom, 32)
            }
            .refreshable { await vm.load() }
        }
    }

    private func reportCard(_ report: GymReport) -> some View {
        let isNew = report.kind == .newGym
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                AdminChip(text: isNew ? "ALTA" : "CORRECCIÓN", color: isNew ? Cumbre.ok : Cumbre.terra, filled: true)
                Text(report.gymName ?? report.proposed?.name ?? "—").font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
            }
            if !report.issues.isEmpty {
                FlowLayoutView(spacing: 6) {
                    ForEach(report.issues, id: \.self) { AdminChip(text: issueLabel($0), color: Cumbre.ink2) }
                }
            }
            if let p = report.proposed {
                Text("\(GymPresentation.kindLabel(p.kind)) · \(String(format: "%.5f, %.5f", p.lat, p.lon))")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            }
            if let comment = report.comment, !comment.isEmpty {
                Text(comment).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            }
            if let photo = report.photoUrl, let url = URL(string: photo) {
                CachedImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Cumbre.paper }
                    .frame(maxWidth: .infinity).frame(height: 180).clipShape(RoundedRectangle(cornerRadius: 10))
            }
            Text("\(report.authorUid.prefix(8)) · \(report.createdAt)").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            HStack(spacing: 8) {
                AdminButton(title: "RECHAZAR", symbol: "xmark", kind: .outline, busy: vm.working.contains(report.id)) {
                    vm.resolve(report, apply: false)
                }
                AdminButton(title: "APLICAR", symbol: "checkmark", kind: .primary, busy: vm.working.contains(report.id)) {
                    vm.resolve(report, apply: true)
                }
            }
        }
        .adminCard()
    }

    private func issueLabel(_ code: String) -> String {
        let keys = ["IS_FREE": "gym_issue_free", "IS_PAID": "gym_issue_paid", "CLOSED": "gym_issue_closed", "IS_BOULDER": "gym_issue_boulder",
                    "IS_ROPE": "gym_issue_rope", "WRONG_LOCATION": "gym_issue_location", "OTHER": "gym_issue_other"]
        return keys[code].map { L($0) } ?? code
    }

    // MARK: - Fichas

    private var gymsList: some View {
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        let matches = vm.gyms.filter { needle.isEmpty || $0.name.lowercased().contains(needle) || $0.city.lowercased().contains(needle) }
        return VStack(spacing: 0) {
            GymSearchField(text: $query)
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(matches.prefix(80)), id: \.id) { gym in
                        Button { editing = GymEditTarget(gym: gym) } label: {
                            HStack(spacing: 10) {
                                GymKindBadge(kind: gym.kind, size: 34)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(gym.name).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                    Text(gym.city).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                                }
                                Spacer(minLength: 0)
                                if gym.hidden { AdminChip(text: "OCULTO", color: Cumbre.bad) }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 10).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Divider().overlay(Cumbre.rule)
                    }
                }
            }
        }
    }
}

/// Ficha elegida para editar (`Gym` es una clase de Kotlin: no es `Identifiable` por sí sola).
struct GymEditTarget: Identifiable {
    let gym: Gym
    var id: Int64 { gym.id }
}
