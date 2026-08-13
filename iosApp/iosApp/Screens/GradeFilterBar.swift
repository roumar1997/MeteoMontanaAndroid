import SwiftUI
import Shared

// Filtro LOCAL por grado dentro de una escuela — ver BLOCK_SEARCH_DESIGN.md §7.
// Espejo de SchoolFiltersBar.kt/SchoolFilterBar (Android): misma escala de
// grados, mismo estilo de chip. Puramente de interfaz — la lógica de qué
// piedra/vía cae en rango vive en shared (GradeFilter.kt, `filterBlocksByGrade`).

/// Escala francesa completa usada en el resto de la app (editor de vías,
/// estadísticas). Única fuente para no divergir entre pantallas.
let GRADE_STEPS: [String] = {
    var out: [String] = []
    for num in 3...9 {
        for letter in ["A", "B", "C", "D"] {
            out.append("\(num)\(letter)")
            out.append("\(num)\(letter)+")
        }
    }
    return out
}()

struct GradeFilterBar: View {
    @Binding var minGrade: String?
    @Binding var maxGrade: String?
    let matchingLines: Int
    let totalLines: Int

    @State private var expanded = false

    private var isActive: Bool { minGrade != nil || maxGrade != nil }

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
                            minGrade = nil; maxGrade = nil
                        } label: {
                            Text("QUITAR")
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
                HStack(spacing: 8) {
                    gradeMenu(title: "mínimo", selection: $minGrade)
                    Text("a").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    gradeMenu(title: "máximo", selection: $maxGrade)
                }
                .padding(.horizontal, 16)

                if isActive {
                    Text("Mostrando \(matchingLines) vías de \(totalLines)")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                        .padding(.horizontal, 16)
                }
            }
        }
        .padding(.bottom, expanded ? 10 : 0)
    }

    @ViewBuilder
    private func gradeMenu(title: String, selection: Binding<String?>) -> some View {
        Menu {
            Button("Sin límite") { selection.wrappedValue = nil }
            ForEach(GRADE_STEPS, id: \.self) { g in
                Button(g) { selection.wrappedValue = g }
            }
        } label: {
            HStack {
                Text(selection.wrappedValue ?? title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        }
        .frame(maxWidth: .infinity)
    }
}
