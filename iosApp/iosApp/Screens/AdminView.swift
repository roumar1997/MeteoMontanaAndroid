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

    func reviewContribution(_ id: String, approve: Bool, reason: String?) {
        working.insert(id)
        Task {
            if approve { _ = try? await approveContrib.invoke(id: id) }
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
            case .gestionar: GestionarTab(vm: vm)
            case .stats: AdminStatsTab(stats: vm.stats)
            case .actividad: AdminLogsTab(logs: vm.logs)
            case .push: AdminPushTab(vm: vm)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
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
                                            onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) }
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

// MARK: - Tarjetas

private struct SubmissionAdminCard: View {
    let submission: Submission
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(submission.proposedName).font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
            let sub = [submission.proposedRockType?.uppercased(), submission.proposedRegion, submission.proposedLocation]
                .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
            if !sub.isEmpty { Text(sub).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3) }
            Text(String(format: "%.5f, %.5f", submission.proposedLat, submission.proposedLon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            if let n = submission.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

private struct ContributionAdminCard: View {
    let contribution: Contribution
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    @State private var showMap = false

    private var isCorrection: Bool { contribution.type.uppercased() == "POSITION_CORRECTION" }
    private var newCoord: CLLocationCoordinate2D? {
        guard let la = contribution.proposedLat?.doubleValue,
              let lo = contribution.proposedLon?.doubleValue else { return nil }
        return CLLocationCoordinate2D(latitude: la, longitude: lo)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(adminTypeLabel(contribution.type)).font(Cumbre.mono(11, .bold)).tracking(0.8)
                    .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 3)
                    .background(isCorrection ? Cumbre.bad : Cumbre.terra)
                Spacer()
                if let a = contribution.submittedByName, !a.isEmpty {
                    Text(a).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Text(contribution.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
            if let nm = contribution.name, !nm.isEmpty { Text(nm).font(.system(size: 14)).foregroundStyle(Cumbre.ink) }
            if let n = contribution.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }

            // QUÉ CAMBIA — visualización por tipo (clave para revisar bien).
            switch contribution.type.uppercased() {
            case "POSITION_CORRECTION":
                VStack(alignment: .leading, spacing: 2) {
                    Text("MUEVE «\((contribution.name ?? "elemento").uppercased())»")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    Text("✕ actual: \(coordStr(contribution.lat, contribution.lon))")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    if let nc = newCoord {
                        Text("★ nueva: \(coordStr(nc.latitude, nc.longitude))")
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    }
                }
            case "BOULDER":
                if let p = contribution.photoUrl, !p.isEmpty {
                    Text(contribution.targetBlockId == nil ? "PIEDRA NUEVA" : "AÑADE VÍAS A UNA PIEDRA")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    TopoPhotoView(photoUrl: p, lines: TopoParse.lines(contribution.bloquesJson))
                } else if let bj = contribution.bloquesJson, !bj.isEmpty {
                    Text("\(TopoParse.lines(bj).count) vía(s) propuesta(s)")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            case "ASSIGN_SECTOR":
                Text("ASIGNA ESTA PIEDRA A UN SECTOR")
                    .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
            default:
                EmptyView()
            }

            Text(coordStr(contribution.lat, contribution.lon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)

            Button { showMap = true } label: {
                Text("VER EN MAPA").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }.buttonStyle(.plain)

            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .sheet(isPresented: $showMap) {
            ContributionMapSheet(contribution: contribution)
        }
    }

    private func coordStr(_ lat: Double, _ lon: Double) -> String {
        String(format: "%.5f, %.5f", lat, lon)
    }
}

func adminTypeLabel(_ t: String) -> String {
    switch t.uppercased() {
    case "PARKING": return "PARKING"
    case "BOULDER": return "PIEDRA"
    case "SECTOR": return "SECTOR"
    case "POSITION_CORRECTION": return "CORREGIR POSICIÓN"
    case "ASSIGN_SECTOR": return "ASIGNAR SECTOR"
    default: return t.uppercased()
    }
}

/// Marcadores de una contribución para el mapa (CORREGIR = viejo ✕ gris + nuevo
/// ★ amarillo; el resto = un marcador amarillo en la posición propuesta).
func contributionMarkers(_ c: Contribution) -> [CumbreMarker] {
    let highlight = UIColor(hex: 0xF59E0B)
    if c.type.uppercased() == "POSITION_CORRECTION" {
        var ms = [CumbreMarker(id: "old", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                               title: "Actual", kind: .block, color: UIColor(white: 0.55, alpha: 1), name: "✕")]
        if let la = c.proposedLat?.doubleValue, let lo = c.proposedLon?.doubleValue {
            ms.append(CumbreMarker(id: "new", coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                                   title: "Nueva", kind: .block, color: highlight, name: "★"))
        }
        return ms
    }
    return [CumbreMarker(id: "p", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                         title: c.name ?? adminTypeLabel(c.type), kind: .block, color: highlight,
                         name: c.name ?? "★")]
}

/// Mapa a pantalla completa de una propuesta (espejo de FullScreenMapDialog).
private struct ContributionMapSheet: View {
    let contribution: Contribution
    @Environment(\.dismiss) private var dismiss
    @State private var style: MapStyleKind = .topo
    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                MapLibreView(
                    center: CLLocationCoordinate2D(latitude: contribution.lat, longitude: contribution.lon),
                    zoom: 15, markers: contributionMarkers(contribution), style: style)
                MapStyleChips(selection: $style)
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(adminTypeLabel(contribution.type))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
    }
}

private struct ReviewButtons: View {
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            if busy {
                ProgressView().frame(maxWidth: .infinity)
            } else {
                Button(action: onReject) {
                    Text("RECHAZAR").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button(action: onApprove) {
                    Text("APROBAR").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(Cumbre.ok)
                }.buttonStyle(.plain)
            }
        }
        .padding(.top, 4)
    }
}

/// Hoja para escribir el motivo del rechazo (opcional).
private struct RejectReasonSheet: View {
    let onConfirm: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var reason = ""
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Motivo del rechazo (opcional)").eyebrow()
                TextField("ej: coordenadas incorrectas", text: $reason, axis: .vertical)
                    .lineLimit(2...5).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                Button {
                    onConfirm(reason.trimmingCharacters(in: .whitespaces).isEmpty ? nil : reason)
                    dismiss()
                } label: {
                    Text("RECHAZAR PROPUESTA").font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14).background(Cumbre.bad)
                }.buttonStyle(.plain)
                Spacer()
            }
            .padding(16)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Rechazar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Cumbre.ink3) }
            }
        }
    }
}

private enum AdminTab: String, CaseIterable {
    case propuestas = "PROPUESTAS"
    case gestionar = "GESTIONAR"
    case stats = "STATS"
    case actividad = "ACTIVIDAD"
    case push = "PUSH"
}

/// Tab STATS — tarjetas con estadísticas de la plataforma (espejo de StatsTab).
private struct AdminStatsTab: View {
    let stats: AdminStats?
    var body: some View {
        ScrollView {
            if let s = stats {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    card("USUARIOS", s.totalUsers)
                    card("ADMINS", s.totalAdmins)
                    card("ESCUELAS", s.totalSchools)
                    card("NOTAS", s.totalNotes)
                    card("PENDIENTES", s.submissionsPending)
                    card("APROBADAS", s.submissionsApproved)
                    card("RECHAZADAS", s.submissionsRejected)
                }.padding(16)
            } else {
                ProgressView().padding(.top, 40)
            }
        }
    }
    private func card(_ label: String, _ value: Int64) -> some View {
        VStack(spacing: 4) {
            Text("\(value)").font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
            Text(label).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 16)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Tab ACTIVIDAD — registro de acciones admin (espejo de ActivityTab).
private struct AdminLogsTab: View {
    let logs: [AdminLog]
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if logs.isEmpty {
                    Text("Sin actividad reciente.").font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3).padding(24)
                }
                ForEach(logs, id: \.id) { l in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l.action).font(Cumbre.mono(13, .bold)).foregroundStyle(Cumbre.ink)
                        Text("\(l.targetType)/\(l.targetId)").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                        if let d = l.details, !d.isEmpty {
                            Text(d).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                        }
                        Text(String(l.createdAt.prefix(16))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    Divider().overlay(Cumbre.rule)
                }
            }
        }
    }
}

/// Tab PUSH — envío manual de notificación (espejo de PushTab).
private struct AdminPushTab: View {
    @ObservedObject var vm: AdminViewModel
    @State private var target = ""
    @State private var title = ""
    @State private var body_ = ""
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                field("UID DESTINO (opcional · vacío = todos)", $target, "uid")
                field("TÍTULO", $title, "Título")
                VStack(alignment: .leading, spacing: 6) {
                    Text("MENSAJE").eyebrow()
                    TextField("Mensaje", text: $body_, axis: .vertical).lineLimit(3...6)
                        .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                Button {
                    vm.sendPush(targetUid: target.trimmingCharacters(in: .whitespaces).isEmpty ? nil : target,
                                title: title, body: body_)
                } label: {
                    HStack { if vm.pushBusy { ProgressView().tint(.white) }
                        Text("ENVIAR PUSH").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                }.buttonStyle(.plain)
                .disabled(vm.pushBusy || title.isEmpty || body_.isEmpty)
                if let r = vm.pushResult {
                    Text(r).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                }
            }.padding(16)
        }
    }
    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Tab GESTIONAR — buscar una escuela y editar/borrar/mover sus bloques
/// (espejo de GestionarTab.kt). Buscar → mapa con todos los bloques → tocar uno.
private struct GestionarTab: View {
    @ObservedObject var vm: AdminViewModel
    @State private var query = ""
    @State private var selected: School?

    private var filtered: [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return Array(vm.allSchools.prefix(60)) }
        return vm.allSchools.filter {
            $0.name.lowercased().contains(q)
            || ($0.location?.lowercased().contains(q) ?? false)
            || ($0.region?.lowercased().contains(q) ?? false)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TextField("Buscar escuela (nombre, lugar, región)…", text: $query)
                .font(.system(size: 15)).padding(10)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1)).padding(16)
            if vm.allSchools.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.top, 30); Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered, id: \.id) { s in
                            Button { selected = s } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.name).font(Cumbre.serif(15, .semibold)).foregroundStyle(Cumbre.ink)
                                        let sub = [s.location, s.region].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                                        if !sub.isEmpty { Text(sub).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3) }
                                    }
                                    Spacer()
                                    Text("▸").foregroundStyle(Cumbre.terra)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 10).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .sheet(item: $selected) { s in SchoolBlocksManageSheet(school: s) }
    }
}

/// Mapa a pantalla completa con todos los bloques de una escuela; tocar uno abre
/// la gestión (editar/borrar). Espejo del FullScreenMapDialog admin.
private struct SchoolBlocksManageSheet: View {
    let school: School
    @Environment(\.dismiss) private var dismiss
    @State private var blocks: [Block] = []
    @State private var style: MapStyleKind = .topo
    @State private var manage: Block?

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                MapLibreView(
                    center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                    zoom: 15, markers: markers, style: style,
                    onTapMarker: { id in manage = blocks.first { $0.id == id } })
                MapStyleChips(selection: $style)
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(school.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task { await reload() }
        .sheet(item: $manage) { b in
            BlockManageSheet(block: b) { Task { await reload() }; manage = nil }
        }
    }

    private func reload() async {
        blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
    }

    private var markers: [CumbreMarker] {
        blocks.map { b in
            CumbreMarker(id: b.id, coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                         title: b.name.isEmpty ? b.type : b.name, kind: kind(b.type),
                         color: color(b.type), name: b.name)
        }
    }
    private func kind(_ t: String) -> MarkerKind {
        switch t.uppercased() { case "PARKING": return .parking; case "ZONE": return .zone; default: return .block }
    }
    private func color(_ t: String) -> UIColor {
        switch t.uppercased() {
        case "PARKING": return UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
        case "ZONE": return UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
        default: return UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
        }
    }
}

/// Gestión de un bloque (admin): editar nombre + coordenadas (preservando las
/// vías) o borrarlo. Espejo de EditBlockDialog/BlockDetailDialog admin.
private struct BlockManageSheet: View {
    let block: Block
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var latText: String
    @State private var lonText: String
    @State private var busy = false
    @State private var confirmDelete = false

    init(block: Block, onDone: @escaping () -> Void) {
        self.block = block; self.onDone = onDone
        _name = State(initialValue: block.name)
        _latText = State(initialValue: String(format: "%.6f", block.lat))
        _lonText = State(initialValue: String(format: "%.6f", block.lon))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    field("NOMBRE", $name, "Nombre")
                    field("LATITUD", $latText, "lat")
                    field("LONGITUD", $lonText, "lon")
                    Button { Task { await save() } } label: {
                        HStack { if busy { ProgressView().tint(.white) }
                            Text("GUARDAR CAMBIOS").font(Cumbre.mono(12, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(busy)
                    Button { confirmDelete = true } label: {
                        Text("BORRAR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.bad)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(busy)
                }.padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.ink3) } }
            .alert("¿Borrar este bloque?", isPresented: $confirmDelete) {
                Button("Cancelar", role: .cancel) {}
                Button("Borrar", role: .destructive) { Task { await remove() } }
            } message: { Text("Se borrará «\(block.name)» y sus vías. No se puede deshacer.") }
        }
    }

    private var typeLabel: String {
        switch block.type.uppercased() { case "PARKING": return "PARKING"; case "ZONE": return "ZONA"; default: return "PIEDRA" }
    }

    private func save() async {
        busy = true
        let lat = Double(latText) ?? block.lat
        let lon = Double(lonText) ?? block.lon
        // Preservamos type/foto/descripción/sector y las VÍAS (si no, se borrarían).
        let lines = block.lines.map {
            CreateBlockLineRequest(name: $0.name, grade: $0.grade, startType: $0.startType, linePath: $0.linePath)
        }
        let req = CreateBlockRequest(type: block.type, name: name, lat: lat, lon: lon,
                                     photoPath: block.photoPath, description: nil,
                                     lines: lines, sectorBlockId: block.sectorBlockId)
        _ = try? await AppDependencies.shared.container.updateBlock.invoke(blockId: block.id, req: req)
        busy = false; dismiss(); onDone()
    }

    private func remove() async {
        busy = true
        _ = try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: block.id)
        busy = false; dismiss(); onDone()
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}
