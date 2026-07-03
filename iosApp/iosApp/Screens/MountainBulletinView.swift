import SwiftUI
import Shared

// Boletín de montaña oficial de AEMET — espejo de MountainBulletinSection.kt.
// Solo aparece si la escuela cae en uno de los 9 macizos con boletín.
// Si el meteorólogo espera TORMENTAS, la tarjeta "se ilumina": borde terra
// + chip de aviso visible SIN abrir el desplegable.
struct MountainBulletinSection: View {
    let lat: Double
    let lon: Double

    @State private var bulletin: MountainBulletinDto? = nil
    @State private var expanded = false

    var body: some View {
        // OJO: contenedor REAL (VStack), no Group. Los modificadores de un
        // Group se aplican a sus hijos; sin boletín no hay hijos → el .task
        // no se ejecutaba nunca y la tarjeta jamás aparecía (bug build 27).
        VStack(spacing: 0) {
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

    /// Aviso si el boletín trae tormentas ("No se esperan" = limpio), o nil.
    private func alert(_ b: MountainBulletinDto) -> String? {
        guard let t = b.texts["tormentas"], !t.hasPrefix("No se esperan") else { return nil }
        var clean = t
        if clean.hasSuffix(".") { clean.removeLast() }
        return "⚠ TORMENTAS: \(clean.lowercased())"
    }

    private func card(_ b: MountainBulletinDto) -> some View {
        let warn = alert(b)
        return VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 10) {
                    Image(systemName: "mountain.2")
                        .font(.system(size: 17))
                        .foregroundStyle(Cumbre.terra)
                        .frame(width: 38, height: 38)
                        .background(Cumbre.terraBg)
                        .clipShape(RoundedRectangle(cornerRadius: 11))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("BOLETÍN DE MONTAÑA · AEMET")
                            .font(Cumbre.mono(10, .bold)).tracking(1.6)
                            .foregroundStyle(Cumbre.ink2)
                        Text(b.areaName)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                        if let w = warn {
                            Text(w).font(Cumbre.mono(9, .bold)).tracking(0.8)
                                .foregroundStyle(Cumbre.terra)
                                .lineLimit(1)
                        }
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
                    row("TORMENTAS", b.texts["tormentas"], highlight: warn != nil)
                    row("TEMPERATURAS", b.texts["temperatura"])
                    row("VIENTO", b.texts["viento"])

                    // Atmósfera libre como chips mono.
                    let chips: [String] = [
                        b.texts["isocero"].map { "ISO 0° · \($0)" },
                        b.texts["v1500"].map { "1.500 M · \($0)" },
                        b.texts["v3000"].map { "3.000 M · \($0)" }
                    ].compactMap { $0 }
                    if !chips.isEmpty {
                        // Grid adaptativa: si un chip no cabe, salta de línea
                        // ENTERO (nada de texto roto en vertical).
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 6)],
                                  alignment: .leading, spacing: 6) {
                            ForEach(chips, id: \.self) { c in
                                Text(c).font(Cumbre.mono(9, .bold)).tracking(0.6)
                                    .lineLimit(1)
                                    .foregroundStyle(Cumbre.ink)
                                    .padding(.horizontal, 8).padding(.vertical, 4)
                                    .background(Cumbre.paper2)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8)
                                        .stroke(Cumbre.rule, lineWidth: 1))
                            }
                        }
                    }

                    if !b.spots.isEmpty {
                        Divider().background(Cumbre.rule)
                        Text("TEMPERATURAS POR COTAS").font(Cumbre.mono(10, .bold)).tracking(1.4)
                            .foregroundStyle(Cumbre.ink2)
                        ForEach(b.spots, id: \.nombre) { spot in
                            HStack {
                                Text(spot.nombre)
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Text(spot.altitud)
                                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                Text("\(spot.minima)° / \(spot.maxima)°")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(Cumbre.ink)
                                    .padding(.leading, 6)
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
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14)
            .stroke(warn != nil ? Cumbre.terra : Cumbre.rule,
                    lineWidth: warn != nil ? 1.5 : 1))
    }

    private func row(_ label: String, _ text: String?, highlight: Bool = false) -> some View {
        Group {
            if let t = text, !t.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(Cumbre.mono(10, .bold)).tracking(1.4)
                        .foregroundStyle(highlight ? Cumbre.terra : Cumbre.ink2)
                    Text(t).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                }
            }
        }
    }
}
