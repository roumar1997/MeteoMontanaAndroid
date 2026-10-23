import Foundation

/// La casa del usuario para "cerca de casa" / "donde vivo". Se guarda SOLO en este móvil (nunca en el servidor);
/// al preguntar se envía junto a la pregunta y el servidor la usa solo para medir distancias, como la ubicación.
struct AssistantHomeStore {
    private let defaults: UserDefaults
    private static let latKey = "assistant_home_lat"
    private static let lonKey = "assistant_home_lon"

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    var home: (lat: Double, lon: Double)? {
        guard defaults.object(forKey: Self.latKey) != nil, defaults.object(forKey: Self.lonKey) != nil else { return nil }
        return (defaults.double(forKey: Self.latKey), defaults.double(forKey: Self.lonKey))
    }

    func save(lat: Double, lon: Double) {
        defaults.set(lat, forKey: Self.latKey)
        defaults.set(lon, forKey: Self.lonKey)
    }

    func clear() {
        defaults.removeObject(forKey: Self.latKey)
        defaults.removeObject(forKey: Self.lonKey)
    }
}
