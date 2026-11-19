import UIKit
import Shared

/// Marcadores de rocódromos para MapLibre. Espejo de `GymMarkerIcons.kt`: círculo del color del tipo (38 pt, 46 si
/// está seleccionado, con aro del mismo color al 33 %) con borde blanco de 3 y sombra, y dentro el icono en blanco;
/// el muro gratis lleva debajo la etiqueta «GRATIS». El grupo es un círculo de tinta de marca con el número.
enum GymMarkerRenderer {

    /// Letras máximas del nombre bajo el marcador (el resto se recorta con «…»), igual que Android.
    private static let maxNameChars = 18

    /// [name] != nil añade el nombre (recortado) bajo el icono, con halo blanco como en los marcadores de Escuelas.
    /// La imagen lleva un margen de alineación para que el CÍRCULO (no el centro de la imagen) caiga en la coordenada.
    static func pin(glyph: String?, fill: UIColor, selected: Bool, badge: String?, name: String? = nil) -> UIImage {
        let d: CGFloat = selected ? 46 : 38
        let ring: CGFloat = 3
        let pad = ring + 2
        let pillHeight: CGFloat = badge == nil ? 0 : 16
        let labelFont = UIFont.systemFont(ofSize: 9, weight: .bold)
        let labelSize = badge.map { ($0 as NSString).size(withAttributes: [.font: labelFont, .kern: 0.9]) } ?? .zero
        let pillWidth = badge == nil ? 0 : labelSize.width + 14
        let title = name.map { $0.count > maxNameChars ? String($0.prefix(maxNameChars - 1)).trimmingCharacters(in: .whitespaces) + "…" : $0 }
        let nameFont = UIFont.systemFont(ofSize: 11, weight: .bold)
        let nameSize = title.map { ($0 as NSString).size(withAttributes: [.font: nameFont]) } ?? .zero
        let nameWidth = title == nil ? 0 : nameSize.width + 6
        let nameHeight = title == nil ? 0 : CGFloat(GymLabelPlanner.shared.LABEL_EXTRA_DP)
        let width = max(d, pillWidth, nameWidth) + pad * 2
        let height = d + pad * 2 + pillHeight - (badge == nil ? 0 : 4) + nameHeight
        let cx = width / 2
        let cy = pad + d / 2
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
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
            if let title {
                drawName(title, font: nameFont, size: nameSize, centerX: cx, bottom: height - 2)
            }
        }
        // El ancla de MapLibre es el centro del rectángulo de alineación: se recorta por abajo hasta que el círculo
        // quede en el centro, y así el nombre (y la etiqueta «GRATIS») cuelgan sin mover el marcador de su punto.
        return image.withAlignmentRectInsets(UIEdgeInsets(top: 0, left: 0, bottom: max(0, height - 2 * cy), right: 0))
    }

    /// Nombre con halo blanco (trazo grueso debajo) y tinta encima, legible sobre satélite y sobre papel.
    private static func drawName(_ text: String, font: UIFont, size: CGSize, centerX: CGFloat, bottom: CGFloat) {
        let origin = CGPoint(x: centerX - size.width / 2, y: bottom - size.height)
        let ns = text as NSString
        ns.draw(at: origin, withAttributes: [.font: font, .foregroundColor: UIColor.white,
                                             .strokeColor: UIColor.white, .strokeWidth: 36])
        ns.draw(at: origin, withAttributes: [.font: font, .foregroundColor: UIColor(rgb: 0x1A1A1A)])
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
