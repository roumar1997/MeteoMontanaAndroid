import SwiftUI
import Shared

/// Interruptores de funciones del servidor (asistente, publicar en Comunidad, crear quedada, PDF de la guía) —
/// espejo de `LocalFeatureFlags` + `AppRootViewModel.refreshFeatureFlags` de Android.
///
/// Una sola instancia para toda la app (como `ThemeManager.shared`): los sheets y covers no heredan siempre los
/// EnvironmentObject de la raíz, y esto se lee desde muchos sitios. Lo refresca `RootView` al arrancar y al volver
/// a primer plano; el límite de frecuencia y la copia local los lleva el repositorio de shared.
@MainActor
final class FeatureFlagsModel: ObservableObject {
    static let shared = FeatureFlagsModel()

    @Published private(set) var flags: FeatureFlags
    private let container: IosDependencyContainer

    init(container: IosDependencyContainer = AppDependencies.shared.container) {
        self.container = container
        self.flags = container.observeFeatureFlags.current()
    }

    /// Pregunta al servidor (si toca) y relee lo que quede. Si falla, se queda lo último guardado.
    func refresh() async {
        try? await container.refreshFeatureFlags.invoke()
        flags = container.observeFeatureFlags.current()
    }

    /// Una clave que no conocemos cuenta como encendida.
    func isEnabled(_ key: String) -> Bool { flags.isEnabled(key: key) }

    /// Aviso del servidor para esa función (ya traducido), o nil.
    func message(_ key: String) -> String? { flags.messageFor(key: key) }
}
