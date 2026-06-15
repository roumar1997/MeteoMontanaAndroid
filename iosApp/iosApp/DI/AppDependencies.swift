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
    /// Bridge de ubicación (CLLocationManager). Las pantallas lo usan para
    /// comprobar/pedir permiso; el `LocationProvider` del contenedor lo
    /// envuelve para los use cases.
    let locationBridge = LocationBridge()

    private init() {
        // authService: nil → MVP público (escuelas + forecast, sin auth).
        // locationProvider: bridge iOS → tab Tiempo en tu ubicación real.
        let location = IosLocationProvider(bridge: locationBridge)
        container = IosDependencyContainer(
            baseUrl: AppConfig.apiBaseUrl,
            authService: nil,
            locationProvider: location
        )
    }
}
