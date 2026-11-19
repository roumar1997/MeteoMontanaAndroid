import SwiftUI
import CoreLocation
import Shared

/// Selector «Roca · Rocódromos · Muros gratis» de Escuelas. Las cápsulas pasan a otra línea si no caben
/// (pantalla estrecha o letra grande), en vez de cortar «MUROS GRATIS». Espejo de `SchoolsLayerPicker`.
struct SchoolsLayerPicker: View {
    @ObservedObject var vm: GymsViewModel

    var body: some View {
        FlowLayoutView(spacing: 8) {
            pill("gym_layer_rock", .rock)
            pill("gym_layer_gyms", .rocodromos)
            pill("gym_layer_free", .freeWalls)
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func pill(_ key: String, _ layer: SchoolsLayer) -> some View {
        Button { vm.setLayer(layer) } label: { GymChip(text: L(key).uppercased(), selected: vm.layer == layer) }
            .buttonStyle(.plain)
    }
}

/// Cuerpo de Escuelas cuando el selector está en «Rocódromos» o «Muros gratis» (sustituye a la lista de escuelas y
/// a su nota del tiempo): buscador, filtros en cápsulas, contador, alternar Mapa/Lista y el contenido.
struct GymsLayerView: View {
    @ObservedObject var vm: GymsViewModel
    let userLat: Double?
    let userLon: Double?
    let needsLocationPermission: Bool
    let onRequestLocation: () -> Void
    let onOpenGym: (Int64) -> Void
    @State private var addWallOpen = false

    var body: some View {
        VStack(spacing: 0) {
            GymSearchField(text: queryBinding)
            GymFilterBar(vm: vm)
            counterRow
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

    private var counterRow: some View {
        HStack(spacing: 8) {
            Text(L(vm.isFreeLayer ? "gym_count_free" : "gym_count_gyms", vm.items.count).uppercased())
                .eyebrow(Cumbre.ink2).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
            Button { vm.viewKind = .map } label: { GymChip(text: L("gym_view_map"), selected: vm.viewKind == .map) }
                .buttonStyle(.plain)
            Button { vm.viewKind = .list } label: { GymChip(text: L("gym_view_list"), selected: vm.viewKind == .list) }
                .buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
    }

    /// Carga, error sin copia, lista vacía, mapa con su tarjeta o la lista.
    @ViewBuilder private var content: some View {
        if vm.loading && vm.all.isEmpty {
            ProgressView().frame(maxWidth: .infinity, minHeight: 240)
        } else if vm.loadFailed && vm.all.isEmpty {
            message(L("gym_load_error"), action: L("gym_retry")) { Task { await vm.refresh() } }
        } else if vm.viewKind == .map {
            GymsMapView(vm: vm, userLat: userLat, userLon: userLon, needsLocationPermission: needsLocationPermission,
                        onRequestLocation: onRequestLocation, onOpenGym: onOpenGym, onAddWall: { addWallOpen = true })
        } else if vm.items.isEmpty {
            message(L("gym_empty"), action: L("gym_clear_filters")) { vm.clearFilters() }
        } else {
            LazyVStack(spacing: 0) {
                ForEach(vm.items, id: \.gym.id) { item in
                    Button { onOpenGym(item.gym.id) } label: {
                        GymListRow(item: item, isFavorite: vm.favorites.contains(item.gym.id))
                    }
                    .buttonStyle(.plain)
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

/// Filtros en cápsulas: Gratis, Bloque, Cuerda, Abierto ahora, Favoritos, cadena y distancia (se desliza si no caben).
struct GymFilterBar: View {
    @ObservedObject var vm: GymsViewModel
    private static let distanceSteps: [Double] = [10, 25, 50, 100]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if !vm.isFreeLayer { toggle("gym_filter_free", \.onlyFree) }
                toggle("gym_filter_boulder", \.boulder)
                toggle("gym_filter_rope", \.rope)
                toggle("gym_filter_open_now", \.openNow)
                toggle("gym_filter_favorites", \.onlyFavorites)
                if !vm.chains.isEmpty { chainMenu }
                distanceMenu
            }
            .padding(.horizontal, 16)
        }
    }

    private func toggle(_ key: String, _ path: WritableKeyPath<GymFilterState, Bool>) -> some View {
        Button { vm.filter[keyPath: path].toggle() } label: {
            GymChip(text: L(key), selected: vm.filter[keyPath: path])
        }
        .buttonStyle(.plain)
    }

    private var chainMenu: some View {
        Menu {
            Button(L("gym_filter_chain_all")) { vm.filter.chain = nil }
            ForEach(vm.chains, id: \.self) { chain in Button(chain) { vm.filter.chain = chain } }
        } label: {
            GymChip(text: vm.filter.chain ?? L("gym_filter_chain"), selected: vm.filter.chain != nil, chevron: true)
        }
    }

    private var distanceMenu: some View {
        Menu {
            Button(L("gym_filter_distance_any_option")) { vm.filter.maxDistanceKm = nil }
            ForEach(Self.distanceSteps, id: \.self) { km in
                Button(L("gym_filter_distance", Int(km))) { vm.filter.maxDistanceKm = km }
            }
        } label: {
            GymChip(text: vm.filter.maxDistanceKm.map { L("gym_filter_distance", Int($0)) } ?? L("gym_filter_distance_any"),
                    selected: vm.filter.maxDistanceKm != nil, chevron: true)
        }
    }
}
