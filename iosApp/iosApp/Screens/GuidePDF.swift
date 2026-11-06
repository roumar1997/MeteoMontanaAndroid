import SwiftUI
import Shared
import PDFKit

/// La GUÍA de una escuela en PDF (A4), generada en el móvil, con el diseño que eligió Álvaro (2026-11-03):
/// portada OSCURA con el satélite entero, el nombre en grande, las cifras y un ÍNDICE de sectores que se puede
/// tocar (salta a su página); páginas CLARAS con la marca Cumbre y el sector en el que estás arriba ("SIGUES EN"
/// si viene de la página anterior), y cada cara de cada piedra en una tarjeta: foto con sus líneas (el mismo
/// pintor que la ficha) y sus vías numeradas (sin casillas de marcar: Álvaro, 2026-11-03).
/// Sale con los MISMOS filtros que la guía en pantalla y funciona sin conexión con la escuela descargada.
enum GuidePDF {

    // MARK: - Medidas y colores

    static let page = CGRect(x: 0, y: 0, width: 595, height: 842)
    static let margin: CGFloat = 32
    static var contentW: CGFloat { page.width - margin * 2 }
    static let bottomLimit: CGFloat = 842 - 46

    static let dark = UIColor(red: 0.078, green: 0.071, blue: 0.059, alpha: 1)      // #14120F
    static let darkCard = UIColor(red: 0.141, green: 0.125, blue: 0.106, alpha: 1)  // #24201B
    static let paper = UIColor(red: 0.969, green: 0.957, blue: 0.933, alpha: 1)     // #F7F4EE
    static let chip = UIColor(red: 0.925, green: 0.898, blue: 0.851, alpha: 1)      // #ECE5D9
    static let terra = UIColor(red: 0.706, green: 0.267, blue: 0.110, alpha: 1)     // #B4441C
    static let accent = UIColor(red: 0.878, green: 0.455, blue: 0.247, alpha: 1)    // #E0743F
    static let muted = UIColor(red: 0.541, green: 0.502, blue: 0.463, alpha: 1)     // #8A8076
    static let mutedDark = UIColor(red: 0.659, green: 0.620, blue: 0.573, alpha: 1) // #A89E92
    static let line = UIColor(red: 0.937, green: 0.918, blue: 0.886, alpha: 1)      // #EFEAE2
    static let box = UIColor(red: 0.812, green: 0.776, blue: 0.722, alpha: 1)       // #CFC6B8

    static func serif(_ s: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Bold", size: s) ?? .systemFont(ofSize: s, weight: .bold)
    }
    static func mono(_ s: CGFloat, bold: Bool = true) -> UIFont {
        UIFont(name: bold ? "JetBrainsMono-Bold" : "JetBrainsMono-Regular", size: s)
            ?? .monospacedSystemFont(ofSize: s, weight: bold ? .bold : .regular)
    }

    // MARK: - Entrada

    /// Genera el PDF y devuelve el fichero. `onlySector` = solo ese sector (su `key`); nil = toda la guía.
    static func make(schoolName: String, guide: GuideView, overview: GuideOverview?, sectorMaps: [String: UIImage] = [:],
                     onlySector: String?) async -> URL? {
        let sectors = guide.sectors.filter { onlySector == nil || $0.key == onlySector }
        guard !sectors.isEmpty else { return nil }

        // Las fotos primero (async); luego se dibuja todo de una vez.
        var photos: [String: UIImage] = [:]
        for s in sectors { for b in s.blocks { for f in b.faces {
            if let p = f.photo, photos[p] == nil, let img = await ImageCache.image(p) { photos[p] = compact(img) }
        } } }

        let safeName = schoolName.replacingOccurrences(of: "/", with: "-")
        let suffix = onlySector.map { " - \(sectors.first?.sector ?? $0)" } ?? ""
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(L("Guía %@%@", safeName, suffix) + ".pdf")

        let maps = sectorMaps.mapValues { compact($0, maxSide: 1100) }
        let input = GuidePDFWriter.Input(school: schoolName, sectors: sectors, overview: overview, photos: photos,
                                 sectorMaps: maps)
        // 1ª pasada en vacío: en qué página empieza cada sector y cuántas hay (el índice y el pie las necesitan).
        var plan = GuidePDFWriter.Plan()
        _ = UIGraphicsPDFRenderer(bounds: page).pdfData { ctx in
            plan = GuidePDFWriter(ctx: ctx, input: input, plan: nil).run()
        }
        var written = GuidePDFWriter.Plan()
        do {
            try UIGraphicsPDFRenderer(bounds: page).writePDF(to: url) { ctx in
                written = GuidePDFWriter(ctx: ctx, input: input, plan: plan).run()
            }
        } catch { return nil }
        addLinks(written, to: url)
        return url
    }

    /// Los enlaces internos (índice → sector, vía → su piedra, marca → índice) como enlaces de página a página
    /// (PDFKit): los abre bien Archivos, la vista previa de iOS, Libros o Acrobat.
    static func addLinks(_ plan: GuidePDFWriter.Plan, to url: URL) {
        guard let doc = PDFDocument(url: url) else { return }
        for l in plan.links {
            guard let target = plan.dests[l.dest],
                  let from = doc.page(at: l.page - 1), let to = doc.page(at: target.page - 1) else { continue }
            // PDF cuenta la altura desde ABAJO; al dibujar la contamos desde arriba.
            let bounds = CGRect(x: l.rect.minX, y: page.height - l.rect.maxY, width: l.rect.width, height: l.rect.height)
            let a = PDFAnnotation(bounds: bounds, forType: .link, withProperties: nil)
            // La barra de arriba de Archivos/Vista previa TAPA lo que cae debajo: se salta 170 pt por encima de la
            // tarjeta. Si eso cae antes del principio de la página, el punto va al final de la página anterior.
            let want = target.y - 170
            let dest: PDFDestination
            if want >= 0 || target.page < 2 {
                dest = PDFDestination(page: to, at: CGPoint(x: 0, y: page.height - max(want, 0)))
            } else if let prev = doc.page(at: target.page - 2) {
                dest = PDFDestination(page: prev, at: CGPoint(x: 0, y: -want))
            } else {
                dest = PDFDestination(page: to, at: CGPoint(x: 0, y: page.height))
            }
            a.action = PDFActionGoTo(destination: dest)
            a.border = PDFBorder()
            a.border?.lineWidth = 0
            from.addAnnotation(a)
        }
        doc.write(to: url)
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

    // MARK: - Piezas de dibujo

    /// Texto que puede ocupar varias líneas; devuelve su alto.
    @discardableResult
    static func text(_ s: String, _ font: UIFont, _ color: UIColor, x: CGFloat, y: CGFloat,
                                 width: CGFloat) -> CGFloat {
        let a = NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color])
        let h = ceil(a.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin], context: nil).height)
        a.draw(with: CGRect(x: x, y: y, width: width, height: h), options: [.usesLineFragmentOrigin], context: nil)
        return h
    }

    static func measure(_ s: String, _ font: UIFont, width: CGFloat) -> CGFloat {
        ceil(NSAttributedString(string: s, attributes: [.font: font])
            .boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                          options: [.usesLineFragmentOrigin], context: nil).height)
    }

    /// Píldora con texto; `at` es su esquina superior izquierda. Devuelve su rectángulo.
    @discardableResult
    static func pill(_ s: String, _ font: UIFont, fg: UIColor, bg: UIColor, at: CGPoint,
                                 padH: CGFloat = 10, padV: CGFloat = 5, radius: CGFloat? = nil,
                                 border: UIColor? = nil) -> CGRect {
        let a = NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: fg])
        let t = a.size()
        let r = CGRect(x: at.x, y: at.y, width: ceil(t.width) + padH * 2, height: ceil(t.height) + padV * 2)
        let path = UIBezierPath(roundedRect: r, cornerRadius: radius ?? r.height / 2)
        bg.setFill(); path.fill()
        if let border { border.setStroke(); path.lineWidth = 1.2; path.stroke() }
        a.draw(at: CGPoint(x: r.minX + padH, y: r.minY + padV))
        return r
    }

    static func pillSize(_ s: String, _ font: UIFont, padH: CGFloat = 10, padV: CGFloat = 5) -> CGSize {
        let t = NSAttributedString(string: s, attributes: [.font: font]).size()
        return CGSize(width: ceil(t.width) + padH * 2, height: ceil(t.height) + padV * 2)
    }

    /// La montaña de la marca (un trazo), en un rectángulo de 20×14 escalado.
    static func mountain(at o: CGPoint, scale: CGFloat, color: UIColor) {
        let p = UIBezierPath()
        let pts: [CGPoint] = [CGPoint(x: 1, y: 13), CGPoint(x: 7, y: 3), CGPoint(x: 10, y: 8),
                              CGPoint(x: 13, y: 4), CGPoint(x: 19, y: 13)]
        p.move(to: CGPoint(x: o.x + pts[0].x * scale, y: o.y + pts[0].y * scale))
        for q in pts.dropFirst() { p.addLine(to: CGPoint(x: o.x + q.x * scale, y: o.y + q.y * scale)) }
        p.close()
        p.lineJoinStyle = .round; p.lineWidth = 1.8
        color.setStroke(); p.stroke()
    }

    /// Píldora de la marca: montaña + CUMBRE (+ escuela en gris). Devuelve su rectángulo.
    @discardableResult
    static func brandPill(at o: CGPoint, school: String?, bg: UIColor) -> CGRect {
        let brand = NSAttributedString(string: "CUMBRE", attributes: [
            .font: mono(8.5), .foregroundColor: UIColor.white, .kern: 2.2
        ])
        let schoolA = school.map {
            NSAttributedString(string: $0.uppercased(), attributes: [
                .font: mono(8, bold: false), .foregroundColor: mutedDark, .kern: 1.2
            ])
        }
        let w = 11 + 15 + 6 + brand.size().width + (schoolA.map { 7 + $0.size().width } ?? 0) + 11
        let r = CGRect(x: o.x, y: o.y, width: ceil(w), height: 22)
        bg.setFill(); UIBezierPath(roundedRect: r, cornerRadius: 11).fill()
        mountain(at: CGPoint(x: r.minX + 11, y: r.minY + 6), scale: 0.75, color: accent)
        var x = r.minX + 11 + 15 + 6
        brand.draw(at: CGPoint(x: x, y: r.midY - brand.size().height / 2))
        x += brand.size().width + 7
        schoolA?.draw(at: CGPoint(x: x, y: r.midY - (schoolA?.size().height ?? 0) / 2))
        return r
    }

    static func sectorTitle(_ s: GuideSector) -> String {
        s.unknownFace ? L("Sin orientación votada") : (s.sector ?? L("Sin sector"))
    }

    static func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let st = feedStartTypeLabel(h.startType) { parts.append(st) }
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - El maquetador (una pasada = un PDF entero)

extension GuidePDF {
    /// Foto reducida y pasada a JPEG antes de meterla en el PDF: las fotos tal cual (y los mapas en PNG) hacían
    /// un PDF de más de 100 MB. Con 1000 px de lado y calidad 0,7 se ve igual de bien en el móvil y al imprimir.
    static func compact(_ img: UIImage, maxSide: CGFloat = 1000) -> UIImage {
        let side = max(img.size.width, img.size.height)
        let scale = min(1, maxSide / max(side, 1))
        let size = CGSize(width: floor(img.size.width * scale), height: floor(img.size.height * scale))
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let small = UIGraphicsImageRenderer(size: size, format: format).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        guard let data = small.jpegData(compressionQuality: 0.7), let jpeg = UIImage(data: data) else { return small }
        return jpeg
    }
}
