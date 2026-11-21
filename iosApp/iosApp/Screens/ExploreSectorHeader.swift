import SwiftUI
import Shared

/// «12 bloques» si todo son bloques, «12 vías» si todo son vías y «12 vías/bloques» si hay mezcla (shared: ExploreSectors).
func exploreCountText(_ count: Int, boulders: Bool, routes: Bool) -> String {
    if boulders { return count == 1 ? L("explore_group_count_boulders_one") : L("explore_group_count_boulders_other", count) }
    if routes { return count == 1 ? L("explore_group_count_routes_one") : L("explore_group_count_routes_other", count) }
    return count == 1 ? L("explore_group_count_one") : L("explore_group_count_other", count)
}

/// Cabecera plegable de un SECTOR dentro de una escuela: tarjeta propia (radio 16, borde, sombra suave) con nombre,
/// contador en píldora y chevron en un círculo que se rellena al abrir. Espejo de `ExploreSectorHeader` (Android).
struct ExploreSectorHeader: View {
    let name: String?
    let count: Int
    let boulders: Bool
    let routes: Bool
    let expanded: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 8) {
                Text(name ?? L("explore_sector_none")).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(exploreCountText(count, boulders: boulders, routes: routes)).font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Cumbre.ink2).padding(.horizontal, 9).padding(.vertical, 3).background(Cumbre.paper2, in: Capsule())
                Circle().fill(expanded ? Cumbre.terraFill : Cumbre.paper2).frame(width: 28, height: 28)
                    .overlay(Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 12, weight: .bold))
                        .foregroundStyle(expanded ? .white : Cumbre.ink2))
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(expanded ? Cumbre.terra.opacity(0.5) : Cumbre.rule, lineWidth: 1))
            .shadow(color: .black.opacity(expanded ? 0 : 0.06), radius: 2, y: 1)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16).padding(.vertical, 4)
    }
}
