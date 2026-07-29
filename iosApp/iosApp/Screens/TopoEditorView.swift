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
        // La vía vieja que se corrige, difuminada (para distinguirla) — al fondo.
        for line in fadedLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.4)),
                       style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round, dash: [6, 6]))
        }
        // Pintor único: índice de vía GLOBAL (normales primero, luego editables)
        // para que las franjas y el abanico casen. La editable seleccionada va
        // más gruesa (lineWidth override). Franjas/abanico/dos pasadas por dentro.
        var vias = normalLines.enumerated().map { (i, l) in
            TopoVia(number: i + 1, grade: l.grade, startType: l.startType, points: l.points)
        }
        for (idx, b) in blocks.enumerated() {
            vias.append(TopoVia(number: idx + 1, grade: b.grade, startType: b.startType,
                                points: b.line, lineWidth: idx == selected ? 8 : 5))
        }
        TopoPainter.paint(GraphicsContextTarget(ctx: ctx), vias: vias, size: size,
                          style: .editor(lineWidth: 5))
    }

}
