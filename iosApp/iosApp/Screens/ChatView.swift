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
    /// Mensaje al que estoy respondiendo (cita), o nil.
    @Published var replyingTo: ChatServiceChatMessage?

    private let chat = AppDependencies.shared.container.chatService
    private let chatPush = AppDependencies.shared.container.chatPushApi
    private var conversationEnsured = false
    private var task: Task<Void, Never>?
    private var convTask: Task<Void, Never>?
    private var rawMessages: [ChatServiceChatMessage] = []
    // Eco optimista: mensajes recién enviados, mostrados YA (estilo WhatsApp) sin
    // esperar al listener de Firestore. Se reconcilian (quitan) cuando llega el
    // mensaje real del servidor con el mismo contenido.
    private var pendingOutgoing: [ChatServiceChatMessage] = []
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
        let server = clearedAt <= 0 ? rawMessages
            : rawMessages.filter { ($0.createdAtMillis?.int64Value ?? Int64.max) > clearedAt }
        // Un pendiente se descarta cuando el servidor ya tiene un mensaje mío con el
        // mismo texto/cita (evita duplicar al reconciliar).
        let stillPending = pendingOutgoing.filter { p in
            !server.contains { $0.fromUid == p.fromUid && $0.text == p.text && $0.replyToId == p.replyToId }
        }
        if stillPending.count != pendingOutgoing.count { pendingOutgoing = stillPending }
        messages = (server + stillPending).sorted {
            ($0.createdAtMillis?.int64Value ?? Int64.max) < ($1.createdAtMillis?.int64Value ?? Int64.max)
        }
    }

    func startReply(_ m: ChatServiceChatMessage) { replyingTo = m }
    func cancelReply() { replyingTo = nil }

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let chat else { return }
        draft = ""
        let reply = replyingTo
        replyingTo = nil
        // Eco optimista: muestra el mensaje YA (estilo WhatsApp) antes de tocar la
        // red. Se reconcilia cuando llega el mensaje real del servidor.
        let optimistic = ChatServiceChatMessage(
            id: "pending_\(UUID().uuidString)",
            fromUid: me,
            text: text,
            createdAtMillis: KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)),
            replyToId: reply?.id, replyText: reply?.text, replyFromUid: reply?.fromUid)
        pendingOutgoing.append(optimistic)
        recompute()
        Task { [weak self] in
            guard let self else { return }
            // El backend crea el documento de conversación (los clientes no pueden,
            // por las reglas de Firestore) y autoriza según el modelo de privacidad.
            // Si no está permitido escribir, lanza (p.ej. 403) y abortamos.
            if !self.conversationEnsured {
                if !self.rawMessages.isEmpty {
                    // La conversación YA existe (hay mensajes EN EL SERVIDOR): no hace
                    // falta que el backend la cree. Saltamos startConversation y
                    // escribimos directo en Firestore, que encola el mensaje offline y
                    // lo entrega al reconectar. Así enviar sin red ya no aborta.
                    self.conversationEnsured = true
                } else {
                    // Conversación nueva: el backend debe crearla y autorizar
                    // primero (las reglas de Firestore no dejan crearla al cliente).
                    // Offline esto falla → quitamos el eco y abortamos (no se puede
                    // iniciar una conversación nueva sin red).
                    do { try await self.chatPush.startConversation(toUid: self.otherUid) }
                    catch {
                        self.pendingOutgoing.removeAll { $0.id == optimistic.id }
                        self.recompute()
                        return
                    }
                    self.conversationEnsured = true
                }
            }
            try? await chat.sendMessage(otherUid: self.otherUid, text: text,
                                        replyToId: reply?.id, replyText: reply?.text,
                                        replyFromUid: reply?.fromUid)
            // Dispara la push del receptor (igual que Android).
            try? await self.chatPush.notifyMessage(toUid: self.otherUid, preview: text)
        }
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
                                MessageRow(m: m, me: vm.me, otherName: vm.otherName,
                                           onReply: { vm.startReply(m) }).id(m.id)
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
            replyBar
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
        // Margen mayor: al abrir desde una notificación el mensaje llega por el
        // listener de Firestore un instante después; con 0.05s a veces no daba
        // tiempo a pintarlo y el último quedaba sin mostrarse.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) }
        }
    }

    /// Cita del mensaje al que respondo (estilo WhatsApp), sobre el input.
    @ViewBuilder private var replyBar: some View {
        if let r = vm.replyingTo {
            let who = r.fromUid == vm.me ? "Tú" : vm.otherName
            HStack(spacing: 8) {
                Rectangle().fill(Cumbre.terra).frame(width: 3, height: 34)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Respondiendo a \(who)").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                    Text(r.text).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                }
                Spacer()
                Button { vm.cancelReply() } label: {
                    Image(systemName: "xmark.circle.fill").font(.system(size: 20)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(Cumbre.paper)
            .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .top)
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

/// Burbuja de un mensaje con deslizar-a-la-derecha-para-responder (estilo
/// WhatsApp) y, si el mensaje es respuesta, la cita del mensaje citado.
private struct MessageRow: View {
    let m: ChatServiceChatMessage
    let me: String
    let otherName: String
    let onReply: () -> Void

    @State private var dragX: CGFloat = 0
    @State private var triggered = false

    var body: some View {
        let mine = m.fromUid == me
        let time = chatTime(m.createdAtMillis?.int64Value ?? -1)
        return ZStack(alignment: .leading) {
            Image(systemName: "arrowshape.turn.up.left.fill")
                .font(.system(size: 14)).foregroundStyle(Cumbre.terra)
                .opacity(Double(min(dragX / 56, 1)))
                .padding(.leading, 8)
            HStack {
                if mine { Spacer(minLength: 40) }
                VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
                    if let rid = m.replyToId, let rtext = m.replyText, !rid.isEmpty {
                        let who = m.replyFromUid == me ? "Tú" : otherName
                        VStack(alignment: .leading, spacing: 1) {
                            Text(who).font(Cumbre.mono(9, .bold))
                                .foregroundStyle(mine ? .white.opacity(0.9) : Cumbre.ink2)
                            Text(rtext).font(.system(size: 12))
                                .foregroundStyle(mine ? .white.opacity(0.85) : Cumbre.ink2).lineLimit(1)
                        }
                        .padding(.horizontal, 6).padding(.vertical, 4)
                        .background((mine ? Color.white : Cumbre.ink).opacity(0.12))
                        .overlay(Rectangle().frame(width: 2)
                            .foregroundStyle(mine ? Color.white.opacity(0.6) : Cumbre.terra), alignment: .leading)
                    }
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
            .offset(x: dragX)
            .gesture(
                DragGesture(minimumDistance: 12)
                    .onChanged { v in
                        let t = max(0, min(v.translation.width, 80))
                        dragX = t
                        if t < 56 { triggered = false }
                    }
                    .onEnded { _ in
                        if dragX >= 56 && !triggered { triggered = true; onReply() }
                        withAnimation(.easeOut(duration: 0.2)) { dragX = 0 }
                    }
            )
        }
    }
}
