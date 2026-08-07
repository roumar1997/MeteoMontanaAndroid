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

/// Arrastre que empieza AL TOCAR, sin esperar a que el dedo se mueva. Un
/// `UIPanGestureRecognizer` normal no arranca hasta que hay unos milímetros de
/// movimiento, y en un editor de trazos eso se traduce en que el principio de
/// cada línea se pierde.
final class ArrastreInmediato: UIPanGestureRecognizer {
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        if state == .possible { state = .began }
    }
}

final class TopoCanvasUIView: UIView, UIGestureRecognizerDelegate {

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
    private var movido: CGFloat = 0
    private var inicio: CGPoint = .zero

    private let arrastre = ArrastreInmediato()
    private let pellizco = UIPinchGestureRecognizer()
    private let dobleToque = UITapGestureRecognizer()
    private let toqueSuelto = UITapGestureRecognizer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        isMultipleTouchEnabled = true
        clipsToBounds = true
        contentMode = .redraw

        arrastre.addTarget(self, action: #selector(alArrastrar))
        arrastre.delegate = self
        addGestureRecognizer(arrastre)

        pellizco.addTarget(self, action: #selector(alPellizcar))
        pellizco.delegate = self
        addGestureRecognizer(pellizco)

        dobleToque.addTarget(self, action: #selector(alDobleToque))
        dobleToque.numberOfTapsRequired = 2
        addGestureRecognizer(dobleToque)

        // El toque suelto solo lo gestiona el visor: en el editor, el punto se
        // coloca al terminar el arrastre (que empieza al tocar).
        toqueSuelto.addTarget(self, action: #selector(alTocar))
        toqueSuelto.require(toFail: dobleToque)
        addGestureRecognizer(toqueSuelto)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    // ── Negociación con los contenedores ─────────────────────────────────

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        // A los arrastres de los contenedores (la hoja que se cierra tirando
        // hacia abajo, el scroll de la ficha) se les pide que ESPEREN a que el
        // nuestro falle. Sin esto la hoja se lleva el dedo y no se puede
        // dibujar; con esto, y como el nuestro falla cuando no nos interesa el
        // gesto, cada uno recibe lo suyo.
        var v: UIView? = superview
        while let actual = v {
            actual.gestureRecognizers?.forEach { g in
                if g is UIPanGestureRecognizer { g.require(toFail: arrastre) }
            }
            v = actual.superview
        }
    }

    /// El arrastre solo nos interesa si vamos a dibujar o si la foto está
    /// ampliada. Si no, se deja pasar: la ficha sigue desplazándose normal.
    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        if g === arrastre { return editable || !camera.isIdentity }
        return true
    }

    func gestureRecognizer(_ g: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otro: UIGestureRecognizer) -> Bool {
        // Los nuestros sí conviven entre ellos: con dos dedos hay que ampliar y
        // mover a la vez. Con los de fuera, no.
        (g === arrastre || g === pellizco) && (otro === arrastre || otro === pellizco)
    }

    // ── Gestos ───────────────────────────────────────────────────────────

    @objc private func alDobleToque(_ g: UITapGestureRecognizer) {
        let p = g.location(in: self)
        setCamera(camera.toggleZoomAt(x: Float(p.x), y: Float(p.y),
                                      viewW: Float(bounds.width), viewH: Float(bounds.height),
                                      zoomedScale: 2.5))
    }

    @objc private func alTocar(_ g: UITapGestureRecognizer) {
        guard !editable else { return }
        let p = foto(g.location(in: self))
        onTap?(p.x, p.y)
    }

    @objc private func alPellizcar(_ g: UIPinchGestureRecognizer) {
        switch g.state {
        case .began:
            // Llegó el segundo dedo: se descarta el trazo a medias.
            cancelarTrazo()
        case .changed:
            let c = g.location(in: self)
            setCamera(camera.zoomBy(factor: Float(g.scale),
                                    focusX: Float(c.x), focusY: Float(c.y),
                                    viewW: Float(bounds.width), viewH: Float(bounds.height)))
            g.scale = 1
        default: break
        }
    }

    @objc private func alArrastrar(_ g: ArrastreInmediato) {
        let p = g.location(in: self)
        switch g.state {
        case .began:
            inicio = p
            movido = 0
            g.setTranslation(.zero, in: self)
            if editable && g.numberOfTouches <= 1 {
                let f = foto(p)
                live = onStrokeStart?(f.x, f.y)
                dibujando = true
                setNeedsDisplay()
            }
        case .changed:
            let t = g.translation(in: self)
            g.setTranslation(.zero, in: self)
            movido += abs(t.x) + abs(t.y)
            if g.numberOfTouches >= 2 {
                // Dos dedos: mover la foto (el pellizco se encarga de ampliar).
                if dibujando { cancelarTrazo() }
                setCamera(camera.panBy(dx: Float(t.x), dy: Float(t.y),
                                       viewW: Float(bounds.width), viewH: Float(bounds.height)))
            } else if dibujando {
                let f = foto(p)
                live = onStrokePoint?(f.x, f.y)
                setNeedsDisplay()
            } else if !camera.isIdentity {
                // Visor AMPLIADO: un dedo mueve la foto.
                setCamera(camera.panBy(dx: Float(t.x), dy: Float(t.y),
                                       viewW: Float(bounds.width), viewH: Float(bounds.height)))
            }
        case .ended:
            terminar()
        case .cancelled, .failed:
            cancelarTrazo()
        default: break
        }
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
        }
        live = nil
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
        setNeedsDisplay()
        if c.strokeFactor() != antes { onZoomChange?(CGFloat(c.strokeFactor())) }
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
