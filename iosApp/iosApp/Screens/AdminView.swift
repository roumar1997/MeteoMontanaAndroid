import SwiftUI
import Shared
import CoreLocation

// Panel de admin — cola de propuestas de escuela y contribuciones de mejora
// pendientes, con aprobar / rechazar (con motivo). Espejo parcial de
// AdminScreen.kt: el mini-mapa y "VER EN MAPA" llegarán con el bridge de MapLibre.
// Solo accesible si el perfil es admin.

@MainActor
final class AdminViewModel: ObservableObject {
    @Published var submissions: [Submission] = []
    @Published var contributions: [Contribution] = []
    @Published var loading = true
    @Published var working: Set<String> = []   // ids en curso (evita doble tap)
    @Published var stats: AdminStats?
    @Published var logs: [AdminLog] = []
    @Published var pushResult: String?
    @Published var pushBusy = false

    private let c = AppDependencies.shared.container
    private let getSubs = AppDependencies.shared.container.getPendingSubmissions
    private let getContribs = AppDependencies.shared.container.getPendingContributions
    private let approveSub = AppDependencies.shared.container.approveSubmission
    private let rejectSub = AppDependencies.shared.container.rejectSubmission
    private let approveContrib = AppDependencies.shared.container.approveContribution
    private let rejectContrib = AppDependencies.shared.container.rejectContribution

    func load() async {
        loading = true
        submissions = (try? await getSubs.invoke()) ?? []
        contributions = (try? await getContribs.invoke()) ?? []
        loading = false
    }

    @Published var allSchools: [School] = []

    func loadStats() async { stats = try? await c.getAdminStats.invoke() }
    func loadLogs() async { logs = (try? await c.getAdminLogs.invoke(limit: 100)) ?? [] }
    func loadSchools() async {
        allSchools = (try? await c.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                      lat: nil, lon: nil, radioKm: nil)) ?? []
    }

    func sendPush(targetUid: String?, title: String, body: String) {
        pushBusy = true; pushResult = nil
        Task {
            if let r = try? await c.sendPush.invoke(targetUid: targetUid, title: title, body: body) {
                pushResult = "Enviado a \(r.sent)/\(r.recipients)"
            } else { pushResult = "Error al enviar" }
            pushBusy = false
        }
    }

    func reviewSubmission(_ id: String, approve: Bool, reason: String?) {
        working.insert(id)
        Task {
            if approve { _ = try? await approveSub.invoke(id: id) }
            else { _ = try? await rejectSub.invoke(id: id, reason: reason) }
            submissions.removeAll { $0.id == id }
            working.remove(id)
        }
    }

    /// "EDITAR Y APROBAR": aprueba con el bloquesJson retocado por el admin.
    func approveContributionEdited(_ id: String, editedBloquesJson: String) {
        working.insert(id)
        Task {
            _ = try? await approveContrib.invoke(id: id, editedBloquesJson: editedBloquesJson)
            working.remove(id)
            contributions.removeAll { $0.id == id }
        }
    }

    func reviewContribution(_ id: String, approve: Bool, reason: String?) {
        working.insert(id)
        Task {
            // editedBloquesJson: los data class de Kotlin no exportan defaults a
            // Swift → hay que pasar nil explícito. El "EDITAR Y APROBAR" de iOS
            // llegará en la siguiente tanda (Android ya lo tiene).
            if approve { _ = try? await approveContrib.invoke(id: id, editedBloquesJson: nil) }
            else { _ = try? await rejectContrib.invoke(id: id, reason: reason) }
            contributions.removeAll { $0.id == id }
            working.remove(id)
        }
    }
}

private enum ContribFilter: String, CaseIterable {
    case todas = "TODAS", piedras = "PIEDRAS", sectores = "SECTORES"
    case parkings = "PARKINGS", mover = "MOVER"
    func matches(_ t: String) -> Bool {
        switch self {
        case .todas: return true
        case .piedras: return t.uppercased() == "BOULDER"
        case .sectores: return t.uppercased() == "SECTOR"
        case .parkings: return t.uppercased() == "PARKING"
        case .mover: return t.uppercased() == "POSITION_CORRECTION"
        }
    }
}

struct AdminView: View {
    /// Si viene true (desde el push de una denuncia) abre en la pestaña DENUNCIAS.
    var openDenuncias: Bool = false
    @StateObject private var vm = AdminViewModel()
    @State private var rejecting: RejectTarget?
    @State private var filter: ContribFilter = .todas

    private var filteredContributions: [Contribution] {
        vm.contributions.filter { filter.matches($0.type) }
    }
    private var groupedBySchool: [(school: String, items: [Contribution])] {
        let groups = Dictionary(grouping: filteredContributions, by: { $0.schoolName })
        return groups.keys.sorted().map { (school: $0, items: groups[$0] ?? []) }
    }

    @State private var tab: AdminTab = .propuestas

    var body: some View {
        VStack(spacing: 0) {
            // Selector de tabs (espejo de AdminScreen.kt).
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(AdminTab.allCases, id: \.self) { t in
                        let on = tab == t
                        Button { tab = t } label: {
                            Text(t.rawValue).font(Cumbre.mono(11, .bold)).tracking(0.8)
                                .foregroundStyle(on ? .white : Cumbre.ink2)
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .background(on ? Cumbre.terra : Color.clear)
                                .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 16).padding(.vertical, 8)
            }
            Divider().overlay(Cumbre.rule)
            switch tab {
            case .propuestas: propuestasContent
            case .denuncias: AdminReportsTab()
            case .gestionar: GestionarTab(vm: vm)
            case .stats: AdminStatsTab(stats: vm.stats, onGoToTab: { tab = $0 })
            case .actividad: AdminLogsTab(logs: vm.logs)
            case .push: AdminPushTab(vm: vm)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .onAppear { if openDenuncias { tab = .denuncias } }
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
        .task(id: tab) {
            if tab == .stats, vm.stats == nil { await vm.loadStats() }
            if tab == .actividad, vm.logs.isEmpty { await vm.loadLogs() }
            if tab == .gestionar, vm.allSchools.isEmpty { await vm.loadSchools() }
        }
        .sheet(item: $rejecting) { target in
            RejectReasonSheet { reason in
                if target.isSubmission { vm.reviewSubmission(target.id, approve: false, reason: reason) }
                else { vm.reviewContribution(target.id, approve: false, reason: reason) }
            }
        }
    }

    private var propuestasContent: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.submissions.isEmpty && vm.contributions.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "checkmark.seal").font(.system(size: 36)).foregroundStyle(Cumbre.ok)
                    Text("Nada pendiente de revisar.").font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                        if !vm.submissions.isEmpty {
                            Section {
                                ForEach(vm.submissions, id: \.id) { s in
                                    SubmissionAdminCard(
                                        submission: s,
                                        busy: vm.working.contains(s.id),
                                        onApprove: { vm.reviewSubmission(s.id, approve: true, reason: nil) },
                                        onReject: { rejecting = RejectTarget(id: s.id, isSubmission: true) }
                                    )
                                    Divider().overlay(Cumbre.rule)
                                }
                            } header: { sectionHeader("ESCUELAS NUEVAS · \(vm.submissions.count)") }
                        }
                        if !vm.contributions.isEmpty {
                            Section {
                                // Chips de filtro por tipo.
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 6) {
                                        ForEach(ContribFilter.allCases, id: \.self) { f in
                                            let on = filter == f
                                            Button { filter = f } label: {
                                                Text(f.rawValue).font(Cumbre.mono(10, .bold)).tracking(0.6)
                                                    .foregroundStyle(on ? .white : Cumbre.ink2)
                                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                                    .background(on ? Cumbre.ink : Color.clear)
                                                    .overlay(Rectangle().stroke(on ? Cumbre.ink : Cumbre.rule, lineWidth: 1))
                                            }.buttonStyle(.plain)
                                        }
                                    }.padding(.horizontal, 16).padding(.vertical, 8)
                                }
                                ForEach(groupedBySchool, id: \.school) { group in
                                    Text("\(group.school.uppercased()) · \(group.items.count)")
                                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.horizontal, 16).padding(.top, 8)
                                    ForEach(group.items, id: \.id) { c in
                                        ContributionAdminCard(
                                            contribution: c,
                                            busy: vm.working.contains(c.id),
                                            onApprove: { vm.reviewContribution(c.id, approve: true, reason: nil) },
                                            onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) },
                                            onApproveEdited: { edited in vm.approveContributionEdited(c.id, editedBloquesJson: edited) }
                                        )
                                        Divider().overlay(Cumbre.rule)
                                    }
                                }
                            } header: { sectionHeader("MEJORAS · \(filteredContributions.count)") }
                        }
                    }
                }
            }
        }
    }

    private func sectionHeader(_ t: String) -> some View {
        Text(t).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(Cumbre.bg)
    }
}

private struct RejectTarget: Identifiable { let id: String; let isSubmission: Bool }
