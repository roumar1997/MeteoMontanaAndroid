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
