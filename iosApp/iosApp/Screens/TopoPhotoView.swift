import SwiftUI
import Shared

// Foto de una piedra con las vías (líneas topo) dibujadas encima — espejo de
// TopoPhotoCanvas.kt / renderTopo de Android. Las coordenadas de cada vía vienen
// en linePath como JSON normalizado [{"x":0.1,"y":0.2},...] (0..1 sobre la foto).

/// Una vía lista para dibujar.
struct TopoLineVM: Identifiable {
    let id: String
    let name: String?
    let grade: String?
    let startType: String?
    let points: [CGPoint]   // normalizados 0..1
    // Opcionales para el diff de admin (no afectan al dibujo). Default nil para
    // no tocar el resto de call sites.
    var variant: String? = nil
    var desc: String? = nil
}

extension TopoLineVM {
    init(_ l: BlockLine) {
        self.init(id: l.id, name: l.name, grade: l.grade,
                  startType: l.startType, points: TopoParse.points(l.linePath),
                  variant: l.variant, desc: l.lineDescription)
    }
}

enum TopoParse {
    /// Parsea `bloquesJson` (`[{name,grade,startType,linePath}, ...]`, donde
    /// linePath es un string JSON de puntos) a vías dibujables.
    static func lines(_ bloquesJson: String?) -> [TopoLineVM] {
        guard let bloquesJson, let data = bloquesJson.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.enumerated().map { idx, o in
            TopoLineVM(id: "b\(idx)", name: o["name"] as? String, grade: o["grade"] as? String,
                       startType: o["startType"] as? String, points: points(o["linePath"] as? String))
        }
    }

    /// ids de vías que un `bloquesJson` corrige (entradas con `targetLineId`).
    /// Para que el admin distinga, en el editor unificado, qué vías son
    /// correcciones (difuminar su versión vieja) y cuáles son nuevas.
    static func targetLineIds(_ bloquesJson: String?) -> Set<String> {
        guard let bloquesJson, let data = bloquesJson.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return Set(arr.compactMap { $0["targetLineId"] as? String }.filter { !$0.isEmpty })
    }

    /// Una vía propuesta con su cara (photoUrl) y a qué vía corrige (targetLineId).
    struct ProposedVia { let line: TopoLineVM; let photoUrl: String?; let targetLineId: String? }

    /// Parsea `bloquesJson` conservando photoUrl + targetLineId por vía (para que
    /// el admin compare por cara: foto actual vs propuesta).
    static func proposedVias(_ bloquesJson: String?) -> [ProposedVia] {
        guard let bloquesJson, let data = bloquesJson.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.enumerated().map { idx, o in
            let line = TopoLineVM(id: "b\(idx)", name: o["name"] as? String, grade: o["grade"] as? String,
                                  startType: o["startType"] as? String, points: points(o["linePath"] as? String),
                                  variant: (o["variant"] as? String).flatMap { $0.isEmpty ? nil : $0 },
                                  desc: (o["description"] as? String).flatMap { $0.isEmpty ? nil : $0 })
            let photo = (o["photoUrl"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            let tId = (o["targetLineId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            return ProposedVia(line: line, photoUrl: photo, targetLineId: tId)
        }
    }

    /// True si dos trazados son iguales (tolerancia mínima) — para saber si una
    /// corrección tocó el dibujo o solo campos de texto.
    static func pointsEqual(_ a: [CGPoint], _ b: [CGPoint]) -> Bool {
        guard a.count == b.count else { return false }
        for i in a.indices where abs(a[i].x - b[i].x) > 0.001 || abs(a[i].y - b[i].y) > 0.001 {
            return false
        }
        return true
    }

    /// Parsea `[{"x":..,"y":..}, ...]` a puntos normalizados.
    static func points(_ json: String?) -> [CGPoint] {
        guard let json, let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.compactMap { o in
            guard let x = (o["x"] as? NSNumber)?.doubleValue,
                  let y = (o["y"] as? NSNumber)?.doubleValue else { return nil }
            return CGPoint(x: x, y: y)
        }
    }
}

/// Foto + vías. Respeta el aspect real de la foto (clamp 0.55..2.2 como Android)
/// para que las líneas normalizadas caigan en el mismo sitio en ambas plataformas.
struct TopoPhotoView: View {
    let photoUrl: String
    /// Vías a destacar (la propuesta/nuevas). Se pintan sólidas con badge.
    let lines: [TopoLineVM]
    /// Vías existentes que NO cambian: se pintan **normales** (sólidas con su
    /// número y tipo) como contexto, no difuminadas.
    var normalLines: [TopoLineVM] = []
    /// Vías difuminadas: SOLO la versión vieja de la vía que se corrige, para que
    /// se distinga del resto. Vacío = nada difuminado.
    var referenceLines: [TopoLineVM] = []
    /// Con zoom y foco (tocar una vía apaga las demás). Se enciende SOLO donde
    /// se mira UNA piedra —ficha, revisión del admin—; en el feed, el buscador
    /// y las miniaturas se deja apagado, porque ahí la foto vive en una lista
    /// que se desplaza y el pellizco pelearía con el scroll.
    var interactive: Bool = false
    @State private var image: UIImage?
    @State private var ratio: CGFloat = 4.0 / 3.0
    /// El foco ya NO se enciende tocando la foto (a Rodrigo no le convencía).
    /// Se deja el mecanismo por si algún día lo activa otra pantalla.
    @State private var focus: Int? = nil

    var body: some View {
        Group {
            if interactive {
                TopoZoomLayer(editable: false) { zoom in
                    lienzo(zoom: zoom)
                }
            } else {
                lienzo(zoom: 1)
            }
        }
        .aspectRatio(ratio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .task(id: photoUrl) { await load() }
    }

    @ViewBuilder
    private func lienzo(zoom: CGFloat) -> some View {
        ZStack {
            Color.black
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ProgressView().tint(.white)
            }
            GeometryReader { geo in
                Canvas { ctx, size in draw(ctx, size, zoom: zoom) }
                    .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }

    private func load() async {
        // Caché en disco: se ve sin conexión si la escuela se guardó offline.
        if let img = await ImageCache.image(photoUrl) {
            await MainActor.run {
                image = img
                let w = img.size.width, h = img.size.height
                ratio = (w > 0 && h > 0) ? min(max(w / h, 0.55), 2.2) : 4.0 / 3.0
            }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize, zoom: CGFloat = 1) {
        // 1. SOLO la vía vieja que se corrige, difuminada (para distinguirla).
        for line in referenceLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.35)),
                       style: StrokeStyle(lineWidth: 4 * zoom, lineCap: .round, lineJoin: .round,
                                          dash: [6 * zoom, 6 * zoom]))
        }
        // 2. Existentes normales + propuesta, sólidas y numeradas en continuo.
        // Pintor único (TopoPainter): las franjas compartidas, el abanico y las
        // dos pasadas (trazos → badges) están dentro. Estilo .photo = tamaños
        // de la ficha/viewer.
        let solid = normalLines + lines
        let vias = solid.enumerated().map { (i, l) in
            TopoVia(number: i + 1, grade: l.grade, startType: l.startType, points: l.points,
                    lineWidth: nil, muted: focus != nil && focus != i)
        }
        // Al ampliar, los grosores se dividen por la escala: si no, al 400% el
        // trazo engorda con la foto y tapa la roca que ibas a mirar de cerca.
        TopoPainter.paint(GraphicsContextTarget(ctx: ctx), vias: vias, size: size,
                          style: .photoScaled(zoom))
    }

}
