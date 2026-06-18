import SwiftUI

// Onboarding de primera apertura — espejo de OnboardingOverlay.kt de Android.
// Tour de 6 pasos estilo Cumbre, saltable; el permiso de ubicación se pide
// DESPUÉS de explicar para qué sirve. El flag se persiste con @AppStorage en RootView.

private struct OnbStep {
    let emoji: String
    let eyebrow: String
    let title: String
    let body: String
    var showScale = false
}

private let onbSteps: [OnbStep] = [
    OnbStep(emoji: "⛰", eyebrow: "BIENVENIDO", title: "Cumbre",
            body: "Tiempo para escalar. Te decimos cuándo y dónde se puede escalar, con la roca seca, en 191 escuelas."),
    OnbStep(emoji: "🌡", eyebrow: "EL ÍNDICE 0–100", title: "¿Hoy se puede?",
            body: "Un número resume las condiciones de cada escuela: temperatura, humedad, viento, lluvia reciente y cuánto tarda en secar SU tipo de roca (la arenisca tarda días; el granito, horas).",
            showScale: true),
    OnbStep(emoji: "🗺", eyebrow: "MAPA Y TOPOS", title: "Cada piedra, al detalle",
            body: "Abre una escuela y verás parkings, sectores y piedras en el mapa. Toca una piedra y aparece su foto con las VÍAS dibujadas, su grado y cómo llegar."),
    OnbStep(emoji: "📅", eyebrow: "PLANIFICA EL FINDE", title: "La mejor ventana",
            body: "Ventana óptima del día, mejor día de la semana, comparador de escuelas y selector de días. Marca favoritas y activa la alerta para que te avise cuando vaya a haber buen tiempo."),
    OnbStep(emoji: "🧗", eyebrow: "COMUNIDAD Y DIARIO", title: "Suma a la guía",
            body: "Propón escuelas, piedras y sectores, deja notas con foto, sigue a otros escaladores y chatea. Marca las vías que haces y lleva tu diario con tus estadísticas."),
    OnbStep(emoji: "📍", eyebrow: "OFFLINE + UBICACIÓN", title: "Listo para el monte",
            body: "Guarda escuelas para verlas SIN cobertura. Te pediremos la ubicación solo para ordenar por cercanía y centrar el mapa: se usa en tu móvil, nunca se comparte.")
]

struct OnboardingView: View {
    /// Se llama al terminar/saltar (pide ubicación y marca el onboarding como visto).
    let onFinish: () -> Void
    @State private var step = 0

    private var last: Int { onbSteps.count - 1 }

    var body: some View {
        VStack {
            // Saltar (arriba a la derecha) salvo en el último paso.
            HStack {
                Spacer()
                if step < last {
                    Button("Saltar") { onFinish() }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            let s = onbSteps[min(step, last)]
            VStack(spacing: 14) {
                Text(s.emoji).font(.system(size: 64))
                Text(s.eyebrow).eyebrow().foregroundStyle(Cumbre.terra)
                Text(s.title).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                    .multilineTextAlignment(.center)
                Text(s.body)
                    .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                    .multilineTextAlignment(.center)
                if s.showScale {
                    VStack(spacing: 6) {
                        legend(Cumbre.score(80), "70+ a escalar")
                        legend(Cumbre.score(58), "50–69 regular")
                        legend(Cumbre.score(30), "<50 mal día")
                    }.padding(.top, 4)
                }
            }
            Spacer()
            // Indicador de pasos.
            HStack(spacing: 8) {
                ForEach(0..<onbSteps.count, id: \.self) { i in
                    Circle().fill(i == step ? Cumbre.terra : Cumbre.rule)
                        .frame(width: 8, height: 8)
                }
            }
            .padding(.bottom, 16)
            Button {
                if step < last { step += 1 } else { onFinish() }
            } label: {
                Text(step < last ? "SIGUIENTE" : "PERMITIR UBICACIÓN Y EMPEZAR")
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
        .animation(.easeInOut(duration: 0.2), value: step)
    }

    private func legend(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 10, height: 10)
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            Text(text).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
        }
    }
}
