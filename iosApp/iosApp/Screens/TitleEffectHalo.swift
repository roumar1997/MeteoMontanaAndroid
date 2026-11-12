import SwiftUI
import Shared

/// Efecto que un admin pone alrededor de la foto, DIRIGIDO POR EL SERVIDOR (GET /api/title-effects): sprites, colores
/// y movimiento vienen del catálogo, así que un efecto nuevo no necesita versión. Animado en el perfil y QUIETO en el
/// feed, los comentarios y los chats. Sustituye a la aureola normal. Espejo de `TitleEffectHalo.kt` de Android.
/// - FACE_PHOTO (hadas, dragones): dan vueltas mirando siempre hacia la foto.
/// - ORBIT (Furgo Lola): «conducen» alrededor, tangentes al círculo, ruedas hacia la foto, sentido horario.
/// Animación a 30 fps como mucho y parada en segundo plano, fuera de pantalla o con «Reducir movimiento».
struct TitleEffectHalo: View {
    let style: TitleEffectStyle
    /// Diámetro de la foto a la que rodea.
    let size: CGFloat
    let animated: Bool

    init(effect: TitleEffectSpec, size: CGFloat, animated: Bool) {
        let style = TitleEffectStyle(effect)
        self.style = style
        self.size = size
        self.animated = animated
        // Las empaquetadas ya desde el primer pintado (como antes): en el feed y los chats no aparecen «un instante después».
        _images = State(initialValue: TitleEffectHalo.bundled(style))
    }

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false
    @State private var images: [UIImage?] = []

    /// Lo que dibuja ocupa más que la foto: el marco crece y se centra sobre ella sin mover nada alrededor.
    private var frameSide: CGFloat { size * (animated ? 3.0 : 1.9) }
    private var moving: Bool { animated && !reduceMotion }
    private var running: Bool { moving && visible && scenePhase == .active }

    var body: some View {
        Group {
            if moving {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !running)) { tl in
                    canvas(time: tl.date.timeIntervalSinceReferenceDate)
                }
            } else {
                canvas(time: 0)
            }
        }
        .frame(width: frameSide, height: frameSide)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { visible = true }
        .onDisappear { visible = false }
        .task(id: style.key) { await loadSprites() }
    }

    /// Las figuras que vienen dentro de la app (nil = esa figura solo existe en el CDN).
    private static func bundled(_ style: TitleEffectStyle) -> [UIImage?] {
        style.assets.map { name in name.flatMap { UIImage(named: $0) } }
    }

    /// Primero las empaquetadas (al momento); luego las del CDN, con la caché en disco de las fotos.
    private func loadSprites() async {
        images = TitleEffectHalo.bundled(style)
        for (i, url) in style.urls.enumerated() {
            guard let url, !url.isEmpty, let img = await ImageCache.image(url) else { continue }
            if i < images.count { images[i] = img }
        }
    }

    private func canvas(time: Double) -> some View {
        let sprites = images
        return Canvas { ctx, canvasSize in
            draw(&ctx, side: canvasSize.width, t: moving ? time : 0, sprites: sprites)
        }
    }

    private func draw(_ ctx: inout GraphicsContext, side: CGFloat, t: Double, sprites: [UIImage?]) {
        let c = CGPoint(x: side / 2, y: side / 2)
        let r = size / 2
        drawGlow(&ctx, c: c, r: r)
        if animated { drawRibbons(&ctx, c: c, r: r, t: t) }
        drawRing(&ctx, c: c, r: r, t: t)
        drawTwinkles(&ctx, c: c, r: r, t: t)
        drawFigures(&ctx, c: c, r: r, t: t, sprites: sprites)
    }

    private func drawGlow(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat) {
        let outer = r * (animated ? 2.1 : 1.6)
        let rect = CGRect(x: c.x - outer, y: c.y - outer, width: outer * 2, height: outer * 2)
        ctx.fill(Path(ellipseIn: rect), with: .radialGradient(
            Gradient(colors: [style.glow.opacity(0.38), style.glow.opacity(0)]),
            center: c, startRadius: r * 0.85, endRadius: outer))
    }

    /// Tres «cintas» de luz que giran (solo animado).
    private func drawRibbons(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double) {
        for k in 0..<3 {
            var g = ctx
            g.translateBy(x: c.x, y: c.y)
            g.rotate(by: .radians(t * 0.32 * (k % 2 == 0 ? 1 : -1) + Double(k) * 2.1))
            let rx = r * (1.55 + CGFloat(k) * 0.12), ry = r * (0.55 + CGFloat(k) * 0.1)
            var p = Path()
            p.addArc(center: .zero, radius: 1, startAngle: .radians(.pi * 0.1), endAngle: .radians(.pi * 0.88), clockwise: false)
            let arc = p.applying(CGAffineTransform(scaleX: rx, y: ry))
            g.stroke(arc, with: .linearGradient(
                Gradient(colors: [style.glow.opacity(0), style.highlight.opacity(0.9), style.glow.opacity(0)]),
                startPoint: CGPoint(x: -rx, y: 0), endPoint: CGPoint(x: rx, y: 0)),
                style: StrokeStyle(lineWidth: k == 1 ? 2.5 : 1.5, lineCap: .round))
        }
    }

    /// Aro con el degradado del catálogo, cerrado (vuelve al primer color) y girando.
    private func drawRing(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double) {
        let w = max(2, size * (animated ? 0.07 : 0.11))
        let rr = r + w / 2
        var g = ctx
        g.translateBy(x: c.x, y: c.y)
        g.rotate(by: .radians(t * 1.1))
        g.stroke(Path(ellipseIn: CGRect(x: -rr, y: -rr, width: rr * 2, height: rr * 2)),
                 with: .conicGradient(Gradient(colors: style.ring + [style.ring[0]]), center: .zero),
                 lineWidth: w)
    }

    /// Estrellitas o brasas fijas alrededor que parpadean.
    private func drawTwinkles(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double) {
        let count = animated ? 18 : 6
        for i in 0..<count {
            let seed = Double(i)
            let angle = seed * 2.399963 + (animated ? t * 0.06 : 0)          // ángulo áureo: repartidas sin amontonarse
            let dist = animated ? 1.25 + (seed.truncatingRemainder(dividingBy: 7)) / 7 * 0.85 : 1.1 + (seed.truncatingRemainder(dividingBy: 3)) * 0.07
            let alpha = animated ? 0.25 + 0.75 * abs(sin(t * 1.7 + seed * 1.3)) : 0.8
            let p = CGPoint(x: c.x + CGFloat(cos(angle)) * r * CGFloat(dist), y: c.y + CGFloat(sin(angle)) * r * CGFloat(dist))
            let sr = r * (animated ? 0.05 + 0.04 * CGFloat(seed.truncatingRemainder(dividingBy: 3)) : 0.07)
            sparkle(&ctx, at: p, radius: sr, alpha: alpha)
        }
    }

    private func figureAngle(_ i: Int, _ t: Double) -> Double {
        t * style.speed + Double(i) * 2 * .pi / Double(style.count)
    }

    private func drawFigures(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, t: Double, sprites: [UIImage?]) {
        guard !sprites.isEmpty else { return }
        let count = animated ? style.count : style.staticAngles.count
        for i in 0..<count {
            let angle = animated ? figureAngle(i, t) : style.staticAngles[i]
            if animated { drawTrail(&ctx, c: c, r: r, i: i, t: t) }
            // ORBIT: siempre una figura que mire a la derecha (sentido horario); voltearla pondría el rótulo en espejo.
            let si = (style.orbits ? style.facesRight.firstIndex(of: true) : nil) ?? (i % style.facesRight.count)
            guard let ui = sprites[si % sprites.count] else { continue }
            let facesRight = style.facesRight[si]
            let pos = figurePosition(c: c, r: r, angle: angle, i: i, t: t)
            let h = r * (animated ? style.size : style.staticSize)
            let ratio = ui.size.height > 0 ? ui.size.width / ui.size.height : 1
            let w = h * ratio
            var g = ctx
            g.translateBy(x: pos.x, y: pos.y)
            if style.orbits {
                // Tangente al círculo: con +90° la parte de abajo apunta al centro y el morro avanza en sentido horario.
                g.rotate(by: .radians(angle + .pi / 2))
                if !facesRight { g.scaleBy(x: -1, y: 1) }
            } else if (pos.x < c.x) != facesRight {
                // Mirando a la foto: a la izquierda del centro miran a la derecha y al revés.
                g.scaleBy(x: -1, y: 1)
            }
            g.addFilter(.shadow(color: style.highlight.opacity(0.8), radius: animated ? 6 : 2))
            g.draw(Image(uiImage: ui), in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))
        }
    }

    /// ORBIT: círculo exacto (para ir tangente). FACE_PHOTO: algo achatado y flotando.
    private func figurePosition(c: CGPoint, r: CGFloat, angle: Double, i: Int, t: Double) -> CGPoint {
        let orbit = animated ? style.orbit : 1.18
        if style.orbits {
            return CGPoint(x: c.x + CGFloat(cos(angle)) * r * orbit, y: c.y + CGFloat(sin(angle)) * r * orbit)
        }
        let bob = animated ? CGFloat(sin(t * 2 + Double(i))) * r * 0.08 : 0
        return CGPoint(x: c.x + CGFloat(cos(angle)) * r * orbit,
                       y: c.y + CGFloat(sin(angle)) * r * orbit * (animated ? 0.9 : 1) + bob)
    }

    /// Estela: brillos (o brasas) en las posiciones por las que acaba de pasar, cada vez más tenues. Sin estado.
    private func drawTrail(_ ctx: inout GraphicsContext, c: CGPoint, r: CGFloat, i: Int, t: Double) {
        for k in 1...7 {
            let past = t - Double(k) * 0.11
            var p = figurePosition(c: c, r: r, angle: figureAngle(i, past), i: i, t: past)
            if !style.orbits { p.y += r * (style.embers ? -0.1 * CGFloat(k) : 0.35) }
            let alpha = (1 - Double(k) / 8) * 0.85
            sparkle(&ctx, at: p, radius: r * (0.09 - 0.008 * CGFloat(k)), alpha: alpha)
        }
    }

    /// Estrella de cuatro puntas o brasa en rombo.
    private func sparkle(_ ctx: inout GraphicsContext, at p: CGPoint, radius s: CGFloat, alpha: Double) {
        var path = Path()
        if style.embers {
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
        ctx.fill(path, with: .color(style.particle.opacity(alpha)))
    }
}
