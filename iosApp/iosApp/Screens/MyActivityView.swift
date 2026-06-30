import SwiftUI
import Shared

// Listas de "mi actividad" accesibles desde el perfil:
// mis propuestas de escuela, mis contribuciones de mejora y solicitudes de
// seguimiento. Espejo de MySubmissionsScreen.kt / contributions / FollowRequests.

// MARK: - Badge de estado compartido

private func statusColor(_ status: String) -> Color {
    switch status.uppercased() {
    case "APPROVED", "ACCEPTED": return Cumbre.ok
    case "REJECTED":             return Cumbre.bad
    default:                     return Cumbre.warn   // PENDING
    }
}

private func statusLabel(_ status: String) -> String {
    switch status.uppercased() {
    case "APPROVED", "ACCEPTED": return "APROBADA"
    case "REJECTED":             return "RECHAZADA"
    default:                     return "PENDIENTE"
    }
}

private struct StatusBadge: View {
    let status: String
    var body: some View {
        let c = statusColor(status)
        Text(statusLabel(status))
            .font(Cumbre.mono(10, .bold)).tracking(0.8)
            .foregroundStyle(c)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .overlay(Rectangle().stroke(c, lineWidth: 1))
    }
}

// MARK: - Mis propuestas de escuela

@MainActor
final class MySubmissionsViewModel: ObservableObject {
    @Published var items: [Submission] = []
    @Published var loading = true
    private let getMySubmissions: GetMySubmissionsUseCase
    init(getMySubmissions: GetMySubmissionsUseCase = AppDependencies.shared.container.getMySubmissions) {
        self.getMySubmissions = getMySubmissions
    }
    func load() async {
        loading = true
        items = (try? await getMySubmissions.invoke()) ?? []
        loading = false
    }
}

struct MySubmissionsView: View {
    @StateObject private var vm = MySubmissionsViewModel()
    var body: some View {
        listScaffold(title: NSLocalizedString("profile_my_proposals", comment: ""), loading: vm.loading, empty: vm.items.isEmpty,
                     emptyText: "Sin propuestas todavía", emptyIcon: "mappin.and.ellipse",
                     emptyHint: "Con \"+ Enviar escuela\" o + PROPONER en el mapa de una escuela puedes proponer cosas. Aquí verás su estado.") {
            ForEach(vm.items, id: \.id) { s in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(s.proposedName).font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        StatusBadge(status: s.status)
                    }
                    if !subtitle(s).isEmpty {
                        Text(subtitle(s)).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3)
                    }
                    if let n = s.notes, !n.isEmpty {
                        Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    }
                    if let reason = s.reviewReason, !reason.isEmpty {
                        Text("Motivo: \(reason)").font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                Divider().overlay(Cumbre.rule)
            }
        }
        .task { await vm.load() }
    }

    // Tipo de roca (mayúsculas) · región — como en Android.
    private func subtitle(_ s: Submission) -> String {
        var parts: [String] = []
        if let r = s.proposedRockType, !r.isEmpty { parts.append(r.uppercased()) }
        if let reg = s.proposedRegion, !reg.isEmpty { parts.append(reg) }
        return parts.joined(separator: "  ·  ")
    }
}

// MARK: - Mis contribuciones de mejora

@MainActor
final class MyContributionsViewModel: ObservableObject {
    @Published var items: [Contribution] = []
    @Published var loading = true
    private let getMyContributions: GetMyContributionsUseCase
    init(getMyContributions: GetMyContributionsUseCase = AppDependencies.shared.container.getMyContributions) {
        self.getMyContributions = getMyContributions
    }
    func load() async {
        loading = true
        items = (try? await getMyContributions.invoke()) ?? []
        loading = false
    }
}

struct MyContributionsView: View {
    @StateObject private var vm = MyContributionsViewModel()
    var body: some View {
        listScaffold(title: "Mis contribuciones", loading: vm.loading, empty: vm.items.isEmpty,
                     emptyText: "Sin mejoras todavía", emptyIcon: "mappin.and.ellipse",
                     emptyHint: "Desde el mapa de una escuela (+ PROPONER) puedes añadir parkings, piedras, vías o sectores.") {
            ForEach(vm.items, id: \.id) { c in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(typeLabel(c.type)).font(Cumbre.mono(11, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.terra)
                        Spacer()
                        StatusBadge(status: c.status)
                    }
                    Text(c.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                    if let n = c.notes, !n.isEmpty {
                        Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    }
                    if let reason = c.reviewReason, !reason.isEmpty {
                        Text("Motivo: \(reason)").font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                Divider().overlay(Cumbre.rule)
            }
        }
        .task { await vm.load() }
    }

    private func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "BOULDER": return "PIEDRA"
        case "SECTOR":  return "SECTOR"
        case "POSITION_CORRECTION": return "CORREGIR POSICIÓN"
        case "ASSIGN_SECTOR": return "ASIGNAR SECTOR"
        default: return t.uppercased()
        }
    }
}

// MARK: - Solicitudes de seguimiento

@MainActor
final class FollowRequestsViewModel: ObservableObject {
    @Published var items: [PublicProfile] = []
    @Published var loading = true
    private let getRequests: GetMyFollowRequestsUseCase
    private let accept: AcceptFollowRequestUseCase
    private let reject: RejectFollowRequestUseCase
    private let follow: FollowUserUseCase
    init(
        getRequests: GetMyFollowRequestsUseCase = AppDependencies.shared.container.getMyFollowRequests,
        accept: AcceptFollowRequestUseCase = AppDependencies.shared.container.acceptFollowRequest,
        reject: RejectFollowRequestUseCase = AppDependencies.shared.container.rejectFollowRequest,
        follow: FollowUserUseCase = AppDependencies.shared.container.followUser
    ) {
        self.getRequests = getRequests; self.accept = accept; self.reject = reject; self.follow = follow
    }
    func load() async {
        loading = true
        items = (try? await getRequests.invoke()) ?? []
        loading = false
    }
    func respond(_ uid: String, accept doAccept: Bool) {
        items.removeAll { $0.uid == uid }
        Task {
            if doAccept { try? await accept.invoke(requesterUid: uid) }
            else { try? await reject.invoke(requesterUid: uid) }
        }
    }
    /// Acepta la solicitud y sigue a esa persona de vuelta.
    func acceptAndFollow(_ uid: String) {
        items.removeAll { $0.uid == uid }
        Task {
            try? await accept.invoke(requesterUid: uid)
            try? await follow.invoke(uid: uid)
        }
    }
}

struct FollowRequestsView: View {
    @StateObject private var vm = FollowRequestsViewModel()
    var body: some View {
        listScaffold(title: NSLocalizedString("profile_follow_requests", comment: ""), loading: vm.loading, empty: vm.items.isEmpty,
                     emptyText: "Sin solicitudes", emptyIcon: "person.crop.circle.badge.questionmark",
                     emptyHint: "Cuando alguien pida seguirte (perfil privado), aparecerá aquí para aceptar o rechazar.") {
            ForEach(vm.items, id: \.uid) { u in
                HStack(spacing: 12) {
                    // Tocar el avatar/nombre abre el perfil público del solicitante
                    // (para reconocerlo antes de aceptar/rechazar).
                    NavigationLink(destination: PublicProfileView(uid: u.uid)) {
                        HStack(spacing: 12) {
                            AvatarCircle(url: u.photoUrl, size: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(u.displayName ?? u.username ?? "Usuario")
                                    .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                if let n = u.username, !n.isEmpty {
                                    Text("@\(n)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                }
                            }
                            Spacer()
                        }
                    }.buttonStyle(.plain)
                    // Aceptar y seguir de vuelta.
                    Button { vm.acceptAndFollow(u.uid) } label: {
                        Image(systemName: "person.badge.plus").foregroundStyle(Cumbre.terra)
                            .frame(width: 36, height: 36).overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)
                    // Solo aceptar.
                    Button { vm.respond(u.uid, accept: true) } label: {
                        Image(systemName: "checkmark").foregroundStyle(Cumbre.ok)
                            .frame(width: 36, height: 36).overlay(Rectangle().stroke(Cumbre.ok, lineWidth: 1))
                    }.buttonStyle(.plain)
                    // Rechazar.
                    Button { vm.respond(u.uid, accept: false) } label: {
                        Image(systemName: "xmark").foregroundStyle(Cumbre.bad)
                            .frame(width: 36, height: 36).overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                    }.buttonStyle(.plain)
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                Divider().overlay(Cumbre.rule)
            }
        }
        .task { await vm.load() }
    }
}

// MARK: - Scaffold compartido de lista

@ViewBuilder
private func listScaffold<Content: View>(
    title: String, loading: Bool, empty: Bool, emptyText: String,
    emptyIcon: String = "tray", emptyHint: String = "",
    @ViewBuilder content: () -> Content
) -> some View {
    Group {
        if loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if empty {
            EmptyStateView(icon: emptyIcon, title: emptyText,
                           message: emptyHint.isEmpty ? " " : emptyHint)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView { LazyVStack(spacing: 0) { content() } }
        }
    }
    .background(Cumbre.bg.ignoresSafeArea())
    .navigationTitle(title)
    .navigationBarTitleDisplayMode(.inline)
}
