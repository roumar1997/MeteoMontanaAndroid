import SwiftUI
import UIKit

/// Tokens del tema Cumbre — espejo de `ui/theme/Color.kt` de Android.
/// Papel, tinta, terracota. Sin gradientes, sin sombras, bordes 1pt color Rule.
/// Los colores son DINÁMICOS: cambian solos entre claro y oscuro según el
/// colorScheme efectivo (lo fija `ThemeManager` en la raíz). Paletas idénticas
/// a las de Color.kt (light + dark).
enum Cumbre {
    // light, dark — espejo exacto de Color.kt
    static let bg      = dyn(0xF5F3EE, 0x15140F)
    static let paper   = dyn(0xEBE7DD, 0x1D1C17)
    static let paper2  = dyn(0xF0EAD8, 0x211F19)
    static let ink     = dyn(0x1C1C1A, 0xECE7D8)
    static let ink2    = dyn(0x5A574F, 0xA8A397)
    static let ink3    = dyn(0x8A8478, 0x6E6A5F)
    static let rule    = dyn(0xD6D2C4, 0x2A281F)
    static let terra   = dyn(0xC2410C, 0xE0612B)
    static let terraBg = dyn(0xFDE4D3, 0x2A1A10)
    static let ok      = dyn(0x3F6B4A, 0x7DA068)
    static let warn    = dyn(0xB45309, 0xD6904A)
    static let bad     = dyn(0x9A3412, 0xC9543B)
    static let rain    = dyn(0x2563C7, 0x5B8AE0)
    static let wind    = dyn(0x4A7C3F, 0x7D8A6A)

    /// Color que se adapta a claro/oscuro automáticamente.
    static func dyn(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { tc in
            tc.userInterfaceStyle == .dark ? UIColor(rgb: dark) : UIColor(rgb: light)
        })
    }

    /// Etiqueta del score — espejo de `scoreLabel()` de SchoolListItem.kt.
    static func scoreLabel(_ s: Int?) -> String {
        guard let s else { return "" }
        switch s {
        case 85...:   return "EXCELENTE"
        case 70..<85: return "MUY BUENO"
        case 55..<70: return "BUENO"
        case 40..<55: return "REGULAR"
        default:      return "MALO"
        }
    }

    /// Color por score — espejo exacto de `scoreColor()` de Color.kt.
    static func score(_ s: Int) -> Color {
        switch s {
        case 90...:   return Color(hex: 0x3F6B4A)
        case 80..<90: return Color(hex: 0x5B7E3F)
        case 70..<80: return Color(hex: 0x7D9A4E)
        case 60..<70: return Color(hex: 0xB48A2E)
        case 50..<60: return Color(hex: 0xB45309)
        case 40..<50: return Color(hex: 0xA0420B)
        case 30..<40: return Color(hex: 0x9A3412)
        case 20..<30: return Color(hex: 0x7C2410)
        default:      return Color(hex: 0x5A1E08)
        }
    }
}

// MARK: - Fuentes (mismas familias que Android)

extension Cumbre {
    /// Source Serif 4 — nombres de escuela, títulos, números de score.
    static func serif(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy, .black: name = "SourceSerif4-Bold"
        case .semibold, .medium:    name = "SourceSerif4-Semibold"
        default:                    name = "SourceSerif4-Regular"
        }
        return .custom(name, size: size)
    }

    /// JetBrains Mono — eyebrows, dígitos, etiquetas técnicas.
    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy, .black, .semibold: name = "JetBrainsMono-Bold"
        default:                               name = "JetBrainsMono-Regular"
        }
        return .custom(name, size: size)
    }
}

extension Color {
    /// Construye un Color desde un entero hex 0xRRGGBB.
    init(hex: UInt32) {
        self.init(
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue:  Double(hex & 0xFF) / 255
        )
    }
}

extension UIColor {
    /// Construye un UIColor desde un entero hex 0xRRGGBB (para colores dinámicos).
    convenience init(rgb: UInt32) {
        self.init(
            red:   CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue:  CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Estilo "eyebrow" Cumbre: mono, mayúsculas, tracking ancho.
extension View {
    func eyebrow(_ color: Color = Cumbre.ink3) -> some View {
        self.font(Cumbre.mono(10, .bold))
            .tracking(1.8)
            .foregroundStyle(color)
    }
}
