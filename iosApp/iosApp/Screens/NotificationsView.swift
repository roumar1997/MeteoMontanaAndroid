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

    init(
        getMyNotifications: GetMyNotificationsUseCase = AppDependencies.shared.container.getMyNotifications,
        markRead: MarkNotificationReadUseCase = AppDependencies.shared.container.markNotificationRead,
        markAllRead: MarkAllNotificationsReadUseCase = AppDependencies.shared.container.markAllNotificationsRead
    ) {
        self.getMyNotifications = getMyNotifications
        self.markRead = markRead
        self.markAllRead = markAllRead
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

    /// Marca leída (si no lo estaba). Devuelve el destino de navegación según el
    /// targetType (espejo de NotificationsScreen.kt: "user"→perfil, "school"→detalle).
    func tap(_ n: Shared.Notification) -> NotifTarget? {
        if n.readAt == nil {
            Task { try? await markRead.invoke(id: n.id); await load() }
        }
        guard let tid = n.targetId, !tid.isEmpty else { return nil }
        switch n.targetType {
        case "user": return .user(tid)
        case "school", "school_detail": return .school(tid)
        default: return nil
        }
    }
}

/// Destino de navegación de una notificación.
enum NotifTarget: Identifiable {
    case school(String)
    case user(String)
    var id: String {
        switch self {
        case .school(let s): return "school-\(s)"
        case .user(let u): return "user-\(u)"
        }
    }
}

/// Bandeja de notificaciones — espejo de NotificationsScreen.kt. Lista con
/// título, cuerpo, fecha y punto de "no leída"; botón "Marcar todas leídas".
struct NotificationsView: View {
    @StateObject private var vm = NotificationsViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var target: NotifTarget?

    var body: some View {
        NavigationStack {
            Group {
                if vm.loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.items.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "bell.slash").font(.system(size: 40)).foregroundStyle(Cumbre.ink3)
                        Text("Sin notificaciones").font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(vm.items, id: \.id) { n in
                                NotificationRow(n: n).onTapGesture { target = vm.tap(n) }
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Notificaciones")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $target) { t in
                switch t {
                case .school(let id): SchoolDetailLoaderView(schoolId: id)
                case .user(let uid): PublicProfileView(uid: uid)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if vm.unread > 0 {
                        Button("Marcar leídas") { Task { await vm.markAll() } }
                            .foregroundStyle(Cumbre.terra)
                    }
                }
            }
            .task { await vm.load() }
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
