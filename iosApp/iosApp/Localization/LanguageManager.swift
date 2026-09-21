import SwiftUI
import ObjectiveC

/// Idioma elegido DENTRO de la app (Perfil → Ajustes → Idioma).
///
/// Tres valores: "system" (el del móvil), "es" o "en". Se guarda en
/// UserDefaults y se aplica sin reiniciar cambiando de qué `.lproj` lee
/// `Bundle.main`: así siguen el idioma tanto los `Text("…")` de SwiftUI como
/// `NSLocalizedString` y `L(...)`. La raíz de la app se reconstruye
/// (`.id(choice)`) para que todas las pantallas vuelvan a leer sus textos.
final class LanguageManager: ObservableObject {
    static let shared = LanguageManager()
    static let storageKey = "app_language"

    @Published var choice: String {
        didSet {
            UserDefaults.standard.set(choice, forKey: Self.storageKey)
            Bundle.applyLanguage(choice)
        }
    }

    init() {
        let saved = UserDefaults.standard.string(forKey: Self.storageKey) ?? "system"
        choice = saved
        Bundle.applyLanguage(saved)
    }

    /// "es" o "en": el idioma con el que se está pintando la app de verdad.
    var effectiveCode: String {
        if choice == "es" || choice == "en" { return choice }
        let first = Locale.preferredLanguages.first ?? "es"
        return first.hasPrefix("en") ? "en" : "es"   // la app solo tiene es/en
    }

    /// Locale para fechas y números (mismo idioma que los textos).
    var locale: Locale { Locale(identifier: effectiveCode == "en" ? "en_US" : "es_ES") }
}

private var languageBundleKey: UInt8 = 0

/// `Bundle.main` con el idioma forzado: delega la búsqueda de textos en el
/// `.lproj` elegido. Si no hay uno elegido, se comporta como siempre.
private final class LocalizedBundle: Bundle {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        if let b = objc_getAssociatedObject(self, &languageBundleKey) as? Bundle {
            return b.localizedString(forKey: key, value: value, table: tableName)
        }
        return super.localizedString(forKey: key, value: value, table: tableName)
    }
}

extension Bundle {
    static func applyLanguage(_ choice: String) {
        object_setClass(Bundle.main, LocalizedBundle.self)
        var target: Bundle?
        if choice != "system", let path = Bundle.main.path(forResource: choice, ofType: "lproj") {
            target = Bundle(path: path)
        }
        objc_setAssociatedObject(Bundle.main, &languageBundleKey, target, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    }
}
