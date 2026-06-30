import SwiftUI
import Shared

// Tab Tiempo — réplica de WeatherScreen.kt: forecast en tu ubicación.
// Usa el LocationProvider (bridge iOS, CLLocationManager) para obtener
// lat/lon y el GetForecastByLocationUseCase compartido. Si no hay permiso,
// muestra el estado "necesita ubicación" con botón para concederlo.
// Incluye el grid de favoritas (score medio por día) sobre el forecast.

@MainActor
final class WeatherViewModel: ObservableObject {
    enum State {
        case loading
        case needPermission
        case success(Forecast)
        case error(String)
    }

    @Published var state: State = .loading
    @Published var favoritesGrid: FavoritesGrid?
    @Published var favorites: [FavoriteSchool] = []
    @Published var selectedFavoriteId: String?   // nil = ubicación

    private let container = AppDependencies.shared.container
    private let bridge = AppDependencies.shared.locationBridge

    /// Madrid — fallback si hay permiso pero el sistema aún no tiene fix.
    private let fallback = (lat: 40.4168, lon: -3.7038)

    var selectedName: String? { favorites.first { $0.id == selectedFavoriteId }?.name }

    func load() async {
        state = .loading
        await loadFavoritesGrid()
        favorites = (try? await container.getMyFavorites.invoke()) ?? []

        // Si hay una favorita seleccionada, su forecast (no la ubicación).
        if let id = selectedFavoriteId {
            do { state = .success(try await container.getForecast.invoke(schoolId: id)) }
            catch { state = .error(error.localizedDescription) }
            return
        }

        guard bridge.hasPermission() else { state = .needPermission; return }
        let loc = try? await container.locationProvider?.current()
        let lat = loc?.lat ?? fallback.lat
        let lon = loc?.lon ?? fallback.lon
        do {
            let forecast = try await container.getForecastByLocation.invoke(
                lat: lat, lon: lon, schoolId: nil
            )
            state = .success(forecast)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    func selectFavorite(_ id: String?) {
        selectedFavoriteId = id
        Task { await load() }
    }

    private func loadFavoritesGrid() async {
        favoritesGrid = try? await container.getFavoritesGrid.invoke()
    }

    func requestPermission() {
        bridge.requestPermission()
    }
}

struct WeatherView: View {
    @StateObject private var vm = WeatherViewModel()
    @State private var factorsExpanded = false
    @State private var selectedDay: DayForecast?

    var body: some View {
        VStack(spacing: 0) {
            header
            if !vm.favorites.isEmpty { favoriteChips }
            Divider().overlay(Cumbre.rule).padding(.top, 8)
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
        .task { await vm.load() }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(NSLocalizedString("weather_title", comment: "")).font(Cumbre.serif(34, .bold)).foregroundStyle(Cumbre.ink)
                Text(vm.selectedName ?? "En tu ubicación")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
            HelpButton(topicKey: "weather")
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }

    private var favoriteChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip("📍 Ubicación", selected: vm.selectedFavoriteId == nil) { vm.selectFavorite(nil) }
                ForEach(vm.favorites, id: \.id) { f in
                    chip(f.name, selected: vm.selectedFavoriteId == f.id) { vm.selectFavorite(f.id) }
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.top, 8)
    }

    private func chip(_ t: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(Cumbre.mono(11, .bold)).tracking(0.6)
                .foregroundStyle(selected ? .white : Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(selected ? Cumbre.terra : Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var content: some View {
        switch vm.state {
        case .loading:
            Spacer(); ProgressView(); Spacer()
        case .needPermission:
            permissionPrompt
        case .error(let message):
            Spacer()
            ContentUnavailableView("Sin previsión", systemImage: "cloud.slash", description: Text(message))
            Spacer()
        case .success(let forecast):
            ScrollView {
                if let grid = vm.favoritesGrid, !grid.rows.isEmpty {
                    FavoritesGridView(grid: grid)
                    Divider().overlay(Cumbre.rule)
                }
                ForecastBodyView(forecast: forecast, factorsExpanded: $factorsExpanded,
                                 onSelectDay: { selectedDay = $0 })
            }
            .sheet(item: $selectedDay) { d in
                DayDetailView(day: d, allHours: forecast.hours)
            }
        }
    }

    private var permissionPrompt: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "location.circle").font(.system(size: 44)).foregroundStyle(Cumbre.ink3)
            Text("Necesitamos tu ubicación").font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
            Text("Para mostrar el tiempo de escalada donde estás.")
                .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
            Button {
                vm.requestPermission()
                // Tras conceder, reintentamos cargar.
                Task { try? await Task.sleep(nanoseconds: 800_000_000); await vm.load() }
            } label: {
                Text(NSLocalizedString("weather_enable_location", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                    .foregroundStyle(.white)
                    .padding(.vertical, 12).padding(.horizontal, 20)
                    .background(Cumbre.terra)
            }
            .buttonStyle(.plain)
            .padding(.top, 8)
            Spacer()
        }
        .padding(32)
    }
}

// MARK: - Grid de favoritas (réplica del grid de WeatherScreen.kt)

/// Tabla compacta de tus escuelas favoritas por día, con score medio coloreado.
/// Filas = escuela, columnas = día. Scroll horizontal si hay muchos días.
private struct FavoritesGridView: View {
    let grid: FavoritesGrid

    private let nameWidth: CGFloat = 120
    private let cellWidth: CGFloat = 40

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("TUS FAVORITAS").eyebrow().padding(.horizontal, 16).padding(.top, 12)

            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 4) {
                    // Cabecera de días
                    HStack(spacing: 2) {
                        Color.clear.frame(width: nameWidth, height: 1)
                        ForEach(Array(grid.days.enumerated()), id: \.offset) { _, day in
                            Text(dayLabel(day))
                                .font(Cumbre.mono(10))
                                .foregroundStyle(Cumbre.ink3)
                                .frame(width: cellWidth)
                        }
                    }
                    // Filas
                    ForEach(grid.rows, id: \.schoolId) { row in
                        HStack(spacing: 2) {
                            Text(row.schoolName)
                                .font(Cumbre.serif(14, .semibold))
                                .foregroundStyle(Cumbre.ink)
                                .lineLimit(1)
                                .frame(width: nameWidth, alignment: .leading)
                            ForEach(Array(row.cells.enumerated()), id: \.offset) { _, cell in
                                cellBox(Int(cell.avgScore))
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 8)
        }
    }

    private func cellBox(_ score: Int) -> some View {
        let color = Cumbre.score(score)
        return Text("\(score)")
            .font(Cumbre.mono(12, .bold))
            .foregroundStyle(color)
            .frame(width: cellWidth, height: 30)
            .background(color.opacity(0.14))
            .overlay(Rectangle().stroke(color, lineWidth: 1))
    }

    // "2026-06-16" -> "LUN"/"MAR"/… (día de la semana, como Android). Si no
    // parsea, cae a los últimos 5 caracteres ("06-16").
    private func dayLabel(_ day: String) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        fmt.locale = Locale(identifier: "en_US_POSIX")
        guard let date = fmt.date(from: day) else {
            return day.count >= 5 ? String(day.suffix(5)) : day
        }
        // weekday: 1=domingo … 7=sábado
        let weekday = Calendar(identifier: .gregorian).component(.weekday, from: date)
        let labels = ["DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"]
        return labels[(weekday - 1) % 7]
    }
}
