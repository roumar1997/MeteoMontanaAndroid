import SwiftUI
import Shared

// Sección APROXIMACIONES de la ficha de escuela — Fase 1 de
// APPROACH_DESIGN.md §6.1. Solo lectura: alta por usuario (grabar, añadir
// chincheta) llega en una fase posterior, sujeta a revisión legal.
//
// El loader vive en SchoolMapSection (no aquí) para que la ficha mini de un
// sector/parking en el mapa también pueda ofrecer "SEGUIR" si existe un
// camino que llega hasta él (ver miniBlockCard).

@MainActor
final class ApproachesLoader: ObservableObject {
    @Published var approaches: [Approach] = []
    @Published var loading = false
    private var loadedSchoolId: String?

    func load(schoolId: String) {
        guard schoolId != loadedSchoolId else { return }
        loadedSchoolId = schoolId
        loading = true
        Task {
            let result = (try? await AppDependencies.shared.container.getApproaches.invoke(schoolId: schoolId)) ?? []
            approaches = result
            loading = false
        }
    }

    /** Primer camino (verificado con preferencia) que llega hasta ese bloque. */
    func approach(to blockId: String) -> Approach? {
        approaches.first { $0.toBlockId == blockId && $0.isVerified }
            ?? approaches.first { $0.toBlockId == blockId }
    }
}

struct ApproachesSection: View {
    @ObservedObject var loader: ApproachesLoader
    let schoolName: String
    @Binding var following: Approach?

    var body: some View {
        Group {
            if !loader.approaches.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("APROXIMACIONES")
                        .font(Cumbre.mono(10, .bold)).tracking(1.8)
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 16)

                    ForEach(loader.approaches, id: \.id) { a in
                        approachCard(a)
                            .padding(.horizontal, 16)
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }

    @ViewBuilder
    private func approachCard(_ a: Approach) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(a.name ?? "Aproximación")
                .font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
            Text(summaryLine(a))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            HStack {
                Text(a.isVerified ? "✓ VERIFICADA" : "⚠ SIN VERIFICAR")
                    .font(Cumbre.mono(9, .bold)).tracking(1)
                    .foregroundStyle(a.isVerified ? Color(hex: 0x3F6B4A) : Color(hex: 0xB45309))
                Spacer()
                Button {
                    following = a
                } label: {
                    Text("SEGUIR")
                        .font(Cumbre.mono(10, .bold)).tracking(1)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(Cumbre.terra)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                }
            }
        }
        .padding(12)
        .background(Cumbre.paper2)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func summaryLine(_ a: Approach) -> String {
        var parts: [String] = []
        if let d = a.distanceM { parts.append(d >= 1000 ? String(format: "%.1f km", Double(d) / 1000) : "\(d) m") }
        if let asc = a.ascentM { parts.append("+\(asc) m") }
        if let dur = a.durationMin { parts.append("~\(dur) min") }
        let pinCount = a.pins.count
        if pinCount > 0 { parts.append(pinCount == 1 ? "1 chincheta" : "\(pinCount) chinchetas") }
        return parts.joined(separator: " · ")
    }
}
