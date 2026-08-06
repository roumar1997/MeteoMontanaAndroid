import SwiftUI
import Shared

/// Lienzo de topo con zoom, espejo de `TopoZoomBox` de Android.
///
/// Las cuentas NO están aquí: vienen de `TopoCamera`, en el módulo compartido.
/// Si la conversión pantalla↔foto se escribiera una vez en Compose y otra en
/// SwiftUI, las dos versiones divergirían y el trazo acabaría guardándose en un
/// sitio distinto en cada app. Aquí solo viven los gestos, que sí son nativos.
///
/// Reparto, contando dedos:
/// - un dedo arrastrando → dibuja;
/// - un toque suelto → añade un punto;
/// - **pellizco → ampliar, y el trazo en curso se cancela** (sin eso, cada vez
///   que fueras a ampliar quedaría una rayita a medias en la roca);
/// - con el pellizco activo, arrastrar mueve la foto;
/// - doble toque → acercar ahí / volver a la vista completa.
struct TopoZoomLayer<Content: View>: View {

    var editable: Bool = false
    var loupeEnabled: Bool = true
    var onStrokeStart: (CGFloat, CGFloat) -> Void = { _, _ in }
    var onStrokePoint: (CGFloat, CGFloat) -> Void = { _, _ in }
    var onStrokeEnd: () -> Void = {}
    var onStrokeCancel: () -> Void = {}
    var onTap: (CGFloat, CGFloat) -> Void = { _, _ in }
    /// El contenido recibe el factor por el que dividir grosores: al ampliar,
    /// el trazo debe seguir midiendo lo mismo en pantalla y no engordar con la
    /// foto hasta tapar la roca.
    @ViewBuilder var content: (CGFloat) -> Content

    @State private var camera: TopoCamera = TopoCamera.companion.NONE
    @State private var pinching = false
    @State private var pinchBase: Float = 1
    @State private var panBase: CGSize = .zero
    @State private var drawing = false
    @State private var strokeMoved: CGFloat = 0
    @State private var lastPoint: CGPoint?
    @State private var fingerAt: CGPoint?

    var body: some View {
        GeometryReader { geo in
            let w = Float(geo.size.width)
            let h = Float(geo.size.height)

            ZStack(alignment: .topLeading) {
                content(CGFloat(camera.strokeFactor()))
                    .frame(width: geo.size.width, height: geo.size.height)
                    .scaleEffect(CGFloat(camera.scale), anchor: .topLeading)
                    .offset(x: CGFloat(camera.offsetX), y: CGFloat(camera.offsetY))

                if loupeEnabled, editable, let f = fingerAt {
                    loupe(at: f, size: geo.size)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
            .contentShape(Rectangle())
            // Pellizco: amplía sobre el punto donde empezó el gesto.
            .simultaneousGesture(
                MagnifyGesture(minimumScaleDelta: 0.01)
                    .onChanged { v in
                        if drawing {          // llegó el segundo dedo
                            drawing = false
                            fingerAt = nil
                            onStrokeCancel()
                        }
                        if !pinching {
                            pinching = true
                            pinchBase = camera.scale
                            panBase = .zero
                        }
                        let objetivo = pinchBase * Float(v.magnification)
                        let factor = objetivo / max(camera.scale, 0.001)
                        camera = camera.zoomBy(
                            factor: factor,
                            focusX: Float(v.startLocation.x), focusY: Float(v.startLocation.y),
                            viewW: w, viewH: h)
                    }
                    .onEnded { _ in pinching = false }
            )
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        if pinching {
                            // Con el pellizco en marcha, arrastrar mueve la foto.
                            let dx = v.translation.width - panBase.width
                            let dy = v.translation.height - panBase.height
                            panBase = v.translation
                            camera = camera.panBy(dx: Float(dx), dy: Float(dy), viewW: w, viewH: h)
                            return
                        }
                        let p = foto(v.location, w: w, h: h)
                        if editable {
                            if !drawing {
                                drawing = true
                                strokeMoved = 0
                                lastPoint = v.location
                                onStrokeStart(p.x, p.y)
                            } else {
                                if let l = lastPoint {
                                    strokeMoved += abs(v.location.x - l.x) + abs(v.location.y - l.y)
                                }
                                lastPoint = v.location
                                onStrokePoint(p.x, p.y)
                            }
                            fingerAt = v.location
                        } else if camera.scale > 1 {
                            // Visor: con la foto ampliada, un dedo la mueve.
                            let dx = v.translation.width - panBase.width
                            let dy = v.translation.height - panBase.height
                            panBase = v.translation
                            camera = camera.panBy(dx: Float(dx), dy: Float(dy), viewW: w, viewH: h)
                        }
                    }
                    .onEnded { v in
                        fingerAt = nil
                        panBase = .zero
                        let p = foto(v.location, w: w, h: h)
                        if drawing {
                            drawing = false
                            if strokeMoved < 12 {         // fue un toque, no un trazo
                                onStrokeCancel()
                                onTap(p.x, p.y)
                            } else {
                                onStrokeEnd()
                            }
                        } else if !pinching && !editable {
                            let movido = abs(v.translation.width) + abs(v.translation.height)
                            if movido < 12 { onTap(p.x, p.y) }
                        }
                        lastPoint = nil
                    }
            )
            .simultaneousGesture(
                SpatialTapGesture(count: 2)
                    .onEnded { v in
                        camera = camera.toggleZoomAt(
                            x: Float(v.location.x), y: Float(v.location.y),
                            viewW: w, viewH: h, zoomedScale: 2.5)
                    }
            )
        }
    }

    /// Punto de pantalla → punto de la foto (0..1), con la cámara compartida.
    private func foto(_ p: CGPoint, w: Float, h: Float) -> (x: CGFloat, y: CGFloat) {
        let r = camera.toPhoto(x: Float(p.x), y: Float(p.y), viewW: w, viewH: h)
        return (CGFloat(truncating: r.first ?? 0), CGFloat(truncating: r.second ?? 0))
    }

    /// Lupa: enseña ampliado lo que hay bajo el dedo, con una cruz en el punto
    /// exacto. Resuelve lo que el zoom no arregla — que el dedo tapa el punto.
    @ViewBuilder
    private func loupe(at p: CGPoint, size: CGSize) -> some View {
        let lado: CGFloat = 116
        let margen: CGFloat = 12
        let x = min(max(p.x - lado / 2, margen), size.width - lado - margen)
        let arriba = p.y - lado - margen * 2
        let y = arriba < margen
            ? min(p.y + margen * 2, size.height - lado - margen)
            : arriba
        let z = CGFloat(camera.scale) * 2.4

        ZStack {
            content(CGFloat(camera.strokeFactor()))
                .frame(width: size.width, height: size.height)
                .scaleEffect(z, anchor: .topLeading)
                .offset(x: -(p.x - CGFloat(camera.offsetX)) * 2.4 + lado / 2,
                        y: -(p.y - CGFloat(camera.offsetY)) * 2.4 + lado / 2)
            Circle().stroke(Color.white.opacity(0.95), lineWidth: 1.5).frame(width: 14, height: 14)
        }
        .frame(width: lado, height: lado)
        .background(Color.black)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.white, lineWidth: 2))
        .shadow(color: .black.opacity(0.5), radius: 5, y: 2)
        .offset(x: x, y: y)
        .allowsHitTesting(false)
    }
}
