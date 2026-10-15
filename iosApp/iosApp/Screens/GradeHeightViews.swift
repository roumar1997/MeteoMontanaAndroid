import SwiftUI
import Shared

// Votar el grado teniendo en cuenta la ALTURA (2026-10-15). Un bloque no se siente igual con 1,60 m que con 1,90 m:
// cada persona elige UNA vez si mide más o menos de 1,70 m (se guarda en la mochilita, privado) y sus votos se agrupan
// con los de su altura. Cuando los dos grupos votan un grado muy distinto, el grado se "parte" en dos colores.

/// Un reparto de grado por altura: el grado preferido por los de más y por los de menos de 1,70 m.
struct GradeSplitInfo: Equatable {
    let tall: String
    let short: String
}

/// Lo justo para pintar la pastilla del grado sin abrir la hoja: cuántos votos hay y si la altura parte la opinión.
struct GradeBadge: Equatable {
    let count: Int
    let split: GradeSplitInfo?

    /// "14 votos · ver y votar" · "14 votos · según la altura" · "Sin votos · votar".
    var caption: String {
        if count == 0 { return L("Sin votos · votar") }
        let votes = count == 1 ? L("1 voto") : L("%@ votos", count)
        return split == nil ? votes + L(" · ver y votar") : votes + L(" · según la altura")
    }
}

/// El grado como una pastilla con su COLOR de siempre y una flecha clara en un círculo (se lee como botón). Si la
/// altura parte la opinión, son dos colores seguidos.
struct GradeChip: View {
    let grade: String
    let split: GradeSplitInfo?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 0) {
                if let split {
                    segment(split.tall)
                    segment(split.short)
                } else {
                    segment(grade)
                }
                chevron
            }
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Grado %@. Ver y votar", grade))
    }

    private func segment(_ g: String) -> some View {
        let dark = GradeColor.chipStyle(g).dark
        return Text(g)
            .font(Cumbre.mono(12, .bold))
            .foregroundStyle(dark ? Color.black : Color.white)
            .padding(.leading, 10).padding(.trailing, 8)
            .frame(height: 30)
            .background(GradeColor.chip(g))
    }

    private var chevron: some View {
        let last = split?.short ?? grade
        let dark = GradeColor.chipStyle(last).dark
        return Image(systemName: "chevron.down")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(dark ? Color.black : Color.white)
            .frame(width: 28, height: 30)
            .background(GradeColor.chip(last).overlay(Color.black.opacity(0.18)))
    }
}

/// "+ 1,70 · − 1,70 · Prefiero no decirlo": la altura en tres opciones.
struct HeightChoiceView: View {
    let selected: HeightGroup?
    let onSelect: (HeightGroup) -> Void

    var body: some View {
        HStack(spacing: 6) {
            option(.tall, "+ 1,70")
            option(.short, "− 1,70")
            option(.unspecified, L("Prefiero no decirlo"))
        }
    }

    private func option(_ group: HeightGroup, _ label: String) -> some View {
        let isOn = selected == group
        return Button { onSelect(group) } label: {
            Text(label)
                .font(Cumbre.mono(11, .bold))
                .foregroundStyle(isOn ? Color.white : Cumbre.ink)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity).padding(.vertical, 9).padding(.horizontal, 2)
                .background(RoundedRectangle(cornerRadius: 8).fill(isOn ? Cumbre.terra : Cumbre.paper))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(isOn ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// El aviso de la hoja de votar cuando todavía no has dicho tu altura: se pide UNA vez, dentro del propio voto.
struct HeightPromptCard: View {
    let selected: HeightGroup?
    let onSelect: (HeightGroup) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("Antes, tu altura")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.terra)
            Text(L("Así tu voto cuenta con los que miden como tú. La guardamos en tu mochilita y no se muestra a nadie."))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            HeightChoiceView(selected: selected, onSelect: onSelect)
        }
        .padding(10)
        .background(Cumbre.terraBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.terra, lineWidth: 1.5))
    }
}

/// Texto de una altura ("+ 1,70", "− 1,70" o "sin decir").
func heightLabel(_ group: HeightGroup) -> String {
    switch group {
    case .tall: return "+ 1,70"
    case .short: return "− 1,70"
    default: return L("sin decir")
    }
}

/// Los votos de un grado separados por la altura de quien vota: "MÁS DE 1,70 · 9 VOTOS" + sus barras, etc.
struct HeightVoteGroups: View {
    let summary: GradeSummary

    var body: some View {
        let groups = [("TALL", L("MÁS DE 1,70")), ("SHORT", L("MENOS DE 1,70")), ("NONE", L("SIN DATO"))]
        VStack(alignment: .leading, spacing: 10) {
            ForEach(groups, id: \.0) { key, title in
                let votes = (summary.votesByHeight[key] ?? [:]).mapValues { $0.intValue }
                let total = votes.values.reduce(0, +)
                if total > 0 {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(title) · \(total == 1 ? L("1 VOTO") : L("%@ VOTOS", total))")
                            .font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
                        VoteBars(votes: votes, highlight: summary.displayedGrade, myVote: summary.myVote, gradeColored: true)
                    }
                    .padding(10)
                    .background(Cumbre.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                }
            }
        }
    }

    /// ¿Hay votos separados por altura? Si todos son "sin dato" se enseña la barra única de siempre.
    static func hasHeightData(_ summary: GradeSummary) -> Bool {
        let tall = (summary.votesByHeight["TALL"] ?? [:]).values.reduce(0) { $0 + $1.intValue }
        let short = (summary.votesByHeight["SHORT"] ?? [:]).values.reduce(0) { $0 + $1.intValue }
        return tall + short > 0
    }
}
