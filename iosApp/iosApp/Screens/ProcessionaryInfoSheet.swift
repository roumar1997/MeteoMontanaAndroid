import SwiftUI

/// Hoja de "🐛 Procesionaria del pino" — espejo exacto de
/// ProcessionaryInfoSheet.kt de Android. Sin datos fiables de dónde hay
/// pinos, la única señal real es que alguien la haya visto.
struct ProcessionaryInfoSheet: View {
    let hasKnownProcessionary: Bool
    let alertActive: Bool
    let onConfirm: () -> Void
    let onRetract: () -> Void
    let onActiveNow: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("🐛 Procesionaria del pino")
                    .font(.system(size: 22, weight: .bold, design: .serif))
                    .foregroundStyle(Cumbre.ink)

                if alertActive {
                    Text("⚠ En esta escuela ya se han visto, y estamos en su época orientativa — extrema la precaución, sobre todo si vas con perro.")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Cumbre.bad)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Cumbre.bad.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                } else if hasKnownProcessionary {
                    Text("Aquí se han visto otros años, pero ahora mismo estamos fuera de su época orientativa (diciembre-mayo aprox.).")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink2)
                }

                Text("Son las orugas del pino, activas sobre todo en invierno y primavera. Sus pelillos son urticantes: para personas dan picor y alergia, pero para los perros pueden ser muy graves — si un perro las toca o las lame se le puede hinchar e incluso necrosar la lengua, y a veces hace falta amputarla para salvarlo. Mantén a tu perro alejado de los procesionarios (bolsas blancas en las ramas) y de las orugas en el suelo.")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)

                Text("No hay ningún mapa fiable de dónde hay pinos con procesionaria — la única forma de saberlo es que alguien las haya visto. Si las ves aquí, dilo: la escuela quedará marcada para avisar cada temporada, sin que nadie tenga que repetirlo.")
                    .font(.system(size: 13))
                    .foregroundStyle(Cumbre.ink2)

                if hasKnownProcessionary {
                    Text("Ya está confirmado — gracias por avisar.")
                        .font(.system(size: 15, weight: .semibold, design: .serif))
                        .foregroundStyle(Cumbre.terra)
                    Button(action: onRetract) {
                        Text("¿Te has equivocado al pulsar? Quitar aviso")
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink2)
                    }
                } else {
                    Button(action: onConfirm) {
                        Text("Las he visto")
                            .font(.system(size: 15, weight: .bold, design: .serif))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 10))
                    }
                }

                if !alertActive {
                    Button(action: onActiveNow) {
                        Text("Hay ahora mismo, antes de tiempo")
                            .font(.system(size: 15, weight: .bold, design: .serif))
                            .foregroundStyle(Cumbre.bad)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.bad, lineWidth: 1))
                    }
                    Text("Actívala aunque no sea su época típica — se apaga sola en unas semanas si nadie más la confirma.")
                        .font(.system(size: 12))
                        .foregroundStyle(Cumbre.ink2)
                }
            }
            .padding(24)
        }
        .presentationDetents([.medium, .large])
    }
}
