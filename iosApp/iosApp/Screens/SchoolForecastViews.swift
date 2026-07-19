import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// CUERPO DEL FORECAST y sus subvistas — espejo de ForecastBody.kt.
// (BestDayBar, muerta desde que el mejor día vive en la cabecera, eliminada.)

struct ForecastBodyView: View {
    let forecast: Forecast
    var directions: (lat: Double, lon: Double, label: String)? = nil
    @Binding var factorsExpanded: Bool
    var onSelectDay: ((DayForecast) -> Void)? = nil
    /// Mapa de la escuela, insertado entre el tiempo actual y "Próximas 16 h"
    /// (el tab Tiempo no lo pasa). AnyView para no acoplar el tipo.
    var mapSlot: AnyView? = nil

    var body: some View {
        let f = forecast
        VStack(alignment: .leading, spacing: 0) {
            HeroSection(forecast: f)
            // El compartir vive ahora como icono junto a la estrella del toolbar
            // (el detalle ya tiene muchos datos). "CÓMO LLEGAR" volverá con mapas.
            RockStatusBand(current: f.current).padding(.horizontal, 16).padding(.bottom, 8)
            HeatmapStrip(hours: upcomingHours(f.hours, 24)).padding(16)
            FactorsAccordion(current: f.current, expanded: $factorsExpanded)
            rule
            CurrentWeather(current: f.current)
            rule
            if let mapSlot {
                mapSlot
                rule
            }
            SectionTitle("PRÓXIMAS 16 HORAS")
            HoursGrid(hours: upcomingHours(f.hours, 16)).padding(.vertical, 8)
            ConditionsGrid(current: f.current)
            rule
            SectionTitle("PRÓXIMOS 7 DÍAS")
            ForEach(Array(f.days.prefix(7).enumerated()), id: \.offset) { _, d in
                Button { onSelectDay?(d) } label: { DayRow(day: d) }.buttonStyle(.plain)
                rule
            }
            // "MEJOR DÍA" quitado a petición.
        }
    }

    private var rule: some View { Divider().overlay(Cumbre.rule) }
}

// MARK: - Hero (¿PUEDO ESCALAR HOY?)

private struct HeroSection: View {
    let forecast: Forecast
    var body: some View {
        let c = forecast.current
        let score = Int(c.score)
        let yes = score >= 55
        HStack(alignment: .top, spacing: 12) {
            Text(yes ? "SÍ" : "NO")
                .font(Cumbre.serif(56, .bold))
                .foregroundStyle(yes ? Cumbre.ok : Cumbre.bad)
            VStack(alignment: .leading, spacing: 4) {
                Text("¿PUEDO ESCALAR HOY?").eyebrow()
                if let w = forecast.bestWindow {
                    Text("Óptimo entre \(short(w.start))–\(short(w.end))")
                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("ÍNDICE").eyebrow()
                HStack(alignment: .bottom, spacing: 2) {
                    Text("\(score)")
                        .font(Cumbre.serif(40, .bold))
                        .foregroundStyle(Cumbre.score(score))
                    Text("/100").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        .padding(.bottom, 6)
                }
            }
        }
        .padding(16)
    }
}

// MARK: - Banda de roca

private struct RockStatusBand: View {
    let current: Current
    var body: some View {
        let dry = current.dryRock
        let accent = dry ? Cumbre.ok : Cumbre.bad
        let subtitle = current.drying?.message ?? (dry ? "Lista para escalar" : "Mejor esperar a que seque")
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(dry ? "● ROCA SECA" : "● ROCA HÚMEDA")
                    .font(Cumbre.mono(13, .bold)).foregroundStyle(accent)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
            Spacer()
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(accent.opacity(0.12))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.45), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Heatmap (tira ancha)

private struct HeatmapStrip: View {
    let hours: [HourForecast]
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                Rectangle().fill(Cumbre.score(Int(h.score)))
            }
        }
        .frame(height: 18)
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

// MARK: - Desglose de factores

private struct FactorsAccordion: View {
    let current: Current
    @Binding var expanded: Bool
    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("¿POR QUÉ ESTE ÍNDICE?").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if expanded {
                VStack(spacing: 0) {
                    ForEach(Array(current.factors.enumerated()), id: \.offset) { _, f in
                        HStack(spacing: 10) {
                            Image(systemName: f.passes ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(f.passes ? Cumbre.ok : Cumbre.bad)
                            Text(f.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            Spacer()
                            Text(f.display).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
                .padding(.bottom, 8)
            }
        }
    }
}

// MARK: - Tiempo actual

private struct CurrentWeather: View {
    let current: Current
    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            Text("\(Int(current.temperature))°")
                .font(Cumbre.serif(56, .bold)).foregroundStyle(Cumbre.ink)
            VStack(alignment: .leading, spacing: 2) {
                Text(cloudLabel(Int(current.cloudCover)))
                    .font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("VIENTO \(Int(current.windSpeed)) km/h  ·  HUM \(Int(current.humidity))%")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(16)
    }
}

// MARK: - Próximas 16 h

private struct HoursGrid: View {
    let hours: [HourForecast]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                    VStack(spacing: 6) {
                        Text(short(h.time)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        WmoIcon(code: Int(h.weatherCode), size: 22, tint: Cumbre.ink2)
                        RoundedRectangle(cornerRadius: 2).fill(Cumbre.score(Int(h.score)))
                            .frame(width: 30, height: 30)
                            .overlay(Text("\(Int(h.score))").font(Cumbre.mono(11, .bold)).foregroundStyle(.white))
                        Text("\(Int(h.temperature))°").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink2)
                        // Lluvia en mm (solo si la hay) — paridad con ForecastBody.
                        if h.precipitation > 0 {
                            Text("\(String(format: "%.1f", h.precipitation)) mm")
                                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.rain)
                        } else {
                            Text(" ").font(Cumbre.mono(9))
                        }
                        // Viento km/h (siempre) — debajo de la lluvia, como Android.
                        Text("\(Int(h.windSpeed)) km/h")
                            .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Condiciones ahora (8 celdas)

private struct ConditionsGrid: View {
    let current: Current
    var body: some View {
        let c = current
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle("CONDICIONES AHORA").padding(.horizontal, 0)
            HStack(spacing: 8) {
                cell("HUMEDAD", "\(Int(c.humidity))", "%")
                cell("VIENTO", "\(Int(c.windSpeed))", "km/h")
                cell("LLUVIA 24H", trim(c.precip24h), "mm")
                cell("NUBES", "\(Int(c.cloudCover))", "%")
            }
            HStack(spacing: 8) {
                cell("LLUVIA 72H", trim(c.precip72h), "mm")
                cell("ROCÍO", c.dewPoint.map { "\(Int(truncating: $0))" } ?? "—", "°")
                cell("PROB LLUVIA", "\(Int(c.precipitationProbability))", "%")
                cell("ROCA", c.dryRock ? "SECA" : "HÚM", "")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func cell(_ label: String, _ value: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Cumbre.mono(9, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
            HStack(alignment: .bottom, spacing: 2) {
                Text(value).font(.system(size: 20, weight: .semibold)).foregroundStyle(Cumbre.ink)
                if !unit.isEmpty { Text(unit).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3).padding(.bottom, 3) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }
}

// MARK: - Día y mejor día

private struct DayRow: View {
    let day: DayForecast
    var body: some View {
        HStack(spacing: 12) {
            Text("\(Int(day.avgScore))")
                .font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.score(Int(day.avgScore)))
                .frame(width: 40, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(dayLabel(day.date)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("MÁX \(Int(day.tempMax))°  ·  MÍN \(Int(day.tempMin))°  ·  \(trim(day.precipitationTotal)) mm")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

private func short(_ iso: String) -> String {
    if let t = iso.firstIndex(of: "T") { return String(iso[iso.index(after: t)...].prefix(5)) }
    return iso
}

/// Devuelve las próximas `count` horas A PARTIR DE LA HORA ACTUAL (no desde el
/// inicio del día). Espejo de "PRÓXIMAS 16 HORAS desde ahora" de Android.
private func upcomingHours(_ hours: [HourForecast], _ count: Int) -> [HourForecast] {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm"
    let now = Date()
    let startIdx = hours.firstIndex { h in
        guard let d = f.date(from: String(h.time.prefix(16))) else { return false }
        return d >= now.addingTimeInterval(-3600) // incluye la hora en curso
    } ?? 0
    return Array(hours[startIdx...].prefix(count))
}

private func trim(_ d: Double) -> String {
    d == d.rounded() ? "\(Int(d))" : String(format: "%.1f", d)
}

private func cloudLabel(_ c: Int) -> String {
    switch c {
    case ..<20: return "Despejado"
    case ..<50: return "Parcialmente nublado"
    case ..<80: return "Mayormente nublado"
    default:    return "Cubierto"
    }
}

private func dayLabel(_ iso: String) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
    guard let d = f.date(from: String(iso.prefix(10))) else { return iso }
    let out = DateFormatter(); out.locale = Locale(identifier: "es_ES"); out.dateFormat = "EEE d MMM"
    return out.string(from: d).capitalized
}

// `Note` (clase Kotlin vía SKIE) ya tiene `id: String`; con esto vale para
// `.sheet(item:)` y `ForEach(id:)` sin envoltorios.
