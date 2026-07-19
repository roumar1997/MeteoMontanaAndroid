import SwiftUI
import Shared

// ViewModel del mapa de escuela — extraído de SchoolMapSection (894 líneas y
// ~25 @State sueltos, auditoría 2026-07-19): aquí viven los DATOS (bloques,
// flag admin, capas, sectores colapsados, buscador); en la vista queda solo
// cámara/gestos/hojas, que es lo intrínsecamente visual. Los workarounds de
// ciclo de vida de MapLibre NO se tocan (frágiles por naturaleza, no por deuda).
@MainActor
final class SchoolMapViewModel: ObservableObject {
    @Published var blocks: [Block] = []
    @Published var isAdmin = false
    /// Capas ocultas por la botonera (BLOCK/PARKING/ZONE).
    @Published var hiddenTypes: Set<String> = []
    /// Zonas (sectores) con sus piedras plegadas.
    @Published var collapsedSectors: Set<String> = []
    @Published var searchQuery = ""
    @Published var searchHighlight: String?

    func loadAdminFlag() async {
        isAdmin = ((try? await AppDependencies.shared.container.getMyProfile.invoke())?.isAdmin) ?? false
    }

    func toggleLayer(_ type: String) {
        if hiddenTypes.contains(type) { hiddenTypes.remove(type) } else { hiddenTypes.insert(type) }
    }

    struct SearchHit { let id: String; let label: String; let sub: String; let viaName: String?; let block: Block }
    func searchHits(_ q: String) -> [SearchHit] {
        var out: [SearchHit] = []
        for b in blocks where b.type.uppercased() == "BLOCK" {
            for l in b.lines where l.name.localizedCaseInsensitiveContains(q) {
                let grade = (l.grade?.isEmpty == false) ? " · \(l.grade!)" : ""
                out.append(SearchHit(id: l.id, label: l.name + grade, sub: b.name, viaName: l.name, block: b))
            }
            if b.name.localizedCaseInsensitiveContains(q) {
                out.append(SearchHit(id: b.id, label: b.name, sub: "\(b.lines.count) vías", viaName: nil, block: b))
            }
        }
        return Array(out.prefix(8))
    }

    /// Recarga y devuelve el bloque fresco de [selectedId] si sigue existiendo
    /// (para refrescar la ficha abierta, p.ej. nueva foto).
    func reloadBlocks(school: School, selectedId: String?) async -> Block? {
        blocks = await loadBlocksOnlineOrOffline(school: school)
        guard let selectedId else { return nil }
        return blocks.first(where: { $0.id == selectedId })
    }

    /// Carga los bloques (piedras/parkings/zonas) por red; si la red falla o no
    /// devuelve nada (offline), cae al snapshot guardado `loadOffline` para que
    /// el mapa, las piedras y sus vías salgan igual sin internet. Las fotos las
    /// resuelve `TopoPhotoView` desde `ImageCache`.
    private func loadBlocksOnlineOrOffline(school: School) async -> [Block] {
        if let online = try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id),
           !online.isEmpty {
            // Si el sitio está guardado offline, refresca su snapshot con lo recién
            // bajado (bloques + fotos) para que SIN conexión no se vea lo viejo tras
            // una modificación. Forecast nil = no se toca el ya cacheado.
            if let repo = AppDependencies.shared.container.savedSchools,
               (try? await repo.loadOffline(id: school.id)) != nil {
                try? await repo.saveOffline(school: school, blocks: online, forecast: nil)
                await ImageCache.prefetch(online.compactMap { $0.photoPath })
            }
            return online
        }
        // Sin red (o sin bloques en la respuesta): tira del snapshot offline.
        if let repo = AppDependencies.shared.container.savedSchools,
           let snap = try? await repo.loadOffline(id: school.id) {
            return snap.blocks.map { repo.toBlock(entity: $0, lines: snap.lines) }
        }
        return []
    }
}
