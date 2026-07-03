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
        case .block:   return block(name: m.name ?? m.title, color: m.color)
        case .school:  return school()
        case .user:    return userDot()
        case .score:   return scoreDiamond(score: m.score, color: m.color,
                                            name: m.showName ? (m.name ?? m.title) : nil)
        case .dot:     return dot(color: m.color)
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
        let size: CGFloat = 64
        return render(size) { ctx in
            let rect = CGRect(x: 8, y: 8, width: size - 16, height: size - 16)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 10)
            UIColor(hex: 0x1A56DB).setFill(); path.fill()
            UIColor.white.setStroke(); path.lineWidth = 3; path.stroke()
            draw(text: "P", in: CGRect(x: 0, y: 0, width: size, height: size),
                 size: 34, color: .white)
        }
    }

    // MARK: - Zona (círculo verde + triángulo, con "Z")

    private static func zone(name: String?) -> UIImage {
        let pin: CGFloat = 56
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
            let r: CGFloat = 20
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
            draw(text: "Z", in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2),
                 size: 20, color: .white)
            // Nombre del sector debajo (con halo blanco), visible al hacer zoom.
            if let label {
                draw(text: label, in: CGRect(x: 0, y: pin - 2, width: w, height: nameH),
                     size: 12, color: UIColor(hex: 0x1A1A1A),
                     bold: true, haloColor: .white, haloWidth: 4)
            }
        }
    }

    // MARK: - Piedra (polígono de roca terra con nombre corto)

    private static func block(name: String, color: UIColor) -> UIImage {
        let size: CGFloat = 66
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
        let size: CGFloat = 64
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

    static func userDot() -> UIImage {
        let size: CGFloat = 48
        return render(size) { ctx in
            let c = CGPoint(x: size / 2, y: size / 2)
            fillCircle(ctx, c, 22, UIColor(red: 30/255, green: 100/255, blue: 220/255, alpha: 50/255))
            fillCircle(ctx, c, 12, .white)
            fillCircle(ctx, c, 9, UIColor(red: 30/255, green: 100/255, blue: 220/255, alpha: 1))
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
