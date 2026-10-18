import SwiftUI
import Shared

/// Mejor hora por sector de hoy. Se pide al abrir la tarjeta HOY; sin red o sin sectores orientados, no se muestra.
@MainActor
final class SectorHoursModel: ObservableObject {
    @Published private(set) var sectors: [SectorHours]?
    private var loadedFor: String?

    func load(schoolId: String) async {
        guard loadedFor != schoolId else { return }
        loadedFor = schoolId
        if let s = try? await AppDependencies.shared.container.getSectorHours.invoke(schoolId: schoolId) {
            sectors = s
        } else {
            loadedFor = nil
        }
    }
}

/// Tarjeta plegable PROPIA bajo la tarjeta HOY (Álvaro, 2026-10-18: «que sea su propio desplegable llamativo»).
/// Plegada: el mejor sector de hoy y su franja.
/// Desplegada: cada sector con su orientación, su franja y una tira de 8 a 20 h coloreada por nota (punto = sol).
/// Espejo de `SectorHoursSection` (Android).
struct SectorHoursSection: View {
    let schoolId: String
    /// La roca ahora mismo: si está húmeda, la «mejor hora» solo vale cuando haya secado (Álvaro, 2026-10-18).
    var dryRock = true
    var dryingMessage: String? = nil
    /// Horas de HOY en las que la previsión trae lluvia: en la tira se marcan y no cuentan como buenas.
    var rainHours: Set<Int> = []
    @StateObject private var model = SectorHoursModel()
    @State private var expanded = false

    var body: some View {
        let list = (model.sectors ?? []).filter { $0.bestFrom != nil }
        // OJO: el `.task` va en un contenedor que SIEMPRE tiene contenido. Pegado a un `Group` que empieza vacío
        // (todavía sin sectores) SwiftUI no lo ejecuta nunca y la fila no cargaba (Álvaro, 2026-10-18, build 289).
        VStack(spacing: 0) {
            Color.clear.frame(height: 0)   // ancla real para el `.task`
            if let best = list.first {
                VStack(spacing: 0) {
                    Button { withAnimation { expanded.toggle() } } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "sun.max.fill").font(.system(size: 18)).foregroundStyle(Cumbre.terra)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("MEJOR HORA POR SECTOR")).font(Cumbre.serif(15, .bold)).tracking(0.4).foregroundStyle(Cumbre.terra)
                                if !dryRock {
                                    Text(L("Roca húmeda: no escales aún"))
                                        .font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.bad)
                                }
                                if !expanded {
                                    Text(dryRock
                                         ? L("Hoy: %@ · de %@ a %@ h", best.name, hour(best.bestFrom), hour(best.bestTo, plus: 1))
                                         : L("Cuando seque: %@ · de %@ a %@ h", best.name, hour(best.bestFrom), hour(best.bestTo, plus: 1)))
                                        .font(.system(size: 12.5)).foregroundStyle(Cumbre.ink).lineLimit(1)
                                }
                            }
                            Spacer()
                            Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.terra)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if expanded {
                        Divider().overlay(Cumbre.rule)
                        VStack(alignment: .leading, spacing: 12) {
                            if !dryRock {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(L("La roca está húmeda ahora"))
                                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.bad)
                                    Text(dryingMessage ?? L("Mejor esperar a que seque"))
                                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                                    Text(L("Las horas de abajo son las que mejor van por sol, temperatura y viento, pero solo valen cuando la roca haya secado. Las marcadas con lluvia no sirven."))
                                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                                }
                                .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                                .background(Cumbre.bad.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                            }
                            ForEach(list, id: \.sectorId) { row($0) }
                            Text(L("Según el sol en cada pared, la temperatura, el viento y la humedad. Punto blanco = le da el sol, gota = llueve. Solo sectores con orientación votada."))
                                .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                    }
                }
                .background(Cumbre.terraBg.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
                .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.terra.opacity(0.5), lineWidth: 1))
                .padding(.horizontal, 12).padding(.vertical, 6)
            }
        }
        .task { await model.load(schoolId: schoolId) }
    }

    private func hour(_ h: KotlinInt?, plus: Int = 0) -> String {
        h.map { "\($0.intValue + plus)" } ?? "–"
    }

    private func row(_ s: SectorHours) -> some View {
        let from = s.bestFrom?.intValue, to = s.bestTo?.intValue
        return VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 8) {
                Text(s.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                Text(aspectLabel(s.aspect)).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                Spacer()
                if let from, let to {
                    Text(L("%@–%@ h", from, to + 1)).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink)
                }
            }
            // Tira de 8 a 20 h: color = nota de esa hora para ESTA pared; la mejor franja más alta; punto blanco = sol.
            HStack(alignment: .top, spacing: 2) {
                ForEach(s.hours, id: \.hour) { h in
                    let hr = Int(h.hour)
                    let inBest = (from.map { f in to.map { hr >= f && hr <= $0 } ?? false } ?? false) && dryRock
                    let rainy = rainHours.contains(hr)
                    VStack(spacing: 1) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 2)
                                .fill((rainy ? Cumbre.rain : Cumbre.score(Int(h.score))).opacity(inBest ? 1 : 0.55))
                                .frame(height: inBest ? 18 : 12)
                            if rainy { Image(systemName: "drop.fill").font(.system(size: 6)).foregroundStyle(.white) }
                            else if h.sun { Circle().fill(.white).frame(width: 5, height: 5) }
                        }
                        Text(hr % 2 == 0 ? "\(hr)" : " ").font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}
