import Foundation
import Shared

/// Configuración del entorno iOS.
enum AppConfig {
    /// Misma URL que el Android de producción (BuildConfig.API_BASE_URL).
    static let apiBaseUrl = "https://api.climbingteams.com/api/"
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

    private init() {
        // authService: nil → MVP público (escuelas + forecast, sin auth).
        container = IosDependencyContainer(baseUrl: AppConfig.apiBaseUrl, authService: nil)
    }
}
