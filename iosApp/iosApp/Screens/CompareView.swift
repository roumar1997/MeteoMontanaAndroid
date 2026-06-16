import SwiftUI
import UIKit
import Shared

// Comparador de escuelas — espejo de CompareScreen.kt. Columnas lado a lado
// con score, condiciones, ventana óptima, mejor día, mini-heatmap y CÓMO LLEGAR.

@MainActor
final class CompareViewModel: ObservableObject {
    @Published var forecasts: [Forecast] = []
    @Published var loading = true

    private let getForecast: GetForecastUseCase
    init(getForecast: GetForecastUseCase = AppDependencies.shared.container.getForecast) {
        self.getForecast = getForecast
    }

    func load(schoolIds: [String]) async {
        loading = true
        var result: [Forecast] = []
        for id in schoolIds {
            if let f = try? await getForecast.invoke(schoolId: id) { result.append(f) }
        }
        forecasts = result
        loading = false
    }
}

struct CompareView: View {
    let schools: [School]
    @StateObject private var vm = CompareViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if vm.loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        HStack(alignment: .top, spacing: 0) {
                            ForEach(vm.forecasts, id: \.schoolId) { f in
                                CompareColumn(forecast: f)
                                    .frame(width: 220)
                                Divider().overlay(Cumbre.rule)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Comparar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
            .task { await vm.load(schoolIds: schools.map { $0.id }) }
        }
    }
}

private struct CompareColumn: View {
    let forecast: Forecast

    var body: some View {
        let c = forecast.current
        let score = Int(c.score)
        VStack(alignment: .leading, spacing: 10) {
            Text(forecast.schoolName)
                .font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink)
                .lineLimit(2).frame(height: 48, alignment: .top)

            // Score
            HStack(alignment: .bottom, spacing: 2) {
                Text("\(score)").font(Cumbre.serif(40, .bold)).foregroundStyle(Cumbre.score(score))
                Text("/100").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3).padding(.bottom, 6)
            }
            Text(Cumbre.scoreLabel(score)).font(Cumbre.mono(10, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.score(score))

            Divider().overlay(Cumbre.rule)
            row("TEMP", "\(Int(c.temperature))°")
            row("HUM", "\(Int(c.humidity))%")
            row("VIENTO", "\(Int(c.windSpeed)) km/h")
            row("ROCA", c.dryRock ? "Seca" : "Mojada", c.dryRock ? Cumbre.ok : Cumbre.bad)

            Divider().overlay(Cumbre.rule)
            if let w = forecast.bestWindow {
                labeled("ÓPTIMO", "\(hm(w.start))–\(hm(w.end))")
            }
            if let b = forecast.bestDay {
                labeled("MEJOR DÍA", "\(b.label) (\(b.score))")
            }

            // Mini heatmap próximas horas
            HStack(spacing: 0) {
                ForEach(Array(forecast.hours.prefix(12).enumerated()), id: \.offset) { _, h in
                    Rectangle().fill(Cumbre.score(Int(h.score))).frame(height: 14)
                }
            }
            .padding(.top, 4)

            DirectionsLink(lat: forecast.lat, lon: forecast.lon, label: forecast.schoolName)
        }
        .padding(12)
    }

    private func row(_ k: String, _ v: String, _ color: Color = Cumbre.ink) -> some View {
        HStack {
            Text(k).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
            Spacer()
            Text(v).font(.system(size: 13, weight: .semibold)).foregroundStyle(color)
        }
    }

    private func labeled(_ k: String, _ v: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
            Text(v).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
        }
    }

    // "2026-06-16T07:00" -> "07:00"
    private func hm(_ t: String) -> String {
        if let r = t.range(of: "T") { return String(t[r.upperBound...].prefix(5)) }
        return String(t.suffix(5))
    }
}

/// Botón CÓMO LLEGAR — abre Google Maps (o web) con destino. Solo URL, sin bridge.
struct DirectionsLink: View {
    let lat: Double
    let lon: Double
    let label: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            let g = URL(string: "comgooglemaps://?daddr=\(lat),\(lon)")!
            let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)")!
            openURL(UIApplication.shared.canOpenURL(g) ? g : web)
        } label: {
            Text("CÓMO LLEGAR").font(Cumbre.mono(11, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.terra)
                .padding(.vertical, 8).frame(maxWidth: .infinity)
                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.top, 6)
    }
}
