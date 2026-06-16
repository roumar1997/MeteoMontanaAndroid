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
    let lines: [TopoLineVM]
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
        guard let url = URL(string: photoUrl) else { return }
        if let (data, _) = try? await URLSession.shared.data(from: url),
           let img = UIImage(data: data) {
            await MainActor.run {
                image = img
                let w = img.size.width, h = img.size.height
                ratio = (w > 0 && h > 0) ? min(max(w / h, 0.55), 2.2) : 4.0 / 3.0
            }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        for (idx, line) in lines.enumerated() where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let pts = line.points.map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }

            var path = Path()
            path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            let dash: [CGFloat] = style.dashed ? [10, 8] : []
            // Línea blanca: contorno negro para que se vea sobre cualquier foto.
            if style.dark {
                ctx.stroke(path, with: .color(.black.opacity(0.8)),
                           style: StrokeStyle(lineWidth: 9, lineCap: .round, lineJoin: .round, dash: dash))
            }
            ctx.stroke(path, with: .color(style.stroke),
                       style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round, dash: dash))

            // Badge numérico en el inicio.
            let textColor: Color = style.dark ? .black : .white
            badge(ctx, at: pts[0], outer: 12, inner: 9.5, fill: .white, ring: style.stroke,
                  text: "\(idx + 1)", textSize: 13, textColor: textColor)

            // Badge de tipo de inicio en el final.
            if let label = startLabel(line.startType) {
                badge(ctx, at: pts[pts.count - 1], outer: 14, inner: 11,
                      fill: style.dark ? .black : .white, ring: style.stroke,
                      text: label, textSize: 9, textColor: textColor)
            }
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
