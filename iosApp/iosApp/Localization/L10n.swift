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
    default: return raw
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
