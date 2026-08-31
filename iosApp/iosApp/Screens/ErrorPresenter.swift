import SwiftUI
import UIKit

/// Presentador GLOBAL de errores de usuario — el helper que faltaba de la
/// regla P2.1 (ARCHITECTURE.md §2): las acciones del usuario nunca fallan en
/// silencio. En vez de `try?` (que se traga el error), las vistas envuelven
/// la llamada con `reporting { ... }` y, si falla, aparece un banner arriba
/// con un mensaje entendible que se oculta solo.
///
/// Espejo conceptual del patrón Android (StateFlow<String?> de error + Toast).
@MainActor
final class ErrorPresenter: ObservableObject {
    static let shared = ErrorPresenter()

    @Published var message: String? = nil

    private var hideTask: Task<Void, Never>? = nil

    func show(_ text: String) {
        hideTask?.cancel()
        withAnimation(.easeOut(duration: 0.2)) { message = text }
        // P1: ADEMAS del banner SwiftUI (que las hojas presentadas tapan),
        // un toast UIKit en la VENTANA — visible sobre sheets y covers.
        Self.showWindowToast(text)
        hideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: 0.2)) { self?.message = nil }
        }
    }

    /// Toast UIKit anclado a la ventana activa: se ve por encima de cualquier
    /// sheet/fullScreenCover (el overlay SwiftUI de RootView no).
    private static func showWindowToast(_ text: String) {
        guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
              let window = scene.keyWindow else { return }

        let label = UILabel()
        label.text = "  \u{26A0} " + text + "  "
        label.font = .systemFont(ofSize: 13, weight: .medium)
        label.textColor = UIColor(red: 0.10, green: 0.10, blue: 0.10, alpha: 1)
        label.backgroundColor = UIColor(red: 0.94, green: 0.92, blue: 0.85, alpha: 1)
        label.layer.borderColor = UIColor(red: 0.75, green: 0.33, blue: 0.17, alpha: 1).cgColor
        label.layer.borderWidth = 1
        label.layer.cornerRadius = 6
        label.clipsToBounds = true
        label.numberOfLines = 2
        label.textAlignment = .center
        label.alpha = 0

        let maxW = window.bounds.width - 32
        let size = label.sizeThatFits(CGSize(width: maxW, height: 80))
        label.frame = CGRect(x: (window.bounds.width - min(size.width + 8, maxW)) / 2,
                             y: window.safeAreaInsets.top + 8,
                             width: min(size.width + 8, maxW), height: size.height + 14)
        window.addSubview(label)
        UIView.animate(withDuration: 0.2) { label.alpha = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
            UIView.animate(withDuration: 0.25, animations: { label.alpha = 0 }) { _ in
                label.removeFromSuperview()
            }
        }
    }

    func dismiss() {
        hideTask?.cancel()
        withAnimation(.easeIn(duration: 0.15)) { message = nil }
    }

    /// Mensaje entendible a partir de la excepción: sin red → mensaje de
    /// conexión; cualquier otra cosa → el mensaje de contexto que pasa la
    /// vista ("No se pudo enviar el comentario", etc.).
    nonisolated static func friendly(_ error: Error, fallback: String) -> String {
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain { return "Sin conexión. Inténtalo de nuevo." }
        let text = String(describing: error).lowercased()
        if text.contains("internet") || text.contains("connect") || text.contains("network")
            || text.contains("timeout") || text.contains("hostname") {
            return "Sin conexión. Inténtalo de nuevo."
        }
        // Código HTTP si se puede sacar del texto de la excepción de Ktor
        // (ClientRequestException/ServerResponseException lo incluyen) — sin
        // esto, cualquier fallo real caía siempre en el mismo mensaje
        // genérico y no había forma de diagnosticarlo a distancia
        // (Rodrigo, 2026-08-22: "no se pudo cambiar estilo", sin más pista).
        if let match = text.range(of: #"\b[45]\d\d\b"#, options: .regularExpression) {
            return "\(fallback) (código \(text[match]))"
        }
        return fallback
    }
}

/// Ejecuta una acción async que puede fallar; si falla, muestra el banner con
/// un mensaje amable y devuelve nil. Uso:
///     if let created = await reporting("No se pudo enviar el comentario", {
///         try await container.addLineComment.invoke(...)
///     }) { comments.append(created) }
@MainActor
@discardableResult
func reporting<T>(_ fallback: String, _ op: () async throws -> T) async -> T? {
    do {
        return try await op()
    } catch {
        ErrorPresenter.shared.show(ErrorPresenter.friendly(error, fallback: fallback))
        return nil
    }
}

/// Banner visual (estilo Cumbre: papel, borde, sin sombras). RootView lo
/// superpone una sola vez para toda la app.
struct ErrorBannerView: View {
    @ObservedObject private var presenter = ErrorPresenter.shared

    var body: some View {
        if let message = presenter.message {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 13, weight: .semibold))
                Text(message)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(2)
                Spacer(minLength: 0)
                Button { presenter.dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .bold))
                }
                .accessibilityLabel("Cerrar aviso")
            }
            .foregroundStyle(Cumbre.ink)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Cumbre.paper2)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.terra, lineWidth: 1))
            .padding(.horizontal, 12)
            .padding(.top, 4)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}
