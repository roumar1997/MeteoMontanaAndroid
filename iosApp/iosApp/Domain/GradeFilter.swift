import Foundation
import Shared

// Espejo de shared/.../domain/util/GradeFilter.kt — MISMA fórmula de score
// que gradeArgb/GradeColor (un único criterio de orden de grados en toda la
// app). Ver BLOCK_SEARCH_DESIGN.md §7 y ARCHITECTURE.md §4 (paridad).

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

/// Resultado del filtro: qué piedras y qué vías concretas caen en rango.
struct GradeFilterResult {
    let matchingBlockIds: Set<String>
    let matchingLineIds: Set<String>
    let totalLines: Int
    let matchingLines: Int
}

/// @param minGrade grado mínimo (ej. "7A"), nil = sin suelo
/// @param maxGrade grado máximo (ej. "7B+"), nil = sin techo
/// Piedras con AL MENOS una vía en rango, y las vías concretas en rango (para
/// atenuar el resto dentro de la ficha/mapa, no ocultarlas).
func filterBlocksByGrade(_ blocks: [Block], minGrade: String?, maxGrade: String?) -> GradeFilterResult {
    let minScore = gradeScore(minGrade) ?? Int.min
    let maxScore = gradeScore(maxGrade) ?? Int.max

    var matchingBlocks = Set<String>()
    var matchingLines = Set<String>()
    var total = 0

    for block in blocks {
        for line in block.lines {
            total += 1
            guard let score = gradeScore(line.grade) else { continue }
            if score >= minScore && score <= maxScore {
                matchingLines.insert(line.id)
                matchingBlocks.insert(block.id)
            }
        }
    }
    return GradeFilterResult(
        matchingBlockIds: matchingBlocks, matchingLineIds: matchingLines,
        totalLines: total, matchingLines: matchingLines.count)
}
