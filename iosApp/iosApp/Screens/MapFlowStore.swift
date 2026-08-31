import SwiftUI
import CoreLocation

/// PUENTE mapa ↔ flujo de proponer/corregir — espejo de `ProposalMapBridge`
/// (MapFlowState.kt, Android). Antes eran 12 `@State` sueltos repartidos por
/// SchoolMapSection; agrupados aquí, el flujo tiene nombre, sus resets son un
/// método (no bloques repetidos) y la sección queda con el estado de cámara/UI
/// que sí le pertenece.
@MainActor
final class MapProposalFlowStore: ObservableObject {
    // Proponer mejora: tap en el mapa fija coords → formulario → envío.
    @Published var waitingTap = false
    @Published var showTypePicker = false
    @Published var formCoord: CLLocationCoordinate2D?
    @Published var proposeType = "PARKING"
    @Published var showSuccess = false
    @Published var boulderCoord: CLLocationCoordinate2D?
    // Corregir posición: seleccionar un marcador y fijar su nueva posición.
    @Published var correctionMode = false
    @Published var corrActive = false        // ya hay un target elegido
    @Published var corrTargetId: String?     // nil + corrActive ⇒ la escuela
    @Published var corrTargetName = ""
    @Published var corrOld: CLLocationCoordinate2D?
    @Published var corrNew: CLLocationCoordinate2D?

    /// Arranca el modo corrección (desde el selector "¿Falta algo?").
    func startCorrection() {
        correctionMode = true
        corrActive = false
        corrNew = nil
    }

    /// Cancela/termina la corrección: todo su estado a cero.
    func resetCorrection() {
        correctionMode = false
        corrActive = false
        corrTargetId = nil
        corrNew = nil
    }
}
