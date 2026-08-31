import Foundation

/// Grados DOBLES ("7a/7a+") — espejo Swift de `GradeRange.kt` del shared.
/// Si se toca el Kotlin, tocar este (mismo patrón que GradeColor/TopoParse).
///
/// Se llama `GradeRangeUI` y no `GradeRange` para no chocar con el objeto
/// Kotlin del mismo nombre que SKIE exporta en el módulo `Shared`.
enum GradeRangeUI {
    static let sep = "/"

    /// Grado con el que se colorea/ordena: el primero del rango.
    static func base(_ grade: String?) -> String? {
        let first = (grade ?? "").components(separatedBy: sep).first ?? ""
        let t = first.trimmingCharacters(in: .whitespaces).uppercased()
        return t.isEmpty ? nil : t
    }

    /// Los grados que componen la selección actual (1 o 2, en orden).
    static func parts(_ grade: String?) -> [String] {
        (grade ?? "").components(separatedBy: sep)
            .map { $0.trimmingCharacters(in: .whitespaces).uppercased() }
            .filter { !$0.isEmpty }
    }

    static func contains(_ current: String?, _ candidate: String) -> Bool {
        parts(current).contains(candidate.trimmingCharacters(in: .whitespaces).uppercased())
    }

    /// Regla de toque en la rejilla de grados (ver GradeRange.kt):
    /// nada → suelto · uno + otro → rango ordenado · ya puesto → se quita ·
    /// rango completo + un tercero → empieza de nuevo con ese.
    static func toggle(_ current: String?, _ tapped: String) -> String? {
        let t = tapped.trimmingCharacters(in: .whitespaces).uppercased()
        guard !t.isEmpty else { return current }
        let p = parts(current)
        if p.contains(t) { return p.filter { $0 != t }.first }
        if p.isEmpty { return t }
        if p.count == 1 {
            return [p[0], t].sorted { score($0) < score($1) }.joined(separator: sep)
        }
        return t
    }

    /// Puntuación para ordenar el rango de fácil a difícil ("7a/7a+").
    private static func score(_ g: String) -> Int {
        let chars = Array(g)
        guard let first = chars.first, let num = first.wholeNumberValue else { return 0 }
        var idx = 1, letter = 0
        if idx < chars.count, let l = "ABCD".firstIndex(of: chars[idx]) {
            letter = "ABCD".distance(from: "ABCD".startIndex, to: l); idx += 1
        }
        let plus = (idx < chars.count && chars[idx] == "+") ? 1 : 0
        return num * 100 + letter * 10 + plus
    }
}
