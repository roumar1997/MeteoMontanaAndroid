import SwiftUI
import Shared

// Cabecera de la ficha de escuela (volver + nombre | favorita, offline, menú). Se pinta DENTRO de la fila de
// `SchoolPresenceRow` (que añade el chat y el botón "Aquí"), así toda la cabecera ocupa una sola fila.
//
// Cabecera propia (no la barra nativa de iOS): con varias acciones a la derecha, la barra nativa podía comprimir
// el título hasta hacerlo desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro" (Rodrigo, con
// capturas, 2026-08-21). Android ya resolvía esto con una fila propia donde el título se queda con el hueco
// sobrante (`weight(1f)` + ellipsis) — mismo patrón aquí.

/// Volver + nombre. El nombre puede ocupar dos líneas o encogerse un poco antes de recortarse.
struct SchoolDetailHeaderTitle: View {
    let name: String
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

            Text(name)
                .font(.system(size: 19, weight: .semibold))
                .lineLimit(2)
                .minimumScaleFactor(0.75)
                .foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Acciones de la escuela: lo que más se usa a la vista (favorita, offline) y el resto en "⋯".
/// La procesionaria sale fuera del menú SOLO cuando hay alerta activa (no debe esconderse).
struct SchoolDetailHeaderActions: View {
    @ObservedObject var vm: SchoolDetailViewModel
    let school: School
    let onHelp: () -> Void
    let onProcessionary: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            if vm.processionaryAlertActive {
                Button(action: onProcessionary) {
                    ProcessionaryIcon(tint: .white)
                        .frame(width: 15, height: 15)
                        .frame(width: 26, height: 26)
                        .background(Cumbre.bad, in: Circle())
                }
            }
            Button { vm.toggleFavorite(schoolId: school.id) } label: {
                Image(systemName: vm.isFavorite ? "star.fill" : "star")
                    .foregroundStyle(vm.isFavorite ? Cumbre.terra : Cumbre.ink3)
                    .frame(width: 34, height: 34)
            }
            if vm.savingOffline {
                ProgressView().frame(width: 34, height: 34)
            } else {
                Button {
                    Task {
                        if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                        else { await vm.saveOffline(school: school) }
                    }
                } label: {
                    Image(systemName: vm.isSaved ? "arrow.down.circle.fill" : "arrow.down.circle")
                        .foregroundStyle(vm.isSaved ? Cumbre.terra : Cumbre.ink3)
                        .frame(width: 34, height: 34)
                }
            }
            Menu {
                Button(action: onHelp) { Label(L("Ayuda"), systemImage: "questionmark.circle") }
                Button(action: onProcessionary) { Label(L("Procesionaria"), systemImage: "exclamationmark.triangle") }
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: { Label(L("Cómo llegar"), systemImage: "arrow.triangle.turn.up.right.diamond") }
                if let f = vm.forecast {
                    ShareLink(item: conditionsShareSummary(f)) { Label(L("Compartir"), systemImage: "square.and.arrow.up") }
                }
            } label: {
                Image(systemName: "ellipsis").foregroundStyle(Cumbre.ink3)
                    .frame(width: 34, height: 34)
            }
        }
        .padding(.horizontal, 2)
        .background(Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
    }
}
