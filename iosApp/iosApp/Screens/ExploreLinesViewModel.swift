import Foundation
import Shared

/// Lo que la pestaña «Vías/Bloques» toma de la lista de Escuelas: filtros comunes (estilo, roca, distancia), dónde
/// estás y la nota de cada escuela (para «Mejores condiciones»). Lo pasa la vista: ningún ViewModel depende del otro.
struct ExploreContext: Equatable {
    var style: String? = nil
    var rock: String? = nil
    var maxDistanceKm: Double? = nil
    var lat: Double? = nil
    var lon: Double? = nil
    /// Nota de HOY o del tramo de días elegido, por escuela.
    var scoreBySchool: [String: Int] = [:]

    /// Lo que se manda al servidor (sin las notas, que solo reordenan).
    fileprivate var request: ExploreContext { var c = self; c.scoreBySchool = [:]; return c }
}

/// Pestaña «Vías/Bloques» de Escuelas (docs/BLOCK_SEARCH_DESIGN.md §4/§8): grado, orientación, ALTURA y QUÉ BUSCAS, orden,
/// resultados agrupados por escuela o en lista. Separado de `SchoolListViewModel` (lista de escuelas) el 2026-11-05:
/// cada uno con una sola razón para cambiar. Mismo nombre y reparto en Android.
@MainActor
final class ExploreLinesViewModel: ObservableObject {
    enum Tab: String { case schools, blocks }
    enum Sort: String, CaseIterable {
        case distance = "Cercanía", bestConditions = "Mejores condiciones", mostRepeated = "Más repetidos"
        var label: String { L(rawValue) }
    }

    private let exploreLines: ExploreLinesUseCase
    init(container: IosDependencyContainer = AppDependencies.shared.container) {
        exploreLines = container.exploreLines
    }

    @Published var tab: Tab = .schools { didSet { search() } }
    /// Se recuerda el último grado usado (Álvaro: «que se te queden guardados los últimos que has utilizado»).
    @Published var gradeMin: String? = UserDefaults.standard.string(forKey: ExploreLinesViewModel.gradeMinKey) {
        didSet { UserDefaults.standard.set(gradeMin, forKey: Self.gradeMinKey); search() }
    }
    @Published var gradeMax: String? = UserDefaults.standard.string(forKey: ExploreLinesViewModel.gradeMaxKey) {
        didSet { UserDefaults.standard.set(gradeMax, forKey: Self.gradeMaxKey); search() }
    }
    private static let gradeMinKey = "explore_grade_min"
    private static let gradeMaxKey = "explore_grade_max"
    @Published var orientations: Set<String> = [] { didSet { search() } }
    @Published var sortBy: Sort = .distance { didSet { search() } }
    /// ALTURA: nil = todas; "TALL" / "SHORT" = con vídeo de beta o hechas por gente de esa altura (el servidor, `beta=`).
    @Published var height: String? = nil { didSet { search() } }
    /// Con altura elegida: "" = vídeo o encadenes, "_VIDEO" = solo vídeo, "_SENDS" = solo encadenes.
    @Published var heightSource: String = "" { didSet { if height != nil { search() } } }
    @Published var grouped = true
    @Published private(set) var hits: [LineSearchHit] = []
    @Published private(set) var loading = false
    @Published private(set) var context = ExploreContext()
    var active: Bool { tab == .blocks }

    /// Cambian los filtros comunes, la ubicación o las notas. Solo se pide otra vez si cambia lo que va al servidor.
    func setContext(_ c: ExploreContext) {
        let changed = c.request != context.request
        context = c
        if changed { search() }
    }

    /// Quita grado y orientación (sin salir de la pestaña): lo usa el «sin resultados» para empezar de cero.
    func clearCriteria() { gradeMin = nil; gradeMax = nil; orientations = [] }

    /// Botón BORRAR de la fila de filtros: además, altura y orden por defecto.
    func clearAll() { clearCriteria(); height = nil; heightSource = ""; sortBy = .distance }

    private var task: Task<Void, Never>?

    /// Resultado en vivo, sin botón «ver N vías»: espera ~300 ms al último cambio.
    private func search() {
        task?.cancel()
        guard active else { hits = []; loading = false; return }
        loading = true
        task = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard let self, !Task.isCancelled else { return }
            let c = self.context
            let criteria = LineExploreCriteria(
                gradeMin: self.gradeMin, gradeMax: self.gradeMax,
                discipline: c.style.map { $0 == "Bloque" ? "BOULDER" : "ROUTE" },
                rockTypes: c.rock.map { [$0] },
                schoolIds: nil,
                orientations: self.orientations.isEmpty ? nil : Array(self.orientations),
                lat: c.lat.map { KotlinDouble(double: $0) },
                lon: c.lon.map { KotlinDouble(double: $0) },
                maxDistanceKm: c.maxDistanceKm.map { KotlinDouble(double: $0) },
                // «Mejores condiciones» no lo sabe el servidor (la nota del tiempo vive en la app): se pide por distancia
                // y se reordena en groups/sortedHits. «Más repetidos» SÍ lo ordena el servidor (cuenta los diarios).
                sort: self.sortBy == .mostRepeated ? "MOST_REPEATED" : "DISTANCE",
                offset: 0, withRatings: false, beta: self.height.map { $0 + self.heightSource },
                startType: nil, withTopo: false,
                // Siempre: cada resultado enseña quién la ha hecho (por altura) y sus vídeos de beta.
                withRepetitions: true)
            let hits = (try? await self.exploreLines.invoke(criteria: criteria)) ?? []
            guard !Task.isCancelled else { return }
            self.hits = hits
            self.loading = false
        }
    }

    private func score(_ schoolId: String) -> Int { context.scoreBySchool[schoolId] ?? -1 }

    /// Agrupados por escuela en el orden del servidor (distancia), salvo «Mejores condiciones», que ordena por nota.
    var groups: [(schoolId: String, schoolName: String, hits: [LineSearchHit])] {
        var order: [String] = []
        var byId: [String: [LineSearchHit]] = [:]
        for h in hits {
            if byId[h.schoolId] == nil { byId[h.schoolId] = []; order.append(h.schoolId) }
            byId[h.schoolId]!.append(h)
        }
        var groups = order.map { id in (schoolId: id, schoolName: byId[id]?.first?.schoolName ?? "", hits: byId[id] ?? []) }
        if sortBy == .bestConditions { groups.sort { score($0.schoolId) > score($1.schoolId) } }
        return groups
    }

    /// Lista plana (sin agrupar) con el mismo criterio de orden.
    var sortedHits: [LineSearchHit] {
        guard sortBy == .bestConditions else { return hits }
        return hits.sorted { score($0.schoolId) > score($1.schoolId) }
    }
}
