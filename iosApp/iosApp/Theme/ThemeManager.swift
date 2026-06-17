import SwiftUI
import UIKit

/// Gestor de tema (claro / oscuro / sistema) persistido en UserDefaults.
/// Espejo del ThemeManager.kt de Android. La raíz aplica `colorScheme` con
/// `.preferredColorScheme`, y la luna del header cicla los modos.
@MainActor
final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    enum Mode: String, CaseIterable { case system, light, dark }

    @Published var mode: Mode {
        didSet {
            UserDefaults.standard.set(mode.rawValue, forKey: Self.key)
            applyToWindows()
        }
    }

    /// Fuerza el estilo claro/oscuro en TODAS las ventanas (UIKit). Necesario
    /// porque los colores `Cumbre.*` se resuelven por `UITraitCollection` y los
    /// sheets de SwiftUI NO heredan de forma fiable `.preferredColorScheme`
    /// (salían en claro/deslumbrando en modo oscuro). Llamar al arrancar y al
    /// cambiar de modo.
    func applyToWindows() {
        let style: UIUserInterfaceStyle = (mode == .dark) ? .dark : .light
        for scene in UIApplication.shared.connectedScenes {
            (scene as? UIWindowScene)?.windows.forEach { $0.overrideUserInterfaceStyle = style }
        }
    }

    private static let key = "cumbre_theme_mode"

    private init() {
        let raw = UserDefaults.standard.string(forKey: Self.key) ?? Mode.light.rawValue
        // Solo claro/oscuro: cualquier valor antiguo "system" pasa a claro.
        let parsed = Mode(rawValue: raw) ?? .light
        mode = parsed == .system ? .light : parsed
    }

    /// Esquema de color forzado (siempre claro u oscuro; sin modo sistema).
    var colorScheme: ColorScheme? {
        switch mode {
        case .dark: return .dark
        default:    return .light
        }
    }

    /// Icono del header: indica a qué modo cambiarías al pulsar.
    var iconName: String {
        mode == .dark ? "sun.max" : "moon"
    }

    /// Alterna claro ↔ oscuro (solo dos estados).
    func cycle() {
        mode = (mode == .dark) ? .light : .dark
    }
}
