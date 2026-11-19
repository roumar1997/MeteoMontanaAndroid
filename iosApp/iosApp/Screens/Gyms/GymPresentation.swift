import Foundation
import Shared

/// Textos derivados de una ficha de rocódromo. Espejo de `GymPresentation.kt`: las claves son las de
/// docs/rocodromos/TEXTOS.md y la lógica de verdad (agrupado de días, cuándo hay precio, enlaces) viene de
/// `shared`; aquí solo se ponen las palabras del idioma activo.
enum GymPresentation {

    static func kindLabel(_ kind: GymKind) -> String {
        switch kind {
        case .municipal: return L("gym_kind_municipal")
        case .free: return L("gym_kind_free")
        default: return L("gym_kind_commercial")
        }
    }

    /// Modalidad como etiquetas sueltas (Bloque, Cuerda…); un muro al aire libre suma la suya.
    static func disciplineChips(_ gym: Gym) -> [String] {
        var chips: [String] = []
        switch gym.discipline {
        case .boulder: chips.append(L("gym_disc_boulder"))
        case .rope: chips.append(L("gym_disc_rope"))
        case .both: chips += [L("gym_disc_boulder"), L("gym_disc_rope")]
        default: break
        }
        if gym.outdoor { chips.append(L("gym_disc_outdoor")) }
        return chips
    }

    /// «Gratis» / «De pago» / nada (un municipal sin precio no se etiqueta: no se sabe).
    static func priceTag(_ gym: Gym) -> String? {
        if gym.isFree { return L("gym_free_tag") }
        if gym.kind == .commercial || (gym.priceSingleEur?.doubleValue ?? 0) > 0 { return L("gym_pay") }
        return nil
    }

    /// «850 m» por debajo del kilómetro, «18 km» por encima; nil sin ubicación.
    static func distanceText(_ km: Double?) -> String? {
        guard let d = GymDistance.companion.of(km: km.map { KotlinDouble(value: $0) }) else { return nil }
        return L(d.inMeters ? "gym_distance_m" : "gym_distance_km", Int(d.value))
    }

    /// «Abierto · hasta 23:00», «Cerrado · abre a las 10:00», «Cerrado»; nil si no hay horario (no se afirma nada).
    static func openStatusText(_ status: GymOpenStatus) -> String? {
        if let open = status as? GymOpenStatus.Open { return L("gym_open_until", open.closesAt) }
        if let closed = status as? GymOpenStatus.Closed {
            if let opens = closed.opensAt { return L("gym_closed_opens", opens) }
            return L("gym_closed")
        }
        return nil
    }

    static func verificationText(_ v: GymVerification) -> String {
        v == .unconfirmed ? L("gym_unknown") : L("gym_verified")
    }

    /// Servicio sí/no/no se sabe: nunca «no» por un nulo.
    static func serviceValue(_ value: KotlinBoolean?) -> String {
        guard let value else { return L("gym_unknown") }
        return value.boolValue ? L("gym_yes") : L("gym_no")
    }

    // MARK: - Horario

    /// Una línea por tramo («L–V 10:00–23:00»); vacío si la ficha no trae horario.
    static func hoursLines(_ slots: [OpeningSlot]?) -> [String] {
        GymHoursFormatter.shared.entries(slots: slots, labels: dayLabels()).map {
            L("gym_hours_line", $0.daysLabel, $0.open, $0.close)
        }
    }

    private static func dayLabels() -> GymDayLabels {
        // shortWeekdaySymbols empieza en domingo; shared espera lunes..domingo.
        let sunFirst = CalendarLabels.weekdaysShortSunFirst()
        let monFirst = Array(sunFirst.dropFirst()) + Array(sunFirst.prefix(1))
        return GymDayLabels(monFri: L("gym_hours_mon_fri"), satSun: L("gym_hours_sat_sun"), sat: L("gym_hours_sat"),
                            sun: L("gym_hours_sun"), daily: L("gym_hours_daily"), shortNames: monFirst)
    }

    // MARK: - Precio

    /// Líneas de precio y nota de fecha/fiabilidad. `lines` vacío = «Sin dato de precio todavía».
    struct PriceInfo {
        let lines: [String]
        let note: String?
    }

    /// Sin fecha de revisión no se enseña el precio; un muro gratis dice «Entrada gratuita» sin fecha.
    static func priceInfo(_ gym: Gym) -> PriceInfo {
        guard let price = GymPrice.companion.of(gym: gym) else { return PriceInfo(lines: [], note: nil) }
        if price.isFree { return PriceInfo(lines: [L("gym_price_free")], note: nil) }
        guard let iso = price.reviewedAtIso, let date = formatDate(iso) else { return PriceInfo(lines: [], note: nil) }
        var lines: [String] = []
        if let single = price.singleEur { lines.append(L("gym_price_single", amount(single.doubleValue))) }
        if let pass = price.passEur {
            lines.append(L("gym_price_pass", price.passLabel ?? L("gym_price_pass_default"), amount(pass.doubleValue)))
        }
        var note = L("gym_price_asof", date)
        if price.unverified { note += " " + L("gym_price_unverified") }
        return PriceInfo(lines: lines, note: note)
    }

    /// 12.0 → «12»; 12.5 → «12,50» (coma en español, punto en inglés).
    private static func amount(_ v: Double) -> String {
        let separator = LanguageManager.shared.locale.decimalSeparator ?? ","
        return GymPrice.companion.amountText(v: v, decimalSeparator: separator)
    }

    /// Fecha ISO (yyyy-MM-dd) en formato medio del idioma activo; nil si no se entiende.
    private static func formatDate(_ iso: String) -> String? {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: iso) else { return nil }
        let out = DateFormatter()
        out.locale = LanguageManager.shared.locale
        out.dateStyle = .medium
        out.timeStyle = .none
        return out.string(from: date)
    }

    // MARK: - Enlaces

    static func webUrl(_ raw: String) -> URL? { URL(string: GymLinks.shared.webUrl(raw: raw)) }
    static func instagramUrl(_ raw: String) -> URL? { URL(string: GymLinks.shared.instagramUrl(raw: raw)) }

    /// «Cadena · Dirección» o, sin ellas, «Ciudad». Lo que falte se omite.
    static func subtitle(_ gym: Gym) -> String? {
        var parts: [String] = []
        if let chain = gym.chain, !chain.trimmingCharacters(in: .whitespaces).isEmpty { parts.append(chain) }
        if let address = gym.address, !address.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append(address)
        } else if !gym.city.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append(gym.city)
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}
