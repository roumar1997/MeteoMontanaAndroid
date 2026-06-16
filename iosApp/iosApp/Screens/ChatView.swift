import SwiftUI
import Shared

/// Conversación 1-a-1. Observa los mensajes (Flow de Firestore vía el bridge) y
/// permite enviar. Espejo de ChatScreen.kt.
@MainActor
final class ChatVM: ObservableObject {
    let otherUid: String
    let otherName: String
    @Published var messages: [ChatServiceChatMessage] = []
    @Published var draft = ""
    @Published var loading = true

    private let chat = AppDependencies.shared.container.chatService
    private var task: Task<Void, Never>?
    var me: String { AppDependencies.shared.authBridge.currentUid() ?? "" }
    private var convId: String { chat?.convIdFor(uidA: me, uidB: otherUid) ?? "" }

    init(otherUid: String, otherName: String) {
        self.otherUid = otherUid
        self.otherName = otherName
    }

    func start() {
        guard task == nil, let chat else { loading = false; return }
        let cid = convId
        task = Task { [weak self] in
            for await msgs in chat.observeMessages(convId: cid) {
                guard let self else { return }
                self.messages = msgs
                self.loading = false
                try? await chat.markRead(convId: cid)
            }
        }
    }

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let chat else { return }
        draft = ""
        Task { try? await chat.sendMessage(otherUid: otherUid, text: text) }
    }

    func stop() { task?.cancel(); task = nil }
}

struct ChatView: View {
    @StateObject private var vm: ChatVM

    init(otherUid: String, otherName: String) {
        _vm = StateObject(wrappedValue: ChatVM(otherUid: otherUid, otherName: otherName))
    }

    var body: some View {
        VStack(spacing: 0) {
            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(vm.messages, id: \.id) { m in
                                bubble(m).id(m.id)
                            }
                        }
                        .padding(16)
                    }
                    .onChange(of: vm.messages.count) { _ in
                        if let last = vm.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                    }
                }
            }
            inputBar
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(vm.otherName.isEmpty ? "Chat" : vm.otherName)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }

    private func bubble(_ m: ChatServiceChatMessage) -> some View {
        let mine = m.fromUid == vm.me
        let time = chatTime(m.createdAtMillis?.int64Value ?? -1)
        return HStack {
            if mine { Spacer(minLength: 40) }
            VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
                Text(m.text)
                    .font(.system(size: 15))
                    .foregroundStyle(mine ? .white : Cumbre.ink)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(mine ? Cumbre.terra : Cumbre.paper)
                    .overlay(Rectangle().stroke(mine ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                if !time.isEmpty {
                    Text(time).font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                }
            }
            if !mine { Spacer(minLength: 40) }
        }
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("Mensaje…", text: $vm.draft, axis: .vertical)
                .lineLimit(1...4).font(.system(size: 15))
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            Button { vm.send() } label: {
                Image(systemName: "arrow.up.circle.fill").font(.system(size: 30)).foregroundStyle(Cumbre.terra)
            }
            .disabled(vm.draft.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(12)
        .background(Cumbre.bg)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .top)
    }
}
