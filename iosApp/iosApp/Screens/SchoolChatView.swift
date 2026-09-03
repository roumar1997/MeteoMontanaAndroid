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
    @Published var presentNow: Set<String> = []
    @Published var text = ""
    @Published var sending = false

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
                presentNow = Set(active.map { $0.uid })
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

struct SchoolChatView: View {
    let schoolId: String
    let schoolName: String
    @StateObject private var vm: SchoolChatViewModel
    @Environment(\.dismiss) private var dismiss

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
            if !vm.presentNow.isEmpty {
                HStack(spacing: 6) {
                    Circle().fill(Cumbre.ok).frame(width: 6, height: 6)
                    Text("\(vm.presentNow.count) aquí ahora · toca un mensaje suyo para hablar en privado")
                        .font(.system(size: 10.5, design: .monospaced))
                        .foregroundStyle(Cumbre.ink2)
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(Cumbre.paper2)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
    }

    private func bubble(_ m: SchoolChatServiceMessage) -> some View {
        let mine = m.fromUid == vm.me
        return VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
            if !mine {
                HStack(spacing: 6) {
                    Text(vm.nameFor(m.fromUid))
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(Cumbre.terra)
                    if vm.presentNow.contains(m.fromUid) {
                        Text("AQUÍ AHORA")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundStyle(Cumbre.ok)
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(Cumbre.ok.opacity(0.15))
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
