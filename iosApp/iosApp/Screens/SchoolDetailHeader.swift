import SwiftUI
import Shared

// Cabecera de la ficha de escuela: «Volver» a la izquierda y, a la derecha, círculos con su nombre debajo (Chat · Aquí ·
// Llegar · Compartir · Favorita · Guardar · Oruga). Se pinta DENTRO de la fila de `SchoolPresenceRow` (que añade el chat
// y «Aquí»), así toda la cabecera ocupa una sola fila. Espejo de `DetailHeaderActions.kt` en Android.
//
// Cabecera propia (no la barra nativa de iOS): con varias acciones a la derecha, la barra nativa podía comprimir
// el título hasta hacerlo desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro" (Rodrigo, con
// capturas, 2026-08-21).

/// Solo "volver", en círculo suelto sin nombre. El nombre de la escuela va como título al principio del contenido
/// (Álvaro, 2026-10-14) y la fila se queda para las acciones.
struct SchoolDetailHeaderTitle: View {
    let onBack: () -> Void

    var body: some View {
        ActionCircle(label: nil, symbol: "chevron.left", a11y: L("common_back"), action: onBack)
    }
}

/// Acciones de la escuela, todas a la vista y cada una con su nombre: cómo llegar, compartir, favorita, guardar offline
/// y oruga (la procesionaria; en rojo si hay alerta). La ayuda y la cámara van junto al nombre de la escuela.
struct SchoolDetailHeaderActions: View {
    @ObservedObject var vm: SchoolDetailViewModel
    let school: School
    let onProcessionary: () -> Void

    private func openDirections() {
        let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
        let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
        UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 2) {
            ActionCircle(label: L("bar_directions"), symbol: "arrow.triangle.turn.up.right.diamond.fill",
                         a11y: L("bar_directions")) { openDirections() }
            if let f = vm.forecast {
                ShareLink(item: conditionsShareSummary(f)) {
                    ActionCircleFace(label: L("bar_share"), symbol: "square.and.arrow.up.fill")
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("bar_share"))
            }
            ActionCircle(label: L("bar_favorite"), symbol: "star.fill", a11y: L("bar_favorite"), active: vm.isFavorite) {
                vm.toggleFavorite(schoolId: school.id)
            }
            // Guardar offline: con el progreso (rueda) mientras baja, y encendido cuando ya está guardada.
            ActionCircle(label: L("bar_save"), a11y: L("bar_save"), active: vm.isSaved, action: {
                Task {
                    if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                    else { await vm.saveOffline(school: school) }
                }
            }, glyph: { tint in
                if vm.savingOffline {
                    ProgressView().tint(tint)
                } else {
                    CircleSymbol(name: vm.isSaved ? "checkmark" : "arrow.down", tint: tint)
                }
            })
            .disabled(vm.savingOffline)
            // Oruga (procesionaria): siempre a la vista para poder avisar en cualquier escuela; roja si hay alerta.
            ActionCircle(label: L("bar_caterpillar"), a11y: L("bar_caterpillar"), active: vm.processionaryAlertActive,
                         activeColor: Cumbre.bad, action: onProcessionary, glyph: { tint in
                ProcessionaryIcon(tint: tint).frame(width: 22, height: 22)
            })
        }
    }
}
