import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// HELPERS COMPARTIDOS del detalle: formatos, CÓMO LLEGAR, títulos de sección.
// (ShareConditionsButton, muerta —el toolbar usa ShareLink directo—, eliminada.)

extension Block: Identifiable {}
extension Approach: Identifiable {}

/// "hace 3 h" / "hace 2 d" / "el 14/06/26" a partir de un epoch en ms.
func relativeUpdated(_ millis: Int64) -> String {
    guard millis > 0 else { return "" }
    let date = Date(timeIntervalSince1970: Double(millis) / 1000)
    let secs = Date().timeIntervalSince(date)
    if secs < 90 { return L("hace un momento") }
    let mins = Int(secs / 60); if mins < 60 { return L("hace %@ min", mins) }
    let hours = Int(secs / 3600); if hours < 24 { return L("hace %@ h", hours) }
    let days = Int(secs / 86400); if days < 30 { return L("hace %@ d", days) }
    let f = DateFormatter(); f.dateFormat = "dd/MM/yy"
    return "el \(f.string(from: date))"
}

// Detalle de escuela — réplica fiel de ForecastBody.kt de Android:
// veredicto SÍ/NO "¿PUEDO ESCALAR HOY?" + ÍNDICE, banda de roca, heatmap,
// desglose de factores, tiempo actual, próximas 16 h, condiciones (8 celdas),
// próximos 7 días y mejor día. Datos del GetForecastUseCase compartido.

struct DirectionsButton: View {
    let lat: Double
    let lon: Double
    let label: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            let g = URL(string: "comgooglemaps://?daddr=\(lat),\(lon)&directionsmode=driving")!
            let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)")!
            openURL(UIApplication.shared.canOpenURL(g) ? g : web)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.turn.up.right.diamond")
                Text("CÓMO LLEGAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.terraFill)
        }
        .buttonStyle(.plain)
    }
}

/// Botón COMPARTIR — comparte un resumen de condiciones por el share sheet del
/// sistema (paridad con Android, que comparte una tarjeta). De momento texto;
/// la imagen-tarjeta es una mejora posterior.
/// Resumen de condiciones para compartir (texto). Reutilizado por el icono del
/// toolbar del detalle.
func conditionsShareSummary(_ forecast: Forecast) -> String {
    // Formato WhatsApp (los *asteriscos* son negrita allí) + nuestro enlace
    // inteligente: abre la app si la tienes, o la página con las stores si no.
    // Espejo exacto de shareSchool() en Android.
    let c = forecast.current
    var text = "🧗 *\(forecast.schoolName)*\n"
    text += L("📊 Índice *%@/100* (%@)\n", Int(c.score), c.scoreLabel)
    if let w = forecast.bestWindow {
        text += L("🕐 Óptimo *%@–%@*\n", w.start, w.end)
    }
    text += c.dryRock ? L("🪨 Roca seca") : L("💧 Roca mojada")
    text += L(" · %@° · viento %@ km/h\n", Int(c.temperature), Int(c.windSpeed))
    let base = AppConfig.apiBaseUrl.replacingOccurrences(of: "api/", with: "")
    text += L("\n👉 Ábrela en Cumbre:\n%@s/e/%@", base, forecast.schoolId)
    return text
}


struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Helpers
