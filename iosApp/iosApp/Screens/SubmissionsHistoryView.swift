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
                                HistoryRowCard(row: row)
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
        }
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
}

private struct HistoryRowCard: View {
    let row: HistoryRow

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
            kind: c.type,
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
        }
        .padding(12)
    }

    private func shortDate(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "d MMM, HH:mm"; f.locale = Locale(identifier: "es_ES")
        return f.string(from: d)
    }
}
