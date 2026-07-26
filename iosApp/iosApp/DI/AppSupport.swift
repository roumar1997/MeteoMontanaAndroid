import Foundation
import os
import Shared

// Base de soporte de la app (P2.1). Establece TRES convenciones para acabar con
// el `try?` disperso (que traga errores en silencio) y el acceso verboso a la DI:
//
//   1. `AppLog`  — logger unificado (sustituye los `print` sueltos).
//   2. `attempt` — ejecuta una operación async que puede lanzar, REGISTRANDO el
//                  error en vez de tragarlo. Devuelve nil si falla. Reemplazo de
//                  `try? await x()` cuando quieres el fallback pero SIN silencio.
//   3. `deps`    — atajo de `AppDependencies.shared.container`.
//
// Regla mecánica: en código nuevo usar `deps.x` y `await attempt { try await ... }`
// en lugar de `try? await AppDependencies.shared.container.x`. La migración del
// código existente (264 `try?`) es incremental, fichero a fichero.

/// Logger unificado de la app (os.Logger por debajo).
enum AppLog {
    private static let logger = Logger(subsystem: "com.meteomontana.cumbre", category: "app")
    static func warn(_ message: String) { logger.warning("\(message, privacy: .public)") }
    static func error(_ message: String) { logger.error("\(message, privacy: .public)") }
    static func info(_ message: String) { logger.info("\(message, privacy: .public)") }
}

/// Ejecuta [op] async, registrando el error si lanza (en vez de tragarlo como
/// `try?`). Devuelve nil si falla. [label] identifica la operación en el log.
@discardableResult
func attempt<T>(_ label: String = #function, _ op: () async throws -> T) async -> T? {
    do {
        return try await op()
    } catch {
        AppLog.warn("\(label) falló: \(error.localizedDescription)")
        return nil
    }
}

/// Atajo del contenedor de dependencias KMP (menos verboso que
/// `AppDependencies.shared.container`).
var deps: IosDependencyContainer { AppDependencies.shared.container }
