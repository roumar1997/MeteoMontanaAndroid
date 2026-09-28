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
    OnbStep(emoji: "⛰", eyebrow: L("BIENVENIDO"), title: "Cumbre",
            body: L("Tiempo para escalar. Te decimos cuándo y dónde se puede escalar, con la roca seca, en 191 escuelas.")),
    OnbStep(emoji: "🌡", eyebrow: L("EL ÍNDICE 0–100"), title: L("¿Hoy se puede?"),
            body: L("Un número resume las condiciones de cada escuela: temperatura, humedad, viento, lluvia reciente y cuánto tarda en secar SU tipo de roca (la arenisca tarda días; el granito, horas)."),
            showScale: true),
    OnbStep(emoji: "🗺", eyebrow: L("MAPA Y TOPOS"), title: L("Cada piedra, al detalle"),
            body: L("Abre una escuela y verás parkings, sectores y piedras en el mapa. Toca una piedra y aparece su foto con las VÍAS dibujadas, su grado y cómo llegar.")),
    OnbStep(emoji: "📅", eyebrow: L("PLANIFICA EL FINDE"), title: L("La mejor ventana"),
            body: L("Ventana óptima del día, mejor día de la semana, comparador de escuelas y selector de días para decidir adónde ir.")),
    OnbStep(emoji: "⭐", eyebrow: L("FAVORITAS Y ALERTAS"), title: L("No te pierdas el buen día"),
            body: L("Marca tus escuelas favoritas, míralas de un vistazo en el widget de inicio y activa la alerta para que te avise cuando vaya a haber buena ventana.")),
    OnbStep(emoji: "📓", eyebrow: L("TU DIARIO"), title: L("Lleva la cuenta"),
            body: L("Marca las vías que encadenas: la app guarda tu diario con tus estadísticas y tu grado máximo, escuela por escuela.")),
    OnbStep(emoji: "🧗", eyebrow: L("SUMA A LA GUÍA"), title: L("Comunidad"),
            body: L("Propón escuelas, piedras y sectores nuevos y deja notas con foto. Un admin las revisa antes de publicarlas para toda la comunidad.")),
    OnbStep(emoji: "💬", eyebrow: L("PERFIL, GENTE Y CHAT"), title: L("Conecta"),
            body: L("Crea tu perfil, busca y sigue a otros escaladores, mira sus diarios y chatea 1 a 1. Las notificaciones te avisan de seguidores y mensajes.")),
    OnbStep(emoji: "📍", eyebrow: L("OFFLINE + UBICACIÓN"), title: L("Listo para el monte"),
            body: L("Guarda escuelas para verlas SIN cobertura. Te pediremos la ubicación solo para ordenar por cercanía y centrar el mapa: se usa en tu móvil, nunca se comparte."))
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
                        legend(Cumbre.score(80), L("70+ a escalar"))
                        legend(Cumbre.score(58), L("50–69 regular"))
                        legend(Cumbre.score(30), L("<50 mal día"))
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
                Text(step < last ? L("SIGUIENTE") : L("PERMITIR UBICACIÓN Y EMPEZAR"))
                    .font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white)
                    .padding(.vertical, 16).frame(maxWidth: .infinity)
                    .background(Cumbre.terraFill)
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
