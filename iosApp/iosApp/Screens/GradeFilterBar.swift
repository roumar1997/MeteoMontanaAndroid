import SwiftUI
import Shared

// Filtro LOCAL por grado dentro de una escuela — ver BLOCK_SEARCH_DESIGN.md §7.
// Chips multi-selección con la paleta de grados de la app (mismo patrón que
// JournalBlocksListView), + lista de resultados agrupada por grado y
// colapsable. La lógica de qué piedra/vía cae en la selección vive en shared
// (GradeFilter.kt, `filterBlocksByGrades`) — este fichero es solo interfaz.

struct GradeFilterBar: View {
    @Binding var selectedGrades: Set<String>
    let availableGrades: [String]
    let result: GradeFilterResult
    let onSelectLine: (GradeMatch) -> Void

    @State private var expanded = false
    /// Grados con su grupo de resultados desplegado. Se conserva mientras la
    /// escuela siga abierta (aunque se navegue a una vía y se vuelva atrás).
    @State private var openGroups: Set<String> = []

    private var isActive: Bool { !selectedGrades.isEmpty }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { expanded.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 13))
                        .foregroundStyle(isActive ? Cumbre.terra : Cumbre.ink3)
                    Text("FILTRAR POR GRADO")
                        .font(Cumbre.mono(10, .bold)).tracking(1.4)
                        .foregroundStyle(isActive ? Cumbre.terra : Cumbre.ink3)
                    Spacer()
                    if isActive {
                        Button {
                            selectedGrades = []
                        } label: {
                            Text("QUITAR TODO")
                                .font(Cumbre.mono(9, .bold)).tracking(1)
                                .foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                    }
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                if availableGrades.isEmpty {
                    Text("Esta escuela todavía no tiene vías con grado.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 16)
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(availableGrades, id: \.self) { g in
                                gradeChip(g)
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    if isActive {
                        Text("Mostrando \(result.matchingLines) vías de \(result.totalLines)")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                            .padding(.horizontal, 16)

                        resultsList
                    }
                }
            }
        }
        .padding(.bottom, expanded ? 10 : 0)
    }

    @ViewBuilder
    private func gradeChip(_ g: String) -> some View {
        let st = GradeColor.style(g)
        let accent = st.dark ? Cumbre.ink : st.stroke
        let active = selectedGrades.contains(g)
        Button {
            if active { selectedGrades.remove(g) } else { selectedGrades.insert(g) }
        } label: {
            Text(g.lowercased())
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(active ? .white : accent)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(RoundedRectangle(cornerRadius: 2)
                    .fill(active ? accent : Cumbre.paper))
                .overlay(RoundedRectangle(cornerRadius: 2)
                    .stroke(accent, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultsList: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(result.groups, id: \.0) { grade, matches in
                gradeGroup(grade: grade, matches: matches)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    @ViewBuilder
    private func gradeGroup(grade: String, matches: [GradeMatch]) -> some View {
        let st = GradeColor.style(grade)
        let accent = st.dark ? Cumbre.ink : st.stroke
        let isOpen = openGroups.contains(grade)

        Button {
            withAnimation(.easeInOut(duration: 0.12)) {
                if isOpen { openGroups.remove(grade) } else { openGroups.insert(grade) }
            }
        } label: {
            HStack(spacing: 8) {
                Text(grade.lowercased())
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 9).padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 2).fill(accent))
                Text(matches.count == 1 ? "1 vía" : "\(matches.count) vías")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                Spacer()
                Image(systemName: isOpen ? "chevron.down" : "chevron.right")
                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)

        if isOpen {
            VStack(spacing: 0) {
                ForEach(matches, id: \.lineId) { match in
                    Button {
                        onSelectLine(match)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(match.lineName)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Cumbre.ink)
                                Text(match.blockName)
                                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11)).foregroundStyle(Cumbre.terra)
                        }
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Divider().background(Cumbre.rule)
                }
            }
        }
    }
}
