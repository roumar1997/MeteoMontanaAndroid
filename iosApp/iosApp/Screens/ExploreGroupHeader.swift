import SwiftUI
import Shared

/// Cabecera de grupo de la pestaña «Vías/Bloques»: nombre de la escuela + su índice de escalabilidad de
/// HOY (el mismo score que en la lista de Escuelas) + aviso MOJADA — una sola vez por grupo en vez de
/// repetirlo vía a vía (§8.3). Pulsable: se minimiza para poder ver el resto de escuelas sin tanto scroll
/// (Álvaro, 2026-10-01: "que se minimice y pueda ver el resto").
struct ExploreGroupHeader: View {
    let schoolName: String
    let hitCount: Int
    let score: SchoolScore?
    /// Score combinado del tramo de días elegido (DaySelectorRow); `nil` si no hay tramo.
    let range: RangeScore?
    let rangeMode: Bool
    let kind: SchoolKind
    let collapsed: Bool
    let onToggle: () -> Void

    var body: some View {
        // Con un tramo de días elegido se muestra el score COMBINADO del tramo, igual que la lista de Escuelas;
        // si no, el de HOY (Álvaro, 2026-10-01).
        let scoreInt = rangeMode ? range.map { Int($0.combinedScore) } : score.map { Int($0.todayScore) }
        let color = scoreInt.map { Cumbre.score($0) } ?? Cumbre.ink3
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 10) {
                    Text(scoreInt.map(String.init) ?? "—")
                        .font(Cumbre.serif(17, .bold))
                        .foregroundStyle(color)
                        .frame(width: 34, height: 30)
                        .background(color.opacity(0.12))
                        .overlay(Rectangle().stroke(color, lineWidth: 1))
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 7) {
                            Text(schoolName).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                            if !kind.isEmpty { SchoolKindIcons(kind: kind, height: 15) }
                        }
                        if score?.dryRock == false {
                            Text(L("● MOJADA")).font(.system(size: 10, weight: .semibold)).tracking(0.6)
                                .foregroundStyle(Cumbre.bad)
                        }
                    }
                    Spacer()
                    Text(hitCount == 1 ? L("1 vía/bloque") : L("%@ vías/bloques", hitCount))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    Image(systemName: collapsed ? "chevron.down" : "chevron.up")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, rangeMode ? 4 : 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // Tramo de días: una puntuación por día (J/V/S…), no la combinada sola — mismo componente que la
            // lista de Escuelas (Álvaro, 2026-10-01: "que salgan las tres puntuaciones... no la suma").
            if rangeMode, let range {
                DayRangeRow(range: range)
                    .padding(.horizontal, 16).padding(.bottom, 8)
            }
        }
        .background(Cumbre.paper)
    }
}
