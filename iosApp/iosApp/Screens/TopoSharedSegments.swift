import SwiftUI

// Tramos COMPARTIDOS entre vías + utilidades del editor — espejo Swift de
// sharedSegmentLines / magnetizeStroke / simplifyStroke / fanOffsets de
// TopoRenderer.kt (shared). Igual que GradeColor/TopoParse: iOS pinta nativo,
// así que la lógica se replica aquí. Si se toca la versión Kotlin, tocar esta.

enum TopoShared {
    /// Largo de cada franja del tramo compartido, en px del canvas.
    /// = SHARED_STRIPE_PX. Los canvas escalados (share 1080) lo multiplican.
    static let stripe: CGFloat = 22

    /// Guion de TODAS las líneas (estilo guía: discontinuas para no tapar la
    /// roca) — espejo del dashPx por defecto de renderTopo.
    static let dash: [CGFloat] = [12, 9]

    /// Clave de un punto normalizado, redondeado a 4 decimales (robusto frente
    /// al viaje JSON; el imán copia los valores exactos).
    private static func pointKey(_ p: CGPoint) -> String {
        "\(Int((p.x * 10000).rounded())),\(Int((p.y * 10000).rounded()))"
    }

    private static func segmentKey(_ a: CGPoint, _ b: CGPoint) -> String {
        let ka = pointKey(a), kb = pointKey(b)
        return ka <= kb ? "\(ka)|\(kb)" : "\(kb)|\(ka)"
    }

    /// Segmento compartido → índices (ordenados) de las vías que lo comparten.
    /// Solo entradas con 2+ vías.
    static func sharedSegmentLines(_ lines: [[CGPoint]]) -> [String: [Int]] {
        var byKey: [String: Set<Int>] = [:]
        for (idx, pts) in lines.enumerated() {
            guard pts.count >= 2 else { continue }
            for i in 0..<(pts.count - 1) {
                byKey[segmentKey(pts[i], pts[i + 1]), default: []].insert(idx)
            }
        }
        return byKey.filter { $0.value.count >= 2 }.mapValues { $0.sorted() }
    }

    /// Una racha de la polilínea: puntos + vías que comparten ese tramo
    /// (vacío = tramo propio).
    struct Run { let pts: [CGPoint]; let sharers: [Int] }

    /// Trocea una polilínea (puntos NORMALIZADOS) en rachas propias/compartidas.
    static func splitRuns(_ points: [CGPoint], shared: [String: [Int]]) -> [Run] {
        guard points.count >= 2 else { return [Run(pts: points, sharers: [])] }
        var runs: [Run] = []
        var runStart = 0
        var runSharers = shared[segmentKey(points[0], points[1])] ?? []
        for i in 1..<(points.count - 1) {
            let s = shared[segmentKey(points[i], points[i + 1])] ?? []
            if s != runSharers {
                runs.append(Run(pts: Array(points[runStart...i]), sharers: runSharers))
                runStart = i
                runSharers = s
            }
        }
        runs.append(Run(pts: Array(points[runStart...]), sharers: runSharers))
        return runs
    }

    /// (dash, phase) de la franja de la vía [lineIdx] en una racha compartida,
    /// escalado por [s]. nil si la racha no es compartida.
    static func stripeStyle(_ run: Run, lineIdx: Int, scale s: CGFloat = 1) -> (dash: [CGFloat], phase: CGFloat)? {
        guard run.sharers.count >= 2 else { return nil }
        let n = CGFloat(run.sharers.count)
        let k = CGFloat(run.sharers.firstIndex(of: lineIdx) ?? 0)
        return ([stripe * s, stripe * s * (n - 1)], k * stripe * s)
    }

    /// IMÁN del editor: espejo exacto de magnetizeStroke de TopoRenderer.kt.
    /// v2: se compara contra CUALQUIER TRAMO de las otras vías (no solo sus
    /// vértices — antes era casi imposible acertar con el dedo) y se pega al
    /// vértice más cercano de ese tramo (compartir sigue siendo EXACTO).
    static func magnetizeStroke(_ drawn: [CGPoint], others: [[CGPoint]],
                                threshold: CGFloat = 0.04) -> [CGPoint] {
        guard !drawn.isEmpty, !others.isEmpty else { return drawn }
        func snap(_ p: CGPoint) -> (li: Int, vi: Int)? {
            var best: (Int, Int)? = nil
            var bestD = threshold * threshold
            for (li, pts) in others.enumerated() {
                if pts.count == 1 {
                    let dx = p.x - pts[0].x, dy = p.y - pts[0].y
                    let d = dx * dx + dy * dy
                    if d < bestD { bestD = d; best = (li, 0) }
                    continue
                }
                for si in 0..<(pts.count - 1) {
                    let a = pts[si], b = pts[si + 1]
                    let abx = b.x - a.x, aby = b.y - a.y
                    let len2 = abx * abx + aby * aby
                    let t = len2 < 1e-12 ? 0
                        : max(0, min(1, ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2))
                    let qx = a.x + t * abx, qy = a.y + t * aby
                    let dx = p.x - qx, dy = p.y - qy
                    let d = dx * dx + dy * dy
                    if d < bestD {
                        bestD = d
                        best = (li, t < 0.5 ? si : si + 1)
                    }
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

    /// SUAVIZADO del trazo a mano (Douglas-Peucker) — espejo de simplifyStroke.
    static func simplifyStroke(_ points: [CGPoint], epsilon: CGFloat = 0.006) -> [CGPoint] {
        guard points.count > 2 else { return points }
        func perpDist(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
            let dx = b.x - a.x, dy = b.y - a.y
            let len = (dx * dx + dy * dy).squareRoot()
            if len < 1e-9 {
                let ex = p.x - a.x, ey = p.y - a.y
                return (ex * ex + ey * ey).squareRoot()
            }
            return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
        }
        var keep = [Bool](repeating: false, count: points.count)
        keep[0] = true; keep[points.count - 1] = true
        func dp(_ from: Int, _ to: Int) {
            var maxD: CGFloat = 0; var maxI = -1
            if to > from + 1 {
                for i in (from + 1)..<to {
                    let d = perpDist(points[i], points[from], points[to])
                    if d > maxD { maxD = d; maxI = i }
                }
            }
            if maxD > epsilon && maxI > 0 {
                keep[maxI] = true
                dp(from, maxI); dp(maxI, to)
            }
        }
        dp(0, points.count - 1)
        return points.enumerated().filter { keep[$0.offset] }.map { $0.element }
    }

    /// ABANICO de badges: desplazamiento X (px) por vía cuando varios badges
    /// coinciden en el mismo punto — espejo de fanOffsets.
    static func fanOffsets(_ anchors: [CGPoint?], spacing: CGFloat) -> [CGFloat] {
        var groups: [String: [Int]] = [:]
        for (idx, p) in anchors.enumerated() {
            if let p { groups[pointKey(p), default: []].append(idx) }
        }
        var out = [CGFloat](repeating: 0, count: anchors.count)
        for members in groups.values where members.count > 1 {
            for (k, idx) in members.enumerated() {
                out[idx] = (CGFloat(k) - CGFloat(members.count - 1) / 2) * spacing
            }
        }
        return out
    }
}
