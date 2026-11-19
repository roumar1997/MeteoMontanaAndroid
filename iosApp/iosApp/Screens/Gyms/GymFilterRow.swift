import SwiftUI
import Shared

/// Dos segmentos de la fila entera con el mismo ancho (el control de «Escuelas | Vías/Bloques»), el primero o el
/// segundo elegido. Sirve para «Roca | Rocódromos» y «Mapa | Lista». Espejo de `SegmentedPair` de Android.
struct GymSegmentedPair: View {
    let first: String
    let second: String
    let firstSelected: Bool
    let onFirst: () -> Void
    let onSecond: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            segment(first, active: firstSelected, action: onFirst)
            segment(second, active: !firstSelected, action: onSecond)
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.18), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        .padding(.horizontal, 12)
    }

    private func segment(_ text: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(Cumbre.mono(12, .bold)).lineLimit(1).minimumScaleFactor(0.7)
                .foregroundStyle(active ? .white : Cumbre.ink2)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(active ? Cumbre.terra : Color.clear, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius - 2))
        }
        .buttonStyle(.plain)
    }
}

/// Fila plegable «FILTROS (n) · N rocódromos ⌄» + BORRAR + «★ FAVORITOS»: la misma que la de Escuelas (sin el chip de
/// días). Con un filtro activo sale BORRAR y la fila superaba el ancho del iPhone; `ViewThatFits` elige la variante que
/// cabe (completa → sin contador → BORRAR solo con ✕ → deslizable). Espejo de `FiltersToggleRow` de Android.
struct GymFilterRow: View {
    @ObservedObject var vm: GymsViewModel
    @Binding var open: Bool

    private var activeCount: Int { vm.filter.activeCount }

    var body: some View {
        ViewThatFits(in: .horizontal) {
            content(showCount: true, compactClear: false)
            content(showCount: false, compactClear: false)
            content(showCount: false, compactClear: true)
            ScrollView(.horizontal, showsIndicators: false) { content(showCount: false, compactClear: true) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
    }

    private func content(showCount: Bool, compactClear: Bool) -> some View {
        HStack(spacing: 8) {
            Button { withAnimation(.easeInOut(duration: 0.2)) { open.toggle() } } label: {
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "slider.horizontal.3").font(.system(size: 12, weight: .semibold))
                        Text(activeCount > 0 ? L("FILTROS (%@)", activeCount) : L("FILTROS")).font(Cumbre.mono(11, .bold)).tracking(1)
                    }
                    .foregroundStyle(activeCount > 0 ? Color.white : Cumbre.ink2)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(activeCount > 0 ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                    .overlay(Capsule().stroke(activeCount > 0 ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                    .lineLimit(1).fixedSize()
                    if showCount {
                        Text(L("gym_count_gyms", vm.items.count)).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            .lineLimit(1).fixedSize()
                    }
                    Spacer(minLength: 0)
                    Image(systemName: open ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            clearButton(compact: compactClear)
            favoritesChip
        }
    }

    /// «BORRAR»: aparece solo con algún filtro movido y lo deja todo como de base (50 km, sin chips).
    @ViewBuilder private func clearButton(compact: Bool) -> some View {
        if activeCount > 0 {
            Button { withAnimation(.easeInOut(duration: 0.2)) { vm.clearFilters(); open = false } } label: {
                HStack(spacing: 4) {
                    Image(systemName: "xmark").font(.system(size: 9, weight: .bold))
                    if !compact { Text(L("BORRAR")).font(Cumbre.mono(10.5, .bold)).tracking(0.6) }
                }
                .foregroundStyle(Cumbre.terra)
                .padding(.horizontal, 10).padding(.vertical, 7)
                .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1))
                .lineLimit(1).fixedSize()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("BORRAR"))
        }
    }

    /// «★ FAVORITOS»: filtra la lista y el mapa a tus rocódromos favoritos.
    private var favoritesChip: some View {
        let on = vm.filter.onlyFavorites
        return Button { withAnimation(.easeInOut(duration: 0.2)) { vm.filter.onlyFavorites.toggle() } } label: {
            HStack(spacing: 4) {
                Image(systemName: on ? "star.fill" : "star").font(.system(size: 10, weight: .bold))
                Text(L("gym_favs_chip")).font(Cumbre.mono(10.5, .bold)).tracking(0.6)
            }
            .foregroundStyle(on ? Color.white : Cumbre.ink2)
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
            .lineLimit(1).fixedSize()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("gym_favs_chip_desc"))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Panel que se despliega bajo «FILTROS (n)», con las mismas secciones y chips que el de Escuelas: DISTANCIA (mismos
/// valores), MODALIDAD, MOSTRAR (gratis, de calle, abierto ahora, cerrados) y CADENA. El orden es por cercanía.
struct GymFiltersPanel: View {
    @ObservedObject var vm: GymsViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            section(L("DISTANCIA")) {
                chipRow(SchoolListViewModel.distanceOptions, id: { $0.map { String(Int($0)) } ?? "all" },
                        isSel: { $0 == vm.filter.maxDistanceKm },
                        label: { $0 == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int($0!)) km" }) {
                    vm.filter.maxDistanceKm = $0
                }
            }
            section(L("gym_section_discipline").uppercased()) {
                chipRow([GymToggle.boulder, .rope], id: { $0.rawValue }, isSel: { vm.filter[keyPath: $0.path] },
                        label: { L($0.key) }) { vm.filter[keyPath: $0.path].toggle() }
            }
            section(L("gym_filter_show").uppercased()) {
                chipRow([GymToggle.free, .street, .openNow, .closed], id: { $0.rawValue },
                        isSel: { vm.filter[keyPath: $0.path] }, label: { L($0.key) }) { vm.filter[keyPath: $0.path].toggle() }
            }
            if !vm.chains.isEmpty {
                section(L("gym_filter_chain").uppercased()) {
                    chipRow([String?.none] + vm.chains.map { Optional($0) }, id: { $0 ?? "all" },
                            isSel: { ($0 ?? "").lowercased() == (vm.filter.chain ?? "").lowercased() },
                            label: { $0 ?? L("gym_filter_chain_all") }) { vm.filter.chain = $0 }
                }
            }
        }
        .padding(.vertical, 8)
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).eyebrow().padding(.horizontal, 12)
            content()
        }
    }

    /// Fila de chips que se desliza si no caben (nunca se cortan ni ensanchan la pantalla).
    private func chipRow<T>(_ items: [T], id: @escaping (T) -> String, isSel: @escaping (T) -> Bool,
                            label: @escaping (T) -> String, onPick: @escaping (T) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button { onPick(item) } label: { chip(label(item), active: isSel(item)) }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func chip(_ text: String, active: Bool) -> some View {
        Text(text).font(Cumbre.mono(11, .bold)).tracking(0.8).lineLimit(1)
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? Cumbre.terra : Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Los chips de dos estados del panel: su texto y el campo de `GymFilterState` que mueven.
private enum GymToggle: String {
    case boulder, rope, free, street, openNow, closed

    var key: String {
        switch self {
        case .boulder: return "gym_filter_boulder"
        case .rope: return "gym_filter_rope"
        case .free: return "gym_filter_free"
        case .street: return "gym_filter_street"
        case .openNow: return "gym_filter_open_now"
        case .closed: return "gym_filter_closed"
        }
    }

    var path: WritableKeyPath<GymFilterState, Bool> {
        switch self {
        case .boulder: return \.boulder
        case .rope: return \.rope
        case .free: return \.onlyFree
        case .street: return \.onlyStreet
        case .openNow: return \.openNow
        case .closed: return \.showClosed
        }
    }
}
