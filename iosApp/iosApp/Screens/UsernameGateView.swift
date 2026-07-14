import SwiftUI
import Shared

/// Gate de username obligatorio — espejo de UsernameGate.kt de Android.
/// Se muestra tras el tutorial SOLO si el perfil no tiene username (dato del
/// servidor: reinstalar la app no lo re-muestra). Pantalla completa, sin
/// forma de saltarla: elegir username es el último paso del primer arranque.
struct UsernameGateView: View {
    /// Se llama al guardar con éxito (RootView pasa a MainTabView).
    let onDone: () -> Void

    @State private var username = ""
    @State private var saving = false
    @State private var error: String? = nil

    /// Mismas reglas que el backend: 3-20, minúsculas/dígitos/guión bajo.
    private var valid: Bool {
        username.range(of: "^[a-z0-9_]{3,20}$", options: .regularExpression) != nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()
            Text("ELIGE TU NOMBRE DE USUARIO")
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
            Text("Es tu identidad en Cumbre: aparece en tus comentarios, en el feed y en el enlace de tu perfil. Solo tienes que elegirlo una vez.")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                .padding(.top, 8).padding(.bottom, 20)
            HStack(spacing: 6) {
                Text("@").font(.system(size: 15)).foregroundStyle(Cumbre.ink3)
                TextField("ej: alvaro_jara", text: $username)
                    .font(.system(size: 15))
                    .foregroundStyle(Cumbre.ink)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onChange(of: username) { _, new in
                        username = String(new.lowercased()
                            .replacingOccurrences(of: " ", with: "_")
                            .prefix(20))
                    }
            }
            .padding(.horizontal, 12).padding(.vertical, 11)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 2)
                .stroke(error == nil ? Cumbre.rule : .red, lineWidth: 1))
            Text(error ?? "3-20 caracteres: minúsculas, números y _")
                .font(Cumbre.mono(10))
                .foregroundStyle(error == nil ? Cumbre.ink3 : .red)
                .padding(.top, 6)
            Button {
                save()
            } label: {
                Group {
                    if saving {
                        ProgressView().tint(.white)
                    } else {
                        Text("GUARDAR").font(Cumbre.mono(11, .bold)).tracking(1.8)
                    }
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(valid && !saving ? Cumbre.terra : Cumbre.ink3)
                .clipShape(RoundedRectangle(cornerRadius: 2))
            }
            .buttonStyle(.plain)
            .disabled(!valid || saving)
            .padding(.top, 20)
            Spacer()
        }
        .padding(.horizontal, 24)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private func save() {
        guard valid, !saving else { return }
        saving = true
        error = nil
        Task {
            defer { saving = false }
            let req = UpdateProfileRequest(
                username: username,
                displayName: nil, bio: nil, topGrade: nil,
                isPublic: nil, photoUrl: nil, gender: nil, gearJson: nil)
            do {
                _ = try await AppDependencies.shared.container.updateMyProfile.invoke(req: req)
                onDone()
            } catch {
                // 409 = ya cogido; el resto, mensaje genérico.
                let desc = String(describing: error)
                self.error = desc.contains("409") || desc.lowercased().contains("conflict")
                    ? "Ese nombre de usuario ya está cogido"
                    : "No se pudo guardar. Inténtalo de nuevo."
            }
        }
    }
}
