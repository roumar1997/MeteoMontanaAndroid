import SwiftUI

/// Color por grado de escalada (sistema francés) — espejo exacto de
/// `GradeColor.kt` de Android (que replica la PWA topo-draw.js).
/// ≤5c+ blanco · 6a-6b+ verde · 6c-6c+ azul · 7a-7a+ morado · 7b-7c+ rojo ·
/// ≥8a negro · proyecto/sin grado rosa.
enum GradeColor {
    struct Style { let stroke: Color; let dashed: Bool; let dark: Bool }

    static func style(_ grade: String?) -> Style {
        let g = (grade ?? "").trimmingCharacters(in: .whitespaces).uppercased()
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
}
