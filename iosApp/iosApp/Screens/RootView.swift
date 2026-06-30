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
    // Onboarding de primera apertura (persistido). Espejo de isOnboardingDone.
    // v2: tour ampliado (6 pasos). Subir la versión re-muestra el tour una vez.
    @AppStorage("onboarding_done_v2") private var onboardingDone = false
    @State private var showLanguagePicker = !LanguageManager.shared.hasChosenLanguage
    // Fuerza re-render de MainTabView cuando el usuario cambia idioma desde Perfil.
    @State private var languageVersion = LanguageManager.shared.currentLanguage

    var body: some View {
        Group {
            if session.user == nil {
                LoginView()
            } else if showLanguagePicker {
                // Selector de idioma: gate entre login y onboarding (espejo de AppRoot.kt).
                // Se muestra solo la primera vez.
                LanguagePickerView(
                    onSelected: { code in
                        LanguageManager.shared.setLanguage(code)
                        languageVersion = code
                        showLanguagePicker = false
                    },
                    onClose: { LanguageManager.shared.markChosen(); showLanguagePicker = false }
                )
            } else if !onboardingDone {
                OnboardingView {
                    // Pide ubicación tras explicar para qué sirve y marca visto.
                    AppDependencies.shared.locationBridge.requestPermission()
                    onboardingDone = true
                }
            } else {
                MainTabView()
                    // Recrear el árbol de vistas cuando cambia el idioma desde Perfil.
                    .id(languageVersion)
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
        // Observa cambios de idioma desde AccountView para refrescar MainTabView.
        .onReceive(NotificationCenter.default.publisher(for: .languageChanged)) { _ in
            languageVersion = LanguageManager.shared.currentLanguage
        }
    }
}
