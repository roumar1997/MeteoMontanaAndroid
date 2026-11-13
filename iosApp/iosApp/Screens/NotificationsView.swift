import SwiftUI
import Shared

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var items: [Shared.Notification] = []
    @Published var unread: Int = 0
    @Published var loading = true

    private let getMyNotifications: GetMyNotificationsUseCase
    private let markRead: MarkNotificationReadUseCase
    private let markAllRead: MarkAllNotificationsReadUseCase
    private let deleteOne: DeleteNotificationUseCase
    private let deleteAllUC: DeleteAllNotificationsUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getFollowStatus: GetFollowStatusUseCase

    /// Lo que he tocado en los botones de seguir (uid → estado); si no, manda lo que dijo el servidor.
    @Published var followOverrides: [String: NotifFollowState] = [:]

    /// Avisos agrupados (varios me gusta del mismo objeto en uno) y repartidos en HOY / ESTA SEMANA / ANTES.
    var groups: [NotificationGroup] { NotificationGrouping.shared.group(items: items) }

    init(
        getMyNotifications: GetMyNotificationsUseCase = AppDependencies.shared.container.getMyNotifications,
        markRead: MarkNotificationReadUseCase = AppDependencies.shared.container.markNotificationRead,
        markAllRead: MarkAllNotificationsReadUseCase = AppDependencies.shared.container.markAllNotificationsRead,
        deleteOne: DeleteNotificationUseCase = AppDependencies.shared.container.deleteNotification,
        deleteAllUC: DeleteAllNotificationsUseCase = AppDependencies.shared.container.deleteAllNotifications,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus
    ) {
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getFollowStatus = getFollowStatus
        self.getMyNotifications = getMyNotifications
        self.markRead = markRead
        self.markAllRead = markAllRead
        self.deleteOne = deleteOne
        self.deleteAllUC = deleteAllUC
    }

    func followState(_ n: Shared.Notification) -> NotifFollowState {
        if let uid = n.actorUid, let s = followOverrides[uid] { return s }
        if n.iFollowActor { return .following }
        return n.followRequested ? .requested : .none
    }

    /// Seguir / dejar de seguir a quien me acaba de seguir, sin salir de la bandeja.
    func toggleFollow(_ n: Shared.Notification) {
        guard let uid = n.actorUid else { return }
        let current = followState(n)
        if current == .requested { return }
        if current == .following {
            followOverrides[uid] = NotifFollowState.none
            Task {
                do { try await unfollowUser.invoke(uid: uid) } catch { followOverrides[uid] = .following }
            }
        } else {
            followOverrides[uid] = .following
            Task {
                do {
                    try await followUser.invoke(uid: uid)
                    let st = try await getFollowStatus.invoke(uid: uid)
                    followOverrides[uid] = st.iFollowThem ? .following : (st.requestPending ? .requested : NotifFollowState.none)
                } catch { followOverrides[uid] = NotifFollowState.none }
            }
        }
    }

    /// Borra un grupo de avisos (uno o varios me gusta fundidos) y lo sincroniza.
    func delete(_ g: NotificationGroup) {
        for n in g.members { delete(n) }
    }

    /// Borra una notificación (optimista) y lo sincroniza.
    func delete(_ n: Shared.Notification) {
        if n.readAt == nil { unread = max(0, unread - 1) }
        items.removeAll { $0.id == n.id }
        Task { try? await deleteOne.invoke(id: n.id) }
    }

    /// Borra todas (optimista) y lo sincroniza.
    func deleteAll() {
        items.removeAll()
        unread = 0
        Task { try? await deleteAllUC.invoke() }
    }

    func load() async {
        loading = true
        if let inbox = try? await getMyNotifications.invoke(limit: 50) {
            items = inbox.items
            unread = Int(inbox.unreadCount)
        }
        loading = false
    }

    func markAll() async {
        try? await markAllRead.invoke()
        await load()
    }

    /// Marca todas como leídas sin recargar (al cerrar la bandeja: ya las viste).
    func markAllSilent() async {
        guard unread > 0 else { return }
        try? await markAllRead.invoke()
    }

    /// Marca leída (si no lo estaba). Devuelve el destino de navegación según el
    /// targetType (espejo de NotificationsScreen.kt: "user"→perfil, "school"→detalle).
    func tap(_ g: NotificationGroup) -> NotifTarget? {
        let n = g.lead
        let unreadIds = g.members.filter { $0.readAt == nil }.map { $0.id }
        if !unreadIds.isEmpty {
            Task { for id in unreadIds { try? await markRead.invoke(id: id) }; await load() }
        }
        // Solicitud de seguimiento → a la lista de solicitudes (aceptar/rechazar).
        if n.targetType == "follow_request" { return .followRequests }
        guard let tid = n.targetId, !tid.isEmpty else { return nil }
        switch n.targetType {
        case "user": return .user(tid)
        case "school", "school_detail": return .school(tid)
        // Paridad con Android (MainScreen.kt): chat y propuestas. Hoy estos tipos
        // solo llegan por push (chat/notify, alerta), y iOS tiene APNs apagado, así
        // que aún no aparecen en la bandeja; el enrutado queda listo para cuando se
        // active APNs. El título de la notif de chat es el nombre del remitente.
        case "chat", "message": return .chat(tid, n.title)
        case "submission", "contribution": return .submissions
        // Actividad del feed Comunidad (likes/comentarios) → detalle del post.
        case "feed_post": return .feedPost(tid)
        default: return nil
        }
    }
}

/// Destino de navegación de una notificación.
enum NotifTarget: Identifiable, Hashable {
    case school(String)
    case user(String)
    case chat(String, String)   // (otherUid, otherName)
    case submissions
    case followRequests
    case feedPost(String)
    var id: String {
        switch self {
        case .school(let s): return "school-\(s)"
        case .user(let u): return "user-\(u)"
        case .chat(let u, _): return "chat-\(u)"
        case .submissions: return "submissions"
        case .followRequests: return "follow-requests"
        case .feedPost(let p): return "feed-post-\(p)"
        }
    }
}

/// Bandeja de notificaciones — espejo de NotificationsScreen.kt. Lista con
/// título, cuerpo, fecha y punto de "no leída"; botón "Marcar todas leídas".
struct NotificationsView: View {
    @StateObject private var vm = NotificationsViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var target: NotifTarget?
    @State private var showDeleteAll = false

    var body: some View {
        NavigationStack {
            Group {
                if vm.loading {
                    SkeletonList(count: 8) { SkeletonPersonRow(avatar: 36) }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                } else if vm.items.isEmpty {
                    EmptyStateView(
                        icon: "bell",
                        title: NSLocalizedString("notifications_empty", comment: ""),
                        message: L("Aquí te avisaremos de nuevos seguidores, solicitudes, mensajes y novedades de tus propuestas.")
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    let groups = vm.groups
                    List {
                        ForEach(Array(groups.enumerated()), id: \.element.id) { idx, g in
                            if idx == 0 || groups[idx - 1].section != g.section {
                                Text(notificationSectionTitle(g.section))
                                    .font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
                                    .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 4)
                                    .listRowInsets(EdgeInsets())
                                    .listRowBackground(Cumbre.bg)
                                    .listRowSeparator(.hidden)
                            }
                            NotificationRowView(
                                g: g, follow: vm.followState(g.lead),
                                onToggleFollow: { vm.toggleFollow(g.lead) },
                                onOpen: { target = vm.tap(g) }
                            )
                                .contentShape(Rectangle())
                                .onTapGesture { target = vm.tap(g) }
                                .listRowInsets(EdgeInsets())
                                .listRowBackground(Cumbre.bg)
                                .listRowSeparator(.hidden)
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) { vm.delete(g) } label: {
                                        Label("Borrar", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(NSLocalizedString("notifications_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $target) { t in
                switch t {
                case .school(let id): SchoolDetailLoaderView(schoolId: id)
                case .user(let uid): PublicProfileView(uid: uid)
                case .chat(let uid, let name): ChatView(otherUid: uid, otherName: name)
                // Pantalla unificada "Mis contribuciones" (PROPUESTAS ⇄ CONTRIBUCIONES).
                case .submissions: MyContributionsUnifiedView()
                case .followRequests: FollowRequestsView()
                case .feedPost(let id): FeedPostDetailView(postIdString: id)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        if vm.unread > 0 {
                            Button("Marcar todas leídas") { Task { await vm.markAll() } }
                        }
                        if !vm.items.isEmpty {
                            Button(NSLocalizedString("notifications_delete_all", comment: ""), role: .destructive) { showDeleteAll = true }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle").foregroundStyle(Cumbre.terra)
                    }
                }
            }
            .task { await vm.load() }
            .confirmationDialog("¿Borrar todas las notificaciones?", isPresented: $showDeleteAll, titleVisibility: .visible) {
                Button(NSLocalizedString("notifications_delete_all", comment: ""), role: .destructive) { vm.deleteAll() }
                Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
            }
            // Al cerrar la bandeja se consideran vistas → se limpia el badge.
            .onDisappear { Task { await vm.markAllSilent() } }
        }
    }
}

/// «hace 2 h», «Ayer», «hace 3 días» o «10 ago» (mismas reglas que `notificationAge` de Android):
/// la fecha cruda «2026-08-10» no se enseña nunca. Hora del servidor = UTC; «Ayer» por día natural.
func notificationDateLabel(_ createdAt: String, now: Date = Date()) -> String {
    let parser = DateFormatter()
    parser.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    parser.locale = Locale(identifier: "en_US_POSIX")
    parser.timeZone = TimeZone(identifier: "UTC")
    guard let created = parser.date(from: String(createdAt.prefix(19))) else { return String(createdAt.prefix(10)) }
    let minutes = max(Int(now.timeIntervalSince(created) / 60), 0)
    let cal = Calendar.current
    let dayGap = cal.dateComponents([.day], from: cal.startOfDay(for: created), to: cal.startOfDay(for: now)).day ?? 0
    if minutes < 1 { return L("ahora") }
    if minutes < 60 { return L("hace %@ min", minutes) }
    if dayGap <= 0 { return L("hace %@ h", minutes / 60) }
    if dayGap == 1 { return L("Ayer") }
    if dayGap < 7 { return L("hace %@ días", dayGap) }
    let out = DateFormatter()
    out.setLocalizedDateFormatFromTemplate("d MMM")
    return out.string(from: created)
}
