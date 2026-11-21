import SwiftUI
import Shared

@MainActor
final class GymsNearbyViewModel: ObservableObject {
    @Published private(set) var near: [GymNear] = []
    private var loadedFor: String?
    private let c = AppDependencies.shared.container.gyms

    /// Plan B: roca mojada + interruptor encendido (el criterio de roca mojada es el de siempre, sin umbrales nuevos).
    func shouldSuggest(_ current: Current, gymsEnabled: Bool) -> Bool {
        c.shouldSuggestGyms.invoke(current: current, gymsEnabled: gymsEnabled)
    }

    func load(lat: Double, lon: Double) async {
        let key = "\(lat),\(lon)"
        guard loadedFor != key else { return }
        loadedFor = key
        if let list = try? await c.findGymsNear.executeNearby(lat: lat, lon: lon) {
            near = list
        }
    }
}

/// Plan B de la ficha de una escuela: si la roca está mojada, «Rocódromos cerca» con los más próximos (30 km, 5).
/// No sale con el interruptor `gyms_enabled` apagado ni mientras no haya resultados. Espejo de `GymsNearbyBanner.kt`.
struct GymsNearbyBanner: View {
    let lat: Double
    let lon: Double
    let current: Current
    @StateObject private var vm = GymsNearbyViewModel()
    @ObservedObject private var flags = FeatureFlagsModel.shared
    @State private var expanded = false

    private var show: Bool { vm.shouldSuggest(current, gymsEnabled: flags.flags.isGymsEnabled) }

    var body: some View {
        Group {
            if show && !vm.near.isEmpty { card }
        }
        .task(id: show) { if show { await vm.load(lat: lat, lon: lon) } }
    }

    /// Una línea pequeña plegable «Rocódromos cerca ⌄»; al abrirla, los más próximos.
    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { expanded.toggle() } label: {
                HStack(spacing: 4) {
                    Text(L("gym_nearby_title")).font(.system(size: 12, weight: .medium)).foregroundStyle(Cumbre.ink2)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
                }
                .padding(.vertical, 6).padding(.horizontal, 4).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if expanded {
                ForEach(vm.near, id: \.id) { gym in
                    NavigationLink {
                        GymDetailView(gymId: gym.id)
                    } label: {
                        HStack(spacing: 10) {
                            GymKindBadge(kind: gym.kind, size: 28)
                            Text(gym.name).font(Cumbre.serif(14, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(GymPresentation.distanceText(gym.distanceKm) ?? "").font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                        }
                        .padding(.vertical, 4).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 2)
    }
}

/// «Hoy la roca está mojada cerca de ti · ¿Rocódromo?»: al tocarlo, Escuelas pasa a la capa de rocódromos.
struct WetRockBanner: View {
    let action: () -> Void
    var body: some View {
        let rain = Cumbre.dyn(0x2563C7, 0x7BA3EE)
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "drop.fill").font(.system(size: 17)).foregroundStyle(rain)
                Text(L("gym_wet_banner")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(rain)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(rain.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(rain.opacity(0.5), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
