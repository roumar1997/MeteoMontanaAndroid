import Foundation
import ObjectiveC

/// Selector de idioma en caliente (sin reiniciar la app) — patrón estándar
/// de swizzling de Bundle. Espejo de LanguagePicker.kt en Android.
final class LanguageManager {
    static let shared = LanguageManager()
    private let key = "selected_language"

    private init() {
        if hasChosenLanguage {
            object_setClass(Bundle.main, CumbreBundle.self)
        }
    }

    var hasChosenLanguage: Bool {
        UserDefaults.standard.string(forKey: key) != nil
    }

    var currentLanguage: String {
        UserDefaults.standard.string(forKey: key)
            ?? Locale.preferredLanguages.first.map { String($0.prefix(2)) }
            ?? "es"
    }

    func setLanguage(_ code: String) {
        UserDefaults.standard.set(code, forKey: key)
        object_setClass(Bundle.main, CumbreBundle.self)
        NotificationCenter.default.post(name: .languageChanged, object: nil)
    }
}

extension Notification.Name {
    static let languageChanged = Notification.Name("LanguageManagerLanguageChanged")
}

private final class CumbreBundle: Bundle {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        let lang = LanguageManager.shared.currentLanguage
        guard let path = Bundle.main.path(forResource: lang, ofType: "lproj"),
              let bundle = Bundle(path: path) else {
            return super.localizedString(forKey: key, value: value, table: tableName)
        }
        return bundle.localizedString(forKey: key, value: value, table: tableName)
    }
}
