import SwiftUI
import Shared

/// Lista de conversaciones (Flow de Firestore vía el bridge). Espejo de
/// ChatListScreen.kt. Resuelve el nombre del otro participante por su uid.
@MainActor
final class ChatListVM: ObservableObject {
    @Published var conversations: [ChatServiceConversation] = []
    @Published var profiles: [String: PublicProfile] = [:]
    @Published var loading = true

    private let chat = AppDependencies.shared.container.chatService
    private var task: Task<Void, Never>?
    var me: String { AppDependencies.shared.authBridge.currentUid() ?? "" }

    func other(_ c: ChatServiceConversation) -> String {
        c.participants.compactMap { $0 as? String }.first { $0 != me } ?? ""
    }
    func name(_ uid: String) -> String {
        profiles[uid].flatMap { $0.displayName ?? $0.username } ?? "Usuario"
    }

    func start() {
        guard task == nil, let chat else { loading = false; return }
        task = Task { [weak self] in
            for await convs in chat.observeMyConversations() {
                guard let self else { return }
                self.conversations = convs
                self.loading = false
                await self.resolveProfiles(convs)
            }
        }
    }

    private func resolveProfiles(_ convs: [ChatServiceConversation]) async {
        let getProfile = AppDependencies.shared.container.getPublicProfile
        for c in convs {
            let uid = other(c)
            if uid.isEmpty || profiles[uid] != nil { continue }
            if let p = try? await getProfile.invoke(uid: uid) { profiles[uid] = p }
        }
    }

    func stop() { task?.cancel(); task = nil }

    /// Swipe → borrar conversación (optimista: la quitamos ya de la lista).
    func delete(_ convId: String) {
        conversations.removeAll { $0.id == convId }
        guard let chat else { return }
        Task { try? await chat.deleteConversation(convId: convId) }
    }

    /// Swipe → marcar como no leída (vuelve a salir el badge).
    func markUnread(_ convId: String) {
        guard let chat else { return }
        Task { try? await chat.markUnread(convId: convId) }
    }
}

/// Hora de un mensaje/conversación (millis epoch): HH:mm si es hoy, si no dd/MM.
func chatTime(_ millis: Int64) -> String {
    guard millis > 0 else { return "" }
    let date = Date(timeIntervalSince1970: Double(millis) / 1000)
    let f = DateFormatter()
    f.dateFormat = Calendar.current.isDateInToday(date) ? "HH:mm" : "dd/MM"
    return f.string(from: date)
}

struct ChatListView: View {
    @StateObject private var vm = ChatListVM()

    var body: some View {
        Group {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if vm.conversations.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "bubble.left.and.bubble.right").font(.system(size: 36)).foregroundStyle(Cumbre.ink3)
                    Text("Aún no tienes conversaciones.").font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    Text("Escribe a alguien desde su perfil.").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
            } else {
                List(vm.conversations, id: \.id) { c in
                    let uid = vm.other(c)
                    let name = vm.name(uid)
                    NavigationLink(destination: ChatView(otherUid: uid, otherName: name)) {
                        HStack(spacing: 12) {
                            AvatarCircle(url: vm.profiles[uid]?.photoUrl, size: 44)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(name).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                    Spacer()
                                    Text(chatTime(c.lastAtMillis?.int64Value ?? -1))
                                        .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                                HStack {
                                    if let lm = c.lastMessage, !lm.isEmpty {
                                        Text(lm).font(.system(size: 13)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                                    }
                                    Spacer()
                                    if c.unreadCount > 0 {
                                        Text("\(c.unreadCount)").font(Cumbre.mono(10, .bold)).foregroundStyle(.white)
                                            .padding(.horizontal, 6).padding(.vertical, 2)
                                            .background(Cumbre.terra).clipShape(Capsule())
                                    }
                                }
                            }
                        }
                    }
                    // Swipe estilo WhatsApp: izquierda → borrar; derecha → no leído.
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button(role: .destructive) { vm.delete(c.id) } label: {
                            Label("Borrar", systemImage: "trash")
                        }
                    }
                    .swipeActions(edge: .leading) {
                        Button { vm.markUnread(c.id) } label: {
                            Label("No leído", systemImage: "envelope.badge")
                        }.tint(Cumbre.terra)
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mensajes")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }
}
