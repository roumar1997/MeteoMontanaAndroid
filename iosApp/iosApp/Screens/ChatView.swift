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
    private var convTask: Task<Void, Never>?
    private var rawMessages: [ChatServiceChatMessage] = []
    private var clearedAt: Int64 = 0   // mi cleared_<me> en millis (0 = no borrada)
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
                self.rawMessages = msgs
                self.recompute()
                self.loading = false
                try? await chat.markRead(convId: cid)
            }
        }
        // Observa mis conversaciones para conocer mi cleared_<me> y ocultar el
        // historial anterior a un "borrado para mí".
        convTask = Task { [weak self] in
            for await convs in chat.observeMyConversations() {
                guard let self else { return }
                self.clearedAt = convs.first { $0.id == cid }?.clearedAtMillis?.int64Value ?? 0
                self.recompute()
            }
        }
    }

    private func recompute() {
        messages = clearedAt <= 0 ? rawMessages
            : rawMessages.filter { ($0.createdAtMillis?.int64Value ?? Int64.max) > clearedAt }
    }

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let chat else { return }
        draft = ""
        Task { try? await chat.sendMessage(otherUid: otherUid, text: text) }
    }

    func stop() { task?.cancel(); task = nil; convTask?.cancel(); convTask = nil }
}

struct ChatView: View {
    @StateObject private var vm: ChatVM
    @FocusState private var inputFocused: Bool

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
                    .scrollDismissesKeyboard(.interactively)
                    // Baja al último mensaje cuando: llegan/cambian mensajes,
                    // termina la carga, o aparece el teclado (para que no tape
                    // los últimos mensajes ni lo que te acaban de escribir).
                    .onChange(of: vm.messages.count) { _ in scrollToLast(proxy) }
                    .onChange(of: vm.loading) { _ in scrollToLast(proxy) }
                    .onChange(of: inputFocused) { focused in if focused { scrollToLast(proxy) } }
                    .onAppear { scrollToLast(proxy) }
                }
            }
            inputBar
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // Nombre del chat → abre el perfil del otro usuario.
            ToolbarItem(placement: .principal) {
                NavigationLink(destination: PublicProfileView(uid: vm.otherUid)) {
                    Text(vm.otherName.isEmpty ? "Chat" : vm.otherName)
                        .font(Cumbre.serif(17, .semibold)).foregroundStyle(Cumbre.ink)
                }
            }
        }
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }

    /// Lleva el scroll al último mensaje. Pequeño delay para esperar al layout
    /// del teclado / a que la lista pinte el nuevo mensaje.
    private func scrollToLast(_ proxy: ScrollViewProxy) {
        guard let last = vm.messages.last else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) }
        }
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
                .focused($inputFocused)
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
