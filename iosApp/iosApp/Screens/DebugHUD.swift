import UIKit

/// SOLO DIAGNÓSTICO — quitar en cuanto se localice el bug de "proponer foto
/// desde dentro de una escuela no hace nada" (Rodrigo, 2026-08-22, sigue
/// fallando tras 3 intentos de arreglo a ciegas: builds 161/162/163/164).
///
/// Una ventana PROPIA, siempre encima, que NO depende de presentar nada (ni
/// alert ni sheet) — así el propio diagnóstico no puede sufrir el mismo fallo
/// silencioso de presentación que estamos intentando cazar.
@MainActor
enum DebugHUD {
    private static var window: UIWindow?
    private static var label: UILabel?
    private static var lines: [String] = []

    static func log(_ msg: String) {
        let stamped = "\(Int(Date().timeIntervalSince1970 * 1000) % 100000) · \(msg)"
        lines.append(stamped)
        ensureWindow()
        label?.text = lines.suffix(14).joined(separator: "\n")
    }

    static func clear() {
        lines.removeAll()
        label?.text = ""
    }

    private static func ensureWindow() {
        guard window == nil else { return }
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first else { return }
        let w = UIWindow(windowScene: scene)
        w.windowLevel = .alert + 100
        w.backgroundColor = .clear
        w.isUserInteractionEnabled = false
        let lbl = UILabel()
        lbl.numberOfLines = 0
        lbl.font = .monospacedSystemFont(ofSize: 11, weight: .semibold)
        lbl.textColor = .white
        lbl.backgroundColor = UIColor.black.withAlphaComponent(0.8)
        lbl.frame = CGRect(x: 8, y: 56, width: scene.screen.bounds.width - 16, height: 260)
        lbl.layer.cornerRadius = 8
        lbl.layer.masksToBounds = true
        let vc = UIViewController()
        vc.view.backgroundColor = .clear
        vc.view.addSubview(lbl)
        w.rootViewController = vc
        w.isHidden = false
        window = w
        label = lbl
    }
}
