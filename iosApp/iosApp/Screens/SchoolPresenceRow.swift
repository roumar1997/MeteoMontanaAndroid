import SwiftUI
import Shared

/// "Estoy aquí": quién está presente en esta escuela ahora mismo, con un
/// botón para marcarte tú también. Reutiliza `ChatView` (chat 1-a-1 ya
/// existente) al tocar un avatar — no hay chat de grupo abierto todavía,
/// eso queda para una siguiente iteración (ver plan de "Estoy aquí" con Álvaro).
@MainActor
final class SchoolPresenceViewModel: ObservableObject {
    @Published var people: [SchoolPresence] = []
    @Published var iAmHere = false
    @Published var loading = false

    private let schoolId: String
    private let myUid: String?
    private let getPresence = AppDependencies.shared.container.getSchoolPresence
    private let markPresence = AppDependencies.shared.container.markSchoolPresence
    private let clearPresence = AppDependencies.shared.container.clearSchoolPresence

    init(schoolId: String, myUid: String?) {
        self.schoolId = schoolId
        self.myUid = myUid
    }

    func load() async {
        // Público: sin sesión también se ve quién hay, igual que el resto de la ficha.
        guard let list = try? await getPresence.execute(schoolId: schoolId) else { return }
        people = list
        iAmHere = myUid.map { uid in list.contains { $0.uid == uid } } ?? false
    }

    func toggle() {
        guard !loading else { return }
        loading = true
        Task {
            defer { loading = false }
            if iAmHere {
                try? await clearPresence.execute(schoolId: schoolId)
            } else {
                _ = try? await markPresence.execute(schoolId: schoolId)
            }
            await load()
        }
    }
}

struct SchoolPresenceRow: View {
    let schoolId: String
    let myUid: String?
    @StateObject private var vm: SchoolPresenceViewModel
    @State private var showPrivacyNote = false
    @State private var openChatFor: (uid: String, name: String)?

    init(schoolId: String, myUid: String?) {
        self.schoolId = schoolId
        self.myUid = myUid
        _vm = StateObject(wrappedValue: SchoolPresenceViewModel(schoolId: schoolId, myUid: myUid))
    }

    var body: some View {
        Group {
            if !vm.people.isEmpty || vm.iAmHere {
                content
            } else {
                // Nadie presente: solo el botón, sin fila vacía que ensucie la pantalla.
                markButton
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
        }
        .task { await vm.load() }
        .sheet(isPresented: $showPrivacyNote) {
            privacySheet
        }
        .background(
            NavigationLink(
                isActive: Binding(get: { openChatFor != nil }, set: { if !$0 { openChatFor = nil } }),
                destination: {
                    if let target = openChatFor {
                        ChatView(otherUid: target.uid, otherName: target.name)
                    } else { EmptyView() }
                },
                label: { EmptyView() }
            )
        )
    }

    private var content: some View {
        HStack(spacing: 8) {
            HStack(spacing: -7) {
                ForEach(vm.people.prefix(4), id: \.uid) { person in
                    Button {
                        guard person.uid != myUid else { return }
                        openChatFor = (person.uid, person.displayName ?? person.username ?? "Usuario")
                    } label: {
                        AvatarCircle(url: person.photoUrl, size: 22)
                            .overlay(Circle().stroke(Cumbre.paper, lineWidth: 2))
                    }
                }
            }
            Text(peopleLabel)
                .font(.system(size: 12.5, weight: .semibold, design: .serif))
                .foregroundStyle(Cumbre.ink)
            Spacer()
            markButton
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Cumbre.paper2)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
    }

    private var peopleLabel: String {
        vm.people.isEmpty ? "" : "\(vm.people.count) aquí ahora"
    }

    private var markButton: some View {
        Button {
            if vm.iAmHere {
                vm.toggle()
            } else {
                showPrivacyNote = true
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "mappin.circle.fill").font(.system(size: 13))
                Text(vm.iAmHere ? "Ya no estoy" : "Estoy aquí")
                    .font(.system(size: 12, weight: .bold, design: .serif))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(vm.iAmHere ? Cumbre.ink3 : Cumbre.terraFill)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .disabled(vm.loading)
    }

    private var privacySheet: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Al marcar \"Estoy aquí\"")
                .font(.system(size: 20, weight: .bold, design: .serif))
            Text("Cualquiera que abra esta escuela verá que estás aquí y podrá escribirte por chat — aunque tu perfil sea privado. Nadie podrá ver tu perfil completo si no te sigue. Se desactiva sola pasadas 10 horas, o puedes quitarla tú antes.")
                .font(.system(size: 15))
                .foregroundStyle(Cumbre.ink2)
            Button {
                showPrivacyNote = false
                vm.toggle()
            } label: {
                Text("Entendido, estoy aquí")
                    .font(.system(size: 15, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Cumbre.terraFill)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding(24)
        .presentationDetents([.medium])
    }
}
