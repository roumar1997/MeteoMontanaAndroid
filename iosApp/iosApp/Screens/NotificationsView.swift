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

    init(
        getMyNotifications: GetMyNotificationsUseCase = AppDependencies.shared.container.getMyNotifications,
        markRead: MarkNotificationReadUseCase = AppDependencies.shared.container.markNotificationRead,
        markAllRead: MarkAllNotificationsReadUseCase = AppDependencies.shared.container.markAllNotificationsRead,
        deleteOne: DeleteNotificationUseCase = AppDependencies.shared.container.deleteNotification,
        deleteAllUC: DeleteAllNotificationsUseCase = AppDependencies.shared.container.deleteAllNotifications
    ) {
        self.getMyNotifications = getMyNotifications
        self.markRead = markRead
        self.markAllRead = markAllRead
        self.deleteOne = deleteOne
        self.deleteAllUC = deleteAllUC
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
    func tap(_ n: Shared.Notification) -> NotifTarget? {
        if n.readAt == nil {
            Task { try? await markRead.invoke(id: n.id); await load() }
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
    var id: String {
        switch self {
        case .school(let s): return "school-\(s)"
        case .user(let u): return "user-\(u)"
        case .chat(let u, _): return "chat-\(u)"
        case .submissions: return "submissions"
        case .followRequests: return "follow-requests"
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
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.items.isEmpty {
                    EmptyStateView(
                        icon: "bell",
                        title: "Sin notificaciones",
                        message: "Aquí te avisaremos de nuevos seguidores, solicitudes, mensajes y novedades de tus propuestas."
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(vm.items, id: \.id) { n in
                            NotificationRow(n: n)
                                .contentShape(Rectangle())
                                .onTapGesture { target = vm.tap(n) }
                                .listRowInsets(EdgeInsets())
                                .listRowBackground(Cumbre.bg)
                                .listRowSeparatorTint(Cumbre.rule)
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) { vm.delete(n) } label: {
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
            .navigationTitle("Notificaciones")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $target) { t in
                switch t {
                case .school(let id): SchoolDetailLoaderView(schoolId: id)
                case .user(let uid): PublicProfileView(uid: uid)
                case .chat(let uid, let name): ChatView(otherUid: uid, otherName: name)
                case .submissions: MySubmissionsView()
                case .followRequests: FollowRequestsView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        if vm.unread > 0 {
                            Button("Marcar todas leídas") { Task { await vm.markAll() } }
                        }
                        if !vm.items.isEmpty {
                            Button("Borrar todas", role: .destructive) { showDeleteAll = true }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle").foregroundStyle(Cumbre.terra)
                    }
                }
            }
            .task { await vm.load() }
            .confirmationDialog("¿Borrar todas las notificaciones?", isPresented: $showDeleteAll, titleVisibility: .visible) {
                Button("Borrar todas", role: .destructive) { vm.deleteAll() }
                Button("Cancelar", role: .cancel) {}
            }
            // Al cerrar la bandeja se consideran vistas → se limpia el badge.
            .onDisappear { Task { await vm.markAllSilent() } }
        }
    }
}

private struct NotificationRow: View {
    let n: Shared.Notification
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(n.readAt == nil ? Cumbre.terra : Color.clear)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
            VStack(alignment: .leading, spacing: 3) {
                Text(n.title)
                    .font(.system(size: 15, weight: n.readAt == nil ? .semibold : .regular))
                    .foregroundStyle(Cumbre.ink)
                if let b = n.body, !b.isEmpty {
                    Text(b).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                }
                Text(String(n.createdAt.prefix(10)))
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}
