import SwiftUI
import UIKit
import Shared

// Colores y piezas visuales de rocódromos. Espejo de `GymTone.kt` y `GymComponents.kt` de Android: los mismos
// tokens (tabla de docs/rocodromos/PARIDAD.md §3), nunca hex sueltos en las pantallas.

enum GymTone {
    /// Relleno (con blanco encima), texto/borde y fondo suave de un tipo de rocódromo.
    struct Colors {
        let fill: Color
        let text: Color
        var soft: Color { text.opacity(0.14) }
    }

    /// Azul municipal: el relleno cambia poco en oscuro; el texto se aclara para leerse.
    private static let municipalFill = Cumbre.dyn(0x2563C7, 0x2B64C4)
    private static let municipalText = Cumbre.dyn(0x2563C7, 0x7BA3EE)
    private static let freeFill = Cumbre.dyn(0x3F6B4A, 0x3F6B4A)

    static func colors(_ kind: GymKind) -> Colors {
        switch kind {
        case .municipal: return Colors(fill: municipalFill, text: municipalText)
        case .free: return Colors(fill: freeFill, text: Cumbre.ok)
        default: return Colors(fill: Cumbre.terraFill, text: Cumbre.terra)
        }
    }

    /// El azul de las etiquetas de modalidad (el mismo de «municipal»).
    static let discipline = municipalText

    /// El mismo relleno como UIColor para los marcadores del mapa (bitmaps).
    static func fillUIColor(_ kind: GymKind, dark: Bool) -> UIColor {
        switch kind {
        case .municipal: return UIColor(rgb: dark ? 0x2B64C4 : 0x2563C7)
        case .free: return UIColor(rgb: 0x3F6B4A)
        default: return UIColor(rgb: dark ? 0xC85018 : 0xC2410C)
        }
    }

    /// Nombre del icono del tipo en el catálogo de assets (importados de docs/rocodromos/icons).
    static func glyph(_ kind: GymKind) -> String {
        switch kind {
        case .municipal: return "gym_ic_municipal"
        case .free: return "gym_ic_free_wall"
        default: return "gym_ic_gym"
        }
    }
}

/// Cuadrado redondeado del color del tipo con su icono en blanco (fila de la lista y tarjeta del mapa).
struct GymKindBadge: View {
    let kind: GymKind
    var size: CGFloat = 52

    var body: some View {
        RoundedRectangle(cornerRadius: size / 4.3, style: .continuous)
            .fill(GymTone.colors(kind).fill)
            .frame(width: size, height: size)
            .overlay(
                Image(GymTone.glyph(kind)).renderingMode(.template).resizable().scaledToFit()
                    .foregroundStyle(.white).frame(width: size * 0.5, height: size * 0.5)
            )
    }
}

/// Estrella de favorito (rellena y terracota si lo es) con zona de toque de 40 pt; la lista y la tarjeta del mapa la comparten.
struct GymFavoriteStar: View {
    let isFavorite: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: isFavorite ? "star.fill" : "star").font(.system(size: 20))
                .foregroundStyle(isFavorite ? Cumbre.terra : Cumbre.ink2)
                .frame(width: 40, height: 40).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L(isFavorite ? "gym_favorite_remove" : "gym_favorite_add"))
    }
}

/// Etiqueta pequeña: píldora de fondo suave y texto del mismo color.
struct GymTag: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(color).lineLimit(1)
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// Etiquetas de una ficha: tipo, cadena, modalidad, precio y estado de apertura. Pasan a otra línea si no caben.
struct GymTags: View {
    let gym: Gym
    let status: GymOpenStatus
    var showKind = true

    var body: some View {
        FlowLayoutView(spacing: 6) {
            if showKind { GymTag(text: GymPresentation.kindLabel(gym.kind), color: GymTone.colors(gym.kind).text) }
            if showKind, let chain = gym.chain, !chain.trimmingCharacters(in: .whitespaces).isEmpty {
                GymTag(text: chain, color: Cumbre.ink2)
            }
            ForEach(GymPresentation.disciplineChips(gym), id: \.self) { GymTag(text: $0, color: GymTone.discipline) }
            if let tag = GymPresentation.priceTag(gym) {
                GymTag(text: tag, color: gym.isFree ? Cumbre.ok : GymTone.colors(.commercial).text)
            }
            // Un cerrado definitivamente dice solo «Cerrado»: su horario ya no vale.
            if gym.closed {
                GymTag(text: L("gym_closed_tag"), color: Cumbre.bad)
            } else if let open = GymPresentation.openStatusText(status) {
                GymTag(text: open, color: (status is GymOpenStatus.Open || status is GymOpenStatus.AlwaysOpen) ? Cumbre.ok : Cumbre.ink2)
            }
        }
    }
}

/// Cápsula de filtro/selector: terracota si está elegida; papel con borde si no (mono, como el resto de chips).
struct GymChip: View {
    let text: String
    let selected: Bool
    var chevron = false

    var body: some View {
        HStack(spacing: 4) {
            Text(text).lineLimit(1)
            if chevron { Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold)) }
        }
        .font(Cumbre.mono(11, .bold)).tracking(0.8)
        .foregroundStyle(selected ? Color.white : Cumbre.ink2)
        .padding(.horizontal, 12).padding(.vertical, 7)
        .background(selected ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        .contentShape(Capsule())
    }
}

/// Botón en píldora: primario relleno de la marca, o neutro sobre papel con borde.
struct GymActionPill: View {
    let text: String
    var icon: String?
    var primary = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if let icon {
                    Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 18, height: 18)
                }
                Text(text).font(.system(size: 15, weight: .semibold)).lineLimit(2).multilineTextAlignment(.center)
            }
            .foregroundStyle(primary ? Color.white : Cumbre.ink)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12).padding(.vertical, 12)
            .background(primary ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(primary ? Color.clear : Cumbre.rule, lineWidth: 1))
            .opacity(enabled ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Píldora discontinua a ancho completo: la acción secundaria («Añadir un muro que falta», «Corregir»).
struct GymDashedPill: View {
    let text: String
    var translucent = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity).padding(.horizontal, 16).padding(.vertical, 12)
                .background(translucent ? Cumbre.bg.opacity(0.92) : Color.clear, in: Capsule())
                .overlay(Capsule().strokeBorder(Cumbre.ink2, style: StrokeStyle(lineWidth: 1, dash: [5, 4])))
        }
        .buttonStyle(.plain)
    }
}
