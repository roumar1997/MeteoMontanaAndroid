import SwiftUI
import Shared

// Cabecera de la ficha de escuela (volver + nombre | favorita, offline, menú). Se pinta DENTRO de la fila de
// `SchoolPresenceRow` (que añade el chat y el botón "Aquí"), así toda la cabecera ocupa una sola fila.
//
// Cabecera propia (no la barra nativa de iOS): con varias acciones a la derecha, la barra nativa podía comprimir
// el título hasta hacerlo desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro" (Rodrigo, con
// capturas, 2026-08-21). Android ya resolvía esto con una fila propia donde el título se queda con el hueco
// sobrante (`weight(1f)` + ellipsis) — mismo patrón aquí.

/// Solo "volver". El nombre de la escuela ya no ocupa sitio fijo en la cabecera: va como título al principio del
/// contenido (Álvaro, 2026-10-14), y la fila se queda para los iconos.
struct SchoolDetailHeaderTitle: View {
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                    .frame(width: 36, height: 36)
            }
            .background(Cumbre.paper, in: Circle())
            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            Spacer(minLength: 0)
        }
    }
}

/// Acciones de la escuela: cómo llegar, compartir, favorita, offline y procesionaria, todas a la vista.
/// La ayuda ("? GUÍA") va junto al nombre de la escuela, no aquí.
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
        HStack(spacing: 0) {
            Button { openDirections() } label: {
                Image(systemName: "arrow.triangle.turn.up.right.diamond").foregroundStyle(Cumbre.ink3)
                    .frame(width: 29, height: 32)
            }
            if let f = vm.forecast {
                ShareLink(item: conditionsShareSummary(f)) {
                    Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink3)
                        .frame(width: 29, height: 32)
                }
            }
            Button { vm.toggleFavorite(schoolId: school.id) } label: {
                Image(systemName: vm.isFavorite ? "star.fill" : "star")
                    .foregroundStyle(vm.isFavorite ? Cumbre.terra : Cumbre.ink3)
                    .frame(width: 29, height: 32)
            }
            if vm.savingOffline {
                ProgressView().frame(width: 29, height: 32)
            } else {
                Button {
                    Task {
                        if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                        else { await vm.saveOffline(school: school) }
                    }
                } label: {
                    Image(systemName: vm.isSaved ? "arrow.down.circle.fill" : "arrow.down.circle")
                        .foregroundStyle(vm.isSaved ? Cumbre.terra : Cumbre.ink3)
                        .frame(width: 29, height: 32)
                }
            }
            // Procesionaria: siempre a la vista (antes iba tras un menú "⋯" con un solo elemento). Roja si hay alerta.
            Button(action: onProcessionary) {
                if vm.processionaryAlertActive {
                    ProcessionaryIcon(tint: .white)
                        .frame(width: 15, height: 15)
                        .frame(width: 26, height: 26)
                        .background(Cumbre.bad, in: Circle())
                        .frame(width: 29, height: 32)
                } else {
                    ProcessionaryIcon(tint: Cumbre.ink3)
                        .frame(width: 17, height: 17)
                        .frame(width: 29, height: 32)
                }
            }
            .accessibilityLabel(L("Procesionaria"))
        }
        .padding(.horizontal, 2)
        .background(Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
    }
}
