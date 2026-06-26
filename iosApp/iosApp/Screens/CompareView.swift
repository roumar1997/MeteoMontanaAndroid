import SwiftUI
import UIKit
import Shared

// Comparador de escuelas — espejo de CompareScreen.kt. Cabecera "HOY MEJOR" +
// tabla por filas (métricas en filas, escuelas en columnas), mejor de cada fila
// en terracota, y columnas pulsables → detalle de esa escuela.

struct CompareItem: Identifiable {
    let id: String
    let name: String
    let lat: Double
    let lon: Double
    let score: Int
    let rockType: String?
    let distanceKm: Double?
    let temp: Int
    let wind: Int
    let humidity: Int
    let rainProb: Int
    let dryRock: Bool
    let optimal: String?
    let bestDay: String?
    let school: School
}

@MainActor
final class CompareViewModel: ObservableObject {
    @Published var items: [CompareItem] = []
    @Published var loading = true

    private let getForecast: GetForecastUseCase
    init(getForecast: GetForecastUseCase = AppDependencies.shared.container.getForecast) {
        self.getForecast = getForecast
    }

    func load(schools: [School]) async {
        loading = true
        let loc = try? await AppDependencies.shared.container.locationProvider?.current()
        var result: [CompareItem] = []
        for s in schools {
            guard let f = try? await getForecast.invoke(schoolId: s.id) else { continue }
            let c = f.current
            var dist: Double? = nil
            if let loc {
                dist = Geo.shared.haversineKm(lat1: loc.lat, lon1: loc.lon, lat2: f.lat, lon2: f.lon)
            }
            result.append(CompareItem(
                id: f.schoolId, name: f.schoolName, lat: f.lat, lon: f.lon,
                score: Int(c.score), rockType: s.rockType, distanceKm: dist,
                temp: Int(c.temperature), wind: Int(c.windSpeed), humidity: Int(c.humidity),
                rainProb: Int(c.precipitationProbability), dryRock: c.dryRock,
                optimal: f.bestWindow.map { "\(shortHour($0.start))–\(shortHour($0.end))h" },
                bestDay: f.bestDay?.label, school: s))
        }
        items = result
        loading = false
    }

    private func shortHour(_ t: String) -> String {
        let hhmm = t.contains("T") ? String(t.split(separator: "T").last ?? "") : t
        let hh = String(hhmm.prefix(2))
        return Int(hh).map(String.init) ?? hhmm
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
                    ScrollView { table }
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
            .task { await vm.load(schools: schools) }
        }
    }

    @ViewBuilder private var table: some View {
        let items = vm.items
        if let winner = items.max(by: { $0.score < $1.score }) {
            VStack(spacing: 14) {
                // Cabecera: mejor de hoy (pulsable).
                NavigationLink(destination: SchoolDetailView(school: winner.school)) {
                    VStack(spacing: 2) {
                        Text("HOY MEJOR").font(Cumbre.mono(11, .bold)).tracking(1.6).foregroundStyle(Cumbre.ink3)
                        HStack(spacing: 6) {
                            Text(winner.name).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                            Text("\(winner.score)").font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.score(winner.score))
                        }
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.score(winner.score).opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
                }.buttonStyle(.plain)

                // Cabecera de columnas (pulsable → detalle).
                HStack(alignment: .top, spacing: 4) {
                    Spacer().frame(width: labelW)
                    ForEach(items) { it in
                        NavigationLink(destination: SchoolDetailView(school: it.school)) {
                            VStack(spacing: 4) {
                                Text(it.name).font(.system(size: 13, weight: .medium))
                                    .foregroundStyle(Cumbre.ink).multilineTextAlignment(.center).lineLimit(2)
                                Text("\(it.score)").font(Cumbre.mono(13, .bold)).foregroundStyle(.white)
                                    .padding(.horizontal, 8).padding(.vertical, 2)
                                    .background(Cumbre.score(it.score), in: RoundedRectangle(cornerRadius: 3))
                            }.frame(maxWidth: .infinity)
                        }.buttonStyle(.plain)
                    }
                }
                let rows: [(String, [String], Set<Int>)] = [
                    ("ROCA", items.map { $0.rockType?.capitalized ?? "—" },
                        Set(items.indices.filter { items[$0].dryRock })),
                    ("DISTANCIA", items.map { $0.distanceKm.map { "\(Int($0)) km" } ?? "—" },
                        minIdx(items.map { $0.distanceKm })),
                    ("TEMP", items.map { "\($0.temp)°" }, []),
                    ("VIENTO", items.map { "\($0.wind) km/h" }, minIdx(items.map { Double($0.wind) })),
                    ("HUMEDAD", items.map { "\($0.humidity)%" }, minIdx(items.map { Double($0.humidity) })),
                    ("PROB. LLUVIA", items.map { "\($0.rainProb)%" }, minIdx(items.map { Double($0.rainProb) })),
                    ("ÓPTIMO", items.map { $0.optimal ?? "—" }, []),
                    ("MEJOR DÍA", items.map { $0.bestDay ?? "—" }, [])
                ]
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { idx, r in
                        metricRow(r.0, r.1, best: r.2, zebra: idx % 2 == 1)
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
            }
            .padding(16)
        }
    }

    private let labelW: CGFloat = 90

    private func metricRow(_ label: String, _ values: [String], best: Set<Int>, zebra: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Text(label).font(Cumbre.mono(10, .bold)).tracking(0.6).foregroundStyle(Cumbre.ink3)
                    .frame(width: labelW, alignment: .leading).padding(.leading, 10)
                ForEach(Array(values.enumerated()), id: \.offset) { i, v in
                    Rectangle().fill(Cumbre.rule.opacity(0.5)).frame(width: 1, height: 22)
                    Text(v).font(.system(size: 13, weight: best.contains(i) ? .semibold : .regular))
                        .foregroundStyle(best.contains(i) ? Cumbre.terra : Cumbre.ink)
                        .frame(maxWidth: .infinity).multilineTextAlignment(.center).lineLimit(1)
                }
            }
            .padding(.vertical, 11)
            .background(zebra ? Cumbre.ink.opacity(0.035) : Color.clear)
            Divider().overlay(Cumbre.rule.opacity(0.5))
        }
    }

    private func minIdx(_ values: [Double?]) -> Set<Int> {
        let present = values.enumerated().compactMap { (i, v) in v.map { (i, $0) } }
        guard let m = present.map({ $0.1 }).min() else { return [] }
        return Set(present.filter { $0.1 == m }.map { $0.0 })
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
