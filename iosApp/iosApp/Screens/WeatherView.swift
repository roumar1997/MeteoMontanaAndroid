import SwiftUI
import Shared

// Tab Tiempo — réplica de WeatherScreen.kt: forecast en tu ubicación.
// Usa el LocationProvider (bridge iOS, CLLocationManager) para obtener
// lat/lon y el GetForecastByLocationUseCase compartido. Si no hay permiso,
// muestra el estado "necesita ubicación" con botón para concederlo.
// (Los chips de favoritas quedan pendientes: requieren login/auth.)

@MainActor
final class WeatherViewModel: ObservableObject {
    enum State {
        case loading
        case needPermission
        case success(Forecast)
        case error(String)
    }

    @Published var state: State = .loading

    private let container = AppDependencies.shared.container
    private let bridge = AppDependencies.shared.locationBridge

    /// Madrid — fallback si hay permiso pero el sistema aún no tiene fix.
    private let fallback = (lat: 40.4168, lon: -3.7038)

    func load() async {
        state = .loading
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

    func requestPermission() {
        bridge.requestPermission()
    }
}

struct WeatherView: View {
    @StateObject private var vm = WeatherViewModel()
    @State private var factorsExpanded = false

    var body: some View {
        VStack(spacing: 0) {
            header
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
                Text("Tiempo").font(Cumbre.serif(34, .bold)).foregroundStyle(Cumbre.ink)
                Text("En tu ubicación").font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.top, 8)
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
                ForecastBodyView(forecast: forecast, factorsExpanded: $factorsExpanded)
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
                Text("ACTIVAR UBICACIÓN").font(Cumbre.mono(12, .bold)).tracking(0.8)
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
