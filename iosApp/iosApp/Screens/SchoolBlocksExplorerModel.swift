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
        /// Pestaña de las destacadas de la guía.
        var tab: String { self == .repeated ? L("Más repetidas") : L("Mejor valoradas") }
    }

    /// "▶ +1,70" / "▶ −1,70" para cada altura de beta que entiende el servidor.
    static func betaLabel(_ height: String) -> String {
        height == BetaHeights.shared.TALL ? "▶ +1,70" : "▶ −1,70"
    }

    @Published private(set) var all: [LineSearchHit] = []
    /// Caras marcadas (N/E/S/O). Vale para la lista y la guía.
    @Published var faces: Set<String> = []
    /// Solo lo que NO tienes hecho en tu diario. Vale para la lista y la guía.
    @Published var onlyNotDone = false
    /// Vías hechas / en proyecto según TU diario (local del móvil). Se releen al volver a la pestaña.
    @Published private(set) var doneIds: Set<String> = []
    @Published private(set) var projectIds: Set<String> = []
    /// Sectores DESPLEGADOS de la guía (al cargar, solo el primero).
    @Published var guideOpen: Set<String> = []
    @Published private(set) var loading = true
    @Published private(set) var failed = false
    /// Vías de la escuela cuyo grado parte la altura (lineId → los dos grados): se pintan en dos colores en las listas.
    @Published private(set) var splits: [String: GradeSplitInfo] = [:]
    @Published var query = ""
    @Published var grades: Set<String> = []
    /// Alturas de beta marcadas. Vacío = sin filtrar; las dos = cualquier vía con beta.
    @Published var betas: Set<String> = []
    @Published private(set) var betaLineIds: [String: Set<String>] = [:]
    @Published var order: Order = .repeated
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

    /// Los filtros de arriba: valen para las destacadas y para la guía.
    private var filter: LinesFilter {
        LinesFilter(query: query, grades: grades, betaHeights: betas, order: order.shared,
                    faces: faces, excludeLineIds: onlyNotDone ? doneIds : [])
    }

    /// Destacadas (más repetidas / mejor valoradas, según `order`) con los mismos filtros; nil = bajando beta.
    var highlights: [LineSearchHit]? {
        SchoolLinesExplorer.shared.highlights(lines: all, filter: filter, betaLineIds: betaLineIds, limit: 10)
    }

    /// La guía con los mismos filtros; nil = bajando beta.
    var guideView: GuideView? {
        SchoolGuide.shared.view(lines: all, filter: filter, betaLineIds: betaLineIds)
    }

    // MARK: - Marcapáginas

    /// Última piedra con la que hiciste algo en la guía (mapa, vía, marcar, foto). Al volver a BLOQUES se salta a
    /// ella, como un marcapáginas. Se guarda por escuela, así que sigue ahí aunque salgas y vuelvas a entrar.
    private(set) var bookmarkBlockId: String?

    private static func bookmarkKey(_ schoolId: String) -> String { "guide.bookmark.\(schoolId)" }

    func setBookmark(schoolId: String, blockId: String) {
        bookmarkBlockId = blockId
        UserDefaults.standard.set(blockId, forKey: Self.bookmarkKey(schoolId))
    }

    /// Despliega el sector de la piedra marcada (si con los filtros actuales sale en la guía) y dice si está.
    func openBookmarkedSector() -> String? {
        guard let id = bookmarkBlockId, let view = guideView,
              let s = view.sectors.first(where: { $0.blocks.contains { $0.blockId == id } }) else { return nil }
        guideOpen.insert(s.key)
        return id
    }

    // MARK: - Mapa general, salto a sector y PDF

    /// Mapa general de la escuela (imagen). Sale primero la copia guardada y luego, con red, se regenera.
    @Published private(set) var overview: GuideOverview?
    /// Sector al que la ficha debe desplazarse (tocado en el mapa general). La ficha lo consume y lo pone a nil.
    @Published var scrollToSector: String?
    /// Generando el PDF (ruedecita en el botón).
    @Published private(set) var makingPDF = false

    func loadOverview(schoolId: String) async {
        if overview == nil { overview = GuideOverviewStore.cached(schoolId: schoolId) }
        // Sin red, `getBlocks` devuelve las piedras guardadas, pero el fondo del mapa no se puede pedir:
        // en ese caso se queda la copia de disco.
        guard let blocks = try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: schoolId),
              let fresh = await GuideOverviewStore.generate(schoolId: schoolId, blocks: blocks) else { return }
        overview = fresh
    }

    /// Toca un sector en el mapa general: se despliega y la ficha baja hasta él.
    func jumpToSector(_ name: String) {
        guard let s = guideView?.sectors.first(where: { $0.sector == name }) else { return }
        guideOpen.insert(s.key)
        scrollToSector = s.key
    }

    /// Genera el PDF (con los filtros de ahora) y abre la hoja de compartir. `sectorKey` nil = toda la guía.
    func exportPDF(schoolName: String, sectorKey: String?) async {
        guard !makingPDF, let g = guideView else { return }
        makingPDF = true
        let url = await GuidePDF.make(schoolName: schoolName, guide: g, overview: overview, onlySector: sectorKey)
        makingPDF = false
        if let url { GuidePDF.share(url) }
    }

    /// Relee el diario del móvil: qué vías de esta escuela tienes hechas o en proyecto.
    func refreshJournal(schoolId: String) {
        let done = JournalDoneStore.shared.all
        let projects = JournalProjectStore.shared.all
        var d = Set<String>(), p = Set<String>()
        for h in all {
            guard let id = h.lineId else { continue }
            let key = "\(schoolId)|#\(id)"
            if done.contains(key) { d.insert(id) } else if projects.contains(key) { p.insert(id) }
        }
        doneIds = d; projectIds = p
    }

    // MARK: - Datos

    /// Baja las vías de la escuela UNA vez (los filtros se aplican en local). `force` para reintentar.
    func loadIfNeeded(schoolId: String, schoolName: String = "", force: Bool = false) async {
        if loadedSchool == schoolId && !force && !failed { return }
        loading = true; failed = false
        do {
            all = try await loadSchoolLines.invoke(schoolId: schoolId, schoolName: schoolName)
            loadedSchool = schoolId
            refreshJournal(schoolId: schoolId)
            if let first = SchoolGuide.shared.sectors(lines: all).first { guideOpen = [first.key] }
            bookmarkBlockId = UserDefaults.standard.string(forKey: Self.bookmarkKey(schoolId))
            // Los grados partidos por altura son un extra: si falla, las listas salen con el grado de siempre.
            if let map = try? await AppDependencies.shared.container.getSchoolGradeSplits.invoke(schoolId: schoolId) {
                splits = map.mapValues { GradeSplitInfo(tall: $0.tallGrade, short: $0.shortGrade) }
            }
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
