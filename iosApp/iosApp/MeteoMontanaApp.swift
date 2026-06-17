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
    @Environment(\.scenePhase) private var scenePhase

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
                // Al arrancar, sube las vías marcadas sin red que quedaron en cola.
                .task { try? await AppDependencies.shared.container.flushJournalOutbox() }
                // Push (APNs/FCM): no-op hasta activarlo (PushManager.enabled).
                .onAppear { PushManager.shared.registerIfEnabled() }
        }
        .onChange(of: scenePhase) { phase in
            // Al volver a primer plano (recuperada la conexión normalmente),
            // reintenta subir la cola offline de vías hechas.
            if phase == .active {
                Task { try? await AppDependencies.shared.container.flushJournalOutbox() }
            }
        }
    }
}
