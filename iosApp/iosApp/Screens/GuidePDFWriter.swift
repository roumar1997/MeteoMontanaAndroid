import SwiftUI
import Shared
import PDFKit

// Maquetador del PDF de la guía: dibuja cada página.

final class GuidePDFWriter {
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
    func link(_ dest: String, _ rect: CGRect) { links.append(Link(page: pageNo, rect: rect, dest: dest)) }
    func dest(_ name: String, y: CGFloat) { dests[name] = (pageNo, y) }

    init(ctx: UIGraphicsPDFRendererContext, input: Input, plan: Plan?) {
        self.ctx = ctx; self.input = input; self.known = plan
    }

    var cg: CGContext { ctx.cgContext }

    func run() -> Plan {
        cover()
        for (i, s) in input.sectors.enumerated() { sector(s, index: i) }
        plan.total = pageNo
        plan.links = links
        plan.dests = dests
        return plan
    }

    // MARK: Portada

    func cover() {
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
    func lightPage(continuing: Bool) {
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

    func sector(_ s: GuideSector, index: Int) {
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

        // Las piedras siguen en el hueco que deje el resumen: cada tarjeta salta de página solo si no cabe.
        y += 4
        for b in s.blocks {
            let faces = b.faces
            for (fi, face) in faces.enumerated() {
                faceCard(block: b, face: face, faceIndex: fi, faceCount: faces.count)
            }
        }
    }

    // MARK: Inicio de sector: foto + todas sus vías por grado

    /// Una foto del sector (la de su primera piedra con foto), ancha y recortada, sin líneas.
    func sectorHero(_ s: GuideSector) {
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
    func sectorIndex(_ s: GuideSector) {
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
                link(GuidePDFWriter.blockDest(b.blockId), r)
                x += w + 5
            }
            y += 25 + 8
        }
    }

    func chipWidth(_ name: String, stone: String, _ f: UIFont, _ sf: UIFont) -> CGFloat {
        let n = NSAttributedString(string: name, attributes: [.font: f]).size().width
        let s = NSAttributedString(string: stone, attributes: [.font: sf]).size().width
        return ceil(8 + 7 + 5 + n + 5 + s + 10)
    }

    /// Nombre de destino del PDF para la tarjeta de una piedra.
    static func blockDest(_ blockId: String) -> String {
        "b" + blockId.filter { $0.isLetter || $0.isNumber }
    }


    /// Página nueva a mitad de sector, recordando de qué piedra viene.
    func continuePage(from block: GuideBlock, startedAt page: Int) {
        lightPage(continuing: true)
        y += 6
        P.terra.setFill()
        UIBezierPath(ovalIn: CGRect(x: P.margin, y: y + 4, width: 6, height: 6)).fill()
        P.text(L("Piedra %@ · viene de la página %@", block.blockName, page), .systemFont(ofSize: 10), P.muted,
               x: P.margin + 12, y: y, width: P.contentW - 12)
        y += 22
    }

    // MARK: Tarjeta de una cara

    let photoMaxH: CGFloat = 330
    let bandH: CGFloat = 40

    func rowH(_ h: LineSearchHit, textW: CGFloat? = nil) -> CGFloat {
        let w = textW ?? rowTextW
        var t = P.measure(h.lineName ?? h.blockName, .systemFont(ofSize: 12, weight: .semibold), width: w)
        if let m = P.meta(h) { t += 2 + P.measure(m, .systemFont(ofSize: 9), width: w) }
        if let d = h.lineDescription, !d.isEmpty { t += 2 + P.measure(d, .italicSystemFont(ofSize: 9.5), width: w) }
        return max(t, 20) + 18
    }
    var innerX: CGFloat { P.margin + 16 }
    var innerW: CGFloat { P.contentW - 32 }
    var rowTextX: CGFloat { innerX + 84 }
    var rowTextW: CGFloat { innerW - 84 }

    func faceCard(block b: GuideBlock, face: GuideFace, faceIndex: Int, faceCount: Int) {
        let photo = face.photo.flatMap { input.photos[$0] }
        if let photo, photo.size.height > photo.size.width * 1.05,
           sideCard(block: b, face: face, photo: photo, faceIndex: faceIndex, faceCount: faceCount) { return }
        let photoH: CGFloat = photo.map { min(P.contentW * $0.size.height / max($0.size.width, 1), photoMaxH) } ?? bandH
        let rows = face.lines.map { rowH($0) }
        let startPage = pageNo

        // Cabe la foto y al menos una vía; si no, página nueva.
        if y + photoH + 12 + (rows.first ?? 0) > P.bottomLimit { continuePage(from: b, startedAt: startPage) }
        // Aquí llegan los enlaces del listado del inicio del sector.
        if faceIndex == 0 { dest(GuidePDFWriter.blockDest(b.blockId), y: y) }

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

    /// Foto VERTICAL: la foto a la izquierda (a su tamaño, sin franjas negras) y las vías a la derecha, para que
    /// quepan dos piedras por página sin que la foto se vea más pequeña. Devuelve false si no cabe ni en una
    /// página entera (muchas vías): entonces se usa la tarjeta normal, que sabe partirse.
    func sideCard(block b: GuideBlock, face: GuideFace, photo: UIImage, faceIndex: Int, faceCount: Int) -> Bool {
        let aspect = photo.size.height / max(photo.size.width, 1)
        let photoH = min(photoMaxH, P.contentW * 0.5 * aspect)
        let photoW = photoH / aspect
        let listX = P.margin + photoW + 14
        let listW = P.contentW - photoW - 14 - 12
        let textW = listW - 84
        let rows = face.lines.map { rowH($0, textW: textW) }
        let cardH = max(photoH, rows.reduce(0, +) + 8)
        let freshTop: CGFloat = 56 + 28
        guard cardH <= P.bottomLimit - freshTop else { return false }

        if y + cardH > P.bottomLimit { continuePage(from: b, startedAt: pageNo) }
        if faceIndex == 0 { dest(GuidePDFWriter.blockDest(b.blockId), y: y) }

        let card = CGRect(x: P.margin, y: y, width: P.contentW, height: cardH)
        let cardPath = UIBezierPath(roundedRect: card, cornerRadius: 16)
        UIColor.white.setFill(); cardPath.fill()
        let r = CGRect(x: card.minX, y: card.minY, width: photoW, height: photoH)
        cg.saveGState()
        cardPath.addClip()
        photo.draw(in: r)
        cg.translateBy(x: r.minX, y: r.minY)
        TopoPainter.paint(CGContextTarget(cg: cg), vias: face.topoVias, size: r.size,
                          style: .share(scale: r.width / 360.0, badgeOuter: 9, badgeInner: 7.2,
                                        badgeText: 9.5, startText: 7, fanStart: 9 * 2 + 4, fanEnd: 10.5 * 2 + 4))
        cg.restoreGState()

        let p1 = P.pill(L("PIEDRA %@", b.blockName.uppercased()), P.mono(8.5), fg: .white,
                        bg: P.dark.withAlphaComponent(0.88), at: CGPoint(x: r.minX + 10, y: r.minY + 10))
        if faceCount > 1 {
            P.pill(L("Cara %@ de %@", faceIndex + 1, faceCount), .systemFont(ofSize: 8.5, weight: .semibold), fg: P.dark,
                   bg: UIColor.white.withAlphaComponent(0.9), at: CGPoint(x: r.minX + 10, y: p1.maxY + 5))
        }

        var ry = card.minY + 4
        for (k, hit) in face.lines.enumerated() {
            drawRow(hit, number: k + 1, y: ry, height: rows[k], last: k == rows.count - 1, x0: listX, width: listW)
            ry += rows[k]
        }
        y += cardH + 14
        return true
    }

    func drawRow(_ h: LineSearchHit, number: Int, y ry: CGFloat, height: CGFloat, last: Bool,
                         x0: CGFloat? = nil, width: CGFloat? = nil) {
        let innerX = x0 ?? self.innerX
        let innerW = width ?? self.innerW
        let rowTextX = innerX + 84
        let rowTextW = innerW - 84
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
