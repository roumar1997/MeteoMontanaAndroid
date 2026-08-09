import SwiftUI
import Shared

/// Rosa de los vientos del mapa: dice hacia dónde queda el norte cuando has
/// girado el mapa con dos dedos, y al tocarla lo devuelve al norte.
///
/// Ojo con lo que representa: aquí NO se dibuja el rumbo del móvil sino el
/// **giro del mapa**, que son cosas distintas. El rumbo del móvil es lo que usa
/// la brújula de elegir orientación (`CompassDial`).
///
/// Espejo de `CompassRose.kt` en Android.
struct CompassRoseIcon: View {
    var mapBearing: Double

    var body: some View {
        Canvas { ctx, size in
            // El mapa girado `bearing` grados en sentido horario deja el norte
            // `-bearing` respecto a la pantalla.
            dibujaAguja(ctx, size, rotacion: -mapBearing)
        }
        .frame(width: 22, height: 22)
    }
}

/// Brújula grande para elegir la orientación de una pared: enseña el rumbo del
/// MÓVIL, con su valor en grados escrito debajo.
///
/// El número se escribe a propósito: la brújula de un móvil se descalibra con
/// facilidad —mochilas con imanes, mosquetones, hierro cerca— y ver el grado
/// ayuda a desconfiar cuando pega un salto. Por eso esto informa y no decide:
/// la orientación la elige el usuario tocando su chip.
struct CompassDial: View {
    var headingDegrees: Double?

    var body: some View {
        ZStack {
            Circle().stroke(Cumbre.rule, lineWidth: 1)
            Canvas { ctx, size in
                if let h = headingDegrees { dibujaAguja(ctx, size, rotacion: h) }
            }
            // Letras fijas: la que gira es la aguja, como en una brújula real.
            VStack {
                Text("N").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                Spacer()
                Text("S").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            HStack {
                Text("O").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                Spacer()
                Text("E").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: 120, height: 120)
    }
}

/// Aguja de dos puntas: la que apunta al norte va en terracota.
private func dibujaAguja(_ ctx: GraphicsContext, _ size: CGSize, rotacion: Double) {
    let cx = size.width / 2, cy = size.height / 2
    let largo = min(size.width, size.height) * 0.42
    let ancho = min(size.width, size.height) * 0.16

    var ctx = ctx
    ctx.translateBy(x: cx, y: cy)
    ctx.rotate(by: .degrees(rotacion))

    func punta(_ haciaArriba: Bool, _ color: Color) {
        let s: CGFloat = haciaArriba ? -1 : 1
        var p = Path()
        p.move(to: CGPoint(x: 0, y: s * largo))
        p.addLine(to: CGPoint(x: ancho, y: -s * largo * 0.22))
        p.addLine(to: CGPoint(x: 0, y: -s * largo * 0.05))
        p.addLine(to: CGPoint(x: -ancho, y: -s * largo * 0.22))
        p.closeSubpath()
        ctx.fill(p, with: .color(color))
    }
    punta(true, Cumbre.terra)
    punta(false, Cumbre.ink3)
    let r = min(size.width, size.height) * 0.06
    ctx.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)),
             with: .color(Cumbre.ink))
}
