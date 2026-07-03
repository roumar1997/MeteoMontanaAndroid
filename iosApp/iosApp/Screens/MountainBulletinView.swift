import SwiftUI
import Shared

// Boletín de montaña oficial de AEMET — espejo de MountainBulletinSection.kt.
// Solo aparece si la escuela cae en uno de los 9 macizos con boletín; se
// carga solo (204 → nil → no pinta nada).
struct MountainBulletinSection: View {
    let lat: Double
    let lon: Double

    @State private var bulletin: MountainBulletinDto? = nil
    @State private var expanded = false

    var body: some View {
        Group {
            if let b = bulletin {
                card(b)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
            }
        }
        .task {
            bulletin = try? await AppDependencies.shared.container.mountainApi
                .getBulletin(lat: lat, lon: lon, day: 0)
        }
    }

    private func card(_ b: MountainBulletinDto) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("BOLETÍN DE MONTAÑA · AEMET")
                            .font(Cumbre.mono(10, .bold)).tracking(1.6)
                            .foregroundStyle(Cumbre.terra)
                        Text(b.areaName)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Cumbre.ink2)
                }
                .padding(12)
            }
            .buttonStyle(.plain)

            if expanded {
                Divider().background(Cumbre.rule)
                VStack(alignment: .leading, spacing: 10) {
                    row("CIELO", b.texts["nubosidad"])
                    row("PRECIPITACIONES", b.texts["pcp"])
                    row("TORMENTAS", b.texts["tormentas"], highlight: true)
                    row("TEMPERATURAS", b.texts["temperatura"])
                    row("VIENTO", b.texts["viento"])

                    let atmo: [String] = [
                        b.texts["isocero"].map { "Isoterma 0° a \($0)" },
                        b.texts["v1500"].map { "Viento a 1.500 m: \($0)" },
                        b.texts["v3000"].map { "Viento a 3.000 m: \($0)" }
                    ].compactMap { $0 }
                    if !atmo.isEmpty {
                        Divider().background(Cumbre.rule)
                        Text("EN ALTURA").font(Cumbre.mono(10, .bold)).tracking(1.4)
                            .foregroundStyle(Cumbre.ink2)
                        ForEach(atmo, id: \.self) { line in
                            Text(line).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        }
                    }

                    if !b.spots.isEmpty {
                        Divider().background(Cumbre.rule)
                        Text("TEMPERATURAS POR COTAS").font(Cumbre.mono(10, .bold)).tracking(1.4)
                            .foregroundStyle(Cumbre.ink2)
                        ForEach(b.spots, id: \.nombre) { spot in
                            HStack {
                                Text("\(spot.nombre) (\(spot.altitud))")
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Text("\(spot.minima)° / \(spot.maxima)°")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(Cumbre.ink)
                            }
                        }
                    }

                    Text("Fuente: AEMET")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                .padding(12)
            }
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func row(_ label: String, _ text: String?, highlight: Bool = false) -> some View {
        Group {
            if let t = text, !t.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(Cumbre.mono(10, .bold)).tracking(1.4)
                        .foregroundStyle(highlight && !t.hasPrefix("No se esperan")
                                         ? Cumbre.terra : Cumbre.ink2)
                    Text(t).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                }
            }
        }
    }
}
