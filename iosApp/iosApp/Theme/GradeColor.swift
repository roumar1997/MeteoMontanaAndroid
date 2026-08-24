import SwiftUI

/// Color por grado de escalada (sistema francés) — espejo exacto de
/// `GradeColor.kt` de Android (que replica la PWA topo-draw.js).
/// ≤5c+ blanco · 6a-6b+ verde · 6c-6c+ azul · 7a-7a+ morado · 7b-7c+ rojo ·
/// ≥8a negro · proyecto/sin grado rosa.
enum GradeColor {
    struct Style { let stroke: Color; let dashed: Bool; let dark: Bool }

    static func style(_ grade: String?) -> Style {
        // Grado DOBLE ("7a/7a+"): colorea como el PRIMERO del rango — espejo de
        // GradeRange.base() del shared. Sin esto caía en el rosa de "proyecto"
        // por no encajar en el patrón (Álvaro, 2026-08-24).
        let g = GradeRangeUI.base(grade) ?? ""
        let project = Style(stroke: Color(hex: 0xFF4FA3), dashed: true, dark: false)
        if g.isEmpty || g == "PROY" || g == "PROYECTO" || g == "?" { return project }

        // ^([3-9])([ABCD])?(\+)?$
        let chars = Array(g)
        guard let first = chars.first, let num = first.wholeNumberValue, (3...9).contains(num) else { return project }
        var idx = 1
        var letterScore = 0
        if idx < chars.count, let l = "ABCD".firstIndex(of: chars[idx]) {
            letterScore = "ABCD".distance(from: "ABCD".startIndex, to: l)
            idx += 1
        }
        var plus = 0
        if idx < chars.count, chars[idx] == "+" { plus = 1; idx += 1 }
        if idx != chars.count { return project }  // sobra algo → no encaja

        let score = num * 100 + letterScore * 10 + plus
        switch score {
        case ...521: return Style(stroke: Color(hex: 0xFFFFFF), dashed: false, dark: true)
        case ...611: return Style(stroke: Color(hex: 0x1FA84E), dashed: false, dark: false)
        case ...621: return Style(stroke: Color(hex: 0x1D6DD6), dashed: false, dark: false)
        case ...701: return Style(stroke: Color(hex: 0x8E3FBF), dashed: false, dark: false)
        case ...721: return Style(stroke: Color(hex: 0xD62828), dashed: false, dark: false)
        default:     return Style(stroke: Color(hex: 0x111111), dashed: false, dark: false)
        }
    }

    static func color(_ grade: String?) -> Color { style(grade).stroke }

    /// El color del grado ADAPTADO al tema, para pintarlo FUERA de una foto
    /// (chips, badges, listas). Sobre la foto de la roca se usa `color(_:)`
    /// tal cual, que ahí siempre hay contraste.
    ///
    /// En oscuro los dos extremos se perdían: el negro de ≥8a se fundía con el
    /// fondo y el blanco de ≤5c+ deslumbraba (Álvaro, 2026-08-24). Espejo de
    /// `gradeChipColor` en GradeColor.kt.
    static func chip(_ grade: String?) -> Color {
        let s = style(grade)
        if s.dark { return Cumbre.dyn(0xFFFFFF, 0xD8D3C4) }   // ≤5c+: blanco → hueso
        // ≥8a es casi negro: se detecta por luminancia, no comparando Color
        // (la igualdad de Color no es de fiar entre construcciones distintas).
        var w: CGFloat = 1
        UIColor(s.stroke).getWhite(&w, alpha: nil)
        if w < 0.12 { return Cumbre.dyn(0x111111, 0x4A4A46) }  // negro → gris piedra
        return s.stroke
    }
}
