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

    private var show: Bool { vm.shouldSuggest(current, gymsEnabled: flags.flags.isGymsEnabled) }

    var body: some View {
        Group {
            if show && !vm.near.isEmpty { card }
        }
        .task(id: show) { if show { await vm.load(lat: lat, lon: lon) } }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("gym_nearby_title").uppercased()).eyebrow(Cumbre.terra)
            Text(L("gym_nearby_subtitle")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            ForEach(vm.near, id: \.id) { gym in
                NavigationLink {
                    GymDetailView(gymId: gym.id)
                } label: {
                    HStack(spacing: 10) {
                        GymKindBadge(kind: gym.kind, size: 34)
                        Text(gym.name).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(GymPresentation.distanceText(gym.distanceKm) ?? "")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    }
                    .padding(.vertical, 4)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
        .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 12).padding(.vertical, 6)
    }
}
