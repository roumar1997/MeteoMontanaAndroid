import SwiftUI
import Shared

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

    /// QUITA EL SOBRE-TRAZADO de trazos antiguos (espejo de dropRetrace de
    /// TopoRenderer.kt): linePath que recorren la misma línea varias veces
    /// («La ola»: 3 pasadas) rellenan los huecos del guion entre pasadas y la
    /// línea se ve CONTINUA (y el canvas pinta 3×). Si hay varias pasadas y la
    /// más larga cubre ≥80% del ancho, nos quedamos solo con esa.
    static func dropRetrace(_ points: [CGPoint]) -> [CGPoint] {
        guard points.count >= 8 else { return points }
        let xs = points.map { $0.x }, ys = points.map { $0.y }
        let spanX = (xs.max() ?? 0) - (xs.min() ?? 0)
        let spanY = (ys.max() ?? 0) - (ys.min() ?? 0)
        let horizontal = spanX >= spanY
        let span = horizontal ? spanX : spanY
        guard span > 1e-6 else { return points }
        func axis(_ p: CGPoint) -> CGFloat { horizontal ? p.x : p.y }

        var travelled: CGFloat = 0
        for i in 0..<(points.count - 1) { travelled += abs(axis(points[i + 1]) - axis(points[i])) }
        guard travelled >= span * 1.6 else { return points }

        let tol: CGFloat = 0.01
        var runs: [(Int, Int)] = []
        var runStart = 0
        var dir: CGFloat = 0
        var reversal: CGFloat = 0
        var reversalStart = -1
        for i in 0..<(points.count - 1) {
            let step = axis(points[i + 1]) - axis(points[i])
            if dir == 0, abs(step) > 1e-6 {
                dir = step > 0 ? 1 : -1
            } else if dir != 0, step * dir < 0 {
                if reversalStart < 0 { reversalStart = i }
                reversal += abs(step)
                if reversal > tol {
                    runs.append((runStart, reversalStart))
                    runStart = reversalStart
                    dir = -dir
                    reversal = 0; reversalStart = -1
                }
            } else {
                reversal = 0; reversalStart = -1
            }
        }
        runs.append((runStart, points.count - 1))
        guard runs.count >= 2 else { return points }
        let best = runs.max { abs(axis(points[$0.1]) - axis(points[$0.0]))
                            < abs(axis(points[$1.1]) - axis(points[$1.0])) }!
        let bestSpan = abs(axis(points[best.1]) - axis(points[best.0]))
        guard bestSpan >= span * 0.8 else { return points }
        return Array(points[best.0...best.1])
    }

    /// Aplica el iman SOLO si esta activado -- espejo de TopoMagnet.apply.
    ///
    /// El interruptor existe porque el iman acertado no siempre es el deseado:
    /// dos vias pueden pasar muy cerca al arrancar sin compartir nada y
    /// juntarse solo a media pared. Quien decide eso es quien mira la roca.
    static func applyMagnet(_ stroke: [CGPoint], others: [[CGPoint]],
                            threshold: CGFloat = 0.04, enabled: Bool) -> [CGPoint] {
        enabled ? magnetizeStroke(stroke, others: others, threshold: threshold) : stroke
    }

    /// Anade UN punto al final, imantando SOLO ese punto -- espejo de
    /// TopoMagnet.appendPoint. Lo ya colocado no se toca: encender el iman a
    /// mitad de una via no debe unir de golpe el arranque que dejaste suelto.
    static func appendPoint(_ line: [CGPoint], _ point: CGPoint, others: [[CGPoint]],
                            threshold: CGFloat = 0.04, enabled: Bool) -> [CGPoint] {
        guard enabled, !others.isEmpty else { return line + [point] }
        guard let ultimo = line.last else {
            return magnetizeStroke([point], others: others, threshold: threshold)
        }
        // Se imanta el tramo "ultimo punto -> nuevo" y se descarta el primero:
        // ese ya estaba puesto y no se discute.
        let cola = magnetizeStroke([ultimo, point], others: others, threshold: threshold)
        return line + cola.dropFirst()
    }

    /// Indices de `line` que han quedado UNIDOS: los que caen exactamente sobre
    /// un vertice de otra via -- espejo de TopoMagnet.joinedIndices. Se marcan
    /// en el editor para no tener que adivinar si el iman engancho.
    static func joinedIndices(_ line: [CGPoint], others: [[CGPoint]]) -> Set<Int> {
        guard !line.isEmpty, !others.isEmpty else { return [] }
        var vertices = Set<String>()
        for pts in others { for p in pts { vertices.insert(pointKey(p)) } }
        return Set(line.indices.filter { vertices.contains(pointKey(line[$0])) })
    }

    /// IMÁN del editor: espejo exacto de magnetizeStroke de TopoRenderer.kt.
    /// v3 (2026-08-20, mismo fallo que ya reportó Rodrigo en Android antes de
    /// que existiera este editor en iOS): v2 siempre se pegaba al VÉRTICE más
    /// cercano del tramo, aunque quedara lejos — tocar a mitad de una vía larga
    /// podía mandar el punto a medio muro de distancia. Ahora el vértice solo
    /// gana si lo tienes CASI debajo (radioVertice, más estrecho que el imán);
    /// si estás cerca de la vía pero lejos de sus vértices, el trazo cae en la
    /// PROYECCIÓN — el punto exacto de la vía más cercano a donde tocaste. Se
    /// pierde el tramo compartido exacto en ese caso, pero la línea cae donde
    /// el usuario dijo, que es lo que importa.
    static func magnetizeStroke(_ drawn: [CGPoint], others: [[CGPoint]],
                                threshold: CGFloat = 0.04) -> [CGPoint] {
        guard !drawn.isEmpty, !others.isEmpty else { return drawn }
        func proyeccion(_ p: CGPoint) -> CGPoint? {
            var mejor: CGPoint? = nil
            var mejorD = threshold * threshold
            for pts in others {
                guard pts.count > 1 else { continue }
                for si in 0..<(pts.count - 1) {
                    let a = pts[si], b = pts[si + 1]
                    let abx = b.x - a.x, aby = b.y - a.y
                    let len2 = abx * abx + aby * aby
                    let t = len2 < 1e-12 ? 0
                        : max(0, min(1, ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2))
                    let qx = a.x + t * abx, qy = a.y + t * aby
                    let dx = p.x - qx, dy = p.y - qy
                    let d = dx * dx + dy * dy
                    if d < mejorD { mejorD = d; mejor = CGPoint(x: qx, y: qy) }
                }
            }
            return mejor
        }
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
        // Radio para pegarse a un VÉRTICE: más estrecho que el del imán. El
        // vértice solo gana si lo tienes casi debajo; si no, gana la proyección.
        let radioVertice = threshold * 0.45
        let nodes = drawn.map { p -> Node in
            guard let s = snap(p) else { return Node(point: p, snapped: nil) }
            let v = others[s.li][s.vi]
            let dx = p.x - v.x, dy = p.y - v.y
            let cerca = dx * dx + dy * dy <= radioVertice * radioVertice
            if cerca { return Node(point: v, snapped: s) }
            return Node(point: proyeccion(p) ?? p, snapped: nil)
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

// MARK: - Acierto de vía/vértice (puente directo al módulo compartido)

extension TopoShared {

    /// [CGPoint] → la lista de pares que espera el módulo compartido.
    static func kpoints(_ pts: [CGPoint]) -> [KotlinPair<KotlinFloat, KotlinFloat>] {
        pts.map { KotlinPair(first: KotlinFloat(float: Float($0.x)),
                             second: KotlinFloat(float: Float($0.y))) }
    }

    /// Qué vía has tocado. **No se reimplementa aquí**: se llama al código
    /// compartido, que es el que tiene los tests. Lo de arriba en este fichero
    /// son espejos escritos a mano por historia; lo nuevo entra por el puente.
    static func nearestLineIndex(_ lines: [[CGPoint]], x: CGFloat, y: CGFloat,
                                 maxDistance: Float = 0.05) -> Int? {
        TopoHitTestKt.nearestLineIndex(lines: lines.map { kpoints($0) },
                                       px: Float(x), py: Float(y),
                                       maxDistance: maxDistance)?.intValue
    }

    /// Qué vértice has agarrado para corregirlo (radio más estrecho: agarrar
    /// uno sin querer cambia una vía que dabas por buena).
    static func nearestVertexIndex(_ points: [CGPoint], x: CGFloat, y: CGFloat,
                                   maxDistance: Float = 0.03) -> Int? {
        TopoHitTestKt.nearestVertexIndex(points: kpoints(points),
                                         px: Float(x), py: Float(y),
                                         maxDistance: maxDistance)?.intValue
    }
}
