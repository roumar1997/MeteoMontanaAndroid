import SwiftUI
import Shared

// Cabecera de la ficha de escuela: una fila de iconos (solo icono, como los de la barra de pestañas) repartidos por
// todo el ancho: Volver · Chat · Aquí · Llegar · Enviar · Favorita · Guardar · Oruga. Se pinta DENTRO de la fila de
// `SchoolPresenceRow` (que añade el chat y «Aquí»). Espejo de `DetailHeaderActions.kt` en Android.
//
// Cabecera propia (no la barra nativa de iOS): con varias acciones a la derecha, la barra nativa podía comprimir
// el título hasta hacerlo desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro" (Rodrigo, con
// capturas, 2026-08-21).

/// Icono de volver, el primero de la fila. El nombre de la escuela va en su propia fila fija, bajo los iconos.
struct SchoolDetailHeaderTitle: View {
    let onBack: () -> Void
    let width: CGFloat

    var body: some View {
        IconAction(symbol: "chevron.left", a11y: L("common_back"), action: onBack)
            .frame(width: width)
    }
}

/// Acciones de la escuela, solo icono: cómo llegar, enviar, favorita, guardar offline y oruga (la procesionaria:
/// siempre visible, se puede avisar en cualquier escuela; en rojo si hay alerta). La ayuda y la cámara van junto al
/// nombre de la escuela. Cada icono mide `tile` de ancho (lo reparte `SchoolPresenceRow`).
struct SchoolDetailHeaderActions: View {
    @ObservedObject var vm: SchoolDetailViewModel
    let school: School
    let tile: CGFloat
    let onProcessionary: () -> Void

    private func openDirections() {
        let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
        let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
        UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
    }

    var body: some View {
        IconAction(symbol: "arrow.triangle.turn.up.right.diamond", a11y: L("bar_directions")) { openDirections() }
            .frame(width: tile)
        ShareLink(item: vm.forecast.map { conditionsShareSummary($0) } ?? school.name) {
            IconActionFace(symbol: "square.and.arrow.up")
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("bar_share"))
        .frame(width: tile)
        IconAction(symbol: vm.isFavorite ? "star.fill" : "star", a11y: L("bar_favorite"), active: vm.isFavorite) {
            vm.toggleFavorite(schoolId: school.id)
        }
        .frame(width: tile)
        // Guardar offline: con el progreso (rueda) mientras baja, y encendido cuando ya está guardada.
        IconAction(a11y: L("bar_save"), active: vm.isSaved, action: {
            Task {
                if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                else { await vm.saveOffline(school: school) }
            }
        }, glyph: { tint in
            if vm.savingOffline {
                ProgressView().tint(tint)
            } else {
                IconSymbol(name: vm.isSaved ? "checkmark" : "arrow.down.to.line", tint: tint)
            }
        })
        .frame(width: tile)
        .disabled(vm.savingOffline)
        // Oruga (procesionaria): siempre visible. Roja si hay alerta activa.
        IconAction(a11y: L("bar_caterpillar"), active: vm.processionaryAlertActive,
                   activeColor: Cumbre.bad, action: onProcessionary, glyph: { tint in
            ProcessionaryIcon(tint: tint).frame(width: 24, height: 24)
        })
        .frame(width: tile)
    }
}
