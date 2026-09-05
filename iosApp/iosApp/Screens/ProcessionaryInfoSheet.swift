import SwiftUI

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
                Text("🐛 Procesionaria del pino")
                    .font(.system(size: 22, weight: .bold, design: .serif))
                    .foregroundStyle(Cumbre.ink)

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
                Text("Ambos se pueden marcar y desmarcar — si te equivocas al pulsar, vuelve a pulsar para quitarlo.")
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
