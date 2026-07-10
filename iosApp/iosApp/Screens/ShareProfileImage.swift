import SwiftUI
import UIKit

/// Comparte un perfil como IMAGEN vertical 1080×1920 (formato historia):
/// avatar + nombre + @username + grado máximo + bio, con marca Cumbre.
/// Espejo de `ShareProfileImage.kt` de Android. En el share sheet aparece
/// Instagram (→ Historia), WhatsApp, etc. El texto acompañante lleva el
/// enlace /s/u/{handle} de siempre.
enum ShareProfileImage {

    /* Paleta Cumbre fija (light), = ShareLineImage */
    private static let paper = UIColor(rgb: 0xFAF7F2)
    private static let ink = UIColor(rgb: 0x1A1A1A)
    private static let inkSoft = UIColor(rgb: 0x6B6B6B)
    private static let rule = UIColor(rgb: 0xE2DCD2)
    private static let terra = UIColor(rgb: 0xC0532B)

    /// Punto de entrada: compone la card y presenta el share sheet.
    static func share(handle: String, displayLabel: String, username: String?,
                      photoUrl: String?, topGrade: String?, bio: String?) async {
        var avatar: UIImage? = nil
        if let url = photoUrl, !url.isEmpty {
            avatar = await ImageCache.image(url)
        }
        let image = await MainActor.run {
            drawCard(displayLabel: displayLabel, username: username,
                     avatar: avatar, topGrade: topGrade, bio: bio)
        }
        let text = "Perfil de \(displayLabel) en Cumbre:\n"
            + "https://api.climbingteams.com/s/u/\(handle)"
        await present([image, text])
    }

    // MARK: - Render

    @MainActor
    private static func drawCard(displayLabel: String, username: String?,
                                 avatar: UIImage?, topGrade: String?, bio: String?) -> UIImage {
        let w: CGFloat = 1080, h: CGFloat = 1920
        let cx = w / 2
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)

        return renderer.image { rctx in
            let cg = rctx.cgContext
            paper.setFill(); cg.fill(CGRect(x: 0, y: 0, width: w, height: h))
            rule.setStroke()
            let border = UIBezierPath(rect: CGRect(x: 16, y: 16, width: w - 32, height: h - 32))
            border.lineWidth = 3; border.stroke()

            // Eyebrow superior, centrado.
            drawCenteredText("ESCALA CONMIGO EN CUMBRE", cx: cx, y: 150,
                             font: mono(30, bold: true), color: terra, kern: 4)

            // Avatar circular (o monograma con inicial) + anillo terra.
            let avatarR: CGFloat = 210
            let avatarC = CGPoint(x: cx, y: 560)
            let avatarRect = CGRect(x: avatarC.x - avatarR, y: avatarC.y - avatarR,
                                    width: avatarR * 2, height: avatarR * 2)
            if let avatar {
                cg.saveGState()
                cg.addEllipse(in: avatarRect); cg.clip()
                // aspect-fill dentro del círculo
                let scale = max(avatarRect.width / avatar.size.width,
                                avatarRect.height / avatar.size.height)
                let dw = avatar.size.width * scale, dh = avatar.size.height * scale
                avatar.draw(in: CGRect(x: avatarRect.midX - dw / 2, y: avatarRect.midY - dh / 2,
                                       width: dw, height: dh))
                cg.restoreGState()
            } else {
                cg.setFillColor(rule.cgColor)
                cg.fillEllipse(in: avatarRect)
                let initial = String(displayLabel.trimmingCharacters(in: .whitespaces)
                    .first.map { $0.uppercased() } ?? "C")
                drawCenteredText(initial, cx: cx, y: avatarC.y - 95,
                                 font: serif(190), color: terra, kern: 0)
            }
            cg.setStrokeColor(terra.cgColor)
            cg.setLineWidth(5)
            cg.strokeEllipse(in: avatarRect.insetBy(dx: -10, dy: -10))

            // Nombre (serif, centrado, hasta 2 líneas).
            var y: CGFloat = avatarC.y + avatarR + 70
            let namePara = NSMutableParagraphStyle()
            namePara.alignment = .center
            namePara.lineBreakMode = .byTruncatingTail
            let nameAttrs: [NSAttributedString.Key: Any] =
                [.font: serif(88), .foregroundColor: ink, .paragraphStyle: namePara]
            let nameRect = CGRect(x: 80, y: y, width: w - 160, height: 210)
            let measured = (displayLabel as NSString).boundingRect(
                with: CGSize(width: nameRect.width, height: nameRect.height),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: nameAttrs, context: nil).height
            (displayLabel as NSString).draw(with: nameRect,
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: nameAttrs, context: nil)
            y += min(ceil(measured), 210) + 26

            // @username.
            if let u = username, !u.isEmpty {
                drawCenteredText("@\(u)", cx: cx, y: y,
                                 font: mono(42, bold: false), color: inkSoft, kern: 0)
                y += 100
            }

            // Grado máximo en caja regla (si lo hay).
            if let g = topGrade, !g.isEmpty {
                y += 20
                let gradeFont = mono(80, bold: true)
                let gw = (g as NSString).size(withAttributes: [.font: gradeFont]).width
                let boxW = max(gw + 120, 320)
                let box = CGRect(x: cx - boxW / 2, y: y, width: boxW, height: 200)
                rule.setStroke()
                let bp = UIBezierPath(rect: box); bp.lineWidth = 3; bp.stroke()
                drawCenteredText("GRADO MÁXIMO", cx: cx, y: box.minY + 28,
                                 font: mono(26, bold: false), color: inkSoft, kern: 4)
                drawCenteredText(g, cx: cx, y: box.minY + 78,
                                 font: gradeFont, color: ink, kern: 0)
                y = box.maxY + 70
            } else {
                y += 30
            }

            // Bio (hasta 3 líneas, centrada).
            if let b = bio, !b.isEmpty {
                let bioPara = NSMutableParagraphStyle()
                bioPara.alignment = .center
                bioPara.lineBreakMode = .byTruncatingTail
                let bioAttrs: [NSAttributedString.Key: Any] =
                    [.font: UIFont.systemFont(ofSize: 40),
                     .foregroundColor: inkSoft, .paragraphStyle: bioPara]
                (b as NSString).draw(with: CGRect(x: 100, y: y, width: w - 200, height: 170),
                    options: [.usesLineFragmentOrigin, .usesFontLeading],
                    attributes: bioAttrs, context: nil)
            }

            // Pie: CTA + marca.
            drawCenteredText("Descarga Cumbre y sígueme", cx: cx, y: h - 230,
                             font: UIFont.systemFont(ofSize: 42), color: ink, kern: 0)
            drawCenteredText("⛰ CUMBRE", cx: cx, y: h - 150,
                             font: mono(36, bold: true), color: terra, kern: 4)
        }
    }

    // MARK: - Helpers (mismos criterios que ShareLineImage)

    private static func drawCenteredText(_ text: String, cx: CGFloat, y: CGFloat,
                                         font: UIFont, color: UIColor, kern: CGFloat) {
        let attrs: [NSAttributedString.Key: Any] =
            [.font: font, .foregroundColor: color, .kern: kern]
        let size = (text as NSString).size(withAttributes: attrs)
        (text as NSString).draw(at: CGPoint(x: cx - size.width / 2, y: y), withAttributes: attrs)
    }

    private static func mono(_ size: CGFloat, bold: Bool) -> UIFont {
        UIFont(name: bold ? "JetBrainsMono-Bold" : "JetBrainsMono-Regular", size: size)
            ?? .monospacedSystemFont(ofSize: size, weight: bold ? .bold : .regular)
    }

    private static func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "SourceSerif4-Bold", size: size) ?? .systemFont(ofSize: size, weight: .bold)
    }

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
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY,
                                    width: 0, height: 0)
            pop.permittedArrowDirections = []
        }
        top.present(vc, animated: true)
    }
}
