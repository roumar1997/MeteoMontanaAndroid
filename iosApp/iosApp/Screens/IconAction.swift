import SwiftUI

/// Boton de icono de la cabecera de Escuelas y de la ficha: lo mismo que un icono de la barra de pestanas de abajo
/// (simbolo de linea de ~22 pt, tinta secundaria), sin fondo, sin borde y sin texto. Encendido (`active`): terracota,
/// como la pestana activa. Area de toque de 44 pt como minimo. `a11y` es el nombre que lee VoiceOver.
/// Espejo de `IconAction` en Android.
///
/// `IconActionFace` es solo el dibujo (para meterlo en un `ShareLink` o un `Menu`); `IconAction` lo envuelve en un boton.
struct IconActionFace<Glyph: View>: View {
    var active = false
    /// Color del icono encendido (terracota salvo la oruga en alerta, que va en rojo).
    var activeColor: Color = Cumbre.terra
    var badge = 0
    let glyph: (Color) -> Glyph

    init(active: Bool = false, activeColor: Color = Cumbre.terra, badge: Int = 0,
         @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.active = active; self.activeColor = activeColor
        self.badge = badge; self.glyph = glyph
    }

    private var tint: Color { active ? activeColor : Cumbre.ink2 }

    var body: some View {
        glyph(tint)
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
            .overlay(alignment: .center) {
                if badge > 0 {
                    Text(badge > 9 ? "9+" : "\(badge)")
                        .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 4).padding(.vertical, 1)
                        .background(Capsule().fill(Cumbre.bad))
                        .offset(x: 11, y: -9)
                }
            }
            .contentShape(Rectangle())
    }
}

struct IconAction<Glyph: View>: View {
    let a11y: String
    var active = false
    var activeColor: Color = Cumbre.terra
    var badge = 0
    let action: () -> Void
    let glyph: (Color) -> Glyph

    init(a11y: String, active: Bool = false, activeColor: Color = Cumbre.terra, badge: Int = 0,
         action: @escaping () -> Void, @ViewBuilder glyph: @escaping (Color) -> Glyph) {
        self.a11y = a11y; self.active = active; self.activeColor = activeColor
        self.badge = badge; self.action = action; self.glyph = glyph
    }

    var body: some View {
        Button(action: action) {
            IconActionFace(active: active, activeColor: activeColor, badge: badge, glyph: glyph)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y)
    }
}

extension IconAction where Glyph == IconSymbol {
    /// Boton con un simbolo del sistema (de linea) como icono.
    init(symbol: String, a11y: String, active: Bool = false, activeColor: Color = Cumbre.terra,
         badge: Int = 0, action: @escaping () -> Void) {
        self.init(a11y: a11y, active: active, activeColor: activeColor, badge: badge,
                  action: action, glyph: { tint in IconSymbol(name: symbol, tint: tint) })
    }
}

extension IconActionFace where Glyph == IconSymbol {
    init(symbol: String, active: Bool = false, activeColor: Color = Cumbre.terra) {
        self.init(active: active, activeColor: activeColor, glyph: { tint in IconSymbol(name: symbol, tint: tint) })
    }
}

/// Simbolo con el tamano y el color de los iconos de la barra de pestanas (se usa dentro de `IconAction`).
struct IconSymbol: View {
    let name: String
    let tint: Color
    var body: some View {
        Image(systemName: name).font(.system(size: 22, weight: .regular)).foregroundStyle(tint)
    }
}

/// Fila de `count` iconos del mismo ancho, repartidos por todo el ancho disponible. Si no caben con el area de toque
/// minima (`minItem`, iPhone estrecho o letra grande), la fila se desliza en horizontal; el area de toque nunca encoge.
/// `content` recibe el ancho que hay que dar a cada icono (`.frame(width:)`). Espejo de `IconActionRow`.
struct IconActionRow<Content: View>: View {
    let count: Int
    var minItem: CGFloat = 44
    @ViewBuilder let content: (CGFloat) -> Content

    var body: some View {
        GeometryReader { geo in
            let fair = geo.size.width / CGFloat(max(count, 1))
            let width = max(minItem, fair.rounded(.down))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) { content(width) }
            }
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        }
        .frame(height: 44)
    }
}
