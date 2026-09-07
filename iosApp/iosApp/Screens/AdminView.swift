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
    private let setSubAwaiting = AppDependencies.shared.container.setSubmissionAwaitingReply
    private let setContribAwaiting = AppDependencies.shared.container.setContributionAwaitingReply

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

    /// "Esperando respuesta": marca/desmarca sin tocar aprobar/rechazar — el
    /// admin le ha preguntado algo al proponente y quiere sacarla de la cola
    /// normal mientras espera contestación (Álvaro, 2026-09-07). Recarga la
    /// cola para reflejar el cambio de grupo (Pendientes ↔ Esperando).
    func toggleSubmissionAwaitingReply(_ id: String, waiting: Bool) {
        working.insert(id)
        Task {
            _ = try? await setSubAwaiting.invoke(id: id, waiting: waiting)
            submissions = (try? await getSubs.invoke(status: nil)) ?? submissions
            working.remove(id)
        }
    }

    func toggleContributionAwaitingReply(_ id: String, waiting: Bool) {
        working.insert(id)
        Task {
            _ = try? await setContribAwaiting.invoke(id: id, waiting: waiting)
            contributions = (try? await getContribs.invoke(status: nil)) ?? contributions
            working.remove(id)
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

struct AdminView: View {
    /// Si viene true (desde el push de una denuncia) abre en la pestaña DENUNCIAS.
    var openDenuncias: Bool = false
    @StateObject private var vm = AdminViewModel()
    @State private var rejecting: RejectTarget?
    @State private var previewUsers: [AdminUserRow]? = nil
    @State private var openDenunciasScreen = false

    @State private var showAllUsers = false
    /// Tarjetas de Stats pulsables (Álvaro, 2026-09-07: "si pulsas usuarios
    /// ves usuarios... ahí ves todo, no scrolleando ver de una en una"):
    /// cada una abre su PROPIA hoja con el contenido completo — no basta con
    /// saltar a la vista previa que ya vive en esta página, porque esa sigue
    /// recortada (8 usuarios, un solo estado de actividad). Solo ESCUELAS
    /// empuja a una pantalla ya existente (GESTIONAR, con mapa).
    @State private var pushGestionar = false
    @State private var usersSheet = false
    @State private var adminsSheet = false
    @State private var notesSheet = false
    @State private var activitySheet: String? = nil   // "APPROVED" | "REJECTED"
    @State private var notes: [AdminNoteRow]? = nil

    private func loadNotesIfNeeded() {
        guard notes == nil else { return }
        Task { notes = (try? await AppDependencies.shared.container.getAdminNotes.invoke()) ?? [] }
    }

    /// Portada única del admin — v3 (2026-09-07). La v1 fusionaba TODO en un
    /// único ScrollView con MÁS ScrollViews del mismo eje dentro, y eso
    /// colgaba la app de verdad en dispositivo (ScrollView-en-ScrollView del
    /// mismo eje es un problema real de gestos en iOS que Xcode/CI no
    /// detectan porque no llegan a correr la UI). La v2 lo evitó a base de
    /// "vista previa + ver todo", pero a Álvaro no le convenció — quería
    /// verlo TODO de una vez, como en la primera maqueta que le gustó.
    /// Esta v3 hace justo eso, sin el problema técnico: TODO vive aquí,
    /// agrupado por color como la maqueta "A", pero ninguna sección mete su
    /// propio ScrollView — todas son contenido plano (VStack/LazyVGrid), así
    /// que solo hay UN ScrollView de verdad en toda la pantalla.
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let s = vm.stats, s.submissionsPending > 0 {
                    HStack(spacing: 12) {
                        Text("⏳").font(.system(size: 26))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("PENDIENTE DE REVISAR").font(Cumbre.mono(10, .bold)).tracking(0.8).opacity(0.85)
                            Text("\(s.submissionsPending) propuesta\(s.submissionsPending == 1 ? "" : "s")")
                                .font(Cumbre.serif(22, .bold))
                            Text("Justo debajo, para revisarlas ya.").font(.system(size: 11.5)).opacity(0.9)
                        }
                        Spacer()
                    }
                    .foregroundStyle(.white).padding(16).background(Cumbre.terraFill)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal, 16).padding(.top, 12)
                }

                // ── PROPUESTAS: todo, sin recortar — nombre+mensaje+historial
                // del que propone ya vienen de serie en estas tarjetas.
                // Separadas en dos grupos: Pendientes (a revisar ya) y
                // Esperando Respuesta (el admin le preguntó algo al proponente
                // y las sacó de en medio hasta que conteste — Álvaro, 2026-09-07).
                sectionLabel("Propuestas")
                if vm.loading {
                    ProgressView().padding(16).frame(maxWidth: .infinity)
                } else if vm.submissions.isEmpty && vm.contributions.isEmpty {
                    Text("Nada pendiente de revisar.").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 16).padding(.bottom, 12)
                } else {
                    let pendingSubs = vm.submissions.filter { $0.awaitingReplySince == nil }
                    let waitingSubs = vm.submissions.filter { $0.awaitingReplySince != nil }
                    let pendingContribs = vm.contributions.filter { $0.awaitingReplySince == nil }
                    let waitingContribs = vm.contributions.filter { $0.awaitingReplySince != nil }

                    if pendingSubs.isEmpty && pendingContribs.isEmpty {
                        Text("Nada pendiente de revisar por ahora.").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                            .padding(.horizontal, 16).padding(.bottom, 12)
                    } else {
                        ForEach(pendingSubs, id: \.id) { s in
                            SubmissionAdminCard(
                                submission: s, busy: vm.working.contains(s.id),
                                onApprove: { vm.reviewSubmission(s.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: s.id, isSubmission: true) },
                                onToggleAwaitingReply: { vm.toggleSubmissionAwaitingReply(s.id, waiting: true) })
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(pendingContribs, id: \.id) { c in
                            ContributionAdminCard(
                                contribution: c, busy: vm.working.contains(c.id),
                                onApprove: { vm.reviewContribution(c.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) },
                                onApproveEdited: { edited in vm.approveContributionEdited(c.id, editedBloquesJson: edited) },
                                onToggleAwaitingReply: { vm.toggleContributionAwaitingReply(c.id, waiting: true) })
                            Divider().overlay(Cumbre.rule)
                        }
                    }

                    if !waitingSubs.isEmpty || !waitingContribs.isEmpty {
                        Text("ESPERANDO RESPUESTA").font(Cumbre.mono(10, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.ink3)
                            .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 6)
                        ForEach(waitingSubs, id: \.id) { s in
                            SubmissionAdminCard(
                                submission: s, busy: vm.working.contains(s.id),
                                onApprove: { vm.reviewSubmission(s.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: s.id, isSubmission: true) },
                                onToggleAwaitingReply: { vm.toggleSubmissionAwaitingReply(s.id, waiting: false) })
                            Divider().overlay(Cumbre.rule)
                        }
                        ForEach(waitingContribs, id: \.id) { c in
                            ContributionAdminCard(
                                contribution: c, busy: vm.working.contains(c.id),
                                onApprove: { vm.reviewContribution(c.id, approve: true, reason: nil) },
                                onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) },
                                onApproveEdited: { edited in vm.approveContributionEdited(c.id, editedBloquesJson: edited) },
                                onToggleAwaitingReply: { vm.toggleContributionAwaitingReply(c.id, waiting: false) })
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }

                // ── STATS: rejilla agrupada por tema y color (la maqueta "A").
                // Cada tarjeta es pulsable — lleva a lo que cuenta, no es solo
                // un número (Álvaro, 2026-09-07).
                sectionLabel("Stats")
                AdminStatsGrid(
                    stats: vm.stats,
                    onUsuarios: { usersSheet = true },
                    onAdmins: { adminsSheet = true },
                    onEscuelas: { pushGestionar = true },
                    onNotas: { loadNotesIfNeeded(); notesSheet = true },
                    onAprobadas: { activitySheet = "APPROVED" },
                    onRechazadas: { activitySheet = "REJECTED" })

                // ── USUARIOS: foto/aportes/denuncias. Empieza mostrando 8 y
                // "ver todos" despliega el resto — con 200 usuarios, cargarlos
                // TODOS de golpe en pantalla es más peso del que hace falta
                // para lo que se mira a diario.
                sectionLabel("Usuarios")
                if let list = previewUsers {
                    ForEach(list.prefix(showAllUsers ? list.count : 8), id: \.uid) { u in
                        AdminUserRowView(u: u).padding(.horizontal, 16)
                        Divider().overlay(Cumbre.rule)
                    }
                    if list.count > 8 {
                        Button { showAllUsers.toggle() } label: {
                            Text(showAllUsers ? "VER MENOS" : "VER TODOS (\(list.count))")
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                    }
                } else {
                    ProgressView().padding(16).frame(maxWidth: .infinity)
                }

                // ── ACTIVIDAD: aprobadas/rechazadas con quién.
                sectionLabel("Actividad")
                AdminActivityTab(vm: vm)

                // ── SUGERENCIAS
                sectionLabel("Sugerencias")
                AdminSuggestionsTab(
                    rows: vm.suggestions,
                    onRespond: { id, resolved, reply in vm.respondToSuggestion(id, resolved: resolved, reply: reply) })

                // ── PUSH
                sectionLabel("Enviar aviso")
                AdminPushTab(vm: vm)

                // Denuncias y Gestionar llevan mapa — se abren aparte: un mapa
                // dentro de un ScrollView sin alto fijo da problemas de verdad.
                sectionLabel("Más")
                homeRow("DENUNCIAS", destination: AdminReportsTab())
                homeRow("GESTIONAR", destination: GestionarTab(vm: vm))
            }
            .padding(.bottom, 40)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $openDenunciasScreen) { AdminReportsTab() }
        .navigationDestination(isPresented: $pushGestionar) { GestionarTab(vm: vm) }
        .task {
            await vm.load()
            await vm.loadStats()
            await vm.loadActivity()
            await vm.loadSuggestions()
            previewUsers = (try? await AppDependencies.shared.container.getAdminUsers.invoke()) ?? []
        }
        .onAppear { if openDenuncias { openDenunciasScreen = true } }
        .sheet(item: $rejecting) { target in
            RejectReasonSheet { reason in
                if target.isSubmission { vm.reviewSubmission(target.id, approve: false, reason: reason) }
                else { vm.reviewContribution(target.id, approve: false, reason: reason) }
            }
        }
        .sheet(isPresented: $adminsSheet) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        let admins = (previewUsers ?? []).filter { $0.isAdmin }
                        if admins.isEmpty {
                            Text("Sin otros admins.").foregroundStyle(Cumbre.ink3).padding(16)
                        } else {
                            ForEach(admins, id: \.uid) { u in
                                AdminUserRowView(u: u).padding(.horizontal, 16)
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
                .background(Cumbre.bg.ignoresSafeArea())
                .navigationTitle("Admins")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(isPresented: $notesSheet) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if let list = notes {
                            if list.isEmpty {
                                Text("Sin notas todavía.").foregroundStyle(Cumbre.ink3).padding(16)
                            } else {
                                ForEach(list, id: \.id) { n in
                                    NavigationLink(destination: AdminNoteDetailView(note: n)) {
                                        adminNoteRow(n)
                                    }.buttonStyle(.plain)
                                    Divider().overlay(Cumbre.rule)
                                }
                            }
                        } else {
                            ProgressView().padding(.top, 30).frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .background(Cumbre.bg.ignoresSafeArea())
                .navigationTitle("Notas")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(isPresented: $usersSheet) {
            NavigationStack { AdminUsersScreen() }
        }
        .sheet(item: activityStatusBinding) { status in
            NavigationStack {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        let subs = vm.activityStatus == status.id ? vm.activitySubmissions : []
                        let contribs = vm.activityStatus == status.id ? vm.activityContributions : []
                        if vm.activityLoading {
                            ProgressView().padding(24).frame(maxWidth: .infinity)
                        } else if subs.isEmpty && contribs.isEmpty {
                            Text("Nada por aquí todavía.").font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                                .padding(16)
                        } else {
                            ForEach(subs, id: \.id) { s in
                                SubmissionAdminCard(submission: s, busy: false, onApprove: {}, onReject: {})
                                Divider().overlay(Cumbre.rule)
                            }
                            ForEach(contribs, id: \.id) { c in
                                ContributionAdminCard(contribution: c, busy: false, onApprove: {}, onReject: {})
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
                .background(Cumbre.bg.ignoresSafeArea())
                .navigationTitle(status.id == "APPROVED" ? "Aprobadas" : "Rechazadas")
                .navigationBarTitleDisplayMode(.inline)
                .task { await vm.loadActivity(status: status.id) }
            }
        }
    }

    /// Adapta `activitySheet` (String?) a `Identifiable` para `.sheet(item:)`
    /// — necesario porque un simple `Bool` no distinguiría "abrir en
    /// APROBADAS" de "abrir en RECHAZADAS" si se pulsa una tras otra.
    private var activityStatusBinding: Binding<IdentifiableStatus?> {
        Binding(
            get: { activitySheet.map(IdentifiableStatus.init) },
            set: { activitySheet = $0?.id }
        )
    }

    private func sectionLabel(_ t: String) -> some View {
        Text(t).font(Cumbre.mono(11, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 8)
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
/// la app la vez pasada. Cada tarjeta es pulsable: no es solo un número, lleva
/// a lo que cuenta (Álvaro, 2026-09-07: "debería ser pulsable y metiéndome
/// en lo de arriba"). USUARIOS/APROBADAS/RECHAZADAS saltan a su sección ya
/// inline en esta misma página; ADMINS/NOTAS abren su propia hoja (no tienen
/// sección propia); ESCUELAS empuja a GESTIONAR (lleva mapa, vive aparte).
struct AdminStatsGrid: View {
    let stats: AdminStats?
    var onUsuarios: () -> Void = {}
    var onAdmins: () -> Void = {}
    var onEscuelas: () -> Void = {}
    var onNotas: () -> Void = {}
    var onAprobadas: () -> Void = {}
    var onRechazadas: () -> Void = {}

    var body: some View {
        if let s = stats {
            VStack(alignment: .leading, spacing: 4) {
                subLabel("Comunidad")
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    card("USUARIOS", s.totalUsers, "👤", Cumbre.rain, action: onUsuarios)
                    card("ADMINS", s.totalAdmins, "🛡️", Cumbre.terra, action: onAdmins)
                }
                subLabel("Contenido")
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    card("ESCUELAS", s.totalSchools, "🧗", Cumbre.rain, action: onEscuelas)
                    card("NOTAS", s.totalNotes, "📓", Cumbre.rain, action: onNotas)
                }
                subLabel("Moderación")
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    card("APROBADAS", s.submissionsApproved, "✔️", Cumbre.ok, action: onAprobadas)
                    card("RECHAZADAS", s.submissionsRejected, "✕", Cumbre.bad, action: onRechazadas)
                }
            }
            .padding(.horizontal, 16)
        } else {
            ProgressView().padding(16).frame(maxWidth: .infinity)
        }
    }

    private func subLabel(_ t: String) -> some View {
        Text(t.uppercased()).font(Cumbre.mono(9.5, .bold)).tracking(1).foregroundStyle(Cumbre.ink3)
            .padding(.top, 10).padding(.bottom, 2)
    }

    private func card(_ label: String, _ value: Int64, _ icon: String, _ tint: Color,
                       action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Text(icon).font(.system(size: 18))
                Text("\(value)").font(Cumbre.serif(22, .bold)).foregroundStyle(tint)
                Text(label).font(Cumbre.mono(9.5, .bold)).tracking(0.5).foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(tint.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct RejectTarget: Identifiable { let id: String; let isSubmission: Bool }

/// Envoltorio para poder usar `.sheet(item:)` con un simple estado String?
/// ("APPROVED"/"REJECTED") — ver uso en `AdminView.activityStatusBinding`.
struct IdentifiableStatus: Identifiable { let id: String }
