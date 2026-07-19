import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Editor de lineas sobre la foto (topo). Reparto de ProposeFlow.swift.

struct TopoEditorView: View {
    var photo: UIImage? = nil
    var photoUrl: String? = nil               // alternativa: cargar foto remota
    /// Vías existentes que NO cambian: se ven NORMALES (sólidas con número/tipo).
    var normalLines: [TopoLineVM] = []
    /// Vías difuminadas: SOLO la versión vieja de la que se corrige.
    var fadedLines: [TopoLineVM] = []
    @Binding var blocks: [BoulderBlockForm]
    @Environment(\.dismiss) private var dismiss
    @State private var selected = 0
    @State private var loaded: UIImage?
    @State private var drawingActive = false   // ¿estamos en mitad de un trazo?
    // Línea previa al gesto en curso: si el gesto acaba siendo un TOQUE (modo
    // por puntos), se restaura y se le añade solo el punto tocado.
    @State private var lineBeforeStroke: [CGPoint] = []

    private var image: UIImage? { photo ?? loaded }
    private var ratio: CGFloat {
        guard let img = image else { return 4.0 / 3.0 }
        let w = img.size.width, h = img.size.height
        return (w > 0 && h > 0) ? min(max(w / h, 0.55), 2.2) : 4.0 / 3.0
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                // Selector de bloque (chips por grado).
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(blocks.enumerated()), id: \.element.id) { idx, b in
                            let on = idx == selected
                            Button { selected = idx } label: {
                                Text("\(idx + 1)\(b.grade.map { " · \($0)" } ?? "")")
                                    .font(Cumbre.mono(11, .bold))
                                    .foregroundStyle(on ? .white : Cumbre.ink2)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(on ? GradeColor.color(b.grade) : Color.clear)
                                    .overlay(Rectangle().stroke(GradeColor.color(b.grade), lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 12)
                }

                GeometryReader { geo in
                    ZStack {
                        Color.black
                        if let img = image {
                            Image(uiImage: img).resizable().scaledToFill()
                        } else { ProgressView().tint(.white) }
                        Canvas { ctx, size in drawLines(ctx, size) }
                    }
                    .frame(width: geo.size.width, height: geo.size.width / ratio)
                    .clipped()
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                        let h = geo.size.width / ratio
                        let nx = max(0, min(1, v.location.x / geo.size.width))
                        let ny = max(0, min(1, v.location.y / h))
                        guard blocks.indices.contains(selected) else { return }
                        // Un ARRASTRE traza una línea nueva de cero; un TOQUE
                        // suelto (se decide en onEnded) añade un punto a la
                        // línea actual. Se guarda la línea previa para poder
                        // restaurarla si al final resulta ser un toque.
                        if !drawingActive {
                            drawingActive = true
                            lineBeforeStroke = blocks[selected].line
                            blocks[selected].line = []
                        }
                        blocks[selected].line.append(CGPoint(x: nx, y: ny))
                    }.onEnded { _ in
                        drawingActive = false
                        guard blocks.indices.contains(selected) else { return }
                        let stroke = blocks[selected].line
                        // Otras vías de la cara (existentes + del editor) para el imán.
                        let others = (normalLines.map { $0.points } +
                                      blocks.enumerated()
                                        .filter { $0.offset != selected }
                                        .map { $0.element.line })
                            .filter { $0.count >= 2 }
                        // ¿Fue un TOQUE? (sin apenas movimiento) → añade UN punto
                        // a la línea que ya había (modo por toques).
                        let isTap = stroke.count <= 2 || (
                            stroke.count < 12 && zip(stroke, stroke.dropFirst()).allSatisfy {
                                abs($0.0.x - $0.1.x) < 0.004 && abs($0.0.y - $0.1.y) < 0.004
                            })
                        if isTap, let tap = stroke.last {
                            var line = lineBeforeStroke
                            line.append(tap)
                            blocks[selected].line = others.isEmpty ? line
                                : TopoShared.magnetizeStroke(line, others: others)
                        } else if stroke.count >= 2 {
                            // TRAZO a mano: 1) SUAVIZADO (fuera el temblor del
                            // pulso) y 2) IMÁN a las vías cercanas.
                            let smooth = TopoShared.simplifyStroke(stroke)
                            blocks[selected].line = others.isEmpty ? smooth
                                : TopoShared.magnetizeStroke(smooth, others: others)
                        }
                        lineBeforeStroke = []
                    })
                }
                .aspectRatio(ratio, contentMode: .fit)
                .padding(.horizontal, 12)

                Text("Toca punto a punto para colocar la línea, o arrastra para trazarla a mano. Cerca de otra vía, el trazo se pega a ella (tramo compartido).")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).padding(.horizontal, 16)
                Spacer()

                HStack(spacing: 10) {
                    Button("✕ BORRAR") {
                        if blocks.indices.contains(selected) { blocks[selected].line = [] }
                    }
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.bad)
                    .padding(.vertical, 12).frame(maxWidth: .infinity)
                    .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                    Button(NSLocalizedString("propose_save_lines", comment: "")) { dismiss() }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                        .padding(.vertical, 12).frame(maxWidth: .infinity).background(Cumbre.ink)
                }
                .buttonStyle(.plain).padding(.horizontal, 12).padding(.bottom, 12)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Dibujar líneas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task {
            if photo == nil, let photoUrl, let url = URL(string: photoUrl),
               let (data, _) = try? await URLSession.shared.data(from: url) {
                loaded = UIImage(data: data)
            }
        }
    }

    private func drawLines(_ ctx: GraphicsContext, _ size: CGSize) {
        // Tramos compartidos (existentes + en edición) → FRANJAS por vía.
        // El índice de vía es GLOBAL (normales primero, luego las editables)
        // para que las fases de las franjas y el abanico casen.
        let allPoints = normalLines.map { $0.points } + blocks.map { $0.line }
        let sharedKeys = TopoShared.sharedSegmentLines(allPoints)
        let startFan = TopoShared.fanOffsets(allPoints.map { $0.first }, spacing: 12 * 2 + 4)
        let endFan = TopoShared.fanOffsets(allPoints.map { $0.last }, spacing: 13 * 2 + 4)
        // 1. Existentes que NO cambian → NORMALES (con número y tipo).
        // (los badges van en una 2ª pasada al final, siempre encima)
        for (i, line) in normalLines.enumerated() where !line.points.isEmpty {
            drawTopoLine(ctx, size, points: line.points, grade: line.grade,
                         startType: line.startType, number: i + 1, lineWidth: 5,
                         lineIdx: i, shared: sharedKeys,
                         startDx: startFan[i], endDx: endFan[i], strokesOnly: true)
        }
        // 2. SOLO la vía vieja que se corrige, difuminada (para distinguirla).
        for line in fadedLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.4)),
                       style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round, dash: [6, 6]))
        }
        // 3. Las que se están dibujando (editable), resaltadas.
        for (idx, b) in blocks.enumerated() where !b.line.isEmpty {
            let g = normalLines.count + idx
            drawTopoLine(ctx, size, points: b.line, grade: b.grade, startType: b.startType,
                         number: idx + 1, lineWidth: idx == selected ? 8 : 5,
                         lineIdx: g, shared: sharedKeys,
                         startDx: startFan[g], endDx: endFan[g], strokesOnly: true)
        }
        // 4. BADGES de todas, encima de todas las líneas.
        for (i, line) in normalLines.enumerated() where !line.points.isEmpty {
            drawTopoLine(ctx, size, points: line.points, grade: line.grade,
                         startType: line.startType, number: i + 1, lineWidth: 5,
                         lineIdx: i, shared: sharedKeys,
                         startDx: startFan[i], endDx: endFan[i], badgesOnly: true)
        }
        for (idx, b) in blocks.enumerated() where !b.line.isEmpty {
            let g = normalLines.count + idx
            drawTopoLine(ctx, size, points: b.line, grade: b.grade, startType: b.startType,
                         number: idx + 1, lineWidth: idx == selected ? 8 : 5,
                         lineIdx: g, shared: sharedKeys,
                         startDx: startFan[g], endDx: endFan[g], badgesOnly: true)
        }
    }

    private func drawTopoLine(_ ctx: GraphicsContext, _ size: CGSize, points: [CGPoint],
                              grade: String?, startType: String?, number: Int, lineWidth: CGFloat,
                              lineIdx: Int = 0, shared: [String: [Int]] = [:],
                              startDx: CGFloat = 0, endDx: CGFloat = 0,
                              strokesOnly: Bool = false, badgesOnly: Bool = false) {
        let style = GradeColor.style(grade)
        var pts = points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
        guard !pts.isEmpty else { return }
        // Rachas propias (color de grado) / compartidas (FRANJAS por vía).
        if !badgesOnly {
        for run in TopoShared.splitRuns(points, shared: shared) {
            let runPts = run.pts.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            guard runPts.count > 1 else { continue }
            var path = Path(); path.move(to: runPts[0])
            for p in runPts.dropFirst() { path.addLine(to: p) }
            if let stripe = TopoShared.stripeStyle(run, lineIdx: lineIdx) {
                ctx.stroke(path, with: .color(style.stroke),
                           style: StrokeStyle(lineWidth: lineWidth, lineCap: .butt, lineJoin: .round,
                                              dash: stripe.dash, dashPhase: stripe.phase))
            } else {
                // ESTILO GUÍA: discontinua siempre (no tapa la roca).
                if style.dark {
                    ctx.stroke(path, with: .color(.black.opacity(0.8)),
                               style: StrokeStyle(lineWidth: lineWidth + 4, lineCap: .round, lineJoin: .round,
                                                  dash: TopoShared.dash))
                }
                ctx.stroke(path, with: .color(style.stroke),
                           style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round,
                                              dash: TopoShared.dash))
            }
        }
        }
        if strokesOnly { return }
        // Abanico: badges coincidentes separados en X.
        if !pts.isEmpty {
            pts[0] = CGPoint(x: pts[0].x + startDx, y: pts[0].y)
            if pts.count > 1 { pts[pts.count - 1] = CGPoint(x: pts[pts.count - 1].x + endDx, y: pts[pts.count - 1].y) }
        }
        // Badge número en el inicio.
        ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 12, y: pts[0].y - 12, width: 24, height: 24)), with: .color(.white))
        ctx.fill(Path(ellipseIn: CGRect(x: pts[0].x - 9, y: pts[0].y - 9, width: 18, height: 18)), with: .color(style.stroke))
        ctx.draw(Text("\(number)").font(.system(size: 12, weight: .bold))
            .foregroundColor(style.dark ? .black : .white), at: pts[0], anchor: .center)
        // Badge de tipo de inicio (PIE/SIT/LAN/TRV) al final.
        if let label = startLabelShort(startType), let end = pts.last {
            ctx.fill(Path(ellipseIn: CGRect(x: end.x - 13, y: end.y - 13, width: 26, height: 26)),
                     with: .color(style.dark ? .black : .white))
            ctx.fill(Path(ellipseIn: CGRect(x: end.x - 10.5, y: end.y - 10.5, width: 21, height: 21)),
                     with: .color(style.stroke))
            ctx.draw(Text(label).font(.system(size: 9, weight: .bold))
                .foregroundColor(style.dark ? .black : .white), at: end, anchor: .center)
        }
    }

    private func startLabelShort(_ t: String?) -> String? {
        switch t?.uppercased() {
        case "PIE", "STAND": return "PIE"
        case "SIT": return "SIT"
        case "SEMI": return "SEM"
        case "LANCE", "JUMP": return "LAN"
        case "TRAV": return "TRV"
        default: return nil
        }
    }
}
