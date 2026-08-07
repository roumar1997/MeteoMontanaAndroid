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
    /// Vértice agarrado para corregirlo. Sin esto, arreglar un punto torcido
    /// obliga a volver a trazar la vía entera.
    @State private var draggingVertex: Int?
    /// Historial para DESHACER: (vía, cómo estaba). Se apila antes de cada
    /// cambio. Sin esto, mover una vía sin querer al revisar la propuesta de
    /// otro no tiene vuelta atrás.
    @State private var historial: [(Int, [CGPoint])] = []
    /// Factor de la ampliación actual (1 = foto entera, 0,25 = ampliada ×4). De
    /// aquí salen el radio del imán y el paso mínimo del trazo, para que los dos
    /// midan siempre el mismo trozo de PANTALLA.
    @State private var zoom: CGFloat = 1
    /// ¿El dedo está trazando ahora mismo?
    @State private var trazando = false

    /// Devuelve la vía a como estaba antes del último cambio.
    private func deshacer() {
        guard let (idx, antes) = historial.popLast(),
              blocks.indices.contains(idx) else { return }
        blocks[idx].line = antes
        selected = idx
    }

    /// Vías con las que el trazo puede compartir tramo: las que ya existen en
    /// la cara y las demás que se están dibujando ahora.
    private func otherLines() -> [[CGPoint]] {
        (normalLines.map { $0.points } +
         blocks.enumerated().filter { $0.offset != selected }.map { $0.element.line })
            .filter { $0.count >= 2 }
    }

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
                    // Los gestos y el zoom viven en TopoZoomLayer (espejo de
                    // TopoZoomBox de Android, con la misma TopoCamera del
                    // módulo compartido). Aquí solo queda lo que SIGNIFICA
                    // cada gesto para el editor.
                    TopoZoomLayer(
                        editable: true,
                        onStrokeStart: { nx, ny in
                            guard blocks.indices.contains(selected) else { return }
                            // Copia de seguridad: si el gesto acaba siendo un
                            // pellizco o un toque, se restaura lo que había.
                            lineBeforeStroke = blocks[selected].line
                            trazando = true
                            historial.append((selected, blocks[selected].line))
                            if historial.count > 40 { historial.removeFirst() }
                            // Si el dedo cae sobre un vértice ya trazado, se
                            // AGARRA ese punto para corregirlo. Solo con la vía
                            // ya hecha: con dos puntos aún se está dibujando.
                            let actual = blocks[selected].line
                            let v: Int? = actual.count >= 3
                                ? TopoShared.nearestVertexIndex(actual, x: nx, y: ny)
                                : nil
                            draggingVertex = v
                            if let v {
                                blocks[selected].line[v] = CGPoint(x: nx, y: ny)
                            } else {
                                blocks[selected].line = [CGPoint(x: nx, y: ny)]
                            }
                        },
                        onStrokePoint: { nx, ny in
                            guard blocks.indices.contains(selected) else { return }
                            if let v = draggingVertex, blocks[selected].line.indices.contains(v) {
                                blocks[selected].line[v] = CGPoint(x: nx, y: ny)
                            } else {
                                // Solo si el dedo se ha movido de verdad: con el
                                // dedo casi quieto llegaban decenas de puntos
                                // idénticos por segundo y el limpiador de pasadas
                                // superpuestas los tomaba por un retrazado,
                                // dejando la línea sin pintar hasta soltar.
                                let ult = blocks[selected].line.last
                                // El paso mínimo también se divide por la
                                // ampliación: con un valor fijo, ampliado ×4 se
                                // tragaba cuatro veces más movimiento y el trazo
                                // salía a trompicones.
                                let paso = 0.003 * zoom
                                let lejos = ult == nil ||
                                    abs(nx - ult!.x) + abs(ny - ult!.y) > paso
                                if lejos { blocks[selected].line.append(CGPoint(x: nx, y: ny)) }
                            }
                        },
                        onStrokeEnd: {
                            trazando = false
                            guard blocks.indices.contains(selected) else { return }
                            let corrigiendo = draggingVertex != nil
                            draggingVertex = nil
                            let stroke = blocks[selected].line
                            guard stroke.count >= 2 else { return }
                            // Corrigiendo un vértice NO se suaviza: el suavizado
                            // quita puntos, y el que acabas de colocar a mano es
                            // justo el que quieres conservar. El imán sí, para
                            // no romper un tramo compartido.
                            let base = corrigiendo ? stroke : TopoShared.simplifyStroke(stroke)
                            let otras = otherLines()
                            blocks[selected].line = otras.isEmpty ? base
                                : TopoShared.magnetizeStroke(base, others: otras,
                                                             threshold: 0.04 * zoom)
                            lineBeforeStroke = []
                        },
                        onStrokeCancel: {
                            trazando = false
                            draggingVertex = nil
                            guard blocks.indices.contains(selected) else { return }
                            blocks[selected].line = lineBeforeStroke
                        },
                        onZoomChange: { zoom = $0 },
                        onTap: { nx, ny in
                            trazando = false
                            guard blocks.indices.contains(selected) else { return }
                            var line = lineBeforeStroke
                            line.append(CGPoint(x: nx, y: ny))
                            let otras = otherLines()
                            blocks[selected].line = otras.isEmpty ? line
                                : TopoShared.magnetizeStroke(line, others: otras,
                                                             threshold: 0.04 * zoom)
                            lineBeforeStroke = []
                        }
                    ) { zoomFactor in
                        ZStack {
                            Color.black
                            if let img = image {
                                Image(uiImage: img).resizable().scaledToFill()
                            } else { ProgressView().tint(.white) }
                            Canvas { ctx, size in drawLines(ctx, size, zoom: zoomFactor) }
                        }
                    }
                    .frame(width: geo.size.width, height: geo.size.width / ratio)
                }
                .aspectRatio(ratio, contentMode: .fit)
                .padding(.horizontal, 12)

                // La lupa es lo único que no se puede juzgar sin el móvil en la
                // mano: a unos les salva y a otros les tapa media foto.
                HStack(spacing: 10) {
                    Button { deshacer() } label: {
                        Text("DESHACER")
                            .font(Cumbre.mono(11, .bold))
                            .foregroundStyle(historial.isEmpty ? Cumbre.ink3 : Cumbre.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .overlay(Rectangle().stroke(
                                historial.isEmpty ? Cumbre.rule : Cumbre.ink, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(historial.isEmpty)
                    Text("Un dedo dibuja · pellizca para ampliar")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16)

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

    private func drawLines(_ ctx: GraphicsContext, _ size: CGSize, zoom: CGFloat = 1) {
        // La vía vieja que se corrige, difuminada (para distinguirla) — al fondo.
        for line in fadedLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.4)),
                       style: StrokeStyle(lineWidth: 4 * zoom, lineCap: .round, lineJoin: .round,
                                          dash: [6 * zoom, 6 * zoom]))
        }
        // Pintor único: índice de vía GLOBAL (normales primero, luego editables)
        // para que las franjas y el abanico casen. La editable seleccionada va
        // más gruesa (lineWidth override). Franjas/abanico/dos pasadas por dentro.
        var vias = normalLines.enumerated().map { (i, l) in
            TopoVia(number: i + 1, grade: l.grade, startType: l.startType, points: l.points)
        }
        for (idx, b) in blocks.enumerated() {
            vias.append(TopoVia(number: idx + 1, grade: b.grade, startType: b.startType,
                                points: b.line, lineWidth: (idx == selected ? 8 : 5) * zoom))
        }
        TopoPainter.paint(GraphicsContextTarget(ctx: ctx), vias: vias, size: size,
                          style: .editor(lineWidth: 5 * zoom))
        // Vértices de la vía seleccionada: si no se ven, nadie adivina que se
        // pueden arrastrar para corregirlos.
        // Igual que en Android: los vértices NO se pintan mientras el dedo
        // traza. En un arrastre son cientos y tapan la línea de color.
        if !trazando, blocks.indices.contains(selected) {
            for p in blocks[selected].line {
                let c = CGPoint(x: p.x * size.width, y: p.y * size.height)
                let r: CGFloat = 5 * zoom
                let caja = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
                ctx.fill(Path(ellipseIn: caja), with: .color(.white))
                ctx.stroke(Path(ellipseIn: caja), with: .color(.black.opacity(0.6)),
                           lineWidth: 1.5 * zoom)
            }
        }
    }

}
