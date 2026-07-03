import SwiftUI
import FirebaseCore
import GoogleSignIn
import Shared

// ⚠️ Escrito sin Mac (Fase C). Validar firmas generadas por SKIE al primer
// build en Xcode (Fase E): nombres de métodos async, tipos opcionales boxed
// (KotlinDouble?), y la exposición del `operator fun invoke`.

/// AppDelegate solo para configurar Firebase en didFinishLaunching, que corre
/// GARANTIZADO antes de que SwiftUI cree la primera escena y sus @StateObject.
/// Antes FirebaseApp.configure() vivía en el init() de la App, pero en builds
/// Release (y en iOS 26 beta) el @StateObject `session = SessionStore()` se
/// creaba ANTES de ese init → SessionStore.init llamaba a Auth.auth() con
/// Firebase sin configurar → EXC_BREAKPOINT (crash al abrir). Este es el patrón
/// oficial de Firebase para SwiftUI.
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        return true
    }
}

@main
struct MeteoMontanaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var session = SessionStore()
    @StateObject private var shareRouter = ShareLinkRouter.shared
    @Environment(\.scenePhase) private var scenePhase

    /// Binding del destino del enlace compartido (para el fullScreenCover).
    private var shareTargetBinding: Binding<ShareLinkRouter.Target?> {
        Binding(get: { shareRouter.target }, set: { shareRouter.target = $0 })
    }

    var body: some Scene {
        WindowGroup {
            // RootView hace de gate: sin sesión → LoginView, con sesión →
            // MainTabView (igual que AppRoot.kt en Android).
            RootView()
                .environmentObject(session)
                // Enlaces compartidos (Universal Links /s/...) o, si no es
                // nuestro, el callback del navegador tras el login de Google.
                .onOpenURL { url in
                    if !ShareLinkRouter.shared.handle(url) {
                        GIDSignIn.sharedInstance.handle(url)
                    }
                }
                // Destino del enlace compartido: escuela/vía o quedada invitada.
                .fullScreenCover(item: shareTargetBinding) { t in
                    NavigationStack {
                        Group {
                            if let school = t.school {
                                SchoolDetailView(school: school, openVia: t.viaId)
                            } else if let meetupId = t.meetupId {
                                MeetupDetailView(meetupId: meetupId)
                            }
                        }
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Button("Cerrar") { ShareLinkRouter.shared.target = nil }
                                    .foregroundStyle(Cumbre.terra)
                            }
                        }
                    }
                }
                // Al arrancar, sube las vías marcadas sin red que quedaron en cola.
                .task {
                    try? await AppDependencies.shared.container.flushJournalOutbox()
                    await ContributionOutboxFlusher.flush()
                }
                // Refresca las escuelas guardadas offline (datos al día, sin
                // tener que pulsar "descargar" de nuevo).
                .task { try? await AppDependencies.shared.container.syncSavedSchools() }
                // Push (APNs/FCM): no-op hasta activarlo (PushManager.enabled).
                .onAppear {
                    PushManager.shared.registerIfEnabled()
                    // Fuerza el modo claro/oscuro en las ventanas (también sheets).
                    ThemeManager.shared.applyToWindows()
                }
        }
        .onChange(of: scenePhase) { phase in
            // Al volver a primer plano (recuperada la conexión normalmente),
            // reintenta subir la cola offline de vías hechas.
            if phase == .active {
                Task {
                    try? await AppDependencies.shared.container.flushJournalOutbox()
                    await ContributionOutboxFlusher.flush()
                }
                Task { try? await AppDependencies.shared.container.syncSavedSchools() }
                ThemeManager.shared.applyToWindows()
            }
        }
    }
}
