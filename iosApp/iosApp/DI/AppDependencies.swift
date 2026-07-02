import Foundation
import Shared

/// Configuración del entorno iOS.
///
/// Igual que en Android (debug vs release): los builds **Debug** (desarrollo,
/// TestFlight interno) hablan con el backend de **staging**; los builds
/// **Release** (App Store) con **producción**. Así desarrollar nunca afecta a
/// los usuarios reales.
enum AppConfig {
    static let apiBaseUrl: String = {
        #if DEBUG
        return "https://meteomontanaapi-staging.up.railway.app/api/"
        #else
        return "https://api.climbingteams.com/api/"
        #endif
    }()
}

/// Punto único de dependencias del lado Swift. Envuelve el grafo de DI escrito
/// en Kotlin (`IosDependencyContainer`), donde vive toda la fontanería
/// suspend/StateFlow. Las pantallas piden use cases a través de `container`.
///
/// Equivalente iOS de Hilt. Cuando se añada login, aquí se construirá el
/// `IosFirebaseAuthService` (Swift) y se pasará al contenedor.
final class AppDependencies {
    static let shared = AppDependencies()

    let container: IosDependencyContainer
    /// Bridge de ubicación (CLLocationManager). Las pantallas lo usan para
    /// comprobar/pedir permiso; el `LocationProvider` del contenedor lo
    /// envuelve para los use cases.
    let locationBridge = LocationBridge()
    /// Bridge de autenticación (FirebaseAuth + Google Sign-In). `LoginView` lo
    /// usa para `signInWithGoogle()`; el `AuthService` del contenedor lo
    /// envuelve para el tokenProvider del HttpClient.
    let authBridge = AuthBridge()
    /// El AuthService compartido (StateFlow de sesión + token + signOut).
    let authService: IosAuthService
    /// Bridge de chat (FirebaseFirestore). El `ChatService` del contenedor lo
    /// envuelve en Flow/suspend para las pantallas de chat.
    let chatBridge = ChatBridge()
    /// Bridge de la báscula WH-C06 (CoreBluetooth). El `GripScaleProvider` del
    /// contenedor lo envuelve en Flow para la pestaña Agarres.
    let gripScaleBridge = GripScaleBridge()

    private init() {
        // locationProvider: bridge iOS → tab Tiempo en tu ubicación real.
        // authService: bridge Firebase → endpoints autenticados reciben token.
        let location = IosLocationProvider(bridge: locationBridge)
        let auth = IosAuthService(bridge: authBridge)
        authService = auth
        let chat = IosChatService(bridge: chatBridge)
        let gripScale = IosGripScaleProvider(bridge: gripScaleBridge)
        // BD SQLDelight local (driver nativo) para el caché del catálogo.
        let db = DatabaseFactory().create()
        container = IosDependencyContainer(
            baseUrl: AppConfig.apiBaseUrl,
            authService: auth,
            locationProvider: location,
            database: db,
            chatService: chat,
            gripScaleProvider: gripScale
        )
    }
}
