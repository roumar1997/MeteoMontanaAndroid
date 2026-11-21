import SwiftUI
import Shared

/// Sugerencias de nombre (las propias primero y luego las de todas las personas): solo mientras se escribe.
/// Espejo de `NameSuggestions` (BoardNameAssist.kt).
struct BoardNameSuggestions: View {
    let suggestions: [BoardProblemSuggestion]
    let onPick: (BoardProblemSuggestion) -> Void

    var body: some View {
        if !suggestions.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(suggestions.enumerated()), id: \.offset) { _, s in
                    let meta = [s.grade.uppercased(), s.angle.map { "\($0)°" }, L("gym_board_suggest_people", s.count)]
                        .compactMap { $0 }.joined(separator: " · ")
                    Button { onPick(s) } label: {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(s.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                            Text(meta).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14).padding(.vertical, 8).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Cuántas veces se apunta de golpe el mismo bloque (entrenos de vueltas): − ×N + (1 a 20, shared).
struct BoardRepsStepper: View {
    @Binding var reps: Int

    var body: some View {
        let max = Int(BoardProblemRepeats.shared.MAX_REPS)
        HStack(spacing: 10) {
            Text(L("gym_board_reps_label")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            step("−", reps > 1, L("gym_board_reps_minus_cd")) { reps = Int(BoardProblemRepeats.shared.clampReps(n: Int32(reps - 1))) }
            Text("×\(reps)").font(.system(size: 16, weight: .bold)).foregroundStyle(Cumbre.ink).padding(.horizontal, 4)
            step("+", reps < max, L("gym_board_reps_plus_cd")) { reps = Int(BoardProblemRepeats.shared.clampReps(n: Int32(reps + 1))) }
        }
    }

    private func step(_ label: String, _ enabled: Bool, _ a11y: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 18, weight: .bold))
                .foregroundStyle(enabled ? Cumbre.terraFill : Cumbre.ink2.opacity(0.4))
                .frame(width: 40, height: 40).overlay(Circle().stroke(Cumbre.rule, lineWidth: 1)).contentShape(Circle())
        }
        .buttonStyle(.plain).disabled(!enabled).accessibilityLabel(a11y)
    }
}
