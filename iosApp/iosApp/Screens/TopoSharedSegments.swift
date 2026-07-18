import SwiftUI

// Tramos COMPARTIDOS entre vías — espejo Swift de sharedSegmentKeys /
// magnetizeStroke / SHARED_SEGMENT_ARGB de TopoRenderer.kt (shared). Igual que
// GradeColor/TopoParse: iOS pinta nativo, así que la lógica se replica aquí.
// Si se toca la versión Kotlin, tocar esta (misma semántica exacta).

enum TopoShared {
    /// Color del tramo compartido (naranja #FF9500 — no choca con la paleta de
    /// grados ni con el rosa discontinuo de proyecto). = SHARED_SEGMENT_ARGB.
    static let color = Color(red: 1.0, green: 149.0 / 255.0, blue: 0.0)

    /// Clave de un punto normalizado, redondeado a 4 decimales (robusto frente
    /// al viaje JSON; el imán copia los valores exactos).
    private static func pointKey(_ p: CGPoint) -> String {
        "\(Int((p.x * 10000).rounded())),\(Int((p.y * 10000).rounded()))"
    }

    private static func segmentKey(_ a: CGPoint, _ b: CGPoint) -> String {
        let ka = pointKey(a), kb = pointKey(b)
        return ka <= kb ? "\(ka)|\(kb)" : "\(kb)|\(ka)"
    }

    /// Claves de los segmentos presentes en DOS o más vías.
    static func sharedSegmentKeys(_ lines: [[CGPoint]]) -> Set<String> {
        var count: [String: Int] = [:]
        for pts in lines {
            var own = Set<String>()
            for i in 0..<max(0, pts.count - 1) { own.insert(segmentKey(pts[i], pts[i + 1])) }
            for k in own { count[k, default: 0] += 1 }
        }
        return Set(count.filter { $0.value >= 2 }.keys)
    }

    /// Trocea una polilínea (puntos NORMALIZADOS) en rachas propias/compartidas.
    /// Devuelve pares (puntos de la racha, esCompartida). Con una sola racha
    /// (lo normal) el resultado es la línea entera sin trocear.
    static func splitRuns(_ points: [CGPoint], shared: Set<String>) -> [(pts: [CGPoint], isShared: Bool)] {
        guard points.count >= 2 else { return [(points, false)] }
        var runs: [(pts: [CGPoint], isShared: Bool)] = []
        var runStart = 0
        var runShared = shared.contains(segmentKey(points[0], points[1]))
        for i in 1..<(points.count - 1) {
            let segShared = shared.contains(segmentKey(points[i], points[i + 1]))
            if segShared != runShared {
                runs.append((Array(points[runStart...i]), runShared))
                runStart = i
                runShared = segShared
            }
        }
        runs.append((Array(points[runStart...]), runShared))
        return runs
    }

    /// IMÁN del editor: espejo exacto de magnetizeStroke de TopoRenderer.kt.
    /// Sustituye los puntos del trazo cercanos a un VÉRTICE de otra vía por ese
    /// vértice exacto, e inserta los vértices intermedios que el dedo saltó.
    static func magnetizeStroke(_ drawn: [CGPoint], others: [[CGPoint]],
                                threshold: CGFloat = 0.02) -> [CGPoint] {
        guard !drawn.isEmpty, !others.isEmpty else { return drawn }
        func snap(_ p: CGPoint) -> (li: Int, vi: Int)? {
            var best: (Int, Int)? = nil
            var bestD = threshold * threshold
            for (li, pts) in others.enumerated() {
                for (vi, v) in pts.enumerated() {
                    let dx = p.x - v.x, dy = p.y - v.y
                    let d = dx * dx + dy * dy
                    if d < bestD { bestD = d; best = (li, vi) }
                }
            }
            return best
        }
        struct Node { let point: CGPoint; let snapped: (li: Int, vi: Int)? }
        let nodes = drawn.map { p -> Node in
            if let s = snap(p) { return Node(point: others[s.li][s.vi], snapped: s) }
            return Node(point: p, snapped: nil)
        }
        var out: [CGPoint] = []
        for (i, n) in nodes.enumerated() {
            if i > 0, let a = nodes[i - 1].snapped, let b = n.snapped,
               a.li == b.li, abs(b.vi - a.vi) > 1 {
                let pts = others[a.li]
                if b.vi > a.vi {
                    for vi in (a.vi + 1)..<b.vi { out.append(pts[vi]) }
                } else {
                    for vi in stride(from: a.vi - 1, through: b.vi + 1, by: -1) { out.append(pts[vi]) }
                }
            }
            out.append(n.point)
        }
        var dedup: [CGPoint] = []
        for p in out where dedup.last != p { dedup.append(p) }
        return dedup
    }
}
