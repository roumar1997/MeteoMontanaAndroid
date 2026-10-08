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
        case "NEEDS_TWO_SCHOOLS": return L("¿Con qué otra escuela quieres compararla?")
        case "NEEDS_LOCATION":
            return L("Necesito saber dónde estás. Activa la ubicación o dime el nombre del sitio.")
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
        if u.topRated { out.append(L("Mejor valoradas")) }
        if let stars = u.minStars { out.append("★ ≥ \(stars.intValue)") }
        if let hours = u.hoursAhead { out.append("\(hours.intValue) h") }
        if u.useMyLocation { out.append(L("Mi ubicación")) }
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

    static func hitsIntro(total: Int, needsLocationForDistance: Bool, rated: Bool = false) -> String {
        var text: String
        if rated {
            text = total == 0
                ? L("Todavía nadie ha valorado vías con esos filtros.")
                : L("Las mejor valoradas (%@):", total)
        } else {
            text = total == 0
                ? L("No he encontrado vías o bloques con esos filtros.")
                : L("He encontrado %@ resultados:", total)
        }
        if needsLocationForDistance {
            text += " " + L("Para filtrar por distancia activa tu ubicación.")
        }
        return text
    }

    /// Aviso de que hay más resultados de los que caben en el chat; nil si se ven todos.
    static func moreNote(total: Int, shown: Int) -> String? {
        total > shown ? L("Y %@ más. Mira todos en Escuelas → Vías/Bloques.", total - shown) : nil
    }

    // MARK: Estrellas

    /// "★ 4,5 (8)": media y número de votos, con la coma o el punto del idioma de la app.
    static func starsLabel(rating: Double, count: Int) -> String {
        "★ \(oneDecimal(rating)) (\(count))"
    }

    /// Deja solo las vías que alguien ha valorado (y, si dijo cuántas estrellas, con esa media o más),
    /// de más a menos estrellas; a igualdad, primero las que más gente ha valorado y, si aún empatan,
    /// el orden en que llegaron. Una vía sin votos no es "mala": simplemente no se puede ordenar.
    static func rankByRating(_ hits: [LineSearchHit], minStars: Int?) -> [LineSearchHit] {
        struct Rated { let hit: LineSearchHit; let avg: Double; let votes: Int; let order: Int }
        let rated: [Rated] = hits.enumerated().compactMap { index, h in
            guard let avg = h.rating?.doubleValue, let votes = h.ratingCount?.intValue, votes > 0 else { return nil }
            return Rated(hit: h, avg: avg, votes: Int(votes), order: index)
        }
        let kept = rated.filter { minStars == nil || $0.avg >= Double(minStars ?? 0) }
        return kept.sorted {
            if $0.avg != $1.avg { return $0.avg > $1.avg }
            if $0.votes != $1.votes { return $0.votes > $1.votes }
            return $0.order < $1.order
        }.map { $0.hit }
    }

    // MARK: Comparar

    static func compareIntro(_ r: AssistantRecommendation) -> String {
        if r.schools.isEmpty { return L("No he podido comparar esas escuelas.") }
        let names = r.schools.map { $0.name }.joined(separator: ", ")
        var text = L("Comparación de %@:", names)
        if r.forecastAvailable { text += " " + L("Ordenadas por el tiempo de esos días y el nº de vías.") }
        return text
    }

    // MARK: El tiempo

    /// Una décima, con coma en español y punto en inglés.
    static func oneDecimal(_ value: Double) -> String {
        let s = String(format: "%.1f", value)
        return LanguageManager.shared.effectiveCode == "en" ? s : s.replacingOccurrences(of: ".", with: ",")
    }

    static func weatherTitle(_ w: AssistantWeather) -> String {
        w.placeName ?? L("Tu ubicación")
    }

    /// "15h" a partir de "2026-10-08T15:00".
    static func hourLabel(_ iso: String) -> String {
        guard let t = iso.split(separator: "T").last, t.count >= 2 else { return iso }
        return "\(t.prefix(2))h"
    }

    /// Altura de la barra de lluvia de una hora: 3 mm o más llenan la barra; un poco de lluvia siempre se ve.
    static func rainBarHeight(mm: Double, full: CGFloat) -> CGFloat {
        guard mm > 0 else { return 0 }
        return max(3, min(full, CGFloat(mm / 3.0) * full))
    }

    static func windLabel(_ kmh: Double) -> String {
        if kmh < 12 { return L("flojo") }
        if kmh < 25 { return L("moderado") }
        if kmh < 40 { return L("fuerte") }
        return L("muy fuerte")
    }

    /// Responde a "¿va a llover?" con las horas que preguntó y el reparto real del pronóstico.
    static func rainHeadline(_ w: AssistantWeather) -> String {
        let r = w.rain
        let hours = Int(r.hoursChecked)
        guard r.expected, let start = r.startsInHours?.intValue else {
            return L("No se espera lluvia en las próximas %@ h (probabilidad máxima %@ %).", hours, Int(r.maxProbability))
        }
        if start == 0 {
            return L("Está lloviendo ahora o va a empezar ya (≈%@ mm en las próximas %@ h).", oneDecimal(r.totalMm), hours)
        }
        return L("Sí: empezará a llover en ~%@ h (≈%@ mm en las próximas %@ h, hasta un %@ % de probabilidad).",
                 Int(start), oneDecimal(r.totalMm), hours, Int(r.maxProbability))
    }

    /// La frase del asistente sobre el tiempo, según lo que se preguntó.
    static func weatherHeadline(_ w: AssistantWeather) -> String {
        let place = weatherTitle(w)
        let n = w.now
        switch w.topic {
        case "RAIN":
            return L("%@: %@", place, rainHeadline(w))
        case "WIND":
            let peak = max(n.windKmh, w.hours.map { $0.windKmh }.max() ?? n.windKmh)
            return L("%@: viento ahora %@ km/h (%@). Máximo en las próximas horas: %@ km/h.",
                     place, Int(n.windKmh.rounded()), windLabel(n.windKmh), Int(peak.rounded()))
        case "HUMIDITY":
            var text = L("%@: humedad %@ %.", place, Int(n.humidity.rounded()))
            if let dew = n.dewPoint?.doubleValue { text += " " + L("Punto de rocío: %@ °C.", Int(dew.rounded())) }
            text += " " + (w.climbing.rockWet ? L("La roca está húmeda.") : L("La roca está seca."))
            return text
        case "TEMPERATURE":
            let temps = w.hours.map { $0.temperature }
            let low = Int((temps.min() ?? n.temperature).rounded()), high = Int((temps.max() ?? n.temperature).rounded())
            return L("%@: ahora hay %@ °C. En las próximas horas, entre %@ y %@ °C.",
                     place, Int(n.temperature.rounded()), low, high)
        default:
            return L("%@: ahora %@ °C, viento %@ km/h, humedad %@ %. Para escalar: %@ (%@/100).",
                     place, Int(n.temperature.rounded()), Int(n.windKmh.rounded()),
                     Int(n.humidity.rounded()), w.climbing.label, Int(w.climbing.score))
        }
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
