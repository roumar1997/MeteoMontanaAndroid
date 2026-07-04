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
        manager.requestLocation()
    }

    /// La pide la pantalla de Tiempo cuando aún no hay permiso.
    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { resolvePending(nil); return }
        resolvePending(UserLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude))
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

/// Rumbo del móvil (brújula) para el cono de dirección del punto azul.
/// Cuantizado a 15º para no re-pintar el marcador a 50 Hz.
final class HeadingProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var heading: Int? = nil
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    func start() { manager.startUpdatingHeading() }
    func stop() { manager.stopUpdatingHeading() }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let deg = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        guard deg >= 0 else { return }
        let q = Int((deg / 15).rounded()) * 15 % 360
        if q != heading { DispatchQueue.main.async { self.heading = q } }
    }
}
