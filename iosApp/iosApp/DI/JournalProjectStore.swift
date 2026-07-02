import Foundation

/// Registro LOCAL de las vías marcadas como PROYECTO (las estás probando, aún
/// no te han salido). Mismo mecanismo que JournalDoneStore.swift (necesario
/// para que funcione sin conexión), con su propia clave en UserDefaults para
/// no mezclarla con la de "hecha".
final class JournalProjectStore {
    static let shared = JournalProjectStore()
    private let key = "journal_project_via_keys"
    private var cached: Set<String>

    private init() {
        cached = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
    }

    var all: Set<String> { cached }
    func contains(_ k: String) -> Bool { cached.contains(k) }

    func add(_ k: String) { cached.insert(k); persist() }
    func remove(_ k: String) { cached.remove(k); persist() }

    func sync(server: Set<String>, pending: Set<String>) {
        cached = server.union(pending); persist()
    }

    private func persist() { UserDefaults.standard.set(Array(cached), forKey: key) }
}
