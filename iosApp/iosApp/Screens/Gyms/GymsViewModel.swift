import SwiftUI
import CoreLocation
import Shared

/// Capa que enseña el selector de Escuelas: la roca de siempre, los rocódromos o los muros gratis.
enum SchoolsLayer {
    case rock, rocodromos, freeWalls

    var gymMode: GymMode? {
        switch self {
        case .rock: return nil
        case .rocodromos: return .rocodromos
        case .freeWalls: return .freeWalls
        }
    }
}

enum GymsViewKind { case map, list }

/// Lo que ha tocado el usuario en los filtros (cápsulas y buscador); el modo lo pone la capa.
struct GymFilterState: Equatable {
    var onlyFree = false
    var boulder = false
    var rope = false
    var openNow = false
    var onlyFavorites = false
    var chain: String?
    var maxDistanceKm: Double?
    var query = ""
}

/// Estado de la capa de rocódromos de Escuelas. Solo habla con use cases de `shared`: el filtrado, el orden y el
/// «abierto ahora» son lógica común con Android (`FilterGymsUseCase`). Espejo de `GymsViewModel.kt`.
@MainActor
final class GymsViewModel: ObservableObject {
    @Published var layer: SchoolsLayer = .rock
    @Published var viewKind: GymsViewKind = .map
    @Published private(set) var all: [Gym] = []
    @Published private(set) var items: [GymListItem] = []
    @Published private(set) var chains: [String] = []
    @Published private(set) var favorites: Set<Int64> = []
    @Published private(set) var loading = false
    /// Sin red y sin copia local: no hay nada que enseñar.
    @Published private(set) var loadFailed = false
    @Published var filter = GymFilterState() { didSet { recompute() } }

    /// Cámara del mapa: sobrevive a alternar Mapa/Lista y a cambiar de capa (la vista se recrea).
    var savedCenter: CLLocationCoordinate2D?
    var savedZoom: Double?

    private var origin: GymOrigin?
    private var started = false
    private let c = AppDependencies.shared.container.gyms

    var isFreeLayer: Bool { layer == .freeWalls }

    func setLayer(_ new: SchoolsLayer) {
        layer = new
        guard new.gymMode != nil else { return }
        // Al cambiar de capa se quitan los filtros que no valen en la otra (p. ej. «Gratis» en Muros gratis).
        var f = filter
        f.onlyFree = false
        f.chain = nil
        filter = f
        recompute()
        ensureLoaded()
    }

    /// Catálogo: primero la copia local (al instante) y luego la red. Se pide una sola vez por proceso.
    func ensureLoaded() {
        guard !started else { return }
        started = true
        Task { await refresh() }
    }

    func refresh() async {
        let cached = c.getCachedGyms.invoke()
        all = cached
        if !cached.isEmpty { loadFailed = false }
        loading = cached.isEmpty
        recompute()
        do {
            all = try await c.getGyms.execute()
            loadFailed = false
        } catch {
            loadFailed = all.isEmpty
        }
        loading = false
        recompute()
        await refreshFavorites()
    }

    /// Al volver de la ficha (donde se puede cambiar el favorito) se relee del servidor.
    func refreshFavorites() async {
        guard let ids = try? await c.getGymFavorites.execute() else { return }
        favorites = Set(ids.map { $0.int64Value })
        recompute()
    }

    func setOrigin(lat: Double?, lon: Double?) {
        let new = (lat != nil && lon != nil) ? GymOrigin(lat: lat ?? 0, lon: lon ?? 0) : nil
        if origin?.lat == new?.lat && origin?.lon == new?.lon { return }
        origin = new
        recompute()
    }

    func clearFilters() { filter = GymFilterState() }

    /// Agrupación del mapa por zoom (rejilla común con Android).
    func cluster(_ gyms: [Gym], zoom: Double) -> [GymMapItem] { c.clusterGyms.invoke(gyms: gyms, zoom: zoom) }

    func toggleFavorite(_ gymId: Int64) {
        let now = !favorites.contains(gymId)
        apply(favorite: now, gymId)
        Task {
            do {
                _ = try await c.setGymFavorite.execute(gymId: gymId, favorite: now)
            } catch {
                // Si el servidor lo rechaza (sin red, sin sesión) se deshace: lo que se ve es lo que hay guardado.
                apply(favorite: !now, gymId)
            }
        }
    }

    private func apply(favorite: Bool, _ gymId: Int64) {
        if favorite { favorites.insert(gymId) } else { favorites.remove(gymId) }
        recompute()
    }

    private func recompute() {
        let mode: GymMode = layer.gymMode ?? .rocodromos
        let f = GymFilters(
            mode: mode, onlyFree: filter.onlyFree, boulder: filter.boulder, rope: filter.rope, openNow: filter.openNow,
            onlyFavorites: filter.onlyFavorites, chain: filter.chain,
            maxDistanceKm: filter.maxDistanceKm.map { KotlinDouble(value: $0) }, query: filter.query, sort: .distance)
        let favs = Set(favorites.map { KotlinLong(value: $0) })
        items = c.filterGyms.filterNow(gyms: all, filters: f, origin: origin, favoriteIds: favs)
        chains = c.filterGyms.chains(gyms: all, mode: mode)
    }
}
