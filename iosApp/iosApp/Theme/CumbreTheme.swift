import SwiftUI

/// Tokens del tema Cumbre — espejo de `ui/theme/Color.kt` de Android.
/// Papel, tinta, terracota. Sin gradientes, sin sombras, bordes 1pt color Rule.
enum Cumbre {
    static let bg      = Color(hex: 0xF5F3EE)
    static let paper   = Color(hex: 0xEBE7DD)
    static let paper2  = Color(hex: 0xF0EAD8)
    static let ink     = Color(hex: 0x1C1C1A)
    static let ink2    = Color(hex: 0x5A574F)
    static let ink3    = Color(hex: 0x8A8478)
    static let rule    = Color(hex: 0xD6D2C4)
    static let terra   = Color(hex: 0xC2410C)
    static let terraBg = Color(hex: 0xFDE4D3)
    static let ok      = Color(hex: 0x3F6B4A)
    static let warn    = Color(hex: 0xB45309)
    static let bad     = Color(hex: 0x9A3412)
    static let rain    = Color(hex: 0x2563C7)
    static let wind    = Color(hex: 0x4A7C3F)

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

/// Estilo "eyebrow" Cumbre: mono, mayúsculas, tracking ancho.
extension View {
    func eyebrow(_ color: Color = Cumbre.ink3) -> some View {
        self.font(.system(size: 10, weight: .bold, design: .monospaced))
            .tracking(1.8)
            .foregroundStyle(color)
    }
}
