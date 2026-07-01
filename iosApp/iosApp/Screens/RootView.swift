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

    var body: some View {
        Group {
            if session.user == nil {
                LoginView()
            } else if !onboardingDone {
                OnboardingView {
                    AppDependencies.shared.locationBridge.requestPermission()
                    onboardingDone = true
                }
            } else {
                MainTabView()
            }
        }
        .preferredColorScheme(theme.colorScheme)
        .task(id: session.user?.uid) {
            guard session.user != nil else { return }
            _ = try? await AppDependencies.shared.container.getMyProfile.invoke()
        }
    }
}
