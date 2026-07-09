import SwiftUI
import UIKit
import Shared

/// Genera una imagen vertical (1080×1920, formato historia) con la FOTO real de
/// la vía y sus líneas dibujadas encima (espejo de `ShareLineImage.kt` de
/// Android) y abre el share sheet del sistema. En el menú aparece Instagram
/// (→Historia), WhatsApp, etc., con la imagen ya adjunta.
///
/// Si la vía no tiene foto o dibujo, cae al compartir de texto de siempre.
enum ShareLineImage {

    private static let playUrl = "https://play.google.com/store/apps/details?id=com.meteomontana.android"
    private static let appStoreUrl = "https://apps.apple.com/app/id6785776686"

    /* ── Paleta Cumbre fija (light) para que la imagen sea consistente ──────── */
    private static let paper = UIColor(rgb: 0xFAF7F2)
    private static let ink = UIColor(rgb: 0x1A1A1A)
    private static let inkSoft = UIColor(rgb: 0x6B6B6B)
    private static let rule = UIColor(rgb: 0xE2DCD2)
    private static let terra = UIColor(rgb: 0xC0532B)

    /// Punto de entrada: compone la imagen (o cae a texto) y presenta el share sheet.
    static func share(block: Block, line: BlockLine, schoolName: String?) async {
        if let image = await renderCard(block: block, line: line, schoolName: schoolName) {
            let text = "🧗 \(line.name) en Cumbre\n\nDescarga Cumbre:\nAndroid: \(playUrl)\niOS: \(appStoreUrl)"
            await present([image, text])
        } else {
            await present([shareLineText(block: block, line: line, schoolName: schoolName)])
        }
    }

    // MARK: - Render

    private static func renderCard(block: Block, line: BlockLine, schoolName: String?) async -> UIImage? {
        // 1. Localiza la cara (foto + líneas) a la que pertenece esta vía.
        let face = block.facesOrDerived().first { f in f.lines.contains { $0.id == line.id } }
        let photoUrl = face?.photoPath ?? line.photoPath ?? block.photoPath
        guard let photoUrl, !photoUrl.isEmpty else { return nil }
        let lines = (face?.lines.map { TopoLineVM($0) } ?? [TopoLineVM(line)])
            .filter { !$0.points.isEmpty }
        guard !lines.isEmpty else { return nil }

        // 2. Descarga la foto a UIImage (caché en disco, igual que TopoPhotoView).
        guard let photo = await ImageCache.image(photoUrl) else { return nil }

        // 3. Compón la card en px reales (scale = 1).
        return await MainActor.run {
            drawCard(block: block, line: line, schoolName: schoolName, lines: lines, photo: photo)
        }
    }

    @MainActor
    private static func drawCard(block: Block, line: BlockLine, schoolName: String?,
                                 lines: [TopoLineVM], photo: UIImage) -> UIImage {
        let w: CGFloat = 1080, h: CGFloat = 1920, pad: CGFloat = 72
        let availW = w - 2 * pad
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1   // px reales, no puntos (evita el 2x/3x del dispositivo)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)

        return renderer.image { rctx in
            let cg = rctx.cgContext
            // Fondo papel + borde regla.
            paper.setFill(); cg.fill(CGRect(x: 0, y: 0, width: w, height: h))
            rule.setStroke()
            let border = UIBezierPath(rect: CGRect(x: 16, y: 16, width: w - 32, height: h - 32))
            border.lineWidth = 3; border.stroke()

            // ── Cabecera ────────────────────────────────────────────────────
            let kind = block.discipline.uppercased() == "ROUTE" ? "VÍA" : "BLOQUE"
            drawText("\(kind) EN CUMBRE", at: CGRect(x: pad, y: 60, width: availW, height: 44),
                     font: mono(30, bold: true), color: terra, kern: 4, align: .left)

            // Nombre de la vía (serif grande, hasta 2 líneas con elipsis).
            let name = line.name.isEmpty ? block.name : line.name
            let titleFont = serif(72)
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byTruncatingTail
            let titleAttrs: [NSAttributedString.Key: Any] =
                [.font: titleFont, .foregroundColor: ink, .paragraphStyle: para]
            let maxTitleH: CGFloat = 210
            let measured = (name as NSString).boundingRect(
                with: CGSize(width: availW, height: maxTitleH),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: titleAttrs, context: nil).height
            let titleH = min(ceil(measured), maxTitleH)
            (name as NSString).draw(with: CGRect(x: pad, y: 130, width: availW, height: titleH),
                                    options: [.usesLineFragmentOrigin, .usesFontLeading],
                                    attributes: titleAttrs, context: nil)

            // Grado (badge coloreado) + piedra · escuela.
            var subX = pad
            let subY = 130 + titleH + 24
            if let grade = line.grade, !grade.isEmpty {
                let gStyle = GradeColor.style(grade)
                let gFont = UIFont.systemFont(ofSize: 34, weight: .bold)
                let gw = (grade as NSString).size(withAttributes: [.font: gFont]).width + 40
                let badge = CGRect(x: subX, y: subY, width: gw, height: 54)
                UIColor(gStyle.stroke).setFill()
                UIBezierPath(roundedRect: badge, cornerRadius: 6).fill()
                drawText(grade, at: badge, font: gFont,
                         color: gStyle.dark ? ink : .white, kern: 0, align: .center, vCenter: true)
                subX = badge.maxX + 20
            }
            var where_ = block.name
            if let s = schoolName, !s.isEmpty { where_ += " · \(s)" }
            drawText(where_, at: CGRect(x: subX, y: subY, width: w - subX - pad, height: 54),
                     font: UIFont.systemFont(ofSize: 34), color: inkSoft, kern: 0,
                     align: .left, vCenter: true)

            // ── Foto con las líneas ─────────────────────────────────────────
            let footerTop = h - 130
            let photoTop = subY + 90
            let availH = footerTop - photoTop - 20
            let ratio = min(max(photo.size.width / max(photo.size.height, 1), 0.55), 2.2)
            var rectW = availW, rectH = rectW / ratio
            if rectH > availH { rectH = availH; rectW = rectH * ratio }
            let left = (w - rectW) / 2
            let top = photoTop + (availH - rectH) / 2
            let photoRect = CGRect(x: left, y: top, width: rectW, height: rectH)

            cg.saveGState()
            cg.addRect(photoRect); cg.clip()
            // Foto en modo aspect-fill (centerCrop) dentro de photoRect.
            let scale = max(photoRect.width / photo.size.width, photoRect.height / photo.size.height)
            let dw = photo.size.width * scale, dh = photo.size.height * scale
            photo.draw(in: CGRect(x: photoRect.midX - dw / 2, y: photoRect.midY - dh / 2,
                                  width: dw, height: dh))
            drawLines(cg, lines: lines, in: photoRect)
            cg.restoreGState()
            rule.setStroke()
            let photoBorder = UIBezierPath(rect: photoRect); photoBorder.lineWidth = 3; photoBorder.stroke()

            // ── Pie de marca ────────────────────────────────────────────────
            drawText("⛰ CUMBRE", at: CGRect(x: pad, y: h - 110, width: availW, height: 44),
                     font: mono(30, bold: true), color: terra, kern: 4, align: .right, vCenter: true)
        }
    }

    /// Dibuja las vías sobre la foto — espejo de `TopoPhotoView.drawSolidLine`,
    /// con grosores/badges escalados al tamaño de la card.
    private static func drawLines(_ cg: CGContext, lines: [TopoLineVM], in rect: CGRect) {
        let s = rect.width / 360.0   // 360 ≈ ancho del Canvas en la app (puntos)
        for (idx, line) in lines.enumerated() where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let stroke = UIColor(style.stroke)
            let pts = line.points.map {
                CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height)
            }
            let path = UIBezierPath()
            path.move(to: pts[0])
            for p in pts.dropFirst() { path.addLine(to: p) }
            path.lineCapStyle = .round; path.lineJoinStyle = .round
            if style.dashed { path.setLineDash([10 * s, 8 * s], count: 2, phase: 0) }
            // Línea blanca: contorno negro para que se vea sobre cualquier foto.
            if style.dark {
                path.lineWidth = 9 * s
                UIColor.black.withAlphaComponent(0.8).setStroke(); path.stroke()
            }
            path.lineWidth = 5 * s
            stroke.setStroke(); path.stroke()

            let textColor: UIColor = style.dark ? .black : .white
            fillCircle(cg, pts[0], 12 * s, .white)
            fillCircle(cg, pts[0], 9.5 * s, stroke)
            drawCentered("\(idx + 1)", at: pts[0], size: 13 * s, color: textColor)
            if let label = startLabel(line.startType), pts.count > 1 {
                let last = pts[pts.count - 1]
                fillCircle(cg, last, 14 * s, style.dark ? .black : .white)
                fillCircle(cg, last, 11 * s, stroke)
                drawCentered(label, at: last, size: 9 * s, color: textColor)
            }
        }
    }

    // MARK: - Helpers de dibujo

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
                                 kern: CGFloat, align: NSTextAlignment, vCenter: Bool = false) {
        let para = NSMutableParagraphStyle()
        para.alignment = align
        para.lineBreakMode = .byTruncatingTail
        let attrs: [NSAttributedString.Key: Any] =
            [.font: font, .foregroundColor: color, .kern: kern, .paragraphStyle: para]
        var r = rect
        if vCenter {
            let th = (text as NSString).size(withAttributes: attrs).height
            r = CGRect(x: rect.minX, y: rect.midY - th / 2, width: rect.width, height: th)
        }
        (text as NSString).draw(in: r, withAttributes: attrs)
    }

    private static func startLabel(_ t: String?) -> String? {
        switch t?.uppercased() {
        case "PIE", "STAND": return "PIE"
        case "SIT": return "SIT"
        case "LANCE", "JUMP": return "LAN"
        case "TRAV": return "TRV"
        default: return nil
        }
    }

    private static func mono(_ size: CGFloat, bold: Bool) -> UIFont {
        UIFont(name: bold ? "JetBrainsMono-Bold" : "JetBrainsMono-Regular", size: size)
            ?? .monospacedSystemFont(ofSize: size, weight: bold ? .bold : .regular)
    }

    private static func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Bold", size: size) ?? .systemFont(ofSize: size, weight: .bold)
    }

    /// Texto de fallback (sin foto/dibujo) — espejo de `shareLineText` de la vista.
    private static func shareLineText(block: Block, line: BlockLine, schoolName: String?) -> String {
        let isRoute = block.discipline.uppercased() == "ROUTE"
        let kind = isRoute ? "vía" : "bloque"
        let article = isRoute ? "esta" : "este"
        let grade = (line.grade?.isEmpty == false) ? " \(line.grade!)" : ""
        var place = block.name
        if let s = schoolName, !s.isEmpty { place += " · \(s)" }
        let base = AppConfig.apiBaseUrl.replacingOccurrences(of: "api/", with: "")
        let link = "\(base)s/v/\(block.schoolId)/\(line.id)"
        return "🧗 Mira \(article) \(kind): «\(line.name)»\(grade)\n"
            + "📍 \(place)\n"
            + "👉 Vela en Cumbre (foto con la línea dibujada):\n\(link)"
    }

    // MARK: - Share sheet

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
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
            pop.permittedArrowDirections = []
        }
        top.present(vc, animated: true)
    }
}
