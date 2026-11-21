import SwiftUI
import Shared

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
                Text(L("ELIGE MIEMBROS (%@)", vm.selected.count))
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
                                AvatarCircle(url: p.photoUrl, size: 40, uid: p.uid)
                                Text(p.displayName ?? p.username ?? L("Usuario"))
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
                    .searchable(text: $query, prompt: L("Buscar contacto"))
                }
                Button { vm.create(onCreated: onCreated) } label: {
                    Group {
                        if vm.creating { ProgressView().tint(.white) } else {
                            Text("CREAR GRUPO").font(Cumbre.mono(13, .bold)).foregroundStyle(.white)
                        }
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
