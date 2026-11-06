import SwiftUI
import PhotosUI
import ImageIO
import UniformTypeIdentifiers
import Shared

// Asistente: lo suyo (diario, objetivo, logros), grado por altura, «¿qué piedra es esta?» y buscar vías.

extension AssistantViewModel {
    func mineAnswer(_ answer: AssistantAnswer) async throws -> MineAnswerer.MineAnswer {
        let u = answer.understood
        let schoolId = answer.resolvedSchool?.id
        let schoolName = answer.resolvedSchool?.name
        switch u?.mineTopic ?? "STATS" {
        case "FAVORITES":
            return MineAnswerer.shared.favorites(favs: try await container.getMyFavorites.invoke())
        case "LAST_VISIT":
            return MineAnswerer.shared.lastVisit(
                journal: try await container.getMyJournal.invoke(), schoolId: schoolId, schoolName: schoolName)
        case "MEETUPS":
            var all = try await container.getMeetups?.execute(schoolId: schoolId, date: nil, relation: nil) ?? []
            // "¿Quién va este finde?": solo las quedadas con algún día dentro del periodo.
            if let from = u?.dateFrom {
                let to = u?.dateTo ?? from
                all = all.filter { m in m.days.contains { $0 >= from && $0 <= to } }
            }
            return MineAnswerer.shared.meetups(all: all, today: Self.todayISO(), schoolId: schoolId, joinedOnly: false)
        default:
            return MineAnswerer.shared.stats(
                journal: try await container.getMyJournal.invoke(),
                discipline: u?.discipline, gradeMin: u?.gradeMin, gradeMax: u?.gradeMax, year: u?.year,
                from: u?.dateFrom, to: u?.dateTo, schoolId: schoolId, schoolName: schoolName)
        }
    }

    /// Objetivo del año o logros, calculados en el móvil con su diario (como en Estadísticas).
    func goalOrAchievements(_ topic: String) async throws -> String {
        let journal = try await container.getMyJournal.invoke()
        let today = Self.todayISO()
        if topic == "GOAL" {
            let year = Int(today.prefix(4)) ?? Calendar.current.component(.year, from: Date())
            guard let goal = try await container.getYearGoal.invoke(year: Int32(year)) else {
                return AssistantPresenter.noGoalText(year: year)
            }
            return AssistantPresenter.goalText(GoalCalculator.shared.progress(goal: goal, all: journal))
        }
        let schools = (try? await container.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                             lat: nil, lon: nil, radioKm: nil)) ?? []
        let places = Dictionary(schools.map { ($0.id, SchoolPlace(region: $0.region, country: $0.country)) },
                                uniquingKeysWith: { a, _ in a })
        return AssistantPresenter.achievementsText(
            AchievementsCalculator.shared.compute(all: journal, places: places, today: today))
    }

    /// Grado votado por altura de las vías encontradas (las 4 primeras con vía concreta).
    func heightGradeText(_ hits: [LineSearchHit]) async -> String {
        var lines: [String] = []
        for hit in hits.filter({ $0.lineId != nil }).prefix(4) {
            guard let id = hit.lineId, let summary = try? await container.getGradeVotes.invoke(lineId: id) else { continue }
            lines.append(AssistantPresenter.heightGradeLine(name: hit.lineName ?? hit.blockName, summary: summary))
        }
        return lines.isEmpty ? L("No he encontrado votos de grado para esas vías.") : lines.joined(separator: "\n")
    }

    // MARK: - Foto de una piedra

    /// "¿Qué piedra es esta?": dónde se hizo la foto (o dónde está él) y, entre las piedras de alrededor, a cuál se
    /// parece más. Todo en el móvil: la foto no se sube a ningún sitio.
    func identifyPhoto(_ result: PHPickerResult) async {
        messages.append(AssistantMessage(isUser: true, text: L("¿Qué piedra es esta? (foto)")))
        isLoading = true
        loadingNote = L("Leyendo dónde se hizo la foto…")
        let gen = generation
        defer { if gen == generation { isLoading = false; loadingNote = nil; persist() } }
        var image: UIImage?
        var lat: Double?, lon: Double?
        // Cada lectura con tiempo máximo: una foto en iCloud o un permiso a medias no pueden dejar el chat colgado.
        if let found = await AsyncTimeout.run(20, { await PhotoExifReader.read(result) }) {
            image = found.image; lat = found.lat; lon = found.lon
        } else if let only = await AsyncTimeout.run(20, { await PhotoExifReader.readImagen(result) }) {
            image = only.image
            // Sin permiso de Fotos la fototeca no da la posición, pero el ARCHIVO de la foto suele llevarla.
            if let gps = await AsyncTimeout.run(10, { await PhotoExifReader.gpsFromFile(result) }) { lat = gps.lat; lon = gps.lon }
        }
        guard let image else {
            messages.append(AssistantMessage(isUser: false, text: L("No he podido abrir esa foto (si está en iCloud, puede que no se haya descargado). Prueba con otra o hazla con la cámara.")))
            return
        }
        var approximate = false
        if lat == nil, let here = await AsyncTimeout.run(10, { await self.currentLocation() }) {
            lat = here.lat; lon = here.lon; approximate = true
        }
        guard gen == generation else { return }
        await identify(image: image, lat: lat, lon: lon, approximate: approximate, gen: gen)
    }

    /// Foto hecha ahora con la cámara: está delante de la piedra, así que su ubicación de este momento es la buena.
    func identifyCameraPhoto(_ image: UIImage) async {
        messages.append(AssistantMessage(isUser: true, text: L("¿Qué piedra es esta? (foto)")))
        isLoading = true
        loadingNote = L("Mirando dónde estás…")
        let gen = generation
        defer { if gen == generation { isLoading = false; loadingNote = nil; persist() } }
        let here = await AsyncTimeout.run(10, { await self.currentLocation() })
        guard gen == generation else { return }
        await identify(image: image, lat: here?.lat, lon: here?.lon, approximate: false, gen: gen)
    }

    /// Sin permiso de cámara: se dice en el chat cómo darlo.
    func cameraDenied() {
        messages.append(AssistantMessage(isUser: false, text: L("Para hacer la foto necesito permiso de cámara. Puedes dárselo en Ajustes.")))
    }

    func identify(image: UIImage, lat: Double?, lon: Double?, approximate: Bool, gen: Int) async {
        let outcome = await AsyncTimeout.run(45, {
            await AssistantPhotoMatcher.match(image: image, lat: lat, lon: lon, approximate: approximate,
                                              container: self.container,
                                              onStage: { s in if gen == self.generation { self.loadingNote = s.text } })
        })
        guard gen == generation else { return }
        guard let outcome else {
            // Se dice en qué paso se atascó: así se sabe qué arreglar.
            let stage = loadingNote ?? L("Buscando escuelas cerca de la foto…")
            messages.append(AssistantMessage(isUser: false, text: L("Ha tardado demasiado (se quedó en: %@). Inténtalo otra vez con buena cobertura.", stage)))
            return
        }
        switch outcome {
        case .noLocation:
            messages.append(AssistantMessage(isUser: false, text: L("La foto no guarda dónde se hizo y no tengo tu ubicación. Activa la ubicación o hazla desde la escuela.")))
        case .noData:
            messages.append(AssistantMessage(isUser: false, text: L("No he podido cargar las escuelas. Comprueba la conexión e inténtalo de nuevo.")))
        case let .noSchoolNear(closestName, closestKm):
            var text = approximate
                ? L("Esa foto no guarda dónde se hizo, y donde estás ahora no hay ninguna escuela cerca. Hazla con la cámara desde la escuela, o elige una foto con ubicación (en Ajustes → Cumbre → Fotos, dale acceso completo).")
                : L("No hay ninguna piedra de Cumbre cerca de donde se hizo la foto.")
            if let closestName, let closestKm {
                text += " " + L("La escuela más cercana es %@, a %@ km.", closestName, Int(closestKm.rounded()))
            }
            messages.append(AssistantMessage(isUser: false, text: text))
        case .noStones(let school):
            messages.append(AssistantMessage(isUser: false, text: L("En %@ aún no hay piedras colocadas cerca de ese punto.", school)))
        case let .found(candidates, sure, approximate):
            guard let first = candidates.first else { return }
            // Si ni la más parecida se parece de verdad, puede que esa piedra aún no esté en Cumbre: se dice.
            let weak = (first.similarity ?? 0) < 0.35
            messages.append(AssistantMessage(
                isUser: false,
                text: AssistantPresenter.photoIntro(sure: sure && !weak, approximate: approximate,
                                                    school: first.schoolName, weak: weak),
                photoMatches: candidates))
        }
    }

    /// "yyyy-MM-dd" de hoy en Madrid (las quedadas se guardan con sus días en ese formato).
    static func todayISO() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Europe/Madrid") ?? .current
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    // MARK: - Resultados de una búsqueda simple

    /// Pide al buscador de la app lo que entendió el asistente: con filtros (grado, modalidad,
    /// roca, orientación, escuela, distancia, estrellas) usa el modo "explorar"; con solo un nombre,
    /// la búsqueda de siempre por nombre. Si la frase nombró un sector, se quedan los de ese sector.
    /// Si pidió lo mejor valorado, se piden también las estrellas y se ordena por ellas.
    func loadHits(_ answer: AssistantAnswer, location: UserLocation?,
                          schoolIds explicit: [String]? = nil) async throws -> [LineSearchHit] {
        guard let u = answer.understood else { return [] }
        // Una escuela, o varias ("los 7a de Albarracín y Zarzalejo"). Con escuela dicha, ella manda: no se
        // filtra por distancia ni se mandan las coordenadas (las piedras sin coordenadas desaparecerían).
        let schoolIds: [String]? = explicit ?? (!answer.resolvedSchools.isEmpty
            ? answer.resolvedSchools.map { $0.id } : answer.resolvedSchool.map { [$0.id] })
        let schoolId = schoolIds?.first
        let minStars = u.minStars.map { Int($0.intValue) }
        let rated = u.topRated || minStars != nil
        let hasFilter = u.gradeMin != nil || u.gradeMax != nil || u.discipline != nil
            || !u.rockTypes.isEmpty || !u.orientations.isEmpty || u.maxDistanceKm != nil
            || schoolId != nil || rated || (u.useMyLocation && location != nil) || u.beta != nil
            || u.startType != nil || u.withTopo || u.notDone || u.mostRepeated

        var hits: [LineSearchHit] = []
        if hasFilter {
            let criteria = LineExploreCriteria(
                gradeMin: u.gradeMin, gradeMax: u.gradeMax, discipline: u.discipline,
                rockTypes: u.rockTypes.isEmpty ? nil : u.rockTypes,
                schoolIds: schoolIds,
                orientations: u.orientations.isEmpty ? nil : u.orientations,
                lat: schoolIds == nil ? location.map { KotlinDouble(double: $0.lat) } : nil,
                lon: schoolIds == nil ? location.map { KotlinDouble(double: $0.lon) } : nil,
                // "cerca de mí" sin cifra = 50 km, como en el servidor.
                maxDistanceKm: schoolIds != nil ? nil
                    : (u.maxDistanceKm ?? (u.useMyLocation && location != nil ? KotlinDouble(double: 50) : nil)),
                // "Las clásicas": por cuánta gente la ha hecho (el servidor ya ordena y cuenta las repeticiones).
                sort: u.mostRepeated ? "MOST_REPEATED" : "DISTANCE", offset: 0, withRatings: rated, beta: u.beta,
                startType: u.startType, withTopo: u.withTopo, withRepetitions: u.mostRepeated)
            hits = try await container.exploreLines.invoke(criteria: criteria)
        } else if let q = u.q, !q.isEmpty {
            hits = try await container.searchLines.invoke(query: q)
        }
        if u.notDone, let journal = try? await container.getMyJournal.invoke() {
            hits = MineAnswerer.shared.excludeDone(hits: hits, journal: journal)
        }
        if let sector = answer.resolvedSector?.name {
            hits = hits.filter { ($0.sectorName ?? "").caseInsensitiveCompare(sector) == .orderedSame }
        }
        if rated { hits = AssistantPresenter.rankByRating(hits, minStars: minStars) }
        return hits
    }

    // MARK: - Ubicación
}
