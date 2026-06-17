import SwiftUI
import Shared

/// Lista de conversaciones (Flow de Firestore vía el bridge). Espejo de
/// ChatListScreen.kt. Resuelve el nombre del otro participante por su uid.
@MainActor
final class ChatListVM: ObservableObject {
    @Published var conversations: [ChatServiceConversation] = []
    @Published var profiles: [String: PublicProfile] = [:]
    @Published var loading = true
    @Published var contacts: [PublicProfile] = []   // seguidores ∪ seguidos (para nuevo chat)

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
                // Oculta las que "borré para mí" (cleared) sin mensajes posteriores.
                let visible = convs.filter { !chatIsHiddenForMe($0) }
                self.conversations = visible
                self.loading = false
                await self.resolveProfiles(visible)
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

    /// Carga los contactos a quienes puedo escribir: seguidores ∪ seguidos.
    func loadContacts() {
        guard contacts.isEmpty else { return }
        Task { [weak self] in
            guard let self else { return }
            let c = AppDependencies.shared.container
            let me = AppDependencies.shared.authBridge.currentUid() ?? ""
            let followers = (try? await c.getFollowers.invoke(uid: me)) ?? []
            let following = (try? await c.getFollowing.invoke(uid: me)) ?? []
            var seen = Set<String>()
            self.contacts = (followers + following)
                .filter { seen.insert($0.uid).inserted }
                .sorted { ($0.displayName ?? $0.username ?? "") < ($1.displayName ?? $1.username ?? "") }
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

/// "Borrada para mí" (cleared) y sin mensajes posteriores → no se muestra. Si
/// llega un mensaje después de cleared, vuelve a aparecer.
func chatIsHiddenForMe(_ c: ChatServiceConversation) -> Bool {
    guard let cleared = c.clearedAtMillis?.int64Value else { return false }
    guard let last = c.lastAtMillis?.int64Value else { return true }
    return last <= cleared
}

/// Hora de un mensaje/conversación (millis epoch): HH:mm si es hoy, si no dd/MM.
func chatTime(_ millis: Int64) -> String {
    guard millis > 0 else { return "" }
    let date = Date(timeIntervalSince1970: Double(millis) / 1000)
    let f = DateFormatter()
    f.dateFormat = Calendar.current.isDateInToday(date) ? "HH:mm" : "dd/MM"
    return f.string(from: date)
}

/// Destino de navegación al iniciar un chat nuevo desde el picker.
private struct ChatTarget: Identifiable, Hashable { let uid: String; let name: String; var id: String { uid } }

struct ChatListView: View {
    @StateObject private var vm = ChatListVM()
    @State private var showPicker = false
    @State private var newChatTarget: ChatTarget?

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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { vm.loadContacts(); showPicker = true } label: {
                    Image(systemName: "square.and.pencil").foregroundStyle(Cumbre.terra)
                }
            }
        }
        .sheet(isPresented: $showPicker) {
            NewChatView(contacts: vm.contacts) { uid, name in
                showPicker = false
                newChatTarget = ChatTarget(uid: uid, name: name)
            }
        }
        .navigationDestination(item: $newChatTarget) { t in
            ChatView(otherUid: t.uid, otherName: t.name)
        }
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }
}

/// Buscador para iniciar un chat: lista de seguidores/seguidos, filtrable.
private struct NewChatView: View {
    let contacts: [PublicProfile]
    let onPick: (String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var filtered: [PublicProfile] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return contacts }
        return contacts.filter {
            ($0.username ?? "").lowercased().contains(q) || ($0.displayName ?? "").lowercased().contains(q)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if contacts.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "person.2").font(.system(size: 32)).foregroundStyle(Cumbre.ink3)
                        Text("Sigue a alguien (o que te sigan) para poder escribirle.")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.center).padding(.horizontal, 32)
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(filtered, id: \.uid) { p in
                        Button {
                            onPick(p.uid, p.displayName ?? p.username ?? "Usuario")
                        } label: {
                            HStack(spacing: 12) {
                                AvatarCircle(url: p.photoUrl, size: 40)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(p.displayName ?? p.username ?? "Usuario")
                                        .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                    if let u = p.username, !u.isEmpty {
                                        Text("@\(u)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                                    }
                                }
                            }
                        }.buttonStyle(.plain)
                    }
                    .listStyle(.plain)
                    .searchable(text: $query, prompt: "Buscar entre seguidores/seguidos")
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Nuevo mensaje")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
    }
}
