import SwiftUI

/// Efecto que un admin pone alrededor de la foto (Álvaro, 2026-10-19): HADAS doradas o DRAGONES rojos, con las figuras
/// recortadas de su propio diseño (`effect_*` en Assets). Animado en el perfil (dan vueltas mirando siempre hacia la
/// foto, con estela de brillos o brasas) y QUIETO en el feed, los comentarios y los chats. Sustituye a la aureola normal.
/// Mismos números que la maqueta aprobada; espejo de `TitleEffectHalo.kt` de Android.
struct TitleEffectHalo: View {
    let effect: TitleEffectKind
    /// Diámetro de la foto a la que rodea.
    let size: CGFloat
    let animated: Bool

    /// Lo que dibuja ocupa más que la foto: el marco crece y se centra sobre ella sin mover nada alrededor.
    private var frameSide: CGFloat { size * (animated ? 3.0 : 1.9) }

    var body: some View {
        Group {
            if animated {
                TimelineView(.animation) { tl in
                    canvas(time: tl.date.timeIntervalSinceReferenceDate)
                }
            } else {
                canvas(time: 0)
            }
        }
        .frame(width: frameSide, height: frameSide)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func canvas(time: Double) -> some View {
        Canvas { ctx, canvasSize in
            draw(&ctx, side: canvasSize.width, t: animated ? time : 0)
        }
    }

    private func draw(_ ctx: inout GraphicsContext, side: CGFloat, t: Double) {
        let c = CGPoint(x: side / 2, y: side / 2)
        let r = size / 2
        let theme = effect.theme
        drawGlow(&ctx, c: c, r: r, theme: theme)
        if animated { drawRibbons(&ctx, c: c, r: r, t: t, theme: theme) }
        drawRing(&ctx, c: c, r: r, t: t, theme: theme)
        drawTwinkles(&ctx, c: c, r: r, t: t, theme: theme)
        drawFigures(&ctx, c: c, r: r, t: t, theme: theme)
    }

    private func drawGlow(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, theme: TitleEffectTheme) {
        let outer = r * (animated ? 2.1 : 1.6)
        let rect = CGRect(x: c.x - outer, y: c.y - outer, width: outer * 2, height: outer * 2)
        ctx.fill(Path(ellipseIn: rect), with: .radialGradient(
            Gradient(colors: [theme.ring.opacity(0.38), theme.ring.opacity(0)]),
            center: c, startRadius: r * 0.85, endRadius: outer))
    }

    /// Tres «cintas» de luz que giran (solo animado).
    private func drawRibbons(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double, theme: TitleEffectTheme) {
        for k in 0..<3 {
            var g = ctx
            g.translateBy(x: c.x, y: c.y)
            g.rotate(by: .radians(t * 0.32 * (k % 2 == 0 ? 1 : -1) + Double(k) * 2.1))
            let rx = r * (1.55 + CGFloat(k) * 0.12), ry = r * (0.55 + CGFloat(k) * 0.1)
            var p = Path()
            p.addArc(center: .zero, radius: 1, startAngle: .radians(.pi * 0.1), endAngle: .radians(.pi * 0.88), clockwise: false)
            let arc = p.applying(CGAffineTransform(scaleX: rx, y: ry))
            g.stroke(arc, with: .linearGradient(
                Gradient(colors: [theme.ring.opacity(0), theme.highlight.opacity(0.9), theme.ring.opacity(0)]),
                startPoint: CGPoint(x: -rx, y: 0), endPoint: CGPoint(x: rx, y: 0)),
                style: StrokeStyle(lineWidth: k == 1 ? 2.5 : 1.5, lineCap: .round))
        }
    }

    private func drawRing(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double, theme: TitleEffectTheme) {
        let w = max(2, size * (animated ? 0.07 : 0.11))
        let rr = r + w / 2
        var g = ctx
        g.translateBy(x: c.x, y: c.y)
        g.rotate(by: .radians(t * 1.1))
        g.stroke(Path(ellipseIn: CGRect(x: -rr, y: -rr, width: rr * 2, height: rr * 2)),
                 with: .conicGradient(Gradient(colors: [theme.ring, theme.highlight, theme.ring, theme.highlight.opacity(0.85), theme.ring]),
                                      center: .zero),
                 lineWidth: w)
    }

    /// Estrellitas (hadas) o brasas (dragones) fijas alrededor que parpadean.
    private func drawTwinkles(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double, theme: TitleEffectTheme) {
        let count = animated ? 18 : 6
        for i in 0..<count {
            let seed = Double(i)
            let angle = seed * 2.399963 + (animated ? t * 0.06 : 0)          // ángulo áureo: repartidas sin amontonarse
            let dist = animated ? 1.25 + (seed.truncatingRemainder(dividingBy: 7)) / 7 * 0.85 : 1.1 + (seed.truncatingRemainder(dividingBy: 3)) * 0.07
            let alpha = animated ? 0.25 + 0.75 * abs(sin(t * 1.7 + seed * 1.3)) : 0.8
            let p = CGPoint(x: c.x + CGFloat(cos(angle)) * r * CGFloat(dist), y: c.y + CGFloat(sin(angle)) * r * CGFloat(dist))
            let sr = r * (animated ? 0.05 + 0.04 * CGFloat(seed.truncatingRemainder(dividingBy: 3)) : 0.07)
            sparkle(&ctx, at: p, radius: sr, theme: theme, alpha: alpha)
        }
    }

    private func drawFigures(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double, theme: TitleEffectTheme) {
        let count = animated ? theme.count : theme.staticAngles.count
        for i in 0..<count {
            let name = theme.sprites[i % theme.sprites.count]
            let angle = animated ? t * theme.speed + Double(i) * 2 * .pi / Double(theme.count) : theme.staticAngles[i]
            let pos = figurePosition(c: c, r: r, angle: angle, i: i, t: t, theme: theme)
            if animated { drawTrail(&ctx, c: c, r: r, i: i, t: t, theme: theme) }
            let h = r * (animated ? theme.size : theme.staticSize)
            let resolved = ctx.resolve(Image(name))
            let ratio = resolved.size.height > 0 ? resolved.size.width / resolved.size.height : 1
            let w = h * ratio
            // Siempre mirando hacia la foto: a la izquierda del centro miran a la derecha y al revés.
            let wantsRight = pos.x < c.x
            let facesRight = theme.facesRight[name] ?? true
            var g = ctx
            g.translateBy(x: pos.x, y: pos.y)
            if wantsRight != facesRight { g.scaleBy(x: -1, y: 1) }
            g.addFilter(.shadow(color: theme.highlight.opacity(0.8), radius: animated ? 6 : 2))
            g.draw(resolved, in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))
        }
    }

    private func figurePosition(c: CGPoint, r: CGFloat, angle: Double, i: Int, t: Double, theme: TitleEffectTheme) -> CGPoint {
        let orbit = animated ? theme.orbit : 1.18
        let bob = animated ? CGFloat(sin(t * 2 + Double(i))) * r * 0.08 : 0
        return CGPoint(x: c.x + CGFloat(cos(angle)) * r * orbit,
                       y: c.y + CGFloat(sin(angle)) * r * orbit * (animated ? 0.9 : 1) + bob)
    }

    /// Estela: brillos (o brasas) en las posiciones por las que acaba de pasar, cada vez más tenues. Sin estado.
    private func drawTrail(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, i: Int, t: Double, theme: TitleEffectTheme) {
        for k in 1...7 {
            let past = t - Double(k) * 0.11
            let angle = past * theme.speed + Double(i) * 2 * .pi / Double(theme.count)
            var p = figurePosition(c: c, r: r, angle: angle, i: i, t: past, theme: theme)
            p.y += r * (theme.embers ? -0.1 * CGFloat(k) : 0.35)
            let alpha = (1 - Double(k) / 8) * 0.85
            sparkle(&ctx, at: p, radius: r * (0.09 - 0.008 * CGFloat(k)), theme: theme, alpha: alpha)
        }
    }

    /// Estrella de cuatro puntas (hadas) o brasa en rombo (dragones).
    private func sparkle(_ ctx: inout GraphicsContext, at p: CGPoint, radius s: CGFloat, theme: TitleEffectTheme, alpha: Double) {
        var path = Path()
        if theme.embers {
            path.move(to: CGPoint(x: p.x, y: p.y - s * 1.6))
            path.addLine(to: CGPoint(x: p.x + s * 0.7, y: p.y))
            path.addLine(to: CGPoint(x: p.x, y: p.y + s * 0.9))
            path.addLine(to: CGPoint(x: p.x - s * 0.7, y: p.y))
            path.closeSubpath()
        } else {
            path.move(to: CGPoint(x: p.x, y: p.y - s))
            path.addQuadCurve(to: CGPoint(x: p.x + s, y: p.y), control: p)
            path.addQuadCurve(to: CGPoint(x: p.x, y: p.y + s), control: p)
            path.addQuadCurve(to: CGPoint(x: p.x - s, y: p.y), control: p)
            path.addQuadCurve(to: CGPoint(x: p.x, y: p.y - s), control: p)
        }
        ctx.fill(path, with: .color(theme.particle.opacity(alpha)))
    }
}

/// Qué efecto lleva una persona (lo que manda el servidor en `titleEffect`).
enum TitleEffectKind {
    case fairies, dragons

    init?(_ raw: String?) {
        switch raw?.uppercased() {
        case "FAIRIES": self = .fairies
        case "DRAGONS": self = .dragons
        default: return nil
        }
    }

    var theme: TitleEffectTheme {
        switch self {
        case .fairies:
            return TitleEffectTheme(
                ring: Color(red: 212 / 255, green: 162 / 255, blue: 28 / 255),
                highlight: Color(red: 1, green: 236 / 255, blue: 170 / 255),
                particle: Color(red: 1, green: 236 / 255, blue: 170 / 255),
                sprites: ["effect_fairy1", "effect_fairy2", "effect_fairy3", "effect_fairy4"],
                facesRight: ["effect_fairy1": true, "effect_fairy2": true, "effect_fairy3": true, "effect_fairy4": true],
                count: 4, orbit: 1.72, size: 1.25, speed: 0.42, embers: false,
                staticAngles: [-2.3, 0.3], staticSize: 0.62)
        case .dragons:
            return TitleEffectTheme(
                ring: Color(red: 196 / 255, green: 40 / 255, blue: 28 / 255),
                highlight: Color(red: 1, green: 170 / 255, blue: 90 / 255),
                particle: Color(red: 1, green: 120 / 255, blue: 40 / 255),
                sprites: ["effect_dragon1", "effect_dragon2"],
                facesRight: ["effect_dragon1": true, "effect_dragon2": false],
                count: 2, orbit: 1.62, size: 1.85, speed: 0.33, embers: true,
                staticAngles: [-2.2], staticSize: 0.95)
        }
    }
}

/// Colores, figuras y números de cada efecto (los de la maqueta aprobada).
struct TitleEffectTheme {
    let ring: Color
    let highlight: Color
    let particle: Color
    let sprites: [String]
    /// Hacia dónde mira cada figura en la imagen original.
    let facesRight: [String: Bool]
    let count: Int
    /// Distancia al centro, en radios de la foto.
    let orbit: CGFloat
    /// Alto de la figura, en radios de la foto.
    let size: CGFloat
    /// Velocidad de giro, en radianes por segundo.
    let speed: Double
    let embers: Bool
    /// En la foto pequeña (quieta): en qué ángulos van las figuras y su tamaño.
    let staticAngles: [Double]
    let staticSize: CGFloat
}
