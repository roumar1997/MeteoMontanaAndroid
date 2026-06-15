import SwiftUI
import Shared

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,
// buscador inline, filtros, y la fila rica (badge tintado + rank + nombre serif
// + estrella + subtítulo + heatmap 10 celdas + tag ● SECA/MOJADA).
// Datos reales del backend vía use cases compartidos (KMP). Filtrado local.

@MainActor
final class SchoolListViewModel: ObservableObject {
    @Published var schools: [School] = []
    @Published var scores: [String: SchoolScore] = [:]
    @Published var loading = true
    @Published var errorText: String?

    @Published var query = ""
    @Published var style: String?
    @Published var rock: String?
    @Published var favoriteIds: Set<String> = []
    @Published var userLat: Double?
    @Published var userLon: Double?

    private let getSchools: GetSchoolsUseCase
    private let getTodayScores: GetTodayScoresUseCase
    private let getMyFavorites: GetMyFavoritesUseCase
    private let addFavorite: AddFavoriteUseCase
    private let removeFavorite: RemoveFavoriteUseCase
    private let locationBridge = AppDependencies.shared.locationBridge
    private let locationProvider = AppDependencies.shared.container.locationProvider
    private let cachedSchools = AppDependencies.shared.container.cachedSchools

    init(
        getSchools: GetSchoolsUseCase = AppDependencies.shared.container.getSchools,
        getTodayScores: GetTodayScoresUseCase = AppDependencies.shared.container.getTodayScores,
        getMyFavorites: GetMyFavoritesUseCase = AppDependencies.shared.container.getMyFavorites,
        addFavorite: AddFavoriteUseCase = AppDependencies.shared.container.addFavorite,
        removeFavorite: RemoveFavoriteUseCase = AppDependencies.shared.container.removeFavorite
    ) {
        self.getSchools = getSchools
        self.getTodayScores = getTodayScores
        self.getMyFavorites = getMyFavorites
        self.addFavorite = addFavorite
        self.removeFavorite = removeFavorite
    }

    var styles: [String] { uniqueValues(schools.map { $0.style }) }
    var rocks: [String] { uniqueValues(schools.map { $0.rockType }) }
    var activeFilters: Bool { style != nil || rock != nil }
    func clearFilters() { style = nil; rock = nil; query = "" }

    var filtered: [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return schools.filter { s in
            (q.isEmpty || s.name.lowercased().contains(q) || (s.location?.lowercased().contains(q) ?? false))
            && (style == nil || s.style?.caseInsensitiveCompare(style!) == .orderedSame)
            && (rock == nil || s.rockType?.caseInsensitiveCompare(rock!) == .orderedSame)
        }.sorted { (scores[$0.id]?.todayScore ?? -1) > (scores[$1.id]?.todayScore ?? -1) }
    }

    func load() async {
        errorText = nil
        // 1. Pinta desde la caché local al instante (si la hay).
        if let cached = try? await cachedSchools?.load(), !cached.isEmpty {
            schools = cached
            loading = false
        } else {
            loading = true
        }
        // 2. Revalida desde red y actualiza la caché.
        do {
            let fresh = try await getSchools.invoke(
                region: nil, style: nil, rockType: nil, lat: nil, lon: nil, radioKm: nil
            )
            schools = fresh
            try? await cachedSchools?.replaceAll(schools: fresh)
        } catch {
            // Sin red: si no había caché, error; si había, seguimos offline.
            if schools.isEmpty { errorText = error.localizedDescription }
        }
        loading = false
        await loadLocation()
        await loadFavorites()
        await loadScores()
    }

    func refresh() async { await load() }

    private func loadLocation() async {
        guard locationBridge.hasPermission() else { return }
        if let loc = try? await locationProvider?.current() {
            userLat = loc.lat; userLon = loc.lon
        }
    }

    /// Distancia en km del usuario a la escuela (Haversine compartido). nil si
    /// no hay ubicación.
    func distanceKm(_ school: School) -> Int? {
        guard let la = userLat, let lo = userLon else { return nil }
        let km = Geo.shared.haversineKm(lat1: la, lon1: lo, lat2: school.lat, lon2: school.lon)
        return Int(km.rounded())
    }

    private func loadFavorites() async {
        // Requiere sesión (el login es obligatorio al arrancar). Si falla, vacío.
        let favs = try? await getMyFavorites.invoke()
        favoriteIds = Set((favs ?? []).map { $0.id })
    }

    /// Toggle optimista: actualiza la estrella al instante y revierte si la red
    /// falla (mismo comportamiento que SchoolListViewModel.kt de Android).
    func toggleFavorite(_ schoolId: String) {
        let wasFavorite = favoriteIds.contains(schoolId)
        if wasFavorite { favoriteIds.remove(schoolId) } else { favoriteIds.insert(schoolId) }
        Task {
            do {
                if wasFavorite { try await removeFavorite.invoke(schoolId: schoolId) }
                else { try await addFavorite.invoke(schoolId: schoolId) }
            } catch {
                // Revertir en caso de error.
                if wasFavorite { favoriteIds.insert(schoolId) } else { favoriteIds.remove(schoolId) }
            }
        }
    }

    private func loadScores() async {
        let ids = schools.map { $0.id }
        for chunk in stride(from: 0, to: ids.count, by: 50) {
            let slice = Array(ids[chunk..<min(chunk + 50, ids.count)])
            guard let batch = try? await getTodayScores.invoke(ids: slice) else { continue }
            for s in batch { scores[s.id] = s }
        }
    }

    private func uniqueValues(_ raw: [String?]) -> [String] {
        Array(Set(raw.compactMap { $0 }.filter { !$0.isEmpty })).sorted()
    }
}

struct SchoolListView: View {
    @StateObject private var vm = SchoolListViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    TopIconsRow()
                    HeaderEscuelas(count: vm.loading ? nil : vm.schools.count)
                    CoffeeBanner()
                    SearchField(text: $vm.query)
                    FilterChips(vm: vm)
                    Divider().overlay(Cumbre.rule)

                    if vm.loading {
                        ForEach(0..<6, id: \.self) { _ in SkeletonRow(); Divider().overlay(Cumbre.rule) }
                    } else if let err = vm.errorText {
                        ErrorRow(message: err) { Task { await vm.refresh() } }
                    } else {
                        let items = vm.filtered
                        if items.isEmpty {
                            EmptyRow(canClear: vm.activeFilters || !vm.query.isEmpty) { vm.clearFilters() }
                        } else {
                            ForEach(Array(items.enumerated()), id: \.element.id) { idx, school in
                                NavigationLink(destination: SchoolDetailView(school: school)) {
                                    SchoolListItemView(
                                        rank: idx + 1,
                                        school: school,
                                        score: vm.scores[school.id],
                                        distanceKm: vm.distanceKm(school),
                                        isFavorite: vm.favoriteIds.contains(school.id),
                                        onToggleFavorite: { vm.toggleFavorite(school.id) }
                                    )
                                }
                                .buttonStyle(.plain)
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .task { await vm.load() }
            .refreshable { await vm.refresh() }
        }
    }
}

// MARK: - Header

private struct TopIconsRow: View {
    @State private var showAccount = false
    @State private var showNotifications = false
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        HStack(spacing: 4) {
            Spacer()
            // Búsqueda y chat aún no cableados (chat necesita bridge Firestore).
            iconButton("magnifyingglass") {}
            iconButton("bubble.left") {}
            iconButton(theme.iconName) { theme.cycle() }
            iconButton("bell") { showNotifications = true }
            iconButton("person") { showAccount = true }
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
        .sheet(isPresented: $showAccount) { AccountView() }
        .sheet(isPresented: $showNotifications) { NotificationsView() }
    }

    private func iconButton(_ name: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 18))
                .foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct HeaderEscuelas: View {
    let count: Int?
    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Escuelas")
                    .font(Cumbre.serif(34, .bold))
                    .foregroundStyle(Cumbre.ink)
                if let count {
                    Text("\(count) escuelas")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            OutlinedCumbreButton(text: "+ Enviar escuela", tint: Cumbre.terra)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

private struct CoffeeBanner: View {
    var body: some View {
        HStack(spacing: 8) {
            Text("☕").font(.system(size: 30))
            VStack(alignment: .leading, spacing: 1) {
                Text("¿Te ayuda la app?")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Text("Mantenida con amor por la comunidad escaladora")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2.opacity(0.8))
            }
            Spacer()
            OutlinedCumbreButton(text: "Apóyanos", tint: Cumbre.ink)
        }
        .padding(12)
        .background(Cumbre.terraBg)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

private struct SearchField: View {
    @Binding var text: String
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
            TextField("Buscar escuela…", text: $text)
                .foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

private struct FilterChips: View {
    @ObservedObject var vm: SchoolListViewModel
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Menu {
                    Picker("Estilo", selection: $vm.style) {
                        Text("Todos").tag(String?.none)
                        ForEach(vm.styles, id: \.self) { Text($0).tag(String?.some($0)) }
                    }
                } label: { chip("ESTILO" + (vm.style.map { ": \($0.uppercased())" } ?? ""), active: vm.style != nil) }

                Menu {
                    Picker("Roca", selection: $vm.rock) {
                        Text("Todas").tag(String?.none)
                        ForEach(vm.rocks, id: \.self) { Text($0).tag(String?.some($0)) }
                    }
                } label: { chip("ROCA" + (vm.rock.map { ": \($0.uppercased())" } ?? ""), active: vm.rock != nil) }

                if vm.activeFilters {
                    Button { vm.clearFilters() } label: { chip("✕ QUITAR", active: false) }
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 4)
    }

    private func chip(_ t: String, active: Bool) -> some View {
        Text(t)
            .font(Cumbre.mono(11, .bold))
            .tracking(0.8)
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? Cumbre.terra : Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}

private struct OutlinedCumbreButton: View {
    let text: String
    var tint: Color = Cumbre.ink
    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .overlay(Rectangle().stroke(Cumbre.ink, lineWidth: 1))
    }
}

// MARK: - Fila rica (réplica de SchoolListItem.kt)

private struct SchoolListItemView: View {
    let rank: Int
    let school: School
    let score: SchoolScore?
    var distanceKm: Int? = nil
    var isFavorite: Bool = false
    var onToggleFavorite: () -> Void = {}

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            ScoreBadge(score: score.map { Int($0.todayScore) })
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 0) {
                    Text(String(format: "%02d", rank))
                        .font(Cumbre.mono(11, .semibold))
                        .tracking(1.4)
                        .foregroundStyle(Cumbre.ink3)
                        .frame(width: 24, alignment: .leading)
                    Text(school.name)
                        .font(Cumbre.serif(19, .bold))
                        .foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    // Estrella tocable con update optimista. BorderlessButtonStyle
                    // para que reciba el tap sin disparar la navegación de la fila.
                    Button(action: onToggleFavorite) {
                        Image(systemName: isFavorite ? "star.fill" : "star")
                            .font(.system(size: 18))
                            .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink3)
                            .frame(width: 36, height: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)
                }
                Text(subtitle)
                    .font(Cumbre.mono(12))
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.top, 4)
                HStack(alignment: .center, spacing: 8) {
                    HeatmapBar(scores: score?.hourlyScores.map { $0.intValue })
                    DryWetTag(dry: score?.dryRock, rainProb: score.map { Int($0.rainProb) }, rainMm: score?.rainMm)
                }
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        var parts: [String] = []
        if let r = school.rockType, !r.isEmpty { parts.append(r.uppercased()) }
        if let reg = school.region, !reg.isEmpty { parts.append(reg) }
        if let km = distanceKm { parts.append("\(km) KM") }
        return parts.joined(separator: "  ·  ")
    }
}

private struct ScoreBadge: View {
    let score: Int?
    var body: some View {
        let color = score.map { Cumbre.score($0) } ?? Cumbre.rule
        VStack(spacing: 1) {
            Text(score.map(String.init) ?? "—")
                .font(Cumbre.serif(28, .bold))
                .foregroundStyle(score != nil ? color : Cumbre.ink2)
            Text(Cumbre.scoreLabel(score))
                .font(.system(size: 8, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(score != nil ? color : Cumbre.ink3)
        }
        .frame(width: 64, height: 72)
        .background((score != nil ? color : Cumbre.paper).opacity(score != nil ? 0.12 : 1))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(color, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

private struct HeatmapBar: View {
    let scores: [Int]?
    var body: some View {
        let cells: [Int?] = {
            if let s = scores, !s.isEmpty { return Array(s.prefix(10)).map { Optional($0) } }
            return Array(repeating: nil, count: 10)
        }()
        HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, s in
                Rectangle().fill(s.map { Cumbre.score($0) } ?? Cumbre.rule.opacity(0.35))
            }
        }
        .frame(height: 16)
        .frame(maxWidth: .infinity)
    }
}

private struct DryWetTag: View {
    let dry: Bool?
    let rainProb: Int?
    let rainMm: Double?
    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if let dry {
                Text(dry ? "● SECA" : "● MOJADA")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.8)
                    .foregroundStyle(dry ? Cumbre.ok : Cumbre.bad)
            }
            if dry == false, let p = rainProb, p > 0 {
                Text("\(p)%").font(.system(size: 10)).foregroundStyle(Cumbre.bad)
            }
        }
    }
}

// MARK: - Estados

private struct SkeletonRow: View {
    var body: some View {
        let tone = Cumbre.ink3.opacity(0.12)
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 64, height: 72)
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 160, height: 16)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(width: 110, height: 12)
                RoundedRectangle(cornerRadius: 2).fill(tone).frame(height: 14)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
    }
}

private struct EmptyRow: View {
    let canClear: Bool
    let onClear: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("No hay escuelas con esos filtros")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            if canClear {
                Button(action: onClear) { OutlinedCumbreButton(text: "QUITAR FILTROS") }
            }
        }
        .frame(maxWidth: .infinity).padding(32)
    }
}

private struct ErrorRow: View {
    let message: String
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Text("Error: \(message)").font(.system(size: 15)).foregroundStyle(Cumbre.bad)
            Button(action: onRetry) { OutlinedCumbreButton(text: "REINTENTAR") }
        }
        .frame(maxWidth: .infinity).padding(40)
    }
}

#Preview { SchoolListView() }
