import SwiftUI

// Bienvenida de primera apertura: UNA sola pantalla (2026-10-14). Antes era un tour de 9 pantallas de texto que
// casi nadie leía; ahora solo queda lo imprescindible — qué es Cumbre en una línea, qué significa el índice y el
// permiso de ubicación, explicado ANTES de pedirlo. Lo demás se aprende en la propia app (guía corta de primera vez
// y ayuda opcional por pantalla). El flag se persiste con @AppStorage en RootView; el nombre de usuario
// obligatorio lo sigue pidiendo `UsernameGateView`, que es otra pantalla y otra condición (la del servidor).

struct OnboardingView: View {
    /// "Permitir ubicación y empezar": RootView pide el permiso y marca la bienvenida como vista.
    let onFinish: () -> Void
    /// "Ahora no": entra sin pedir la ubicación (se puede dar luego desde Escuelas).
    var onSkip: () -> Void = {}

    var body: some View {
        VStack {
            Spacer()
            VStack(spacing: 14) {
                Text("⛰").font(.system(size: 64))
                Text(L("BIENVENIDO")).eyebrow().foregroundStyle(Cumbre.terra)
                Text("Cumbre").font(Cumbre.serif(30, .bold)).foregroundStyle(Cumbre.ink)
                Text(L("Te decimos si hoy se puede escalar y dónde, según el tiempo y lo que tarda en secar cada roca."))
                    .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                    .multilineTextAlignment(.center)
                // El índice 0–100, en una línea de leyenda.
                VStack(spacing: 6) {
                    legend(Cumbre.score(80), L("70+ a escalar"))
                    legend(Cumbre.score(58), L("50–69 regular"))
                    legend(Cumbre.score(30), L("<50 mal día"))
                }
                .padding(.top, 4)
                Text(L("Usamos tu ubicación solo para ordenar las escuelas por cercanía y centrar el mapa. Se queda en tu móvil."))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    .multilineTextAlignment(.center)
                    .padding(.top, 10)
            }
            Spacer()
            Button(action: onFinish) {
                Text(L("PERMITIR UBICACIÓN Y EMPEZAR"))
                    .font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white)
                    .padding(.vertical, 16).frame(maxWidth: .infinity)
                    .background(Cumbre.terraFill, in: Capsule())
            }
            .buttonStyle(.plain)
            Button(action: onSkip) {
                Text(L("AHORA NO")).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink3)
                    .padding(.vertical, 14).frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private func legend(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 10, height: 10)
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
            Text(text).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
        }
    }
}
