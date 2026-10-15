import SwiftUI

/// Grado en píldora rellena con SU color (el mismo de la app: verde, azul, morado, rojo…). Fuera de una foto el
/// color se adapta al tema (`GradeColor.chip`), y los grados ≤5c+ (blancos) llevan texto oscuro y borde para no
/// perderse sobre el papel.
struct GradePill: View {
    let grade: String?
    var small = false
    var large = false
    /// Si la altura parte la opinión sobre este grado: los dos grados, cada uno con su color.
    var split: GradeSplitInfo? = nil

    var body: some View {
        if let split {
            HStack(spacing: 0) { half(split.tall); half(split.short) }
                .clipShape(Capsule())
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        } else {
            single
        }
    }

    private func half(_ g: String) -> some View {
        let dark = GradeColor.style(g).dark
        return Text(g)
            .font(Cumbre.mono(large ? 12 : (small ? 10 : 11), .bold))
            .foregroundStyle(dark ? Color.black.opacity(0.75) : Color.white)
            .padding(.horizontal, large ? 9 : 7).padding(.vertical, large ? 6 : 3)
            .background(GradeColor.chip(g))
    }

    @ViewBuilder private var single: some View {
        let s = GradeColor.style(grade)
        Text(grade ?? "")
            .font(Cumbre.mono(large ? 12 : (small ? 10 : 11), .bold))
            .foregroundStyle(s.dark ? Color.black.opacity(0.75) : Color.white)
            .padding(.horizontal, large ? 11 : 8).padding(.vertical, large ? 6 : 3)
            .background(GradeColor.chip(grade), in: Capsule())
            .overlay(Capsule().stroke(s.dark ? Cumbre.rule : Color.clear, lineWidth: 1))
    }
}
