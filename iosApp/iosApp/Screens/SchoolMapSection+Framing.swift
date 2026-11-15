import SwiftUI
import Shared
import CoreLocation

// Encuadre del mapa de una escuela: SOLO las piedras (nunca parkings, sectores ni aproximaciones), al entrar y cada
// vez que cambia un filtro. Qué puntos cuentan lo decide shared (`MapGeometry.boundsForVisibleBlocks`), igual que en
// Android (SchoolMapCamera.kt), para que las dos apps encuadren lo mismo.

extension SchoolMapSection {
    /// Si estoy a más de esto del centro de las piedras, mi ubicación no entra en el encuadre (igual que Android).
    static let maxUserLocationIncludeKm = 20.0

    /// Piedras que los filtros dejan atenuadas o fuera (grado, Vía/Bloque, orientación): no cuentan para el encuadre.
    var dimmedStoneIds: Set<String> {
        var ids = Set<String>()
        for b in vm.blocks where b.type.uppercased() == "BLOCK" {
            if gradeFilterActive && !gradeFilter.matchingBlockIds.contains(b.id) { ids.insert(b.id); continue }
            if !matchesStyleFilter(b) { ids.insert(b.id); continue }
            if let f = orientationFilter, blockOrientations[b.id] != f { ids.insert(b.id) }
        }
        return ids
    }

    /// Cambia cuando cambia cualquier filtro que mueve el encuadre. Un solo `onChange` en vez de tres.
    var framingSignature: String {
        let grades = selectedGrades.sorted().joined(separator: ",")
        let styles = styleFilter.sorted().joined(separator: ",")
        return "\(grades)|\(styles)|\(orientationFilter ?? "")"
    }

    /// Encuadra las piedras visibles con margen. Devuelve false si no queda ninguna (el que llama decide qué hacer).
    @discardableResult
    func frameVisibleStones(includeUser: Bool = false) -> Bool {
        guard let b = MapGeometry.shared.boundsForVisibleBlocks(blocks: vm.blocks, dimmedIds: dimmedStoneIds) else {
            return false
        }
        var south = b.south, west = b.west, north = b.north, east = b.east
        // Mi ubicación entra en el encuadre de entrada SOLO si estoy cerca (paridad con Android: ver desde casa no debe
        // estirar el zoom hasta abarcarme).
        if includeUser, let u = userCoord {
            let distKm = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude,
                                                lat2: (south + north) / 2, lon2: (west + east) / 2)
            if distKm <= Self.maxUserLocationIncludeKm {
                south = min(south, u.latitude); north = max(north, u.latitude)
                west = min(west, u.longitude); east = max(east, u.longitude)
            }
        }
        focusMargenMinimo = 0.0008
        if south == north && west == east {
            // Una sola piedra: no hay caja que encuadrar, se centra en ella con un zoom cómodo.
            focusFit = []
            focusCoord = CLLocationCoordinate2D(latitude: south, longitude: west)
            focusZoom = 17.0
        } else {
            focusFit = [CLLocationCoordinate2D(latitude: south, longitude: west),
                        CLLocationCoordinate2D(latitude: north, longitude: east)]
        }
        focusToken += 1
        return true
    }

    /// Encuadre de entrada: las piedras; si la escuela aún no tiene ninguna, todos los marcadores (como antes).
    /// Una sola vez por visita (al volver de pantalla completa se respeta la cámara del usuario).
    func frameInitially() {
        guard !didInitialFit, !vm.blocks.isEmpty else { return }
        didInitialFit = true
        if !frameVisibleStones(includeUser: true) { recenterOnAllMarkers() }
    }

    /// Al cambiar un filtro: re-encuadra con animación las piedras que quedan. Si no queda ninguna, no mueve nada.
    func reframeAfterFilterChange() {
        guard didInitialFit else { return }
        frameVisibleStones()
    }

    /// Todos los marcadores de la escuela (la escuela, parkings, sectores y piedras): solo si no hay piedras.
    func recenterOnAllMarkers() {
        var coords = [CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)]
        coords += vm.blocks.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        focusMargenMinimo = 0.004
        if coords.count >= 2 { focusFit = coords } else {
            focusFit = []
            focusCoord = coords[0]
        }
        focusToken += 1
    }
}
