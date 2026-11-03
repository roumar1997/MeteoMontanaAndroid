import SwiftUI
import Shared

/// La GUÍA de una escuela en PDF (A4), generada en el móvil: portada con el mapa general y, sector a sector,
/// cada piedra con su foto y sus líneas, y la lista de vías (grado, salida, estrellas, repeticiones y comentario).
/// Sale con los MISMOS filtros que la guía en pantalla. Las fotos salen de la caché del móvil, así que con la
/// escuela descargada se genera también sin conexión.
enum GuidePDF {
    private static let page = CGRect(x: 0, y: 0, width: 595, height: 842)
    private static let margin: CGFloat = 36
    private static var contentWidth: CGFloat { page.width - margin * 2 }
    private static let ink = UIColor(red: 0.10, green: 0.10, blue: 0.10, alpha: 1)
    private static let ink3 = UIColor(red: 0.45, green: 0.45, blue: 0.45, alpha: 1)
    private static let rule = UIColor(red: 0.85, green: 0.85, blue: 0.85, alpha: 1)

    /// Genera el PDF y devuelve el fichero. `onlySector` = solo ese sector (su `key`); nil = toda la guía.
    static func make(schoolName: String, guide: GuideView, overview: GuideOverview?,
                     onlySector: String?) async -> URL? {
        let sectors = guide.sectors.filter { onlySector == nil || $0.key == onlySector }
        guard !sectors.isEmpty else { return nil }

        // Las fotos primero (async); luego se dibuja todo de una vez.
        var photos: [String: UIImage] = [:]
        for s in sectors { for b in s.blocks {
            if let p = b.photoPath, photos[p] == nil, let img = await ImageCache.image(p) { photos[p] = img }
        } }

        let safeName = schoolName.replacingOccurrences(of: "/", with: "-")
        let suffix = onlySector.map { " - \(sectors.first?.sector ?? $0)" } ?? ""
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(L("Guía %@%@", safeName, suffix) + ".pdf")

        let renderer = UIGraphicsPDFRenderer(bounds: page)
        do {
            try renderer.writePDF(to: url) { ctx in
                var y: CGFloat = 0
                func newPage() { ctx.beginPage(); y = margin; footer() }
                func ensure(_ h: CGFloat) { if y + h > page.height - margin - 18 { newPage() } }

                // Portada
                newPage()
                y += draw(schoolName, font: .systemFont(ofSize: 30, weight: .bold), color: ink, at: y)
                let lines = sectors.reduce(0) { $0 + Int($1.lineCount) }
                let stones = sectors.reduce(0) { $0 + $1.blocks.count }
                let subtitle = L("Guía de escalada · %@ vías · %@ piedras · %@ sectores", lines, stones, sectors.count)
                y += draw(subtitle, font: .systemFont(ofSize: 12), color: ink3, at: y + 4) + 16
                if let ov = overview {
                    let h = min(contentWidth * ov.image.size.height / max(ov.image.size.width, 1), 430)
                    let r = CGRect(x: margin, y: y, width: contentWidth, height: h)
                    ov.image.draw(in: r)
                    for s in ov.sectors {
                        drawTag(s.name.uppercased(), center: CGPoint(x: r.minX + s.x * r.width, y: r.minY + s.y * r.height))
                    }
                    y += h + 16
                }
                for s in sectors {
                    ensure(18)
                    y += draw("•  \(sectorTitle(s))  —  " + L("%@ piedras · %@ vías", s.blocks.count, Int(s.lineCount)),
                              font: .systemFont(ofSize: 11), color: ink, at: y) + 3
                }

                // Sector a sector
                for s in sectors {
                    newPage()
                    y += draw(sectorTitle(s), font: .systemFont(ofSize: 22, weight: .bold), color: ink, at: y)
                    y += draw(L("%@ piedras · %@ vías", s.blocks.count, Int(s.lineCount)),
                              font: .systemFont(ofSize: 11), color: ink3, at: y + 2) + 14
                    for b in s.blocks {
                        let photo = b.photoPath.flatMap { photos[$0] }
                        let photoH: CGFloat = photo.map { min(contentWidth * $0.size.height / max($0.size.width, 1), 300) } ?? 0
                        let firstRow = b.lines.first.map { rowHeight($0) } ?? 0
                        ensure(22 + photoH + 8 + firstRow)
                        y += draw(b.blockName, font: .systemFont(ofSize: 14, weight: .semibold), color: ink, at: y) + 4
                        if let photo {
                            let w = photoH * photo.size.width / max(photo.size.height, 1)
                            let r = CGRect(x: margin + (contentWidth - w) / 2, y: y, width: w, height: photoH)
                            photo.draw(in: r)
                            drawLines(b, in: r, cg: ctx.cgContext)
                            y += photoH + 8
                        }
                        for h in b.lines {
                            let rh = rowHeight(h)
                            ensure(rh)
                            drawRow(h, at: y)
                            y += rh
                        }
                        y += 14
                    }
                }
            }
        } catch { return nil }
        return url
    }

    // MARK: - Piezas

    private static func sectorTitle(_ s: GuideSector) -> String {
        s.unknownFace ? L("Sin orientación votada") : (s.sector ?? L("Sin sector"))
    }

    private static func footer() {
        let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 8), .foregroundColor: ink3]
        NSAttributedString(string: L("Guía generada con Cumbre · climbingteams.com"), attributes: attrs)
            .draw(at: CGPoint(x: margin, y: page.height - margin + 6))
    }

    /// Dibuja un texto que puede ocupar varias líneas y devuelve su alto.
    @discardableResult
    private static func draw(_ text: String, font: UIFont, color: UIColor, at y: CGFloat,
                             x: CGFloat = margin, width: CGFloat = contentWidth) -> CGFloat {
        let s = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color])
        let h = ceil(s.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin], context: nil).height)
        s.draw(with: CGRect(x: x, y: y, width: width, height: h), options: [.usesLineFragmentOrigin], context: nil)
        return h
    }

    private static func drawTag(_ text: String, center: CGPoint) {
        let s = NSAttributedString(string: text, attributes: [
            .font: UIFont.monospacedSystemFont(ofSize: 7, weight: .bold), .foregroundColor: ink
        ])
        let t = s.size()
        let r = CGRect(x: center.x - t.width / 2 - 5, y: center.y - t.height / 2 - 3, width: t.width + 10, height: t.height + 6)
        UIColor.white.withAlphaComponent(0.92).setFill()
        UIBezierPath(roundedRect: r, cornerRadius: r.height / 2).fill()
        s.draw(at: CGPoint(x: r.minX + 5, y: r.minY + 3))
    }

    private static func drawLines(_ b: GuideBlock, in r: CGRect, cg: CGContext) {
        for h in b.lines where h.photoPath == b.photoPath {
            let pts = dedupPoints(TopoParse.points(h.linePath))
                .map { CGPoint(x: r.minX + $0.x * r.width, y: r.minY + $0.y * r.height) }
            guard pts.count >= 2 else { continue }
            let path = UIBezierPath()
            path.move(to: pts[0]); pts.dropFirst().forEach { path.addLine(to: $0) }
            path.lineCapStyle = .round; path.lineJoinStyle = .round
            UIColor.black.withAlphaComponent(0.5).setStroke(); path.lineWidth = 4.5; path.stroke()
            UIColor(GradeColor.color(h.grade)).setStroke(); path.lineWidth = 2.5; path.stroke()
        }
    }

    private static let pillW: CGFloat = 40
    private static var textX: CGFloat { margin + pillW + 10 }
    private static var textW: CGFloat { contentWidth - pillW - 10 }

    private static func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let st = feedStartTypeLabel(h.startType) { parts.append(st) }
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        if let n = h.repetitions?.intValue, n > 0 { parts.append("✓ \(n)") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private static func measure(_ text: String, _ font: UIFont) -> CGFloat {
        ceil(NSAttributedString(string: text, attributes: [.font: font])
            .boundingRect(with: CGSize(width: textW, height: .greatestFiniteMagnitude),
                          options: [.usesLineFragmentOrigin], context: nil).height)
    }

    private static func rowHeight(_ h: LineSearchHit) -> CGFloat {
        var total = measure(h.lineName ?? h.blockName, .systemFont(ofSize: 11.5, weight: .semibold))
        if let m = meta(h) { total += 2 + measure(m, .systemFont(ofSize: 9)) }
        if let d = h.lineDescription, !d.isEmpty { total += 2 + measure(d, .italicSystemFont(ofSize: 9.5)) }
        return max(total, 18) + 9
    }

    private static func drawRow(_ h: LineSearchHit, at y0: CGFloat) {
        // Grado en una pastilla de su color (los blancos con borde y texto oscuro).
        let style = GradeColor.style(h.grade)
        let pill = CGRect(x: margin, y: y0 + 1, width: pillW, height: 16)
        let pillPath = UIBezierPath(roundedRect: pill, cornerRadius: 8)
        UIColor(style.stroke).setFill(); pillPath.fill()
        if style.dark { rule.setStroke(); pillPath.lineWidth = 1; pillPath.stroke() }
        let g = NSAttributedString(string: h.grade ?? "?", attributes: [
            .font: UIFont.systemFont(ofSize: 9, weight: .bold), .foregroundColor: style.dark ? ink : UIColor.white
        ])
        let gs = g.size()
        g.draw(at: CGPoint(x: pill.midX - gs.width / 2, y: pill.midY - gs.height / 2))

        var y = y0
        y += draw(h.lineName ?? h.blockName, font: .systemFont(ofSize: 11.5, weight: .semibold), color: ink,
                  at: y, x: textX, width: textW)
        if let m = meta(h) {
            y += 2 + draw(m, font: .systemFont(ofSize: 9), color: ink3, at: y + 2, x: textX, width: textW)
        }
        if let d = h.lineDescription, !d.isEmpty {
            draw(d, font: .italicSystemFont(ofSize: 9.5), color: ink, at: y + 2, x: textX, width: textW)
        }
        // Raya fina debajo de la fila.
        let lineY = y0 + rowHeight(h) - 4
        rule.setStroke()
        let sep = UIBezierPath(); sep.move(to: CGPoint(x: textX, y: lineY)); sep.addLine(to: CGPoint(x: page.width - margin, y: lineY))
        sep.lineWidth = 0.5; sep.stroke()
    }

    /// Abre la hoja de compartir del sistema (Guardar en Archivos, WhatsApp, imprimir…).
    @MainActor
    static func share(_ url: URL) {
        guard let scene = UIApplication.shared.connectedScenes
                .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if let pop = vc.popoverPresentationController {
            pop.sourceView = top.view
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        }
        top.present(vc, animated: true)
    }
}
