import UIKit

/// Marcadores de rocódromos para MapLibre. Espejo de `GymMarkerIcons.kt`: círculo del color del tipo (38 pt, 46 si
/// está seleccionado, con aro del mismo color al 33 %) con borde blanco de 3 y sombra, y dentro el icono en blanco;
/// el muro gratis lleva debajo la etiqueta «GRATIS». El grupo es un círculo de tinta de marca con el número.
enum GymMarkerRenderer {

    static func pin(glyph: String?, fill: UIColor, selected: Bool, badge: String?) -> UIImage {
        let d: CGFloat = selected ? 46 : 38
        let ring: CGFloat = 3
        let pad = ring + 2
        let pillHeight: CGFloat = badge == nil ? 0 : 16
        let labelFont = UIFont.systemFont(ofSize: 9, weight: .bold)
        let labelSize = badge.map { ($0 as NSString).size(withAttributes: [.font: labelFont, .kern: 0.9]) } ?? .zero
        let pillWidth = badge == nil ? 0 : labelSize.width + 14
        let width = max(d, pillWidth) + pad * 2
        let height = d + pad * 2 + pillHeight - (badge == nil ? 0 : 4)
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        return renderer.image { ctx in
            let cg = ctx.cgContext
            let cx = width / 2
            let cy = pad + d / 2
            cg.setFillColor(UIColor.black.withAlphaComponent(0.24).cgColor)
            cg.fillEllipse(in: CGRect(x: cx - d / 2, y: cy - d / 2 + 2, width: d, height: d))
            if selected {
                cg.setFillColor(fill.withAlphaComponent(0.33).cgColor)
                let r = d / 2 + ring
                cg.fillEllipse(in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
            }
            cg.setFillColor(fill.cgColor)
            cg.fillEllipse(in: CGRect(x: cx - d / 2, y: cy - d / 2, width: d, height: d))
            cg.setStrokeColor(UIColor.white.cgColor)
            cg.setLineWidth(ring)
            cg.strokeEllipse(in: CGRect(x: cx - d / 2 + ring / 2, y: cy - d / 2 + ring / 2, width: d - ring, height: d - ring))
            drawGlyph(glyph, center: CGPoint(x: cx, y: cy), size: d * 0.52)
            if let badge {
                let top = cy + d / 2 - 4 + 2
                let rect = CGRect(x: cx - pillWidth / 2, y: top, width: pillWidth, height: pillHeight - 2)
                let path = UIBezierPath(roundedRect: rect, cornerRadius: rect.height / 2)
                fill.setFill()
                path.fill()
                UIColor.white.setStroke()
                path.lineWidth = 1.5
                path.stroke()
                let origin = CGPoint(x: rect.midX - labelSize.width / 2, y: rect.midY - labelSize.height / 2)
                (badge as NSString).draw(at: origin, withAttributes: [.font: labelFont, .foregroundColor: UIColor.white, .kern: 0.9])
            }
        }
    }

    static func cluster(count: Int, color: UIColor) -> UIImage {
        let d: CGFloat = count >= 100 ? 48 : 40
        let pad: CGFloat = 4
        let size = d + pad * 2
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        return renderer.image { ctx in
            let cg = ctx.cgContext
            let c = size / 2
            cg.setFillColor(UIColor.black.withAlphaComponent(0.24).cgColor)
            cg.fillEllipse(in: CGRect(x: c - d / 2, y: c - d / 2 + 2, width: d, height: d))
            cg.setFillColor(color.cgColor)
            cg.fillEllipse(in: CGRect(x: c - d / 2, y: c - d / 2, width: d, height: d))
            cg.setStrokeColor(UIColor.white.cgColor)
            cg.setLineWidth(3)
            cg.strokeEllipse(in: CGRect(x: c - d / 2 + 1.5, y: c - d / 2 + 1.5, width: d - 3, height: d - 3))
            let font = UIFont.systemFont(ofSize: 15, weight: .bold)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
            let text = "\(count)" as NSString
            let ts = text.size(withAttributes: attrs)
            text.draw(at: CGPoint(x: c - ts.width / 2, y: c - ts.height / 2), withAttributes: attrs)
        }
    }

    private static func drawGlyph(_ name: String?, center: CGPoint, size: CGFloat) {
        guard let name, let image = UIImage(named: name)?.withRenderingMode(.alwaysTemplate) else { return }
        let rect = CGRect(x: center.x - size / 2, y: center.y - size / 2, width: size, height: size)
        // withTintColor + alwaysOriginal pinta la plantilla de blanco al dibujarla fuera de una vista.
        image.withTintColor(.white, renderingMode: .alwaysOriginal).draw(in: rect)
    }
}
