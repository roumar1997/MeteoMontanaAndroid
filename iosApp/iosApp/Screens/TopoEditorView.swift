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
    /// Imán: encendido por defecto (el caso normal es querer unir). Se puede
    /// apagar y volver a encender EN MITAD del dibujo, que es lo que permite
    /// compartir solo el tramo del medio. Se recuerda entre vías.
    @State private var iman = true
    /// Solo la vía seleccionada: apagado por defecto. Con muchas vías en la
    /// misma cara, verlas todas a la vez tapa la que estás dibujando (Álvaro,
    /// 2026-09-03). NO afecta al imán: `otherLines()` sigue viendo todas las
    /// vías para pegarse a ellas aunque no se PINTEN.
    @State private var soloEsta = false

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
                                // Etiqueta compartida: numero (el que se pinta en
                                // la roca) + nombre + variante + grado.
                                Text(TopoChipLabel.shared.of(index: Int32(idx), name: b.name,
                                                             variant: b.variant, grade: b.grade))
                                    .lineLimit(1)
                                    .font(Cumbre.mono(11, .bold))
                                    // Los grados bajos son casi blancos: texto
                                    // negro encima o el nombre desaparece al
                                    // seleccionar el chip. Espejo de Android.
                                    .foregroundStyle(on
                                        ? (GradeColor.style(b.grade).dark ? Color.black : .white)
                                        : Cumbre.ink2)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(on ? GradeColor.color(b.grade) : Color.clear)
                                    .overlay(Rectangle().stroke(GradeColor.color(b.grade), lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 12)
                }

                GeometryReader { geo in
                    // Los gestos y el zoom viven en TopoCanvas (lienzo UIKit,
                    // espejo de TopoZoomBox de Android, con la misma TopoCamera
                    // del módulo compartido). Aquí solo queda lo que SIGNIFICA
                    // cada gesto para el editor.
                    TopoCanvas(
                        image: image,
                        scene: escena,
                        editable: true,
                        onStrokeStart: { nx, ny in
                            guard blocks.indices.contains(selected) else { return [] }
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
                            return blocks[selected].line
                        },
                        onStrokePoint: { nx, ny in
                            guard blocks.indices.contains(selected) else { return [] }
                            if let v = draggingVertex, blocks[selected].line.indices.contains(v) {
                                blocks[selected].line[v] = CGPoint(x: nx, y: ny)
                            } else {
                                // Solo si el dedo se ha movido de verdad: con el
                                // dedo casi quieto llegaban decenas de puntos
                                // idénticos por segundo y el limpiador de pasadas
                                // superpuestas los tomaba por un retrazado.
                                // El paso mínimo se divide por la ampliación: con
                                // un valor fijo, ampliado x4 se tragaba cuatro
                                // veces más movimiento.
                                let ult = blocks[selected].line.last
                                let paso = 0.003 * zoom
                                let lejos = ult == nil ||
                                    abs(nx - ult!.x) + abs(ny - ult!.y) > paso
                                if lejos { blocks[selected].line.append(CGPoint(x: nx, y: ny)) }
                            }
                            return blocks[selected].line
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
                            // justo el que quieres conservar.
                            let base = corrigiendo ? stroke : TopoShared.simplifyStroke(stroke)
                            blocks[selected].line = TopoShared.applyMagnet(
                                base, others: otherLines(), threshold: 0.04 * zoom, enabled: iman)
                            lineBeforeStroke = []
                        },
                        onStrokeCancel: {
                            trazando = false
                            draggingVertex = nil
                            guard blocks.indices.contains(selected) else { return }
                            blocks[selected].line = lineBeforeStroke
                        },
                        onTap: { nx, ny in
                            trazando = false
                            guard blocks.indices.contains(selected) else { return }
                            // Solo se decide sobre el punto NUEVO: encender el
                            // imán no debe unir de golpe lo que ya dibujaste
                            // suelto a propósito.
                            blocks[selected].line = TopoShared.appendPoint(
                                lineBeforeStroke, CGPoint(x: nx, y: ny),
                                others: otherLines(), threshold: 0.04 * zoom, enabled: iman)
                            lineBeforeStroke = []
                        },
                        onZoomChange: { zoom = $0 }
                    )
                    .frame(width: geo.size.width, height: geo.size.width / ratio)
                }
                .aspectRatio(ratio, contentMode: .fit)
                .padding(.horizontal, 12)

                HStack(spacing: 10) {
                    Button { deshacer() } label: {
                        Text("DESHACER")
                            .font(Cumbre.mono(11, .bold))
                            .foregroundStyle(historial.isEmpty ? Cumbre.ink3 : Cumbre.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .overlay(Rectangle().stroke(
                                historial.isEmpty ? Cumbre.rule : Cumbre.ink, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(historial.isEmpty)
                    // UNIR: se puede apagar y encender EN MITAD del dibujo, que
                    // es lo que permite compartir solo el tramo del medio.
                    Button { iman.toggle() } label: {
                        Text(iman ? "UNIR: SÍ" : "UNIR: NO")
                            .font(Cumbre.mono(11, .bold))
                            .foregroundStyle(iman ? .white : Cumbre.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(iman ? Cumbre.terra : Color.clear)
                            .overlay(Rectangle().stroke(iman ? Cumbre.terra : Cumbre.ink, lineWidth: 1))
                    }.buttonStyle(.plain)
                    // SOLO ESTA: oculta el resto de vías mientras dibujas, sin
                    // apagar el imán (sigue pegándose a ellas aunque no se vean).
                    Button { soloEsta.toggle() } label: {
                        Text(soloEsta ? "SOLO ESTA" : "VER TODAS")
                            .font(Cumbre.mono(11, .bold))
                            .foregroundStyle(soloEsta ? .white : Cumbre.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(soloEsta ? Cumbre.terra : Color.clear)
                            .overlay(Rectangle().stroke(soloEsta ? Cumbre.terra : Cumbre.ink, lineWidth: 1))
                    }.buttonStyle(.plain)
                    Text("Un dedo dibuja · pellizca para ampliar")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16)

                Text(iman
                     ? "Toca punto a punto para colocar la línea, o arrastra para trazarla a mano. Cerca de otra vía, el trazo se pega a ella (tramo compartido)."
                     : "Toca punto a punto para colocar la línea, o arrastra para trazarla a mano. Con UNIR en NO, el trazo va libre aunque pases pegado a otra vía.")
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
                        .padding(.vertical, 12).frame(maxWidth: .infinity).background(Cumbre.inkButton)
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

    /// Lo que hay que pintar. El lienzo no sabe nada de vías ni de grados:
    /// recibe datos ya masticados.
    private var escena: TopoScene {
        // "Solo esta" oculta el resto del PINTADO — otherLines() (el imán)
        // sigue viendo todas las vías igual, para no perder el tramo
        // compartido solo por no dibujarlas.
        var vias = soloEsta ? [] : normalLines.enumerated().map { (i, l) in
            TopoVia(number: i + 1, grade: l.grade, startType: l.startType, points: l.points)
        }
        // Índice de vía GLOBAL (normales primero, luego editables) para que las
        // franjas del tramo compartido y el abanico de badges casen.
        for (idx, b) in blocks.enumerated() where !soloEsta || idx == selected {
            vias.append(TopoVia(number: idx + 1, grade: b.grade, startType: b.startType,
                                points: b.line, lineWidth: (idx == selected ? 8 : 5)))
        }
        let seleccionada = blocks.indices.contains(selected) ? blocks[selected].line : []
        return TopoScene(
            faded: soloEsta ? [] : fadedLines.map { ($0.points, $0.grade) },
            vias: vias,
            dots: seleccionada,
            joined: TopoShared.joinedIndices(seleccionada, others: otherLines()),
            liveIndex: soloEsta ? 0 : normalLines.count + selected,
            style: { z in TopoStyle.editor(lineWidth: 5 * z) },
            fadedAlpha: 0.4)
    }

}
