import SwiftUI
import Shared

// Cabecera de la ficha de escuela: «Volver» pequeño a la izquierda y, a su lado, baldosas con su nombre debajo (Chat ·
// Aquí · Llegar · Enviar · Favorita · Guardar · Oruga si hay aviso). Se pinta DENTRO de la fila de `SchoolPresenceRow`
// (que añade el chat y «Aquí»), así toda la cabecera ocupa una sola fila. Espejo de `DetailHeaderActions.kt` en Android.
//
// Cabecera propia (no la barra nativa de iOS): con varias acciones a la derecha, la barra nativa podía comprimir
// el título hasta hacerlo desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro" (Rodrigo, con
// capturas, 2026-08-21).

/// Solo "volver", en baldosa pequeña sin nombre. El nombre de la escuela va en su propia fila fija, bajo las baldosas.
struct SchoolDetailHeaderTitle: View {
    let onBack: () -> Void

    var body: some View {
        ActionTile(label: nil, symbol: "chevron.left", a11y: L("common_back"), action: onBack)
            .frame(width: 38)
    }
}

/// Acciones de la escuela, todas a la vista y cada una con su nombre: cómo llegar, enviar, favorita, guardar offline
/// y oruga (la procesionaria; en rojo si hay alerta; solo aparece si hay procesionaria activa o aviso). La ayuda y la
/// cámara van junto al nombre de la escuela. Cada baldosa mide `tile` de ancho (lo reparte `SchoolPresenceRow`).
struct SchoolDetailHeaderActions: View {
    @ObservedObject var vm: SchoolDetailViewModel
    let school: School
    let tile: CGFloat
    let onProcessionary: () -> Void

    /// ¿Se pinta la oruga? Debe coincidir con el recuento de baldosas que se le da a `SchoolPresenceRow`.
    static func showsCaterpillar(_ vm: SchoolDetailViewModel) -> Bool {
        vm.processionaryAlertActive || vm.processionaryActiveNowSet
    }

    private func openDirections() {
        let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
        let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
        UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
    }

    var body: some View {
        ActionTile(label: L("bar_directions"), symbol: "arrow.triangle.turn.up.right.diamond",
                   a11y: L("bar_directions")) { openDirections() }
            .frame(width: tile)
        ShareLink(item: vm.forecast.map { conditionsShareSummary($0) } ?? school.name) {
            ActionTileFace(label: L("bar_share"), symbol: "square.and.arrow.up")
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("bar_share"))
        .frame(width: tile)
        ActionTile(label: L("bar_favorite"), symbol: vm.isFavorite ? "star.fill" : "star",
                   a11y: L("bar_favorite"), active: vm.isFavorite) {
            vm.toggleFavorite(schoolId: school.id)
        }
        .frame(width: tile)
        // Guardar offline: con el progreso (rueda) mientras baja, y encendido cuando ya está guardada.
        ActionTile(label: L("bar_save"), a11y: L("bar_save"), active: vm.isSaved, action: {
            Task {
                if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                else { await vm.saveOffline(school: school) }
            }
        }, glyph: { tint in
            if vm.savingOffline {
                ProgressView().tint(tint)
            } else {
                TileSymbol(name: vm.isSaved ? "checkmark" : "arrow.down.to.line", tint: tint)
            }
        })
        .frame(width: tile)
        .disabled(vm.savingOffline)
        // Oruga (procesionaria): solo si hay procesionaria activa o aviso; si no, no ocupa hueco. Roja si hay alerta.
        if Self.showsCaterpillar(vm) {
            ActionTile(label: L("bar_caterpillar"), a11y: L("bar_caterpillar"), active: vm.processionaryAlertActive,
                       activeColor: Cumbre.bad, action: onProcessionary, glyph: { tint in
                ProcessionaryIcon(tint: tint).frame(width: 22, height: 22)
            })
            .frame(width: tile)
        }
    }
}
