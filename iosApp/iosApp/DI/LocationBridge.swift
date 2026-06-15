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
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func hasPermission() -> Bool {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: return true
        default: return false
        }
    }

    func current(callback: @escaping (UserLocation?) -> Void) {
        guard hasPermission() else { callback(nil); return }
        // Última ubicación cacheada por el sistema, si la hay (rápido).
        if let loc = manager.location {
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
