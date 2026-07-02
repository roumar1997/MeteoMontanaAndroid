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

// Etiquetas por eje, para los selectores DEDOS × ESTILO (espejo de
// GripsViewModel.kt de Android).
func fingerGroupLabel(_ fingerGroup: String) -> String {
    switch fingerGroup {
    case "FIVE": return "5 dedos"
    case "FOUR": return "4 dedos"
    case "THREE": return "3 dedos"
    case "FRONT_TWO": return "2 frontales"
    case "MID_TWO": return "2 centrales"
    default: return fingerGroup
    }
}

func gripStyleLabel(_ style: String) -> String {
    switch style {
    case "CRIMP": return "Arqueo"
    case "HALF_CRIMP": return "Semi-arqueo"
    case "DRAG": return "Extensión"
    default: return style
    }
}

let fingerGroupOrder = ["FIVE", "FOUR", "THREE", "FRONT_TWO", "MID_TWO"]
let gripStyleOrder = ["CRIMP", "HALF_CRIMP", "DRAG"]

/// Selector de agarre en DOS ejes (DEDOS × ESTILO) — espejo de
/// GripTypeTwoAxisSelector de Android (GripUiComponents.kt).
struct GripTypeTwoAxisSelector: View {
    let gripTypes: [GripType]
    let selected: GripType?
    var enabled: Bool = true
    let onSelect: (GripType) -> Void

    var body: some View {
        let currentFinger = selected?.fingerGroup ?? fingerGroupOrder[0]
        let currentStyle = selected?.style ?? gripStyleOrder[0]

        VStack(alignment: .leading, spacing: 4) {
            Text("DEDOS").eyebrow()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(fingerGroupOrder.filter { f in gripTypes.contains { $0.fingerGroup == f } }, id: \.self) { f in
                        gripChip(fingerGroupLabel(f), selected: f == currentFinger) {
                            pick(finger: f, style: currentStyle)
                        }
                    }
                }
            }
            Text("ESTILO").eyebrow().padding(.top, 10)
            HStack(spacing: 6) {
                ForEach(gripStyleOrder.filter { s in gripTypes.contains { $0.style == s } }, id: \.self) { s in
                    gripChip(gripStyleLabel(s), selected: s == currentStyle, expand: true) {
                        pick(finger: currentFinger, style: s)
                    }
                }
            }
        }
    }

    private func pick(finger: String, style: String) {
        if let g = gripTypes.first(where: { $0.fingerGroup == finger && $0.style == style }) {
            onSelect(g)
        }
    }

    private func gripChip(_ label: String, selected isSel: Bool, expand: Bool = false, action: @escaping () -> Void) -> some View {
        Text(label)
            .font(.system(size: 13, weight: .semibold))
            .lineLimit(1)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: expand ? .infinity : nil)
            .background(isSel ? Cumbre.terra : Cumbre.paper)
            .foregroundStyle(isSel ? .white : Cumbre.ink)
            .overlay(Rectangle().stroke(isSel ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
            .onTapGesture { if enabled { action() } }
    }
}

/// Selector IZQUIERDA / DERECHA grande y claro.
struct HandSelectorView: View {
    let hand: String
    var enabled: Bool = true
    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: 6) {
            ForEach([("LEFT", "IZQUIERDA"), ("RIGHT", "DERECHA")], id: \.0) { value, label in
                let isSel = hand == value
                Text(label)
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(isSel ? Cumbre.terra : Cumbre.paper)
                    .foregroundStyle(isSel ? .white : Cumbre.ink)
                    .overlay(Rectangle().stroke(isSel ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    .onTapGesture { if enabled { onSelect(value) } }
            }
        }
    }
}

/// Tabla de máximos: una fila por agarre medido, columnas IZQ / DER — la
/// mano más fuerte de cada agarre en terracota.
struct GripMaxesTableView: View {
    let gripTypes: [GripType]
    let maxes: [GripMaxRecord]

    var body: some View {
        let byGrip = Dictionary(grouping: maxes, by: { $0.gripTypeId })
        let ordered = gripTypes
            .sorted {
                let a = (fingerGroupOrder.firstIndex(of: $0.fingerGroup) ?? 99, gripStyleOrder.firstIndex(of: $0.style) ?? 99)
                let b = (fingerGroupOrder.firstIndex(of: $1.fingerGroup) ?? 99, gripStyleOrder.firstIndex(of: $1.style) ?? 99)
                return a < b
            }
            .filter { byGrip[$0.id] != nil }

        VStack(spacing: 0) {
            HStack {
                Text("AGARRE").eyebrow()
                Spacer()
                Text("IZQ").eyebrow().frame(width: 60, alignment: .trailing)
                Text("DER").eyebrow().frame(width: 60, alignment: .trailing)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            Divider().overlay(Cumbre.rule)

            ForEach(Array(ordered.enumerated()), id: \.element.id) { idx, gripType in
                let records = byGrip[gripType.id] ?? []
                let left = records.first { $0.hand == "LEFT" }?.maxKg
                let right = records.first { $0.hand == "RIGHT" }?.maxKg
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(fingerGroupLabel(gripType.fingerGroup))
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        Text(gripStyleLabel(gripType.style))
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                    Spacer()
                    maxKgCell(left, strongest: left != nil && (right == nil || left! >= right!))
                    maxKgCell(right, strongest: right != nil && (left == nil || right! >= left!))
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(idx % 2 == 1 ? Cumbre.bg : Color.clear)
                if idx < ordered.count - 1 {
                    Divider().overlay(Cumbre.rule.opacity(0.5))
                }
            }
        }
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private func maxKgCell(_ kg: Double?, strongest: Bool) -> some View {
        VStack(alignment: .trailing, spacing: 0) {
            Text(kg.map { String(format: "%.1f", $0) } ?? "—")
                .font(Cumbre.mono(16, .bold))
                .foregroundStyle(kg == nil ? Cumbre.ink3 : (strongest ? Cumbre.terra : Cumbre.ink))
            if kg != nil {
                Text("kg").font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: 60, alignment: .trailing)
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
