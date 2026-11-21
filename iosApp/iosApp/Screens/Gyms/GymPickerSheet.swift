import SwiftUI
import Shared

/// Un rocódromo del selector con lo que se enseña debajo del nombre (distancia, «tu rocódromo» o «hace 2 semanas»).
struct GymPickerEntry: Identifiable {
    let gym: Gym
    let distanceKm: Double?
    var daysAgo: Int?
    var isTop = false
    var id: Int64 { gym.id }
}

/// Selector de rocódromo para apuntar un entreno: favoritos, recientes y cercanos arriba; con texto, los que coinciden.
/// Espejo de `GymPickerViewModel` (Android); el filtro y el orden (favoritos primero) salen de shared.
@MainActor
final class GymPickerViewModel: ObservableObject {
    @Published var query = "" { didSet { recompute() } }
    @Published private(set) var results: [GymPickerEntry] = []
    @Published private(set) var favorites: [GymPickerEntry] = []
    @Published private(set) var recents: [GymPickerEntry] = []
    @Published private(set) var nearby: [GymPickerEntry] = []
    private let c = AppDependencies.shared.container.gyms
    private var all: [Gym]
    private var origin: GymOrigin?
    private var favoriteIds = Set<Int64>()

    init() {
        all = c.getCachedGyms.invoke()
        recompute()
        Task {
            if let list = try? await c.getGyms.execute() { all = list; recompute() }
            if let ids = try? await c.getGymFavorites.execute() { favoriteIds = Set(ids.map { $0.int64Value }); recompute() }
        }
    }

    func setOrigin(lat: Double, lon: Double) {
        origin = GymOrigin(lat: lat, lon: lon)
        recompute()
    }

    private func recompute() {
        let f = GymFilters(onlyFree: false, boulder: false, rope: false, openNow: false, onlyFavorites: false,
                           onlyStreet: false, showClosed: false, chain: nil, maxDistanceKm: nil, query: query, sort: .distance)
        let items = c.filterGyms.filterNow(gyms: all, filters: f, origin: origin,
                                           favoriteIds: Set(favoriteIds.map { KotlinLong(value: $0) }))
        if !query.trimmingCharacters(in: .whitespaces).isEmpty {
            results = items.prefix(20).map { entry($0) }
            return
        }
        results = []
        let byId = Dictionary(items.map { ($0.gym.id, $0) }, uniquingKeysWith: { a, _ in a })
        let today = c.gymTraining.today()
        let sessions = c.gymTraining.summary(period: .year, date: today).sessions
        var counts: [Int64: Int] = [:]
        for s in sessions { counts[s.gymId, default: 0] += 1 }
        let topId = counts.max { $0.value < $1.value }?.key
        let favs = favoriteIds.compactMap { byId[$0] }.sorted { ($0.distanceKm?.doubleValue ?? .greatestFiniteMagnitude) < ($1.distanceKm?.doubleValue ?? .greatestFiniteMagnitude) }
        var seen = Set<Int64>()
        let todayDate = GymDates.date(today)
        let recentSessions = sessions.sorted { GymDates.date($0.date) > GymDates.date($1.date) }
            .filter { seen.insert($0.gymId).inserted && !favoriteIds.contains($0.gymId) }.prefix(4)
        recents = recentSessions.compactMap { s in
            byId[s.gymId].map { item in
                let days = Calendar.current.dateComponents([.day], from: GymDates.date(s.date), to: todayDate).day ?? 0
                return GymPickerEntry(gym: item.gym, distanceKm: item.distanceKm?.doubleValue, daysAgo: days, isTop: item.gym.id == topId)
            }
        }
        favorites = favs.map { entry($0, top: $0.gym.id == topId) }
        let used = Set(favorites.map { $0.gym.id } + recents.map { $0.gym.id })
        nearby = items.filter { !used.contains($0.gym.id) }.prefix(6).map { entry($0) }
    }

    private func entry(_ i: GymListItem, top: Bool = false) -> GymPickerEntry {
        GymPickerEntry(gym: i.gym, distanceKm: i.distanceKm?.doubleValue, isTop: top)
    }
}

private enum PickDay { case today, yesterday, other }

/// «¿Dónde has entrenado?» (maqueta 3): buscador, día (Hoy / Ayer / Otro día) y rocódromos favoritos, recientes y
/// cercanos. «Seguir» devuelve el rocódromo y el día.
struct GymPickerSheet: View {
    let onPick: (Gym, Date) -> Void
    /// Si se pasa, arriba sale «Solo board» (boards sin rocódromo) y devuelve solo el día elegido.
    var onSoloBoard: ((Date) -> Void)?
    @StateObject private var vm = GymPickerViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var chosen: Gym?
    @State private var day: PickDay = .today
    @State private var other = Date()
    @State private var datePicking = false

    private var date: Date {
        switch day {
        case .today: return Date()
        case .yesterday: return Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date()
        case .other: return other
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("gym_pick_eyebrow").uppercased()).eyebrow(Cumbre.terra)
                    Text(L("gym_pick_title")).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                }
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17)).foregroundStyle(Cumbre.ink).frame(width: 40, height: 40)
                }
                .buttonStyle(.plain).accessibilityLabel(L("common_close"))
            }
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink2)
                TextField(L("gym_search_hint"), text: $vm.query).font(.system(size: 14))
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Cumbre.paper, in: Capsule())
            .padding(.top, 8)
            HStack(spacing: 8) {
                dayChip(L("gym_pick_today"), day == .today) { day = .today }
                dayChip(L("gym_pick_yesterday"), day == .yesterday) { day = .yesterday }
                dayChip(day == .other ? otherLabel : L("gym_pick_other_day"), day == .other, icon: true) { datePicking = true }
            }
            .padding(.top, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if let onSoloBoard { soloRow { onSoloBoard(date) } }
                    if !vm.query.trimmingCharacters(in: .whitespaces).isEmpty {
                        ForEach(vm.results) { row($0) }
                    } else {
                        section(L("gym_pick_favorites"), vm.favorites)
                        section(L("gym_pick_recent"), vm.recents)
                        section(L("gym_pick_near"), vm.nearby)
                    }
                }
                .padding(.top, 8)
            }
            Button { if let chosen { onPick(chosen, date) } } label: {
                Text((chosen.map { L("gym_pick_continue", $0.name) } ?? L("gym_pick_choose")).uppercased())
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(.white).lineLimit(1)
                    .frame(maxWidth: .infinity).padding(.vertical, 15)
                    .background(Cumbre.terraFill.opacity(chosen != nil ? 1 : 0.4), in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(chosen == nil)
            .padding(.top, 8)
        }
        .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 12)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task {
            guard AppDependencies.shared.locationBridge.hasPermission() else { return }
            if let loc = try? await AppDependencies.shared.container.locationProvider?.current() {
                vm.setOrigin(lat: loc.lat, lon: loc.lon)
            }
        }
        .sheet(isPresented: $datePicking) {
            VStack {
                DatePicker("", selection: $other, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.graphical).labelsHidden().tint(Cumbre.terra)
                HStack {
                    Button(L("common_cancel").uppercased()) { datePicking = false }
                    Spacer()
                    Button(L("common_save").uppercased()) { day = .other; datePicking = false }
                }
                .font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.terra)
            }
            .padding(16)
            .presentationDetents([.medium, .large])
        }
    }

    /// «Solo board»: boards sin rocódromo (tablero de casa).
    private func soloRow(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 12).fill(Cumbre.terraFill).frame(width: 40, height: 40)
                    .overlay(Image(systemName: "square.grid.2x2").font(.system(size: 18)).foregroundStyle(.white))
                VStack(alignment: .leading, spacing: 1) {
                    Text(L("gym_solo_board_title")).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink)
                    Text(L("gym_solo_board_sub")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.terraFill, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var otherLabel: String {
        let c = Calendar.current.dateComponents([.day, .month], from: other)
        return "\(c.day ?? 1)/\(c.month ?? 1)"
    }

    private func dayChip(_ text: String, _ on: Bool, icon: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if icon { Image(systemName: "calendar").font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
                Text(text.uppercased()).eyebrow(on ? Cumbre.terraFill : Cumbre.ink2).lineLimit(1)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(on ? Cumbre.terraBg : Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: on ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private func section(_ title: String, _ items: [GymPickerEntry]) -> some View {
        if !items.isEmpty {
            Text(title.uppercased()).eyebrow(Cumbre.ink2).padding(.top, 4)
            ForEach(items) { row($0) }
        }
    }

    private func row(_ e: GymPickerEntry) -> some View {
        let selected = e.gym.id == chosen?.id
        return Button { chosen = e.gym } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 12).fill(Cumbre.terraFill).frame(width: 40, height: 40)
                    .overlay(Text(String(e.gym.name.prefix(1)).uppercased()).font(Cumbre.serif(18, .bold)).foregroundStyle(.white))
                VStack(alignment: .leading, spacing: 2) {
                    Text(e.gym.name).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                        .multilineTextAlignment(.leading)
                    let sub = subtitle(e)
                    if !sub.isEmpty { Text(sub).font(.system(size: 12)).foregroundStyle(Cumbre.ink2) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if selected {
                    Image(systemName: "checkmark").foregroundStyle(Cumbre.terraFill)
                } else if e.isTop {
                    Image(systemName: "star.fill").font(.system(size: 14)).foregroundStyle(Cumbre.terraFill)
                }
            }
            .padding(12)
            .background(selected ? Cumbre.terraBg : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Cumbre.terraFill : Cumbre.rule, lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private func subtitle(_ e: GymPickerEntry) -> String {
        var parts: [String] = []
        if let d = GymPresentation.distanceText(e.distanceKm) { parts.append(d) }
        if e.isTop { parts.append(L("gym_pick_yours")) }
        if let days = e.daysAgo { parts.append(Self.ago(days)) }
        if e.distanceKm == nil && e.daysAgo == nil {
            let place = Array(NSOrderedSet(array: [e.gym.city, e.gym.province].filter { !$0.isEmpty })) as? [String] ?? []
            if !place.isEmpty { parts.append(place.joined(separator: ", ")) }
        }
        return parts.joined(separator: " · ")
    }

    static func ago(_ days: Int) -> String {
        if days <= 0 { return L("gym_pick_today").lowercased() }
        if days < 14 { return LP("gym_pick_ago_days", days) }
        if days < 60 { return LP("gym_pick_ago_weeks", days / 7) }
        return LP("gym_pick_ago_months", days / 30)
    }
}
