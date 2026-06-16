import SwiftUI

/// Icono meteorológico SVG line-art — equivalente exacto a `WmoWeatherIcon.kt`
/// de Android (que a su vez copia `wmoSvg()` de la PWA). Mismos path strings,
/// viewport 24×24, stroke 1.4 con cap/join redondos.
struct WmoIcon: View {
    let code: Int
    var size: CGFloat = 20
    var tint: Color = Cumbre.ink2

    var body: some View {
        Canvas { ctx, canvasSize in
            let s = canvasSize.width / 24.0
            var combined = Path()
            for d in wmoPaths(code) {
                combined.addPath(SVGPath.parse(d, scale: s))
            }
            ctx.stroke(
                combined,
                with: .color(tint),
                style: StrokeStyle(lineWidth: 1.4 * s, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: size, height: size)
    }
}

/// Path strings por categoría WMO — copia literal de wmoSvgPaths() de Android.
private func wmoPaths(_ code: Int) -> [String] {
    switch code {
    case 0:
        return [
            "M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z",
            "M12 2 L12 5 M12 19 L12 22 M2 12 L5 12 M19 12 L22 12 " +
            "M5.05 5.05 L7.17 7.17 M16.83 16.83 L18.95 18.95 " +
            "M5.05 18.95 L7.17 16.83 M16.83 7.17 L18.95 5.05",
        ]
    case 1, 2, 3:
        return [
            "M9 6 A3 3 0 1 0 9 12 A3 3 0 1 0 9 6 Z",
            "M15 18 A4 4 0 0 0 15 10 A5 5 0 0 0 5.5 12 A4 4 0 0 0 5.5 18 Z",
        ]
    case 45, 48:
        return ["M3 8 L21 8 M3 12 L17 12 M3 16 L21 16 M3 20 L15 20"]
    case 51...67:
        return [
            "M15 18 A4 4 0 0 0 15 10 A5 5 0 0 0 5.5 12 A4 4 0 0 0 5.5 18 Z",
            "M8 20 L7 22 M12 20 L11 22 M16 20 L15 22",
        ]
    case 71...77:
        return [
            "M15 16 A4 4 0 0 0 15 8 A5 5 0 0 0 5.5 10 A4 4 0 0 0 5.5 16 Z",
            "M8 19 L8.5 20 M12 19 L12.5 20 M16 19 L16.5 20",
        ]
    case 80...82:
        return [
            "M15 16 A4 4 0 0 0 15 8 A5 5 0 0 0 5.5 10 A4 4 0 0 0 5.5 16 Z",
            "M7 19 L8 21 M11 19 L12 21 M15 19 L16 21",
        ]
    default:  // 95-99 tormenta y resto
        return [
            "M15 14 A4 4 0 0 0 15 6 A5 5 0 0 0 5.5 8 A4 4 0 0 0 5.5 14 Z",
            "M11 14 L9 18 L12 18 L10 22",
        ]
    }
}

/// Mini-parser de path SVG: comandos absolutos M, L, A, Z. Escala 24→tamaño.
/// Los arcos (rx==ry, círculos) se muestrean como segmentos para evitar
/// ambigüedades de dirección de Path.addArc en coordenadas y-abajo.
enum SVGPath {
    static func parse(_ d: String, scale: CGFloat) -> Path {
        var path = Path()
        let tokens = tokenize(d)
        var i = 0
        var current = CGPoint.zero
        func nextNum() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i]) ?? 0) }

        while i < tokens.count {
            let t = tokens[i]
            switch t {
            case "M":
                i += 1
                let x = nextNum() * scale, y = nextNum() * scale
                current = CGPoint(x: x, y: y); path.move(to: current)
                // pares implícitos => L
                while i + 1 < tokens.count, Double(tokens[i]) != nil {
                    let lx = nextNum() * scale, ly = nextNum() * scale
                    current = CGPoint(x: lx, y: ly); path.addLine(to: current)
                }
            case "L":
                i += 1
                while i + 1 < tokens.count, Double(tokens[i]) != nil {
                    let lx = nextNum() * scale, ly = nextNum() * scale
                    current = CGPoint(x: lx, y: ly); path.addLine(to: current)
                }
            case "A":
                i += 1
                while i + 6 < tokens.count, Double(tokens[i]) != nil {
                    let rx = nextNum() * scale
                    _ = nextNum() // ry (== rx en nuestros iconos)
                    _ = nextNum() // x-axis-rotation (0)
                    let largeArc = nextNum() != 0
                    let sweep = nextNum() != 0
                    let ex = nextNum() * scale, ey = nextNum() * scale
                    appendArc(&path, from: current, to: CGPoint(x: ex, y: ey),
                              r: rx, largeArc: largeArc, sweep: sweep)
                    current = CGPoint(x: ex, y: ey)
                }
            case "Z", "z":
                path.closeSubpath(); i += 1
            default:
                i += 1  // separadores / desconocido
            }
        }
        return path
    }

    /// Arco circular SVG (endpoint) → muestreo de segmentos.
    private static func appendArc(_ path: inout Path, from p1: CGPoint, to p2: CGPoint,
                                  r: CGFloat, largeArc: Bool, sweep: Bool) {
        let dx = (p1.x - p2.x) / 2, dy = (p1.y - p2.y) / 2
        var rr = r
        // Asegura radio suficiente.
        let d2 = (dx * dx + dy * dy)
        if rr * rr < d2 { rr = sqrt(d2) }
        let num = max(0, rr * rr - d2)
        let den = d2
        let coef = (den > 0 ? sqrt(num / den) : 0) * (largeArc != sweep ? 1 : -1)
        let cxp = coef * dy
        let cyp = coef * -dx
        let cx = cxp + (p1.x + p2.x) / 2
        let cy = cyp + (p1.y + p2.y) / 2
        let a1 = atan2(p1.y - cy, p1.x - cx)
        var a2 = atan2(p2.y - cy, p2.x - cx)
        if sweep, a2 < a1 { a2 += 2 * .pi }
        if !sweep, a2 > a1 { a2 -= 2 * .pi }
        let steps = max(6, Int(abs(a2 - a1) / (.pi / 24)))
        for s in 1...steps {
            let a = a1 + (a2 - a1) * CGFloat(s) / CGFloat(steps)
            path.addLine(to: CGPoint(x: cx + rr * cos(a), y: cy + rr * sin(a)))
        }
    }

    private static func tokenize(_ d: String) -> [String] {
        var out: [String] = []
        var num = ""
        for ch in d {
            if ch == "M" || ch == "L" || ch == "A" || ch == "Z" || ch == "z" {
                if !num.isEmpty { out.append(num); num = "" }
                out.append(String(ch))
            } else if ch == " " || ch == "," {
                if !num.isEmpty { out.append(num); num = "" }
            } else if ch == "-" {
                if !num.isEmpty { out.append(num) }
                num = "-"
            } else {
                num.append(ch)
            }
        }
        if !num.isEmpty { out.append(num) }
        return out
    }
}
