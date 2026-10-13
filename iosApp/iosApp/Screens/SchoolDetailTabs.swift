import SwiftUI

/// Pestañas de la ficha de escuela, bajo la tarjeta HOY. El mapa va por defecto.
enum SchoolTab: CaseIterable {
    case map, blocks, weather, notes

    /// "BLOQUES" si solo hay bloques, "VÍAS" si solo hay vías y "BLOQUES/VÍAS" si hay de las dos
    /// (`school.style` = "Vía,Bloque"). Sin estilo, "BLOQUES".
    static func blocksLabel(style: String?) -> String {
        let styles = Set(style?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).uppercased() } ?? [])
        let hasRoutes = styles.contains("VÍA") || styles.contains("VIA")
        let hasBoulders = styles.contains("BLOQUE")
        if hasRoutes && hasBoulders { return L("BLOQUES/VÍAS") }
        if hasRoutes { return L("VÍAS") }
        return L("BLOQUES")
    }

    func label(schoolStyle: String?) -> String {
        switch self {
        case .map: return L("MAPA")
        case .blocks: return Self.blocksLabel(style: schoolStyle)
        case .weather: return L("TIEMPO")
        case .notes: return L("NOTAS")
        }
    }
}

/// Barra de pestañas en píldoras (controles interactivos = redondeados en toda la app).
struct SchoolTabBar: View {
    @Binding var selection: SchoolTab
    let schoolStyle: String?

    var body: some View {
        HStack(spacing: 6) {
            ForEach(SchoolTab.allCases, id: \.self) { t in
                Button { selection = t } label: {
                    Text(t.label(schoolStyle: schoolStyle)).font(Cumbre.mono(10.5, .bold)).tracking(0.6)
                        .lineLimit(1).minimumScaleFactor(0.8)
                        .foregroundStyle(selection == t ? Color.white : Cumbre.ink2)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .background(selection == t ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                        .overlay(Capsule().stroke(selection == t ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}
