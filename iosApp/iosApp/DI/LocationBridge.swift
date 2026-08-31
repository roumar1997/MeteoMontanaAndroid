import CoreLocation
import Foundation
import Shared

/// Implementación Swift del bridge `IosLocationBridge` (definido en Kotlin
/// `iosMain`). Usa CLLocationManager. El lado Kotlin (`IosLocationProvider`)
/// envuelve estos callbacks en una función `suspend`, de modo que los use
/// cases compartidos ven un `LocationProvider` normal.
///
/// Es el equivalente iOS del FusedLocation de Android.
final class LocationBridge: NSObject, IosLocationBridge, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    /// Callback pendiente mientras esperamos un `requestLocation()` asíncrono.
    private var pending: ((UserLocation?) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        // NearestTenMeters (antes Kilometer → el punto azul caía a 500 m-1 km
        // en el monte; mismo bug que Android con BALANCED_POWER).
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func hasPermission() -> Bool {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: return true
        default: return false
        }
    }

    func current(callback: @escaping (UserLocation?) -> Void) {
        guard hasPermission() else { callback(nil); return }
        // Última ubicación cacheada, solo si es reciente y precisa (si no,
        // puede ser una posición vieja a cientos de metros).
        if let loc = manager.location,
           loc.timestamp.timeIntervalSinceNow > -60,
           loc.horizontalAccuracy >= 0, loc.horizontalAccuracy <= 100 {
            callback(UserLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude))
            return
        }
        // Si no, pedimos una nueva (resuelve en el delegate).
        pending = callback
        // OJO: si el mapa de la escuela ya tiene un stream CONTINUO activo
        // (startStream, el puntito de "mi ubicación"), NO se debe pedir
        // requestLocation() a la vez sobre el mismo manager — Apple no
        // soporta bien mezclar ambos modos y la petición puntual se quedaba
        // sin resolver NUNCA (foto por cámara "sin ubicación" solo DENTRO de
        // una escuela, nunca desde Escuelas, donde no hay stream corriendo;
        // Rodrigo, 2026-08-22). El stream ya en marcha entrega la siguiente
        // posición por didUpdateLocations, que resuelve `pending` igual.
        if streamCallback == nil {
            manager.requestLocation()
        }
    }

    /// La pide la pantalla de Tiempo cuando aún no hay permiso.
    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    // ── Seguimiento CONTINUO (mapa de escuela abierto) ──────────────────
    // requestLocation() cada 5 s daba fixes pobres en montaña (el GPS se
    // enfría entre peticiones). startUpdatingLocation mantiene el chip
    // caliente y afina en segundos, como las apps de mapas.
    private var streamCallback: ((UserLocation) -> Void)?

    func startStream(callback: @escaping (UserLocation) -> Void) {
        streamCallback = callback
        manager.startUpdatingLocation()
    }

    func stopStream() {
        streamCallback = nil
        manager.stopUpdatingLocation()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { resolvePending(nil); return }
        let u = UserLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude)
        streamCallback?(u)
        resolvePending(u)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        resolvePending(nil)
    }

    private func resolvePending(_ location: UserLocation?) {
        let cb = pending
        pending = nil
        cb?(location)
    }
}

/// Rumbo del móvil (brújula), para el cono de dirección del punto azul y para
/// la brújula de elegir la orientación de una pared.
///
/// Cuantizado a `stepDegrees` para no re-pintar a 50 Hz: 15º basta para el cono,
/// pero la brújula de la orientación pide un paso fino (2º) porque ahí se lee el
/// número de grados. Espejo de `rememberDeviceHeading` en Android.
final class HeadingProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var heading: Int? = nil
    private let manager = CLLocationManager()
    private let stepDegrees: Double

    init(stepDegrees: Double = 15) {
        self.stepDegrees = stepDegrees
        super.init()
        manager.delegate = self
    }

    func start() { manager.startUpdatingHeading() }
    func stop() { manager.stopUpdatingHeading() }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let deg = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        guard deg >= 0 else { return }
        let q = Int((deg / stepDegrees).rounded() * stepDegrees) % 360
        if q != heading { DispatchQueue.main.async { self.heading = q } }
    }
}
