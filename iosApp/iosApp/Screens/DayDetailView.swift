import SwiftUI
import Shared

// `DayForecast` (clase Kotlin) Identifiable por su fecha — para .sheet(item:).
extension DayForecast: Identifiable { public var id: String { date } }

/// Detalle de un día — espejo de DayDetailScreen.kt: índice del día, tabla de
/// condiciones y desglose por horas de ese día.
struct DayDetailView: View {
    let day: DayForecast
    let allHours: [HourForecast]
    @Environment(\.dismiss) private var dismiss

    private var dayHours: [HourForecast] { allHours.filter { $0.time.hasPrefix(day.date) } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    indexSection
                    Divider().overlay(Cumbre.rule)
                    conditionsTable
                    Divider().overlay(Cumbre.rule)
                    SectionTitleDD("HORA A HORA")
                    if dayHours.isEmpty {
                        Text("Sin datos horarios para este día.")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(16)
                    } else {
                        ForEach(Array(dayHours.enumerated()), id: \.offset) { _, h in
                            hourRow(h); Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(day.date)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }

    private var indexSection: some View {
        let score = Int(day.avgScore)
        return VStack(alignment: .leading, spacing: 8) {
            Text("ÍNDICE DEL DÍA").font(Cumbre.mono(10, .bold)).tracking(1.8).foregroundStyle(Cumbre.ink3)
            HStack(spacing: 0) {
                ForEach(0..<20, id: \.self) { i in
                    Rectangle().fill(i < score / 5 ? Cumbre.score(score) : Cumbre.rule.opacity(0.35))
                }
            }.frame(height: 16)
            HStack(alignment: .bottom, spacing: 2) {
                Text("\(score)").font(Cumbre.serif(40, .bold)).foregroundStyle(Cumbre.score(score))
                Text("/100").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3).padding(.bottom, 6)
                Spacer()
                Text("● \(Cumbre.scoreLabel(score))").font(Cumbre.mono(11, .bold))
                    .foregroundStyle(Cumbre.score(score))
            }
        }
        .padding(16)
    }

    private var conditionsTable: some View {
        let cells: [(String, String)] = [
            ("MÁX", "\(Int(day.tempMax))°"),
            ("MÍN", "\(Int(day.tempMin))°"),
            ("LLUVIA", String(format: "%.1f mm", day.precipitationTotal)),
        ]
        return HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, c in
                VStack(spacing: 4) {
                    Text(c.0).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                    Text(c.1).font(.system(size: 16, weight: .semibold)).foregroundStyle(Cumbre.ink)
                }.frame(maxWidth: .infinity)
            }
        }.padding(16)
    }

    private func hourRow(_ h: HourForecast) -> some View {
        HStack(spacing: 12) {
            Text(hm(h.time)).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2).frame(width: 48, alignment: .leading)
            WmoIcon(code: Int(h.weatherCode), size: 20, tint: Cumbre.ink2)
            RoundedRectangle(cornerRadius: 2).fill(Cumbre.score(Int(h.score)))
                .frame(width: 30, height: 24)
                .overlay(Text("\(Int(h.score))").font(Cumbre.mono(10, .bold)).foregroundStyle(.white))
            Text("\(Int(h.temperature))°").font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink)
            Spacer()
            // Lluvia: mm si los hay, y la probabilidad.
            if h.precipitation > 0 {
                Text(String(format: "%.1f mm", h.precipitation))
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.rain)
            }
            if h.precipitationProbability > 0 {
                Text("\(h.precipitationProbability)%").font(Cumbre.mono(11)).foregroundStyle(Cumbre.rain)
            }
            // Viento km/h — como Android.
            Text("\(Int(h.windSpeed)) km/h").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    private func hm(_ t: String) -> String {
        if let r = t.range(of: "T") { return String(t[r.upperBound...].prefix(5)) }
        return String(t.suffix(5))
    }
}

private struct SectionTitleDD: View {
    let text: String
    init(_ t: String) { text = t }
    var body: some View {
        Text(text).font(Cumbre.mono(10, .bold)).tracking(1.8).foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 4)
    }
}
