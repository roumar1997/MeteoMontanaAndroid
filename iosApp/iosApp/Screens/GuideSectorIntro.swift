import Shared

/// Inicio de un sector de la guía: todas sus vías agrupadas por grado, de fácil a difícil. Se calcula UNA vez
/// junto con la guía (en `SchoolBlocksExplorerModel`), no cada vez que se pinta la tarjeta.
struct GuideSectorIntro {
    /// Grados presentes, ordenados de fácil a difícil ("?" para las que no tienen).
    let grades: [String]
    /// Vías (con su piedra) de cada grado, en el orden de la guía.
    let chipsByGrade: [String: [GuideChip]]

    init(_ s: GuideSector) {
        let pairs = s.blocks.flatMap { b in b.lines.map { GuideChip(block: b, hit: $0) } }
        var byGrade: [String: [GuideChip]] = [:]
        for c in pairs { byGrade[c.hit.grade ?? "?", default: []].append(c) }
        grades = byGrade.keys.sorted { GradeOrder.score($0) < GradeOrder.score($1) }
        chipsByGrade = byGrade
    }
}
