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

/// Identifica con quién abrir el chat 1-a-1. `Identifiable` para poder usar
/// `navigationDestination(item:)` — el `NavigationLink(isActive:)` de antes
/// (en `.background()`) se comía el toque del botón "Ya no estoy" al
/// compartir zona de gesto con el resto de la fila (Álvaro, 2026-09-03:
/// el botón no respondía en TestFlight). `navigationDestination(item:)` no
/// necesita una vista fantasma en el árbol, así que no interfiere.
private struct ChatTarget: Identifiable, Hashable {
    let uid: String
    let name: String
    var id: String { uid }
}

struct SchoolPresenceRow: View {
    let schoolId: String
    let myUid: String?
    @StateObject private var vm: SchoolPresenceViewModel
    @State private var showPrivacyNote = false
    @State private var openChatFor: ChatTarget?

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
                // Nadie presente: la misma barra fina, solo con el botón a la derecha.
                HStack {
                    Spacer()
                    markButton
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 5)
                .frame(height: 34)
                .background(Cumbre.paper2)
                .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
            }
        }
        .task { await vm.load() }
        .sheet(isPresented: $showPrivacyNote) {
            privacySheet
        }
        .navigationDestination(item: $openChatFor) { target in
            ChatView(otherUid: target.uid, otherName: target.name)
        }
    }

    private var content: some View {
        HStack(spacing: 6) {
            HStack(spacing: -6) {
                ForEach(vm.people.prefix(4), id: \.uid) { person in
                    Button {
                        guard person.uid != myUid else { return }
                        openChatFor = ChatTarget(uid: person.uid, name: person.displayName ?? person.username ?? "Usuario")
                    } label: {
                        AvatarCircle(url: person.photoUrl, size: 18)
                            .overlay(Circle().stroke(Cumbre.paper2, lineWidth: 1.5))
                    }
                }
            }
            Text(peopleLabel)
                .font(.system(size: 11.5, weight: .semibold, design: .serif))
                .foregroundStyle(Cumbre.ink)
            Spacer()
            markButton
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 5)
        .frame(height: 34)
        .background(Cumbre.paper2)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
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
            HStack(spacing: 4) {
                Image(systemName: "mappin.circle.fill").font(.system(size: 11))
                Text(vm.iAmHere ? "Ya no estoy" : "Estoy aquí")
                    .font(.system(size: 11, weight: .bold, design: .serif))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(vm.iAmHere ? Cumbre.ink3 : Cumbre.terraFill)
            .clipShape(RoundedRectangle(cornerRadius: 7))
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
