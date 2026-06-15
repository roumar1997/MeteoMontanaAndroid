import SwiftUI
import FirebaseCore
import GoogleSignIn
import Shared

// ⚠️ Escrito sin Mac (Fase C). Validar firmas generadas por SKIE al primer
// build en Xcode (Fase E): nombres de métodos async, tipos opcionales boxed
// (KotlinDouble?), y la exposición del `operator fun invoke`.

@main
struct MeteoMontanaApp: App {
    @StateObject private var session = SessionStore()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            // RootView hace de gate: sin sesión → LoginView, con sesión →
            // MainTabView (igual que AppRoot.kt en Android).
            RootView()
                .environmentObject(session)
                // Callback del navegador tras el login de Google.
                .onOpenURL { url in GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
