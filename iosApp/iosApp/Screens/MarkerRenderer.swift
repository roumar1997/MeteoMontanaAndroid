import UIKit
import CoreGraphics

// Dibuja la imagen de cada marcador del mapa replicando los bitmaps de Android
// (SchoolMap.kt / FullScreenMapDialog.kt / SchoolsMapPanel.kt): parking cuadrado
// azul "P", zona pin verde "Z", piedra polígono de roca terra con nombre corto,
// escuela triángulo oscuro, usuario punto azul con halo, y el diamante de la
// lista coloreado por score con el número dentro y el nombre debajo.

enum MarkerRenderer {

    static func image(for m: CumbreMarker) -> UIImage {
        switch m.kind {
        case .parking: return parking()
        case .zone:    return zone(name: m.showName ? (m.name ?? m.title) : nil)
        case .block:   return block(name: m.name ?? m.title, color: m.color, discipline: m.discipline)
        case .school:  return school()
        case .user:    return userDot(bearing: m.score)
        case .score where m.glyph == "ring":
            return scoreRing(score: m.score, wet: m.badge == "wet", selected: m.selected, name: m.showName ? (m.name ?? m.title) : nil)
        case .score:   return scoreDiamond(score: m.score, color: m.color,
                                            name: m.showName ? (m.name ?? m.title) : nil)
        case .dot:     return dot(color: m.color)
        case .cluster: return cluster(count: m.score ?? 0, color: m.color)
        case .gym:
            return GymMarkerRenderer.pin(glyph: m.glyph, fill: m.color, selected: m.selected, badge: m.badge,
                                         name: m.showName ? (m.name ?? m.title) : nil)
        case .gymCluster: return GymMarkerRenderer.cluster(count: m.score ?? 0, color: m.color)
        }
    }

    // MARK: - Clúster de piedras (círculo terra + halo con el contador)

    private static func cluster(count: Int, color: UIColor) -> UIImage {
        let size = CGSize(width: 60, height: 60)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            color.withAlphaComponent(0.28).setFill()
            ctx.cgContext.fillEllipse(in: CGRect(x: 2, y: 2, width: 56, height: 56))
            let inner = CGRect(x: 10, y: 10, width: 40, height: 40)
            color.setFill()
            ctx.cgContext.fillEllipse(in: inner)
            UIColor.white.setStroke()
            ctx.cgContext.setLineWidth(2.5)
            ctx.cgContext.strokeEllipse(in: inner)
            let text = "\(count)" as NSString
            let font = UIFont.systemFont(ofSize: count >= 10 ? 16 : 19, weight: .bold)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
            let ts = text.size(withAttributes: attrs)
            text.draw(at: CGPoint(x: 30 - ts.width / 2, y: 30 - ts.height / 2), withAttributes: attrs)
        }
    }

    // MARK: - Puntito de color (zoom país: los diamantes taparían el mapa)

    private static func dot(color: UIColor) -> UIImage {
        let size = CGSize(width: 16, height: 16)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let r = CGRect(x: 1.5, y: 1.5, width: 13, height: 13)
            color.setFill()
            ctx.cgContext.fillEllipse(in: r)
            UIColor.white.setStroke()
            ctx.cgContext.setLineWidth(1.6)
            ctx.cgContext.strokeEllipse(in: r)
        }
    }

    // MARK: - Parking (cuadrado azul redondeado con "P")

    private static func parking() -> UIImage {
        let size: CGFloat = 50
        return render(size) { ctx in
            let rect = CGRect(x: 8, y: 8, width: size - 16, height: size - 16)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 10)
            UIColor(hex: 0x1A56DB).setFill(); path.fill()
            UIColor.white.setStroke(); path.lineWidth = 3; path.stroke()
            draw(text: "P", in: CGRect(x: 0, y: 0, width: size, height: size),
                 size: 26, color: .white)
        }
    }

    // MARK: - Zona (círculo verde + triángulo, con "Z")

    private static func zone(name: String?) -> UIImage {
        let pin: CGFloat = 48
        let label = name.map { truncate($0, 18) }?.uppercased()
        let nameH: CGFloat = label != nil ? 26 : 0
        // Ancho extra si el nombre del sector es largo (para que no se recorte).
        let textW: CGFloat = label.map { ($0 as NSString).size(
            withAttributes: [.font: UIFont.boldSystemFont(ofSize: 12)]).width } ?? 0
        let w = max(pin, textW + 14)
        let h = pin + nameH
        let sz = CGSize(width: w, height: h)
        return UIGraphicsImageRenderer(size: sz).image { _ in
            let cx = w / 2, cy = pin / 2 - 6
            let r: CGFloat = 17
            let circle = UIBezierPath(arcCenter: CGPoint(x: cx, y: cy), radius: r,
                                      startAngle: 0, endAngle: .pi * 2, clockwise: true)
            // Triángulo inferior (punta del pin).
            let tri = UIBezierPath()
            tri.move(to: CGPoint(x: cx - 10, y: cy + r - 4))
            tri.addLine(to: CGPoint(x: cx + 10, y: cy + r - 4))
            tri.addLine(to: CGPoint(x: cx, y: cy + r + 14))
            tri.close()
            UIColor(hex: 0x3F6B4A).setFill()
            tri.fill(); circle.fill()
            UIColor.white.setStroke(); circle.lineWidth = 2; circle.stroke()
            draw(text: "S", in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2),
                 size: 17, color: .white)
            // Nombre del sector debajo (con halo blanco), visible al hacer zoom.
            if let label {
                draw(text: label, in: CGRect(x: 0, y: pin - 2, width: w, height: nameH),
                     size: 12, color: UIColor(hex: 0x1A1A1A),
                     bold: true, haloColor: .white, haloWidth: 4)
            }
        }
    }

    // MARK: - Piedra: crashpad (bloque) / mosquetón (vía) con el número en una chapa

    /// Con discipline conocida pinta el icono de su modalidad (el mismo que en la
    /// lista de escuelas) y el nombre corto en una chapa blanca en la esquina; sin
    /// ella (marcadores especiales del admin: ✕ ★) mantiene el polígono de roca.
    private static func block(name: String, color: UIColor, discipline: String?) -> UIImage {
        guard let d = discipline?.uppercased(), d == "ROUTE" || d == "BOULDER",
              let icon = UIImage(named: d == "ROUTE" ? "kind_route" : "kind_boulder"),
              icon.size.height > 0 else {
            return rockPolygon(name: name, color: color)
        }
        let canvas = CGSize(width: 64, height: 58)
        let iconH: CGFloat = d == "ROUTE" ? 46 : 36
        let iconW = iconH * icon.size.width / icon.size.height
        let iconRect = CGRect(x: (canvas.width - iconW) / 2 - 3, y: canvas.height - iconH - 3,
                              width: iconW, height: iconH)
        let label = shortLabel(name)
        return UIGraphicsImageRenderer(size: canvas).image { ctx in
            // Contorno blanco: el icono en blanco desplazado en 8 direcciones.
            let white = icon.withTintColor(.white, renderingMode: .alwaysOriginal)
            for k in 0..<8 {
                let a = CGFloat(k) / 8 * .pi * 2
                white.draw(in: iconRect.offsetBy(dx: cos(a) * 2, dy: sin(a) * 2))
            }
            icon.withTintColor(color, renderingMode: .alwaysOriginal).draw(in: iconRect)
            // Chapa con el número, dentro del lienzo.
            let r: CGFloat = label.count > 1 ? 11 : 10
            let c = CGPoint(x: min(iconRect.maxX, canvas.width - r - 1), y: max(iconRect.minY + 2, r + 1))
            fillCircle(ctx, c, r, .white)
            color.setStroke()
            let ring = UIBezierPath(arcCenter: c, radius: r - 1, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            ring.lineWidth = 2; ring.stroke()
            draw(text: label, in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2),
                 size: label.count > 1 ? 10 : 12, color: UIColor(hex: 0x7A2412))
        }
    }

    // MARK: - Piedra clásica (polígono de roca terra con nombre corto)

    private static func rockPolygon(name: String, color: UIColor) -> UIImage {
        let size: CGFloat = 52
        let label = shortLabel(name)
        return render(size) { ctx in
            let cx = size / 2, cy = size / 2
            let baseR = size * 0.40
            let n = 7
            let path = UIBezierPath()
            let seed = stableHash(name)
            for i in 0..<n {
                let ang = (CGFloat(i) / CGFloat(n)) * .pi * 2 - .pi / 2
                // Jitter determinista 0.78..1.0 (igual que Android).
                let j = 0.78 + 0.22 * pseudo(seed, i)
                let rr = baseR * j
                let p = CGPoint(x: cx + cos(ang) * rr, y: cy + sin(ang) * rr)
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.close()
            color.setFill(); path.fill()
            UIColor.white.setStroke(); path.lineWidth = 4; path.stroke()
            if !label.isEmpty {
                let fs: CGFloat = label.count <= 2 ? size * 0.42 : size * 0.30
                draw(text: label, in: CGRect(x: 0, y: 0, width: size, height: size),
                     size: fs, color: .white, shadow: true)
            }
        }
    }

    // MARK: - Escuela (triángulo oscuro con punto)

    private static func school() -> UIImage {
        let size: CGFloat = 50
        return render(size) { ctx in
            let path = UIBezierPath()
            path.move(to: CGPoint(x: size / 2, y: 8))
            path.addLine(to: CGPoint(x: 8, y: size - 10))
            path.addLine(to: CGPoint(x: size - 8, y: size - 10))
            path.close()
            UIColor(hex: 0x1C1C1A).setFill(); path.fill()
            UIColor.white.setStroke(); path.lineWidth = 3; path.stroke()
            UIColor.white.setFill()
            let dot = CGRect(x: size / 2 - 4, y: size - 28, width: 8, height: 8)
            ctx.cgContext.fillEllipse(in: dot)
        }
    }

    // MARK: - Usuario (punto azul con halo)

    /// Punto azul; con bearing (brújula, grados) añade un cono de dirección.
    static func userDot(bearing: Int? = nil) -> UIImage {
        let size: CGFloat = 72
        return render(size) { ctx in
            let c = CGPoint(x: size / 2, y: size / 2)
            if let bearing {
                let g = ctx.cgContext
                let rad = CGFloat(bearing) * .pi / 180
                g.saveGState()
                g.translateBy(x: c.x, y: c.y)
                g.rotate(by: rad)
                g.move(to: .zero)
                g.addLine(to: CGPoint(x: -13, y: -32))
                g.addLine(to: CGPoint(x: 13, y: -32))
                g.closePath()
                g.setFillColor(UIColor(red: 30/255, green: 100/255, blue: 220/255, alpha: 0.45).cgColor)
                g.fillPath()
                g.restoreGState()
            }
            fillCircle(ctx, c, 22, UIColor(red: 30/255, green: 100/255, blue: 220/255, alpha: 50/255))
            fillCircle(ctx, c, 12, .white)
            fillCircle(ctx, c, 9, UIColor(red: 30/255, green: 100/255, blue: 220/255, alpha: 1))
        }
    }

    // MARK: - Anillo de nota de las escuelas (propuesta A de maquetas-3/marcadores)

    /// Anillo que se llena según la nota sobre pista blanca translúcida, centro crema con la nota en mono; mojada: anillo
    /// azul, centro azul claro y gota arriba a la derecha; elegida: 50 en vez de 40 con aureola terracota; nombre debajo
    /// en etiqueta blanca. Espejo de `ringBitmap` (Android).
    static func scoreRing(score: Int?, wet: Bool, selected: Bool, name: String?) -> UIImage {
        let d: CGFloat = selected ? 50 : 40
        let pad: CGFloat = 6
        let label = name.map { $0.count > 18 ? String($0.prefix(17)).trimmingCharacters(in: .whitespaces) + "…" : $0 }
        let font = UIFont.boldSystemFont(ofSize: 10.5)
        let chipH: CGFloat = label != nil ? 15 : 0
        let chipGap: CGFloat = label != nil ? 3 : 0
        let chipW: CGFloat = label.map { ($0 as NSString).size(withAttributes: [.font: font]).width + 14 } ?? 0
        let w = max(d + pad * 2, chipW + 4)
        // La imagen se ancla por su centro: el mismo hueco arriba que la etiqueta abajo deja el anillo sobre el punto.
        let below = chipGap + chipH
        let h = d + pad * 2 + below * 2
        let rain = UIColor(rgb: 0x2563C7)
        let ringColor = wet ? rain : UIColor(Cumbre.score(score ?? 0))
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h)).image { c in
            let g = c.cgContext
            let cx = w / 2, cy = below + pad + d / 2
            let band = d * 0.115
            g.setFillColor(UIColor.black.withAlphaComponent(0.27).cgColor)
            g.fillEllipse(in: CGRect(x: cx - d / 2, y: cy - d / 2 + 2, width: d, height: d))
            if selected {
                g.setStrokeColor(UIColor(rgb: 0xC2410C).cgColor)
                g.setLineWidth(3)
                g.strokeEllipse(in: CGRect(x: cx - d / 2 - 2.5, y: cy - d / 2 - 2.5, width: d + 5, height: d + 5))
            }
            g.setFillColor(UIColor.white.withAlphaComponent(0.43).cgColor)
            g.fillEllipse(in: CGRect(x: cx - d / 2, y: cy - d / 2, width: d, height: d))
            let r = d / 2 - band / 2
            let sweep = CGFloat(max(2, min(100, wet ? 100 : (score ?? 0)))) / 100 * 2 * .pi
            let arc = UIBezierPath(arcCenter: CGPoint(x: cx, y: cy), radius: r, startAngle: -.pi / 2,
                                   endAngle: -.pi / 2 + sweep, clockwise: true)
            arc.lineWidth = band
            ringColor.setStroke()
            arc.stroke()
            g.setFillColor((wet ? UIColor(rgb: 0xE3ECFA) : UIColor(rgb: 0xFBF9F4)).cgColor)
            g.fillEllipse(in: CGRect(x: cx - d / 2 + band, y: cy - d / 2 + band, width: d - band * 2, height: d - band * 2))
            let num = score.map { "\($0)" } ?? "·"
            let numFont = UIFont.monospacedSystemFont(ofSize: d * 0.36, weight: .bold)
            let numSize = (num as NSString).size(withAttributes: [.font: numFont])
            (num as NSString).draw(at: CGPoint(x: cx - numSize.width / 2, y: cy - numSize.height / 2),
                                   withAttributes: [.font: numFont, .foregroundColor: wet ? rain : UIColor(rgb: 0x1C1C1A)])
            if wet {
                let bx = cx + d * 0.38, by = cy - d * 0.38, br: CGFloat = 8
                g.setFillColor(rain.cgColor)
                g.fillEllipse(in: CGRect(x: bx - br, y: by - br, width: br * 2, height: br * 2))
                g.setStrokeColor(UIColor.white.cgColor)
                g.setLineWidth(2)
                g.strokeEllipse(in: CGRect(x: bx - br + 1, y: by - br + 1, width: br * 2 - 2, height: br * 2 - 2))
                let drop = UIBezierPath()
                drop.move(to: CGPoint(x: bx, y: by - 4.6))
                drop.addCurve(to: CGPoint(x: bx + 3.4, y: by + 1.2), controlPoint1: CGPoint(x: bx + 1, y: by - 2.8),
                              controlPoint2: CGPoint(x: bx + 3.4, y: by - 0.8))
                drop.addCurve(to: CGPoint(x: bx - 3.4, y: by + 1.2), controlPoint1: CGPoint(x: bx + 3.4, y: by + 3.4),
                              controlPoint2: CGPoint(x: bx - 3.4, y: by + 3.4))
                drop.addCurve(to: CGPoint(x: bx, y: by - 4.6), controlPoint1: CGPoint(x: bx - 3.4, y: by - 0.8),
                              controlPoint2: CGPoint(x: bx - 1, y: by - 2.8))
                drop.close()
                UIColor.white.setFill()
                drop.fill()
            }
            if let label {
                let top = cy + d / 2 + pad + chipGap
                let rect = CGRect(x: cx - chipW / 2, y: top, width: chipW, height: chipH)
                UIColor.white.withAlphaComponent(0.92).setFill()
                UIBezierPath(roundedRect: rect, cornerRadius: chipH / 2).fill()
                let ts = (label as NSString).size(withAttributes: [.font: font])
                (label as NSString).draw(at: CGPoint(x: cx - ts.width / 2, y: rect.midY - ts.height / 2),
                                         withAttributes: [.font: font, .foregroundColor: UIColor(rgb: 0x1C1C1A)])
            }
        }
    }

    // MARK: - Diamante de score (lista) con número y nombre opcional

    private static func scoreDiamond(score: Int?, color: UIColor, name: String?) -> UIImage {
        let pin: CGFloat = 64
        let nameH: CGFloat = name != nil ? 28 : 0
        let label = name.map { truncate($0, 15) }
        // Ancho extra si el nombre es largo.
        let textW: CGFloat = label.map { ($0 as NSString).size(
            withAttributes: [.font: UIFont.boldSystemFont(ofSize: 13)]).width } ?? 0
        let w = max(pin, textW + 14)
        // Padding superior = nameH para que el diamante quede centrado sobre la
        // coordenada (la imagen se ancla por su centro).
        let h = pin + nameH * 2
        let sz = CGSize(width: w, height: h)
        let r = UIGraphicsImageRenderer(size: sz)
        return r.image { c in
            let cx = w / 2, cy = nameH + pin / 2 - 4
            let side = pin * 0.55
            c.cgContext.saveGState()
            c.cgContext.translateBy(x: cx, y: cy)
            c.cgContext.rotate(by: .pi / 4)
            let rect = CGRect(x: -side / 2, y: -side / 2, width: side, height: side)
            let dia = UIBezierPath(roundedRect: rect, cornerRadius: 6)
            color.setFill(); dia.fill()
            UIColor.white.setStroke(); dia.lineWidth = 3; dia.stroke()
            c.cgContext.restoreGState()
            // Número de score centrado en el diamante.
            draw(text: score.map { "\($0)" } ?? "·",
                 in: CGRect(x: 0, y: cy - pin / 2, width: w, height: pin),
                 size: 18, color: .white)
            // Nombre debajo, con halo blanco + tinta oscura.
            if let label {
                let nameRect = CGRect(x: 0, y: nameH + pin - 2, width: w, height: nameH)
                draw(text: label, in: nameRect, size: 13, color: UIColor(hex: 0x1A1A1A),
                     bold: true, haloColor: .white, haloWidth: 4)
            }
        }
    }

    // MARK: - Helpers de dibujo

    private static func render(_ size: CGFloat, _ body: @escaping (UIGraphicsImageRendererContext) -> Void) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: size, height: size)).image { body($0) }
    }

    private static func fillCircle(_ ctx: UIGraphicsImageRendererContext, _ c: CGPoint, _ r: CGFloat, _ color: UIColor) {
        color.setFill()
        ctx.cgContext.fillEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }

    private static func draw(text: String, in rect: CGRect, size: CGFloat, color: UIColor,
                             bold: Bool = true, shadow: Bool = false,
                             haloColor: UIColor? = nil, haloWidth: CGFloat = 0) {
        let font = bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size)
        let para = NSMutableParagraphStyle(); para.alignment = .center
        let textSize = (text as NSString).size(withAttributes: [.font: font])
        let y = rect.midY - textSize.height / 2
        let r = CGRect(x: rect.minX, y: y, width: rect.width, height: textSize.height)
        if let halo = haloColor, haloWidth > 0 {
            let attrs: [NSAttributedString.Key: Any] = [
                .font: font, .paragraphStyle: para,
                .strokeColor: halo, .strokeWidth: haloWidth, .foregroundColor: halo]
            (text as NSString).draw(in: r, withAttributes: attrs)
        }
        var attrs: [NSAttributedString.Key: Any] = [
            .font: font, .paragraphStyle: para, .foregroundColor: color]
        if shadow {
            let sh = NSShadow(); sh.shadowColor = UIColor.black.withAlphaComponent(0.6)
            sh.shadowBlurRadius = 4; sh.shadowOffset = .zero
            attrs[.shadow] = sh
        }
        (text as NSString).draw(in: r, withAttributes: attrs)
    }

    private static func shortLabel(_ name: String) -> String {
        let t = name.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return "B" }
        if t.count <= 2 { return t.uppercased() }
        return String(t.prefix(2)).uppercased()
    }

    private static func truncate(_ s: String, _ n: Int) -> String {
        s.count <= n ? s : String(s.prefix(n)) + "…"
    }

    // Hash determinista (String.hashValue no es estable entre ejecuciones).
    private static func stableHash(_ s: String) -> Int {
        var h = 5381
        for u in s.unicodeScalars { h = ((h << 5) &+ h) &+ Int(u.value) }
        return abs(h)
    }
    private static func pseudo(_ seed: Int, _ i: Int) -> CGFloat {
        let x = (seed &* 31 &+ i &* 2654435761) & 0xFFFF
        return CGFloat(x) / CGFloat(0xFFFF)
    }
}

extension UIColor {
    convenience init(hex: Int) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
