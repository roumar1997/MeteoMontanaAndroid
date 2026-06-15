import SwiftUI
import Shared

// Tab Tiempo — réplica de WeatherScreen.kt: forecast en tu ubicación + chips de
// favoritas + forecastBody. Requiere el LocationProvider (bridge iOS, pendiente)
// y las favoritas (auth). De momento muestra el encabezado y el estado "necesita
// ubicación" con el estilo Cumbre; se cablea al implementar el bridge de
// localización.
struct WeatherView: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Tiempo").font(Cumbre.serif(34, .bold)).foregroundStyle(Cumbre.ink)
                    Text("En tu ubicación").font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            .padding(.horizontal, 16).padding(.top, 8)
            Divider().overlay(Cumbre.rule).padding(.top, 8)
            Spacer()
            VStack(spacing: 12) {
                Image(systemName: "location.circle").font(.system(size: 44)).foregroundStyle(Cumbre.ink3)
                Text("Necesitamos tu ubicación").font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("Para mostrar el tiempo de escalada donde estás.\n(Disponible al activar la ubicación en iOS.)")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    .multilineTextAlignment(.center)
            }
            .padding(32)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }
}
