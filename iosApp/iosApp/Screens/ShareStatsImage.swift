import SwiftUI
import Shared
import UIKit

/// Comparte MIS ESTADÍSTICAS como imagen 1080×1920 (formato historia, estilo
/// "Wrapped") — espejo de ShareStatsImage.kt. Los números vienen del
/// JournalStatsCalculator compartido: esta capa solo pinta.
enum ShareStatsImage {

    static func share(periodLabel: String, disciplineLabel: String,
                      summary: JournalStatsCalculator.Summary, maxGrade: String?,
                      progression: JournalStatsCalculator.Progression? = nil) async {
        let image = render(periodLabel: periodLabel, disciplineLabel: disciplineLabel,
                           summary: summary, maxGrade: maxGrade, progression: progression)
        let text = "Mis estadísticas de escalada en Cumbre:\n\(ShareBase.url)/app"
        await MainActor.run {
            guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene }).first,
                  let root = scene.keyWindow?.rootViewController else { return }
            let vc = UIActivityViewController(activityItems: [image, text],
                                              applicationActivities: nil)
            var top = root
            while let presented = top.presentedViewController { top = presented }
            top.present(vc, animated: true)
        }
    }

    // Paleta Cumbre (= ShareProfileImage).
    private static let paper = UIColor(red: 0.98, green: 0.968, blue: 0.949, alpha: 1)
    private static let ink = UIColor(red: 0.102, green: 0.102, blue: 0.102, alpha: 1)
    private static let inkSoft = UIColor(red: 0.42, green: 0.42, blue: 0.42, alpha: 1)
    private static let rule = UIColor(red: 0.886, green: 0.863, blue: 0.824, alpha: 1)
    private static let terra = UIColor(red: 0.753, green: 0.325, blue: 0.169, alpha: 1)

    private static func render(periodLabel: String, disciplineLabel: String,
                               summary s: JournalStatsCalculator.Summary,
                               maxGrade: String?,
                               progression: JournalStatsCalculator.Progression?) -> UIImage {
        let w: CGFloat = 1080, h: CGFloat = 1920
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)
            .image { ctx in
                let c = ctx.cgContext
                paper.setFill(); c.fill(CGRect(x: 0, y: 0, width: w, height: h))
                rule.setStroke(); c.setLineWidth(3)
                c.stroke(CGRect(x: 16, y: 16, width: w - 32, height: h - 32))

                func draw(_ text: String, x: CGFloat, y: CGFloat, font: UIFont,
                          color: UIColor, centered: Bool = true, kern: CGFloat = 0) {
                    let attrs: [NSAttributedString.Key: Any] =
                        [.font: font, .foregroundColor: color, .kern: kern]
                    let size = (text as NSString).size(withAttributes: attrs)
                    let px = centered ? x - size.width / 2 : x
                    (text as NSString).draw(at: CGPoint(x: px, y: y), withAttributes: attrs)
                }

                // N9: logo real de la app, circular con anillo terra.
                if let logo = UIImage(named: "logo_cumbre") {
                    let r: CGFloat = 84
                    let rect = CGRect(x: w / 2 - r, y: 88, width: r * 2, height: r * 2)
                    c.saveGState()
                    UIBezierPath(ovalIn: rect).addClip()
                    logo.draw(in: rect)
                    c.restoreGState()
                    terra.setStroke(); c.setLineWidth(4)
                    c.strokeEllipse(in: rect.insetBy(dx: -6, dy: -6))
                }
                let mono = UIFont.monospacedSystemFont(ofSize: 32, weight: .bold)
                draw("CUMBRE · \(disciplineLabel)", x: w / 2, y: 290,
                     font: mono, color: terra, kern: 6)
                draw(periodLabel, x: w / 2, y: 352,
                     font: UIFont(name: "Georgia-Bold", size: 92)
                        ?? .systemFont(ofSize: 92, weight: .bold),
                     color: ink)

                // 4 métricas 2×2.
                let metrics: [(String, String, Bool)] = [
                    ("\(s.daysOut)", "DÍAS DE ROCA", false),
                    ("\(s.currentStreakWeeks) sem", "RACHA", true),
                    ("\(s.projectsFallen)", "PROYECTOS CAÍDOS", false),
                    (maxGrade ?? "—", "GRADO MÁXIMO", true)
                ]
                let boxW = (w - 200) / 2, boxH: CGFloat = 220
                for (i, m) in metrics.enumerated() {
                    let col = CGFloat(i % 2), row = CGFloat(i / 2)
                    let left = 80 + col * (boxW + 40)
                    let top = 520 + row * (boxH + 28)
                    rule.setStroke()
                    c.stroke(CGRect(x: left, y: top, width: boxW, height: boxH))
                    draw(m.0, x: left + boxW / 2, y: top + 34,
                         font: UIFont(name: "Georgia-Bold", size: 82)
                            ?? .systemFont(ofSize: 82, weight: .bold),
                         color: m.2 ? terra : ink)
                    draw(m.1, x: left + boxW / 2, y: top + 152,
                         font: UIFont.monospacedSystemFont(ofSize: 26, weight: .bold),
                         color: inkSoft, kern: 3)
                }

                // Pirámide.
                var y: CGFloat = 1070
                draw("PIRÁMIDE DE GRADOS", x: 80, y: y,
                     font: UIFont.monospacedSystemFont(ofSize: 29, weight: .bold),
                     color: inkSoft, centered: false, kern: 5)
                y += 60
                let pyramid = Array(s.pyramid.prefix(6))
                let maxCount = max(pyramid.map { $0.second!.intValue }.max() ?? 1, 1)
                let barMaxW = w - 420
                for (i, pair) in pyramid.enumerated() {
                    let grade = pair.first! as String
                    let count = pair.second!.intValue
                    draw(grade, x: 80, y: y,
                         font: UIFont.monospacedSystemFont(ofSize: 42, weight: .bold),
                         color: i == 0 ? terra : ink, centered: false)
                    let barW = max(barMaxW * CGFloat(count) / CGFloat(maxCount), 24)
                    terra.withAlphaComponent(max(1 - CGFloat(i) * 0.09, 0.35)).setFill()
                    UIBezierPath(roundedRect: CGRect(x: 220, y: y, width: barW, height: 50),
                                 cornerRadius: 12).fill()
                    draw("\(count)", x: 240 + barW, y: y + 6,
                         font: .systemFont(ofSize: 34), color: inkSoft, centered: false)
                    y += 72
                }

                if let bm = s.bestMonth {
                    y += 40
                    let months = ["enero", "febrero", "marzo", "abril", "mayo", "junio",
                                  "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]
                    let parts = bm.split(separator: "-")
                    let name = parts.count == 2 ? months[(Int(parts[1]) ?? 1) - 1] : bm
                    draw("Mejor mes: \(name) (\(s.bestMonthCount) ascensos)",
                         x: w / 2, y: y, font: .systemFont(ofSize: 42), color: ink)
                }

                // N9: últimas 12 semanas + grado por trimestre. FLUYEN tras el
                // bloque anterior (el layout fijo en 1560 se solapaba con "Mejor
                // mes"). Cada sección comprueba que cabe por encima del pie.
                let footerTop = h - 260
                if let p = progression {
                    var py: CGFloat = y + 80
                    if py + 110 < footerTop {
                        draw("ÚLT. 12 SEMANAS", x: 80, y: py,
                             font: UIFont.monospacedSystemFont(ofSize: 27, weight: .bold),
                             color: inkSoft, centered: false, kern: 4)
                        py += 44
                        let cellW = (w - 160 - 11 * 8) / 12
                        for (i, out) in p.weeksOut.enumerated() {
                            (out.boolValue ? terra : rule).setFill()
                            UIBezierPath(roundedRect: CGRect(x: 80 + CGFloat(i) * (cellW + 8), y: py,
                                                             width: cellW, height: 40),
                                         cornerRadius: 8).fill()
                        }
                        py += 66
                    }
                    let quarters = Array(p.maxGradePerQuarter.suffix(4))
                    if !quarters.isEmpty, py + 100 < footerTop {
                        let colW = (w - 160) / CGFloat(quarters.count)
                        for (i, pair) in quarters.enumerated() {
                            let qcx = 80 + colW * CGFloat(i) + colW / 2
                            draw(pair.second! as String, x: qcx, y: py,
                                 font: UIFont(name: "Georgia-Bold", size: 52)
                                    ?? .systemFont(ofSize: 52, weight: .bold),
                                 color: terra)
                            draw((pair.first! as String).components(separatedBy: "-").last ?? "",
                                 x: qcx, y: py + 62,
                                 font: UIFont.monospacedSystemFont(ofSize: 25, weight: .bold),
                                 color: inkSoft)
                        }
                    }
                }

                draw("Descarga Cumbre", x: w / 2, y: h - 230,
                     font: .systemFont(ofSize: 42), color: ink)
                draw("CUMBRE", x: w / 2, y: h - 150,
                     font: UIFont.monospacedSystemFont(ofSize: 38, weight: .bold),
                     color: terra, kern: 6)
            }
    }
}
