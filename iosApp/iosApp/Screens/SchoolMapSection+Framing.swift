import SwiftUI
import Shared
import CoreLocation

// Encuadre del mapa de una escuela: SOLO las piedras (nunca parkings, sectores ni aproximaciones), al entrar y cada
// vez que cambia un filtro. Qué puntos cuentan lo decide shared (`MapGeometry.boundsForVisibleBlocks`), igual que en
// Android (SchoolMapCamera.kt), para que las dos apps encuadren lo mismo.

extension SchoolMapSection {
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
    func frameVisibleStones() -> Bool {
        guard let b = MapGeometry.shared.boundsForVisibleBlocks(blocks: vm.blocks, dimmedIds: dimmedStoneIds) else {
            return false
        }
        focusMargenMinimo = 0.0008
        if b.isDegenerate {
            // Una sola piedra: no hay caja que encuadrar, se centra en ella con un zoom cómodo.
            focusFit = []
            focusCoord = CLLocationCoordinate2D(latitude: b.south, longitude: b.west)
            focusZoom = 17.0
        } else {
            focusFit = [CLLocationCoordinate2D(latitude: b.south, longitude: b.west),
                        CLLocationCoordinate2D(latitude: b.north, longitude: b.east)]
        }
        focusToken += 1
        return true
    }

    /// Encuadre de entrada: las piedras; si la escuela aún no tiene ninguna, todos los marcadores (como antes).
    /// Una sola vez por visita (al volver de pantalla completa se respeta la cámara del usuario).
    func frameInitially() {
        guard !didInitialFit, !vm.blocks.isEmpty else { return }
        didInitialFit = true
        if !frameVisibleStones() { recenterOnAllMarkers() }
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
