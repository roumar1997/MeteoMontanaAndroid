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
        Task { [weak self] in
            guard let self else { return }
            if let active = try? await getPresence.execute(schoolId: schoolId) {
                presentList = active
            }
        }
    }

    private func resolveNames(_ uids: [String]) async {
        for uid in Set(uids) where uid != me && memberNames[uid] == nil {
            if let p = try? await getProfile.invoke(uid: uid) {
                memberNames[uid] = p.username ?? p.displayName ?? String(uid.prefix(6))
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
    @Environment(\.dismiss) private var dismiss
    @State private var showAllPresent = false
    @State private var openChatFor: SchoolChatTarget?

    init(schoolId: String, schoolName: String) {
        self.schoolId = schoolId
        self.schoolName = schoolName
        _vm = StateObject(wrappedValue: SchoolChatViewModel(schoolId: schoolId))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(vm.messages, id: \.id) { m in
                            bubble(m)
                        }
                    }
                    .padding(14)
                }
                .onChange(of: vm.messages.count) { _ in
                    if let last = vm.messages.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
            }
            inputBar
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { vm.start() }
        .sheet(isPresented: $showAllPresent) { allPresentSheet }
        .navigationDestination(item: $openChatFor) { target in
            ChatView(otherUid: target.uid, otherName: target.name)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Cumbre.ink)
                }
                Text(schoolName)
                    .font(.system(size: 17, weight: .bold, design: .serif))
                    .foregroundStyle(Cumbre.ink)
                Spacer()
                Text("se borra solo pasados unos días")
                    .font(.system(size: 9, design: .monospaced))
                    .foregroundStyle(Cumbre.ink3)
            }
            if !vm.presentList.isEmpty {
                Button { showAllPresent = true } label: {
                    HStack(spacing: 6) {
                        Circle().fill(Cumbre.terraFill).frame(width: 7, height: 7)
                        Text("\(vm.presentList.count) aquí ahora")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundStyle(Cumbre.terra)
                        Text("· ver todos")
                            .font(.system(size: 10.5, design: .monospaced))
                            .foregroundStyle(Cumbre.ink2)
                    }
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(Cumbre.paper2)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
    }

    private var allPresentSheet: some View {
        NavigationStack {
            List(vm.presentList, id: \.uid) { person in
                Button {
                    guard person.uid != vm.me else { return }
                    showAllPresent = false
                    openChatFor = SchoolChatTarget(uid: person.uid, name: person.displayName ?? person.username ?? "Usuario")
                } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: person.photoUrl, size: 36)
                        Text(person.uid == vm.me ? "Tú" : (person.displayName ?? person.username ?? "Usuario"))
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
            .navigationTitle("\(vm.presentList.count) aquí ahora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") { showAllPresent = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func bubble(_ m: SchoolChatServiceMessage) -> some View {
        let mine = m.fromUid == vm.me
        let content = VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
            if !mine {
                HStack(spacing: 6) {
                    Text(vm.nameFor(m.fromUid))
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(Cumbre.terra)
                    if vm.presentNow.contains(m.fromUid) {
                        Text("AQUÍ AHORA")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Cumbre.terraFill)
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 2)
            }
            Text(m.text)
                .font(.system(size: 13.5))
                .foregroundStyle(Cumbre.ink)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(mine ? Cumbre.paper2 : Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(mine ? Color.clear : Cumbre.rule, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .frame(maxWidth: 260, alignment: mine ? .trailing : .leading)
        .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
        .id(m.id)

        // Mensaje de alguien que sigue presente: tocarlo abre chat 1-a-1
        // (la cabecera lo anuncia). El resto de mensajes no son pulsables.
        if !mine && vm.presentNow.contains(m.fromUid) {
            return AnyView(Button {
                openChatFor = SchoolChatTarget(uid: m.fromUid, name: vm.nameFor(m.fromUid))
            } label: { content }.buttonStyle(.plain))
        }
        return AnyView(content)
    }

    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("Escribe algo...", text: $vm.text)
                .font(.system(size: 13))
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                .clipShape(Capsule())
            Button {
                vm.send()
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(vm.text.trimmingCharacters(in: .whitespaces).isEmpty ? Cumbre.ink3 : Cumbre.terra)
            }
            .disabled(vm.text.trimmingCharacters(in: .whitespaces).isEmpty || vm.sending)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Cumbre.paper2)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .top)
    }
}
