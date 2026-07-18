import SwiftUI
import UIKit
import Shared

/// Comparte un post del feed como IMAGEN 1080×1920 (formato historia): foto de
/// la cara con SOLO la línea del post + cabecera Cumbre (tipo de logro, vía y
/// grado, piedra · escuela y autor). Espejo de `ShareFeedPostImage.kt`.
///
/// Si el post no tiene foto/trazo se comparte igualmente una tarjeta de datos
/// (grado enorme en terra). El texto que acompaña es mínimo y SIN deep link.
enum ShareFeedPostImage {

    /// Facebook App ID para Instagram Stories (`instagram-stories://`).
    /// ⚠️ PENDIENTE: cuando Rodrigo registre la app en developers.facebook.com,
    /// pegar aquí el App ID (solo dígitos). Mientras esté VACÍO, el botón
    /// "HISTORIAS" no se muestra en la UI.
    static let facebookAppId = ""

    /// true si podemos ofrecer "COMPARTIR EN HISTORIAS".
    static var canShareToStories: Bool { !facebookAppId.isEmpty }

    /* ── Paleta Cumbre fija (light), = ShareLineImage ─────────────────────── */
    private static let paper = UIColor(rgb: 0xFAF7F2)
    private static let ink = UIColor(rgb: 0x1A1A1A)
    private static let inkSoft = UIColor(rgb: 0x6B6B6B)
    private static let rule = UIColor(rgb: 0xE2DCD2)
    private static let terra = UIColor(rgb: 0xC0532B)

    /// Punto de entrada: compone la imagen y presenta el share sheet.
    static func share(post: FeedPost) async {
        let image = await renderCard(post: post)
        await present([image, plainText(post)])
    }

    /// Directo a Instagram Stories. Solo llamar si `canShareToStories`.
    static func shareToStories(post: FeedPost) async {
        guard canShareToStories else { return }
        let image = await renderCard(post: post)
        guard let data = image.pngData(),
              let url = URL(string: "instagram-stories://share?source_application=\(facebookAppId)")
        else { return }
        await MainActor.run {
            UIPasteboard.general.setItems(
                [["com.instagram.sharedSticker.backgroundImage": data]],
                options: [.expirationDate: Date().addingTimeInterval(300)])
            UIApplication.shared.open(url, options: [:]) { ok in
                if !ok { Task { await share(post: post) } }
            }
        }
    }

    /// Texto plano de acompañamiento — SIN enlace (decisión del usuario).
    private static func plainText(_ post: FeedPost) -> String {
        let title = feedPostTitle(post)
        var parts: [String] = []
        if !title.isEmpty { parts.append("🧗 " + title) }
        let place = feedPostPlace(post)
        if !place.isEmpty { parts.append(place) }
        // Deep link al post: si el receptor tiene Cumbre se abre el detalle
        // (landing /s/p con Open Graph si no la tiene).
        parts.append("Míralo en Cumbre: https://api.climbingteams.com/s/p/\(post.id)")
        return parts.joined(separator: "\n")
    }

    private static func kindEyebrow(_ post: FeedPost) -> String {
        feedKindLabel(post.kind, post.discipline)
    }

    // MARK: - Render

    private static func renderCard(post: FeedPost) async -> UIImage {
        // Fotos (si las hay), con la caché de disco de siempre.
        var topoPhoto: UIImage? = nil
        if let p = post.photoPath, !p.isEmpty {
            topoPhoto = await ImageCache.image(p)
        }
        var celebration: UIImage? = nil
        if let u = post.photoUrl, !u.isEmpty {
            celebration = await ImageCache.image(u)
        }
        // Mismo layout que la tarjeta: sin topo, la foto de celebración es la
        // imagen principal (sin trazo); con topo, va como miniatura en la esquina.
        let photo = topoPhoto ?? celebration
        let corner = topoPhoto != nil ? celebration : nil
        let drawLine = topoPhoto != nil
        return await MainActor.run {
            drawCard(post: post, photo: photo, drawLine: drawLine, corner: corner)
        }
    }

    @MainActor
    private static func drawCard(post: FeedPost, photo: UIImage?,
                                 drawLine shouldDrawLine: Bool = true,
                                 corner: UIImage? = nil) -> UIImage {
        let w: CGFloat = 1080, h: CGFloat = 1920, pad: CGFloat = 72
        let availW = w - 2 * pad
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1   // px reales
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)

        return renderer.image { rctx in
            let cg = rctx.cgContext
            paper.setFill(); cg.fill(CGRect(x: 0, y: 0, width: w, height: h))
            rule.setStroke()
            let border = UIBezierPath(rect: CGRect(x: 16, y: 16, width: w - 32, height: h - 32))
            border.lineWidth = 3; border.stroke()

            // ── Cabecera: eyebrow del tipo de logro ──
            drawText(kindEyebrow(post), at: CGRect(x: pad, y: pad, width: availW, height: 44),
                     font: mono(34, bold: true), color: terra, kern: 5, align: .left)

            // Vía + grado (serif grande, hasta 2 líneas).
            let title = feedPostTitle(post).isEmpty ? "Ascenso" : feedPostTitle(post)
            let titleFont = serif(78)
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byTruncatingTail
            let titleAttrs: [NSAttributedString.Key: Any] =
                [.font: titleFont, .foregroundColor: ink, .paragraphStyle: para]
            let maxTitleH: CGFloat = 190
            let measured = (title as NSString).boundingRect(
                with: CGSize(width: availW, height: maxTitleH),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: titleAttrs, context: nil).height
            let titleH = min(ceil(measured), maxTitleH)
            (title as NSString).draw(with: CGRect(x: pad, y: pad + 60, width: availW, height: titleH),
                                     options: [.usesLineFragmentOrigin, .usesFontLeading],
                                     attributes: titleAttrs, context: nil)
            var y = pad + 60 + titleH + 14

            // Piedra · escuela (+ roca si viene).
            let place = feedPostPlace(post)
            if !place.isEmpty {
                drawText(place, at: CGRect(x: pad, y: y, width: availW, height: 48),
                         font: UIFont.systemFont(ofSize: 38), color: inkSoft, kern: 0, align: .left)
                y += 52
            }

            // Autor.
            var authorLabel = ""
            if let u = post.author.username, !u.isEmpty { authorLabel = "@" + u }
            else if let d = post.author.displayName, !d.isEmpty { authorLabel = d }
            if !authorLabel.isEmpty {
                drawText("por " + authorLabel, at: CGRect(x: pad, y: y, width: availW, height: 44),
                         font: UIFont.systemFont(ofSize: 36, weight: .bold),
                         color: terra, kern: 0, align: .left)
                y += 48
            }
            y += 16

            // ── Foto con SOLO la línea del post ──
            let footerH: CGFloat = 130
            if let photo {
                let avail = h - footerH - y - pad
                let ratio = min(max(photo.size.width / max(photo.size.height, 1), 0.55), 2.2)
                var rectW = availW, rectH = rectW / ratio
                if rectH > avail { rectH = avail; rectW = rectH * ratio }
                let left = (w - rectW) / 2
                let top = y + (avail - rectH) / 2
                let photoRect = CGRect(x: left, y: top, width: rectW, height: rectH)

                cg.saveGState()
                cg.addRect(photoRect); cg.clip()
                // Foto en modo aspect-fill (centerCrop).
                let scale = max(photoRect.width / photo.size.width,
                                photoRect.height / photo.size.height)
                let dw = photo.size.width * scale, dh = photo.size.height * scale
                photo.draw(in: CGRect(x: photoRect.midX - dw / 2, y: photoRect.midY - dh / 2,
                                      width: dw, height: dh))
                if shouldDrawLine { drawLine(cg, post: post, in: photoRect) }
                cg.restoreGState()
                rule.setStroke()
                let pb = UIBezierPath(rect: photoRect); pb.lineWidth = 3; pb.stroke()

                // Miniatura de la foto de celebración (esquina superior derecha
                // del topo, mismo layout que la tarjeta: ~88×110 con borde papel).
                if let corner {
                    let miniW = rectW * 0.26
                    let miniH = miniW * 110.0 / 88.0
                    let margin: CGFloat = 20
                    let mini = CGRect(x: photoRect.maxX - margin - miniW,
                                      y: photoRect.minY + margin,
                                      width: miniW, height: miniH)
                    let radius: CGFloat = 14
                    let clipPath = UIBezierPath(roundedRect: mini, cornerRadius: radius)
                    cg.saveGState()
                    clipPath.addClip()
                    // Aspect-fill (centerCrop) dentro de la miniatura.
                    let cScale = max(mini.width / corner.size.width,
                                     mini.height / corner.size.height)
                    let cw = corner.size.width * cScale, ch = corner.size.height * cScale
                    corner.draw(in: CGRect(x: mini.midX - cw / 2, y: mini.midY - ch / 2,
                                           width: cw, height: ch))
                    cg.restoreGState()
                    paper.setStroke()
                    let mb = UIBezierPath(roundedRect: mini, cornerRadius: radius)
                    mb.lineWidth = 6; mb.stroke()
                }
            } else if let g = post.grade, !g.isEmpty {
                // Sin foto: tarjeta de datos centrada (grado enorme en terra).
                let gFont = serif(260)
                let attrs: [NSAttributedString.Key: Any] = [.font: gFont, .foregroundColor: terra]
                let sz = (g as NSString).size(withAttributes: attrs)
                (g as NSString).draw(at: CGPoint(x: (w - sz.width) / 2, y: (h - sz.height) / 2),
                                     withAttributes: attrs)
            }

            // ── Pie de marca ──
            drawText("⛰ CUMBRE", at: CGRect(x: pad, y: h - 110, width: availW, height: 44),
                     font: mono(34, bold: true), color: terra, kern: 5, align: .right)
        }
    }

    /// Dibuja las líneas del post sobre la foto: la del ascenso (badge "1") o,
    /// en posts de piedra nueva (sin linePath), TODAS las vías de la cara
    /// portada (blockLines) — antes la imagen compartida salía sin líneas.
    private static func drawLine(_ cg: CGContext, post: FeedPost, in rect: CGRect) {
        var vias: [(name: String?, grade: String?, startType: String?, pts: [CGPoint])] = []
        let single = TopoParse.points(post.linePath)
        if !single.isEmpty {
            vias.append((post.lineName, post.grade, post.startType, single))
        } else if let lines = post.blockLines {
            for l in lines {
                let p = TopoParse.points(l.linePath)
                if !p.isEmpty { vias.append((l.name, l.grade, l.startType, p)) }
            }
        }
        guard !vias.isEmpty else { return }
        let s = rect.width / 380.0
        // Tramos compartidos → FRANJAS por vía; badges en abanico si coinciden.
        let shared = TopoShared.sharedSegmentLines(vias.map { $0.pts })
        let startFan = TopoShared.fanOffsets(vias.map { $0.pts.first }, spacing: (14 * 2 + 4) * s)
        let endFan = TopoShared.fanOffsets(vias.map { $0.pts.last }, spacing: (14 * 2 + 4) * s)
        for (idx, via) in vias.enumerated() {
            let style = GradeColor.style(via.grade)
            let stroke = UIColor(style.stroke)
            var pts = via.pts.map {
                CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height)
            }
            if !pts.isEmpty {
                pts[0].x += startFan[idx]
                if pts.count > 1 { pts[pts.count - 1].x += endFan[idx] }
            }
            for run in TopoShared.splitRuns(via.pts, shared: shared) {
                let runPts = run.pts.map {
                    CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height)
                }
                guard runPts.count > 1 else { continue }
                let path = UIBezierPath()
                path.move(to: runPts[0])
                for p in runPts.dropFirst() { path.addLine(to: p) }
                path.lineJoinStyle = .round
                if let stripe = TopoShared.stripeStyle(run, lineIdx: idx, scale: s) {
                    path.lineCapStyle = .butt
                    path.setLineDash(stripe.dash, count: stripe.dash.count, phase: stripe.phase)
                } else {
                    path.lineCapStyle = .round
                    if style.dashed { path.setLineDash([10 * s, 8 * s], count: 2, phase: 0) }
                    if style.dark {
                        path.lineWidth = 9 * s
                        UIColor.black.withAlphaComponent(0.8).setStroke(); path.stroke()
                    }
                }
                path.lineWidth = 5 * s
                stroke.setStroke(); path.stroke()
            }

            let textColor: UIColor = style.dark ? .black : .white
            fillCircle(cg, pts[0], 14 * s, .white)
            fillCircle(cg, pts[0], 11 * s, stroke)
            drawCentered("\(idx + 1)", at: pts[0], size: 15 * s, color: textColor)
            // Círculo del tipo de inicio en la base de la línea (= ShareLineImage).
            if let label = startLabel(via.startType), pts.count > 1 {
                let last = pts[pts.count - 1]
                fillCircle(cg, last, 14 * s, style.dark ? .black : .white)
                fillCircle(cg, last, 11 * s, stroke)
                drawCentered(label, at: last, size: 9 * s, color: textColor)
            }
        }
    }

    /// Abreviatura del tipo de inicio (= startLabel de ShareLineImage, que es private allí).
    private static func startLabel(_ t: String?) -> String? {
        switch t?.uppercased() {
        case "PIE", "STAND": return "PIE"
        case "SIT": return "SIT"
        case "SEMI": return "SEM"
        case "LANCE", "JUMP": return "LAN"
        case "TRAV": return "TRV"
        default: return nil
        }
    }

    // MARK: - Helpers de dibujo (= ShareLineImage; son private allí)

    private static func fillCircle(_ cg: CGContext, _ c: CGPoint, _ r: CGFloat, _ color: UIColor) {
        cg.setFillColor(color.cgColor)
        cg.fillEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }

    private static func drawCentered(_ text: String, at c: CGPoint, size: CGFloat, color: UIColor) {
        let font = UIFont.systemFont(ofSize: size, weight: .bold)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let sz = (text as NSString).size(withAttributes: attrs)
        (text as NSString).draw(at: CGPoint(x: c.x - sz.width / 2, y: c.y - sz.height / 2),
                                withAttributes: attrs)
    }

    private static func drawText(_ text: String, at rect: CGRect, font: UIFont, color: UIColor,
                                 kern: CGFloat, align: NSTextAlignment) {
        let para = NSMutableParagraphStyle()
        para.alignment = align
        para.lineBreakMode = .byTruncatingTail
        let attrs: [NSAttributedString.Key: Any] =
            [.font: font, .foregroundColor: color, .kern: kern, .paragraphStyle: para]
        (text as NSString).draw(in: rect, withAttributes: attrs)
    }

    private static func mono(_ size: CGFloat, bold: Bool) -> UIFont {
        UIFont(name: bold ? "JetBrainsMono-Bold" : "JetBrainsMono-Regular", size: size)
            ?? .monospacedSystemFont(ofSize: size, weight: bold ? .bold : .regular)
    }

    private static func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Bold", size: size) ?? .systemFont(ofSize: size, weight: .bold)
    }

    // MARK: - Share sheet (patrón de ShareLineImage.present)

    @MainActor
    private static func present(_ items: [Any]) {
        guard let scene = UIApplication.shared.connectedScenes
                .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
        else { return }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let pop = vc.popoverPresentationController {
            pop.sourceView = top.view
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY,
                                    width: 0, height: 0)
            pop.permittedArrowDirections = []
        }
        top.present(vc, animated: true)
    }
}
