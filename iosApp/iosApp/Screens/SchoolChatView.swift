import SwiftUI
import Shared

/// Chat ABIERTO de una escuela: cualquiera lo lee y escribe, sin necesidad de
/// "unirse" (a diferencia de Quedadas). Cabecera fusionada con quién está
/// presente ahora ("Estoy aquí") en vez de una lista aparte — así un
/// vistazo dice a la vez "cuántos hay" y "de qué se habla".
@MainActor
final class SchoolChatViewModel: ObservableObject {
    @Published var messages: [SchoolChatServiceMessage] = []
    @Published var memberNames: [String: String] = [:]
    // Lista completa (para "ver todos") — presentNow deriva los uids de aquí,
    // así no se piden los mismos datos dos veces.
    @Published var presentList: [SchoolPresence] = []
    @Published var text = ""
    @Published var sending = false
    @Published var photos: [String: String] = [:]

    var presentNow: Set<String> { Set(presentList.map { $0.uid }) }

    let schoolId: String
    let me: String

    private let chat = AppDependencies.shared.container.schoolChatService
    private let getProfile = AppDependencies.shared.container.getPublicProfile
    private let getPresence = AppDependencies.shared.container.getSchoolPresence
    private var task: Task<Void, Never>?

    init(schoolId: String) {
        self.schoolId = schoolId
        self.me = AppDependencies.shared.authBridge.currentUid() ?? ""
    }

    func start() {
        guard let chat, task == nil else { return }
        task = Task { [weak self] in
            for await msgs in chat.observeMessages(schoolId: self?.schoolId ?? "", limit: 100) {
                guard let self else { return }
                await self.resolveNames(msgs.map { $0.fromUid })
                self.messages = msgs
            }
        }
        Task { [weak self] in await self?.refreshPresence() }
    }

    // Deslizar hacia abajo pide otra vez quién hay — los mensajes ya llegan
    // en vivo por el listener de arriba, pero la presencia no tiene uno
    // propio (Álvaro, 2026-09-04: mejor a demanda que sondear sola).
    func refreshPresence() async {
        if let active = try? await getPresence.execute(schoolId: schoolId) {
            presentList = active
        }
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
        uid == me ? "Tú" : (memberNames[uid] ?? String(uid.prefix(6)))
    }

    func send() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !sending, let chat else { return }
        sending = true
        let toSend = trimmed
        text = ""
        Task {
            defer { sending = false }
            try? await chat.sendMessage(schoolId: schoolId, text: toSend)
        }
    }
}

private struct SchoolChatTarget: Identifiable, Hashable {
    let uid: String
    let name: String
    var id: String { uid }
}

struct SchoolChatView: View {
    let schoolId: String
    let schoolName: String
    @StateObject private var vm: SchoolChatViewModel
    /// Tu presencia en la escuela: desde aquí se marca "Estoy aquí".
    @StateObject private var presence: SchoolPresenceViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showAllPresent = false
    @State private var openChatFor: SchoolChatTarget?
    @State private var openProfile: String?

    init(schoolId: String, schoolName: String) {
        self.schoolId = schoolId
        self.schoolName = schoolName
        _vm = StateObject(wrappedValue: SchoolChatViewModel(schoolId: schoolId))
        _presence = StateObject(wrappedValue: SchoolPresenceViewModel(
            schoolId: schoolId, myUid: AppDependencies.shared.authBridge.currentUid()))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollViewReader { proxy in
                ScrollView {
                    let msgs = vm.messages
                    let dayStarts = chatDayStartIds(msgs.map { ($0.id, $0.createdAtMillis?.int64Value) })
                    let runs = chatRunPositions(msgs.map { $0.fromUid }) { i in i < msgs.count && dayStarts[msgs[i].id] != nil }
                    LazyVStack(spacing: 4) {
                        ForEach(Array(msgs.enumerated()), id: \.element.id) { i, m in
                            if let day = dayStarts[m.id] { ChatDayDivider(millis: day) }
                            OpenChatBubble(text: m.text, millis: m.createdAtMillis?.int64Value, mine: m.fromUid == vm.me,
                                           name: vm.nameFor(m.fromUid), present: vm.presentNow.contains(m.fromUid), run: runs[i],
                                           photoUrl: vm.photos[m.fromUid], uid: m.fromUid,
                                           onOpenProfile: { openProfile = m.fromUid },
                                           onTap: { openChatFor = SchoolChatTarget(uid: m.fromUid, name: vm.nameFor(m.fromUid)) })
                            .id(m.id)
                        }
                    }
                    .padding(16)
                }
                .onChange(of: vm.messages.count) { _ in
                    if let last = vm.messages.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
                .refreshable { await vm.refreshPresence() }
            }
            // Sin marcar «Aquí» no se escribe: la caja se sustituye por el botón grande que te marca.
            if notMarkedHere { markBar } else { inputBar }
        }
        .overlay {
            if notMarkedHere && vm.messages.isEmpty { markNotice } else if vm.messages.isEmpty { ChatEmptyState() }
        }
        .contentShape(Rectangle())
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { vm.start(); await presence.load() }
        // Al marcar o quitar "Estoy aquí", la cuenta de la cabecera se actualiza.
        .onChange(of: presence.iAmHere) { _, _ in Task { await vm.refreshPresence() } }
        .sheet(isPresented: $showAllPresent) { allPresentSheet }
        .navigationDestination(item: $openChatFor) { target in
            ChatView(otherUid: target.uid, otherName: target.name)
        }
        .navigationDestination(isPresented: Binding(get: { openProfile != nil }, set: { if !$0 { openProfile = nil } })) {
            if let uid = openProfile { PublicProfileView(uid: uid) }
        }
    }

    /// Sin marcar «Aquí» el chat y el recuento de gente no van contigo: aviso con el botón grande que te marca.
    private var notMarkedHere: Bool { presence.loaded && !presence.iAmHere }

    /// Sin mensajes y sin marcar: en el centro solo el icono y el aviso (el botón está abajo, en el sitio del input).
    private var markNotice: some View {
        VStack(spacing: 10) {
            Image(systemName: "mappin").font(.system(size: 40)).foregroundStyle(Cumbre.terra)
            Text(L("Pulsa «Aquí» para participar en el chat y ver cuánta gente hay en la escuela"))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity)
        .allowsHitTesting(false)
    }

    private var markBar: some View {
        ChatLockedBar { PresenceMarkButton(vm: presence, visibleWhenHere: false) }
    }

    /// Cabecera única de los chats (ChatTopBar): inicial de la escuela, «N aquí ahora» (toca para ver quién) o el aviso
    /// de que se borra solo, y «Aquí» a la derecha.
    private var header: some View {
        ChatTopBar(title: schoolName, onBack: { dismiss() },
                   onTitleClick: vm.presentList.isEmpty ? nil : { showAllPresent = true },
                   avatar: { ChatInitialAvatar(name: schoolName, color: Cumbre.terraFill) },
                   subtitle: {
                       HStack(spacing: 5) {
                           if !vm.presentList.isEmpty && !notMarkedHere {
                               Circle().fill(Cumbre.ok).frame(width: 7, height: 7)
                               ChatSubtitle(text: L("%@ aquí ahora", vm.presentList.count))
                           } else {
                               ChatSubtitle(text: L("school_chat_screen_v2_se_borra_solo_pasados_unos"))
                           }
                       }
                   },
                   actions: { PresenceMarkButton(vm: presence, visibleWhenHere: true).padding(.trailing, 6) })
    }

    private var allPresentSheet: some View {
        NavigationStack {
            List(vm.presentList, id: \.uid) { person in
                Button {
                    guard person.uid != vm.me else { return }
                    showAllPresent = false
                    openChatFor = SchoolChatTarget(uid: person.uid, name: person.displayName ?? person.username ?? L("Usuario"))
                } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: person.photoUrl, size: 36, uid: person.uid)
                        Text(person.uid == vm.me ? "Tú" : (person.displayName ?? person.username ?? L("Usuario")))
                            .font(.system(size: 15, design: .serif))
                            .foregroundStyle(Cumbre.ink)
                        Spacer()
                        if person.uid != vm.me {
                            Image(systemName: "bubble.left").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        }
                    }
                }
                .disabled(person.uid == vm.me)
            }
            .navigationTitle(L("%@ aquí ahora", vm.presentList.count))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") { showAllPresent = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var inputBar: some View {
        ChatInputPill(text: $vm.text, enabled: !vm.sending, placeholder: L("school_chat_screen_v2_escribe_algo")) { vm.send() }
    }
}
