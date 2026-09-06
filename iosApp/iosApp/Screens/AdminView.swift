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
    @Published var suggestions: [AdminSuggestionRow]?
    /// Historial APROBADAS/RECHAZADAS (Álvaro, 2026-09-06: "poder ver las que
    /// rechacé las que aprobé, con quien lo puso").
    @Published var activityStatus: String = "APPROVED"
    @Published var activitySubmissions: [Submission] = []
    @Published var activityContributions: [Contribution] = []
    @Published var activityLoading = false

    private let c = AppDependencies.shared.container
    private let getSubs = AppDependencies.shared.container.getPendingSubmissions
    private let getContribs = AppDependencies.shared.container.getPendingContributions
    private let approveSub = AppDependencies.shared.container.approveSubmission
    private let rejectSub = AppDependencies.shared.container.rejectSubmission
    private let approveContrib = AppDependencies.shared.container.approveContribution
    private let rejectContrib = AppDependencies.shared.container.rejectContribution

    func load() async {
        loading = true
        submissions = (try? await getSubs.invoke(status: nil)) ?? []
        contributions = (try? await getContribs.invoke(status: nil)) ?? []
        loading = false
    }

    @Published var allSchools: [School] = []

    func loadStats() async { stats = try? await c.getAdminStats.invoke() }
    func loadLogs() async { logs = (try? await c.getAdminLogs.invoke(limit: 100)) ?? [] }

    func loadActivity(status: String? = nil) async {
        if let status { activityStatus = status }
        activityLoading = true
        async let subs = getSubs.invoke(status: activityStatus)
        async let contribs = getContribs.invoke(status: activityStatus)
        activitySubmissions = (try? await subs) ?? []
        activityContributions = (try? await contribs) ?? []
        activityLoading = false
    }
    func loadSchools() async {
        allSchools = (try? await c.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                      lat: nil, lon: nil, radioKm: nil)) ?? []
    }

    func loadSuggestions() async {
        suggestions = (try? await c.getAdminSuggestions.invoke()) ?? []
    }

    func respondToSuggestion(_ id: String, resolved: Bool?, reply: String?) {
        let resolvedK = resolved.map { KotlinBoolean(bool: $0) }
        Task {
            if let updated = try? await c.respondToSuggestion.invoke(id: id, resolved: resolvedK, reply: reply) {
                suggestions = suggestions?.map { $0.id == id ? updated : $0 }
            }
        }
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

    /// Álvaro: "pensé que había hablado lo de todo directamente" — en la
    /// maqueta se veía todo abierto de golpe, no plegado a base de toques.
    /// Todas las secciones empiezan abiertas; se pueden plegar si molestan,
    /// pero el estado de entrada es "todo visible" (2026-09-06).
    @State private var openSections: Set<String> = ["propuestas", "stats", "actividad", "sugerencias", "push"]
    @State private var openDenunciasScreen = false
    @State private var openGestionarScreen = false

    /// Una sola pantalla larga, sin barra de pestañas — Álvaro, 2026-09-06:
    /// "quiero que todo el admin sea una sola pantalla". Denuncias y
    /// Gestionar (llevan mapa) se abren aparte al tocarlas: un mapa dentro de
    /// un scroll dentro de otro scroll da problemas de gestos reales.
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                section("PROPUESTAS", key: "propuestas", badge: vm.stats?.submissionsPending) { propuestasContent }
                section("STATS", key: "stats") { AdminStatsTab(stats: vm.stats, onGoToTab: { _ in openSections.insert("propuestas") }) }
                section("ACTIVIDAD", key: "actividad") { AdminActivityTab(vm: vm) }
                section("SUGERENCIAS", key: "sugerencias") {
                    AdminSuggestionsTab(rows: vm.suggestions,
                        onRespond: { id, resolved, reply in vm.respondToSuggestion(id, resolved: resolved, reply: reply) })
                }
                section("PUSH", key: "push") { AdminPushTab(vm: vm) }

                Button { openDenunciasScreen = true } label: { openRow("DENUNCIAS") }.buttonStyle(.plain)
                Button { openGestionarScreen = true } label: { openRow("GESTIONAR") }.buttonStyle(.plain)
            }
            .padding(.bottom, 24)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .onAppear { if openDenuncias { openDenunciasScreen = true } }
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $openDenunciasScreen) { AdminReportsTab() }
        .navigationDestination(isPresented: $openGestionarScreen) { GestionarTab(vm: vm) }
        .task {
            await vm.load()
            await vm.loadStats()
            await vm.loadActivity()
            await vm.loadSuggestions()
        }
        .sheet(item: $rejecting) { target in
            RejectReasonSheet { reason in
                if target.isSubmission { vm.reviewSubmission(target.id, approve: false, reason: reason) }
                else { vm.reviewContribution(target.id, approve: false, reason: reason) }
            }
        }
    }

    /// Sección plegable: toca la cabecera para abrir/cerrar. Solo una lógica
    /// de despliegue simple — nada de pestañas que hay que recordar dónde
    /// están.
    @ViewBuilder
    private func section<Content: View>(_ title: String, key: String, badge: Int64? = nil,
                                         @ViewBuilder content: () -> Content) -> some View {
        let isOpen = openSections.contains(key)
        // Cuando hay algo pendiente, la cabecera se ilumina en terracota con
        // el número — antes solo lo decía el aviso de arriba y, si lo
        // cerrabas, la sección volvía a verse gris como cualquier otra
        // (Álvaro, 2026-09-06: "que se ilumine cuando salgan propuestas").
        let lit = (badge ?? 0) > 0
        Button {
            if isOpen { openSections.remove(key) } else { openSections.insert(key) }
        } label: {
            HStack(spacing: 8) {
                Text(title).font(Cumbre.mono(11, .bold)).tracking(1.2)
                    .foregroundStyle(lit ? .white : Cumbre.ink2)
                if let badge, badge > 0 {
                    Text("\(badge)").font(Cumbre.mono(11, .bold))
                        .foregroundStyle(lit ? Cumbre.terra : Cumbre.ink2)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Circle().fill(lit ? Color.white : Cumbre.rule))
                }
                Spacer()
                Image(systemName: isOpen ? "chevron.up" : "chevron.down").font(.system(size: 12))
                    .foregroundStyle(lit ? .white.opacity(0.85) : Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(lit ? Cumbre.terraFill : Color.clear)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
        Divider().overlay(Cumbre.rule)
        if isOpen {
            content()
            Divider().overlay(Cumbre.rule)
        }
    }

    private func openRow(_ title: String) -> some View {
        HStack {
            Text(title).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink2)
            Spacer()
            Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .contentShape(Rectangle())
        .overlay(Divider().overlay(Cumbre.rule), alignment: .bottom)
    }

    private var propuestasContent: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity).padding(24)
            } else if vm.submissions.isEmpty && vm.contributions.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "checkmark.seal").font(.system(size: 36)).foregroundStyle(Cumbre.ok)
                    Text("Nada pendiente de revisar.").font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                }
                .frame(maxWidth: .infinity).padding(32)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    if !vm.submissions.isEmpty {
                        sectionHeader("ESCUELAS NUEVAS · \(vm.submissions.count)")
                        ForEach(vm.submissions, id: \.id) { s in
                            SubmissionAdminCard(
                                submission: s,
                                busy: vm.working.contains(s.id),
                                onApprove: { vm.reviewSubmission(s.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: s.id, isSubmission: true) }
                            )
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                    if !vm.contributions.isEmpty {
                        sectionHeader("MEJORAS · \(filteredContributions.count)")
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
