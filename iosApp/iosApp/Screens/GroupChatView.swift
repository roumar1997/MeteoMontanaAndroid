import SwiftUI
import Shared

// MARK: - Chat de grupo (por convId). Espejo de GroupChatScreen.kt.

@MainActor
final class GroupChatVM: ObservableObject {
    let convId: String
    @Published var groupName: String
    @Published var messages: [ChatServiceChatMessage] = []
    @Published var memberNames: [String: String] = [:]
    @Published var canWrite = true
    @Published var draft = ""
    @Published var loading = true
    /// true si la ventana en vivo llegó llena → hay mensajes anteriores por cargar.
    @Published var canLoadMore = false
    @Published var replyingTo: ChatServiceChatMessage?
    // Quedada asociada (si la conversación es de una quedada).
    @Published var meetupId: String?
    /** Texto listo para invitar al grupo (con el enlace del backend). */
    @Published var inviteText: String?
    @Published var schoolLat: Double?
    @Published var schoolLon: Double?
    @Published var gearSummary: String?
    @Published var members: [MeetupMember] = []
    @Published var discipline: String?
    @Published var myGearJson: String?
    @Published var muted: Bool
    /// La quedada (para el material, el día y el color de la cabecera).
    @Published var meetup: Meetup?
    /// Mi mochila (Mi material del perfil): cuenta como material disponible.
    @Published var myMochila: String?
    @Published var photos: [String: String] = [:]

    /// Material pedido y cubierto (con mi mochila sumada); vacío si no se pidió. Lógica en shared.
    var gearLines: [MeetupGearLine] {
        guard let meetup else { return [] }
        return MeetupGearStatus.shared.lines(meetup: MeetupGearStatus.shared.withMochilas(meetup: meetup, mochilas: [me: myMochila]))
    }

    private let chat = AppDependencies.shared.container.chatService
    private let chatPush = AppDependencies.shared.container.chatPushApi
    private let getProfile = AppDependencies.shared.container.getPublicProfile
    private let getMeetupByConv = AppDependencies.shared.container.getMeetupByConversation
    private var task: Task<Void, Never>?
    private var convTask: Task<Void, Never>?
    // Ventana en vivo creciente: empieza en 50 y +50 al pulsar "Mensajes anteriores".
    private var messageLimit = 50
    var me: String { AppDependencies.shared.authBridge.currentUid() ?? "" }

    private static let mutedKey = "muted_conv_ids"

    init(convId: String, groupName: String) {
        self.convId = convId
        self.groupName = groupName
        let mutedSet = Set(UserDefaults.standard.stringArray(forKey: Self.mutedKey) ?? [])
        self.muted = mutedSet.contains(convId)
    }

    func toggleMute() {
        muted.toggle()
        var s = Set(UserDefaults.standard.stringArray(forKey: Self.mutedKey) ?? [])
        if muted { s.insert(convId) } else { s.remove(convId) }
        UserDefaults.standard.set(Array(s), forKey: Self.mutedKey)
    }

    func start() {
        guard convTask == nil, let chat else { if chat == nil { loading = false }; return }
        // Quedada asociada (para el título → detalle y el botón "Cómo llegar").
        Task { [weak self] in
            guard let self else { return }
            if let m = try? await self.getMeetupByConv.execute(conversationId: self.convId) {
                let mems = Array(m.members)
                self.meetup = m
                self.meetupId = m.id
                self.myMochila = try? await AppDependencies.shared.container.getMyProfile.invoke().gearJson
                self.schoolLat = m.schoolLat?.doubleValue
                self.schoolLon = m.schoolLon?.doubleValue
                self.members = mems
                self.discipline = m.discipline
                self.myGearJson = mems.first(where: { $0.uid == self.me })?.gearJson
                let gear = totalGearSummary(mems)
                self.gearSummary = gear.isEmpty ? nil : gear
                // Enlace de invitación al grupo (los miembros pueden invitar).
                if let link = try? await AppDependencies.shared.container.meetupApi
                    .getInviteLink(id: m.id), !link.isEmpty {
                    self.inviteText = L("🧗 Te invito a la quedada *%@* en Cumbre\n", m.name)
                        + L("👉 Únete desde aquí:\n%@", link)
                }
            }
        }
        subscribeMessages()
        convTask = Task { [weak self] in
            for await convs in chat.observeMyConversations() {
                guard let self else { return }
                guard let conv = convs.first(where: { $0.id == self.convId }) else { continue }
                let parts = conv.participants.compactMap { $0 as? String }
                await self.resolveNames(parts)
                self.groupName = conv.name ?? self.groupName
                self.canWrite = parts.contains(self.me)
            }
        }
    }

    /// (Re)suscribe el listener de mensajes con la ventana actual.
    private func subscribeMessages() {
        guard let chat else { return }
        let cid = convId
        let lim = Int32(messageLimit)
        task?.cancel()
        task = Task { [weak self] in
            for await msgs in chat.observeMessages(convId: cid, limit: lim) {
                guard let self else { return }
                await self.resolveNames(msgs.map { $0.fromUid })
                self.messages = msgs
                self.canLoadMore = msgs.count >= self.messageLimit
                self.loading = false
                try? await chat.markRead(convId: cid)
            }
        }
    }

    /// Carga otra página de mensajes anteriores (aumenta la ventana en vivo).
    func loadOlder() {
        messageLimit += 50
        subscribeMessages()
    }

    private func resolveNames(_ uids: [String]) async {
        for uid in Set(uids) where uid != me && memberNames[uid] == nil {
            if let p = try? await getProfile.invoke(uid: uid) {
                memberNames[uid] = p.username ?? p.displayName ?? String(uid.prefix(6))
                if let url = p.photoUrl { photos[uid] = url }
            }
        }
    }

    func nameFor(_ uid: String) -> String {
        uid == me ? "Tú" : (memberNames[uid] ?? "")
    }

    func startReply(_ m: ChatServiceChatMessage) { replyingTo = m }
    func cancelReply() { replyingTo = nil }

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let chat else { return }
        draft = ""
        let reply = replyingTo
        replyingTo = nil
        Task { [weak self] in
            guard let self else { return }
            try? await chat.sendGroupMessage(convId: self.convId, text: text,
                                             replyToId: reply?.id, replyText: reply?.text,
                                             replyFromUid: reply?.fromUid)
            try? await self.chatPush.notifyGroup(convId: self.convId, preview: text)
        }
    }

    private let updateMyGearUC = AppDependencies.shared.container.updateMyGear

    func updateMyGear(_ gearJson: String) {
        guard let meetupId else { return }
        Task { [weak self] in
            guard let self else { return }
            if let updated = try? await self.updateMyGearUC.execute(meetupId: meetupId, gearJson: gearJson) {
                self.meetup = updated
                let mems = Array(updated.members)
                self.members = mems
                self.discipline = updated.discipline
                self.myGearJson = mems.first(where: { $0.uid == self.me })?.gearJson
                let gear = totalGearSummary(mems)
                self.gearSummary = gear.isEmpty ? nil : gear
            }
        }
    }

    func stop() { task?.cancel(); task = nil; convTask?.cancel(); convTask = nil }
}

struct GroupChatView: View {
    @StateObject private var vm: GroupChatVM
    @FocusState private var inputFocused: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var openMeetup = false
    @State private var openProfile: String?

    init(convId: String, groupName: String) {
        _vm = StateObject(wrappedValue: GroupChatVM(convId: convId, groupName: groupName))
    }

    @State private var gearExpanded = false
    @State private var showEditGear = false

    var body: some View {
        VStack(spacing: 0) {
            GroupChatHeader(vm: vm, onBack: { dismiss() }, onOpenMeetup: { openMeetup = true })
            GroupChatActions(vm: vm, onOpenMeetup: { openMeetup = true })
            GroupChatGearCard(vm: vm) { showEditGear = true }

            if vm.loading {
                SkeletonChat()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            if vm.canLoadMore {
                                Button { vm.loadOlder() } label: {
                                    Text("Mensajes anteriores")
                                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                }
                                .padding(.vertical, 6)
                            }
                            let dayStarts = chatDayStarts(vm.messages)
                            let msgs = vm.messages
                            let runs = chatRunPositions(msgs.map { $0.fromUid }) { i in i < msgs.count && dayStarts[msgs[i].id] != nil }
                            ForEach(Array(msgs.enumerated()), id: \.element.id) { i, m in
                                VStack(spacing: 8) {
                                    if let ms = dayStarts[m.id] { ChatDayDivider(millis: ms) }
                                    GroupMessageRow(m: m, me: vm.me, run: runs[i], photoUrl: vm.photos[m.fromUid],
                                                    onOpenProfile: { openProfile = m.fromUid },
                                                    senderName: vm.nameFor(m.fromUid),
                                                    nameFor: { vm.nameFor($0) },
                                                    onReply: { vm.startReply(m) })
                                }.id(m.id)
                            }
                        }
                        .padding(16)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .overlay { if vm.messages.isEmpty { ChatEmptyState() } }
                    // Solo baja al fondo cuando llega un mensaje NUEVO (cambia el
                    // último id), no al cargar antiguos (que añaden por arriba).
                    .onChange(of: vm.messages.last?.id) { _ in scrollToLast(proxy) }
                    .onChange(of: vm.loading) { _ in scrollToLast(proxy) }
                    .onChange(of: inputFocused) { f in if f { scrollToLast(proxy) } }
                    .onAppear { scrollToLast(proxy) }
                }
            }
            if let r = vm.replyingTo {
                let who = vm.nameFor(r.fromUid)
                HStack(spacing: 8) {
                    Rectangle().fill(Cumbre.terraFill).frame(width: 3, height: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(L("Respondiendo a %@", who)).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
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
            if vm.canWrite {
                inputBar
            } else {
                Text("Ya no eres miembro de este grupo")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity).padding(12)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $openMeetup) { if let mid = vm.meetupId { MeetupDetailView(meetupId: mid) } }
        .navigationDestination(isPresented: Binding(get: { openProfile != nil }, set: { if !$0 { openProfile = nil } })) {
            if let uid = openProfile { PublicProfileView(uid: uid) }
        }
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
        .sheet(isPresented: $showEditGear) {
            EditGearSheet(
                discipline: vm.discipline,
                currentGearJson: vm.myGearJson,
                gym: vm.meetup?.isGymMeetup ?? false, placeName: vm.meetup?.placeName ?? vm.meetup?.schoolName,
                members: vm.members, myUid: vm.me
            ) { json in
                vm.updateMyGear(json)
                showEditGear = false
            } onCancel: {
                showEditGear = false
            }
        }
    }

    private func scrollToLast(_ proxy: ScrollViewProxy) {
        guard let last = vm.messages.last else { return }
        // Margen mayor: el mensaje recién llegado por Firestore necesita un instante
        // para pintarse; con 0.05s a veces no se mostraba el último al abrir.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) }
        }
    }

    private var inputBar: some View {
        ChatInputPill(text: $vm.draft, focused: $inputFocused) { vm.send() }
    }
}

/// Burbuja de grupo: muestra el nombre del emisor (en mensajes de otros),
/// deslizar-para-responder y la cita si es respuesta.
private struct GroupMessageRow: View {
    let m: ChatServiceChatMessage
    let me: String
    let run: ChatRunPos
    let photoUrl: String?
    let onOpenProfile: () -> Void
    let senderName: String
    let nameFor: (String) -> String
    let onReply: () -> Void

    @State private var dragX: CGFloat = 0
    @State private var triggered = false

    var body: some View {
        let mine = m.fromUid == me
        let time = chatClock(m.createdAtMillis?.int64Value ?? -1)
        return ZStack(alignment: .leading) {
            Image(systemName: "arrowshape.turn.up.left.fill")
                .font(.system(size: 14)).foregroundStyle(Cumbre.terra)
                .opacity(Double(min(dragX / 56, 1))).padding(.leading, 8)
            HStack(alignment: .bottom, spacing: 6) {
                if mine { Spacer(minLength: 40) } else {
                    ChatSenderAvatarSlot(show: run.last, photoUrl: photoUrl, uid: m.fromUid, onTap: onOpenProfile)
                }
                VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
                    if !mine && !senderName.isEmpty && run.first {
                        Text(senderName).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                    }
                    if let rid = m.replyToId, let rtext = m.replyText, !rid.isEmpty {
                        let who = nameFor(m.replyFromUid ?? "")
                        VStack(alignment: .leading, spacing: 1) {
                            if !who.isEmpty {
                                Text(who).font(Cumbre.mono(9, .bold))
                                    .foregroundStyle(mine ? .white.opacity(0.9) : Cumbre.ink2)
                            }
                            Text(rtext).font(.system(size: 12))
                                .foregroundStyle(mine ? .white.opacity(0.85) : Cumbre.ink2).lineLimit(1)
                        }
                        .padding(.horizontal, 6).padding(.vertical, 4)
                        .background((mine ? Color.white : Cumbre.ink).opacity(0.12))
                        .overlay(Rectangle().frame(width: 2)
                            .foregroundStyle(mine ? Color.white.opacity(0.6) : Cumbre.terra), alignment: .leading)
                    }
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(m.text)
                            .font(.system(size: 15))
                            .foregroundStyle(mine ? .white : Cumbre.ink)
                        if !time.isEmpty {
                            Text(time).font(Cumbre.mono(9))
                                .foregroundStyle(mine ? Color.white.opacity(0.7) : Cumbre.ink3)
                        }
                    }
                    .chatBubble(mine: mine)
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

