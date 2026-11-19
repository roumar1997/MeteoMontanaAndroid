import SwiftUI
import CoreLocation
import Shared

/// Mapa de rocódromos (MapLibre): un marcador por ficha o un grupo con su número. La agrupación sale de `shared`
/// (`GymClusterer`) para que iOS y Android agrupen igual. Tocar un grupo acerca la cámara a sus miembros; tocar un
/// marcador abre la tarjeta de cristal. Mismo mapa que Escuelas: satélite por defecto, botón de capas y de ubicación.
/// Bajo cada marcador suelto va su nombre cuando hay zoom y sitio (`GymLabelPlanner` de shared). Espejo de `GymsMap.kt`.
struct GymsMapView: View {
    @ObservedObject var vm: GymsViewModel
    let userLat: Double?
    let userLon: Double?
    let needsLocationPermission: Bool
    let onRequestLocation: () -> Void
    let onOpenGym: (Int64) -> Void
    let onAddWall: () -> Void

    @Environment(\.colorScheme) private var scheme
    @State private var zoom = 5.0
    @State private var selectedId: Int64?
    @State private var lastMarkerTap = Date.distantPast
    // Encuadre: cuando cambia QUÉ fichas se ven (filtros, capa) la cámara las abarca; no al cambiar solo la selección.
    @State private var fittedIds: [Int64] = []
    @State private var focusToken = 0
    @State private var focusFit: [CLLocationCoordinate2D] = []
    @State private var focusCoordinate: CLLocationCoordinate2D?
    @State private var focusZoom = 13.5

    private var mapHeight: CGFloat { max(380, UIScreen.main.bounds.height * 0.6) }
    private var gyms: [Gym] { vm.items.map { $0.gym } }
    private var ids: [Int64] { vm.items.map { $0.gym.id } }

    var body: some View {
        ZStack(alignment: .bottom) {
            MapLibreView(center: initialCenter, zoom: vm.savedZoom ?? initialZoom, markers: markers,
                         style: vm.mapStyle,
                         onZoomChange: { zoom = $0 },
                         onCameraChange: { center, z in vm.savedCenter = center; vm.savedZoom = z },
                         onTapMarker: tapMarker,
                         onMapTap: { _ in tapEmpty() },
                         focusCoordinate: focusCoordinate, focusZoom: focusZoom, focusToken: focusToken,
                         focusFitCoordinates: focusFit, compassTopMargin: 56)
            .frame(height: mapHeight)
            mapButtons
            bottomCard
        }
        .frame(height: mapHeight)
        .onAppear {
            zoom = vm.savedZoom ?? initialZoom
            if fittedIds.isEmpty { fittedIds = ids }
        }
        .onChange(of: ids) { _, new in refit(new) }
    }

    // MARK: - Marcadores

    private var markers: [CumbreMarker] {
        let dark = scheme == .dark
        let items = clusters
        let named = labelledIds(items)
        var out: [CumbreMarker] = items.map { item in
            let coord = CLLocationCoordinate2D(latitude: item.lat, longitude: item.lon)
            if item.isCluster {
                return CumbreMarker(id: item.id, coordinate: coord, title: "\(item.count)", kind: .gymCluster,
                                    color: UIColor(rgb: dark ? 0x2A281F : 0x1C1C1A), score: Int(item.count))
            }
            let gym = item.gyms[0]
            var marker = CumbreMarker(id: item.id, coordinate: coord, title: gym.name, kind: .gym,
                                      color: GymTone.fillUIColor(gym.kind, dark: dark))
            marker.glyph = GymTone.glyph(gym.kind)
            marker.selected = gym.id == selectedId
            marker.badge = gym.kind == .free ? L("gym_free_badge") : nil
            marker.name = gym.name
            marker.showName = named.contains(item.id)
            return marker
        }
        if let lat = userLat, let lon = userLon {
            out.append(CumbreMarker(id: "__USER__", coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                                    title: "", kind: .user))
        }
        return out
    }

    private var clusters: [GymMapItem] { vm.cluster(gyms, zoom: zoom) }

    /// Ids de los marcadores sueltos que enseñan nombre: solo con zoom cercano y sin pisarse (`GymLabelPlanner`).
    /// Las posiciones en pantalla salen de proyectar a Mercator con el zoom actual (MapLibre: mundo de 512 pt a zoom 0);
    /// el reparto solo depende de las posiciones relativas, así que no hace falta saber dónde está la cámara.
    private func labelledIds(_ items: [GymMapItem]) -> Set<String> {
        guard zoom >= GymLabelPlanner.shared.MIN_ZOOM, !items.isEmpty else { return [] }
        let world = 512.0 * pow(2.0, zoom)
        func project(_ item: GymMapItem) -> (x: Double, y: Double) {
            let latRad = item.lat * .pi / 180
            return ((item.lon + 180) / 360 * world, (1 - log(tan(latRad) + 1 / cos(latRad)) / .pi) / 2 * world)
        }
        let origin = project(items[0])
        let candidates = items.map { item -> LabelCandidate in
            let p = project(item)
            let single: Gym? = item.isCluster ? nil : item.gyms[0]
            return LabelCandidate(id: item.id, x: Float(p.x - origin.x), y: Float(p.y - origin.y),
                                  nameChars: Int32(single?.name.count ?? 0), priority: single?.id == selectedId)
        }
        return GymLabelPlanner.shared.plan(candidates: candidates, density: 1)
    }

    private var initialCenter: CLLocationCoordinate2D {
        if let c = vm.savedCenter { return c }
        if let lat = userLat, let lon = userLon { return CLLocationCoordinate2D(latitude: lat, longitude: lon) }
        return CLLocationCoordinate2D(latitude: 40.4, longitude: -3.7)
    }

    /// La primera vez: centrado en el usuario a zoom 9,5, o España a zoom 5 sin ubicación.
    private var initialZoom: Double { userLat != nil && userLon != nil ? 9.5 : 5 }

    // MARK: - Toques y encuadre

    private func tapMarker(_ id: String) {
        lastMarkerTap = Date()
        guard let item = clusters.first(where: { $0.id == id }) else { return }
        if item.isCluster { zoomInto(item) } else { selectedId = item.gyms[0].id }
    }

    /// El toque en el mapa vacío cierra la tarjeta; el que acaba de tocar un marcador no cuenta.
    private func tapEmpty() {
        if Date().timeIntervalSince(lastMarkerTap) > 0.4 { selectedId = nil }
    }

    /// Tocar un grupo acerca la cámara a sus miembros; si están todos en el mismo punto, zoom directo por encima del grupo.
    private func zoomInto(_ item: GymMapItem) {
        let lats = item.gyms.map { $0.lat }
        let lons = item.gyms.map { $0.lon }
        let tiny = ((lats.max() ?? 0) - (lats.min() ?? 0)) < 1e-4 && ((lons.max() ?? 0) - (lons.min() ?? 0)) < 1e-4
        if tiny {
            focusFit = []
            focusCoordinate = CLLocationCoordinate2D(latitude: item.lat, longitude: item.lon)
            focusZoom = 14
        } else {
            focusFit = item.gyms.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        }
        focusToken += 1
    }

    private func refit(_ new: [Int64]) {
        guard new != fittedIds, !new.isEmpty else { return }
        fittedIds = new
        let coords = gyms.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        if coords.count == 1 {
            focusFit = []
            focusCoordinate = coords[0]
            focusZoom = 13.5
        } else {
            focusFit = coords
        }
        focusToken += 1
    }

    // MARK: - Controles

    private var mapButtons: some View {
        VStack {
            HStack(spacing: 8) {
                Spacer()
                Button { vm.mapStyle = vm.mapStyle == .satellite ? .topo : .satellite } label: {
                    Image(systemName: "square.3.layers.3d")
                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                        .frame(width: 34, height: 34).background(Cumbre.bg, in: Circle())
                        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Satélite"))
                Button(action: locate) {
                    Image(systemName: needsLocationPermission ? "location.slash" : "location.fill")
                        .font(.system(size: 15)).foregroundStyle(needsLocationPermission ? Cumbre.ink : Cumbre.terra)
                        .frame(width: 34, height: 34).background(Cumbre.bg, in: Circle())
                        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Mi ubicación"))
            }
            Spacer()
        }
        .padding(10)
    }

    private func locate() {
        guard let lat = userLat, let lon = userLon else { onRequestLocation(); return }
        focusFit = []
        focusCoordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        focusZoom = max(zoom, 11)
        focusToken += 1
    }

    @ViewBuilder private var bottomCard: some View {
        if let selected = vm.items.first(where: { $0.gym.id == selectedId }) {
            GymMapCard(item: selected, isFavorite: vm.favorites.contains(selected.gym.id),
                       onToggleFavorite: { vm.toggleFavorite(selected.gym.id) },
                       onClose: { selectedId = nil }, onOpen: { onOpenGym(selected.gym.id) })
                .padding(.horizontal, 12).padding(.bottom, 12)
        } else {
            GymDashedPill(text: L("gym_add_missing"), translucent: true, action: onAddWall)
                .padding(.horizontal, 12).padding(.bottom, 12)
        }
    }
}
