import FirebaseAuth
import SwiftUI

/// Estado de sesión observable a nivel de app. Se apoya en el listener nativo de
/// FirebaseAuth (no en el StateFlow de Kotlin) para el gating de UI, mientras
/// que el `AuthService` compartido sigue alimentando el token del HttpClient.
@MainActor
final class SessionStore: ObservableObject {
    @Published var user: FirebaseAuth.User?

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
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

    var body: some View {
        Group {
            if session.user == nil {
                LoginView()
            } else {
                MainTabView()
            }
        }
        // Aplica el tema elegido (nil = seguir al sistema).
        .preferredColorScheme(theme.colorScheme)
        // JIT provisioning: al iniciar sesión, getMyProfile crea/asegura el
        // usuario en el backend (espejo de ensureUserProvisioned en AppRoot.kt).
        .task(id: session.user?.uid) {
            guard session.user != nil else { return }
            _ = try? await AppDependencies.shared.container.getMyProfile.invoke()
        }
    }
}
