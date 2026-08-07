import SwiftUI
import UIKit
import Shared

/// Lienzo de topos de iOS, en UIKit — espejo de `TopoZoomBox` de Android.
///
/// Antes esto eran gestos de SwiftUI (`MagnifyGesture` + `DragGesture`) sobre un
/// `Canvas`, y daba dos problemas que no se arreglaron con dos intentos de
/// parche: el trazo no aparecía hasta soltar el dedo, y mover la foto ampliada
/// iba a trompicones. Las dos cosas son la misma causa: en SwiftUI ni los
/// toques ni el momento de repintar son nuestros. Android reparte los dedos con
/// su propio bucle y repinta cuando quiere, y por eso allí va fino.
///
/// Aquí se hace igual: toques crudos y `setNeedsDisplay`.
///
/// **Reparto de gestos**, contando dedos, igual que Android:
/// - un dedo arrastrando → dibuja;
/// - un toque suelto → añade un punto;
/// - dos dedos → ampliar y mover, y el trazo en curso se CANCELA (si no, cada
///   vez que fueras a ampliar quedaría una rayita basura);
/// - doble toque → acercar ahí / volver a la vista completa.
///
/// **El trazo en curso lo dibuja el propio lienzo.** Es lo único que decide, y
/// tiene su razón: es quien recibe los dedos, y esperar a que el estado suba a
/// SwiftUI y vuelva es exactamente lo que hacía que la línea no se viera. El
/// dueño de los datos sigue siendo quien llama: le pregunta, en cada punto,
/// "¿cómo queda la vía ahora?" y pinta lo que le responda.

/// Lo que hay que pintar. Sin SwiftUI: son datos, no vistas.
struct TopoScene {
    /// Vías difuminadas al fondo (la versión vieja de la que se corrige).
    var faded: [(points: [CGPoint], grade: String?)] = []
    var vias: [TopoVia] = []
    /// Vértices agarrables de la vía en edición.
    var dots: [CGPoint] = []
    /// Cuáles de esos vértices han quedado UNIDOS a otra vía (anillo blanco).
    var joined: Set<Int> = []
    /// Índice, dentro de `vias`, de la que se está trazando ahora. Es la que se
    /// repinta desde el trazo vivo del lienzo.
    var liveIndex: Int? = nil
    /// Tamaños, en función del factor de ampliación.
    var style: (CGFloat) -> TopoStyle = { TopoStyle.photoScaled($0) }
    /// Opacidad de las difuminadas (el editor y la ficha usan valores distintos).
    var fadedAlpha: CGFloat = 0.4
}

final class TopoCanvasUIView: UIView {

    var image: UIImage? { didSet { setNeedsDisplay() } }
    var scene = TopoScene() { didSet { setNeedsDisplay() } }
    var editable = false

    /// Devuelven cómo queda la vía que se está trazando, para pintarla ya.
    var onStrokeStart: ((CGFloat, CGFloat) -> [CGPoint])?
    var onStrokePoint: ((CGFloat, CGFloat) -> [CGPoint])?
    var onStrokeEnd: (() -> Void)?
    var onStrokeCancel: (() -> Void)?
    var onTap: ((CGFloat, CGFloat) -> Void)?
    /// Solo se avisa cuando cambia la AMPLIACIÓN, no al mover: mover no cambia
    /// los grosores, y avisar en cada fotograma haría trabajar a SwiftUI para
    /// nada justo mientras el dedo se desliza.
    var onZoomChange: ((CGFloat) -> Void)?

    private var camera = TopoCamera.companion.NONE
    private var live: [CGPoint]?
    private var dibujando = false
    private var multiTouch = false
    private var movido: CGFloat = 0
    private var ultimo: CGPoint = .zero
    private var inicio: CGPoint = .zero
    private var pinchPrev: (dist: CGFloat, centro: CGPoint)?
    /// Scroll del contenedor, apagado mientras la foto está ampliada.
    private weak var scrollPausado: UIScrollView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        isMultipleTouchEnabled = true
        clipsToBounds = true
        contentMode = .redraw

        let doble = UITapGestureRecognizer(target: self, action: #selector(dobleToque))
        doble.numberOfTapsRequired = 2
        // Sin esto, el reconocedor corta el flujo de toques y se pierde el
        // trazo que ya estaba en marcha.
        doble.cancelsTouchesInView = false
        addGestureRecognizer(doble)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    // ── Gestos ───────────────────────────────────────────────────────────

    @objc private func dobleToque(_ g: UITapGestureRecognizer) {
        let p = g.location(in: self)
        setCamera(camera.toggleZoomAt(x: Float(p.x), y: Float(p.y),
                                      viewW: Float(bounds.width), viewH: Float(bounds.height),
                                      zoomedScale: 2.5))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        let activos = (event?.touches(for: self) ?? touches).filter {
            $0.phase != .ended && $0.phase != .cancelled
        }
        if activos.count >= 2 {
            cancelarTrazo()
            multiTouch = true
            pinchPrev = nil
            return
        }
        guard let t = touches.first else { return }
        inicio = t.location(in: self)
        ultimo = inicio
        movido = 0
        multiTouch = false
        if editable {
            let p = foto(inicio)
            live = onStrokeStart?(p.x, p.y)
            dibujando = true
            setNeedsDisplay()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        let activos = (event?.touches(for: self) ?? touches).filter {
            $0.phase != .ended && $0.phase != .cancelled
        }
        if activos.count >= 2 {
            if dibujando { cancelarTrazo() }
            multiTouch = true
            let ps = activos.map { $0.location(in: self) }
            let a = ps[0], b = ps[1]
            let dist = hypot(b.x - a.x, b.y - a.y)
            let centro = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
            if let prev = pinchPrev, prev.dist > 0 {
                var c = camera
                let factor = dist / prev.dist
                if abs(factor - 1) > 0.0001 {
                    c = c.zoomBy(factor: Float(factor),
                                 focusX: Float(centro.x), focusY: Float(centro.y),
                                 viewW: Float(bounds.width), viewH: Float(bounds.height))
                }
                let dx = centro.x - prev.centro.x, dy = centro.y - prev.centro.y
                if dx != 0 || dy != 0 {
                    c = c.panBy(dx: Float(dx), dy: Float(dy),
                                viewW: Float(bounds.width), viewH: Float(bounds.height))
                }
                setCamera(c)
            }
            pinchPrev = (dist, centro)
            return
        }
        guard let t = activos.first ?? touches.first else { return }
        let p = t.location(in: self)
        movido += hypot(p.x - ultimo.x, p.y - ultimo.y)
        let d = CGPoint(x: p.x - ultimo.x, y: p.y - ultimo.y)
        ultimo = p

        if dibujando {
            let f = foto(p)
            live = onStrokePoint?(f.x, f.y)
            setNeedsDisplay()
        } else if !multiTouch && !camera.isIdentity {
            // Visor AMPLIADO: un dedo mueve la foto. Sin ampliar no se toca
            // nada, y así la lista de la ficha sigue desplazándose normal.
            setCamera(camera.panBy(dx: Float(d.x), dy: Float(d.y),
                                   viewW: Float(bounds.width), viewH: Float(bounds.height)))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        let quedan = (event?.touches(for: self) ?? []).filter {
            $0.phase != .ended && $0.phase != .cancelled
        }
        guard quedan.isEmpty else { return }
        terminar()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        cancelarTrazo()
        multiTouch = false
        pinchPrev = nil
    }

    private func terminar() {
        if dibujando {
            dibujando = false
            if movido < 12 {
                // Fue un toque, no un trazo: se descarta lo empezado y se trata
                // como "añadir un punto".
                onStrokeCancel?()
                let p = foto(inicio)
                onTap?(p.x, p.y)
            } else {
                onStrokeEnd?()
            }
        } else if !editable && movido < 12 {
            let p = foto(inicio)
            onTap?(p.x, p.y)
        }
        live = nil
        multiTouch = false
        pinchPrev = nil
        setNeedsDisplay()
    }

    private func cancelarTrazo() {
        guard dibujando else { return }
        dibujando = false
        live = nil
        onStrokeCancel?()
        setNeedsDisplay()
    }

    private func foto(_ p: CGPoint) -> (x: CGFloat, y: CGFloat) {
        let r = camera.toPhoto(x: Float(p.x), y: Float(p.y),
                               viewW: Float(bounds.width), viewH: Float(bounds.height))
        return (CGFloat(truncating: r.first ?? 0), CGFloat(truncating: r.second ?? 0))
    }

    private func setCamera(_ c: TopoCamera) {
        guard c != camera else { return }
        let antes = camera.strokeFactor()
        camera = c
        pausarScrollSiHaceFalta()
        setNeedsDisplay()
        if c.strokeFactor() != antes { onZoomChange?(CGFloat(c.strokeFactor())) }
    }

    /// Mientras la foto está ampliada, el scroll del contenedor se apaga: si no,
    /// se lleva el dedo a mitad de gesto y mover la foto va a saltos. Es el
    /// equivalente del `requestDisallowInterceptTouchEvent` de Android.
    private func pausarScrollSiHaceFalta() {
        if camera.isIdentity {
            scrollPausado?.isScrollEnabled = true
            scrollPausado = nil
            return
        }
        guard scrollPausado == nil else { return }
        var v: UIView? = superview
        while let actual = v {
            if let sv = actual as? UIScrollView {
                sv.isScrollEnabled = false
                scrollPausado = sv
                return
            }
            v = actual.superview
        }
    }

    override func willMove(toWindow newWindow: UIWindow?) {
        super.willMove(toWindow: newWindow)
        if newWindow == nil {          // al salir, devolver el scroll
            scrollPausado?.isScrollEnabled = true
            scrollPausado = nil
        }
    }

    // ── Dibujo ───────────────────────────────────────────────────────────

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext(), bounds.width > 0 else { return }
        ctx.saveGState()
        // La foto y las líneas se amplían JUNTAS con la misma transformación:
        // así no pueden desalinearse nunca.
        ctx.translateBy(x: CGFloat(camera.offsetX), y: CGFloat(camera.offsetY))
        ctx.scaleBy(x: CGFloat(camera.scale), y: CGFloat(camera.scale))

        if let img = image { img.draw(in: encajeLlenando(img.size)) }

        let z = CGFloat(camera.strokeFactor())
        let style = scene.style(z)
        let target = CGContextTarget(cg: ctx)

        for f in scene.faded where !f.points.isEmpty {
            let pts = f.points.map { CGPoint(x: $0.x * bounds.width, y: $0.y * bounds.height) }
            let color = UIColor(GradeColor.style(f.grade).stroke)
                .withAlphaComponent(scene.fadedAlpha)
            target.strokePath(pts, color: color, width: 4 * z, roundCap: true,
                              dash: [6 * z, 6 * z], dashPhase: 0)
        }

        // La vía que se está trazando se pinta desde el trazo VIVO del lienzo:
        // es el único que está al día en mitad del gesto.
        // Los grosores propios de cada via (el editor resalta la seleccionada)
        // se dividen por la ampliacion igual que los del estilo: si no, al 400%
        // engordan con la foto hasta tapar la roca.
        var vias = scene.vias.map { v -> TopoVia in
            guard let w = v.lineWidth else { return v }
            return TopoVia(number: v.number, grade: v.grade, startType: v.startType,
                           points: v.points, lineWidth: w * z, muted: v.muted)
        }
        if let live, let idx = scene.liveIndex, vias.indices.contains(idx) {
            vias[idx] = TopoVia(number: vias[idx].number, grade: vias[idx].grade,
                                startType: vias[idx].startType, points: live,
                                lineWidth: vias[idx].lineWidth, muted: vias[idx].muted)
        }
        TopoPainter.paint(target, vias: vias, size: bounds.size, style: style)

        // Vértices agarrables. NO se pintan mientras el dedo traza: en un
        // arrastre son cientos y lo único que se vería es una fila de puntitos
        // blancos encima de la línea.
        if !dibujando {
            for (i, p) in scene.dots.enumerated() {
                let c = CGPoint(x: p.x * bounds.width, y: p.y * bounds.height)
                target.fillCircle(c, radius: 5 * z, color: .white)
                if scene.joined.contains(i) {
                    // Unido a otra vía: anillo blanco alrededor.
                    ctx.setStrokeColor(UIColor.white.cgColor)
                    ctx.setLineWidth(2.5 * z)
                    ctx.strokeEllipse(in: CGRect(x: c.x - 10 * z, y: c.y - 10 * z,
                                                 width: 20 * z, height: 20 * z))
                }
            }
        }
        ctx.restoreGState()
    }

    /// Rectángulo para que la foto LLENE el marco conservando su proporción
    /// (equivalente de `scaledToFill`).
    private func encajeLlenando(_ tam: CGSize) -> CGRect {
        guard tam.width > 0, tam.height > 0 else { return bounds }
        let escala = max(bounds.width / tam.width, bounds.height / tam.height)
        let w = tam.width * escala, h = tam.height * escala
        return CGRect(x: (bounds.width - w) / 2, y: (bounds.height - h) / 2, width: w, height: h)
    }
}

/// Envoltorio SwiftUI. Mismo papel que `TopoZoomBox` en Compose: colocar el
/// lienzo y pasarle los avisos.
struct TopoCanvas: UIViewRepresentable {
    var image: UIImage?
    var scene: TopoScene
    var editable: Bool = false
    var onStrokeStart: (CGFloat, CGFloat) -> [CGPoint] = { _, _ in [] }
    var onStrokePoint: (CGFloat, CGFloat) -> [CGPoint] = { _, _ in [] }
    var onStrokeEnd: () -> Void = {}
    var onStrokeCancel: () -> Void = {}
    var onTap: (CGFloat, CGFloat) -> Void = { _, _ in }
    var onZoomChange: (CGFloat) -> Void = { _ in }

    func makeUIView(context: Context) -> TopoCanvasUIView { TopoCanvasUIView() }

    func updateUIView(_ v: TopoCanvasUIView, context: Context) {
        v.editable = editable
        v.onStrokeStart = onStrokeStart
        v.onStrokePoint = onStrokePoint
        v.onStrokeEnd = onStrokeEnd
        v.onStrokeCancel = onStrokeCancel
        v.onTap = onTap
        v.onZoomChange = onZoomChange
        v.image = image
        v.scene = scene
    }
}
