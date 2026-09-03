import SwiftUI
import Shared

/// "Estoy aquí": quién está presente en esta escuela ahora mismo, con un
/// botón para marcarte tú también. Tocar el grupo de avatares/contador abre
/// la lista completa (hoja); desde ahí, tocar a alguien abre `ChatView`
/// (chat 1-a-1 ya existente). El icono de chat abre `SchoolChatView`, el
/// tablón abierto del sitio.
@MainActor
final class SchoolPresenceViewModel: ObservableObject {
    @Published var people: [SchoolPresence] = []
    @Published var iAmHere = false
    @Published var loading = false
    // Antes se tragaba cualquier fallo con `try?` sin decir nada — "Ya no
    // estoy" parecía no hacer nada y no había forma de saber por qué
    // (Álvaro, 2026-09-03). Ahora, si algo falla de verdad, se ve en pantalla.
    @Published var errorText: String?

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
        do {
            let list = try await getPresence.execute(schoolId: schoolId)
            people = list
            iAmHere = myUid.map { uid in list.contains { $0.uid == uid } } ?? false
        } catch {
            errorText = "No se pudo cargar quién hay aquí: \(error.localizedDescription)"
        }
    }

    func toggle() {
        guard !loading else { return }
        loading = true
        errorText = nil
        Task {
            defer { loading = false }
            do {
                if iAmHere {
                    try await clearPresence.execute(schoolId: schoolId)
                } else {
                    _ = try await markPresence.execute(schoolId: schoolId)
                }
            } catch {
                errorText = "\(iAmHere ? "No se pudo quitar" : "No se pudo marcar") la presencia: \(error.localizedDescription)"
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
    let schoolName: String
    let myUid: String?
    @StateObject private var vm: SchoolPresenceViewModel
    @State private var showPrivacyNote = false
    @State private var openChatFor: ChatTarget?
    @State private var showSchoolChat = false
    @State private var showAllPresent = false

    init(schoolId: String, schoolName: String, myUid: String?) {
        self.schoolId = schoolId
        self.schoolName = schoolName
        self.myUid = myUid
        _vm = StateObject(wrappedValue: SchoolPresenceViewModel(schoolId: schoolId, myUid: myUid))
    }

    var body: some View {
        VStack(spacing: 0) {
            Group {
                if !vm.people.isEmpty || vm.iAmHere {
                    content
                } else {
                    // Nadie presente: la misma barra fina, con chat + botón a la derecha.
                    HStack {
                        Spacer()
                        chatButton
                        markButton
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 5)
                    .frame(height: 34)
                    .background(Cumbre.paper2)
                    .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
                }
            }
            if let err = vm.errorText {
                Text(err)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Cumbre.bad)
                    .padding(.horizontal, 16).padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Cumbre.bad.opacity(0.1))
            }
        }
        .task { await vm.load() }
        .sheet(isPresented: $showPrivacyNote) {
            privacySheet
        }
        .navigationDestination(item: $openChatFor) { target in
            ChatView(otherUid: target.uid, otherName: target.name)
        }
        .navigationDestination(isPresented: $showSchoolChat) {
            SchoolChatView(schoolId: schoolId, schoolName: schoolName)
        }
        .sheet(isPresented: $showAllPresent) {
            allPresentSheet
        }
    }

    private var content: some View {
        HStack(spacing: 6) {
            // Un toque en cualquier parte del grupo de avatares abre la lista
            // completa — con más de 4 presentes, tocar un avatar concreto en
            // la pila superpuesta es ambiguo (¿cuál de los que se tapan he
            // tocado?); la lista deja elegir con nombre y sin dudas.
            Button { showAllPresent = true } label: {
                HStack(spacing: -6) {
                    ForEach(vm.people.prefix(4), id: \.uid) { person in
                        AvatarCircle(url: person.photoUrl, size: 18)
                            .overlay(Circle().stroke(Cumbre.paper2, lineWidth: 1.5))
                    }
                }
            }
            Button { showAllPresent = true } label: {
                Text(peopleLabel)
                    .font(.system(size: 11.5, weight: .semibold, design: .serif))
                    .foregroundStyle(Cumbre.ink)
            }
            Spacer()
            chatButton
            markButton
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 5)
        .frame(height: 34)
        .background(Cumbre.paper2)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
    }

    private var allPresentSheet: some View {
        NavigationStack {
            List(vm.people, id: \.uid) { person in
                Button {
                    guard person.uid != myUid else { return }
                    showAllPresent = false
                    openChatFor = ChatTarget(uid: person.uid, name: person.displayName ?? person.username ?? "Usuario")
                } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: person.photoUrl, size: 36)
                        Text(person.uid == myUid ? "Tú" : (person.displayName ?? person.username ?? "Usuario"))
                            .font(.system(size: 15, design: .serif))
                            .foregroundStyle(Cumbre.ink)
                        Spacer()
                        if person.uid != myUid {
                            Image(systemName: "bubble.left").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        }
                    }
                }
                .disabled(person.uid == myUid)
            }
            .navigationTitle("\(vm.people.count) aquí ahora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") { showAllPresent = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var chatButton: some View {
        Button { showSchoolChat = true } label: {
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 13))
                .foregroundStyle(Cumbre.ink2)
                .frame(width: 26, height: 26)
                .background(Cumbre.paper, in: Circle())
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
        }
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
