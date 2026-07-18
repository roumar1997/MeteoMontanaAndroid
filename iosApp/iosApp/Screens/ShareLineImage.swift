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
    private static let green = UIColor(rgb: 0x1FA84E)   // "HECHO" (= verde de grado)

    /// Punto de entrada: compone la imagen (o cae a texto) y presenta el share sheet.
    static func share(block: Block, line: BlockLine, schoolName: String?,
                      tickedIds: Set<String> = [], projectIds: Set<String> = [],
                      sectorName: String? = nil) async {
        if let image = await renderCard(block: block, line: line, schoolName: schoolName,
                                        tickedIds: tickedIds, projectIds: projectIds) {
            let text = shareLineText(block: block, line: line, schoolName: schoolName,
                                     sectorName: sectorName, appWording: true)
            await present([image, text])
        } else {
            await present([shareLineText(block: block, line: line, schoolName: schoolName,
                                         sectorName: sectorName)])
        }
    }

    // MARK: - Render

    private static func renderCard(block: Block, line: BlockLine, schoolName: String?,
                                   tickedIds: Set<String>, projectIds: Set<String>) async -> UIImage? {
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
            drawCard(block: block, line: line, schoolName: schoolName, lines: lines, photo: photo,
                     tickedIds: tickedIds, projectIds: projectIds)
        }
    }

    @MainActor
    private static func drawCard(block: Block, line: BlockLine, schoolName: String?,
                                 lines: [TopoLineVM], photo: UIImage,
                                 tickedIds: Set<String>, projectIds: Set<String>) -> UIImage {
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

            // Nombre de la PIEDRA (serif grande, hasta 2 líneas con elipsis).
            let name = block.name.isEmpty ? line.name : block.name
            let titleFont = serif(66)
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byTruncatingTail
            let titleAttrs: [NSAttributedString.Key: Any] =
                [.font: titleFont, .foregroundColor: ink, .paragraphStyle: para]
            let maxTitleH: CGFloat = 190
            let measured = (name as NSString).boundingRect(
                with: CGSize(width: availW, height: maxTitleH),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: titleAttrs, context: nil).height
            let titleH = min(ceil(measured), maxTitleH)
            (name as NSString).draw(with: CGRect(x: pad, y: 130, width: availW, height: titleH),
                                    options: [.usesLineFragmentOrigin, .usesFontLeading],
                                    attributes: titleAttrs, context: nil)

            // Escuela.
            var listY = 130 + titleH + 12
            if let s = schoolName, !s.isEmpty {
                drawText(s, at: CGRect(x: pad, y: listY, width: availW, height: 48),
                         font: UIFont.systemFont(ofSize: 34), color: inkSoft, kern: 0, align: .left)
                listY += 52
            }

            // ── Lista de líneas ─────────────────────────────────────────────
            // Número (círculo del color del grado, = badge de la foto) · nombre
            // · grado · estado (HECHO / PROYECTO). Se listan TODAS las vías.
            listY += 8
            let maxRows = 8
            let rowH: CGFloat = 56
            for (idx, l) in lines.prefix(maxRows).enumerated() {
                let style = GradeColor.style(l.grade)
                let cx = pad + 22, cyc = listY + 12
                fillCircle(cg, CGPoint(x: cx, y: cyc), 24, .white)
                fillCircle(cg, CGPoint(x: cx, y: cyc), 21, UIColor(style.stroke))
                drawCentered("\(idx + 1)", at: CGPoint(x: cx, y: cyc), size: 30,
                             color: style.dark ? ink : .white)

                // Estado a la derecha (reserva su ancho).
                var rightLimit = w - pad
                let status: (String, UIColor)? =
                    tickedIds.contains(l.id) ? ("HECHO", green)
                    : projectIds.contains(l.id) ? ("PROYECTO", terra) : nil
                if let (label, col) = status {
                    let sFont = mono(30, bold: true)
                    let sw = (label as NSString).size(withAttributes: [.font: sFont, .kern: 1.2]).width
                    drawText(label, at: CGRect(x: w - pad - sw - 6, y: listY, width: sw + 6, height: 48),
                             font: sFont, color: col, kern: 1.2, align: .right)
                    rightLimit = w - pad - sw - 30
                }

                // Nombre + grado (recortado para no pisar el estado).
                let nameFont = UIFont.systemFont(ofSize: 40)
                let gradeFont = UIFont.systemFont(ofSize: 34, weight: .bold)
                let gradeTxt = (l.grade?.isEmpty == false) ? l.grade! : nil
                let gradeW: CGFloat = gradeTxt != nil
                    ? (gradeTxt! as NSString).size(withAttributes: [.font: gradeFont]).width + 18 : 0
                let tx = pad + 56
                let nm = (l.name?.isEmpty == false) ? l.name! : "Vía \(idx + 1)"
                drawText(nm, at: CGRect(x: tx, y: listY, width: rightLimit - tx - gradeW, height: 48),
                         font: nameFont, color: ink, kern: 0, align: .left)
                if let g = gradeTxt {
                    drawText(g, at: CGRect(x: rightLimit - gradeW + 4, y: listY, width: gradeW, height: 48),
                             font: gradeFont, color: inkSoft, kern: 0, align: .left)
                }
                listY += rowH
            }
            if lines.count > maxRows {
                drawText("+\(lines.count - maxRows) vías más",
                         at: CGRect(x: pad + 56, y: listY, width: availW - 56, height: 48),
                         font: UIFont.systemFont(ofSize: 32), color: inkSoft, kern: 0, align: .left)
                listY += rowH
            }

            // ── Foto con las líneas ─────────────────────────────────────────
            let footerTop = h - 130
            let photoTop = listY + 14
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
        // Tramos compartidos entre vías → color propio (espejo de renderTopo).
        let shared = TopoShared.sharedSegmentKeys(lines.map { $0.points })
        for (idx, line) in lines.enumerated() where !line.points.isEmpty {
            let style = GradeColor.style(line.grade)
            let stroke = UIColor(style.stroke)
            let pts = line.points.map {
                CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height)
            }
            for run in TopoShared.splitRuns(line.points, shared: shared) {
                let runPts = run.pts.map {
                    CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height)
                }
                guard runPts.count > 1 else { continue }
                let path = UIBezierPath()
                path.move(to: runPts[0])
                for p in runPts.dropFirst() { path.addLine(to: p) }
                path.lineCapStyle = .round; path.lineJoinStyle = .round
                if style.dashed && !run.isShared { path.setLineDash([10 * s, 8 * s], count: 2, phase: 0) }
                // Línea blanca: contorno negro para que se vea sobre cualquier foto.
                if style.dark && !run.isShared {
                    path.lineWidth = 9 * s
                    UIColor.black.withAlphaComponent(0.8).setStroke(); path.stroke()
                }
                path.lineWidth = 5 * s
                (run.isShared ? UIColor(TopoShared.color) : stroke).setStroke(); path.stroke()
            }

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
        case "SEMI": return "SEM"
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

    /// Texto que acompaña al compartir (imagen o fallback): tipo + nombre de la
    /// vía + grado, y piedra · escuela · sector, más el enlace que abre la app.
    /// `appWording=true` cuando ya va una imagen adjunta (el texto no describe la foto).
    private static func shareLineText(block: Block, line: BlockLine, schoolName: String?,
                                      sectorName: String? = nil, appWording: Bool = false) -> String {
        let isRoute = block.discipline.uppercased() == "ROUTE"
        let kind = isRoute ? "vía" : "bloque"
        let article = isRoute ? "esta" : "este"
        let grade = (line.grade?.isEmpty == false) ? " \(line.grade!)" : ""
        var place = block.name
        if let s = schoolName, !s.isEmpty { place += " · \(s)" }
        if let sec = sectorName, !sec.isEmpty { place += " · \(sec)" }
        let base = AppConfig.apiBaseUrl.replacingOccurrences(of: "api/", with: "")
        let link = "\(base)s/v/\(block.schoolId)/\(line.id)"
        let cta = appWording ? "👉 Míralo en la app:" : "👉 Míralo en Cumbre (foto con la línea dibujada):"
        return "🧗 Mira \(article) \(kind): «\(line.name)»\(grade)\n"
            + "📍 \(place)\n"
            + "\(cta)\n\(link)"
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
