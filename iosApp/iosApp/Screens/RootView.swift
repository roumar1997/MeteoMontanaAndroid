import FirebaseAuth
import FirebaseCore
import SwiftUI

/// Estado de sesión observable a nivel de app. Se apoya en el listener nativo de
/// FirebaseAuth (no en el StateFlow de Kotlin) para el gating de UI, mientras
/// que el `AuthService` compartido sigue alimentando el token del HttpClient.
@MainActor
final class SessionStore: ObservableObject {
    @Published var user: FirebaseAuth.User?

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
        // Protección: Auth.auth() hace fatalError si Firebase no está configurado.
        // El AppDelegate lo configura antes de la primera escena, pero por si el
        // orden fallara en alguna build (Release/beta), lo configuramos aquí si
        // aún no lo está — así la app NUNCA crashea al arrancar por esto.
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        user = Auth.auth().currentUser
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.user = user
        }
    }

    deinit {
        if let handle { Auth.auth().removeStateDidChangeListener(handle) }
    }
}

/// Raíz de la app — espejo de `AppRoot.kt` de Android: el login es un gate
/// obligatorio al arrancar. Sin sesión → `LoginView` (pantalla de marca a
/// pantalla completa). Con sesión → `MainTabView` (Tiempo · Escuelas · Radar).
/// No hay modo invitado, igual que en Android.
struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    @ObservedObject private var theme = ThemeManager.shared
    // Onboarding de primera apertura (persistido). Espejo de isOnboardingDone.
    // v2: tour ampliado (6 pasos). Subir la versión re-muestra el tour una vez.
    @AppStorage("onboarding_done_v2") private var onboardingDone = false
    // Gate de username obligatorio tras el tutorial: condición del SERVIDOR
    // (username == nil en /api/me), no de la instalación — reinstalar no lo
    // re-muestra si ya lo tienes. nil = perfil aún sin cargar.
    @State private var needsUsername: Bool? = nil

    // Actualización OBLIGATORIA: URL de la tienda si esta build está por debajo
    // del mínimo del backend; nil = todo bien (o no se pudo comprobar — un
    // fallo de red NUNCA bloquea la app).
    @State private var forceUpdateUrl: String? = nil

    var body: some View {
        Group {
            if let url = forceUpdateUrl {
                ForceUpdateView(storeUrl: url)
            } else if session.user == nil {
                LoginView()
            } else if !onboardingDone {
                OnboardingView {
                    AppDependencies.shared.locationBridge.requestPermission()
                    onboardingDone = true
                }
            } else if needsUsername == true {
                UsernameGateView { needsUsername = false }
            } else {
                MainTabView()
            }
        }
        .preferredColorScheme(theme.colorScheme)
        // Gate de versión mínima (antes incluso del login).
        .task {
            guard let dto = try? await AppDependencies.shared.container.appVersionApi.get() else { return }
            let build = Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0
            if build > 0 && build < Int(dto.minIosBuild) {
                forceUpdateUrl = dto.iosUrl ?? "https://api.climbingteams.com/app"
            }
        }
        .task(id: session.user?.uid) {
            guard session.user != nil else { return }
            if let profile = try? await AppDependencies.shared.container.getMyProfile.invoke() {
                needsUsername = profile.username == nil
            } else {
                // Offline: no bloqueamos la app; se reintenta al próximo arranque.
                needsUsername = false
            }
        }
    }
}

/// Pantalla completa NO descartable: hay que actualizar para seguir usando la
/// app — espejo de ForceUpdateScreen de AppRoot.kt.
struct ForceUpdateView: View {
    let storeUrl: String

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            Text("ACTUALIZACIÓN NECESARIA")
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
            Text("Hay una versión nueva obligatoria")
                .font(Cumbre.serif(22, .bold))
                .foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            Text("Esta versión de Cumbre ya no es compatible. Actualiza para seguir usándola.")
                .font(.system(size: 14))
                .foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            Button {
                if let url = URL(string: storeUrl) { UIApplication.shared.open(url) }
            } label: {
                Text("ACTUALIZAR AHORA")
                    .font(Cumbre.mono(11, .bold)).tracking(1.4)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Cumbre.terra)
                    .clipShape(RoundedRectangle(cornerRadius: 2))
            }
            .buttonStyle(.plain)
            .padding(.top, 24)
            Spacer()
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }
}
