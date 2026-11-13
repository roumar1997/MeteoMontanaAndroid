import SwiftUI
import Shared

/// Solo admin, sección «NOMBRE Y USUARIO» de la ficha de moderación: ponerle el nombre y el usuario que elijas
/// (mismas reglas que el perfil: usuario de 3-20 minúsculas, números o _, y libre) o restablecer uno neutro.
struct AdminNameSection: View {
    let uid: String
    let username: String?
    let displayName: String?
    /// La ficha actualizada que devuelve el servidor tras cada acción (para refrescar el historial).
    let onUpdated: (UserModerationDto?) -> Void

    @State private var newName = ""
    @State private var newUsername = ""
    @State private var saving = false
    @State private var confirmSave = false
    @State private var confirmReset = false
    @State private var confirmResetName = false
    @State private var note: String?
    @State private var noteIsError = false

    private var api: KtorModerationApi { AppDependencies.shared.container.moderationApi }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("Ahora: «%@» · @%@", displayName ?? "…", username ?? "…"))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            AdminField(placeholder: L("Nombre nuevo"), text: $newName)
                .onChange(of: newName) { _, v in if v.count > 120 { newName = String(v.prefix(120)) } }
            AdminField(placeholder: L("Usuario nuevo (sin @)"), text: $newUsername)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .onChange(of: newUsername) { _, v in
                    let clean = v.lowercased().replacingOccurrences(of: "@", with: "")
                    if clean != v { newUsername = clean }
                }
            Button { confirmSave = true } label: {
                Text(saving ? L("GUARDANDO…") : L("GUARDAR"))
                    .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                    .background(Cumbre.terraFill.opacity(canSave ? 1 : 0.35), in: Capsule())
            }
            .buttonStyle(.plain).disabled(!canSave)
            if let note {
                Text(note).font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(noteIsError ? Cumbre.bad : Cumbre.ok)
            }
            Text(L("O ponle uno neutro («Escalador» o escalador_1234); le llega un mensaje explicándolo."))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).padding(.top, 4)
            outlineButton(L("RESTABLECER @%@", username ?? "…")) { confirmReset = true }
            outlineButton(L("RESTABLECER NOMBRE «%@»", displayName ?? "…")) { confirmResetName = true }
        }
        .confirmationDialog(L("¿Guardar el nombre nuevo?"), isPresented: $confirmSave, titleVisibility: .visible) {
            Button(L("Guardar")) { save() }
            Button(L("Cancelar"), role: .cancel) {}
        } message: {
            Text(saveSummary)
        }
        .confirmationDialog(L("¿Restablecer el nombre?"), isPresented: $confirmResetName, titleVisibility: .visible) {
            Button(L("Restablecer"), role: .destructive) { resetName() }
            Button(L("Cancelar"), role: .cancel) {}
        } message: {
            Text(L("Se le cambiará «%@» por «Escalador» y le llegará un mensaje del Equipo Cumbre explicándolo.", displayName ?? ""))
        }
        .confirmationDialog(L("¿Restablecer el nombre de usuario?"), isPresented: $confirmReset, titleVisibility: .visible) {
            Button(L("Restablecer"), role: .destructive) { resetUsername() }
            Button(L("Cancelar"), role: .cancel) {}
        } message: {
            Text(L("Se le cambiará @%@ por uno neutro y le llegará un mensaje del Equipo Cumbre explicándolo.", username ?? ""))
        }
    }

    private var trimmedName: String { newName.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var trimmedUsername: String { newUsername.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSave: Bool { !saving && (!trimmedName.isEmpty || !trimmedUsername.isEmpty) }

    private var saveSummary: String {
        var parts: [String] = []
        if !trimmedName.isEmpty { parts.append(L("Nombre: «%@»", trimmedName)) }
        if !trimmedUsername.isEmpty { parts.append(L("Usuario: @%@", trimmedUsername)) }
        return parts.joined(separator: "\n")
    }

    private func save() {
        saving = true
        Task {
            let r = try? await api.renameUser(uid: uid,
                                              username: trimmedUsername.isEmpty ? nil : trimmedUsername,
                                              displayName: trimmedName.isEmpty ? nil : trimmedName)
            saving = false
            if let m = r?.moderation {
                newName = ""; newUsername = ""
                show(L("Guardado."), error: false)
                onUpdated(m)
            } else {
                switch r?.error ?? "failed" {
                case "taken": show(L("Ese usuario ya lo tiene otra persona."), error: true)
                case "invalid": show(L("El usuario debe tener 3-20 caracteres: minúsculas, números o _."), error: true)
                default: show(L("No se pudo guardar. Inténtalo otra vez."), error: true)
                }
            }
        }
    }

    private func resetName() {
        Task {
            let updated = try? await api.resetDisplayName(uid: uid)
            show(updated == nil ? L("No se pudo restablecer. Inténtalo otra vez.") : L("Nombre restablecido."),
                 error: updated == nil)
            onUpdated(updated ?? nil)
        }
    }

    private func resetUsername() {
        Task {
            let updated = try? await api.resetUsername(uid: uid)
            show(updated == nil ? L("No se pudo restablecer. Inténtalo otra vez.") : L("Nombre de usuario restablecido."),
                 error: updated == nil)
            onUpdated(updated ?? nil)
        }
    }

    private func show(_ text: String, error: Bool) { note = text; noteIsError = error }

    private func outlineButton(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.bad)
                .lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity).padding(.vertical, 11)
                .overlay(Capsule().stroke(Cumbre.bad, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
