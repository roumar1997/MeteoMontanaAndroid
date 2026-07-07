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
                self.meetupId = m.id
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
                    self.inviteText = "🧗 Te invito a la quedada *\(m.name)* en Cumbre\n"
                        + "👉 Únete desde aquí:\n\(link)"
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

    init(convId: String, groupName: String) {
        _vm = StateObject(wrappedValue: GroupChatVM(convId: convId, groupName: groupName))
    }

    @State private var gearExpanded = false
    @State private var showEditGear = false

    var body: some View {
        VStack(spacing: 0) {
            // Expandable gear banner
            if vm.meetupId != nil {
                let hasSomeGear = vm.gearSummary != nil
                VStack(spacing: 0) {
                    // Header row
                    HStack(spacing: 6) {
                        Image(systemName: "bag").font(.system(size: 13))
                            .foregroundStyle(Cumbre.terra)
                        if hasSomeGear {
                            Text(vm.gearSummary!)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(Cumbre.ink)
                                .lineLimit(1)
                        } else {
                            Text("Sin material indicado")
                                .font(.system(size: 13))
                                .foregroundStyle(Cumbre.ink.opacity(0.5))
                        }
                        Spacer()
                        if !hasSomeGear {
                            Button {
                                showEditGear = true
                            } label: {
                                Text("+ Anadir")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(Cumbre.terra)
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra, lineWidth: 1))
                            }
                        }
                        if hasSomeGear {
                            Image(systemName: gearExpanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 10))
                                .foregroundStyle(Cumbre.ink.opacity(0.4))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .contentShape(Rectangle())
                    .onTapGesture { gearExpanded.toggle() }

                    // Expanded content
                    if gearExpanded && hasSomeGear {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(vm.members, id: \.uid) { member in
                                let gear = parseGear(member.gearJson)
                                let summary = gear.isEmpty ? "" : gear.map { "\($0.value) \(gearLabel($0.key))" }.joined(separator: " \u{00B7} ")
                                HStack(spacing: 8) {
                                    MeetupAvatarCircle(url: member.photoUrl, size: 24)
                                    Text(member.displayName ?? member.username ?? String(member.uid.prefix(6)))
                                        .font(.system(size: 12, weight: .medium))
                                    Spacer()
                                    if !summary.isEmpty {
                                        Text(summary)
                                            .font(.system(size: 12))
                                            .foregroundColor(Cumbre.ink.opacity(0.6))
                                    } else {
                                        Text(NSLocalizedString("meetup_detail_no_gear", comment: ""))
                                            .font(.system(size: 12)).italic()
                                            .foregroundColor(Cumbre.ink.opacity(0.35))
                                    }
                                }
                            }
                            Button {
                                showEditGear = true
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "pencil").font(.system(size: 10))
                                    Text(NSLocalizedString("meetup_detail_edit_gear", comment: "")).font(.system(size: 12, weight: .bold))
                                }
                                .foregroundColor(Cumbre.terra)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 7)
                                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra, lineWidth: 1))
                            }
                            .padding(.top, 4)
                        }
                        .padding(.horizontal, 12).padding(.bottom, 8)
                    }

                    Divider()
                }
            }

            if vm.loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
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
                            ForEach(vm.messages, id: \.id) { m in
                                GroupMessageRow(m: m, me: vm.me,
                                                senderName: vm.nameFor(m.fromUid),
                                                nameFor: { vm.nameFor($0) },
                                                onReply: { vm.startReply(m) }).id(m.id)
                            }
                        }
                        .padding(16)
                    }
                    .scrollDismissesKeyboard(.interactively)
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
            if vm.canWrite {
                inputBar
            } else {
                Text("Ya no eres miembro de este grupo")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity).padding(12)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                if let mid = vm.meetupId {
                    NavigationLink(destination: MeetupDetailView(meetupId: mid)) {
                        VStack(spacing: 0) {
                            Text(vm.groupName).font(Cumbre.serif(15, .semibold)).foregroundStyle(Cumbre.ink)
                                .lineLimit(1).truncationMode(.tail)
                            Text("Ver detalles ›").font(Cumbre.mono(9)).foregroundStyle(Cumbre.terra)
                        }
                        .frame(maxWidth: 180)
                    }
                } else {
                    VStack(spacing: 0) {
                        Text(vm.groupName).font(Cumbre.serif(15, .semibold)).foregroundStyle(Cumbre.ink)
                            .lineLimit(1).truncationMode(.tail)
                        Text("\(vm.memberNames.count + 1) miembros").font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: 180)
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if let lat = vm.schoolLat, let lon = vm.schoolLon {
                    Button {
                        openDirections(lat: lat, lon: lon)
                    } label: {
                        Image(systemName: "location.fill").foregroundStyle(Cumbre.terra)
                    }
                }
                // Invitar al grupo: enlace que permite unirse sin relación de
                // follows (los "no mixto" siguen exigiendo género). Espejo Android.
                if let invite = vm.inviteText {
                    ShareLink(item: invite) {
                        Image(systemName: "person.badge.plus").foregroundStyle(Cumbre.terra)
                    }
                }
                Button {
                    vm.toggleMute()
                } label: {
                    Image(systemName: vm.muted ? "bell.slash" : "bell")
                        .foregroundStyle(vm.muted ? Cumbre.ink3 : Cumbre.terra)
                }
            }
        }
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
        .sheet(isPresented: $showEditGear) {
            EditGearSheet(
                discipline: vm.discipline,
                currentGearJson: vm.myGearJson
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

/// Burbuja de grupo: muestra el nombre del emisor (en mensajes de otros),
/// deslizar-para-responder y la cita si es respuesta.
private struct GroupMessageRow: View {
    let m: ChatServiceChatMessage
    let me: String
    let senderName: String
    let nameFor: (String) -> String
    let onReply: () -> Void

    @State private var dragX: CGFloat = 0
    @State private var triggered = false

    var body: some View {
        let mine = m.fromUid == me
        let time = chatTime(m.createdAtMillis?.int64Value ?? -1)
        return ZStack(alignment: .leading) {
            Image(systemName: "arrowshape.turn.up.left.fill")
                .font(.system(size: 14)).foregroundStyle(Cumbre.terra)
                .opacity(Double(min(dragX / 56, 1))).padding(.leading, 8)
            HStack {
                if mine { Spacer(minLength: 40) }
                VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
                    if !mine && !senderName.isEmpty {
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

// MARK: - Crear grupo (elegir miembros + nombre)

@MainActor
final class NewGroupVM: ObservableObject {
    @Published var contacts: [PublicProfile] = []
    @Published var selected: Set<String> = []
    @Published var name = ""
    @Published var creating = false

    private let chatPush = AppDependencies.shared.container.chatPushApi

    func load() {
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

    func toggle(_ uid: String) {
        if selected.contains(uid) { selected.remove(uid) } else { selected.insert(uid) }
    }

    func create(onCreated: @escaping (String, String) -> Void) {
        let n = name.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty, !selected.isEmpty, !creating else { return }
        creating = true
        Task { [weak self] in
            guard let self else { return }
            let convId = try? await self.chatPush.createGroup(name: n, memberUids: Array(self.selected))
            self.creating = false
            if let convId { onCreated(convId, n) }
        }
    }
}

struct NewGroupView: View {
    let onCreated: (String, String) -> Void
    @StateObject private var vm = NewGroupVM()
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    // Contactos visibles según la búsqueda (sobre @usuario y nombre).
    private var shownContacts: [PublicProfile] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return vm.contacts }
        return vm.contacts.filter {
            ($0.username ?? "").lowercased().contains(q) ||
            ($0.displayName ?? "").lowercased().contains(q)
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                TextField("Nombre del grupo", text: $vm.name)
                    .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    .padding(16)
                Text("ELIGE MIEMBROS (\(vm.selected.count))")
                    .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16)
                if vm.contacts.isEmpty {
                    Spacer()
                    Text("Sigue a alguien (o que te sigan) para añadir miembros.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                        .multilineTextAlignment(.center).padding(32)
                    Spacer()
                } else {
                    List(shownContacts, id: \.uid) { p in
                        Button { vm.toggle(p.uid) } label: {
                            HStack(spacing: 12) {
                                AvatarCircle(url: p.photoUrl, size: 40)
                                Text(p.displayName ?? p.username ?? "Usuario")
                                    .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                if vm.selected.contains(p.uid) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(Cumbre.terra)
                                }
                            }
                        }.buttonStyle(.plain)
                    }
                    .listStyle(.plain)
                    .searchable(text: $query, prompt: "Buscar contacto")
                }
                Button { vm.create(onCreated: onCreated) } label: {
                    Group {
                        if vm.creating { ProgressView().tint(.white) }
                        else { Text("CREAR GRUPO").font(Cumbre.mono(13, .bold)).foregroundStyle(.white) }
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(canCreate ? Cumbre.terra : Cumbre.ink3)
                }
                .disabled(!canCreate)
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(NSLocalizedString("chat_new_group", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
            .onAppear { vm.load() }
        }
    }

    private var canCreate: Bool {
        !vm.name.trimmingCharacters(in: .whitespaces).isEmpty && !vm.selected.isEmpty && !vm.creating
    }
}
