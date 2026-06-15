import SwiftUI

/// Gestor de tema (claro / oscuro / sistema) persistido en UserDefaults.
/// Espejo del ThemeManager.kt de Android. La raíz aplica `colorScheme` con
/// `.preferredColorScheme`, y la luna del header cicla los modos.
@MainActor
final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    enum Mode: String, CaseIterable { case system, light, dark }

    @Published var mode: Mode {
        didSet { UserDefaults.standard.set(mode.rawValue, forKey: Self.key) }
    }

    private static let key = "cumbre_theme_mode"

    private init() {
        let raw = UserDefaults.standard.string(forKey: Self.key) ?? Mode.system.rawValue
        mode = Mode(rawValue: raw) ?? .system
    }

    /// nil = seguir al sistema.
    var colorScheme: ColorScheme? {
        switch mode {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }

    /// Icono del header según el modo actual.
    var iconName: String {
        switch mode {
        case .system: return "circle.lefthalf.filled"
        case .light:  return "sun.max"
        case .dark:   return "moon"
        }
    }

    /// Cicla sistema → claro → oscuro → sistema.
    func cycle() {
        switch mode {
        case .system: mode = .light
        case .light:  mode = .dark
        case .dark:   mode = .system
        }
    }
}
