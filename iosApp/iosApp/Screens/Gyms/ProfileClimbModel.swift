import SwiftUI
import Shared

/// Un orden que elige la persona (categorías de Ascensiones, rocódromos): caché local y marca de «pendiente de subir»
/// por si se cambió sin red. La verdad vive en el servidor. Espejo de `OrderPreference` (Android).
struct ProfileOrderPref {
    let key: String
    private var d: UserDefaults { .standard }

    func get() -> String? { d.string(forKey: key).flatMap { $0.isEmpty ? nil : $0 } }
    func set(_ order: String, pending: Bool) {
        d.set(order, forKey: key)
        d.set(pending, forKey: key + "_pending")
    }
    var isPending: Bool { d.bool(forKey: key + "_pending") }

    /// Propio: el servidor manda; si no tiene nada, o hay un cambio sin subir, sube el del móvil.
    func sync(server: String?, upload: (String) async throws -> Void) async {
        if let local = get(), server == nil || isPending {
            if (try? await upload(local)) != nil { set(local, pending: false) }
            return
        }
        if let server { set(server, pending: false) }
    }

    /// Guarda ya y lo sube; si falla (sin red) queda «pendiente» para subirlo al abrir el perfil.
    func save(_ order: String, upload: (String) async throws -> Void) async {
        set(order, pending: true)
        if (try? await upload(order)) != nil { set(order, pending: false) }
    }

    static let ascents = ProfileOrderPref(key: "ascent_tab_order")
    static let gyms = ProfileOrderPref(key: "gym_order")
}

/// Preferencias pequeñas del perfil que solo viven en el móvil.
enum ProfilePrefs {
    private static let homeKey = "home_gym_id"
    private static let boardsKey = "boards_expanded"

    static var homeGymId: Int64? {
        get { (UserDefaults.standard.string(forKey: homeKey)).flatMap { Int64($0) } }
        set { UserDefaults.standard.set(newValue.map { String($0) } ?? "", forKey: homeKey) }
    }

    /// Tableros que la persona dejó DESPLEGADOS (por defecto, todos plegados).
    static var expandedBoards: Set<String> {
        get { Set((UserDefaults.standard.string(forKey: boardsKey) ?? "").split(separator: ",").map(String.init).filter { !$0.isEmpty }) }
        set { UserDefaults.standard.set(newValue.sorted().joined(separator: ","), forKey: boardsKey) }
    }
}

/// Un tramo de la barra de colores: un grado o el color de una escala, con cuántas veces y su orden de dificultad.
struct BarSeg: Hashable {
    let grade: String?
    let hex: String?
    let count: Int
    let order: Int
}

/// Lo del rocódromo en el perfil, propio o de otra persona. Espejo de `ProfileClimbViewModel` (Android): el cálculo vive
/// en shared (`GymBreakdown`, `GymOrder`); aquí solo se junta y se guarda el orden elegido.
@MainActor
final class ProfileClimbModel: ObservableObject {
    @Published var summary = GymSummary(sessions: 0, gyms: 0, ascents: 0, maxGrade: nil, byGym: [], items: [], byMonth: [],
                                        homeGym: nil, ascentOrder: nil, gymOrder: nil, boardProblems: [], boardAscents: [])
    /// `summary.byGym` ya ordenado (favoritos primero o el orden elegido).
    @Published var byGym: [GymSummaryGym] = []
    @Published var rockDays: Int?
    @Published var segments: [BarSeg] = []
    @Published var homeGym: HomeGym?
    @Published var ascentOrder: String?
    @Published var gymOrder: String?
    @Published var favorites = Set<Int64>()
    @Published var expandedBoards = ProfilePrefs.expandedBoards
    @Published var streakWeeks = 0
    @Published var stats: JournalStats?
    @Published var loaded = false
    /// Uid de la persona (nil = perfil propio).
    private(set) var uid: String?

    private let c = AppDependencies.shared.container
    private var gyms: [Int64: Gym] { Dictionary(c.gyms.getCachedGyms.invoke().map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a }) }

    var own: Bool { uid == nil }
    var totalDays: Int { (rockDays ?? 0) + Int(summary.sessions) }

    func load(uid: String?) async {
        self.uid = uid
        let me = AppDependencies.shared.authBridge.currentUid()
        let gymsById = gyms
        if own { apply(c.gyms.getGymSummary.local(name: { gymsById[$0.int64Value]?.name }), gymsById) }
        if let ids = try? await c.gyms.getGymFavorites.execute() { favorites = Set(ids.map { $0.int64Value }) }
        if own {
            stats = try? await c.getMyJournalStats.invoke()
            if let entries = try? await c.getMyJournal.invoke() { rockDays = Int(JournalStatsCalculator.shared.rockDays(entries: entries)) }
            try? await c.gyms.getGymSummary.refresh()
        } else if let uid {
            stats = try? await c.getUserStats.invoke(uid: uid)
            if let entries = try? await c.getUserJournal.invoke(uid: uid) {
                rockDays = Int(JournalStatsCalculator.shared.rockDays(entries: entries))
            }
        }
        let target = own ? me : uid
        var remote: GymSummary?
        if let target { remote = try? await c.gyms.getGymSummary.remote(uidOrUsername: target) }
        if own, let remote {
            if let server = remote.homeGym { ProfilePrefs.homeGymId = server.id } else if let local = ProfilePrefs.homeGymId {
                _ = try? await c.gyms.homeGym.set(gymId: KotlinLong(value: local))
            }
            await ProfileOrderPref.ascents.sync(server: remote.ascentOrder) { _ = try await self.c.gyms.ascentOrder.set(order: $0) }
            await ProfileOrderPref.gyms.sync(server: remote.gymOrder) { _ = try await self.c.gyms.gymOrder.set(order: $0) }
        }
        let fallback = own ? c.gyms.getGymSummary.local(name: { gymsById[$0.int64Value]?.name }) : summary
        apply(remote ?? fallback, gymsById)
        loaded = true
    }

    private func apply(_ s: GymSummary, _ gyms: [Int64: Gym]) {
        summary = s
        var segs: [BarSeg] = GymBreakdown.shared.byGrade(items: s.items).compactMap { p in
            guard let g = p.first as? String, let n = p.second else { return nil }
            return BarSeg(grade: g, hex: nil, count: Int(truncating: n), order: Int(GymAscentKeys.shared.gradeRank(grade: g)))
        }
        for a in GymBreakdown.shared.byColor(items: s.items) {
            if let hex = colorHex(a.key, gyms) { segs.append(BarSeg(grade: nil, hex: hex, count: Int(a.count), order: 1000)) }
        }
        segments = segs.sorted { $0.order < $1.order }
        gymOrder = own ? (ProfileOrderPref.gyms.get() ?? s.gymOrder) : s.gymOrder
        sortGyms()
        homeGym = s.homeGym ?? (own ? ProfilePrefs.homeGymId.flatMap { gyms[$0] }.map { HomeGym(id: $0.id, name: $0.name, city: $0.city) } : nil)
        ascentOrder = own ? (ProfileOrderPref.ascents.get() ?? s.ascentOrder) : s.ascentOrder
        streakWeeks = own ? Int(c.gyms.getGymStats.cached().streakWeeks) : 0
    }

    private func sortGyms() {
        let ids = GymOrder.shared.parse(saved: gymOrder)
        byGym = GymOrder.shared.sort(gyms: summary.byGym.map { g in
            g.name != nil ? g : GymSummaryGym(gymId: g.gymId, name: gyms[g.gymId]?.name, sessions: g.sessions, ascents: g.ascents,
                                              maxGrade: g.maxGrade, items: g.items, days: g.days)
        }, saved: ids, favorites: Set(favorites.map { KotlinLong(value: $0) }))
    }

    private func colorHex(_ key: String, _ gyms: [Int64: Gym]) -> String? {
        for g in gyms.values { if let s = g.scale, s.isColors, let lv = s.levelNamed(key: key) { return lv.hex } }
        return nil
    }

    /// Color (hex) de un nivel de la escala de ESE rocódromo; si no se conoce, el de cualquiera con ese nombre.
    func colorHexFor(gymId: Int64, key: String) -> String? {
        let all = gyms
        if let s = all[gymId]?.scale, s.isColors, let lv = s.levelNamed(key: key) { return lv.hex }
        return colorHex(key, all)
    }

    func gym(_ id: Int64) -> Gym? { gyms[id] }

    func setAscentOrder(_ order: String) {
        ascentOrder = order
        Task { await ProfileOrderPref.ascents.save(order) { _ = try await self.c.gyms.ascentOrder.set(order: $0) } }
    }

    func setGymOrder(_ ids: [Int64]) {
        let order = GymOrder.shared.serialize(ids: ids.map { KotlinLong(value: $0) })
        gymOrder = order
        sortGyms()
        Task { await ProfileOrderPref.gyms.save(order) { _ = try await self.c.gyms.gymOrder.set(order: $0) } }
    }

    func toggleBoard(_ kind: BoardKind) {
        let name = kind.name
        if expandedBoards.contains(name) { expandedBoards.remove(name) } else { expandedBoards.insert(name) }
        ProfilePrefs.expandedBoards = expandedBoards
    }

    func setHomeGym(_ g: Gym?) {
        ProfilePrefs.homeGymId = g?.id
        homeGym = g.map { HomeGym(id: $0.id, name: $0.name, city: $0.city) }
        Task { _ = try? await c.gyms.homeGym.set(gymId: g.map { KotlinLong(value: $0.id) }) }
    }
}
