import SwiftUI

// Onboarding de primera apertura — espejo de OnboardingOverlay.kt de Android.
// 2 pasos estilo Cumbre; el permiso de ubicación se pide DESPUÉS de explicar
// para qué sirve. El flag se persiste con @AppStorage.

struct OnboardingView: View {
    /// Se llama al terminar (pide ubicación y marca el onboarding como visto).
    let onFinish: () -> Void
    @State private var step = 0

    var body: some View {
        VStack {
            Spacer()
            VStack(spacing: 16) {
                if step == 0 { stepIndex } else { stepLocation }
            }
            Spacer()
            // Indicador de paso (2 puntos).
            HStack(spacing: 8) {
                ForEach(0..<2, id: \.self) { i in
                    Circle().fill(i == step ? Cumbre.terra : Cumbre.rule)
                        .frame(width: 8, height: 8)
                }
            }
            .padding(.bottom, 16)
            Button {
                if step == 0 { step = 1 } else { onFinish() }
            } label: {
                Text(step == 0 ? "SIGUIENTE" : "PERMITIR UBICACIÓN Y EMPEZAR")
                    .font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white)
                    .padding(.vertical, 16).frame(maxWidth: .infinity)
                    .background(Cumbre.terra)
            }
            .buttonStyle(.plain)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private var stepIndex: some View {
        VStack(spacing: 14) {
            Text("⛰").font(.system(size: 64))
            Text("EL ÍNDICE 0–100").eyebrow().foregroundStyle(Cumbre.terra)
            Text("Cumbre resume en un número las condiciones de escalada de cada escuela: temperatura, humedad, viento, lluvia reciente y cuánto tarda en secar su roca.")
                .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
            VStack(spacing: 6) {
                legend(Cumbre.score(80), "70+ a escalar")
                legend(Cumbre.score(58), "50–69 regular")
                legend(Cumbre.score(30), "<50 mal día")
            }
            .padding(.top, 4)
        }
    }

    private var stepLocation: some View {
        VStack(spacing: 14) {
            Text("📍").font(.system(size: 64))
            Text("TU UBICACIÓN").eyebrow().foregroundStyle(Cumbre.terra)
            Text("Te pediremos permiso de ubicación para ordenar las escuelas por cercanía y centrar el mapa donde estás. Solo se usa en tu móvil; nunca se comparte.")
                .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
        }
    }

    private func legend(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 10, height: 10)
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            Text(text).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
        }
    }
}
