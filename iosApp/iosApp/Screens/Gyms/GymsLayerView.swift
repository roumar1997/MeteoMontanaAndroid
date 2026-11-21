import SwiftUI
import CoreLocation
import Shared

/// Selector «Roca | Rocódromos» de Escuelas: dos segmentos que ocupan toda la fila, con el mismo control que
/// «Escuelas | Vías/Bloques». Espejo de `SchoolsLayerPicker`.
struct SchoolsLayerPicker: View {
    @ObservedObject var vm: GymsViewModel

    var body: some View {
        GymSegmentedPair(
            first: L("gym_layer_rock").uppercased(), second: L("gym_layer_gyms").uppercased(),
            firstSelected: vm.layer == .rock,
            onFirst: { vm.setLayer(.rock) }, onSecond: { vm.setLayer(.rocodromos) }
        )
        .padding(.vertical, 4)
    }
}

/// Cuerpo de Escuelas cuando el selector está en «Rocódromos» (sustituye a la lista de escuelas y a su nota del
/// tiempo): buscador, «Mapa | Lista», fila plegable «FILTROS (n) · N rocódromos» con su panel y el contenido.
struct GymsLayerView: View {
    @ObservedObject var vm: GymsViewModel
    let userLat: Double?
    let userLon: Double?
    let needsLocationPermission: Bool
    let onRequestLocation: () -> Void
    let onOpenGym: (Int64) -> Void
    @State private var addWallOpen = false
    @State private var filtersOpen = false
    /// Igual que Escuelas: el mapa se abre y se cierra (cerrado al arrancar); debajo, siempre la lista por cercanía.
    @State private var mapOpen = false

    var body: some View {
        VStack(spacing: 0) {
            GymSearchField(text: queryBinding)
            if mapOpen {
                GymsMapView(vm: vm, userLat: userLat, userLon: userLon, needsLocationPermission: needsLocationPermission,
                            onRequestLocation: onRequestLocation, onOpenGym: onOpenGym, onAddWall: { addWallOpen = true })
            }
            GymMapToggleBar(open: $mapOpen).padding(.top, 4)
            GymFilterRow(vm: vm, open: $filtersOpen).padding(.top, 8).padding(.bottom, 8)
            if filtersOpen { GymFiltersPanel(vm: vm) { filtersOpen = false } }
            Divider().overlay(Cumbre.rule)
            content
        }
        .task(id: "\(userLat ?? 0),\(userLon ?? 0)") { vm.setOrigin(lat: userLat, lon: userLon) }
        .task { vm.ensureLoaded(); await vm.refreshFavorites() }
        .sheet(isPresented: $addWallOpen) {
            let center = vm.savedCenter
            GymNewSheet(lat: center?.latitude ?? userLat ?? 40.4168, lon: center?.longitude ?? userLon ?? -3.7038)
        }
    }

    private var queryBinding: Binding<String> {
        Binding(get: { vm.filter.query }, set: { vm.filter.query = $0 })
    }

    /// Carga, error sin copia, lista vacía, mapa con su tarjeta o la lista.
    @ViewBuilder private var content: some View {
        if vm.loading && vm.all.isEmpty {
            ProgressView().frame(maxWidth: .infinity, minHeight: 240)
        } else if vm.loadFailed && vm.all.isEmpty {
            message(L("gym_load_error"), action: L("gym_retry")) { Task { await vm.refresh() } }
        } else if vm.items.isEmpty {
            message(L("gym_empty"), action: L("gym_clear_filters")) { vm.clearFilters() }
        } else {
            LazyVStack(spacing: 0) {
                ForEach(vm.items, id: \.gym.id) { item in
                    GymListRow(item: item, isFavorite: vm.favorites.contains(item.gym.id),
                               onOpen: { onOpenGym(item.gym.id) }, onToggleFavorite: { vm.toggleFavorite(item.gym.id) })
                    Divider().overlay(Cumbre.rule)
                }
                GymDashedPill(text: L("gym_add_missing")) { addWallOpen = true }.padding(16)
            }
        }
    }

    private func message(_ text: String, action: String, onAction: @escaping () -> Void) -> some View {
        VStack(spacing: 12) {
            Text(text).foregroundStyle(Cumbre.ink2).multilineTextAlignment(.center)
            Button(action: onAction) { GymChip(text: action, selected: true) }.buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, minHeight: 240).padding(24)
    }
}

/// Buscador de la capa: nombre, ciudad, provincia y cadena (sin tildes, lo hace `GymFilterEngine`).
struct GymSearchField: View {
    @Binding var text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
            TextField(L("gym_search_hint"), text: $text)
                .foregroundStyle(Cumbre.ink)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .dismissKeyboardOnSubmit()
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3) }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

/// «VER MAPA / OCULTAR MAPA» a todo el ancho, como el de Escuelas (`MapToggleBar`).
struct GymMapToggleBar: View {
    @Binding var open: Bool
    var body: some View {
        Button { withAnimation { open.toggle() } } label: {
            HStack(spacing: 8) {
                Image(systemName: "map").font(.system(size: 14, weight: .semibold))
                Text(open ? L("OCULTAR MAPA") : L("VER MAPA")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                Spacer(minLength: 0)
                Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 11, weight: .bold))
            }
            .foregroundStyle(open ? Color.white : Cumbre.terra)
            .padding(.horizontal, 14).padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .background(open ? Cumbre.terra : Cumbre.terra.opacity(0.10), in: Capsule())
            .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 12)
    }
}
