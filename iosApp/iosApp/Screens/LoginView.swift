import SwiftUI
import AuthenticationServices

/// Pantalla de login â€” gate obligatorio al arrancar, espejo fiel de
/// `LoginScreen.kt` de Android: marca arriba (logo + CUMBRE + subtÃ­tulos),
/// botÃ³n "Continuar con Google" en el centro, legal abajo. Al iniciar sesiÃ³n,
/// el `SessionStore` de la raÃ­z detecta el cambio y muestra `MainTabView`.
struct LoginView: View {
    /// QuÃ© proveedor estÃ¡ autenticando ahora mismo (para mostrar el spinner en su
    /// propio botÃ³n). Antes habÃ­a un solo `working` que compartÃ­an los dos botones,
    /// asÃ­ que al pulsar Apple el spinner salÃ­a en el botÃ³n de Google.
    private enum Loading { case none, google, apple }
    @State private var loading: Loading = .none
    @State private var errorText: String?

    private var working: Bool { loading != .none }

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
                Text(NSLocalizedString("login_tagline", comment: ""))
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
            }
            .padding(.top, 40)

            Spacer()

            // Middle: estado o botÃ³n
            VStack(spacing: 16) {
                googleButton
                appleButton
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
                Text("Al continuar aceptas los tÃ©rminos y la polÃ­tica de privacidad.")
                    .font(Cumbre.mono(11))
                    .foregroundStyle(Cumbre.ink2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                HStack {
                    Link(NSLocalizedString("login_terms", comment: ""), destination: URL(string: "https://climbingteams.com/terms.html")!)
                        .font(Cumbre.mono(11, .bold))
                        .foregroundStyle(Cumbre.terra)
                        .padding(8)
                    Link(NSLocalizedString("login_privacy", comment: ""), destination: URL(string: "https://climbingteams.com/privacy.html")!)
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

    // BotÃ³n oscuro con la "G" de Google a color â€” igual que GoogleSignInButton de Android.
    private var googleButton: some View {
        Button {
            Task { await signIn() }
        } label: {
            HStack(spacing: 10) {
                if loading == .google {
                    ProgressView().tint(.white)
                        .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    Text("G")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color(hex: 0x4285F4))
                }
                Text(NSLocalizedString("login_google", comment: ""))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(Cumbre.inkButton)
            .clipShape(RoundedRectangle(cornerRadius: 2))
        }
        .buttonStyle(.plain)
        .disabled(working)
    }

    // BotÃ³n "Continuar con Apple" (requisito App Store por ofrecer Google).
    private var appleButton: some View {
        Button {
            Task { await signInApple() }
        } label: {
            HStack(spacing: 10) {
                if loading == .apple {
                    ProgressView().tint(.white)
                        .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    Image(systemName: "applelogo").font(.system(size: 17, weight: .medium))
                }
                Text(NSLocalizedString("login_apple", comment: "")).font(.system(size: 15, weight: .medium))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity).frame(height: 52)
            .background(Color.black)
            .clipShape(RoundedRectangle(cornerRadius: 2))
        }
        .buttonStyle(.plain)
        .disabled(working)
    }

    private func signIn() async {
        loading = .google; errorText = nil
        do { try await authBridge.signInWithGoogle() }
        catch { errorText = error.localizedDescription }
        loading = .none
    }

    private func signInApple() async {
        loading = .apple; errorText = nil
        do { try await authBridge.signInWithApple() }
        catch {
            // El usuario cancelando (cÃ³digo 1001) no es un error que mostrar.
            let ns = error as NSError
            if !(ns.domain == ASAuthorizationError.errorDomain && ns.code == ASAuthorizationError.canceled.rawValue) {
                errorText = error.localizedDescription
            }
        }
        loading = .none
    }
}
