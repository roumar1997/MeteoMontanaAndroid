import Foundation

/// Texto traducible con datos variables.
///
/// La clave es el texto en español con un `%@` por cada hueco (sea cual sea su
/// tipo). Se busca en Localizable.strings del idioma activo; si no hay
/// traducción devuelve la propia clave, así que el español funciona sin fichero.
///
/// Por qué no `Text("… \(x)")` a secas: SwiftUI construye la clave con
/// `%lld` / `%@` según el TIPO del dato, y un `Int` donde se esperaba un
/// `String` deja el texto sin traducir en silencio. Aquí todos los huecos son
/// `%@` y se rellenan a mano, sin depender de tipos.
///
/// Admite huecos posicionales (`%1$@`, `%2$@`) para los idiomas que necesiten
/// otro orden.
func L(_ key: String, _ args: Any...) -> String {
    var s = NSLocalizedString(key, comment: "")
    for (i, a) in args.enumerated() {
        let v = String(describing: a)
        let positional = "%\(i + 1)$@"
        if s.contains(positional) {
            s = s.replacingOccurrences(of: positional, with: v)
        } else if let r = s.range(of: "%@") {
            s.replaceSubrange(r, with: v)
        }
    }
    return s
}

/// Orientación ("N", "SO"…): los códigos que viajan a la API no cambian; solo
/// cómo se muestran (en inglés el oeste es W, no O).
func aspectLabel(_ code: String) -> String {
    switch code.trimmingCharacters(in: .whitespaces).uppercased() {
    case "SO": return L("aspect_sw")
    case "O": return L("aspect_w")
    case "NO": return L("aspect_nw")
    default: return code
    }
}

/// Tipo de roca del catálogo (llega en español del servidor); solo se traduce al mostrarlo.
func rockLabel(_ raw: String) -> String {
    switch raw.trimmingCharacters(in: .whitespaces).lowercased() {
    case "granito": return L("rock_granite")
    case "caliza": return L("rock_limestone")
    case "arenisca": return L("rock_sandstone")
    case "basalto": return L("rock_basalt")
    case "conglomerado": return L("rock_conglomerate")
    case "pizarra": return L("rock_slate")
    default:
        let extra = ["Cuarcita": "Quartzite", "Volcánica": "Volcanic", "Caliza / calcoarenita": "Limestone / calcarenite",
                     "Otra / muro barrenado": "Other / drilled wall"]
        return LanguageManager.shared.effectiveCode == "en" ? (extra[raw.trimmingCharacters(in: .whitespaces)] ?? raw) : raw
    }
}

/// Nombres de meses y días en el idioma de la app (símbolos de `Calendar`, no
/// listas escritas a mano). Todos empiezan en enero / domingo.
enum CalendarLabels {
    private static var cal: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.locale = LanguageManager.shared.locale
        return c
    }
    private static func clean(_ s: String) -> String { s.replacingOccurrences(of: ".", with: "") }

    static func monthsShort() -> [String] { cal.shortMonthSymbols.map(clean) }
    static func monthsLong() -> [String] { cal.monthSymbols }
    /// Domingo primero (índice 0 = domingo), como `Calendar.component(.weekday)` - 1.
    static func weekdaysShortSunFirst() -> [String] { cal.shortWeekdaySymbols.map(clean) }
}

/// Textos que genera el SERVIDOR en español dentro de la previsión (desglose
/// "¿Por qué este índice?" y mejor temporada). Se traducen al mostrarlos; lo que
/// no se reconoce se deja igual. Solo actúa en inglés. Parche del lado cliente a
/// propósito (funciona ya contra producción); lo limpio sería que el servidor
/// los redacte según Accept-Language, que la app ya manda.
enum ForecastText {
    private static var english: Bool { LanguageManager.shared.effectiveCode == "en" }

    private static let names: [String: String] = [
        "TEMPERATURA": "TEMPERATURE", "HUMEDAD": "HUMIDITY", "VIENTO": "WIND",
        "LLUVIA 24H": "RAIN 24H", "LLUVIA 72H": "RAIN 72H", "SEQUEDAD AIRE": "AIR DRYNESS", "ROCA": "ROCK",
    ]

    static func factorName(_ name: String) -> String {
        guard english else { return name }
        if let n = names[name] { return n }
        if name.hasPrefix("ROCA · ") { return "ROCK · " + rockLabel(String(name.dropFirst(7))).uppercased() }
        return name
    }

    private static func capture(_ pattern: String, in s: String) -> [String]? {
        guard let re = try? NSRegularExpression(pattern: pattern),
              let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) else { return nil }
        return (1..<m.numberOfRanges).compactMap { Range(m.range(at: $0), in: s).map { String(s[$0]) } }
    }

    static func factorDisplay(_ display: String) -> String {
        guard english else { return display }
        if display == "Roca seca" { return "Dry rock" }
        if display == "Roca húmeda" { return "Damp rock" }
        if let g = capture(#"^Aún templada \((\d+)°\): guarda el calor (~\d+ h) tras el sol$"#, in: display) {
            return "Still warm (\(g[0])°): holds the heat \(g[1]) after sun"
        }
        if let g = capture(#"^Fría \((\d+)°\): buena fricción · se enfría en (~\d+ h)$"#, in: display) {
            return "Cold (\(g[0])°): good friction · cools down in \(g[1])"
        }
        if let g = capture(#"^Templada \((\d+)°\) · inercia (~\d+ h)$"#, in: display) {
            return "Mild (\(g[0])°) · inertia \(g[1])"
        }
        return display
    }

    /// "Enero-Febrero" → "January-February".
    static func monthsIn(_ text: String) -> String {
        guard english else { return text }
        let es = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]
        let long = CalendarLabels.monthsLong()
        var out = text
        for (i, m) in es.enumerated() {
            out = out.replacingOccurrences(of: m, with: long[i].capitalized, options: .caseInsensitive)
        }
        return out
    }
}

/// Comunidad autónoma / región del catálogo ("Comunidad de Madrid"): llega en español
/// del servidor; en inglés se muestra traducida. Admite varias separadas por " / ".
func regionLabel(_ raw: String) -> String {
    guard LanguageManager.shared.effectiveCode == "en" else { return raw }
    let regions: [String: String] = [
        "Cataluña": "Catalonia",
        "Castilla y León": "Castile and León",
        "Aragón": "Aragon",
        "Andalucía": "Andalusia",
        "Comunidad Valenciana": "Valencian Community",
        "Comunidad de Madrid": "Community of Madrid",
        "Galicia": "Galicia",
        "Asturias": "Asturias",
        "País Vasco": "Basque Country",
        "Navarra": "Navarre",
        "Canarias": "Canary Islands",
        "Extremadura": "Extremadura",
        "Islas Baleares": "Balearic Islands",
        "Cantabria": "Cantabria",
        "Región de Murcia": "Region of Murcia",
        "Castilla-La Mancha": "Castilla-La Mancha",
        "Nueva Aquitania": "Nouvelle-Aquitaine",
        "Comunidad Autónoma de Cantabria": "Cantabria",
        "Leon": "León",
        "Burgos": "Burgos",
        "Jaén": "Jaén"
    ]
    return raw.components(separatedBy: " / ")
        .map { regions[$0.trimmingCharacters(in: .whitespaces)] ?? $0.trimmingCharacters(in: .whitespaces) }
        .joined(separator: " / ")
}

/// Estilo del catálogo ("Vía", "Bloque" o "Bloque,Vía").
func styleLabel(_ raw: String) -> String {
    raw.split(separator: ",").map { L($0.trimmingCharacters(in: .whitespaces)) }.joined(separator: ", ")
}
