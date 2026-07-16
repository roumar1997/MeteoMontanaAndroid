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
}

extension TopoLineVM {
    init(_ l: BlockLine) {
        self.init(id: l.id, name: l.name, grade: l.grade,
                  startType: l.startType, points: TopoParse.points(l.linePath))
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
                                  startType: o["startType"] as? String, points: points(o["linePath"] as? String))
            let photo = (o["photoUrl"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            let tId = (o["targetLineId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            return ProposedVia(line: line, photoUrl: photo, targetLineId: tId)
        }
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
    @State private var image: UIImage?
    @State private var ratio: CGFloat = 4.0 / 3.0

    var body: some View {
        ZStack {
            Color.black
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ProgressView().tint(.white)
            }
            GeometryReader { geo in
                Canvas { ctx, size in draw(ctx, size) }
                    .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .aspectRatio(ratio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .task(id: photoUrl) { await load() }
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

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        // 1. SOLO la vía vieja que se corrige, difuminada (para distinguirla).
        for line in referenceLines where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
            var path = Path(); path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            ctx.stroke(path, with: .color(style.stroke.opacity(0.35)),
                       style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round, dash: [6, 6]))
        }
        // 2. Existentes normales + propuesta, sólidas y numeradas en continuo.
        let solid = normalLines + lines
        for (idx, line) in solid.enumerated() where !line.points.isEmpty {
            drawSolidLine(ctx, size, line: line, number: idx + 1)
        }
    }

    private func drawSolidLine(_ ctx: GraphicsContext, _ size: CGSize, line: TopoLineVM, number: Int) {
        let style = GradeColor.style(line.grade)
        let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
        guard !pts.isEmpty else { return }
        var path = Path()
        path.move(to: pts[0])
        for p in pts.dropFirst() { path.addLine(to: p) }
        let dash: [CGFloat] = style.dashed ? [10, 8] : []
        // Tamaños unificados con Android (TopoPhotoCanvas.kt): badge 9/7,
        // inicio 10.5/8.5, trazo 3.5 — antes iOS pintaba ~2.5x más grande
        // (pt vs px físicos) y tapaba la piedra.
        // Línea blanca: contorno negro para que se vea sobre cualquier foto.
        if style.dark {
            ctx.stroke(path, with: .color(.black.opacity(0.8)),
                       style: StrokeStyle(lineWidth: 6.5, lineCap: .round, lineJoin: .round, dash: dash))
        }
        ctx.stroke(path, with: .color(style.stroke),
                   style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round, dash: dash))
        let textColor: Color = style.dark ? .black : .white
        badge(ctx, at: pts[0], outer: 9, inner: 7, fill: .white, ring: style.stroke,
              text: "\(number)", textSize: 10, textColor: textColor)
        if let label = startLabel(line.startType), pts.count > 1 {
            badge(ctx, at: pts[pts.count - 1], outer: 10.5, inner: 8.5,
                  fill: style.dark ? .black : .white, ring: style.stroke,
                  text: label, textSize: 7, textColor: textColor)
        }
    }

    private func badge(_ ctx: GraphicsContext, at p: CGPoint, outer: CGFloat, inner: CGFloat,
                       fill: Color, ring: Color, text: String, textSize: CGFloat, textColor: Color) {
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - outer, y: p.y - outer, width: outer * 2, height: outer * 2)),
                 with: .color(fill))
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - inner, y: p.y - inner, width: inner * 2, height: inner * 2)),
                 with: .color(ring))
        let t = Text(text).font(.system(size: textSize, weight: .bold)).foregroundColor(textColor)
        ctx.draw(t, at: p, anchor: .center)
    }

    private func startLabel(_ t: String?) -> String? {
        switch t?.uppercased() {
        case "PIE", "STAND": return "PIE"
        case "SIT": return "SIT"
        case "LANCE", "JUMP": return "LAN"
        case "TRAV": return "TRV"
        default: return nil
        }
    }
}
