import SwiftUI
import Shared

/// Etiqueta legible para un GripType, p.ej. "4 dedos · Semi-arqueo".
/// Espejo de GripType.label() en GripsViewModel.kt (Android).
func gripTypeLabel(_ t: GripType) -> String {
    let fingers: String
    switch t.fingerGroup {
    case "FIVE": fingers = "5 dedos"
    case "FOUR": fingers = "4 dedos"
    case "THREE": fingers = "3 dedos"
    case "FRONT_TWO": fingers = "2 dedos frontales"
    case "MID_TWO": fingers = "2 dedos centrales"
    default: fingers = t.fingerGroup
    }
    let style: String
    switch t.style {
    case "CRIMP": style = "Arqueo"
    case "HALF_CRIMP": style = "Semi-arqueo"
    case "DRAG": style = "Extensión"
    default: style = t.style
    }
    return "\(fingers) · \(style)"
}

func handModeLabel(_ mode: String) -> String {
    switch mode {
    case "UNA": return "Una mano"
    case "POR_SERIE": return "Alterna por serie"
    case "POR_REP": return "Alterna por rep"
    default: return mode
    }
}

/// Un punto de la gráfica: fuerza (kg) + de qué mano es (para colorearlo
/// según la mano activa — ver GRIPS_DESIGN.md sección 4.4).
struct GripChartPoint {
    let kg: Double
    let hand: String? // "LEFT" | "RIGHT" | nil
}

/// Gráfica de líneas simple dibujada a mano con Canvas (sin librería externa,
/// espejo de GripLineChart.kt de Android).
struct GripLineChartView: View {
    let points: [GripChartPoint]
    var targetMin: Double? = nil
    var targetMax: Double? = nil

    private let leftColor = Color(hex: 0xB5654A)
    private let rightColor = Color(hex: 0x3A6B8A)

    var body: some View {
        Canvas { context, size in
            let maxKg = max(points.map { $0.kg }.max() ?? 1, targetMax ?? 0) * 1.15
            let safeMax = maxKg <= 0 ? 1 : maxKg
            let w = size.width
            let h = size.height

            func y(for kg: Double) -> Double { h - (kg / safeMax) * h }

            if let tMin = targetMin, let tMax = targetMax {
                let rect = CGRect(x: 0, y: y(for: tMax), width: w, height: y(for: tMin) - y(for: tMax))
                context.fill(Path(rect), with: .color(Cumbre.terra.opacity(0.12)))
            }

            for frac in [0.25, 0.5, 0.75, 1.0] {
                let gy = h - h * frac
                var path = Path()
                path.move(to: CGPoint(x: 0, y: gy))
                path.addLine(to: CGPoint(x: w, y: gy))
                context.stroke(path, with: .color(Cumbre.rule.opacity(0.3)), lineWidth: 1)
            }

            guard points.count >= 2 else { return }
            let stepX = w / Double(max(points.count - 1, 1))
            for i in 0..<(points.count - 1) {
                let p0 = points[i]
                let p1 = points[i + 1]
                let color: Color = p1.hand == "LEFT" ? leftColor : (p1.hand == "RIGHT" ? rightColor : Cumbre.terra)
                var path = Path()
                path.move(to: CGPoint(x: Double(i) * stepX, y: y(for: p0.kg)))
                path.addLine(to: CGPoint(x: Double(i + 1) * stepX, y: y(for: p1.kg)))
                context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 4, lineCap: .round))
            }
        }
        .frame(height: 180)
        .background(Cumbre.paper)
    }
}
