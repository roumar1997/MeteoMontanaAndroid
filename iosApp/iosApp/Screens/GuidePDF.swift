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

    fileprivate static let page = CGRect(x: 0, y: 0, width: 595, height: 842)
    fileprivate static let margin: CGFloat = 32
    fileprivate static var contentW: CGFloat { page.width - margin * 2 }
    fileprivate static let bottomLimit: CGFloat = 842 - 46

    fileprivate static let dark = UIColor(red: 0.078, green: 0.071, blue: 0.059, alpha: 1)      // #14120F
    fileprivate static let darkCard = UIColor(red: 0.141, green: 0.125, blue: 0.106, alpha: 1)  // #24201B
    fileprivate static let paper = UIColor(red: 0.969, green: 0.957, blue: 0.933, alpha: 1)     // #F7F4EE
    fileprivate static let chip = UIColor(red: 0.925, green: 0.898, blue: 0.851, alpha: 1)      // #ECE5D9
    fileprivate static let terra = UIColor(red: 0.706, green: 0.267, blue: 0.110, alpha: 1)     // #B4441C
    fileprivate static let accent = UIColor(red: 0.878, green: 0.455, blue: 0.247, alpha: 1)    // #E0743F
    fileprivate static let muted = UIColor(red: 0.541, green: 0.502, blue: 0.463, alpha: 1)     // #8A8076
    fileprivate static let mutedDark = UIColor(red: 0.659, green: 0.620, blue: 0.573, alpha: 1) // #A89E92
    fileprivate static let line = UIColor(red: 0.937, green: 0.918, blue: 0.886, alpha: 1)      // #EFEAE2
    fileprivate static let box = UIColor(red: 0.812, green: 0.776, blue: 0.722, alpha: 1)       // #CFC6B8

    fileprivate static func serif(_ s: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Bold", size: s) ?? .systemFont(ofSize: s, weight: .bold)
    }
    fileprivate static func mono(_ s: CGFloat, bold: Bool = true) -> UIFont {
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
        let input = Writer.Input(school: schoolName, sectors: sectors, overview: overview, photos: photos,
                                 sectorMaps: maps)
        // 1ª pasada en vacío: en qué página empieza cada sector y cuántas hay (el índice y el pie las necesitan).
        var plan = Writer.Plan()
        _ = UIGraphicsPDFRenderer(bounds: page).pdfData { ctx in
            plan = Writer(ctx: ctx, input: input, plan: nil).run()
        }
        var written = Writer.Plan()
        do {
            try UIGraphicsPDFRenderer(bounds: page).writePDF(to: url) { ctx in
                written = Writer(ctx: ctx, input: input, plan: plan).run()
            }
        } catch { return nil }
        addLinks(written, to: url)
        return url
    }

    /// Los enlaces internos (índice → sector, vía → su piedra, marca → índice) como enlaces de página a página
    /// (PDFKit): los abre bien Archivos, la vista previa de iOS, Libros o Acrobat.
    private static func addLinks(_ plan: Writer.Plan, to url: URL) {
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
    fileprivate static func text(_ s: String, _ font: UIFont, _ color: UIColor, x: CGFloat, y: CGFloat,
                                 width: CGFloat) -> CGFloat {
        let a = NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color])
        let h = ceil(a.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin], context: nil).height)
        a.draw(with: CGRect(x: x, y: y, width: width, height: h), options: [.usesLineFragmentOrigin], context: nil)
        return h
    }

    fileprivate static func measure(_ s: String, _ font: UIFont, width: CGFloat) -> CGFloat {
        ceil(NSAttributedString(string: s, attributes: [.font: font])
            .boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                          options: [.usesLineFragmentOrigin], context: nil).height)
    }

    /// Píldora con texto; `at` es su esquina superior izquierda. Devuelve su rectángulo.
    @discardableResult
    fileprivate static func pill(_ s: String, _ font: UIFont, fg: UIColor, bg: UIColor, at: CGPoint,
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

    fileprivate static func pillSize(_ s: String, _ font: UIFont, padH: CGFloat = 10, padV: CGFloat = 5) -> CGSize {
        let t = NSAttributedString(string: s, attributes: [.font: font]).size()
        return CGSize(width: ceil(t.width) + padH * 2, height: ceil(t.height) + padV * 2)
    }

    /// La montaña de la marca (un trazo), en un rectángulo de 20×14 escalado.
    fileprivate static func mountain(at o: CGPoint, scale: CGFloat, color: UIColor) {
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
    fileprivate static func brandPill(at o: CGPoint, school: String?, bg: UIColor) -> CGRect {
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

    fileprivate static func sectorTitle(_ s: GuideSector) -> String {
        s.unknownFace ? L("Sin orientación votada") : (s.sector ?? L("Sin sector"))
    }

    fileprivate static func meta(_ h: LineSearchHit) -> String? {
        var parts: [String] = []
        if let st = feedStartTypeLabel(h.startType) { parts.append(st) }
        if let avg = h.rating?.doubleValue, (h.ratingCount?.intValue ?? 0) > 0 { parts.append("★ \(String(format: "%.1f", avg))") }
        if let r = CommunityBadgesRow.text(h) { parts.append(r) }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - El maquetador (una pasada = un PDF entero)

private final class Writer {
    struct Input {
        let school: String
        let sectors: [GuideSector]
        let overview: GuideOverview?
        let photos: [String: UIImage]
        /// Foto aérea de cada sector (clave → imagen): la portada del sector. Sin ella, la foto de una piedra.
        let sectorMaps: [String: UIImage]
    }
    /// Lo que la 1ª pasada averigua para la 2ª.
    struct Plan {
        var sectorPage: [Int: Int] = [:]
        var total = 0
        var links: [Link] = []
        var dests: [String: (page: Int, y: CGFloat)] = [:]
    }

    typealias P = GuidePDF
    let ctx: UIGraphicsPDFRendererContext
    let input: Input
    let known: Plan?
    var plan = Plan()
    var pageNo = 0
    var y: CGFloat = 0
    /// Sector de la página actual (para la cabecera y el pie).
    var currentSector = ""
    /// Índice del sector de la página actual: la píldora del sector y «INICIO DEL SECTOR» saltan a su primera página.
    var currentSectorIndex = 0
    /// Enlaces internos: se apuntan aquí y se añaden al final con PDFKit (página → página), que es lo que
    /// entienden todos los visores. Los enlaces "con nombre" de UIKit no funcionaban en Archivos.
    struct Link { let page: Int; let rect: CGRect; let dest: String }
    var links: [Link] = []
    var dests: [String: (page: Int, y: CGFloat)] = [:]
    private func link(_ dest: String, _ rect: CGRect) { links.append(Link(page: pageNo, rect: rect, dest: dest)) }
    private func dest(_ name: String, y: CGFloat) { dests[name] = (pageNo, y) }

    init(ctx: UIGraphicsPDFRendererContext, input: Input, plan: Plan?) {
        self.ctx = ctx; self.input = input; self.known = plan
    }

    private var cg: CGContext { ctx.cgContext }

    func run() -> Plan {
        cover()
        for (i, s) in input.sectors.enumerated() { sector(s, index: i) }
        plan.total = pageNo
        plan.links = links
        plan.dests = dests
        return plan
    }

    // MARK: Portada

    private func cover() {
        ctx.beginPage(); pageNo += 1
        dest("indice", y: 0)
        P.dark.setFill(); UIRectFill(P.page)

        // El satélite ENTERO, a todo lo ancho (sin recortar: se ven todos los parkings aunque estén lejos).
        var top: CGFloat = 64
        if let ov = input.overview {
            let h = min(P.page.width * ov.image.size.height / max(ov.image.size.width, 1), 470)
            let w = h * ov.image.size.width / max(ov.image.size.height, 1)
            let r = CGRect(x: (P.page.width - w) / 2, y: 0, width: w, height: h)
            GuidePDF.compact(ov.image, maxSide: 1400).draw(in: r)
            for s in ov.sectors {
                let t = s.name.uppercased()
                let size = P.pillSize(t, P.mono(6.5), padH: 5, padV: 2.5)
                P.pill(t, P.mono(6.5), fg: P.dark, bg: UIColor.white.withAlphaComponent(0.88),
                       at: CGPoint(x: r.minX + s.x * r.width - size.width / 2, y: r.minY + s.y * r.height - size.height / 2),
                       padH: 5, padV: 2.5)
            }
            top = h
        }
        P.brandPill(at: CGPoint(x: 20, y: 20), school: nil, bg: P.dark.withAlphaComponent(0.85))
        let tag = L("GUÍA DE ESCALADA")
        let ts = P.pillSize(tag, P.mono(8))
        P.pill(tag, P.mono(8), fg: P.dark, bg: P.paper, at: CGPoint(x: P.page.width - 20 - ts.width, y: 20))

        y = top + 24
        y += P.text(input.school, P.serif(54), .white, x: P.margin, y: y, width: P.contentW) + 16

        // Cifras en tres tarjetas.
        let lines = input.sectors.reduce(0) { $0 + Int($1.lineCount) }
        let stones = input.sectors.reduce(0) { $0 + $1.blocks.count }
        let stats: [(String, String)] = [("\(lines)", L("VÍAS")), ("\(stones)", L("PIEDRAS")),
                                         ("\(input.sectors.count)", L("SECTORES"))]
        let cardW = (P.contentW - 20) / 3
        for (i, st) in stats.enumerated() {
            let r = CGRect(x: P.margin + CGFloat(i) * (cardW + 10), y: y, width: cardW, height: 54)
            P.darkCard.setFill(); UIBezierPath(roundedRect: r, cornerRadius: 14).fill()
            P.text(st.0, .systemFont(ofSize: 24, weight: .bold), .white, x: r.minX + 14, y: r.minY + 8, width: cardW - 20)
            P.text(st.1, P.mono(7.5), P.mutedDark, x: r.minX + 14, y: r.minY + 37, width: cardW - 20)
        }
        y += 54 + 20

        // Índice: cada sector es una píldora que SALTA a su página.
        P.text(L("ÍNDICE · TOCA UN SECTOR"), P.mono(8), P.accent, x: P.margin, y: y, width: P.contentW)
        y += 16
        var x = P.margin
        for (i, s) in input.sectors.enumerated() {
            let name = P.sectorTitle(s)
            let pageText = known?.sectorPage[i].map { "  p.\($0)" } ?? "  p.00"
            let a = NSMutableAttributedString(string: name, attributes: [
                .font: UIFont.systemFont(ofSize: 9.5, weight: .semibold), .foregroundColor: P.dark
            ])
            a.append(NSAttributedString(string: pageText, attributes: [.font: P.mono(8.5), .foregroundColor: P.terra]))
            let size = CGSize(width: ceil(a.size().width) + 20, height: ceil(a.size().height) + 10)
            if x + size.width > P.page.width - P.margin { x = P.margin; y += size.height + 6 }
            let r = CGRect(origin: CGPoint(x: x, y: y), size: size)
            P.paper.setFill(); UIBezierPath(roundedRect: r, cornerRadius: r.height / 2).fill()
            a.draw(at: CGPoint(x: r.minX + 10, y: r.minY + 5))
            link("s\(i)", r)
            x += size.width + 6
        }

        let date = DateFormatter.localizedString(from: Date(), dateStyle: .medium, timeStyle: .none)
        P.text("CLIMBINGTEAMS.COM · \(date.uppercased())", P.mono(7, bold: false), P.mutedDark,
               x: P.margin, y: P.page.height - 26, width: 300)
        let right = L("DESCARGADA DESDE LA APP CUMBRE")
        let rw = NSAttributedString(string: right, attributes: [.font: P.mono(7, bold: false)]).size().width
        P.text(right, P.mono(7, bold: false), P.mutedDark, x: P.page.width - P.margin - rw - 2,
               y: P.page.height - 26, width: rw + 4)
    }

    // MARK: Páginas claras

    /// Página nueva con cabecera (marca + sector) y pie (sector · página / total).
    private func lightPage(continuing: Bool) {
        ctx.beginPage(); pageNo += 1
        P.paper.setFill(); UIRectFill(P.page)
        let brand = P.brandPill(at: CGPoint(x: P.margin, y: 18), school: input.school, bg: P.dark)
        link("indice", brand)   // la marca vuelve al índice

        let sectorPill = P.pillSize(currentSector, .systemFont(ofSize: 9.5, weight: .bold), padH: 11, padV: 5)
        let px = P.page.width - P.margin - sectorPill.width
        P.pill(currentSector, .systemFont(ofSize: 9.5, weight: .bold), fg: .white, bg: P.terra,
               at: CGPoint(x: px, y: 18), padH: 11, padV: 5)
        link("s\(currentSectorIndex)", CGRect(origin: CGPoint(x: px, y: 18), size: sectorPill))
        let label = continuing ? L("SIGUES EN") : L("SECTOR")
        let lw = NSAttributedString(string: label, attributes: [.font: P.mono(7.5, bold: false)]).size().width
        P.text(label, P.mono(7.5, bold: false), P.muted, x: px - lw - 8, y: 18 + (sectorPill.height - 10) / 2, width: lw + 2)

        // Pie
        // Botones de volver (en todas las páginas): al índice de la portada y al inicio del sector.
        let backY = P.page.height - 34
        let toIndex = L("← ÍNDICE")
        let ti = P.pillSize(toIndex, P.mono(7.5), padH: 10, padV: 5)
        P.pill(toIndex, P.mono(7.5), fg: .white, bg: P.dark, at: CGPoint(x: P.margin, y: backY), padH: 10, padV: 5)
        link("indice", CGRect(origin: CGPoint(x: P.margin, y: backY), size: ti))
        let toSector = L("↑ INICIO DEL SECTOR")
        let tsz = P.pillSize(toSector, P.mono(7.5), padH: 10, padV: 5)
        let sx = P.margin + ti.width + 6
        P.pill(toSector, P.mono(7.5), fg: .white, bg: P.terra, at: CGPoint(x: sx, y: backY), padH: 10, padV: 5)
        link("s\(currentSectorIndex)", CGRect(origin: CGPoint(x: sx, y: backY), size: tsz))
        let total = known.map { "\($0.total)" } ?? "00"
        let foot = "\(currentSector.uppercased()) · \(pageNo) / \(total)"
        let fs = P.pillSize(foot, P.mono(7), padH: 9, padV: 4)
        P.pill(foot, P.mono(7), fg: P.dark, bg: P.chip, at: CGPoint(x: P.page.width - P.margin - fs.width, y: P.page.height - 32),
               padH: 9, padV: 4)
        y = 56
    }

    private func sector(_ s: GuideSector, index: Int) {
        currentSector = P.sectorTitle(s)
        currentSectorIndex = index
        lightPage(continuing: false)
        plan.sectorPage[index] = pageNo
        dest("s\(index)", y: 0)

        y += 8
        let eyebrow = String(format: "%02d / %02d", index + 1, input.sectors.count)
        P.text(eyebrow, P.mono(9), P.terra, x: P.margin, y: y, width: 120)
        y += 14
        // Cifras del sector, a la derecha del título.
        let chips = [L("%@ piedras", s.blocks.count), L("%@ vías", Int(s.lineCount))]
        var cx = P.page.width - P.margin
        for c in chips.reversed() {
            let size = P.pillSize(c, .systemFont(ofSize: 9.5, weight: .semibold), padH: 10, padV: 6)
            cx -= size.width
            P.pill(c, .systemFont(ofSize: 9.5, weight: .semibold), fg: P.dark, bg: P.chip,
                   at: CGPoint(x: cx, y: y + 10), padH: 10, padV: 6, radius: 10)
            cx -= 6
        }
        y += P.text(currentSector, P.serif(40), P.dark, x: P.margin, y: y, width: cx - P.margin - 10) + 18

        sectorHero(s)
        sectorIndex(s)

        // Las piedras empiezan en página nueva, después del resumen del sector.
        lightPage(continuing: true)
        for b in s.blocks {
            let faces = b.faces
            for (fi, face) in faces.enumerated() {
                faceCard(block: b, face: face, faceIndex: fi, faceCount: faces.count)
            }
        }
    }

    // MARK: Inicio de sector: foto + todas sus vías por grado

    /// Una foto del sector (la de su primera piedra con foto), ancha y recortada, sin líneas.
    private func sectorHero(_ s: GuideSector) {
        // Primero la foto aérea del sector (satélite acercado con sus piedras); si no hay, la de una piedra.
        if let map = input.sectorMaps[s.key] {
            let r = CGRect(x: P.margin, y: y, width: P.contentW, height: P.contentW * map.size.height / max(map.size.width, 1))
            cg.saveGState()
            UIBezierPath(roundedRect: r, cornerRadius: 16).addClip()
            map.draw(in: r)
            cg.restoreGState()
            y += r.height + 20
            return
        }
        guard let path = s.blocks.lazy.flatMap({ $0.faces }).compactMap({ $0.photo }).first(where: { input.photos[$0] != nil }),
              let img = input.photos[path] else { return }
        let r = CGRect(x: P.margin, y: y, width: P.contentW, height: 190)
        cg.saveGState()
        UIBezierPath(roundedRect: r, cornerRadius: 16).addClip()
        // Rellena el marco (como "cover"): escala por el lado que más falte y centra.
        let scale = max(r.width / img.size.width, r.height / img.size.height)
        let w = img.size.width * scale, h = img.size.height * scale
        img.draw(in: CGRect(x: r.midX - w / 2, y: r.midY - h / 2, width: w, height: h))
        cg.restoreGState()
        y += r.height + 20
    }

    /// TODAS las vías del sector agrupadas por grado (de fácil a difícil), cada grupo con su color. Cada vía es
    /// una etiqueta que SALTA a la tarjeta de su piedra.
    private func sectorIndex(_ s: GuideSector) {
        P.text(L("TODAS LAS VÍAS · TOCA UNA PARA IR A SU PIEDRA"), P.mono(8), P.terra, x: P.margin, y: y, width: P.contentW)
        y += 18
        var byGrade: [String: [(GuideBlock, LineSearchHit)]] = [:]
        for b in s.blocks { for h in b.lines { byGrade[h.grade ?? "?", default: []].append((b, h)) } }
        let grades = byGrade.keys.sorted { GradeOrder.score($0) < GradeOrder.score($1) }

        let labelW: CGFloat = 50
        let chipFont = UIFont.systemFont(ofSize: 9, weight: .semibold)
        let stoneFont = P.mono(7.5, bold: false)
        for g in grades {
            let style = GradeColor.style(g)
            let color = UIColor(style.stroke)
            // Altura que ocupará el grupo (para no partirlo entre páginas si cabe entero en la siguiente).
            var x = P.margin + labelW, rows = 1
            for (b, h) in byGrade[g] ?? [] {
                let w = chipWidth(h.lineName ?? b.blockName, stone: b.blockName, chipFont, stoneFont)
                if x + w > P.page.width - P.margin { x = P.margin + labelW; rows += 1 }
                x += w + 5
            }
            let groupH = CGFloat(rows) * 25 + 8
            if y + min(groupH, 120) > P.bottomLimit { lightPage(continuing: true) }

            // Grado: pastilla de su color a la izquierda.
            let gp = CGRect(x: P.margin, y: y + 1, width: 40, height: 20)
            let gpath = UIBezierPath(roundedRect: gp, cornerRadius: 8)
            (style.dark ? UIColor.white : color).setFill(); gpath.fill()
            if style.dark { P.box.setStroke(); gpath.lineWidth = 1.2; gpath.stroke() }
            let ga = NSAttributedString(string: g, attributes: [
                .font: UIFont.systemFont(ofSize: 10, weight: .bold), .foregroundColor: style.dark ? P.dark : UIColor.white
            ])
            ga.draw(at: CGPoint(x: gp.midX - ga.size().width / 2, y: gp.midY - ga.size().height / 2))

            // Sus vías: etiquetas blancas con un punto del color del grado y la piedra en gris.
            x = P.margin + labelW
            for (b, h) in byGrade[g] ?? [] {
                let name = h.lineName ?? b.blockName
                let w = chipWidth(name, stone: b.blockName, chipFont, stoneFont)
                if x + w > P.page.width - P.margin {
                    x = P.margin + labelW; y += 25
                    if y + 22 > P.bottomLimit { lightPage(continuing: true); x = P.margin + labelW }
                }
                let r = CGRect(x: x, y: y, width: w, height: 22)
                let path = UIBezierPath(roundedRect: r, cornerRadius: 11)
                UIColor.white.setFill(); path.fill()
                P.line.setStroke(); path.lineWidth = 1; path.stroke()
                let dot = CGRect(x: r.minX + 8, y: r.midY - 3.5, width: 7, height: 7)
                color.setFill(); UIBezierPath(ovalIn: dot).fill()
                if style.dark { P.box.setStroke(); let d = UIBezierPath(ovalIn: dot); d.lineWidth = 0.8; d.stroke() }
                let na = NSAttributedString(string: name, attributes: [.font: chipFont, .foregroundColor: P.dark])
                na.draw(at: CGPoint(x: dot.maxX + 5, y: r.midY - na.size().height / 2))
                let sa = NSAttributedString(string: b.blockName, attributes: [.font: stoneFont, .foregroundColor: P.muted])
                sa.draw(at: CGPoint(x: dot.maxX + 5 + na.size().width + 5, y: r.midY - sa.size().height / 2))
                link(Writer.blockDest(b.blockId), r)
                x += w + 5
            }
            y += 25 + 8
        }
    }

    private func chipWidth(_ name: String, stone: String, _ f: UIFont, _ sf: UIFont) -> CGFloat {
        let n = NSAttributedString(string: name, attributes: [.font: f]).size().width
        let s = NSAttributedString(string: stone, attributes: [.font: sf]).size().width
        return ceil(8 + 7 + 5 + n + 5 + s + 10)
    }

    /// Nombre de destino del PDF para la tarjeta de una piedra.
    static func blockDest(_ blockId: String) -> String {
        "b" + blockId.filter { $0.isLetter || $0.isNumber }
    }


    /// Página nueva a mitad de sector, recordando de qué piedra viene.
    private func continuePage(from block: GuideBlock, startedAt page: Int) {
        lightPage(continuing: true)
        y += 6
        P.terra.setFill()
        UIBezierPath(ovalIn: CGRect(x: P.margin, y: y + 4, width: 6, height: 6)).fill()
        P.text(L("Piedra %@ · viene de la página %@", block.blockName, page), .systemFont(ofSize: 10), P.muted,
               x: P.margin + 12, y: y, width: P.contentW - 12)
        y += 22
    }

    // MARK: Tarjeta de una cara

    private let photoMaxH: CGFloat = 330
    private let bandH: CGFloat = 40

    private func rowH(_ h: LineSearchHit) -> CGFloat {
        let w = rowTextW
        var t = P.measure(h.lineName ?? h.blockName, .systemFont(ofSize: 12, weight: .semibold), width: w)
        if let m = P.meta(h) { t += 2 + P.measure(m, .systemFont(ofSize: 9), width: w) }
        if let d = h.lineDescription, !d.isEmpty { t += 2 + P.measure(d, .italicSystemFont(ofSize: 9.5), width: w) }
        return max(t, 20) + 18
    }
    private var innerX: CGFloat { P.margin + 16 }
    private var innerW: CGFloat { P.contentW - 32 }
    private var rowTextX: CGFloat { innerX + 84 }
    private var rowTextW: CGFloat { innerW - 84 }

    private func faceCard(block b: GuideBlock, face: GuideFace, faceIndex: Int, faceCount: Int) {
        let photo = face.photo.flatMap { input.photos[$0] }
        let photoH: CGFloat = photo.map { min(P.contentW * $0.size.height / max($0.size.width, 1), photoMaxH) } ?? bandH
        let rows = face.lines.map(rowH)
        let startPage = pageNo

        // Cabe la foto y al menos una vía; si no, página nueva.
        if y + photoH + 12 + (rows.first ?? 0) > P.bottomLimit { continuePage(from: b, startedAt: startPage) }
        // Aquí llegan los enlaces del listado del inicio del sector.
        if faceIndex == 0 { dest(Writer.blockDest(b.blockId), y: y) }

        var idx = 0
        var withPhoto = true
        while withPhoto || idx < rows.count {
            let topH = withPhoto ? photoH : bandH
            var h = topH + 8
            var n = 0
            while idx + n < rows.count && y + h + rows[idx + n] + 4 <= P.bottomLimit { h += rows[idx + n]; n += 1 }
            if n == 0 && idx < rows.count { n = 1; h += rows[idx] }   // siempre se avanza
            h += 4

            let card = CGRect(x: P.margin, y: y, width: P.contentW, height: h)
            UIColor.white.setFill(); UIBezierPath(roundedRect: card, cornerRadius: 16).fill()

            // Cabeza: la foto con sus líneas (recortada a la tarjeta) o una banda clara.
            let head = CGRect(x: card.minX, y: card.minY, width: card.width, height: topH)
            if withPhoto, let photo {
                cg.saveGState()
                UIBezierPath(roundedRect: head, byRoundingCorners: [.topLeft, .topRight],
                             cornerRadii: CGSize(width: 16, height: 16)).addClip()
                P.darkCard.setFill(); UIRectFill(head)
                let w = photoH * photo.size.width / max(photo.size.height, 1)
                let r = CGRect(x: head.midX - w / 2, y: head.minY, width: w, height: photoH)
                photo.draw(in: r)
                cg.translateBy(x: r.minX, y: r.minY)
                TopoPainter.paint(CGContextTarget(cg: cg), vias: face.topoVias, size: r.size,
                                  style: .share(scale: r.width / 360.0, badgeOuter: 9, badgeInner: 7.2,
                                                badgeText: 9.5, startText: 7, fanStart: 9 * 2 + 4, fanEnd: 10.5 * 2 + 4))
                cg.restoreGState()
            }
            let stone = L("PIEDRA %@", b.blockName.uppercased())
            let p1 = P.pill(stone, P.mono(8.5), fg: .white, bg: P.dark.withAlphaComponent(0.88),
                            at: CGPoint(x: head.minX + 12, y: head.minY + 10))
            if faceCount > 1 {
                let caraText = withPhoto ? L("Cara %@ de %@", faceIndex + 1, faceCount)
                                         : L("Cara %@ de %@ · sigue", faceIndex + 1, faceCount)
                P.pill(caraText, .systemFont(ofSize: 8.5, weight: .semibold), fg: P.dark,
                       bg: UIColor.white.withAlphaComponent(0.9), at: CGPoint(x: p1.maxX + 6, y: p1.minY),
                       border: withPhoto ? nil : P.line)
            }

            var ry = card.minY + topH + 4
            for k in 0..<n {
                let hit = face.lines[idx + k]
                drawRow(hit, number: idx + k + 1, y: ry, height: rows[idx + k], last: k == n - 1)
                ry += rows[idx + k]
            }
            y += h + 14
            idx += n
            withPhoto = false
            if idx < rows.count { continuePage(from: b, startedAt: startPage) }
            else { break }
        }
    }

    private func drawRow(_ h: LineSearchHit, number: Int, y ry: CGFloat, height: CGFloat, last: Bool) {
        let style = GradeColor.style(h.grade)
        let chipStyle = GradeColor.chipStyle(h.grade)
        // Número (el mismo que la línea en la foto).
        let c = CGRect(x: innerX, y: ry + 9, width: 20, height: 20)
        UIColor(GradeColor.chip(h.grade)).setFill(); UIBezierPath(ovalIn: c).fill()
        if chipStyle.dark { P.dark.setStroke(); let o = UIBezierPath(ovalIn: c.insetBy(dx: 0.6, dy: 0.6)); o.lineWidth = 1.2; o.stroke() }
        let num = NSAttributedString(string: "\(number)", attributes: [
            .font: UIFont.systemFont(ofSize: 9.5, weight: .bold), .foregroundColor: chipStyle.dark ? P.dark : UIColor.white
        ])
        num.draw(at: CGPoint(x: c.midX - num.size().width / 2, y: c.midY - num.size().height / 2))
        // Grado.
        let g = CGRect(x: innerX + 30, y: ry + 10, width: 44, height: 18)
        let gp = UIBezierPath(roundedRect: g, cornerRadius: 8)
        (style.dark ? UIColor.white : UIColor(style.stroke)).setFill(); gp.fill()
        if style.dark { P.box.setStroke(); gp.lineWidth = 1.2; gp.stroke() }
        let ga = NSAttributedString(string: h.grade ?? "?", attributes: [
            .font: UIFont.systemFont(ofSize: 9.5, weight: .bold), .foregroundColor: style.dark ? P.dark : UIColor.white
        ])
        ga.draw(at: CGPoint(x: g.midX - ga.size().width / 2, y: g.midY - ga.size().height / 2))
        // Nombre, salida/estrellas/repeticiones y comentario.
        var ty = ry + 9
        ty += P.text(h.lineName ?? h.blockName, .systemFont(ofSize: 12, weight: .semibold), P.dark,
                     x: rowTextX, y: ty, width: rowTextW)
        if let m = P.meta(h) {
            ty += 2 + P.text(m, .systemFont(ofSize: 9), P.muted, x: rowTextX, y: ty + 2, width: rowTextW)
        }
        if let d = h.lineDescription, !d.isEmpty {
            P.text(d, .italicSystemFont(ofSize: 9.5), UIColor(red: 0.29, green: 0.26, blue: 0.23, alpha: 1),
                   x: rowTextX, y: ty + 2, width: rowTextW)
        }
        if !last {
            P.line.setStroke()
            let sep = UIBezierPath()
            sep.move(to: CGPoint(x: innerX, y: ry + height - 0.5)); sep.addLine(to: CGPoint(x: innerX + innerW, y: ry + height - 0.5))
            sep.lineWidth = 1; sep.stroke()
        }
    }
}

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
