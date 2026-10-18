import SwiftUI

// Estilo común del panel de admin (Álvaro, 2026-10-18: «que se vea más moderno», sugerencias, denuncias, push,
// propuestas): tarjetas redondeadas, chips de estado, botones llenos o con borde, y estados vacíos con icono.

/// Tarjeta del panel: fondo papel, borde fino y esquinas redondeadas.
struct AdminCard: ViewModifier {
    var tint: Color? = nil
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background((tint?.opacity(0.07) ?? Color.clear), in: RoundedRectangle(cornerRadius: 12))
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(tint?.opacity(0.45) ?? Cumbre.rule, lineWidth: 1))
    }
}

extension View {
    func adminCard(tint: Color? = nil) -> some View { modifier(AdminCard(tint: tint)) }
}

/// Etiqueta redondeada de estado («ATENDIDA», «ADMIN», «NUEVO»…).
struct AdminChip: View {
    let text: String
    var color: Color = Cumbre.ink2
    var filled = false
    var body: some View {
        Text(text).font(Cumbre.mono(9.5, .bold)).tracking(0.6)
            .foregroundStyle(filled ? Color.white : color)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(filled ? color : color.opacity(0.10), in: Capsule())
            .overlay(Capsule().stroke(color.opacity(filled ? 0 : 0.5), lineWidth: 1))
    }
}

/// Botón del panel: lleno (acción principal), con borde (secundaria) o de texto.
struct AdminButton: View {
    enum Kind { case primary, danger, outline, quiet }
    let title: String
    var symbol: String? = nil
    var kind: Kind = .primary
    var busy = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if busy { ProgressView().tint(fg) }
                else if let symbol { Image(systemName: symbol).font(.system(size: 12, weight: .semibold)) }
                Text(title).font(Cumbre.mono(11, .bold)).tracking(0.5)
            }
            .foregroundStyle(fg)
            .frame(maxWidth: .infinity).padding(.vertical, 11)
            .background(bg, in: RoundedRectangle(cornerRadius: 9))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var fg: Color {
        switch kind {
        case .primary: return .white
        case .danger: return Cumbre.bad
        case .outline: return Cumbre.ink
        case .quiet: return Cumbre.ink3
        }
    }
    private var bg: Color { kind == .primary ? Cumbre.terraFill : Color.clear }
    private var border: Color {
        switch kind {
        case .primary: return .clear
        case .danger: return Cumbre.bad.opacity(0.6)
        case .outline: return Cumbre.rule
        case .quiet: return Cumbre.rule.opacity(0.6)
        }
    }
}

/// Pantalla vacía con un icono grande y una frase.
struct AdminEmptyState: View {
    let symbol: String
    let text: String
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol).font(.system(size: 38, weight: .light)).foregroundStyle(Cumbre.ink3)
            Text(text).font(.system(size: 14)).foregroundStyle(Cumbre.ink3).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 60).padding(.horizontal, 24)
    }
}

/// Cabecera de sección dentro de una lista del panel.
struct AdminSectionLabel: View {
    let text: String
    var body: some View {
        Text(text).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.terra)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Campo de texto del panel: fondo suave, esquinas redondeadas.
struct AdminField: View {
    let placeholder: String
    @Binding var text: String
    var axis: Axis = .horizontal
    var lines: ClosedRange<Int> = 1...1
    var body: some View {
        TextField(placeholder, text: $text, axis: axis).lineLimit(lines)
            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
            .padding(11)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
    }
}
