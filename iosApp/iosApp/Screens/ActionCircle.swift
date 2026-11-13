import SwiftUI

/// Acción de cabecera: círculo suelto de papel (borde fino y sombra suave) con el icono relleno centrado y, debajo,
/// su nombre en pequeño. Encendida (`active`) va en terracota con el icono blanco. Es la única forma de acción en las
/// cabeceras de Escuelas y de la ficha: todas a la vista, ninguna escondida en un «⋯». Sin `label` es el círculo solo
/// (el «Volver»). Espejo de `ActionCircle` en Android. El círculo mide siempre lo mismo (es un objetivo táctil); el
/// nombre crece con el tamaño de letra del sistema.
///
/// `ActionCircleFace` es solo el dibujo (para meterlo en un `ShareLink` o un `Menu`); `ActionCircle` lo envuelve en un botón.
struct ActionCircleFace<Glyph: View>: View {
    let label: String?
    var active = false
    /// Color del círculo encendido (terracota salvo la oruga en alerta, que va en rojo).
    var activeColor: Color = Cumbre.terraFill
    var badge = 0
    var pulseKey: String? = nil
    let glyph: (Color) -> Glyph

    init(label: String?, active: Bool = false, activeColor: Color = Cumbre.terraFill, badge: Int = 0,
         pulseKey: String? = nil, @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.label = label; self.active = active; self.activeColor = activeColor
        self.badge = badge; self.pulseKey = pulseKey; self.glyph = glyph
    }

    private var tint: Color { active ? .white : Cumbre.ink }

    var body: some View {
        VStack(spacing: 3) {
            circle
            if let label {
                Text(label)
                    .font(.system(size: 9.5, weight: .semibold))
                    .foregroundStyle(active ? activeColor : Cumbre.ink2)
                    .lineLimit(1).fixedSize()
            }
        }
        .frame(minWidth: 40)
        .contentShape(Rectangle())
    }

    private var circle: some View {
        glyph(tint)
            .frame(width: 40, height: 40)
            .background(active ? activeColor : Cumbre.paper, in: Circle())
            .overlay(Circle().stroke(active ? activeColor : Cumbre.rule, lineWidth: 1))
            .shadow(color: .black.opacity(0.12), radius: 2, x: 0, y: 1)
            .overlay(alignment: .topTrailing) {
                if badge > 0 {
                    Text(badge > 9 ? "9+" : "\(badge)")
                        .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 4).padding(.vertical, 1)
                        .background(Capsule().fill(Cumbre.bad))
                        .offset(x: 4, y: -3)
                }
            }
            .modifier(OptionalGuidePulse(key: pulseKey))
    }
}

struct ActionCircle<Glyph: View>: View {
    let label: String?
    var a11y: String? = nil
    var active = false
    var activeColor: Color = Cumbre.terraFill
    var badge = 0
    var pulseKey: String? = nil
    let action: () -> Void
    let glyph: (Color) -> Glyph

    init(label: String?, a11y: String? = nil, active: Bool = false, activeColor: Color = Cumbre.terraFill,
         badge: Int = 0, pulseKey: String? = nil, action: @escaping () -> Void,
         @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.label = label; self.a11y = a11y; self.active = active; self.activeColor = activeColor
        self.badge = badge; self.pulseKey = pulseKey; self.action = action; self.glyph = glyph
    }

    var body: some View {
        Button(action: action) {
            ActionCircleFace(label: label, active: active, activeColor: activeColor, badge: badge,
                             pulseKey: pulseKey, glyph: glyph)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y ?? label ?? "")
    }
}

/// Aplica el parpadeo de la guía solo si hay clave (la lista de Escuelas y la ficha lo usan en su "?").
private struct OptionalGuidePulse: ViewModifier {
    let key: String?
    @ViewBuilder func body(content: Content) -> some View {
        if let key { content.guidePulse(key) } else { content }
    }
}

extension ActionCircle where Glyph == CircleSymbol {
    /// Círculo con un símbolo del sistema (relleno) como icono.
    init(label: String?, symbol: String, a11y: String? = nil, active: Bool = false, activeColor: Color = Cumbre.terraFill,
         badge: Int = 0, pulseKey: String? = nil, action: @escaping () -> Void) {
        self.init(label: label, a11y: a11y, active: active, activeColor: activeColor, badge: badge, pulseKey: pulseKey,
                  action: action, glyph: { tint in CircleSymbol(name: symbol, tint: tint) })
    }
}

extension ActionCircleFace where Glyph == CircleSymbol {
    init(label: String?, symbol: String, active: Bool = false, activeColor: Color = Cumbre.terraFill) {
        self.init(label: label, active: active, activeColor: activeColor, glyph: { tint in CircleSymbol(name: symbol, tint: tint) })
    }
}

/// Símbolo con el tamaño y el color de los iconos de las cabeceras (se usa dentro de `ActionCircle`).
struct CircleSymbol: View {
    let name: String
    let tint: Color
    var body: some View {
        Image(systemName: name).font(.system(size: 17, weight: .bold)).foregroundStyle(tint)
    }
}
