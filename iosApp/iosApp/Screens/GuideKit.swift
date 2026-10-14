import SwiftUI
import Shared

// Guías por pantalla (2026-10-14): un recorrido corto que señala elementos REALES de la pantalla. Opcional: se abre desde
// el "?" de cada pantalla (que parpadea las primeras visitas) y, la primera vez tras crear la cuenta, la de Escuelas
// sale sola (saltable). El CONTENIDO (qué se señala y qué se cuenta, ES/EN) vive en shared (`GuideCatalog`, con tests);
// aquí solo está la maquinaria de pintarlo: registrar el elemento, el foco sobre él y la tarjeta.

// MARK: - Estado

/// Qué guía está abierta y en qué punto va. Una sola a la vez (la pantalla visible).
@MainActor
final class GuideStore: ObservableObject {
    static let shared = GuideStore()

    @Published private(set) var activeKey: String?
    @Published private(set) var index = 0
    /// Sube cada vez que se abre una guía: los "?" que parpadean lo observan y dejan de parpadear al momento
    /// (antes seguían hasta volver a entrar en la pantalla).
    @Published private(set) var usedVersion = 0
    /// Guía que arranca sola al terminar la actual (la de la pantalla seguida de la de su pestaña).
    private var chained: String?

    /// Interruptor general de las guías: lo decide el usuario al empezar y se cambia desde Ajustes. Apagado, ningún
    /// "?" parpadea y la de Escuelas no sale sola; los "?" siguen abriendo su guía si se pulsan.
    nonisolated static var enabled: Bool { UserDefaults.standard.object(forKey: "guides_enabled") as? Bool ?? true }
    nonisolated static func setEnabled(_ on: Bool) { UserDefaults.standard.set(on, forKey: "guides_enabled") }

    var guide: Guide? {
        activeKey.flatMap { GuideCatalog.shared.byKey(key: $0, lang: LanguageManager.shared.effectiveCode) }
    }

    /// Abre la guía de esa pantalla. Si no existe, no hace nada (el llamante cae a la ayuda de texto).
    @discardableResult
    func start(_ key: String, then followUp: String? = nil) -> Bool {
        guard GuideCatalog.shared.byKey(key: key, lang: LanguageManager.shared.effectiveCode) != nil else { return false }
        activeKey = key
        index = 0
        chained = followUp
        UserDefaults.standard.set(true, forKey: Self.usedKey(key))   // ya la ha abierto: el "?" deja de parpadear
        usedVersion += 1
        return true
    }

    /// UN solo "?" por pantalla: la primera vez cuenta la pantalla y, a continuación, la pestaña que se está viendo;
    /// las siguientes, solo la de la pestaña.
    @discardableResult
    func startScreenAndTab(screen: String, tab: String) -> Bool {
        if UserDefaults.standard.bool(forKey: Self.usedKey(screen)) { return start(tab) }
        return start(screen, then: tab)
    }

    func next() {
        guard let g = guide else { return stop() }
        if index + 1 < g.steps.count { index += 1; return }
        if let follow = chained, start(follow) { return }   // terminó la de la pantalla: sigue la de la pestaña
        stop()
    }

    func stop() { activeKey = nil; index = 0; chained = nil }

    static func usedKey(_ key: String) -> String { "guide_used_\(key)" }

    /// "Volver a ver las guías": borra lo que recuerda (visitas y guías abiertas) y deja la de Escuelas pendiente.
    nonisolated static func resetAll() {
        let d = UserDefaults.standard
        for k in d.dictionaryRepresentation().keys where k.hasPrefix("guide_visits_") || k.hasPrefix("guide_used_") {
            d.removeObject(forKey: k)
        }
        d.set(true, forKey: "guide_pending_schools")
        setEnabled(true)
    }
}

// MARK: - Registrar elementos

struct GuideTargetKey: PreferenceKey {
    static var defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Marca este elemento como señalable por una guía (`id` = el `target` del `GuideStep`). `when: false` lo ignora
    /// (p. ej. solo la primera fila de una lista).
    func guideTarget(_ id: String, when active: Bool = true) -> some View {
        anchorPreference(key: GuideTargetKey.self, value: .bounds) { active ? [id: $0] : [:] }
    }

    /// Pone esta vista como "anfitriona" de la guía `key`: pinta el foco y la tarjeta encima de toda la pantalla.
    func guideHost(_ key: String) -> some View { modifier(GuideHost(key: key)) }
}

// MARK: - Anfitrión y capa

private struct GuideHost: ViewModifier {
    let key: String
    @ObservedObject private var store = GuideStore.shared
    /// Si se cambia el idioma con una guía abierta, se vuelve a pintar en el idioma nuevo.
    @ObservedObject private var language = LanguageManager.shared

    /// Una pantalla atiende su guía ("detail") y las de sus pestañas ("detail_map", "detail_blocks"…).
    private func isMine(_ active: String?) -> Bool {
        guard let active else { return false }
        return active == key || active.hasPrefix(key + "_")
    }

    func body(content: Content) -> some View {
        content.overlayPreferenceValue(GuideTargetKey.self) { anchors in
            GeometryReader { geo in
                if isMine(store.activeKey), let g = store.guide, store.index < g.steps.count {
                    let step = g.steps[store.index]
                    GuideOverlay(step: step, position: store.index + 1, total: g.steps.count,
                                 hole: anchors[step.target].map { geo[$0] }, size: geo.size,
                                 onNext: { store.next() }, onStop: { store.stop() })
                }
            }
            .ignoresSafeArea()
        }
    }
}

private struct GuideOverlay: View {
    let step: GuideStep
    let position: Int
    let total: Int
    /// nil = ese elemento no está en pantalla ahora: la tarjeta sale centrada, sin foco.
    let hole: CGRect?
    let size: CGSize
    let onNext: () -> Void
    let onStop: () -> Void

    var body: some View {
        ZStack {
            dim
            card
        }
        .transition(.opacity)
    }

    /// Pantalla atenuada con un hueco redondeado sobre el elemento (relleno par-impar).
    private var dim: some View {
        let focus = hole.map { $0.insetBy(dx: -6, dy: -6) }
        return ZStack {
            Path { p in
                p.addRect(CGRect(origin: .zero, size: size))
                if let focus { p.addRoundedRect(in: focus, cornerSize: CGSize(width: 12, height: 12)) }
            }
            .fill(Color.black.opacity(0.62), style: FillStyle(eoFill: true))
            if let focus {
                RoundedRectangle(cornerRadius: 12).stroke(Cumbre.terra, lineWidth: 2)
                    .frame(width: focus.width, height: focus.height)
                    .position(x: focus.midX, y: focus.midY)
            }
        }
        // Tocar fuera de la tarjeta NO cierra ni avanza: solo los botones (así no se salta una guía sin querer).
        .contentShape(Rectangle())
        .onTapGesture {}
    }

    private var card: some View {
        let cardBody = VStack(alignment: .leading, spacing: 8) {
            Text("\(position)/\(total)").font(Cumbre.mono(10, .bold)).tracking(1.6).foregroundStyle(Cumbre.terra)
            Text(step.title).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(step.body).font(.system(size: 14.5)).foregroundStyle(Cumbre.ink2)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button(action: onStop) {
                    Text(L("SALIR")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                        .padding(.vertical, 10)
                }.buttonStyle(.plain)
                Spacer()
                Button(action: onNext) {
                    Text(position < total ? L("SIGUIENTE") : L("ENTENDIDO"))
                        .font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Cumbre.terraFill, in: Capsule())
                }.buttonStyle(.plain)
            }
            .padding(.top, 4)
        }
        .padding(16)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 20)

        return Group {
            if let hole {
                // Debajo del elemento si está en la mitad de arriba; encima si está abajo.
                if hole.midY < size.height * 0.55 {
                    VStack(spacing: 0) {
                        Spacer().frame(height: min(hole.maxY + 18, size.height * 0.62))
                        cardBody
                        Spacer(minLength: 0)
                    }
                } else {
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        cardBody
                        Spacer().frame(height: max(size.height - hole.minY + 18, 0))
                    }
                }
            } else {
                cardBody
            }
        }
    }
}

// MARK: - El "?" que abre la guía (y parpadea las primeras veces)

/// Anillo que se expande y se desvanece sobre el botón. Solo las primeras 3 visitas a la pantalla y mientras nadie
/// haya abierto su guía: llama la atención sin molestar a quien ya la conoce.
struct GuidePulse: ViewModifier {
    let key: String
    @ObservedObject private var store = GuideStore.shared
    @State private var pulsing = false
    @State private var enabled = false
    private static let maxVisits = 3

    func body(content: Content) -> some View {
        content
            .background(
                Capsule().stroke(Cumbre.terra, lineWidth: 2)
                    .scaleEffect(pulsing ? 1.5 : 0.95)
                    .opacity(enabled ? (pulsing ? 0 : 0.9) : 0)
            )
            .onAppear {
                let d = UserDefaults.standard
                let visits = d.integer(forKey: "guide_visits_\(key)") + 1
                d.set(visits, forKey: "guide_visits_\(key)")
                enabled = GuideStore.enabled && !d.bool(forKey: GuideStore.usedKey(key)) && visits <= Self.maxVisits
                guard enabled else { return }
                withAnimation(.easeOut(duration: 1.3).repeatForever(autoreverses: false)) { pulsing = true }
            }
            // Pulsado el "?" (o apagadas las guías), deja de parpadear en el acto.
            .onChange(of: store.usedVersion) { _, _ in
                guard enabled, UserDefaults.standard.bool(forKey: GuideStore.usedKey(key)) else { return }
                enabled = false
                withTransaction(Transaction(animation: nil)) { pulsing = false }
            }
    }
}

extension View {
    func guidePulse(_ key: String) -> some View { modifier(GuidePulse(key: key)) }
}

/// "?" de una pantalla: abre su guía y, si esa pantalla aún no tiene, su ayuda de texto de siempre.
struct GuideHelpButton: View {
    let topicKey: String
    @State private var showText = false

    var body: some View {
        GuidePill(topicKey: topicKey) {
            if !GuideStore.shared.start(topicKey) { showText = true }
        }
        .sheet(isPresented: $showText) { HelpSheet(topicKey: topicKey) }
    }
}

/// "Explicarme cada pantalla": interruptor general de las guías. Se ofrece en la bienvenida y se puede cambiar en Ajustes.
/// Apagado, ningún "?" parpadea y la guía de Escuelas no sale sola (los "?" siguen funcionando si se pulsan).
struct GuidesToggleRow: View {
    @AppStorage("guides_enabled") private var enabled = true

    var body: some View {
        Toggle(isOn: $enabled) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("Explicarme cada pantalla")).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text(L("Un “?” parpadea la primera vez que entras. Puedes cambiarlo en Ajustes."))
                    .font(.system(size: 12.5)).foregroundStyle(Cumbre.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(Cumbre.terra)
    }
}

/// Botón "? AYUDA": el MISMO en todas las pantallas (símbolo "?" + palabra). Con la palabra se entiende qué hace
/// (Álvaro, 2026-10-14: el "?" suelto no se identificaba); "GUÍA" se descartó porque en una app de escalada se lee
/// como guía de escalada. Parpadea las primeras visitas.
struct GuidePill: View {
    let topicKey: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: "questionmark.circle.fill").font(.system(size: 13, weight: .semibold))
                Text(L("AYUDA")).font(Cumbre.mono(11, .bold)).tracking(0.8)
            }
            .foregroundStyle(Cumbre.terra)
            .padding(.horizontal, 11).padding(.vertical, 8)
            .background(Cumbre.terra.opacity(0.10), in: Capsule())
            .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1.2))
            .guidePulse(topicKey)
        }
        .buttonStyle(.plain)
        .fixedSize()
        .accessibilityLabel(L("AYUDA"))
    }
}
