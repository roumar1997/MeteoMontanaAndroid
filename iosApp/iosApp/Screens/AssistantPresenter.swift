import Foundation
import Shared

/// Convierte lo que devuelve el asistente en textos y etiquetas para pintar.
///
/// La lógica y las frases (ES y EN) viven en `shared` (`presentation/assistant/AssistantPresenter.kt`) y son las
/// mismas que usa Android: una sola copia desde el 2026-11-05. Aquí solo se adaptan tipos a Swift (estructuras
/// `Identifiable` para las listas, `Date`, `CGFloat`, iconos SF Symbols) y viven las tres cosas que solo tiene iOS:
/// el detalle de «¿qué piedra es esta?» y lo que se lee en voz alta.
enum AssistantPresenter {

    /// El presentador compartido en el idioma actual de la app (se crea al usarlo: el idioma puede cambiar en marcha).
    private static var p: Shared.AssistantPresenter {
        Shared.AssistantPresenter(lang: LanguageManager.shared.effectiveCode, labels: IosAssistantLabels())
    }

    // MARK: Estados, aclaraciones y lo entendido

    static func message(for status: AssistantStatus) -> String? { p.message(status: status) }
    static func clarification(_ c: AssistantClarification) -> String { p.clarification(c: c) }
    static func chips(_ u: AssistantUnderstood, school: String?, sector: String?) -> [String] {
        p.chips(u: u, school: school, sector: sector)
    }

    // MARK: Acciones

    static func actionPrompt(kind: String, school: String) -> String { p.actionPrompt(kind: kind, school: school) }
    static func logPrompt(kind: String, hit: LineSearchHit, date: String) -> String {
        p.logPrompt(kind: kind, hit: hit, date: date)
    }
    static func actionDone(kind: String, school: String) -> String { p.actionDone(kind: kind, school: school) }
    static func meetupPrompt(school: String, date: String?, time: String?) -> String {
        p.meetupPrompt(school: school, date: date, time: time)
    }
    static func meetupName(school: String, date: String?, time: String?) -> String {
        p.meetupName(school: school, date: date, time: time)
    }
    static func multiLogPrompt(count: Int, date: String, missing: [String]) -> String {
        p.multiLogPrompt(count: Int32(count), date: date, missing: missing)
    }

    // MARK: Fichas y textos

    static func summaryText(_ s: AssistantSummary) -> String { p.summaryText(s: s) }
    static func mineText(_ r: MineAnswerer.MineAnswer, period: String? = nil) -> String { p.mineText(r: r, period: period) }
    static func startLabel(_ start: String) -> String { p.startLabel(start: start) }
    static func dayLabel(_ iso: String) -> String { p.dayLabel(iso: iso) }
    static func dateRange(_ from: String, _ to: String?) -> String { p.dateRange(from: from, to: to) }
    static func greeting(displayName: String?) -> String { p.greeting(displayName: displayName) }

    // MARK: Resultados de una búsqueda

    /// Resultados de un sector dentro de una escuela (name == nil: piedras sin sector asignado).
    struct HitSector: Identifiable {
        let name: String?
        let hits: [LineSearchHit]
        var id: String { name ?? "" }
    }

    /// Resultados de una escuela, repartidos por sectores.
    struct HitGroup: Identifiable {
        let id: String            // schoolId
        let schoolName: String
        let sectors: [HitSector]
        /// Todos los de esa escuela, también los que no se enseñan.
        let total: Int
        var shown: [LineSearchHit] { sectors.flatMap { $0.hits } }
    }

    private static func swift(_ g: Shared.AssistantPresenter.HitGroup) -> HitGroup {
        HitGroup(id: g.id, schoolName: g.schoolName, sectors: g.sectors.map(swift), total: Int(g.total))
    }
    private static func swift(_ s: Shared.AssistantPresenter.HitSector) -> HitSector { HitSector(name: s.name, hits: s.hits) }

    static func groupAllHits(_ hits: [LineSearchHit]) -> [HitGroup] { p.groupAllHits(hits: hits).map(swift) }
    static func initiallyExpanded(_ groups: [HitGroup], limit: Int = 20) -> Set<String> {
        let total = groups.reduce(0) { $0 + $1.total }
        return total <= limit ? Set(groups.map { $0.id }) : Set(groups.prefix(1).map { $0.id })
    }
    static func sectorKey(_ schoolId: String, _ sectorId: String) -> String { p.sectorKey(schoolId: schoolId, sectorId: sectorId) }
    static func groupHits(_ hits: [LineSearchHit], maxSchools: Int = 5, perSchool: Int = 4,
                          perSchoolWhenAlone: Int = 60) -> [HitGroup] {
        p.groupHits(hits: hits, maxSchools: clamp(maxSchools), perSchool: clamp(perSchool),
                    perSchoolWhenAlone: clamp(perSchoolWhenAlone)).map(swift)
    }
    static func bySector(_ hits: [LineSearchHit]) -> [HitSector] { p.bySector(hits: hits).map(swift) }
    static func hitsIntro(total: Int, needsLocationForDistance: Bool, rated: Bool = false) -> String {
        p.hitsIntro(total: Int32(total), needsLocationForDistance: needsLocationForDistance, rated: rated)
    }
    static func moreNote(total: Int, shown: Int) -> String? { p.moreNote(total: Int32(total), shown: Int32(shown)) }
    static func starsLabel(rating: Double, count: Int) -> String { p.starsLabel(rating: rating, count: Int32(count)) }
    static func rankByRating(_ hits: [LineSearchHit], minStars: Int?) -> [LineSearchHit] {
        p.rankByRating(hits: hits, minStars: minStars.map { KotlinInt(int: Int32($0)) })
    }
    static func compareIntro(_ r: AssistantRecommendation) -> String { p.compareIntro(r: r) }

    /// `Int.max` de Swift no cabe en el Int de Kotlin (32 bits): se recorta.
    private static func clamp(_ n: Int) -> Int32 { Int32(min(n, Int(Int32.max))) }

    // MARK: El tiempo

    static func oneDecimal(_ value: Double) -> String { p.oneDecimal(value: value) }
    static func weatherTitle(_ w: AssistantWeather) -> String { p.weatherTitle(w: w) }
    static func hourLabel(_ iso: String) -> String { p.hourLabel(iso: iso) }
    static func rainBarHeight(mm: Double, full: CGFloat) -> CGFloat {
        CGFloat(p.rainBarHeight(mm: mm, full: Float(full)))
    }
    static func windLabel(_ kmh: Double) -> String { p.windLabel(kmh: kmh) }
    static func rainHeadline(_ w: AssistantWeather) -> String { p.rainHeadline(w: w) }
    static func temperatureByDay(_ w: AssistantWeather) -> String { p.temperatureByDay(w: w) }
    static func weatherHeadline(_ w: AssistantWeather) -> String { p.weatherHeadline(w: w) }
    static func savedNote(createdAt: Date, now: Date = Date()) -> String? {
        p.savedNote(createdAtMs: Int64(createdAt.timeIntervalSince1970 * 1000), nowMs: Int64(now.timeIntervalSince1970 * 1000))
    }

    // MARK: Cabeceras de los resultados

    static func recommendationIntro(_ r: AssistantRecommendation) -> String { p.recommendationIntro(r: r) }
    static func breakdownIntro(_ b: AssistantBreakdown) -> String { p.breakdownIntro(b: b) }
    static func sectorSummary(_ s: AssistantSector, sun: String?) -> String { p.sectorSummary(s: s, sun: sun) }
    static func stoneLabel(_ s: AssistantStone) -> String { p.stoneLabel(s: s) }

    // MARK: Chat

    /// Icono (SF Symbol) de una etiqueta de lo entendido; el compartido dice QUÉ icono, aquí se elige el de Apple.
    static func chipIcon(_ chip: String) -> String? {
        guard let icon = p.chipIcon(chip: chip) else { return nil }
        switch icon {
        case .shade: return "circle.lefthalf.filled"
        case .sun: return "sun.max"
        case .morning: return "sunrise"
        case .afternoon: return "sunset"
        case .boulder: return "square.stack.3d.up"
        case .route: return "arrow.up.to.line"
        case .location: return "location"
        case .star: return "star"
        case .noRain: return "cloud.sun"
        case .topo: return "photo"
        case .notDone: return "circle.dashed"
        case .mostLines: return "list.number"
        case .beta: return "figure.climbing"
        case .face: return "safari"
        case .calendar: return "calendar"
        case .rock: return "mountain.2"
        case .pin: return "mappin"
        }
    }

    /// Una sugerencia para seguir la conversación: o una pregunta que se envía tal cual, o abrir una escuela.
    struct FollowUp: Identifiable, Equatable {
        enum Action: Equatable { case ask(String), open(schoolId: String) }
        let title: String
        let action: Action
        var id: String { title }
    }

    /// Qué ofrecer debajo de una respuesta: el compartido decide las frases según lo que enseñó el mensaje.
    static func followUps(for m: AssistantMessage) -> [FollowUp] {
        let waiting = m.isUser || m.retryText != nil || !m.options.isEmpty ||
            m.pendingActions.contains(where: { $0.state == .waiting })
        let shown: Shared.AssistantPresenter.Shown =
            waiting ? .nothing
            : m.breakdown != nil ? .breakdown
            : m.recommendation != nil ? .recommendation
            : m.weather != nil ? .weather
            : !m.hits.isEmpty ? .hits
            : !m.journalEntries.isEmpty ? .journal
            : .nothing
        return p.followUps(shown: shown, breakdown: m.breakdown).map { f in
            FollowUp(title: f.title, action: f.openSchoolId.map { .open(schoolId: $0) } ?? .ask(f.title))
        }
    }

    // MARK: Restricciones

    static func restrictionsIntro(_ r: AssistantRestrictions, school: String?) -> String { p.restrictionsIntro(r: r, school: school) }
    static func restrictionKind(_ kind: String) -> String { p.restrictionKind(kind: kind) }
    static func restrictionPlace(_ i: AssistantRestriction) -> String { p.restrictionPlace(i: i) }
    static func restrictionWhen(_ i: AssistantRestriction) -> String { p.restrictionWhen(i: i) }

    // MARK: Viaje de varios días

    /// Un día del plan: la mejor escuela de ese día (sin lluvia si alguna se libra).
    struct TripDay: Identifiable, Equatable {
        let date: String
        let schoolId: String
        let schoolName: String
        let score: Int
        let rainy: Bool
        var id: String { date }
    }

    static func tripPlan(_ r: AssistantRecommendation) -> [TripDay] {
        p.tripPlan(r: r).map {
            TripDay(date: $0.date, schoolId: $0.schoolId, schoolName: $0.schoolName, score: Int($0.score), rainy: $0.rainy)
        }
    }
    static func tripIntro(_ plan: [TripDay]) -> String {
        p.tripIntro(plan: plan.map {
            Shared.AssistantPresenter.TripDay(date: $0.date, schoolId: $0.schoolId, schoolName: $0.schoolName,
                                              score: Int32($0.score), rainy: $0.rainy)
        })
    }

    // MARK: Grado según la altura, objetivo y logros

    static func heightGradeLine(name: String, summary: GradeSummary) -> String { p.heightGradeLine(name: name, summary: summary) }
    static func goalText(_ p0: GoalProgress) -> String { p.goalText(p: p0) }
    static func noGoalText(year: Int) -> String { p.noGoalText(year: Int32(year)) }
    static func achievementsText(_ all: [Achievement]) -> String {
        p.achievementsText(all: all, describe: { achievementText($0) })
    }

    // MARK: Solo iOS — «¿qué piedra es esta?» y voz

    /// "a 12 m · se parece un 74 %" (sin metros cuando no se sabe dónde se hizo la foto).
    static func photoDetail(_ c: AssistantPhotoMatcher.Candidate) -> String {
        let similarity = c.similarity.map { L("se parece un %@ %", Int(($0 * 100).rounded())) } ?? L("sin foto para comparar")
        guard c.meters >= 0 else { return similarity }
        return L("a %@ m", Int(c.meters.rounded())) + " · " + similarity
    }

    static func photoIntro(sure: Bool, approximate: Bool, school: String, weak: Bool = false) -> String {
        let base = weak
            ? L("No se parece mucho a ninguna de las piedras de %@ que hay cerca: puede que aún no esté en Cumbre. Las más cercanas:", school)
            : (sure ? L("Creo que es esta, en %@:", school) : L("Puede ser una de estas de %@:", school))
        return approximate ? base + " " + L("(la foto no guardaba dónde se hizo: uso dónde estás ahora)") : base
    }

    /// Lo que se lee en voz alta: el texto de la burbuja y un resumen corto de la tarjeta, nunca listas largas.
    static func speech(for m: AssistantMessage) -> String {
        var parts = [m.text]
        if !m.trip.isEmpty {
            parts += m.trip.prefix(4).map { L("%@: %@, %@ sobre 100.", dayLabel($0.date), $0.schoolName, $0.score) }
        } else if let first = m.recommendation?.schools.first {
            if let score = first.combinedScore?.intValue {
                parts.append(L("La mejor es %@, %@ sobre 100.", first.name, score))
            } else {
                parts.append(L("La primera es %@.", first.name))
            }
        }
        if let r = m.restrictions {
            parts += r.items.filter { $0.active }.prefix(2)
                .map { "\(restrictionKind($0.kind)): \(restrictionPlace($0)), \(restrictionWhen($0))." }
        }
        if !m.hits.isEmpty {
            parts.append(m.hits.prefix(3).map { $0.lineName ?? $0.blockName }.joined(separator: ", ") + ".")
        }
        if let first = m.photoMatches.first { parts.append(first.blockName + ".") }
        return parts.joined(separator: " ")
    }
}

/// Cómo se muestran en iOS los valores del catálogo (roca, región, orientación), para el presentador compartido.
final class IosAssistantLabels: NSObject, AssistantLabels {
    func rock(raw: String) -> String { rockLabel(raw) }
    func region(raw: String) -> String { regionLabel(raw) }
    func aspect(code: String) -> String { aspectLabel(code) }
}
