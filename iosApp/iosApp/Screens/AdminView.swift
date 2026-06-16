import SwiftUI
import Shared

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

struct AdminView: View {
    @StateObject private var vm = AdminViewModel()
    @State private var rejecting: RejectTarget?

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
                                ForEach(vm.contributions, id: \.id) { c in
                                    ContributionAdminCard(
                                        contribution: c,
                                        busy: vm.working.contains(c.id),
                                        onApprove: { vm.reviewContribution(c.id, approve: true, reason: nil) },
                                        onReject: { rejecting = RejectTarget(id: c.id, isSubmission: false) }
                                    )
                                    Divider().overlay(Cumbre.rule)
                                }
                            } header: { sectionHeader("MEJORAS · \(vm.contributions.count)") }
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
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(typeLabel(contribution.type)).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                Spacer()
                if let a = contribution.submittedByName, !a.isEmpty {
                    Text(a).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Text(contribution.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
            if let nm = contribution.name, !nm.isEmpty { Text(nm).font(.system(size: 14)).foregroundStyle(Cumbre.ink) }
            if let n = contribution.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
            Text(String(format: "%.5f, %.5f", contribution.lat, contribution.lon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "BOULDER": return "PIEDRA"
        case "SECTOR": return "SECTOR"
        case "POSITION_CORRECTION": return "CORREGIR POSICIÓN"
        case "ASSIGN_SECTOR": return "ASIGNAR SECTOR"
        default: return t.uppercased()
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
