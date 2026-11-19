import SwiftUI
import Shared

/// Estado de la tarjeta «Rocódromo» del perfil. Los números salen de `shared` (`GetGymStatsUseCase`): primero lo que hay
/// en el móvil (sale al instante y sin red) y después, si el servidor ya tiene las rutas de entrenos, lo mezcla.
/// Espejo de `GymStatsViewModel` de Android.
@MainActor
final class GymStatsViewModel: ObservableObject {
    @Published private(set) var stats: GymStats
    @Published private(set) var topGymName: String?

    private let c = AppDependencies.shared.container.gyms

    init() {
        let cached = AppDependencies.shared.container.gyms.getGymStats.cached()
        stats = cached
        topGymName = Self.name(of: cached, in: AppDependencies.shared.container.gyms.getCachedGyms.invoke())
    }

    func refresh() async {
        let cached = c.getGymStats.cached()
        stats = cached
        topGymName = Self.name(of: cached, in: c.getCachedGyms.invoke())
        guard let fresh = try? await c.getGymStats.refresh() else { return }
        stats = fresh
        topGymName = Self.name(of: fresh, in: c.getCachedGyms.invoke())
    }

    private static func name(of stats: GymStats, in gyms: [Gym]) -> String? {
        guard let id = stats.topGymId?.int64Value else { return nil }
        return gyms.first { $0.id == id }?.name
    }
}

/// Tarjeta «Rocódromo» del perfil propio: semana, mes, año, el más visitado y la racha en semanas. Va detrás de
/// `gyms_enabled` (lo decide quien la pone). Funciona sin red. Espejo de `GymStatsCard` de Android.
struct GymStatsCard: View {
    @StateObject private var vm = GymStatsViewModel()

    var body: some View {
        let s = vm.stats
        VStack(alignment: .leading, spacing: 8) {
            Text(L("gym_stats_title").uppercased()).eyebrow(Cumbre.terra)
            if s.isEmpty {
                Text(L("gym_stats_empty")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                HStack(spacing: 8) {
                    cell("\(s.week)", L("gym_stats_week"))
                    cell("\(s.month)", L("gym_stats_month"))
                    cell("\(s.year)", L("gym_stats_year"))
                }
                if let name = vm.topGymName {
                    infoLine(L("gym_stats_top"), L("gym_stats_top_value", name, s.topGymVisits))
                }
                if s.streakWeeks > 0 {
                    infoLine(L("gym_stats_streak"),
                             s.streakWeeks == 1 ? L("gym_stats_streak_one") : L("gym_stats_streak_many", s.streakWeeks))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task { await vm.refresh() }
    }

    private func cell(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1).minimumScaleFactor(0.6)
            Text(label.uppercased()).font(Cumbre.mono(8.5, .bold)).tracking(0.6).foregroundStyle(Cumbre.ink3)
                .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 9).padding(.horizontal, 4)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
        .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func infoLine(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label.uppercased()).eyebrow(Cumbre.ink2)
            Text(value).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
