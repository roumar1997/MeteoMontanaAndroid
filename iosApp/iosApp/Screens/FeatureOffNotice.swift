import SwiftUI
import Shared

/// Aviso de «esta función está en pausa» — espejo de `FeatureOffNotice.kt`. Tarjeta Cumbre (papel, borde de 1 pt,
/// radio 2) con el eyebrow «EN PAUSA». Usa el mensaje del servidor si lo hay (ya viene traducido); si no, el de siempre.
struct FeatureOffNotice: View {
    let message: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("EN PAUSA"))
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
            Text(FeatureOff.text(message))
                .font(.system(size: 14))
                .foregroundStyle(Cumbre.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

/// Aviso de función en pausa sacado de un error (el 503 FEATURE_DISABLED del servidor). `message` puede ser nil.
struct FeatureNotice {
    let message: String?
}

enum FeatureOff {
    /// El texto a enseñar: el del servidor si lo hay, y si no el de siempre.
    static func text(_ serverMessage: String?) -> String {
        if let m = serverMessage, !m.isEmpty { return m }
        return L("Esta función está en pausa un momento. Lo estamos arreglando; vuelve a probar en un rato.")
    }

    /// Si el error es `FeatureDisabledException` (503 FEATURE_DISABLED de un POST), devuelve su aviso.
    /// Una excepción de Kotlin lanzada a Swift llega como NSError con la excepción original en `userInfo`.
    nonisolated static func notice(from error: Error) -> FeatureNotice? {
        if let e = (error as NSError).userInfo["KotlinException"] as? FeatureDisabledException {
            return FeatureNotice(message: e.message)
        }
        if let e = error as? FeatureDisabledException {
            return FeatureNotice(message: e.message)
        }
        return nil
    }
}
