import SwiftUI
import Shared

/// Carga (y recuerda) la previsión de una escuela para pintar «seca en ~X h». El cálculo es de shared (`RockDryOutlook`).
@MainActor
enum RockDryOutlookCache {
    private static var cache: [String: RockDryOutlook] = [:]

    static func outlook(_ schoolId: String) async -> RockDryOutlook? {
        if let o = cache[schoolId] { return o }
        guard let f = try? await AppDependencies.shared.container.getForecast.invoke(schoolId: schoolId) else { return nil }
        let o = RockDryOutlook.Companion.shared.of(current: f.current, days: f.days, today: GymDates.local(Date()))
        cache[schoolId] = o
        return o
    }
}

enum RockDryText {
    static let wetBlue = Color(hex: 0x2563C7)
    static let dubiousAmber = Color(hex: 0xD9962B)

    static func dayName(_ d: Kotlinx_datetimeLocalDate, full: Bool = true) -> String {
        let f = DateFormatter()
        f.locale = LanguageManager.shared.locale
        f.dateFormat = full ? "EEEE" : "EEE"
        return f.string(from: GymDates.date(d)).replacingOccurrences(of: ".", with: "").lowercased()
    }

    /// Texto principal y secundario del estado de la roca (espejo de `rockDryTexts`).
    static func texts(_ o: RockDryOutlook) -> (String, String?) {
        if o.wet, let h = o.dryInHours { return (L("dry_wet_hours", h), nil) }
        if o.wet, let d = o.dryFromDate { return (L("dry_wet_day", dayName(d)), nil) }
        if o.wet { return (L("dry_wet"), nil) }
        if let w = o.worsensOnDate {
            let head = o.dryUntilDate.map { L("dry_until", dayName($0)) } ?? L("dry_now")
            return (head, L("dry_worsens", dayName(w)))
        }
        return (L("dry_now"), L("dry_all_week"))
    }

    /// El estado llega dentro de un Pair de Kotlin: se compara por su nombre (WET, DUBIOUS, DRY).
    static func color(_ s: String) -> Color {
        switch s {
        case "WET": return wetBlue
        case "DUBIOUS": return dubiousAmber
        default: return Cumbre.ok
        }
    }

    static func icon(_ s: String) -> String {
        switch s {
        case "WET": return "drop"
        case "DUBIOUS": return "cloud"
        default: return "sun.max"
        }
    }
}

/// Franja de 5 días (sol seca, nube dudosa, gota mojada; hoy recuadrado) y el resumen en una línea.
struct RockDryBanner: View {
    let outlook: RockDryOutlook
    var body: some View {
        let (title, sub) = RockDryText.texts(outlook)
        VStack(alignment: .leading, spacing: 8) {
            if !outlook.strip.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(outlook.strip.enumerated()), id: \.offset) { i, pair in
                        if let date = pair.first, let raw = pair.second {
                            let state = String(describing: raw).uppercased()
                            let c = RockDryText.color(state)
                            VStack(spacing: 2) {
                                Image(systemName: RockDryText.icon(state)).font(.system(size: 15)).foregroundStyle(c)
                                Text((i == 0 ? L("dry_today") : RockDryText.dayName(date, full: false)).uppercased())
                                    .font(.system(size: 9, weight: .bold)).foregroundStyle(c).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 7)
                            .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(i == 0 ? Cumbre.terra : Color.clear, lineWidth: 2))
                        }
                    }
                }
            }
            HStack(alignment: .top, spacing: 6) {
                Image(systemName: "clock").font(.system(size: 13)).foregroundStyle(outlook.wet ? RockDryText.wetBlue : Cumbre.ok)
                Text([title, sub].compactMap { $0 }.joined(separator: " · ")).font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Cumbre.ink2)
            }
        }
    }
}

/// Pastillas compactas de seca/mojada de la lista: «Mojada · seca en ~6 h», «Seca hasta el viernes» + «Empeora sábado».
struct SchoolDryPill: View {
    let schoolId: String
    let dry: Bool
    @State private var outlook: RockDryOutlook?

    var body: some View {
        HStack(spacing: 6) {
            if let o = outlook {
                let c = o.wet ? rainBlue : Cumbre.ok
                pill(RockDryText.texts(o).0, c)
                if !o.wet, let w = o.worsensOnDate { pill(L("dry_chip_worsens", RockDryText.dayName(w)), rainBlue) }
            } else {
                pill(dry ? L("Roca seca") : L("Roca mojada"), dry ? Cumbre.ok : rainBlue)
            }
        }
        .task(id: schoolId) { outlook = await RockDryOutlookCache.outlook(schoolId) }
    }

    private func pill(_ text: String, _ c: Color) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(c).lineLimit(1)
            .padding(.horizontal, 9).padding(.vertical, 3).background(c.opacity(0.14), in: Capsule())
    }
}
