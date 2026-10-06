import SwiftUI

/// Qué se escala en una escuela, según su campo `style` del catálogo
/// ("Bloque", "Vía" o "Bloque,Vía"). Lógica pura, sin vista: se prueba sola
/// (SchoolKindTests). Álvaro, 2026-10-06: mosquetón = vías, crashpad = bloques,
/// los dos = ambos, en la fila de la lista de escuelas.
struct SchoolKind: Equatable {
    let hasRoutes: Bool
    let hasBoulders: Bool

    init(style: String?) {
        let s = (style ?? "").folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
        hasBoulders = s.contains("bloque") || s.contains("boulder")
        hasRoutes = s.contains("via") || s.contains("route")
    }

    var isEmpty: Bool { !hasRoutes && !hasBoulders }
}

/// Iconos de vía/bloque junto al nombre de la escuela. Imágenes en plantilla
/// (template) para que tomen el color de la app; vacío si no se sabe el estilo.
struct SchoolKindIcons: View {
    let kind: SchoolKind
    var height: CGFloat = 18

    var body: some View {
        HStack(spacing: 6) {
            if kind.hasRoutes {
                Image("kind_route")
                    .renderingMode(.template)
                    .resizable().scaledToFit()
                    .frame(height: height)
                    .accessibilityLabel(L("Vías"))
            }
            if kind.hasBoulders {
                Image("kind_boulder")
                    .renderingMode(.template)
                    .resizable().scaledToFit()
                    .frame(height: height)
                    .accessibilityLabel(L("Bloques"))
            }
        }
        .foregroundStyle(Cumbre.terra)
    }
}
