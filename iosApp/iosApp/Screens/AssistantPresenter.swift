import Foundation
import Shared

/// Convierte lo que devuelve el asistente en textos y etiquetas para pintar.
///
/// El servidor NO manda frases (solo un estado y datos reales): aquí se escriben
/// los textos en el idioma de la app. Es lógica pura, sin vistas, para poder
/// probarla sin simulador (AssistantPresenterTests).
enum AssistantPresenter {

    // MARK: Estados sin datos

    /// Texto para los estados que no traen resultados; nil si hay datos que mostrar.
    static func message(for status: AssistantStatus) -> String? {
        switch status {
        case .outOfScope:
            return L("Solo puedo ayudarte con escuelas, bloques, vías y el tiempo para escalar.")
        case .unavailable:
            return L("Ahora mismo no puedo responder. Puedes usar los filtros de la pestaña Escuelas.")
        case .userLimit:
            return L("Has llegado al límite de preguntas de hoy. Mañana podrás volver a usarme.")
        case .globalLimit:
            return L("El asistente está descansando por hoy. Vuelve mañana o usa los filtros.")
        case .busy:
            return L("Hay muchas preguntas ahora mismo. Prueba de nuevo en unos segundos.")
        default:
            return nil
        }
    }

    /// Qué preguntar al usuario cuando falta o no se encontró una escuela/sector.
    static func clarification(_ c: AssistantClarification) -> String {
        switch c.kind {
        case "NEEDS_SCHOOL": return L("¿De qué escuela hablas?")
        case "SCHOOL_NOT_FOUND": return L("No encuentro esa escuela. ¿Puedes escribir su nombre de otra forma?")
        case "SCHOOL_AMBIGUOUS": return L("¿A cuál de estas te refieres?")
        case "SECTOR_NOT_FOUND":
            return c.options.isEmpty
                ? L("Esa escuela no tiene sectores.")
                : L("No encuentro ese sector. Estos son los de la escuela:")
        default: return L("Ahora mismo no puedo responder. Puedes usar los filtros de la pestaña Escuelas.")
        }
    }

    // MARK: Chips con lo entendido

    /// Etiquetas con lo que se ha entendido, para que el usuario vea si acertó.
    static func chips(_ u: AssistantUnderstood, school: String?, sector: String?) -> [String] {
        var out: [String] = []
        if let school { out.append(school) }
        if let sector { out.append(sector) }
        if u.discipline == "BOULDER" { out.append(L("Bloque")) }
        if u.discipline == "ROUTE" { out.append(L("Vía")) }
        if let a = u.gradeMin, let b = u.gradeMax {
            out.append(a == b ? a : "\(a) – \(b)")
        } else if let a = u.gradeMin {
            out.append("≥ \(a)")
        } else if let b = u.gradeMax {
            out.append("≤ \(b)")
        }
        if let from = u.dateFrom { out.append(dateRange(from, u.dateTo)) }
        if u.sun == "SHADE" { out.append(L("Sombra")) }
        if u.sun == "SUN" { out.append(L("Sol")) }
        if u.dayPart == "MORNING" { out.append(L("Mañana")) }
        if u.dayPart == "AFTERNOON" { out.append(L("Tarde")) }
        out.append(contentsOf: u.orientations.map { L("Cara %@", aspectLabel($0)) })
        out.append(contentsOf: u.rockTypes.map { rockLabel($0) })
        if let km = u.maxDistanceKm { out.append("< \(Int(km.doubleValue)) km") }
        return out
    }

    // MARK: Fechas

    private static var madrid: TimeZone { TimeZone(identifier: "Europe/Madrid") ?? .current }

    private static func parse(_ iso: String) -> Date? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = madrid
        f.dateFormat = "yyyy-MM-dd"
        return f.date(from: iso)
    }

    /// "SÁB 6" (día de la semana abreviado + número) en el idioma de la app.
    static func dayLabel(_ iso: String) -> String {
        guard let date = parse(iso) else { return iso }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = madrid
        let weekday = cal.component(.weekday, from: date) - 1   // domingo = 0
        let day = cal.component(.day, from: date)
        let names = CalendarLabels.weekdaysShortSunFirst()
        guard names.indices.contains(weekday) else { return iso }
        return "\(names[weekday].uppercased()) \(day)"
    }

    /// "6 – 8 dic" o "6 dic" si es un solo día.
    static func dateRange(_ from: String, _ to: String?) -> String {
        guard let a = parse(from) else { return from }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = madrid
        let months = CalendarLabels.monthsShort()
        func part(_ d: Date) -> (Int, String) {
            let m = cal.component(.month, from: d) - 1
            return (cal.component(.day, from: d), months.indices.contains(m) ? months[m] : "")
        }
        let (d1, m1) = part(a)
        guard let to, to != from, let b = parse(to) else { return "\(d1) \(m1)" }
        let (d2, m2) = part(b)
        return m1 == m2 ? "\(d1) – \(d2) \(m2)" : "\(d1) \(m1) – \(d2) \(m2)"
    }

    // MARK: Resultados de una búsqueda

    /// Resultados de un sector dentro de una escuela (name == nil: piedras sin sector asignado).
    struct HitSector: Identifiable {
        let name: String?
        let hits: [LineSearchHit]
        var id: String { name ?? "" }
    }

    /// Resultados de una escuela, repartidos por sectores y recortados para que el chat no se haga eterno.
    struct HitGroup: Identifiable {
        let id: String            // schoolId
        let schoolName: String
        let sectors: [HitSector]
        /// Todos los de esa escuela, también los que no se enseñan.
        let total: Int
        var shown: [LineSearchHit] { sectors.flatMap { $0.hits } }
    }

    /// Agrupa los resultados por escuela conservando el orden en que llegan (el servidor los manda
    /// por cercanía) y, dentro de cada escuela, por sector. Como mucho maxSchools escuelas; con
    /// varias se enseñan perSchool filas de cada una, y si solo hay UNA (p. ej. "los 6 de
    /// Albarracín") se enseñan hasta perSchoolWhenAlone para que se vea el reparto por sectores.
    static func groupHits(_ hits: [LineSearchHit], maxSchools: Int = 5, perSchool: Int = 4,
                          perSchoolWhenAlone: Int = 60) -> [HitGroup] {
        var order: [String] = []
        var bySchool: [String: [LineSearchHit]] = [:]
        for h in hits {
            if bySchool[h.schoolId] == nil { order.append(h.schoolId) }
            bySchool[h.schoolId, default: []].append(h)
        }
        let chosen = Array(order.prefix(maxSchools))
        let limit = chosen.count == 1 ? perSchoolWhenAlone : perSchool
        return chosen.map { id in
            let all = bySchool[id] ?? []
            return HitGroup(id: id, schoolName: all.first?.schoolName ?? "",
                            sectors: bySector(Array(all.prefix(limit))), total: all.count)
        }
    }

    /// Reparte por sector: primero los que más resultados tienen; "sin sector" siempre al final.
    static func bySector(_ hits: [LineSearchHit]) -> [HitSector] {
        var bySector: [String: [LineSearchHit]] = [:]
        for h in hits {
            let key = (h.sectorName ?? "").trimmingCharacters(in: .whitespaces)
            bySector[key, default: []].append(h)
        }
        return bySector
            .map { HitSector(name: $0.key.isEmpty ? nil : $0.key, hits: $0.value) }
            .sorted { a, b in
                if (a.name == nil) != (b.name == nil) { return b.name == nil }
                if a.hits.count != b.hits.count { return a.hits.count > b.hits.count }
                return (a.name ?? "") < (b.name ?? "")
            }
    }

    static func hitsIntro(total: Int, needsLocationForDistance: Bool) -> String {
        var text = total == 0
            ? L("No he encontrado vías o bloques con esos filtros.")
            : L("He encontrado %@ resultados:", total)
        if needsLocationForDistance {
            text += " " + L("Para filtrar por distancia activa tu ubicación.")
        }
        return text
    }

    /// Aviso de que hay más resultados de los que caben en el chat; nil si se ven todos.
    static func moreNote(total: Int, shown: Int) -> String? {
        total > shown ? L("Y %@ más. Mira todos en Escuelas → Vías/Bloques.", total - shown) : nil
    }

    // MARK: Mensajes recuperados

    /// Aviso de cuánto hace que se enseñó una tarjeta guardada ("Guardado hace 3 h. El tiempo
    /// puede haber cambiado."). nil si hace menos de media hora: ahí no hace falta decir nada.
    static func savedNote(createdAt: Date, now: Date = Date()) -> String? {
        let seconds = now.timeIntervalSince(createdAt)
        guard seconds >= 1800 else { return nil }
        let hours = max(1, Int((seconds / 3600).rounded()))
        return L("Guardado hace %@ h. El tiempo puede haber cambiado.", hours)
    }

    // MARK: Cabeceras de los resultados

    static func recommendationIntro(_ r: AssistantRecommendation) -> String {
        if r.schools.isEmpty {
            return L("No he encontrado escuelas con vías de ese grado en la zona.")
        }
        if !r.forecastAvailable {
            return L("Aún no hay previsión para esas fechas. Te ordeno las escuelas por número de vías de ese grado.")
        }
        guard let first = r.dates.first else { return L("Esta es la mejor opción:") }
        return L("Para %@, esta es la mejor opción:", dateRange(first, r.dates.last))
    }

    static func breakdownIntro(_ b: AssistantBreakdown) -> String {
        if b.totalStones == 0 {
            return L("No he encontrado piedras que cumplan eso en %@.", b.school.name)
        }
        let known = L("De %@ piedras, %@ tienen la orientación votada.", Int(b.totalStones), Int(b.stonesWithOrientation))
        let filtered = b.sun != nil || b.sectors.contains { $0.unknownOrientation > 0 }
        return filtered ? known : L("Estas son las piedras de %@ por sector.", b.school.name)
    }

    /// Resumen corto de un sector: "2 a la sombra · 1 sin orientación".
    static func sectorSummary(_ s: AssistantSector, sun: String?) -> String {
        var parts: [String] = []
        let matching = Int(s.matching)
        if sun == "SHADE" { parts.append(L("%@ a la sombra", matching)) }
        else if sun == "SUN" { parts.append(L("%@ al sol", matching)) }
        else { parts.append(L("%@ piedras", matching)) }
        if s.unknownOrientation > 0 { parts.append(L("%@ sin orientación", Int(s.unknownOrientation))) }
        return parts.joined(separator: " · ")
    }

    /// Etiqueta de una piedra: orientación votada y, si se sabe, sombra o sol.
    static func stoneLabel(_ s: AssistantStone) -> String {
        guard let aspect = s.aspect else { return L("sin orientación votada") }
        switch s.sun {
        case "SHADE": return "\(aspectLabel(aspect)) · \(L("sombra"))"
        case "SUN": return "\(aspectLabel(aspect)) · \(L("sol"))"
        case "MIXED": return "\(aspectLabel(aspect)) · \(L("sol y sombra"))"
        default: return aspectLabel(aspect)
        }
    }
}
