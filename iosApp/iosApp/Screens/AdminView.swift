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
            case .denuncias: AdminReportsTab()
            case .gestionar: GestionarTab(vm: vm)
            case .stats: AdminStatsTab(stats: vm.stats, onGoToTab: { tab = $0 })
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
    // Bloques de la escuela (para resolver la piedra/sector destino al revisar
    // corregir/añadir vías o asignar sector — el admin debe ver QUÉ se cambia).
    @State private var schoolBlocks: [Block] = []
    @State private var blocksLoaded = false
    private var targetBlock: Block? { schoolBlocks.first { $0.id == contribution.targetBlockId } }

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

            // QUÉ CAMBIA en una línea — se entiende la propuesta sin scrollear
            // (el detalle foto a foto sigue debajo). Espejo de ContributionCard.kt.
            Text(contributionSummary(contribution, blocks: schoolBlocks))
                .font(.system(size: 13, weight: .medium)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Cumbre.terraBg.opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.terra.opacity(0.4), lineWidth: 1))

            if let nm = contribution.name, !nm.isEmpty { Text(nm).font(.system(size: 14)).foregroundStyle(Cumbre.ink) }
            if let n = contribution.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }

            // QUÉ CAMBIA — visualización por tipo (clave para revisar bien).
            switch contribution.type.uppercased() {
            case "POSITION_CORRECTION":
                VStack(alignment: .leading, spacing: 2) {
                    // targetBlockId == nil ⇒ el backend mueve la escuela entera;
                    // si hay id, mueve ese bloque concreto (espejo de la materialización).
                    Text(contribution.targetBlockId == nil
                         ? "MUEVE LA ESCUELA ENTERA"
                         : "MUEVE «\((contribution.name ?? "bloque").uppercased())»")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    Text("✕ actual: \(coordStr(contribution.lat, contribution.lon))")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    if let nc = newCoord {
                        Text("★ nueva: \(coordStr(nc.latitude, nc.longitude))")
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    }
                }
            case "BOULDER":
                BoulderReviewView(contribution: contribution, targetBlock: targetBlock)
            case "ASSIGN_SECTOR":
                let sector = schoolBlocks.first { $0.id == contribution.sectorBlockId }
                VStack(alignment: .leading, spacing: 2) {
                    Text("ASIGNAR SECTOR A PIEDRA").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    Text("PIEDRA · \((targetBlock?.name ?? contribution.name ?? "?").uppercased())")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    Text("→ SECTOR · \((sector?.name ?? "?").uppercased())")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                }
            default:
                EmptyView()
            }

            Text(coordStr(contribution.lat, contribution.lon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)

            // Mini-mapa con el cambio en contexto (existentes atenuados + propuesta
            // resaltada). El admin ve directamente dónde cae y si pisa algo.
            if blocksLoaded {
                ContributionMiniMap(contribution: contribution, blocks: schoolBlocks)
            }

            Button { showMap = true } label: {
                Text(NSLocalizedString("admin_view_map", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }.buttonStyle(.plain)

            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .sheet(isPresented: $showMap) {
            ContributionMapSheet(contribution: contribution)
        }
        .task(id: contribution.id) {
            guard !blocksLoaded else { return }
            schoolBlocks = (try? await AppDependencies.shared.container.getBlocks
                .invoke(schoolId: contribution.schoolId)) ?? []
            blocksLoaded = true
        }
    }

    private func coordStr(_ lat: Double, _ lon: Double) -> String {
        String(format: "%.5f, %.5f", lat, lon)
    }
}

/// Visualización de una contribución BOULDER para el admin: piedra nueva, añadir
/// vías, o corregir una vía. Dibuja existentes (difuminadas) + nuevas sobre la
/// foto y resume ORIGINAL vs PROPUESTA. Espejo de ContributionCard.kt.
private struct BoulderReviewView: View {
    let contribution: Contribution
    let targetBlock: Block?

    private var isNewBoulder: Bool { contribution.targetBlockId == nil }
    private var newLines: [TopoLineVM] { TopoParse.lines(contribution.bloquesJson) }
    // ids de vías que esta propuesta corrige: targetLineId de la contribución
    // (corrección individual) + targetLineId por entrada (editor unificado).
    private var correctedIds: Set<String> {
        var ids = TopoParse.targetLineIds(contribution.bloquesJson)
        if let t = contribution.targetLineId { ids.insert(t) }
        return ids
    }
    private var isEditLine: Bool { !correctedIds.isEmpty }
    // Existentes que NO cambian → normales.
    private var existingNormal: [TopoLineVM] {
        (targetBlock?.lines ?? []).filter { !correctedIds.contains($0.id) }.map { TopoLineVM($0) }
    }
    // Vías viejas que se corrigen (difuminadas), para distinguir el cambio.
    private var oldEdited: [TopoLineVM] {
        (targetBlock?.lines ?? []).filter { correctedIds.contains($0.id) }.map { TopoLineVM($0) }
    }
    private var photo: String? { isNewBoulder ? contribution.photoUrl : targetBlock?.photoPath }
    private var editedOriginals: [BlockLine] {
        (targetBlock?.lines ?? []).filter { correctedIds.contains($0.id) }
    }

    // Propuesta agrupada por cara (foto) para comparar ACTUAL vs PROPUESTA.
    private var proposed: [TopoParse.ProposedVia] { TopoParse.proposedVias(contribution.bloquesJson) }
    private var faceGroups: [(key: String, vias: [TopoParse.ProposedVia])] {
        // Agrupa por photoUrl (clave "" si nil) preservando el orden de aparición.
        var order: [String] = []
        var map: [String: [TopoParse.ProposedVia]] = [:]
        for p in proposed {
            let k = p.photoUrl ?? ""
            if map[k] == nil { order.append(k) }
            map[k, default: []].append(p)
        }
        return order.map { (key: $0, vias: map[$0] ?? []) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(header).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)

            if isNewBoulder {
                // Piedra nueva: foto propuesta + sus líneas.
                if let photo = contribution.photoUrl, !photo.isEmpty {
                    ZoomableTopo(photoUrl: photo, lines: newLines)
                } else if !newLines.isEmpty {
                    Text("SIN FOTO — el proponente no adjuntó imagen")
                        .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                }
                ForEach(Array(newLines.enumerated()), id: \.offset) { _, l in
                    Text("NUEVA · \(lineSummary(l.name, l.grade, l.startType))")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                }
            } else {
                // Corrección/edición: una sección por cara con FOTO ACTUAL vs PROPUESTA.
                let faces = targetBlock?.facesOrDerived() ?? []
                ForEach(Array(faceGroups.enumerated()), id: \.offset) { _, group in
                    let targetIds = Set(group.vias.compactMap { $0.targetLineId })
                    let oldFace = faces.first { f in f.lines.contains { targetIds.contains($0.id) } }
                        ?? faces.first { ($0.photoPath ?? "") == group.key }
                        ?? faces.first
                    let oldPhoto = oldFace?.photoPath ?? targetBlock?.photoPath
                    let photoChanged = !group.key.isEmpty && group.key != (oldPhoto ?? "")
                    let proposalPhoto = group.key.isEmpty ? oldPhoto : group.key

                    Divider().overlay(Cumbre.rule)
                    // FOTO ACTUAL
                    if let op = oldPhoto, !op.isEmpty {
                        Text(photoChanged ? "FOTO ACTUAL" : "ACTUAL")
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                        ZoomableTopo(photoUrl: op, lines: (oldFace?.lines ?? []).map { TopoLineVM($0) })
                    }
                    // PROPUESTA
                    Text(photoChanged ? "FOTO PROPUESTA (NUEVA)" : "PROPUESTA")
                        .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                    if let pp = proposalPhoto, !pp.isEmpty {
                        let keep = photoChanged ? [] :
                            (oldFace?.lines ?? []).filter { !targetIds.contains($0.id) }.map { TopoLineVM($0) }
                        ZoomableTopo(photoUrl: pp, lines: keep + group.vias.map { $0.line })
                    }
                    // Texto: original → propuesta por vía.
                    ForEach(Array(group.vias.enumerated()), id: \.offset) { _, v in
                        if let tid = v.targetLineId, let o = oldFace?.lines.first(where: { $0.id == tid }) {
                            Text("• \(lineSummary(o.name, o.grade, o.startType))  →  \(lineSummary(v.line.name, v.line.grade, v.line.startType))")
                                .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink)
                        } else {
                            Text("• NUEVA · \(lineSummary(v.line.name, v.line.grade, v.line.startType))")
                                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
                }
            }
        }
    }

    private var header: String {
        if isNewBoulder { return "PIEDRA NUEVA" }
        let nm = (targetBlock?.name ?? contribution.name ?? "piedra").uppercased()
        if isEditLine && !newLines.isEmpty && newLines.count > oldEdited.count {
            return "EDITA / AÑADE VÍAS DE «\(nm)»"
        }
        return isEditLine ? "CORRIGE VÍA(S) DE «\(nm)»" : "AÑADE VÍAS A «\(nm)»"
    }
}

/// Topo pulsable: tocar la foto la abre a pantalla completa con las vías
/// pintadas encima (para revisar cómo las trazó el proponente).
private struct ZoomableTopo: View {
    let photoUrl: String
    let lines: [TopoLineVM]
    @State private var open = false
    var body: some View {
        TopoPhotoView(photoUrl: photoUrl, lines: lines)
            .overlay(alignment: .bottomTrailing) {
                Text("TOCA PARA AMPLIAR")
                    .font(Cumbre.mono(8, .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 5).padding(.vertical, 3)
                    .background(Color.black.opacity(0.55))
                    .padding(4)
            }
            .contentShape(Rectangle())
            .onTapGesture { open = true }
            .fullScreenCover(isPresented: $open) {
                ZStack(alignment: .topTrailing) {
                    Color.black.ignoresSafeArea()
                        .onTapGesture { open = false }
                    VStack {
                        Spacer()
                        TopoPhotoView(photoUrl: photoUrl, lines: lines)
                        Spacer()
                    }
                    Button("✕ CERRAR") { open = false }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                        .padding(16)
                }
            }
    }
}

/// Resumen de una vía (nombre · grado · tipo) para el admin.
private func lineSummary(_ name: String?, _ grade: String?, _ start: String?) -> String {
    [name?.isEmpty == false ? name : nil, grade, start]
        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
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

/// Resumen humano de UNA LÍNEA de qué cambia una propuesta — espejo de
/// contributionSummary de ContributionCard.kt (Android).
func contributionSummary(_ c: Contribution, blocks: [Block]) -> String {
    let target = blocks.first { $0.id == c.targetBlockId }
    switch c.type.uppercased() {
    case "PARKING":
        let nm = (c.name?.isEmpty == false) ? " «\(c.name!)»" : ""
        return "Añade un parking nuevo" + nm
    case "SECTOR":
        let nm = (c.name?.isEmpty == false) ? " «\(c.name!)»" : ""
        return "Añade un sector nuevo" + nm
    case "ASSIGN_SECTOR":
        let sector = blocks.first { $0.id == c.sectorBlockId }
        return "Mueve «\(target?.name ?? "una piedra")» al sector «\(sector?.name ?? "?")»"
    case "POSITION_CORRECTION":
        let what = c.targetBlockId == nil ? "la ESCUELA entera"
                   : "«\(target?.name ?? c.name ?? "un elemento")»"
        if let la = c.proposedLat?.doubleValue, let lo = c.proposedLon?.doubleValue {
            let meters = Int(Geo.shared.haversineKm(lat1: c.lat, lon1: c.lon, lat2: la, lon2: lo) * 1000)
            return "Mueve \(what) unos \(meters) m"
        }
        return "Mueve \(what)"
    case "BOULDER":
        let vias = TopoParse.proposedVias(c.bloquesJson)
        let corrige = vias.filter { $0.targetLineId != nil }.count
        let nuevas = max(vias.count - corrige, 0)
        let esMuro = (c.geometry ?? "").uppercased() == "LINE"
        if target == nil {
            return (esMuro ? "Muro NUEVO" : "Piedra NUEVA") + " con \(vias.count) vía\(vias.count == 1 ? "" : "s")"
        }
        var parts: [String] = []
        if nuevas > 0 { parts.append("añade \(nuevas) vía\(nuevas == 1 ? "" : "s")") }
        if corrige > 0 { parts.append("corrige \(corrige)") }
        if parts.isEmpty { parts.append("cambios en el trazado/orden") }
        return (esMuro ? "Muro" : "Piedra") + " «\(target!.name)»: " + parts.joined(separator: " y ")
    default:
        return "Propuesta de tipo \(c.type)"
    }
}


/// Color por tipo de bloque (parking azul, zona verde, piedra terra).
func blockTypeColor(_ t: String) -> UIColor {
    switch t.uppercased() {
    case "PARKING": return UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    case "ZONE":    return UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    default:        return UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    }
}
func markerKindFor(_ t: String) -> MarkerKind {
    switch t.uppercased() { case "PARKING": return .parking; case "ZONE": return .zone; default: return .block }
}

/// Bloques existentes de la escuela como CONTEXTO (atenuados), para que el admin
/// vea dónde cae la propuesta y si pisa algo. Excluye la piedra/sector implicados
/// (van resaltados en `contributionMarkers`).
func contextMarkers(_ c: Contribution, blocks: [Block]) -> [CumbreMarker] {
    blocks.filter { $0.id != c.targetBlockId && $0.id != c.sectorBlockId }.map { b in
        CumbreMarker(id: "ctx-\(b.id)",
            coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
            title: b.name.isEmpty ? b.type : b.name, kind: markerKindFor(b.type),
            color: blockTypeColor(b.type).withAlphaComponent(0.5), name: b.name)
    }
}

/// Marcadores RESALTADOS de la propuesta (lo que cambia):
/// CORREGIR = ✕ viejo gris + ★ nuevo ámbar; ASIGNAR SECTOR = piedra ★ ámbar +
/// sector viejo ✕ gris + sector nuevo ★ verde; resto = ★ ámbar en la propuesta.
func contributionMarkers(_ c: Contribution, blocks: [Block] = []) -> [CumbreMarker] {
    let amber = UIColor(hex: 0xF59E0B)
    let gray = UIColor(white: 0.55, alpha: 1)
    let green = UIColor(red: 0.20, green: 0.55, blue: 0.30, alpha: 1)
    switch c.type.uppercased() {
    case "POSITION_CORRECTION":
        var ms = [CumbreMarker(id: "old", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                               title: "Actual", kind: .block, color: gray, name: "✕")]
        if let la = c.proposedLat?.doubleValue, let lo = c.proposedLon?.doubleValue {
            ms.append(CumbreMarker(id: "new", coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                                   title: "Nueva", kind: .block, color: amber, name: "★"))
        }
        return ms
    case "ASSIGN_SECTOR":
        let stone = blocks.first { $0.id == c.targetBlockId }
        let stoneCoord = stone.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
            ?? CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon)
        var ms = [CumbreMarker(id: "stone", coordinate: stoneCoord, title: "Piedra", kind: .block,
                               color: amber, name: stone.flatMap { $0.name.isEmpty ? nil : $0.name } ?? "PIEDRA")]
        if let oldId = stone?.sectorBlockId, let oldSec = blocks.first(where: { $0.id == oldId }) {
            ms.append(CumbreMarker(id: "oldsec",
                coordinate: CLLocationCoordinate2D(latitude: oldSec.lat, longitude: oldSec.lon),
                title: "Sector actual", kind: .zone, color: gray, name: "✕ \(oldSec.name)"))
        }
        if let newSec = blocks.first(where: { $0.id == c.sectorBlockId }) {
            ms.append(CumbreMarker(id: "newsec",
                coordinate: CLLocationCoordinate2D(latitude: newSec.lat, longitude: newSec.lon),
                title: "Sector nuevo", kind: .zone, color: green, name: "★ \(newSec.name)"))
        }
        return ms
    default:
        return [CumbreMarker(id: "p", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                             title: c.name ?? adminTypeLabel(c.type), kind: markerKindFor(c.type),
                             color: amber, name: c.name.flatMap { $0.isEmpty ? nil : $0 } ?? "★")]
    }
}

/// Polilíneas de un cambio de sector: piedra → sector viejo (gris) y → nuevo (verde).
func contributionPolylines(_ c: Contribution, blocks: [Block]) -> [CumbrePolyline] {
    guard c.type.uppercased() == "ASSIGN_SECTOR",
          let stone = blocks.first(where: { $0.id == c.targetBlockId }) else { return [] }
    let stoneCoord = CLLocationCoordinate2D(latitude: stone.lat, longitude: stone.lon)
    var lines: [CumbrePolyline] = []
    if let oldId = stone.sectorBlockId, let oldSec = blocks.first(where: { $0.id == oldId }) {
        lines.append(CumbrePolyline(id: "old",
            coordinates: [stoneCoord, CLLocationCoordinate2D(latitude: oldSec.lat, longitude: oldSec.lon)],
            color: UIColor(white: 0.55, alpha: 1), width: 3, alpha: 0.9))
    }
    if let newSec = blocks.first(where: { $0.id == c.sectorBlockId }) {
        lines.append(CumbrePolyline(id: "new",
            coordinates: [stoneCoord, CLLocationCoordinate2D(latitude: newSec.lat, longitude: newSec.lon)],
            color: UIColor(red: 0.20, green: 0.55, blue: 0.30, alpha: 1), width: 4, alpha: 1))
    }
    return lines
}

/// Mini-mapa (no interactivo) dentro de la card del admin: contexto + cambio.
/// Espejo del mini-mapa de ContributionCard.kt. Para interactuar, "VER EN MAPA".
private struct ContributionMiniMap: View {
    let contribution: Contribution
    let blocks: [Block]
    @State private var style: MapStyleKind = .topo
    private var proposed: [CumbreMarker] { contributionMarkers(contribution, blocks: blocks) }
    var body: some View {
        MapLibreView(
            center: CLLocationCoordinate2D(latitude: contribution.lat, longitude: contribution.lon),
            zoom: 16, markers: contextMarkers(contribution, blocks: blocks) + proposed, style: style,
            fitToCoordinatesOnLoad: proposed.count >= 2 ? proposed.map { $0.coordinate } : [],
            polylines: contributionPolylines(contribution, blocks: blocks))
        .frame(height: 200)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .allowsHitTesting(false)
    }
}

/// Mapa a pantalla completa de una propuesta (espejo de FullScreenMapDialog).
/// Carga los bloques de la escuela: pinta los existentes de CONTEXTO (atenuados)
/// y resalta lo que cambia (✕ viejo / ★ nuevo, o piedra + sector viejo/nuevo).
private struct ContributionMapSheet: View {
    let contribution: Contribution
    @Environment(\.dismiss) private var dismiss
    @State private var style: MapStyleKind = .topo
    @State private var blocks: [Block] = []
    @State private var loaded = false

    private var proposed: [CumbreMarker] { contributionMarkers(contribution, blocks: blocks) }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                if loaded {
                    MapLibreView(
                        center: CLLocationCoordinate2D(latitude: contribution.lat, longitude: contribution.lon),
                        zoom: 16, markers: contextMarkers(contribution, blocks: blocks) + proposed, style: style,
                        // Encuadra los marcadores clave de la propuesta al cargar
                        // (✕/★ o piedra+sectores) para que todos entren en pantalla.
                        fitToCoordinatesOnLoad: proposed.count >= 2 ? proposed.map { $0.coordinate } : [],
                        polylines: contributionPolylines(contribution, blocks: blocks))
                    MapStyleChips(selection: $style)
                } else {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(adminTypeLabel(contribution.type))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task {
            blocks = (try? await AppDependencies.shared.container.getBlocks
                .invoke(schoolId: contribution.schoolId)) ?? []
            loaded = true
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
                    Text(NSLocalizedString("admin_reject", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button(action: onApprove) {
                    Text(NSLocalizedString("admin_approve", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(.white)
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
                ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_cancel", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) }
            }
        }
    }
}

private enum AdminTab: String, CaseIterable {
    case propuestas = "PROPUESTAS"
    case denuncias = "DENUNCIAS"
    case gestionar = "GESTIONAR"
    case stats = "STATS"
    case actividad = "ACTIVIDAD"
    case push = "PUSH"
}

/// Tab STATS — tarjetas con estadísticas de la plataforma (espejo de StatsTab).
private struct AdminStatsTab: View {
    let stats: AdminStats?
    /// Cambia de pestaña (ESCUELAS → gestionar, PENDIENTES → propuestas).
    var onGoToTab: (AdminTab) -> Void = { _ in }
    @State private var openList: String? = nil
    @State private var users: [AdminUserRowDto]? = nil
    @State private var notes: [AdminNoteRowDto]? = nil

    var body: some View {
        ScrollView {
            if let s = stats {
                Text("Toca una tarjeta para ver su lista")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.top, 10)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    card("USUARIOS", s.totalUsers) { openList = "users"; loadUsers() }
                    card("ADMINS", s.totalAdmins) { openList = "admins"; loadUsers() }
                    card("ESCUELAS", s.totalSchools) { onGoToTab(.gestionar) }
                    card("NOTAS", s.totalNotes) { openList = "notes"; loadNotes() }
                    card("PENDIENTES", s.submissionsPending) { onGoToTab(.propuestas) }
                    card("APROBADAS", s.submissionsApproved) { onGoToTab(.actividad) }
                    card("RECHAZADAS", s.submissionsRejected) { onGoToTab(.actividad) }
                }.padding(16)
            } else {
                ProgressView().padding(.top, 40)
            }
        }
        .sheet(isPresented: Binding(get: { openList != nil }, set: { if !$0 { openList = nil } })) {
            listSheet
        }
    }

    private func loadUsers() {
        guard users == nil else { return }
        Task { users = (try? await AppDependencies.shared.container.moderationApi.getAdminUsers()) ?? [] }
    }
    private func loadNotes() {
        guard notes == nil else { return }
        Task { notes = (try? await AppDependencies.shared.container.moderationApi.getAdminNotes()) ?? [] }
    }

    @ViewBuilder private var listSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if openList == "notes" {
                        if let list = notes {
                            ForEach(list, id: \.id) { n in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(n.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    Text([n.author, n.schoolId, (n.createdAt ?? "").isEmpty ? nil : String((n.createdAt ?? "").prefix(10))]
                                            .compactMap { $0 }.joined(separator: " - "))
                                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.vertical, 8)
                                Divider().overlay(Cumbre.rule)
                            }
                        } else { ProgressView().padding(.top, 30) }
                    } else {
                        if let list = users {
                            let shown = openList == "admins" ? list.filter { $0.isAdmin } : list
                            ForEach(shown, id: \.uid) { u in
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))))
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        if u.isAdmin {
                                            Text("ADMIN").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                                        }
                                    }
                                    Spacer()
                                    Text(String((u.createdAt ?? "").prefix(10)))
                                        .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(.vertical, 8)
                                Divider().overlay(Cumbre.rule)
                            }
                        } else { ProgressView().padding(.top, 30) }
                    }
                }
                .padding(.horizontal, 16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(openList == "notes" ? "Notas" : (openList == "admins" ? "Admins" : "Usuarios"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func card(_ label: String, _ value: Int64, action: @escaping () -> Void = {}) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text("\(value)").font(Cumbre.serif(28, .bold)).foregroundStyle(Cumbre.ink)
                Text(label).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 16)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
    @State private var targetUid: String? = nil
    @State private var targetLabel: String? = nil
    @State private var query = ""
    @State private var results: [PublicProfile] = []
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var title = ""
    @State private var body_ = ""
    @State private var confirmAll = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let uid = targetUid {
                    HStack(spacing: 8) {
                        Text("PARA: " + (targetLabel ?? uid))
                            .font(Cumbre.mono(11, .bold)).tracking(0.6)
                            .foregroundStyle(Cumbre.terra)
                        Button("✕ QUITAR") { targetUid = nil; targetLabel = nil }
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                            .buttonStyle(.plain)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("DESTINATARIO").eyebrow()
                        TextField("Buscar por @usuario o nombre…", text: $query)
                            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            .onChange(of: query) { _, q in
                                searchTask?.cancel()
                                let t = q.trimmingCharacters(in: .whitespaces)
                                guard t.count >= 2 else { results = []; return }
                                searchTask = Task {
                                    try? await Task.sleep(nanoseconds: 250_000_000)
                                    guard !Task.isCancelled else { return }
                                    results = (try? await AppDependencies.shared.container.searchUsers.invoke(query: t, limit: 10)) ?? []
                                }
                            }
                        ForEach(results.prefix(6), id: \.uid) { u in
                            Button {
                                targetUid = u.uid
                                targetLabel = u.username.map { "@" + $0 } ?? u.displayName
                                query = ""; results = []
                            } label: {
                                Text(u.username.map { "@" + $0 } ?? (u.displayName ?? u.uid))
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 10).padding(.vertical, 8)
                                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                        Text("Sin destinatario → se enviará a TODOS los usuarios.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                }
                field("TÍTULO", $title, "Título")
                VStack(alignment: .leading, spacing: 6) {
                    Text("MENSAJE").eyebrow()
                    TextField("Mensaje", text: $body_, axis: .vertical).lineLimit(3...6)
                        .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                Button {
                    if targetUid == nil { confirmAll = true }
                    else { vm.sendPush(targetUid: targetUid, title: title, body: body_) }
                } label: {
                    HStack { if vm.pushBusy { ProgressView().tint(.white) }
                        Text(targetUid == nil ? "ENVIAR A TODOS LOS USUARIOS" : "ENVIAR PUSH")
                            .font(Cumbre.mono(13, .bold)).tracking(0.8) }
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                }.buttonStyle(.plain)
                .disabled(vm.pushBusy || title.isEmpty || body_.isEmpty)
                .alert("¿Enviar a TODOS?", isPresented: $confirmAll) {
                    Button("Cancelar", role: .cancel) {}
                    Button("Sí, a todos", role: .destructive) {
                        vm.sendPush(targetUid: nil, title: title, body: body_)
                    }
                } message: {
                    Text("El push llegará a todos los usuarios de Cumbre.")
                }
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
        .fullScreenCover(item: $selected) { s in
            NavigationStack {
                SchoolDetailView(school: s)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("CERRAR") { selected = nil }
                                .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
            }
        }
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
    @State private var moving: Block?   // bloque en modo "mover pulsando en el mapa"

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                MapLibreView(
                    center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                    zoom: 15, markers: markers, style: style,
                    // En modo mover, el tap del mapa fija la nueva posición; si no,
                    // tocar un marcador abre su gestión.
                    onTapMarker: { id in if moving == nil { manage = blocks.first { $0.id == id } } },
                    onMapTap: moving == nil ? nil : { coord in Task { await move(to: coord) } })
                MapStyleChips(selection: $style)
            }
            .overlay(alignment: .top) { if let m = moving { moveBanner(m) } }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(school.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task { await reload() }
        .sheet(item: $manage) { b in
            BlockManageSheet(block: b,
                             onMove: { moved in moving = moved; manage = nil },
                             onDone: { Task { await reload() }; manage = nil })
        }
    }

    private func moveBanner(_ b: Block) -> some View {
        HStack(spacing: 10) {
            Text("PULSA LA NUEVA POSICIÓN DE «\((b.name.isEmpty ? blockTypeLabel(b.type) : b.name).uppercased())»")
                .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button { moving = nil } label: {
                Text("CANCELAR").font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity).background(Cumbre.terra)
    }

    /// Mueve el bloque a la coord pulsada, preservando type/foto/sector y las VÍAS
    /// (mismo cuidado que BlockManageSheet.save: si no se mandan, se borrarían).
    private func move(to coord: CLLocationCoordinate2D) async {
        guard let b = moving else { return }
        let lines = b.lines.map {
            CreateBlockLineRequest(name: $0.name, grade: $0.grade, startType: $0.startType, linePath: $0.linePath, photoPath: $0.photoPath, faceOrder: $0.faceOrder, description: $0.lineDescription)
        }
        let req = CreateBlockRequest(type: b.type, name: b.name,
                                     lat: coord.latitude, lon: coord.longitude,
                                     photoPath: b.photoPath, description: b.descriptionText,
                                     lines: lines, sectorBlockId: b.sectorBlockId,
                                     discipline: b.discipline,
                                     geometry: b.geometry, path: b.path, direction: b.direction)
        _ = try? await AppDependencies.shared.container.updateBlock.invoke(blockId: b.id, req: req)
        moving = nil
        await reload()
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

/// Etiqueta corta del tipo de un bloque (PARKING / ZONA / PIEDRA).
func blockTypeLabel(_ t: String) -> String {
    switch t.uppercased() { case "PARKING": return "PARKING"; case "ZONE": return "ZONA"; default: return "PIEDRA" }
}

/// Gestión de un bloque (admin): editar nombre + descripción + coordenadas
/// (preservando las vías) o borrarlo. Espejo de EditBlockDialog/BlockDetailDialog.
private struct BlockManageSheet: View {
    let block: Block
    let onMove: (Block) -> Void
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var desc: String
    @State private var latText: String
    @State private var lonText: String
    @State private var discipline: String
    @State private var busy = false
    @State private var confirmDelete = false

    init(block: Block, onMove: @escaping (Block) -> Void, onDone: @escaping () -> Void) {
        self.block = block; self.onMove = onMove; self.onDone = onDone
        _name = State(initialValue: block.name)
        // `descriptionText` (alias Kotlin) evita el choque con NSObject.description.
        _desc = State(initialValue: block.descriptionText ?? "")
        _latText = State(initialValue: String(format: "%.6f", block.lat))
        _lonText = State(initialValue: String(format: "%.6f", block.lon))
        _discipline = State(initialValue: block.discipline)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    field("NOMBRE", $name, "Nombre")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("DESCRIPCIÓN").eyebrow()
                        TextField("Descripción (opcional)", text: $desc, axis: .vertical)
                            .lineLimit(2...5).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    if block.type == "BLOCK" {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("MODALIDAD").eyebrow()
                            DisciplineSelector(selected: $discipline)
                        }
                    }
                    field("LATITUD", $latText, "lat")
                    field("LONGITUD", $lonText, "lon")
                    Button { Task { await save() } } label: {
                        HStack { if busy { ProgressView().tint(.white) }
                            Text("GUARDAR CAMBIOS").font(Cumbre.mono(12, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(busy)
                    Button { onMove(block) } label: {
                        Text("📍 MOVER PULSANDO EN EL MAPA").font(Cumbre.mono(12, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.ink)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
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
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
            .alert("¿Borrar este bloque?", isPresented: $confirmDelete) {
                Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
                Button(NSLocalizedString("common_delete", comment: ""), role: .destructive) { Task { await remove() } }
            } message: { Text("Se borrará «\(block.name)» y sus vías. No se puede deshacer.") }
        }
    }

    private var typeLabel: String { blockTypeLabel(block.type) }

    private func save() async {
        busy = true
        let lat = Double(latText) ?? block.lat
        let lon = Double(lonText) ?? block.lon
        // Preservamos type/foto/sector y las VÍAS (si no, se borrarían).
        let lines = block.lines.map {
            CreateBlockLineRequest(name: $0.name, grade: $0.grade, startType: $0.startType, linePath: $0.linePath, photoPath: $0.photoPath, faceOrder: $0.faceOrder, description: $0.lineDescription)
        }
        let trimmed = desc.trimmingCharacters(in: .whitespacesAndNewlines)
        let req = CreateBlockRequest(type: block.type, name: name, lat: lat, lon: lon,
                                     photoPath: block.photoPath,
                                     description: trimmed.isEmpty ? nil : trimmed,
                                     lines: lines, sectorBlockId: block.sectorBlockId,
                                     discipline: block.type == "BLOCK" ? discipline : nil,
                                     geometry: block.geometry, path: block.path, direction: block.direction)
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

// MARK: - Tab DENUNCIAS (contenido + quedadas) — espejo de DenunciasTab.kt
private struct AdminReportsTab: View {
    @State private var contentReports: [ContentReportDto] = []
    @State private var meetupReports: [MeetupReport] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if contentReports.isEmpty && meetupReports.isEmpty {
                    Text("Sin denuncias pendientes")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.top, 40)
                }
                if !contentReports.isEmpty {
                    Text("CONTENIDO (comentarios / notas / usuarios)")
                        .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    ForEach(contentReports, id: \.id) { r in
                        contentCard(r)
                    }
                }
                if !meetupReports.isEmpty {
                    Text("QUEDADAS")
                        .font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        .padding(.top, 6)
                    ForEach(meetupReports, id: \.id) { r in
                        meetupCard(r)
                    }
                }
            }
            .padding(16)
        }
        .background(Cumbre.bg)
        .task { await load() }
    }

    private func load() async {
        let c = AppDependencies.shared.container
        contentReports = (try? await c.moderationApi.getContentReports()) ?? []
        meetupReports = (try? await c.getPendingReports.invoke()) ?? []
    }

    private func resolveContent(_ id: String, _ action: String) {
        Task {
            _ = try? await AppDependencies.shared.container.moderationApi
                .resolveContentReport(id: id, action: action)
            await load()
        }
    }

    private func resolveMeetup(_ id: String, _ action: String) {
        Task {
            _ = try? await AppDependencies.shared.container.resolveReport.invoke(id: id, action: action)
            await load()
        }
    }

    @ViewBuilder private func contentCard(_ r: ContentReportDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(kindLabel(r.targetType) + " - " + reasonLabel(r.reason))
                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.bad)
                Spacer()
                Text(String((r.createdAt ?? "").prefix(10)))
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            }
            Text(r.snapshot ?? "(contenido no disponible)")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            HStack(spacing: 8) {
                Button { resolveContent(r.id, "REMOVE") } label: {
                    Text(r.targetType == "USER" ? "MARCAR REVISADO" : "RETIRAR CONTENIDO")
                        .font(Cumbre.mono(11, .bold)).tracking(0.5).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { resolveContent(r.id, "IGNORE") } label: {
                    Text("IGNORAR")
                        .font(Cumbre.mono(11, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(12)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    @ViewBuilder private func meetupCard(_ r: MeetupReport) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(reasonLabel(String(describing: r.reason)) + " - quedada " + String(r.meetupId.prefix(8)))
                .font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.bad)
            if let ctx = r.context, !ctx.isEmpty {
                Text(ctx).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            }
            HStack(spacing: 8) {
                Button { resolveMeetup(r.id, "resolve") } label: {
                    Text("RESOLVER").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { resolveMeetup(r.id, "dismiss") } label: {
                    Text("IGNORAR").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink3)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(12)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func kindLabel(_ t: String) -> String {
        switch t { case "COMMENT": return "COMENTARIO"; case "NOTE": return "NOTA"; default: return "USUARIO" }
    }
    private func reasonLabel(_ r: String) -> String {
        switch r.uppercased() {
        case "SPAM": return "Spam"; case "OFFENSIVE": return "Ofensivo"
        case "FALSE_INFO": return "Info falsa"; case "HARASSMENT": return "Acoso"
        case "INAPPROPRIATE": return "Inapropiado"; default: return "Otro" }
    }
}
