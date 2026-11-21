import SwiftUI
import Shared

/// Colores de los distintivos FLASH (dorado) y A VISTA (azul); los mismos de Android (`FlashBadge.kt`, `OnsightBadge.kt`).
enum StyleBadgeColors {
    static let flashStart = Color(hex: 0xF6C744)
    static let flashEnd = Color(hex: 0xF59E0B)
    static let flashBrown = Color(hex: 0x3B2300)
    static let onsightStart = Color(hex: 0x5EC8E5)
    static let onsightEnd = Color(hex: 0x2F80ED)
    static let onsightNavy = Color(hex: 0x062A52)
    static var flash: LinearGradient { LinearGradient(colors: [flashStart, flashEnd], startPoint: .leading, endPoint: .trailing) }
    static var onsight: LinearGradient { LinearGradient(colors: [onsightStart, onsightEnd], startPoint: .leading, endPoint: .trailing) }
}

/// Puente de los intentos de shared (`KotlinInt?`) a Swift.
enum AttemptsBridge {
    static func kotlin(_ n: Int?) -> KotlinInt? { n.map { KotlinInt(int: Int32($0)) } }
    static var flash: Int { Int(Attempts.shared.FLASH) }
    static var quick: [Int] { Attempts.shared.QUICK.map { Int(truncating: $0) } }
    static var more: [Int] { Attempts.shared.MORE.map { Int(truncating: $0) } }
    static func isFlash(_ n: Int?) -> Bool { Attempts.shared.isFlash(n: kotlin(n)) }
    static func showsCount(_ n: Int?) -> Bool { Attempts.shared.showsCount(n: kotlin(n)) }
    static func isFivePlus(_ n: Int?) -> Bool { Attempts.shared.isFivePlus(n: kotlin(n)) }
}

/// Distintivo dorado «⚡ FLASH» (degradado F6C744 a F59E0B, texto marrón, contorno blanco y brillo). Espejo de `FlashBadge.kt`.
struct FlashBadge: View {
    var label: String = L("flash_badge")
    var large = false
    var body: some View {
        StyleBadge(icon: "bolt.fill", label: label, ink: StyleBadgeColors.flashBrown, fill: StyleBadgeColors.flash,
                   glow: StyleBadgeColors.flashEnd, large: large)
    }
}

/// Distintivo azul «👁 A VISTA» (degradado 5EC8E5 a 2F80ED, texto 062A52, contorno blanco y brillo azul). Espejo de `OnsightBadge.kt`.
struct OnsightBadge: View {
    var label: String = L("onsight_badge")
    var large = false
    var body: some View {
        StyleBadge(icon: "eye", label: label, ink: StyleBadgeColors.onsightNavy, fill: StyleBadgeColors.onsight,
                   glow: StyleBadgeColors.onsightEnd, large: large)
    }
}

private struct StyleBadge: View {
    let icon: String
    let label: String
    let ink: Color
    let fill: LinearGradient
    let glow: Color
    let large: Bool
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: large ? 14 : 9, weight: .bold))
            Text(label).font(.system(size: large ? 14 : 10, weight: .heavy)).tracking(1).lineLimit(1)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, large ? 14 : 8).padding(.vertical, large ? 8 : 2)
        .background(fill, in: Capsule())
        .overlay(Capsule().stroke(Color.white, lineWidth: 1.5))
        .shadow(color: glow.opacity(0.45), radius: large ? 4 : 3)
    }
}

/// Pastilla naranja «🎯 N INTENTOS» (fondo FBE3D6, texto 9A3412, borde C2410C) para más de un intento.
struct AttemptsPill: View {
    let attempts: Int
    var body: some View {
        Text(L("attempts_pill", attempts)).font(.system(size: 10, weight: .heavy)).tracking(0.8).lineLimit(1)
            .foregroundStyle(Color(hex: 0x9A3412))
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(Color(hex: 0xFBE3D6), in: Capsule())
            .overlay(Capsule().stroke(Color(hex: 0xC2410C), lineWidth: 1))
    }
}

/// FLASH si fue a la primera, «N INTENTOS» si hubo más, nada si no se dijo.
struct AttemptsBadge: View {
    let attempts: Int?
    var body: some View {
        if AttemptsBridge.isFlash(attempts) {
            FlashBadge()
        } else if let n = attempts, AttemptsBridge.showsCount(n) {
            AttemptsPill(attempts: n)
        }
    }
}

/// UNA insignia de estilo por prioridad (a vista gana; si no, flash; si no, intentos). Filas de Mis vías/bloques y perfil público.
struct AscentStyleBadge: View {
    let aVista: Bool
    let alFlash: Bool
    let attempts: Int?
    static func shows(aVista: Bool, alFlash: Bool, attempts: Int?) -> Bool {
        aVista || alFlash || AttemptsBridge.showsCount(attempts)
    }
    var body: some View {
        switch AscentStyle(aVista: aVista, alFlash: alFlash) {
        case .onsight: OnsightBadge()
        case .flash: FlashBadge()
        case .none: AttemptsBadge(attempts: attempts)
        }
    }
}

/// «N intentos» en texto (plural de Android `attempts_n`).
func attemptsText(_ n: Int) -> String { LP("attempts_n", n) }
