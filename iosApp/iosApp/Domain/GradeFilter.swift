import Foundation
import Shared

// Espejo de shared/.../domain/util/GradeFilter.kt — MISMA fórmula de score
// que gradeArgb/GradeColor (un único criterio de orden de grados en toda la
// app). Ver BLOCK_SEARCH_DESIGN.md §7 y ARCHITECTURE.md §4 (paridad).
// Selección MÚLTIPLE de grados exactos (chips, como en el diario) — no rango.

/// Convierte un grado francés ("6a", "7B+"...) al score numérico:
/// score = número*100 + letra*10 + (+ ? 1 : 0). nil si no es reconocible.
func gradeScore(_ grade: String?) -> Int? {
    let g = (grade ?? "").trimmingCharacters(in: .whitespaces).uppercased()
    guard let regex = try? NSRegularExpression(pattern: "^([3-9])([ABCD])?(\\+)?$") else { return nil }
    let range = NSRange(g.startIndex..., in: g)
    guard let match = regex.firstMatch(in: g, range: range) else { return nil }

    func group(_ i: Int) -> String {
        guard let r = Range(match.range(at: i), in: g) else { return "" }
        return String(g[r])
    }
    guard let num = Int(group(1)) else { return nil }
    let letterScore = ["A": 0, "B": 1, "C": 2, "D": 3][group(2)] ?? 0
    let plus = group(3) == "+" ? 1 : 0
    return num * 100 + letterScore * 10 + plus
}

/// Grados que EXISTEN de verdad en estas piedras, de más difícil a más fácil.
func availableGrades(_ blocks: [Block]) -> [String] {
    var seen = Set<String>()
    var ordered: [String] = []
    for block in blocks {
        for line in block.lines {
            let g = (line.grade ?? "").trimmingCharacters(in: .whitespaces).uppercased()
            guard gradeScore(g) != nil, !seen.contains(g) else { continue }
            seen.insert(g)
            ordered.append(g)
        }
    }
    return ordered.sorted { (gradeScore($0) ?? 0) > (gradeScore($1) ?? 0) }
}

/// Una vía que ha caído dentro de la selección de grados, con su piedra de origen.
struct GradeMatch {
    let lineId: String
    let lineName: String
    let blockId: String
    let blockName: String
    let grade: String
}

/// Resultado del filtro: qué piedras/vías caen en la selección, agrupadas por grado.
struct GradeFilterResult {
    let matchingBlockIds: Set<String>
    let matchingLineIds: Set<String>
    let totalLines: Int
    /// Grado (desc.) → vías con ese grado, en el mismo orden que availableGrades.
    let groups: [(String, [GradeMatch])]
    var matchingLines: Int { matchingLineIds.count }
}

/// @param selectedGrades grados exactos elegidos (ej. ["6A+", "7B"]), vacío = sin filtro.
/// Piedras con AL MENOS una vía seleccionada, las vías concretas, y esas mismas
/// vías agrupadas por grado para listarlas en la UI.
func filterBlocksByGrades(_ blocks: [Block], selectedGrades: Set<String>) -> GradeFilterResult {
    let selected = Set(selectedGrades.map { $0.trimmingCharacters(in: .whitespaces).uppercased() })

    var matchingBlocks = Set<String>()
    var matchingLines = Set<String>()
    var byGrade: [String: [GradeMatch]] = [:]
    var gradeOrder: [String] = []
    var total = 0

    for block in blocks {
        for line in block.lines {
            total += 1
            let g = (line.grade ?? "").trimmingCharacters(in: .whitespaces).uppercased()
            guard !g.isEmpty, selected.contains(g) else { continue }
            matchingLines.insert(line.id)
            matchingBlocks.insert(block.id)
            if byGrade[g] == nil { byGrade[g] = []; gradeOrder.append(g) }
            byGrade[g]!.append(GradeMatch(
                lineId: line.id, lineName: line.displayName,
                blockId: block.id, blockName: block.name, grade: g))
        }
    }

    let groups = gradeOrder
        .sorted { (gradeScore($0) ?? 0) > (gradeScore($1) ?? 0) }
        .map { ($0, byGrade[$0] ?? []) }

    return GradeFilterResult(
        matchingBlockIds: matchingBlocks, matchingLineIds: matchingLines,
        totalLines: total, groups: groups)
}
