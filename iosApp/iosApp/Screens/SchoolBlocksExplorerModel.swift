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

    /// "Vídeo o encadenes" / "Solo vídeo" / "Solo encadenes" (el sufijo que se añade a la altura para el servidor).
    static func sourceLabel(_ source: String) -> String {
        switch source {
        case "_VIDEO": return L("Solo vídeo")
        case "_SENDS": return L("Solo encadenes")
        default: return L("Vídeo o encadenes")
        }
    }

    /// "▶ +1,70" / "▶ −1,70" para cada altura de beta que entiende el servidor.
    static func betaLabel(_ height: String) -> String {
        height == BetaHeights.shared.TALL ? "+1,70" : "−1,70"
    }

    // Los `didSet` de los datos y filtros recalculan la guía y las destacadas (ver `recompute`).
    @Published private(set) var all: [LineSearchHit] = [] { didSet { recompute() } }
    /// Caras marcadas (N/E/S/O). Vale para la lista y la guía.
    @Published var faces: Set<String> = [] { didSet { if faces != oldValue { recompute() } } }
    /// Solo lo que NO tienes hecho en tu diario. Vale para la lista y la guía.
    @Published var onlyNotDone = false { didSet { if onlyNotDone != oldValue { recompute() } } }
    /// Vías hechas / en proyecto según TU diario (local del móvil). Se releen al volver a la pestaña.
    @Published private(set) var doneIds: Set<String> = [] {
        didSet { if onlyNotDone && doneIds != oldValue { recompute() } }
    }
    @Published private(set) var projectIds: Set<String> = []
    /// Sectores DESPLEGADOS de la guía (al cargar, solo el primero).
    @Published var guideOpen: Set<String> = []
    @Published private(set) var loading = true
    @Published private(set) var failed = false
    /// Vías de la escuela cuyo grado parte la altura (lineId → los dos grados): se pintan en dos colores en las listas.
    @Published private(set) var splits: [String: GradeSplitInfo] = [:]
    @Published var query = "" { didSet { if query != oldValue { recompute() } } }
    @Published var grades: Set<String> = [] { didSet { if grades != oldValue { recompute() } } }
    /// Alturas de beta marcadas. Vacío = sin filtrar; las dos = cualquier vía con beta.
    @Published var betas: Set<String> = [] { didSet { if betas != oldValue { recompute() } } }
    @Published private(set) var betaLineIds: [String: Set<String>] = [:] {
        didSet { if betaLineIds != oldValue { recompute() } }
    }
    /// Qué cuenta en la altura: "" = vídeo o encadenes, "_VIDEO" = solo vídeo, "_SENDS" = solo encadenes (el servidor).
    @Published var betaSource: String = "" { didSet { if betaSource != oldValue { betaLineIds = [:] } } }
    /// Cambia al tocar una altura o lo que se busca: la vista vuelve a pedir las vías.
    var betaTaskKey: String { betas.sorted().joined() + betaSource }
    @Published var order: Order = .repeated { didSet { if order != oldValue { recompute() } } }
    /// Panel de filtros (grado, beta, orden): plegado por defecto para que se vea antes la lista.
    @Published var filtersOpen = false

    private let loadSchoolLines: LoadSchoolLinesUseCase
    private let loadBetaLineIds: LoadBetaLineIdsUseCase
    private var loadedSchool: String?

    init(container: IosDependencyContainer = AppDependencies.shared.container) {
        loadSchoolLines = container.loadSchoolLines
        loadBetaLineIds = container.loadBetaLineIds
        recompute()
        observeMemoryWarnings()
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
    /// Se recalculan solo cuando cambian los datos o los filtros (no en cada pintado).
    @Published private(set) var highlights: [LineSearchHit]?

    /// La guía con los mismos filtros; nil = bajando beta.
    @Published private(set) var guideView: GuideView?

    /// Inicio de cada sector de la guía (vías agrupadas por grado), precalculado junto con la guía.
    private(set) var sectorIntros: [String: GuideSectorIntro] = [:]

    private func recompute() {
        let f = filter
        highlights = SchoolLinesExplorer.shared.highlights(lines: all, filter: f, betaLineIds: betaLineIds, limit: 10)
        let g = SchoolGuide.shared.view(lines: all, filter: f, betaLineIds: betaLineIds)
        var intros: [String: GuideSectorIntro] = [:]
        for s in g?.sectors ?? [] { intros[s.key] = GuideSectorIntro(s) }
        sectorIntros = intros
        guideView = g
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
    /// Piedra a la que la ficha debe bajar (tocada en el listado del inicio de sector). Se consume y vuelve a nil.
    @Published var scrollToBlock: String?
    /// Generando el PDF (ruedecita en el botón).
    @Published private(set) var makingPDF = false

    func loadOverview(schoolId: String) async {
        if overview == nil { overview = GuideOverviewStore.cached(schoolId: schoolId) }
        // Sin red, `getBlocks` devuelve las piedras guardadas, pero el fondo del mapa no se puede pedir:
        // en ese caso se queda la copia de disco.
        guard let blocks = try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: schoolId) else {
            overviewLoaded = true
            return
        }
        overviewBlocks = blocks
        overviewLoaded = true
        guard let fresh = await GuideOverviewStore.generate(schoolId: schoolId, blocks: blocks) else { return }
        overview = fresh
    }

    /// Piedras, parkings y sectores de la escuela, para el mapa general EN VIVO (pantalla completa con red).
    @Published private(set) var overviewBlocks: [Block] = []
    /// Las piedras del resumen ya se pidieron (llegaran o no). Hasta entonces, la foto aérea de un sector
    /// sin copia en disco no se da por fallida: se queda el hueco con brillo, nunca una foto intermedia.
    @Published private(set) var overviewLoaded = false

    /// Con red, el mapa general a pantalla completa es un mapa real (se acerca de verdad); sin red, la imagen.
    var isOnline: Bool { AppDependencies.shared.container.networkMonitor?.isOnlineNow() ?? true }

    /// Toca un sector en el mapa general: se despliega y la ficha baja hasta él.
    func jumpToSector(_ name: String) {
        guard let s = guideView?.sectors.first(where: { $0.sector == name }) else { return }
        jumpToSector(key: s.key)
    }

    /// Lo mismo desde el índice de la guía (por su clave: también vale para «Sin sector»).
    func jumpToSector(key: String) {
        guideOpen.insert(key)
        scrollToSector = key
    }

    /// Foto aérea de cada sector (clave del sector → imagen), para el inicio del sector en la guía y en el PDF.
    @Published private(set) var sectorMaps: [String: UIImage] = [:]
    /// Sectores cuya foto aérea ya se intentó (con o sin éxito): hasta entonces se enseña un hueco, no otra foto.
    @Published private(set) var sectorMapsTried: Set<String> = []

    /// Genera (o lee de disco) la foto aérea de un sector con SUS piedras.
    func loadSectorMap(schoolId: String, sector s: GuideSector) async {
        // `inFlight`: si ya se está haciendo (la tarjeta y el PDF a la vez), no se lanza otra.
        guard sectorMaps[s.key] == nil, !sectorMapsInFlight.contains(s.key) else { return }
        sectorMapsInFlight.insert(s.key)
        defer { sectorMapsInFlight.remove(s.key) }
        let ids = Set(s.blocks.map(\.blockId))
        // Si las piedras llegan mientras se intentaba sin ellas, se repite una vez ya con ellas.
        while sectorMaps[s.key] == nil {
            let ready = overviewLoaded
            let stones = overviewBlocks.filter { ids.contains($0.id) }
            // `sectorMap` no está atada al hilo principal: la foto se pide y se pinta fuera de él.
            if let img = await GuideOverviewStore.sectorMap(schoolId: schoolId, key: s.key, stones: stones) {
                sectorMaps[s.key] = img
            }
            // Sin las piedras aún, un fallo no es definitivo: se queda el hueco hasta que lleguen.
            if sectorMaps[s.key] != nil || ready { sectorMapsTried.insert(s.key); return }
            if !overviewLoaded || Task.isCancelled { return }
        }
    }

    private var sectorMapsInFlight: Set<String> = []
    nonisolated(unsafe) private var memoryWarning: NSObjectProtocol?

    /// Suelta las fotos aéreas (al salir de la guía o si el sistema pide memoria); se vuelven a generar al verlas.
    func releaseSectorMaps() {
        sectorMaps = [:]
        sectorMapsTried = []
    }

    private func observeMemoryWarnings() {
        memoryWarning = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.releaseSectorMaps() }
        }
    }

    deinit {
        if let memoryWarning { NotificationCenter.default.removeObserver(memoryWarning) }
    }

    /// Genera el PDF (con los filtros de ahora) y abre la hoja de compartir. `sectorKey` nil = toda la guía.
    func exportPDF(schoolId: String, schoolName: String, sectorKey: String?) async {
        guard !makingPDF, let g = guideView else { return }
        makingPDF = true
        for s in g.sectors where sectorKey == nil || s.key == sectorKey {
            await loadSectorMap(schoolId: schoolId, sector: s)
        }
        let url = await GuidePDF.make(schoolName: schoolName, guide: g, overview: overview, sectorMaps: sectorMaps,
                                      onlySector: sectorKey)
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
                betaLineIds[height] = try await loadBetaLineIds.invoke(schoolId: schoolId, height: height + betaSource)
            } catch is CancellationError {
                // Cambió de chip mientras bajaba: la tarea nueva se encarga; NO se toca su selección.
            } catch {
                // Sin conexión a medias: se quita el filtro en vez de dejar la lista vacía sin explicación.
                betas.remove(height)
            }
        }
    }
}
