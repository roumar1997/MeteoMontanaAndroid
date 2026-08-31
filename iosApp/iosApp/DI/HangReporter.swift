import Foundation
import MetricKit
import FirebaseCrashlytics

/// Diagnóstico REAL de cuelgues (los watchdog 0x8BADF00D de jul-2026): iOS
/// entrega vía MetricKit el stack de cada cuelgue del hilo principal tal y
/// como ocurrió en el dispositivo. Lo subimos a Crashlytics como no-fatal —
/// se acabó reconstruir la historia a mano desde .ips por WhatsApp.
///
/// Responsabilidad única: suscribirse a MetricKit y reenviar diagnósticos.
final class HangReporter: NSObject, MXMetricManagerSubscriber {

    static let shared = HangReporter()

    func start() {
        MXMetricManager.shared.add(self)
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let crashlytics = Crashlytics.crashlytics()
        for payload in payloads {
            for hang in payload.hangDiagnostics ?? [] {
                let seconds = hang.hangDuration.converted(to: .seconds).value
                crashlytics.log("HANG \(String(format: "%.1f", seconds))s")
                // El callStackTree lleva el stack simbolicable del cuelgue.
                let json = String(data: hang.callStackTree.jsonRepresentation(), encoding: .utf8) ?? ""
                // Crashlytics trunca logs largos: trocear en líneas de 1 KB.
                var rest = Substring(json)
                var part = 0
                while !rest.isEmpty && part < 64 {
                    let chunk = rest.prefix(1024)
                    crashlytics.log("HANGSTACK[\(part)] \(chunk)")
                    rest = rest.dropFirst(chunk.count)
                    part += 1
                }
                let err = NSError(domain: "MainThreadHang", code: Int(seconds * 10),
                                  userInfo: [NSLocalizedDescriptionKey:
                                    "Cuelgue del hilo principal de \(String(format: "%.1f", seconds))s (MetricKit)"])
                crashlytics.record(error: err)
            }
        }
    }
}
