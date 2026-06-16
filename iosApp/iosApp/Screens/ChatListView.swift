import SwiftUI
import Shared

/// Lista de conversaciones (Flow de Firestore vía el bridge). Espejo de
/// ChatListScreen.kt. Resuelve el nombre del otro participante por su uid.
@MainActor
final class ChatListVM: ObservableObject {
    @Published var conversations: [ChatServiceConversation] = []
    @Published var names: [String: String] = [:]
    @Published var loading = true

    private let chat = AppDependencies.shared.container.chatService
    private var task: Task<Void, Never>?
    var me: String { AppDependencies.shared.authBridge.currentUid() ?? "" }

    func other(_ c: ChatServiceConversation) -> String {
        c.participants.compactMap { $0 as? String }.first { $0 != me } ?? ""
    }

    func start() {
        guard task == nil, let chat else { loading = false; return }
        task = Task { [weak self] in
            for await convs in chat.observeMyConversations() {
                guard let self else { return }
                self.conversations = convs
                self.loading = false
                await self.resolveNames(convs)
            }
        }
    }

    private func resolveNames(_ convs: [ChatServiceConversation]) async {
        let getProfile = AppDependencies.shared.container.getPublicProfile
        for c in convs {
            let uid = other(c)
            if uid.isEmpty || names[uid] != nil { continue }
            if let p = try? await getProfile.invoke(uid: uid) {
                names[uid] = p.displayName ?? p.username ?? uid
            }
        }
    }

    func stop() { task?.cancel(); task = nil }
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
                    let name = vm.names[uid] ?? "Usuario"
                    NavigationLink(destination: ChatView(otherUid: uid, otherName: name)) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(name).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                if c.unreadCount > 0 {
                                    Text("\(c.unreadCount)").font(Cumbre.mono(10, .bold)).foregroundStyle(.white)
                                        .padding(.horizontal, 6).padding(.vertical, 2)
                                        .background(Cumbre.terra).clipShape(Capsule())
                                }
                            }
                            if let lm = c.lastMessage, !lm.isEmpty {
                                Text(lm).font(.system(size: 13)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                            }
                        }
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
