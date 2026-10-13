import SwiftUI
import Shared

/// Estado de la pestaña BLOQUES/VÍAS de la ficha de escuela. Vive en la ficha (no en la pestaña) para que al ir al
/// mapa o al tiempo y volver no se pierdan los filtros ni se vuelva a bajar la lista.
///
/// SOLO guarda estado de pantalla y descarga datos con los use cases. Filtrar, ordenar, el podio y agrupar por
/// sector es lógica de dominio y vive en `SchoolLinesExplorer` (shared, con tests): iOS y Android hacen lo mismo.
@MainActor
final class SchoolBlocksExplorerModel: ObservableObject {

    enum Order: CaseIterable {
        case repeated, rated
        var label: String { self == .repeated ? L("MÁS REPETIDOS") : L("MEJOR VALORADOS") }
        var shared: LinesOrder { self == .repeated ? .mostRepeated : .bestRated }
    }

    /// "▶ +1,70" / "▶ −1,70" para cada altura de beta que entiende el servidor.
    static func betaLabel(_ height: String) -> String {
        height == BetaHeights.shared.TALL ? "▶ +1,70" : "▶ −1,70"
    }

    @Published private(set) var all: [LineSearchHit] = []
    @Published private(set) var loading = true
    @Published private(set) var failed = false
    @Published var query = ""
    @Published var grades: Set<String> = []
    /// Alturas de beta marcadas. Vacío = sin filtrar; las dos = cualquier vía con beta.
    @Published var betas: Set<String> = []
    @Published private(set) var betaLineIds: [String: Set<String>] = [:]
    @Published var order: Order = .repeated
    /// Sectores DESPLEGADOS (empiezan todos plegados, como los grupos de la búsqueda de Escuelas).
    @Published var openSectors: Set<String> = []
    /// Panel de filtros (grado, beta, orden): plegado por defecto para que se vea antes la lista.
    @Published var filtersOpen = false

    private let loadSchoolLines: LoadSchoolLinesUseCase
    private let loadBetaLineIds: LoadBetaLineIdsUseCase
    private var loadedSchool: String?

    init(container: IosDependencyContainer = AppDependencies.shared.container) {
        loadSchoolLines = container.loadSchoolLines
        loadBetaLineIds = container.loadBetaLineIds
    }

    // MARK: - Lo que pinta la vista (todo calculado por shared)

    var hasLines: Bool { all.contains { $0.lineId != nil } }

    var gradeOptions: [String] { SchoolLinesExplorer.shared.gradeOptions(lines: all) }

    /// nil = todavía bajando los ids de beta de alguna altura marcada.
    var linesView: LinesView? {
        SchoolLinesExplorer.shared.view(
            lines: all,
            filter: LinesFilter(query: query, grades: grades, betaHeights: betas, order: order.shared),
            betaLineIds: betaLineIds)
    }

    // MARK: - Datos

    /// Baja las vías de la escuela UNA vez (los filtros se aplican en local). `force` para reintentar.
    func loadIfNeeded(schoolId: String, force: Bool = false) async {
        if loadedSchool == schoolId && !force && !failed { return }
        loading = true; failed = false
        do {
            all = try await loadSchoolLines.invoke(schoolId: schoolId)
            loadedSchool = schoolId
        } catch is CancellationError {
            // La vista se fue a medias: no es un fallo, se reintenta al volver.
        } catch {
            failed = true
        }
        loading = false
    }

    /// Los enlaces de beta viven en el servidor: se piden por altura y se guardan.
    func loadBetaIfNeeded(schoolId: String) async {
        for height in betas.sorted() where betaLineIds[height] == nil {
            do {
                betaLineIds[height] = try await loadBetaLineIds.invoke(schoolId: schoolId, height: height)
            } catch is CancellationError {
                // Cambió de chip mientras bajaba: la tarea nueva se encarga; NO se toca su selección.
            } catch {
                // Sin conexión a medias: se quita el filtro en vez de dejar la lista vacía sin explicación.
                betas.remove(height)
            }
        }
    }
}
