import SwiftUI

/// Silueta de oruga peluda en diagonal (cabeza oscura abajo, cola arriba, con
/// pelillos de punta en abanico) — dibujo elegido por Álvaro de una lámina de
/// referencia de 16 poses (2026-09-05, "usa la 1"). Espejo exacto de
/// ProcessionaryIcon en Android.
struct ProcessionaryIcon: View {
    var tint: Color

    var body: some View {
        Canvas { context, size in
            let s = size.width / 64
            // Segmentos del cuerpo (de la cabeza hacia la cola), en un espacio de 64x64.
            let segs: [(CGPoint, CGFloat)] = [
                (CGPoint(x: 15.7, y: 39.2), 7.4),
                (CGPoint(x: 21.5, y: 33.2), 6.9),
                (CGPoint(x: 27.9, y: 27.7), 6.4),
                (CGPoint(x: 34.1, y: 23.5), 5.9),
                (CGPoint(x: 40.4, y: 20.2), 5.4),
                (CGPoint(x: 47.4, y: 17.6), 4.9),
                (CGPoint(x: 54.0, y: 16.0), 4.4)
            ]
            let headCenter = CGPoint(x: 10, y: 46)
            let headRadius: CGFloat = 8.4

            // Cuerpo: cápsulas superpuestas, más gruesas cerca de la cabeza.
            for (pt, r) in segs {
                let c = CGPoint(x: pt.x * s, y: pt.y * s)
                let dot = Path(ellipseIn: CGRect(x: c.x - r * s, y: c.y - r * s, width: r * s * 2, height: r * s * 2))
                context.fill(dot, with: .color(tint))
            }

            // Pelillos de punta: 3 por segmento, en abanico hacia arriba.
            for (pt, r) in segs {
                let cx = pt.x * s
                let cy = (pt.y - r * 0.6) * s
                let len = r * 1.7 * s
                for degFromUp: Double in [-35, -8, 20] {
                    let angle = (degFromUp - 90) * .pi / 180
                    let dx = len * cos(angle)
                    let dy = len * sin(angle)
                    var spike = Path()
                    spike.move(to: CGPoint(x: cx, y: cy))
                    spike.addLine(to: CGPoint(x: cx + dx, y: cy + dy))
                    context.stroke(spike, with: .color(tint), style: StrokeStyle(lineWidth: 1.2 * s, lineCap: .round))
                }
            }

            // Cabeza oscura + un par de patitas.
            let hc = CGPoint(x: headCenter.x * s, y: headCenter.y * s)
            let head = Path(ellipseIn: CGRect(x: hc.x - headRadius * s, y: hc.y - headRadius * s, width: headRadius * s * 2, height: headRadius * s * 2))
            context.fill(head, with: .color(tint))
            var leg1 = Path(); leg1.move(to: CGPoint(x: hc.x - 1 * s, y: hc.y + 7 * s)); leg1.addLine(to: CGPoint(x: hc.x - 4 * s, y: hc.y + 12 * s))
            context.stroke(leg1, with: .color(tint), style: StrokeStyle(lineWidth: 1.3 * s, lineCap: .round))
            var leg2 = Path(); leg2.move(to: CGPoint(x: hc.x + 3 * s, y: hc.y + 7 * s)); leg2.addLine(to: CGPoint(x: hc.x + 2 * s, y: hc.y + 13 * s))
            context.stroke(leg2, with: .color(tint), style: StrokeStyle(lineWidth: 1.3 * s, lineCap: .round))
        }
    }
}

/// Hoja de "🐛 Procesionaria del pino" — espejo exacto de
/// ProcessionaryInfoSheet.kt de Android. Sin datos fiables de dónde hay
/// pinos, la única señal real es que alguien la haya visto.
///
/// Observa el ViewModel directamente (en vez de recibir Bools sueltos por
/// parámetro): `.sheet(isPresented:)` construye este `body` de nuevo en cada
/// cambio de un `@Published` que la vista observa, pero solo si algo dentro
/// del propio sheet está suscrito a ese cambio — un `let` capturado al abrir
/// no se entera. Con `@ObservedObject` sí (Álvaro, 2026-09-05: "no os cuenta
/// cuando pulsas... si no recargas no se ve").
struct ProcessionaryInfoSheet: View {
    @ObservedObject var vm: SchoolDetailViewModel
    let onConfirm: () -> Void
    let onRetract: () -> Void
    let onActiveNow: () -> Void
    let onClearActiveNow: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    ProcessionaryIcon(tint: Cumbre.ink).frame(width: 26, height: 26)
                    Text("Procesionaria del pino")
                        .font(.system(size: 22, weight: .bold, design: .serif))
                        .foregroundStyle(Cumbre.ink)
                }
                Text("Época habitual: de diciembre a mayo (orientativo).")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2)

                // Botones arriba del todo: lo primero que hay que poder
                // pulsar, sin scroll de por medio.
                ToggleButton(
                    label: "Sí que hay en este sector",
                    pressed: vm.hasKnownProcessionary,
                    accent: Cumbre.terraFill,
                    action: { vm.hasKnownProcessionary ? onRetract() : onConfirm() }
                )
                ToggleButton(
                    label: "Las he visto antes de tiempo",
                    pressed: vm.processionaryActiveNowSet,
                    accent: Cumbre.bad,
                    action: { vm.processionaryActiveNowSet ? onClearActiveNow() : onActiveNow() }
                )
                Text("\"Sí que hay en este sector\" marca la escuela para siempre: cada diciembre-mayo avisará sola, sin que nadie tenga que repetirlo. \"Las he visto antes de tiempo\" enciende el aviso YA, aunque estemos fuera de esos meses. Ambos se pueden marcar y desmarcar — si te equivocas al pulsar, vuelve a pulsar para quitarlo.")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2)

                if vm.processionaryAlertActive {
                    Text("⚠ En esta escuela ya se han visto, y estamos en su época orientativa (más o menos) — extrema la precaución, sobre todo si vas con perro.")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Cumbre.bad)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Cumbre.bad.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                } else if vm.hasKnownProcessionary {
                    Text("Aquí se han visto otros años, pero ahora mismo estamos fuera de su época orientativa (diciembre-mayo aprox.).")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink2)
                }

                Text("Son las orugas del pino, activas sobre todo en invierno y primavera. Sus pelillos son urticantes: para personas dan picor y alergia, pero para los perros pueden ser muy graves — si un perro las toca o las lame se le puede hinchar e incluso necrosar la lengua, y a veces hace falta amputarla para salvarlo. Mantén a tu perro alejado de los procesionarios (bolsas blancas en las ramas) y de las orugas en el suelo.")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)

                Text("No hay ningún mapa fiable de dónde hay pinos con procesionaria — la única forma de saberlo es que alguien las haya visto. Si las ves aquí, marca \"Sí que hay en este sector\": la escuela quedará avisando cada temporada, sin que nadie tenga que repetirlo.")
                    .font(.system(size: 13))
                    .foregroundStyle(Cumbre.ink2)

                if vm.hasKnownProcessionary {
                    Text("Confirmado — gracias por avisar.")
                        .font(.system(size: 15, weight: .semibold, design: .serif))
                        .foregroundStyle(Cumbre.terra)
                }
                if !vm.processionaryAlertActive {
                    Text("\"Las he visto antes de tiempo\" activa el aviso aunque no sea su época típica — se apaga sola en unas semanas si nadie más la confirma.")
                        .font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink2)
                }
            }
            .padding(24)
        }
        .presentationDetents([.large])
    }
}

/// Botón-toggle: relleno cuando está pulsado, solo borde cuando no — para que
/// se note a simple vista que algo quedó marcado (Álvaro, 2026-09-05: "que se
/// note que lo has marcado").
private struct ToggleButton: View {
    let label: String
    let pressed: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                if pressed {
                    Text("✓")
                        .font(.system(size: 15, weight: .bold, design: .serif))
                        .foregroundStyle(.white)
                }
                Text(label)
                    .font(.system(size: 15, weight: .bold, design: .serif))
                    .foregroundStyle(pressed ? .white : accent)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(pressed ? accent : Color.clear, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent, lineWidth: 1))
        }
    }
}
