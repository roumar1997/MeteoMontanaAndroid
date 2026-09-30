import SwiftUI
import Shared

/// Historial de propuestas de escuela y mejoras ya revisadas: qué se pidió,
/// quién lo propuso, quién lo aprobó o rechazó (y por qué si se rechazó).
/// Álvaro, 2026-09-30: "me gustaria poder ver propuestas pasadas, para ver
/// que se hizo, saber quien la hizo, quien la aprobó tambien, las rechazadas
/// tambien".
struct SubmissionsHistoryView: View {
    /// "APPROVED" o "REJECTED".
    let status: String

    @State private var submissions: [Submission]?
    @State private var contributions: [Contribution]?
    @Environment(\.dismiss) private var dismiss

    private let getSubs = AppDependencies.shared.container.getPendingSubmissions
    private let getContribs = AppDependencies.shared.container.getPendingContributions

    /// "MODIFICAR": qué se está cargando/editando ahora mismo, si algo.
    @State private var modifySchool: School?
    @State private var modifyBlock: Block?
    @State private var modifyError = false

    private var title: String { status == "APPROVED" ? "Aprobadas" : "Rechazadas" }

    /// Items mezclados y ordenados por fecha de revisión, más reciente primero.
    private var rows: [HistoryRow] {
        let a = (submissions ?? []).map { HistoryRow.submission($0) }
        let b = (contributions ?? []).map { HistoryRow.contribution($0) }
        return (a + b).sorted { $0.sortDate > $1.sortDate }
    }

    var body: some View {
        NavigationStack {
            Group {
                if submissions == nil || contributions == nil {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if rows.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "tray").font(.system(size: 32)).foregroundStyle(Cumbre.ink3)
                        Text("Nada por aquí todavía.").font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(rows) { row in
                                HistoryRowCard(row: row) { target in Task { await openModify(target) } }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
            .task {
                submissions = (try? await getSubs.invoke(status: status)) ?? []
                contributions = (try? await getContribs.invoke(status: status)) ?? []
            }
            .sheet(item: $modifySchool) { s in EditSchoolSheet(school: s) }
            .sheet(item: $modifyBlock) { b in
                BlockManageSheet(block: b, onMove: { _ in }, onDone: {}, showMoveOnMap: false)
            }
            .alert("No se pudo cargar para modificar.", isPresented: $modifyError) {
                Button(NSLocalizedString("common_ok", comment: ""), role: .cancel) {}
            }
        }
    }

    /// Resuelve el objetivo (escuela o bloque) y lo carga antes de abrir su
    /// ficha de edición — el Historial solo tiene el id, no el objeto entero.
    private func openModify(_ target: HistoryModifyTarget) async {
        switch target {
        case .school(let id):
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: id) {
                modifySchool = s
            } else { modifyError = true }
        case .block(let id):
            if let b = try? await AppDependencies.shared.container.getBlock.invoke(blockId: id) {
                modifyBlock = b
            } else { modifyError = true }
        }
    }
}

/// A qué hay que saltar al pulsar "MODIFICAR" en una fila del Historial
/// (Álvaro, 2026-09-30: "por si modificarla despues... y si es un sector?
/// tambien... y si es una escuela? tambien").
enum HistoryModifyTarget: Identifiable {
    case school(String)
    case block(String)
    var id: String {
        switch self { case .school(let i): return "s_\(i)"; case .block(let i): return "b_\(i)" }
    }
}

private enum HistoryRow: Identifiable {
    case submission(Submission)
    case contribution(Contribution)

    var id: String {
        switch self {
        case .submission(let s): return "sub_\(s.id)"
        case .contribution(let c): return "con_\(c.id)"
        }
    }
    var sortDate: String {
        switch self {
        case .submission(let s): return s.reviewedAt ?? s.createdAt
        case .contribution(let c): return c.reviewedAt ?? c.createdAt ?? ""
        }
    }

    /// A qué se puede saltar con "MODIFICAR" — nil si esta fila no dejó nada
    /// que tocar (p.ej. una escuela o una piedra nueva que fue RECHAZADA, así
    /// que nunca llegó a existir).
    var modifyTarget: HistoryModifyTarget? {
        switch self {
        case .submission(let s):
            guard let schoolId = s.createdSchoolId else { return nil }
            return .school(schoolId)
        case .contribution(let c):
            if c.type == "SCHOOL_NAME_CORRECTION" || c.type == "SCHOOL_STYLE_CORRECTION" {
                return .school(c.schoolId)
            }
            if let created = c.createdBlockId { return .block(created) }
            if let target = c.targetBlockId { return .block(target) }
            return nil
        }
    }
}

/// "BOULDER" es el tipo interno de la contribución, pero cubre tanto una
/// PIEDRA/MURO nuevos como añadir o corregir VÍAS sobre una piedra ya
/// existente — mostrar el tipo en crudo confundía ("por qué pone Boulder si
/// es una vía", Álvaro, 2026-09-30). Mismo criterio que contributionSummary
/// en AdminContributionCards.swift, sin necesitar la lista de bloques.
private func historyKindLabel(_ c: Contribution) -> String {
    guard c.type.uppercased() == "BOULDER" else { return adminTypeLabel(c.type) }
    let esMuro = (c.geometry ?? "").uppercased() == "LINE"
    if c.targetBlockId == nil {
        return esMuro ? "MURO NUEVO" : "PIEDRA NUEVA"
    }
    let vias = TopoParse.proposedVias(c.bloquesJson)
    let corrige = vias.filter { $0.targetLineId != nil }.count
    let nuevas = max(vias.count - corrige, 0)
    if nuevas > 0 && corrige > 0 { return "AÑADE Y CORRIGE VÍAS" }
    if corrige > 0 { return "CORRIGE VÍA" + (corrige == 1 ? "" : "S") }
    if nuevas > 0 { return "AÑADE VÍA" + (nuevas == 1 ? "" : "S") }
    return "CAMBIOS EN LA PIEDRA"
}

private struct HistoryRowCard: View {
    let row: HistoryRow
    let onModify: (HistoryModifyTarget) -> Void

    var body: some View {
        switch row {
        case .submission(let s): body(
            kind: "ESCUELA NUEVA",
            title: s.proposedName,
            photoUrl: s.submittedByPhotoPath,
            submittedByName: s.submittedByName,
            reviewedByName: s.reviewedByName,
            reviewedAt: s.reviewedAt,
            rejectReason: s.status == "REJECTED" ? s.reviewReason : nil
        )
        case .contribution(let c): body(
            kind: historyKindLabel(c),
            title: c.name ?? c.schoolName,
            photoUrl: c.submittedByPhotoPath,
            submittedByName: c.submittedByName,
            reviewedByName: c.reviewedByName,
            reviewedAt: c.reviewedAt,
            rejectReason: c.status == "REJECTED" ? c.reviewReason : nil
        )
        }
    }

    private func body(
        kind: String, title: String, photoUrl: String?,
        submittedByName: String?, reviewedByName: String?,
        reviewedAt: String?, rejectReason: String?
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(kind.uppercased()).font(Cumbre.mono(9, .bold)).tracking(0.6).foregroundStyle(Cumbre.ink3)
                Spacer()
                if let reviewedAt, let d = parseIsoLocalDateTime(reviewedAt) {
                    Text(shortDate(d)).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                }
            }
            Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)

            HStack(spacing: 6) {
                AvatarCircle(url: photoUrl, size: 22)
                Text(submittedByName ?? "alguien").font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                Text("→").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                Text(reviewedByName ?? "un admin").font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }

            if let rejectReason, !rejectReason.isEmpty {
                Text(rejectReason).font(.system(size: 12)).foregroundStyle(Cumbre.terra)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Cumbre.terraBg).overlay(Rectangle().stroke(Cumbre.terra.opacity(0.4), lineWidth: 1))
            }

            if let target = row.modifyTarget {
                Button { onModify(target) } label: {
                    Text("MODIFICAR ▸").font(Cumbre.mono(10, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                }.buttonStyle(.plain).padding(.top, 2)
            }
        }
        .padding(12)
    }

    private func shortDate(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "d MMM, HH:mm"; f.locale = Locale(identifier: "es_ES")
        return f.string(from: d)
    }
}
