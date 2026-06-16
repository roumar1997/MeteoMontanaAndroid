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
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Admin")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
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
