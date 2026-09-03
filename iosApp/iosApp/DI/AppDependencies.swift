import Foundation
import Shared

/// Configuración del entorno iOS.
///
/// Igual que en Android (debug vs release): los builds **Debug** (desarrollo,
/// TestFlight interno) hablan con el backend de **staging**; los builds
/// **Release** (App Store) con **producción**. Así desarrollar nunca afecta a
/// los usuarios reales.
/// El .ipa de DESARROLLO se compila en RELEASE (la velocidad real de SwiftUI:
/// los cierres 0x8BADF00D eran el runtime Debug sin optimizar) pero debe seguir
/// apuntando a staging. ios-ci.yml cambia este flag a true con sed ANTES de
/// compilar; el workflow de prod no lo toca (queda false → producción).
enum BuildFlags {
    static let ciStaging = false

    /// Sello de compilación, que se ve en Ajustes. El CI lo sustituye por la
    /// fecha real al construir el `.ipa`; en local queda "local".
    ///
    /// Existe porque hoy nos ha costado media mañana distinguir "no te ha
    /// llegado el arreglo" de "no lo has arreglado": con la fecha a la vista, la
    /// pregunta se responde en dos segundos.
    static let buildStamp = "local"
}

enum AppConfig {
    static let apiBaseUrl: String = {
        #if DEBUG
        return "https://meteomontanaapi-staging.up.railway.app/api/"
        #else
        return BuildFlags.ciStaging
            ? "https://meteomontanaapi-staging.up.railway.app/api/"
            : "https://api.climbingteams.com/api/"
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
    /// Bridge del chat ABIERTO de escuela ("Estoy aquí"). Se envuelve igual
    /// que `chatBridge` pero con su propio servicio Kotlin (no comparten
    /// puerto: uno exige participantes, el otro no).
    let schoolChatBridge = SchoolChatBridge()

    private init() {
        // locationProvider: bridge iOS → tab Tiempo en tu ubicación real.
        // authService: bridge Firebase → endpoints autenticados reciben token.
        let location = IosLocationProvider(bridge: locationBridge)
        let auth = IosAuthService(bridge: authBridge)
        authService = auth
        let chat = IosChatService(bridge: chatBridge)
        let schoolChat = IosSchoolChatService(bridge: schoolChatBridge)
        // BD SQLDelight local (driver nativo) para el caché del catálogo.
        let db = DatabaseFactory().create()
        container = IosDependencyContainer(
            baseUrl: AppConfig.apiBaseUrl,
            authService: auth,
            locationProvider: location,
            database: db,
            chatService: chat,
            schoolChatService: schoolChat
        )
    }
}
