import SwiftUI
import Shared

/// Solo admin, sección «AVISAR» de la ficha de moderación: escribirle como «Equipo Cumbre» (no con tu cuenta),
/// con mensajes rápidos para lo más habitual. Le llega por push y se queda en su campana; queda apuntado en el
/// historial de esta ficha.
struct AdminTeamMessageSection: View {
    let uid: String
    /// La ficha actualizada que devuelve el servidor tras cada acción (para refrescar el historial).
    let onUpdated: (UserModerationDto?) -> Void

    @State private var text = ""
    @State private var sending = false
    @State private var sentNote: String?

    private var api: KtorModerationApi { AppDependencies.shared.container.moderationApi }

    /// Mensajes rápidos: se cargan en el cuadro y se pueden retocar antes de enviar.
    private var templates: [(String, String)] {
        [
            (L("Cambiar nombre"), L("Hola. Tu nombre de usuario no cumple las normas de la comunidad. Por favor, cámbialo desde tu perfil (Editar perfil). Si no, lo cambiaremos nosotros. Gracias.")),
            (L("Recordar normas"), L("Hola. Te escribimos para recordarte que en Cumbre hay que tratar a todo el mundo con respeto. Gracias por ayudar a cuidar la comunidad.")),
            (L("Foto inapropiada"), L("Hola. Una de tus fotos no cumple las normas de la comunidad y la hemos retirado. Si tienes dudas, contesta a este mensaje desde la ayuda de la app."))
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
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
}
