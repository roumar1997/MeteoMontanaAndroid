import SwiftUI
import Shared

/// Catálogo de efectos de título del servidor (GET /api/title-effects) — espejo de `rememberTitleEffectCatalog` de
/// Android. Se lee al instante lo guardado en disco (o los integrados) y se renueva detrás: nunca bloquea la UI.
/// Lo refresca `RootView` al arrancar y al volver a primer plano; el límite de frecuencia lo lleva shared.
@MainActor
final class TitleEffectsModel: ObservableObject {
    static let shared = TitleEffectsModel()

    @Published private(set) var catalog: TitleEffectCatalog
    private let container: IosDependencyContainer

    init(container: IosDependencyContainer = AppDependencies.shared.container) {
        self.container = container
        self.catalog = container.observeTitleEffects.current()
    }

    func refresh() async {
        try? await container.refreshTitleEffects.invoke()
        catalog = container.observeTitleEffects.current()
    }

    /// El efecto de esa clave, o nil (sin efecto, o una clave que esta versión aún no conoce).
    func find(_ key: String?) -> TitleEffectSpec? { catalog.find(key: key) }
}

/// Lo que el halo necesita de un efecto, ya en tipos de Swift (los números de Kotlin llegan como Float/KotlinFloat).
struct TitleEffectStyle {
    let key: String
    let orbits: Bool
    let embers: Bool
    let count: Int
    let ring: [Color]
    let glow: Color
    let highlight: Color
    let particle: Color
    let orbit: CGFloat
    let size: CGFloat
    let speed: Double
    let staticAngles: [Double]
    let staticSize: CGFloat
    let facesRight: [Bool]
    let assets: [String?]
    let urls: [String?]

    init(_ spec: TitleEffectSpec) {
        key = spec.key
        orbits = spec.orbits
        embers = spec.embers
        count = max(1, Int(spec.figureCount))
        let ringColors = spec.ringColors.compactMap { colorFromHex($0) }
        ring = ringColors.isEmpty ? [.white] : ringColors
        glow = colorFromHex(spec.glowColor) ?? ring[0]
        highlight = colorFromHex(spec.highlightColor) ?? .white
        particle = colorFromHex(spec.particleColor) ?? .white
        orbit = CGFloat(spec.motion.orbit)
        size = CGFloat(spec.motion.size)
        speed = Double(spec.motion.speed)
        staticAngles = spec.motion.staticAngles.map { $0.doubleValue }
        staticSize = CGFloat(spec.motion.staticSize)
        facesRight = spec.sprites.map { $0.facesRight }
        assets = spec.sprites.map { $0.asset }
        urls = spec.sprites.map { $0.url }
    }
}
