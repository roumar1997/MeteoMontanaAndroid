import SwiftUI
import Shared

/// Id del chat de un rocódromo dentro de `school_chats` (mismas reglas de Firestore que las escuelas).
func gymChatId(_ gymId: Int64) -> String { "gym-\(gymId)" }

/// Chat de un rocódromo: el mismo chat abierto que el de escuela con la clave `gym-{id}`. Solo escribe quien ha
/// marcado «Aquí». Espejo de `GymChatScreen.kt`.
@MainActor
final class GymChatViewModel: ObservableObject {
    @Published var messages: [SchoolChatServiceMessage] = []
    @Published var text = ""
    @Published var sending = false
    @Published var people: [SchoolPresence] = []
    /// false hasta la primera respuesta de presencia: no se enseña el candado antes de saber si ya estás «Aquí».
    @Published var presenceLoaded = false
    @Published var iAmHere = false
    @Published var presenceBusy = false
    @Published private var names: [String: String] = [:]
    @Published private var photos: [String: String] = [:]

    let gymId: Int64
    let me: String
    private let chat = AppDependencies.shared.container.schoolChatService
    private let getProfile = AppDependencies.shared.container.getPublicProfile
    private let gyms = AppDependencies.shared.container.gyms
    private var task: Task<Void, Never>?

    init(gymId: Int64) {
        self.gymId = gymId
        me = AppDependencies.shared.authBridge.currentUid() ?? ""
    }

    func start() {
        guard let chat, task == nil else { return }
        Task { await loadPresence() }
        let id = gymChatId(gymId)
        task = Task { [weak self] in
            for await msgs in chat.observeMessages(schoolId: id, limit: 100) {
                guard let self else { return }
                await self.resolve(msgs.map { $0.fromUid })
                self.messages = msgs
            }
        }
    }

    func stop() { task?.cancel(); task = nil }

    func loadPresence() async {
        if let list = try? await gyms.getGymPresence.execute(gymId: gymId) {
            people = list
            iAmHere = list.contains { $0.uid == me }
            presenceLoaded = true
        }
    }

    func markHere() {
        guard !presenceBusy else { return }
        presenceBusy = true
        Task {
            let ok = (try? await gyms.markGymPresence.execute(gymId: gymId)) != nil
            presenceBusy = false
            if ok { iAmHere = true }
            await loadPresence()
        }
    }

    private func resolve(_ uids: [String]) async {
        for uid in Set(uids) where uid != me && names[uid] == nil {
            if let p = try? await getProfile.invoke(uid: uid) {
                names[uid] = p.username ?? p.displayName ?? String(uid.prefix(6))
                if let url = p.photoUrl { photos[uid] = url }
            }
        }
    }

    func photoFor(_ uid: String) -> String? { photos[uid] }
    func nameFor(_ uid: String) -> String { uid == me ? "Tú" : (names[uid] ?? String(uid.prefix(6))) }

    func send() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !sending, iAmHere, let chat else { return }
        sending = true
        text = ""
        let id = gymChatId(gymId)
        Task {
            defer { sending = false }
            try? await chat.sendMessage(schoolId: id, text: String(trimmed.prefix(500)))
        }
    }
}

struct GymChatTarget: Identifiable, Hashable {
    let uid: String
    let name: String
    var id: String { uid }
}

struct GymChatView: View {
    let gymName: String
    @StateObject private var vm: GymChatViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var privacyOpen = false
    @State private var openChat: GymChatTarget?
    @State private var openProfile: String?

    init(gymId: Int64, gymName: String) {
        self.gymName = gymName
        _vm = StateObject(wrappedValue: GymChatViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            ChatTopBar(title: gymName, onBack: { dismiss() }, avatar: {
                ChatInitialAvatar(name: gymName, color: Cumbre.ok)
            }, subtitle: {
                HStack(spacing: 5) {
                    if !vm.people.isEmpty { Circle().fill(Cumbre.ok).frame(width: 7, height: 7) }
                    ChatSubtitle(text: vm.people.isEmpty ? L("school_chat_screen_v2_se_borra_solo_pasados_unos")
                                 : L("gym_chat_here_now", vm.people.count))
                }
            }, actions: { EmptyView() })
            messagesList
            Divider().overlay(Cumbre.rule)
            if vm.presenceLoaded && !vm.iAmHere {
                ChatLockedBar {
                    Button { privacyOpen = true } label: {
                        Text(L("gym_here_top").uppercased()).font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 9).background(Cumbre.terraFill, in: Capsule())
                    }
                    .buttonStyle(.plain).disabled(vm.presenceBusy)
                }
            } else {
                ChatInputPill(text: $vm.text, enabled: !vm.sending, placeholder: L("school_chat_screen_v2_escribe_algo")) { vm.send() }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
        .sheet(isPresented: $privacyOpen) { PresencePrivacySheet { privacyOpen = false; vm.markHere() } }
        .navigationDestination(item: $openChat) { t in ChatView(otherUid: t.uid, otherName: t.name) }
        .navigationDestination(isPresented: Binding(get: { openProfile != nil }, set: { if !$0 { openProfile = nil } })) {
            if let uid = openProfile { PublicProfileView(uid: uid) }
        }
    }

    private var messagesList: some View {
        let msgs = vm.messages
        let dayStarts = chatDayStartIds(msgs.map { ($0.id, $0.createdAtMillis?.int64Value) })
        let runs = chatRunPositions(msgs.map { $0.fromUid }) { i in i < msgs.count && dayStarts[msgs[i].id] != nil }
        let present = Set(vm.people.map { $0.uid })
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(msgs.enumerated()), id: \.element.id) { i, m in
                        if let day = dayStarts[m.id] { ChatDayDivider(millis: day) }
                        OpenChatBubble(text: m.text, millis: m.createdAtMillis?.int64Value, mine: m.fromUid == vm.me,
                                       name: vm.nameFor(m.fromUid), present: present.contains(m.fromUid), run: runs[i],
                                       photoUrl: vm.photoFor(m.fromUid), uid: m.fromUid,
                                       onOpenProfile: { openProfile = m.fromUid },
                                       onTap: { openChat = GymChatTarget(uid: m.fromUid, name: vm.nameFor(m.fromUid)) })
                        .id(m.id)
                    }
                }
                .padding(16)
            }
            .overlay { if msgs.isEmpty { ChatEmptyState() } }
            .onChange(of: msgs.count) { _, _ in
                if let last = msgs.last?.id { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
            }
        }
        .frame(maxHeight: .infinity)
    }
}
