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
        } catch is CancellationError {
            // Cancelada (p.ej. al salir de la pantalla a mitad) — no es un
            // fallo real, no hay nada que avisar.
        } catch {
            // El mensaje técnico ("Socket timeout has expired...") no le dice
            // nada al usuario — se registra para depurar y se muestra uno
            // normal (Álvaro, 2026-09-04).
            print("SchoolPresence: no se pudo cargar la presencia — \(error)")
            errorText = L("No se pudo cargar quién hay aquí. Comprueba tu conexión.")
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
                print("SchoolPresence: no se pudo marcar/quitar la presencia — \(error)")
                errorText = iAmHere ? L("No se pudo quitar la presencia. Comprueba tu conexión.") : L("No se pudo marcar la presencia. Comprueba tu conexión.")
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

struct SchoolPresenceRow<Leading: View, Trailing: View>: View {
    let schoolId: String
    let schoolName: String
    let myUid: String?
    /// Sube al deslizar hacia abajo en la ficha (pull-to-refresh) para pedir
    /// otra vez quién hay — sin caché, es información en vivo (Álvaro,
    /// 2026-09-04: mejor a demanda que sondear sola cada X segundos).
    var refreshTrigger: Int = 0
    /// La cabecera de la ficha (volver + nombre | iconos) se pinta AQUÍ, en la misma fila que el chat de la escuela
    /// y el botón "Aquí": así no hace falta una fila fija extra (2026-10-14, para ganar altura).
    let leading: Leading
    let trailing: Trailing
    @StateObject private var vm: SchoolPresenceViewModel
    @State private var openChatFor: ChatTarget?
    @State private var showSchoolChat = false
    @State private var showAllPresent = false

    init(schoolId: String, schoolName: String, myUid: String?, refreshTrigger: Int = 0,
         @ViewBuilder leading: () -> Leading, @ViewBuilder trailing: () -> Trailing) {
        self.schoolId = schoolId
        self.schoolName = schoolName
        self.myUid = myUid
        self.refreshTrigger = refreshTrigger
        self.leading = leading()
        self.trailing = trailing()
        _vm = StateObject(wrappedValue: SchoolPresenceViewModel(schoolId: schoolId, myUid: myUid))
    }

    var body: some View {
        VStack(spacing: 0) {
            // Fila de cabecera: el nombre se queda con el hueco sobrante y puede encogerse.
            HStack(spacing: 6) {
                leading.frame(maxWidth: .infinity, alignment: .leading)
                presenceControls
                    .guideTarget("detail.here")
                trailing
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            // Quién hay ahora mismo: solo ocupa una fila cuando de verdad hay gente.
            if !vm.people.isEmpty || vm.iAmHere {
                peopleBar
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
        .task(id: refreshTrigger) { if refreshTrigger > 0 { await vm.load() } }
        .navigationDestination(item: $openChatFor) { target in
            ChatView(otherUid: target.uid, otherName: target.name)
        }
        .navigationDestination(isPresented: $showSchoolChat) {
            SchoolChatView(schoolId: schoolId, schoolName: schoolName)
        }
        // Dentro del chat se puede marcar "Estoy aquí": al volver, la fila refleja quién hay.
        .onChange(of: showSchoolChat) { _, open in
            if !open { Task { await vm.load() } }
        }
        .sheet(isPresented: $showAllPresent) {
            allPresentSheet
        }
    }

    private var peopleBar: some View {
        HStack(spacing: 6) {
            // Un toque en cualquier parte del grupo de avatares abre la lista
            // completa — con más de 4 presentes, tocar un avatar concreto en
            // la pila superpuesta es ambiguo (¿cuál de los que se tapan he
            // tocado?); la lista deja elegir con nombre y sin dudas.
            Button { showAllPresent = true } label: {
                HStack(spacing: -6) {
                    ForEach(vm.people.prefix(4), id: \.uid) { person in
                        AvatarCircle(url: person.photoUrl, size: 18, uid: person.uid)
                            .overlay(Circle().stroke(Cumbre.bg, lineWidth: 1.5))
                    }
                }
            }
            Button { showAllPresent = true } label: {
                Text(peopleLabel)
                    .font(.system(size: 11.5, weight: .semibold, design: .serif))
                    .foregroundStyle(Cumbre.ink)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 5)
        .frame(height: 30)
        .background(Cumbre.bg)
        .overlay(Rectangle().frame(height: 1).foregroundStyle(Cumbre.rule), alignment: .bottom)
    }

    private var allPresentSheet: some View {
        NavigationStack {
            List(vm.people, id: \.uid) { person in
                Button {
                    guard person.uid != myUid else { return }
                    showAllPresent = false
                    openChatFor = ChatTarget(uid: person.uid, name: person.displayName ?? person.username ?? L("Usuario"))
                } label: {
                    HStack(spacing: 12) {
                        AvatarCircle(url: person.photoUrl, size: 36, uid: person.uid)
                        Text(person.uid == myUid ? "Tú" : (person.displayName ?? person.username ?? L("Usuario")))
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
            .navigationTitle(L("%@ aquí ahora", vm.people.count))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") { showAllPresent = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// Chat de la escuela (símbolo) + "Aquí": con el menú "⋯" fuera sobra sitio en la cabecera (Álvaro, 2026-10-14).
    /// "Aquí" también se puede marcar desde dentro del chat.
    private var presenceControls: some View {
        HStack(spacing: 6) {
            Button { showSchoolChat = true } label: {
                Image(systemName: "bubble.left.and.bubble.right")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                    .frame(width: 40, height: 40)
                    .background(Cumbre.paper, in: Circle())
                    .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Chat de la escuela"))
            PresenceMarkButton(vm: vm)
        }
    }

    private var peopleLabel: String {
        vm.people.isEmpty ? "" : L("%@ aquí ahora", vm.people.count)
    }
}

/// "Estoy aquí" / "Ya no": marca o quita tu presencia en la escuela (con el aviso de privacidad la primera vez de cada
/// vez). Vive DENTRO del chat de la escuela; la fila de la ficha solo enseña un símbolo que lleva al chat.
struct PresenceMarkButton: View {
    @ObservedObject var vm: SchoolPresenceViewModel
    @State private var showPrivacyNote = false

    var body: some View {
        Button {
            if vm.iAmHere { vm.toggle() } else { showPrivacyNote = true }
        } label: {
            HStack(spacing: 3) {
                Image(systemName: "mappin.circle.fill").font(.system(size: 14))
                Text(vm.iAmHere ? L("Ya no") : L("Aquí"))
                    .font(.system(size: 14, weight: .bold, design: .serif))
                    .lineLimit(1).fixedSize()
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10).padding(.vertical, 10)
            .background(vm.iAmHere ? Cumbre.ink3 : Cumbre.terraFill, in: Capsule())
        }
        .disabled(vm.loading)
        .sheet(isPresented: $showPrivacyNote) { privacySheet }
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
