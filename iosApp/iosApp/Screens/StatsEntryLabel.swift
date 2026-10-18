import SwiftUI

/// El botón de ESTADÍSTICAS del perfil: el MISMO en tu perfil y en el de cualquier persona (antes eran dos celdas
/// distintas, una con «▃▅▇» y otra con «▸»). Unas barritas que suben con los colores de los grados, el título y una
/// línea que dice qué hay dentro (Álvaro, 2026-10-18: «hacerlo algo más bonito»).
struct StatsEntryLabel: View {
    /// Alturas relativas y color de cada barrita: los colores de los grados, de fácil a duro.
    private let bars: [(CGFloat, Color)] = [
        (0.35, Color(red: 0.62, green: 0.62, blue: 0.58)),
        (0.55, Color(red: 0.25, green: 0.62, blue: 0.40)),
        (0.78, Color(red: 0.15, green: 0.39, blue: 0.78)),
        (1.00, Color(red: 0.55, green: 0.36, blue: 0.96)),
    ]

    var body: some View {
        HStack(spacing: 14) {
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                    RoundedRectangle(cornerRadius: 2).fill(bar.1).frame(width: 7, height: 30 * bar.0)
                }
            }
            .frame(width: 40, height: 30, alignment: .bottomLeading)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("ESTADÍSTICAS")).font(Cumbre.mono(12, .bold)).tracking(1).foregroundStyle(Cumbre.ink)
                Text(L("Pirámide, objetivo del año y logros")).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.terra)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
        .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.terra.opacity(0.55), lineWidth: 1))
    }
}
