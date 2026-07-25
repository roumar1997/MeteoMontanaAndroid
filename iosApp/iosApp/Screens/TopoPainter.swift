import SwiftUI
import UIKit

// Pintor de topos ÚNICO (P1.4). Antes había 4 copias del MISMO algoritmo de
// dibujo (TopoPhotoView, TopoEditorView, ShareLineImage, ShareFeedPostImage),
// que solo diferían en el backend (SwiftUI GraphicsContext vs UIKit CGContext),
// el tipo de color y los tamaños. La lógica de negocio (rachas compartidas,
// franjas, abanico, contorno) ya vivía en TopoShared; lo duplicado era el
// PLUMBING. Aquí se centraliza: una pasada de trazos + una de badges sobre un
// `TopoDrawTarget` abstracto, con `TopoStyle` para los tamaños de cada sitio.
// Espejo del renderTopo único de Android/Kotlin.

/// Vía a pintar (puntos NORMALIZADOS 0..1; el número es su índice+1).
struct TopoVia {
    let number: Int
    let grade: String?
    let startType: String?
    let points: [CGPoint]
    /// Override del ancho de trazo (el editor resalta la vía seleccionada más
    /// gruesa). nil = usar el de TopoStyle. El contorno oscuro pasa a ancho+4.
    var lineWidth: CGFloat? = nil
}

/// Tamaños de un topo (varían entre editor / ficha / imágenes de compartir).
/// Todos en las MISMAS unidades que el backend de dibujo concreto.
struct TopoStyle {
    var lineWidth: CGFloat
    var darkOutlineWidth: CGFloat     // contorno negro de la línea blanca
    var dash: [CGFloat]               // patrón de guiones (estilo guía)
    var badgeOuter: CGFloat
    var badgeInner: CGFloat
    var badgeText: CGFloat
    var startOuter: CGFloat
    var startInner: CGFloat
    var startText: CGFloat
    var fanStartSpacing: CGFloat
    var fanEndSpacing: CGFloat
    /// Escala aplicada a stripeStyle (1 en Canvas; s en las imágenes 1080px).
    var stripeScale: CGFloat = 1

    /// Ficha de escuela / viewer (TopoPhotoView): tamaños fijos en pt (mismos
    /// literales que el original; el Canvas de SwiftUI dibuja en puntos).
    static let photo = TopoStyle(
        lineWidth: 3.5, darkOutlineWidth: 6.5, dash: TopoShared.dash,
        badgeOuter: 9, badgeInner: 7, badgeText: 10,
        startOuter: 10.5, startInner: 8.5, startText: 7,
        fanStartSpacing: 9 * 2 + 4, fanEndSpacing: 10.5 * 2 + 4)

    /// Editor de topos (TopoEditorView): badges más grandes para tocar.
    static func editor(lineWidth w: CGFloat) -> TopoStyle {
        TopoStyle(lineWidth: w, darkOutlineWidth: w + 4, dash: TopoShared.dash,
                  badgeOuter: 12, badgeInner: 9, badgeText: 12,
                  startOuter: 13, startInner: 10.5, startText: 9,
                  fanStartSpacing: 12 * 2 + 4, fanEndSpacing: 14 * 2 + 4)
    }

    /// Imagen de compartir 1080px, escala s = ancho/base (360 vía, 380 feed).
    static func share(scale s: CGFloat, badgeOuter: CGFloat, badgeInner: CGFloat,
                      badgeText: CGFloat, startText: CGFloat,
                      fanStart: CGFloat, fanEnd: CGFloat) -> TopoStyle {
        TopoStyle(lineWidth: 5 * s, darkOutlineWidth: 9 * s,
                  dash: TopoShared.dash.map { $0 * s },
                  badgeOuter: badgeOuter * s, badgeInner: badgeInner * s, badgeText: badgeText * s,
                  startOuter: 14 * s, startInner: 11 * s, startText: startText * s,
                  fanStartSpacing: fanStart * s, fanEndSpacing: fanEnd * s,
                  stripeScale: s)
    }
}

/// Primitivas de dibujo abstractas (un backend por adaptador).
protocol TopoDrawTarget {
    func strokePath(_ points: [CGPoint], color: UIColor, width: CGFloat,
                    roundCap: Bool, dash: [CGFloat], dashPhase: CGFloat)
    func fillCircle(_ center: CGPoint, radius: CGFloat, color: UIColor)
    func drawText(_ text: String, at center: CGPoint, size: CGFloat, color: UIColor)
}

enum TopoPainter {

    /// Pinta todas las vías sobre `size` (px del backend). Dos pasadas: primero
    /// TODOS los trazos, luego TODOS los badges (si no, la línea de una vía
    /// posterior taparía los badges de las anteriores).
    static func paint(_ target: TopoDrawTarget, vias: [TopoVia], size: CGSize, style: TopoStyle) {
        let solid = vias.filter { !$0.points.isEmpty }
        guard !solid.isEmpty else { return }
        let shared = TopoShared.sharedSegmentLines(solid.map { $0.points })
        let startFan = TopoShared.fanOffsets(solid.map { $0.points.first }, spacing: style.fanStartSpacing)
        let endFan = TopoShared.fanOffsets(solid.map { $0.points.last }, spacing: style.fanEndSpacing)

        func px(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x * size.width, y: p.y * size.height) }

        // ── Pasada 1: trazos ──────────────────────────────────────────────
        for (idx, via) in solid.enumerated() {
            let stroke = UIColor(GradeColor.style(via.grade).stroke)
            let dark = GradeColor.style(via.grade).dark
            let lw = via.lineWidth ?? style.lineWidth
            let darkW = via.lineWidth != nil ? lw + 4 : style.darkOutlineWidth
            for run in TopoShared.splitRuns(via.points, shared: shared) {
                let runPts = run.pts.map(px)
                guard runPts.count > 1 else { continue }
                if let stripe = TopoShared.stripeStyle(run, lineIdx: idx, scale: style.stripeScale) {
                    target.strokePath(runPts, color: stroke, width: lw,
                                      roundCap: false, dash: stripe.dash, dashPhase: stripe.phase)
                } else {
                    if dark {
                        target.strokePath(runPts, color: UIColor.black.withAlphaComponent(0.8),
                                          width: darkW, roundCap: true,
                                          dash: style.dash, dashPhase: 0)
                    }
                    target.strokePath(runPts, color: stroke, width: lw,
                                      roundCap: true, dash: style.dash, dashPhase: 0)
                }
            }
        }

        // ── Pasada 2: badges ──────────────────────────────────────────────
        for (idx, via) in solid.enumerated() {
            let s = GradeColor.style(via.grade)
            let stroke = UIColor(s.stroke)
            let textColor: UIColor = s.dark ? .black : .white
            let pts = via.points.map(px)
            let start = CGPoint(x: pts[0].x + startFan[idx], y: pts[0].y)
            target.fillCircle(start, radius: style.badgeOuter, color: .white)
            target.fillCircle(start, radius: style.badgeInner, color: stroke)
            target.drawText("\(via.number)", at: start, size: style.badgeText, color: textColor)
            if let label = startLabel(via.startType), pts.count > 1 {
                let last = pts[pts.count - 1]
                let end = CGPoint(x: last.x + endFan[idx], y: last.y)
                target.fillCircle(end, radius: style.startOuter, color: s.dark ? .black : .white)
                target.fillCircle(end, radius: style.startInner, color: stroke)
                target.drawText(label, at: end, size: style.startText, color: textColor)
            }
        }
    }

    /// Etiqueta corta del tipo de inicio (PIE/SIT/SEM/LAN/TRV) o nil.
    static func startLabel(_ t: String?) -> String? {
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

// ── Adaptador SwiftUI (GraphicsContext) — editor y ficha ──────────────────
struct GraphicsContextTarget: TopoDrawTarget {
    let ctx: GraphicsContext

    func strokePath(_ points: [CGPoint], color: UIColor, width: CGFloat,
                    roundCap: Bool, dash: [CGFloat], dashPhase: CGFloat) {
        guard points.count > 1 else { return }
        var path = Path()
        path.move(to: points[0])
        for p in points.dropFirst() { path.addLine(to: p) }
        ctx.stroke(path, with: .color(Color(color)),
                   style: StrokeStyle(lineWidth: width, lineCap: roundCap ? .round : .butt,
                                      lineJoin: .round, dash: dash, dashPhase: dashPhase))
    }

    func fillCircle(_ center: CGPoint, radius: CGFloat, color: UIColor) {
        ctx.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                        width: radius * 2, height: radius * 2)),
                 with: .color(Color(color)))
    }

    func drawText(_ text: String, at center: CGPoint, size: CGFloat, color: UIColor) {
        ctx.draw(Text(text).font(.system(size: size, weight: .bold)).foregroundColor(Color(color)),
                 at: center, anchor: .center)
    }
}

// ── Adaptador UIKit (CGContext) — imágenes de compartir 1080px ─────────────
struct CGContextTarget: TopoDrawTarget {
    let cg: CGContext

    func strokePath(_ points: [CGPoint], color: UIColor, width: CGFloat,
                    roundCap: Bool, dash: [CGFloat], dashPhase: CGFloat) {
        guard points.count > 1 else { return }
        let path = UIBezierPath()
        path.move(to: points[0])
        for p in points.dropFirst() { path.addLine(to: p) }
        path.lineJoinStyle = .round
        path.lineCapStyle = roundCap ? .round : .butt
        if !dash.isEmpty { path.setLineDash(dash, count: dash.count, phase: dashPhase) }
        path.lineWidth = width
        color.setStroke()
        path.stroke()
    }

    func fillCircle(_ center: CGPoint, radius: CGFloat, color: UIColor) {
        cg.setFillColor(color.cgColor)
        cg.fillEllipse(in: CGRect(x: center.x - radius, y: center.y - radius,
                                  width: radius * 2, height: radius * 2))
    }

    func drawText(_ text: String, at center: CGPoint, size: CGFloat, color: UIColor) {
        let font = UIFont.systemFont(ofSize: size, weight: .bold)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let sz = (text as NSString).size(withAttributes: attrs)
        (text as NSString).draw(at: CGPoint(x: center.x - sz.width / 2, y: center.y - sz.height / 2),
                                withAttributes: attrs)
    }
}
