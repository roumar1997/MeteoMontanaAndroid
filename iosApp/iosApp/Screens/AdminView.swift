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
    /// Reject de las tarjetas en VISTA PREVIA de la portada — la pantalla
    /// completa (AdminPropuestasScreen) tiene la suya propia, independiente.
    @State private var rejecting: RejectTarget?
    @State private var previewUsers: [AdminUserRow]? = nil
    @State private var openDenunciasScreen = false

    /// Portada única del admin — v2 (2026-09-07). La v1 fusionaba TODO el
    /// contenido de cada sección en un único ScrollView con más ScrollViews
    /// anidados dentro, y eso colgaba la app de verdad en dispositivo (bien
    /// documentado: ScrollView-en-ScrollView da problemas reales de gestos en
    /// iOS, algo que Xcode/CI no detectan porque no llegan a correr la UI).
    /// Esta vez: UN solo ScrollView de verdad, cada sección muestra solo una
    /// VISTA PREVIA (2-3 elementos, sin scroll propio) con un "Ver todo" que
    /// EMPUJA (NavigationLink) a la pantalla completa — esa sí con su propio
    /// ScrollView, pero nunca anidado dentro de otro.
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let s = vm.stats, s.submissionsPending > 0 {
                    NavigationLink(destination: AdminPropuestasScreen(vm: vm)) {
                        HStack(spacing: 12) {
                            Text("⏳").font(.system(size: 26))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("PENDIENTE DE REVISAR").font(Cumbre.mono(10, .bold)).tracking(0.8).opacity(0.85)
                                Text("\(s.submissionsPending) propuesta\(s.submissionsPending == 1 ? "" : "s")")
                                    .font(Cumbre.serif(22, .bold))
                                Text("Toca para ir directo a revisarlas →").font(.system(size: 11.5)).opacity(0.9)
                            }
                            Spacer()
                        }
                        .foregroundStyle(.white).padding(16).background(Cumbre.terraFill)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }.buttonStyle(.plain)
                    .padding(.horizontal, 16).padding(.top, 12)
                }

                homeSection("PROPUESTAS", count: vm.submissions.count + vm.contributions.count,
                            destination: AdminPropuestasScreen(vm: vm)) {
                    if vm.loading {
                        ProgressView().padding(16).frame(maxWidth: .infinity)
                    } else if vm.submissions.isEmpty && vm.contributions.isEmpty {
                        Text("Nada pendiente de revisar.").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                            .padding(.horizontal, 16).padding(.bottom, 12)
                    } else {
                        ForEach(vm.submissions.prefix(2), id: \.id) { s in
                            SubmissionAdminCard(
                                submission: s, busy: vm.working.contains(s.id),
                                onApprove: { vm.reviewSubmission(s.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: s.id, isSubmission: true) })
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(vm.contributions.prefix(max(0, 2 - vm.submissions.count)), id: \.id) { c in
                            ContributionAdminCard(
                                contribution: c, busy: vm.working.contains(c.id),
                                onApprove: { vm.reviewContribution(c.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) },
                                onApproveEdited: { edited in vm.approveContributionEdited(c.id, editedBloquesJson: edited) })
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }

                sectionLabel("STATS")
                AdminStatsGrid(stats: vm.stats)

                homeSection("USUARIOS", count: previewUsers?.count, destination: AdminUsersScreen()) {
                    if let list = previewUsers {
                        ForEach(list.prefix(3), id: \.uid) { u in
                            AdminUserRowView(u: u).padding(.horizontal, 16)
                            Divider().overlay(Cumbre.rule)
                        }
                    } else {
                        ProgressView().padding(16).frame(maxWidth: .infinity)
                    }
                }

                homeRow("ACTIVIDAD", destination: AdminActivityTab(vm: vm))
                homeRow("DENUNCIAS", destination: AdminReportsTab())
                homeRow("GESTIONAR", destination: GestionarTab(vm: vm))
                homeRow("SUGERENCIAS", destination: AdminSuggestionsTab(
                    rows: vm.suggestions,
                    onRespond: { id, resolved, reply in vm.respondToSuggestion(id, resolved: resolved, reply: reply) }))
                homeRow("PUSH", destination: AdminPushTab(vm: vm))
            }
            .padding(.bottom, 24)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $openDenunciasScreen) { AdminReportsTab() }
        .task {
            await vm.load()
            await vm.loadStats()
            previewUsers = (try? await AppDependencies.shared.container.getAdminUsers.invoke()) ?? []
        }
        .onAppear { if openDenuncias { openDenunciasScreen = true } }
        .sheet(item: $rejecting) { target in
            RejectReasonSheet { reason in
                if target.isSubmission { vm.reviewSubmission(target.id, approve: false, reason: reason) }
                else { vm.reviewContribution(target.id, approve: false, reason: reason) }
            }
        }
    }

    private func sectionLabel(_ t: String) -> some View {
        Text(t).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 8)
    }

    /// Sección de la portada con vista previa (contenido pasado, SIN scroll
    /// propio) + "Ver todo" que empuja a la pantalla completa. El recuento se
    /// pone junto al título para que se sepa cuánto hay sin tener que entrar.
    @ViewBuilder
    private func homeSection<Destination: View, Content: View>(
        _ title: String, count: Int?, destination: Destination, @ViewBuilder content: () -> Content
    ) -> some View {
        HStack {
            Text(title).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink2)
            if let count { Text("· \(count)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3) }
            Spacer()
            NavigationLink(destination: destination) {
                Text("VER TODO").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
            }
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 8)
        content()
    }

    /// Fila simple que solo empuja a su pantalla — para secciones que no
    /// tienen una vista previa que aportar en la portada (formularios, mapas).
    private func homeRow<Destination: View>(_ title: String, destination: Destination) -> some View {
        NavigationLink(destination: destination) {
            HStack {
                Text(title).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink2)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
        .overlay(Divider().overlay(Cumbre.rule), alignment: .bottom)
    }
}

/// Rejilla de Stats sin ScrollView propio, para vivir dentro de la portada
/// (que ya tiene el suyo) sin anidar — el anidado fue justo lo que colgaba
/// la app la vez pasada.
struct AdminStatsGrid: View {
    let stats: AdminStats?

    var body: some View {
        if let s = stats {
            VStack(alignment: .leading, spacing: 8) {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    card("USUARIOS", s.totalUsers, "👤", Cumbre.rain)
                    card("ADMINS", s.totalAdmins, "🛡️", Cumbre.terra)
                    card("ESCUELAS", s.totalSchools, "🧗", Cumbre.rain)
                    card("NOTAS", s.totalNotes, "📓", Cumbre.rain)
                    card("APROBADAS", s.submissionsApproved, "✔️", Cumbre.ok)
                    card("RECHAZADAS", s.submissionsRejected, "✕", Cumbre.bad)
                }
            }
            .padding(.horizontal, 16)
        } else {
            ProgressView().padding(16).frame(maxWidth: .infinity)
        }
    }

    private func card(_ label: String, _ value: Int64, _ icon: String, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(icon).font(.system(size: 18))
            Text("\(value)").font(Cumbre.serif(22, .bold)).foregroundStyle(tint)
            Text(label).font(Cumbre.mono(9.5, .bold)).tracking(0.5).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(tint.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

/// Pantalla completa de Propuestas ("Ver todo" desde la portada) — su propio
/// ScrollView, independiente. Autosuficiente: no comparte estado con la
/// portada, para que anidar/empujar entre pantallas no dependa de bindings.
struct AdminPropuestasScreen: View {
    @ObservedObject var vm: AdminViewModel
    @State private var rejecting: RejectTarget?
    @State private var filter: ContribFilter = .todas

    private var filteredContributions: [Contribution] {
        vm.contributions.filter { filter.matches($0.type) }
    }
    private var groupedBySchool: [(school: String, items: [Contribution])] {
        let groups = Dictionary(grouping: filteredContributions, by: { $0.schoolName })
        return groups.keys.sorted().map { (school: $0, items: groups[$0] ?? []) }
    }

    var body: some View {
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
                    LazyVStack(alignment: .leading, spacing: 0) {
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
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Propuestas")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $rejecting) { target in
            RejectReasonSheet { reason in
                if target.isSubmission { vm.reviewSubmission(target.id, approve: false, reason: reason) }
                else { vm.reviewContribution(target.id, approve: false, reason: reason) }
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

struct RejectTarget: Identifiable { let id: String; let isSubmission: Bool }
