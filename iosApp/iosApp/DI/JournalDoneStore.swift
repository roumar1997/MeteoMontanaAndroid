import Foundation

/// Registro LOCAL de las vías marcadas como hechas (clave "escuelaId|nombreVía").
///
/// Igual que en Android: el diario vive en el servidor y SIN conexión no se
/// puede consultar, así que sin este registro la app no sabría qué ya marcaste
/// y dejaría volver a sumarlo (duplicados). Persiste en UserDefaults.
final class JournalDoneStore {
    static let shared = JournalDoneStore()
    private let key = "journal_done_via_keys"
    private var cached: Set<String>

    private init() {
        cached = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
    }

    var all: Set<String> { cached }
    func contains(_ k: String) -> Bool { cached.contains(k) }

    func add(_ k: String) { cached.insert(k); persist() }
    func remove(_ k: String) { cached.remove(k); persist() }

    /// Sincroniza con la verdad del servidor (al cargar el diario online),
    /// conservando las marcadas offline aún sin subir (pendientes en cola).
    func sync(server: Set<String>, pending: Set<String>) {
        cached = server.union(pending); persist()
    }

    private func persist() { UserDefaults.standard.set(Array(cached), forKey: key) }
}
