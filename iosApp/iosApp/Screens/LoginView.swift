import SwiftUI

/// Pantalla de login — gate obligatorio al arrancar, espejo fiel de
/// `LoginScreen.kt` de Android: marca arriba (logo + CUMBRE + subtítulos),
/// botón "Continuar con Google" en el centro, legal abajo. Al iniciar sesión,
/// el `SessionStore` de la raíz detecta el cambio y muestra `MainTabView`.
struct LoginView: View {
    @State private var working = false
    @State private var errorText: String?

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        VStack {
            // Top: marca
            VStack(spacing: 0) {
                Image("logo_cumbre")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
                Spacer().frame(height: 20)
                Text("CUMBRE")
                    .font(Cumbre.serif(36, .bold))
                    .tracking(4)
                    .foregroundStyle(Cumbre.terra)
                Spacer().frame(height: 4)
                Text("MeteoMontana")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Cumbre.ink)
                Spacer().frame(height: 2)
                Text("Tiempo para escalar")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
            }
            .padding(.top, 40)

            Spacer()

            // Middle: estado o botón
            VStack(spacing: 16) {
                googleButton
                if let err = errorText {
                    Text(err)
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.bad)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                }
            }

            Spacer()

            // Bottom: legal
            VStack(spacing: 4) {
                Text("Al continuar aceptas los términos y la política de privacidad.")
                    .font(Cumbre.mono(11))
                    .foregroundStyle(Cumbre.ink2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                HStack {
                    Link("TÉRMINOS", destination: URL(string: "https://climbingteams.com/terms.html")!)
                        .font(Cumbre.mono(11, .bold))
                        .foregroundStyle(Cumbre.terra)
                        .padding(8)
                    Link("PRIVACIDAD", destination: URL(string: "https://climbingteams.com/privacy.html")!)
                        .font(Cumbre.mono(11, .bold))
                        .foregroundStyle(Cumbre.terra)
                        .padding(8)
                }
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    // Botón oscuro con la "G" de Google a color — igual que GoogleSignInButton de Android.
    private var googleButton: some View {
        Button {
            Task { await signIn() }
        } label: {
            HStack(spacing: 10) {
                if working {
                    ProgressView().tint(.white)
                } else {
                    Text("G")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color(hex: 0x4285F4))
                }
                Text("Continuar con Google")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(Cumbre.ink)
            .clipShape(RoundedRectangle(cornerRadius: 2))
        }
        .buttonStyle(.plain)
        .disabled(working)
    }

    private func signIn() async {
        working = true; errorText = nil
        do { try await authBridge.signInWithGoogle() }
        catch { errorText = error.localizedDescription }
        working = false
    }
}
