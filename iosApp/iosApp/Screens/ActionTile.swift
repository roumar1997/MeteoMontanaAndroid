import SwiftUI
import UIKit
import CoreText

/// Baldosa de acción (estilo «Baldosas» elegido por Álvaro): rectángulo redondeado del papel y borde fino de las
/// tarjetas de escuelas, icono de LÍNEA arriba y el nombre debajo (Inter 600, 10,5 pt, tinta). Encendida (`active`):
/// icono, texto y borde en terracota. Espejo de `ActionTile` en Android. El ancho lo pone quien la usa (todas las de
/// una fila miden lo mismo, ver `ActionTileRow`); el alto es fijo para que todas queden alineadas.
///
/// `ActionTileFace` es solo el dibujo (para meterlo en un `ShareLink` o un `Menu`); `ActionTile` lo envuelve en un botón.
struct ActionTileFace<Glyph: View>: View {
    let label: String?
    var active = false
    /// Color de la baldosa encendida (terracota salvo la oruga en alerta, que va en rojo).
    var activeColor: Color = Cumbre.terra
    var badge = 0
    let glyph: (Color) -> Glyph

    init(label: String?, active: Bool = false, activeColor: Color = Cumbre.terra, badge: Int = 0,
         @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.label = label; self.active = active; self.activeColor = activeColor
        self.badge = badge; self.glyph = glyph
    }

    private var tint: Color { active ? activeColor : Cumbre.ink }

    var body: some View {
        VStack(spacing: 3) {
            glyph(tint).frame(height: 22)
            if let label {
                Text(label)
                    .font(Cumbre.inter(10.5, weight: 600))
                    .foregroundStyle(tint)
                    .lineLimit(1).fixedSize()
            }
        }
        .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
        .background(Cumbre.tileBg, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(active ? activeColor : Cumbre.rule, lineWidth: 1))
        .overlay(alignment: .topTrailing) {
            if badge > 0 {
                Text(badge > 9 ? "9+" : "\(badge)")
                    .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 4).padding(.vertical, 1)
                    .background(Capsule().fill(Cumbre.bad))
                    .padding(.top, 3).padding(.trailing, 5)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct ActionTile<Glyph: View>: View {
    let label: String?
    var a11y: String? = nil
    var active = false
    var activeColor: Color = Cumbre.terra
    var badge = 0
    let action: () -> Void
    let glyph: (Color) -> Glyph

    init(label: String?, a11y: String? = nil, active: Bool = false, activeColor: Color = Cumbre.terra,
         badge: Int = 0, action: @escaping () -> Void, @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.label = label; self.a11y = a11y; self.active = active; self.activeColor = activeColor
        self.badge = badge; self.action = action; self.glyph = glyph
    }

    var body: some View {
        Button(action: action) {
            ActionTileFace(label: label, active: active, activeColor: activeColor, badge: badge, glyph: glyph)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y ?? label ?? "")
    }
}

extension ActionTile where Glyph == TileSymbol {
    /// Baldosa con un símbolo del sistema (de línea) como icono.
    init(label: String?, symbol: String, a11y: String? = nil, active: Bool = false, activeColor: Color = Cumbre.terra,
         badge: Int = 0, action: @escaping () -> Void) {
        self.init(label: label, a11y: a11y, active: active, activeColor: activeColor, badge: badge,
                  action: action, glyph: { tint in TileSymbol(name: symbol, tint: tint) })
    }
}

extension ActionTileFace where Glyph == TileSymbol {
    init(label: String?, symbol: String, active: Bool = false, activeColor: Color = Cumbre.terra) {
        self.init(label: label, active: active, activeColor: activeColor, glyph: { tint in TileSymbol(name: symbol, tint: tint) })
    }
}

/// Símbolo con el tamaño y el color de los iconos de las baldosas (se usa dentro de `ActionTile`).
struct TileSymbol: View {
    let name: String
    let tint: Color
    var body: some View {
        Image(systemName: name).font(.system(size: 18, weight: .regular)).foregroundStyle(tint)
    }
}

/// Fila de `count` baldosas del mismo ancho, repartidas por todo el ancho disponible. Si a ese reparto no le cabe el
/// nombre (iPhone estrecho o letra grande: ancho mínimo `minTile`), la fila se desliza en horizontal; nunca se encoge
/// el texto. `content` recibe el ancho que hay que dar a cada baldosa (`.frame(width:)`). Espejo de `ActionTileRow`.
struct ActionTileRow<Content: View>: View {
    let count: Int
    var minTile: CGFloat = 50
    var gap: CGFloat = 6
    @ViewBuilder let content: (CGFloat) -> Content

    var body: some View {
        GeometryReader { geo in
            let n = CGFloat(max(count, 1))
            let fair = (geo.size.width - gap * (n - 1)) / n
            let width = max(minTile, fair.rounded(.down))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: gap) { content(width) }
            }
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        }
        .frame(height: 52)
    }
}

extension Cumbre {
    /// Papel de las baldosas: el mismo que el de las tarjetas de escuelas (0xFBFAF6 / 0x211F19).
    static let tileBg = dyn(0xFBFAF6, 0x211F19)

    /// Inter (variable, `Inter-Variable.ttf` en UIAppFonts) a un peso concreto del eje `wght`; crece con el tamaño de
    /// letra del sistema hasta un tope. Si la fuente no se pudiera cargar, iOS pone la del sistema (no se rompe nada).
    static func inter(_ size: CGFloat, weight: CGFloat = 400) -> Font {
        let variation = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
        let descriptor = UIFontDescriptor(fontAttributes: [
            .name: "Inter-Regular",
            variation: [NSNumber(value: 2003265652): NSNumber(value: Double(weight))]   // 'wght'
        ])
        let base = UIFont(descriptor: descriptor, size: size)
        let scaled = UIFontMetrics(forTextStyle: .caption2).scaledFont(for: base, maximumPointSize: size * 1.4)
        return Font(scaled as CTFont)
    }
}
