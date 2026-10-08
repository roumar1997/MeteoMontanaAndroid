import SwiftUI
import Shared

/// Tarjeta del tiempo del asistente: responde a "¿va a llover donde estoy?", "¿qué tal el viento en
/// Zarzalejo?", "¿cómo ves para escalar hoy en…?". Enseña siempre lo esencial (temperatura, viento,
/// humedad, lluvia próxima y cómo está para escalar) y destaca lo que se preguntó en la frase de arriba.
/// Son los mismos datos y el mismo índice que la pantalla del tiempo: no pueden contradecirse.
struct AssistantWeatherCard: View {
    let weather: AssistantWeather

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            rainBanner
            if !weather.hours.isEmpty { hourStrip }
            climbingRow
        }
        .padding(12)
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }

    // MARK: Cabecera: sitio + temperatura + viento y humedad

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 5) {
                        if weather.myLocation {
                            Image(systemName: "location.fill")
                                .font(.system(size: 10)).foregroundStyle(Cumbre.terra)
                        }
                        Text(AssistantPresenter.weatherTitle(weather))
                            .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    }
                    Text(L("AHORA"))
                        .font(Cumbre.mono(9, .bold)).tracking(1.6).foregroundStyle(Cumbre.ink3)
                }
                Spacer(minLength: 8)
                Text("\(Int(weather.now.temperature.rounded()))°")
                    .font(Cumbre.serif(34, .bold)).foregroundStyle(Cumbre.ink)
            }
            HStack(spacing: 8) {
                metric("wind", "\(Int(weather.now.windKmh.rounded())) km/h", highlight: weather.topic == "WIND")
                metric("humidity", "\(Int(weather.now.humidity.rounded())) %", highlight: weather.topic == "HUMIDITY")
                metric("cloud", "\(Int(weather.now.cloudCover)) %", highlight: false)
            }
        }
    }

    private func metric(_ icon: String, _ value: String, highlight: Bool) -> some View {
        let symbol: String = {
            switch icon {
            case "wind": return "wind"
            case "humidity": return "humidity"
            default: return "cloud"
            }
        }()
        return HStack(spacing: 4) {
            Image(systemName: symbol).font(.system(size: 11))
            Text(value).font(Cumbre.mono(11, .bold))
        }
        .foregroundStyle(highlight ? .white : Cumbre.ink2)
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(highlight ? Cumbre.terra : Cumbre.bg)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(highlight ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
    }

    // MARK: Lluvia próxima

    private var rainBanner: some View {
        let rain = weather.rain
        let wet = rain.expected
        return HStack(alignment: .top, spacing: 8) {
            Image(systemName: wet ? "cloud.rain.fill" : "sun.max.fill")
                .font(.system(size: 14)).foregroundStyle(wet ? Cumbre.rain : Cumbre.ok)
            Text(AssistantPresenter.rainHeadline(weather))
                .font(.system(size: 13, weight: weather.topic == "RAIN" ? .semibold : .regular))
                .foregroundStyle(Cumbre.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background((wet ? Cumbre.rain : Cumbre.ok).opacity(0.10))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke((wet ? Cumbre.rain : Cumbre.ok).opacity(0.35), lineWidth: 1))
    }

    // MARK: Tira de horas (barras = lluvia prevista)

    private var hourStrip: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L("PRÓXIMAS HORAS"))
                .font(Cumbre.mono(9, .bold)).tracking(1.4).foregroundStyle(Cumbre.ink3)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(Array(weather.hours.enumerated()), id: \.offset) { _, h in
                        VStack(spacing: 3) {
                            Text("\(Int(h.rainProbability))%")
                                .font(Cumbre.mono(8, .bold))
                                .foregroundStyle(h.rainProbability >= 60 ? Cumbre.rain : Cumbre.ink3)
                            ZStack(alignment: .bottom) {
                                Rectangle().fill(Cumbre.rule.opacity(0.35)).frame(width: 14, height: 34)
                                Rectangle().fill(Cumbre.rain)
                                    .frame(width: 14, height: AssistantPresenter.rainBarHeight(mm: h.precipitationMm, full: 34))
                            }
                            Text("\(Int(h.temperature.rounded()))°")
                                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink)
                            Text(AssistantPresenter.hourLabel(h.time))
                                .font(Cumbre.mono(8, .bold)).foregroundStyle(Cumbre.ink3)
                        }
                        .frame(width: 26)
                    }
                }
            }
        }
    }

    // MARK: Cómo está para escalar

    private var climbingRow: some View {
        let c = weather.climbing
        return HStack(spacing: 10) {
            Text("\(Int(c.score))")
                .font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.score(Int(c.score)))
                .frame(width: 40, height: 34)
                .overlay(Rectangle().stroke(Cumbre.score(Int(c.score)), lineWidth: 1))
            VStack(alignment: .leading, spacing: 2) {
                Text(L("Para escalar: %@", c.label))
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink)
                if let drying = c.dryingMessage, !drying.isEmpty {
                    Text(drying).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                } else {
                    Text(c.rockWet ? L("La roca está húmeda.") : L("La roca está seca."))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
                if let a = c.bestWindowStart, let b = c.bestWindowEnd {
                    Text(L("Mejor ventana: %@–%@", AssistantPresenter.hourLabel(a), AssistantPresenter.hourLabel(b)))
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer(minLength: 0)
        }
    }
}
