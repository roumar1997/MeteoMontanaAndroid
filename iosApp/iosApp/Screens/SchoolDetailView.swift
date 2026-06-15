import SwiftUI
import Shared

// Detalle de escuela con previsión: hero del score, condiciones actuales,
// ventana óptima, mejor día y las próximas horas. Usa GetForecastUseCase del
// módulo compartido (SKIE lo expone como async throws). Sin auth ni ubicación,
// así que no necesita los ports bridge todavía.

@MainActor
final class SchoolDetailViewModel: ObservableObject {
    @Published var forecast: Forecast?
    @Published var loading = true
    @Published var errorText: String?

    private let getForecast: GetForecastUseCase

    init(getForecast: GetForecastUseCase = AppDependencies.shared.container.getForecast) {
        self.getForecast = getForecast
    }

    func load(schoolId: String) async {
        loading = true
        errorText = nil
        do {
            forecast = try await getForecast.invoke(schoolId: schoolId)
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}

struct SchoolDetailView: View {
    let school: School
    @StateObject private var vm = SchoolDetailViewModel()

    var body: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 40)
            } else if let err = vm.errorText {
                ContentUnavailableView("Sin previsión", systemImage: "cloud.slash", description: Text(err))
                    .padding(.top, 40)
            } else if let f = vm.forecast {
                VStack(alignment: .leading, spacing: 16) {
                    scoreHero(f.current)
                    conditions(f.current)
                    if let w = f.bestWindow { optimalWindow(w) }
                    if let b = f.bestDay { bestDay(b) }
                    hours(f.hours)
                }
                .padding(16)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(school.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load(schoolId: school.id) }
    }

    // MARK: - Hero del score

    private func scoreHero(_ c: Current) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ÍNDICE DE HOY").eyebrow()
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("\(c.score)")
                    .font(.system(size: 56, weight: .bold, design: .serif))
                    .foregroundStyle(Cumbre.score(Int(c.score)))
                Text(c.scoreLabel.uppercased())
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Cumbre.ink2)
            }
            Text(c.dryRock ? "● ROCA SECA" : "● ROCA MOJADA")
                .font(.system(size: 13, weight: .semibold, design: .monospaced))
                .foregroundStyle(c.dryRock ? Cumbre.ok : Cumbre.rain)
            if let d = c.drying, let msg = d.message {
                Text(msg).font(.caption).foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .cumbreCard()
    }

    // MARK: - Condiciones actuales

    private func conditions(_ c: Current) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("AHORA").eyebrow()
            HStack(spacing: 0) {
                metric("\(Int(c.temperature))°", "TEMP")
                metric("\(Int(c.humidity))%", "HUM")
                metric("\(Int(c.windSpeed))", "VIENTO")
                metric("\(c.precipitationProbability)%", "LLUVIA")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .cumbreCard()
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 20, weight: .semibold, design: .monospaced))
                .foregroundStyle(Cumbre.ink)
            Text(label).eyebrow()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Ventana óptima

    private func optimalWindow(_ w: OptimalWindow) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("VENTANA ÓPTIMA").eyebrow(Cumbre.terra)
            Text("\(shortTime(w.start)) – \(shortTime(w.end))")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Cumbre.ink)
            Text("Score medio \(w.avgScore)")
                .font(.caption).foregroundStyle(Cumbre.ink2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Cumbre.terraBg)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    // MARK: - Mejor día

    private func bestDay(_ b: BestDay) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("MEJOR DÍA").eyebrow()
                Text(b.label).font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
            }
            Spacer()
            Text("\(b.score)")
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundStyle(Cumbre.score(Int(b.score)))
        }
        .padding(16)
        .cumbreCard()
    }

    // MARK: - Próximas horas

    private func hours(_ hs: [HourForecast]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("PRÓXIMAS HORAS").eyebrow()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(hs.prefix(16).enumerated()), id: \.offset) { _, h in
                        VStack(spacing: 6) {
                            Text(shortTime(h.time))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(Cumbre.ink3)
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Cumbre.score(Int(h.score)))
                                .frame(width: 28, height: 28)
                                .overlay(
                                    Text("\(h.score)")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(.white)
                                )
                            Text("\(Int(h.temperature))°")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(Cumbre.ink2)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .cumbreCard()
    }

    /// "2026-06-15T14:00" → "14:00"; "2026-06-15" → "15 jun".
    private func shortTime(_ iso: String) -> String {
        if let tIdx = iso.firstIndex(of: "T") {
            return String(iso[iso.index(after: tIdx)...].prefix(5))
        }
        return iso
    }
}

/// Tarjeta Cumbre: fondo paper, borde rule 1pt, radius 2 (sin sombra).
private extension View {
    func cumbreCard() -> some View {
        self.background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}
