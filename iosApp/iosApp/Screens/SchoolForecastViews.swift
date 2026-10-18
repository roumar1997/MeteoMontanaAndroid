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
    /// Ficha de escuela (2026-10-13): tras la puntuación y las próximas 16 horas van PESTAÑAS (mapa, bloques,
    /// tiempo, notas) en vez de una columna larga. Si se pasa, sustituye al mapa suelto y al resto del cuerpo.
    var tabsSlot: AnyView? = nil
    /// Tarjeta HOY plegable de la ficha con pestañas (nil = la ficha de siempre). La ficha la abre sola en TIEMPO.
    var hoyOpen: Binding<Bool>? = nil

    var body: some View {
        let f = forecast
        VStack(alignment: .leading, spacing: 0) {
            if let tabsSlot {
                // Ficha de escuela: UNA tarjeta HOY (SÍ/NO, índice, roca, óptimo) que se despliega con las 16 horas
                // y el "por qué"; así las pestañas y el mapa empiezan mucho más arriba.
                HoyCard(forecast: f, open: hoyOpen ?? .constant(false), factorsExpanded: $factorsExpanded)
                    .guideTarget("detail.hoy")
                tabsSlot
            } else {
                HeroSection(forecast: f)
                classicBody(f)
            }
        }
    }

    /// El "TIEMPO" de siempre (ahora una pestaña): condiciones de ahora y los próximos 7 días.
    static func weatherTab(_ f: Forecast, onSelectDay: ((DayForecast) -> Void)?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HeatmapStrip(hours: upcomingHours(f.hours, 24)).padding(16)
                .guideTarget("detail_weather.heat")
            Divider().overlay(Cumbre.rule)
            CurrentWeather(current: f.current)
                .guideTarget("detail_weather.now")
            Divider().overlay(Cumbre.rule)
            ConditionsGrid(current: f.current)
            Divider().overlay(Cumbre.rule)
            SectionTitle(L("PRÓXIMOS 7 DÍAS"))
                .guideTarget("detail_weather.days")
            ForEach(Array(f.days.prefix(7).enumerated()), id: \.offset) { _, d in
                Button { onSelectDay?(d) } label: { DayRow(day: d) }.buttonStyle(.plain)
                Divider().overlay(Cumbre.rule)
            }
        }
    }

    @ViewBuilder private func classicBody(_ f: Forecast) -> some View {
        VStack(alignment: .leading, spacing: 0) {
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
            SectionTitle(L("PRÓXIMAS 16 HORAS"))
            HoursGrid(hours: upcomingHours(f.hours, 16)).padding(.vertical, 8)
            ConditionsGrid(current: f.current)
            rule
            SectionTitle(L("PRÓXIMOS 7 DÍAS"))
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
                    Text(L("Óptimo entre %@–%@", short(w.start), short(w.end)))
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

// MARK: - Tarjeta HOY (ficha de escuela con pestañas)

/// Plegada: SÍ/NO + índice + estado de la roca + ventana óptima en UNA fila. Desplegada: además el motivo de
/// la roca, el "¿por qué este índice?" y las próximas 16 horas.
private struct HoyCard: View {
    let forecast: Forecast
    @Binding var open: Bool
    @Binding var factorsExpanded: Bool

    var body: some View {
        let c = forecast.current
        let score = Int(c.score)
        let yes = score >= 55
        let dry = c.dryRock
        let accent = dry ? Cumbre.ok : Cumbre.bad
        let subtitle = c.drying?.message ?? (dry ? L("Lista para escalar") : L("Mejor esperar a que seque"))
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation(.easeInOut(duration: 0.2)) { open.toggle() } } label: {
                HStack(spacing: 10) {
                    Text(yes ? "SÍ" : "NO").font(Cumbre.serif(26, .bold)).foregroundStyle(yes ? Cumbre.ok : Cumbre.bad)
                    HStack(alignment: .lastTextBaseline, spacing: 1) {
                        Text("\(score)").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.score(score))
                        Text("/100").font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(dry ? L("● ROCA SECA") : L("● ROCA HÚMEDA")).font(Cumbre.mono(10.5, .bold)).foregroundStyle(accent)
                        if let w = forecast.bestWindow {
                            Text(L("Óptimo %@–%@", short(w.start), short(w.end)))
                                .font(.system(size: 11.5)).foregroundStyle(Cumbre.ink2).lineLimit(1).minimumScaleFactor(0.8)
                        }
                    }
                    Spacer(minLength: 4)
                    Image(systemName: open ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                Divider().overlay(Cumbre.rule)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 12).padding(.top, 8)
                // Al abrir la tarjeta sale TODO a la vez (porqué del índice + mejor hora por sector), con títulos grandes
                // para que se lea de un vistazo (Álvaro, 2026-10-18: «muchísimos despliegues» y «con otra letra»).
                FactorsAccordion(current: c, expanded: $factorsExpanded, compact: true, alwaysOpen: true)
                // Mejor hora por sector: se pide ahora, al abrir la tarjeta (no al entrar en la escuela).
                SectorHoursSection(schoolId: forecast.schoolId, alwaysOpen: true)
                HoursGrid(hours: upcomingHours(forecast.hours, 16), compact: true).padding(.top, 2).padding(.bottom, 10)
            }
        }
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 12).padding(.vertical, 6)
    }
}

// MARK: - Banda de roca

private struct RockStatusBand: View {
    let current: Current
    var body: some View {
        let dry = current.dryRock
        let accent = dry ? Cumbre.ok : Cumbre.bad
        let subtitle = current.drying?.message ?? (dry ? L("Lista para escalar") : L("Mejor esperar a que seque"))
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(dry ? L("● ROCA SECA") : L("● ROCA HÚMEDA"))
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
    /// Ficha de escuela: la misma cabecera pero más baja y con letra más pequeña.
    var compact = false
    /// Dentro de la tarjeta HOY: sin desplegable, siempre abierto y con el título llamativo.
    var alwaysOpen = false
    var body: some View {
        VStack(spacing: 0) {
            if alwaysOpen {
                Text("¿POR QUÉ ESTE ÍNDICE?").font(Cumbre.serif(17, .bold)).tracking(0.5).foregroundStyle(Cumbre.terra)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 6)
            } else {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("¿POR QUÉ ESTE ÍNDICE?").font(Cumbre.mono(compact ? 10 : 12, .bold)).foregroundStyle(Cumbre.ink2)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, compact ? 6 : 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            }
            if expanded || alwaysOpen {
                VStack(spacing: 0) {
                    ForEach(Array(current.factors.enumerated()), id: \.offset) { _, f in
                        HStack(spacing: 10) {
                            Image(systemName: f.passes ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(f.passes ? Cumbre.ok : Cumbre.bad)
                            Text(ForecastText.factorName(f.name)).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            Spacer()
                            Text(ForecastText.factorDisplay(f.display)).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
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
                Text(L("VIENTO %@ km/h  ·  HUM %@%", Int(current.windSpeed), Int(current.humidity)))
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
    /// Ficha de escuela: misma información (con el viento) pero más apretada para ganar altura.
    var compact = false
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                    VStack(spacing: compact ? 3 : 6) {
                        Text(short(h.time)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        WmoIcon(code: Int(h.weatherCode), size: compact ? 18 : 22, tint: Cumbre.ink2)
                        RoundedRectangle(cornerRadius: 2).fill(Cumbre.score(Int(h.score)))
                            .frame(width: 30, height: compact ? 24 : 30)
                            .overlay(Text("\(Int(h.score))").font(Cumbre.mono(11, .bold)).foregroundStyle(.white))
                        Text("\(Int(h.temperature))°").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink2)
                        // Lluvia en mm (solo si la hay) — paridad con ForecastBody.
                        if h.precipitation > 0 {
                            Text("\(String(format: "%.1f", h.precipitation)) mm")
                                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.rain)
                        } else if !compact {
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
            SectionTitle(L("CONDICIONES AHORA")).padding(.horizontal, 0)
            HStack(spacing: 8) {
                cell(L("HUMEDAD"), "\(Int(c.humidity))", "%")
                cell(L("VIENTO"), "\(Int(c.windSpeed))", "km/h")
                cell(L("LLUVIA 24H"), trim(c.precip24h), "mm")
                cell(L("NUBES"), "\(Int(c.cloudCover))", "%")
            }
            HStack(spacing: 8) {
                cell(L("LLUVIA 72H"), trim(c.precip72h), "mm")
                cell(L("ROCÍO"), c.dewPoint.map { "\(Int(truncating: $0))" } ?? "—", "°")
                cell(L("PROB LLUVIA"), "\(Int(c.precipitationProbability))", "%")
                cell(L("ROCA"), c.dryRock ? L("SECA") : L("HÚM"), "")
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
                Text(L("MÁX %@°  ·  MÍN %@°  ·  %@ mm", Int(day.tempMax), Int(day.tempMin), trim(day.precipitationTotal)))
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
private let hourFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm"
    return f
}()
private let dayInFormatter: DateFormatter = {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f
}()
private let dayOutFormatter: DateFormatter = {
    let f = DateFormatter(); f.locale = LanguageManager.shared.locale; f.dateFormat = "EEE d MMM"; return f
}()

private func upcomingHours(_ hours: [HourForecast], _ count: Int) -> [HourForecast] {
    let f = hourFormatter
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
    case ..<20: return L("Despejado")
    case ..<50: return L("Parcialmente nublado")
    case ..<80: return L("Mayormente nublado")
    default:    return L("Cubierto")
    }
}

private func dayLabel(_ iso: String) -> String {
    guard let d = dayInFormatter.date(from: String(iso.prefix(10))) else { return iso }
    return dayOutFormatter.string(from: d).capitalized
}

// `Note` (clase Kotlin vía SKIE) ya tiene `id: String`; con esto vale para
// `.sheet(item:)` y `ForEach(id:)` sin envoltorios.
