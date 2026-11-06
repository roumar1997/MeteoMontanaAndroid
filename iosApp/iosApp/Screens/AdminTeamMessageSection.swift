import SwiftUI
import Shared

/// Solo admin, dentro de la ficha de moderación de un usuario: escribirle como «Equipo Cumbre» (no con tu cuenta),
/// con plantillas para lo más habitual, y restablecer un nombre de usuario que no cumple las normas. Le llega por push
/// y se queda en su campana; queda apuntado en el historial de esta ficha.
struct AdminTeamMessageSection: View {
    let uid: String
    let username: String?
    /// La ficha actualizada que devuelve el servidor tras cada acción (para refrescar el historial).
    let onUpdated: (UserModerationDto?) -> Void

    @State private var text = ""
    @State private var sending = false
    @State private var confirmReset = false
    @State private var sentNote: String?

    private var api: KtorModerationApi { AppDependencies.shared.container.moderationApi }

    /// Plantillas: se cargan en el cuadro y se pueden retocar antes de enviar.
    private var templates: [(String, String)] {
        [
            (L("Cambiar nombre"), L("Hola. Tu nombre de usuario no cumple las normas de la comunidad. Por favor, cámbialo desde tu perfil (Editar perfil). Si no, lo cambiaremos nosotros. Gracias.")),
            (L("Recordar normas"), L("Hola. Te escribimos para recordarte que en Cumbre hay que tratar a todo el mundo con respeto. Gracias por ayudar a cuidar la comunidad.")),
            (L("Foto inapropiada"), L("Hola. Una de tus fotos no cumple las normas de la comunidad y la hemos retirado. Si tienes dudas, contesta a este mensaje desde la ayuda de la app."))
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("MENSAJE DEL EQUIPO CUMBRE")).font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
            Text(L("Le llega firmado como «Equipo Cumbre», no con tu cuenta. Por notificación y en su campana."))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(templates, id: \.0) { title, body in
                        Button { text = body } label: {
                            Text(title).font(.system(size: 13, weight: .medium)).foregroundStyle(Cumbre.terra)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .overlay(Capsule().stroke(Cumbre.terra.opacity(0.6), lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }
            }
            TextField(L("Escribe el mensaje…"), text: $text, axis: .vertical)
                .lineLimit(3...8)
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: text) { _, v in if v.count > 500 { text = String(v.prefix(500)) } }
            Button { send() } label: {
                Text(sending ? L("ENVIANDO…") : L("ENVIAR COMO EQUIPO CUMBRE"))
                    .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .background(Cumbre.terraFill.opacity(canSend ? 1 : 0.35), in: Capsule())
            }
            .buttonStyle(.plain).disabled(!canSend)
            if let note = sentNote {
                Text(note).font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ok)
            }

            Divider().overlay(Cumbre.rule).padding(.vertical, 4)
            Text(L("NOMBRE DE USUARIO")).font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
            Text(L("Si no lo cambia: se le pone uno neutro (escalador_1234) y se le explica por qué."))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            Button { confirmReset = true } label: {
                Text(L("RESTABLECER @%@", username ?? "…"))
                    .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.bad)
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .overlay(Capsule().stroke(Cumbre.bad, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
        .confirmationDialog(L("¿Restablecer el nombre de usuario?"), isPresented: $confirmReset, titleVisibility: .visible) {
            Button(L("Restablecer"), role: .destructive) { resetUsername() }
            Button(L("Cancelar"), role: .cancel) {}
        } message: {
            Text(L("Se le cambiará @%@ por uno neutro y le llegará un mensaje del Equipo Cumbre explicándolo.", username ?? ""))
        }
    }

    private var canSend: Bool { !sending && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private func send() {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        sending = true
        Task {
            let updated = try? await api.sendTeamMessage(uid: uid, body: body)
            sending = false
            if let updated { text = ""; sentNote = L("Mensaje enviado."); onUpdated(updated) }
            else { sentNote = L("No se pudo enviar. Inténtalo otra vez.") }
        }
    }

    private func resetUsername() {
        Task {
            let updated = try? await api.resetUsername(uid: uid)
            sentNote = updated == nil ? L("No se pudo restablecer. Inténtalo otra vez.") : L("Nombre de usuario restablecido.")
            onUpdated(updated ?? nil)
        }
    }
}
