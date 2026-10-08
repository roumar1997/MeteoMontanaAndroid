import Foundation
import Shared

/// Conversación del asistente guardada EN EL MÓVIL (nunca en un servidor) para que
/// no se pierda si el usuario cierra la app un rato.
///
/// Decisiones (Álvaro, 2026-10-08):
///  - Solo unas horas: pasadas `ttl` la conversación empieza de cero.
///  - Se guardan también las tarjetas de resultados (para poder comprobar lo que se
///    enseñó), como texto (`payload`, lo genera AssistantSnapshotCodec en Kotlin). Como llevan
///    pronóstico, cada mensaje guarda su hora y la pantalla avisa de cuánto hace.
///  - Es de UNA cuenta: si cambia el usuario, no se restaura (y se borra al cerrar sesión).
struct StoredAssistantConversation: Codable, Equatable {
    struct Message: Codable, Equatable {
        var isUser: Bool
        var text: String
        var chips: [String]
        /// Tarjetas del mensaje (escuelas, sectores, vías) como texto; nil si no tenía.
        var payload: String?
        var createdAt: Date
    }

    /// Lo último que entendió el servidor, para poder seguir refinando ("ahora solo 7a").
    struct Understood: Codable, Equatable {
        var intent: String
        var dateFrom: String?
        var dateTo: String?
        var gradeMin: String?
        var gradeMax: String?
        var discipline: String?
        var rockTypes: [String]
        var orientations: [String]
        var maxDistanceKm: Double?
        var q: String?
        var schoolMention: String?
        var sectorMention: String?
        var sun: String?
        var dayPart: String?
        // Campos nuevos como opcionales: una conversación guardada con la versión anterior sigue leyéndose.
        var schoolMentions: [String]?
        var topRated: Bool?
        var minStars: Int?
        var hoursAhead: Int?
        var weatherTopic: String?
        var useMyLocation: Bool?
        var beta: String?
        var startType: String?
        var withTopo: Bool?
        var noRain: Bool?
        var mineTopic: String?
        var year: Int?
        var notDone: Bool?
        var action: String?
        var mostLines: Bool?
    }

    var uid: String
    var savedAt: Date
    var messages: [Message]
    var previous: Understood?
}

extension StoredAssistantConversation.Understood {
    init(_ u: AssistantUnderstood) {
        self.init(intent: u.intent, dateFrom: u.dateFrom, dateTo: u.dateTo,
                  gradeMin: u.gradeMin, gradeMax: u.gradeMax, discipline: u.discipline,
                  rockTypes: u.rockTypes, orientations: u.orientations,
                  maxDistanceKm: u.maxDistanceKm?.doubleValue, q: u.q,
                  schoolMention: u.schoolMention, sectorMention: u.sectorMention,
                  sun: u.sun, dayPart: u.dayPart,
                  schoolMentions: u.schoolMentions, topRated: u.topRated,
                  minStars: u.minStars.map { Int($0.intValue) }, hoursAhead: u.hoursAhead.map { Int($0.intValue) },
                  weatherTopic: u.weatherTopic, useMyLocation: u.useMyLocation, beta: u.beta,
                  startType: u.startType, withTopo: u.withTopo, noRain: u.noRain,
                  mineTopic: u.mineTopic, year: u.year.map { Int($0.intValue) }, notDone: u.notDone,
                  action: u.action, mostLines: u.mostLines)
    }

    func toKotlin() -> AssistantUnderstood {
        AssistantUnderstood(
            intent: intent, dateFrom: dateFrom, dateTo: dateTo,
            gradeMin: gradeMin, gradeMax: gradeMax, discipline: discipline,
            rockTypes: rockTypes, orientations: orientations,
            maxDistanceKm: maxDistanceKm.map { KotlinDouble(double: $0) },
            q: q, schoolMention: schoolMention, sectorMention: sectorMention,
            sun: sun, dayPart: dayPart,
            schoolMentions: schoolMentions ?? [], topRated: topRated ?? false,
            minStars: minStars.map { KotlinInt(int: Int32($0)) },
            hoursAhead: hoursAhead.map { KotlinInt(int: Int32($0)) },
            weatherTopic: weatherTopic, useMyLocation: useMyLocation ?? false, beta: beta,
            startType: startType, withTopo: withTopo ?? false, noRain: noRain ?? false,
            mineTopic: mineTopic, year: year.map { KotlinInt(int: Int32($0)) }, notDone: notDone ?? false,
            action: action, mostLines: mostLines ?? false)
    }
}

struct AssistantTranscriptStore {
    /// Cuánto se conserva: pasado esto, la conversación se descarta.
    static let ttl: TimeInterval = 12 * 3600
    /// Máximo de mensajes guardados (los más recientes).
    static let maxMessages = 30

    private let defaults: UserDefaults
    private let key = "assistant_conversation_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(_ conversation: StoredAssistantConversation) {
        var c = conversation
        c.messages = Array(c.messages.suffix(Self.maxMessages))
        if let data = try? JSONEncoder().encode(c) { defaults.set(data, forKey: key) }
    }

    /// La conversación guardada si es de esta cuenta y no ha caducado; si no, nil
    /// (y se borra lo caducado para no dejar basura).
    func load(uid: String, now: Date = Date()) -> StoredAssistantConversation? {
        guard let data = defaults.data(forKey: key),
              let c = try? JSONDecoder().decode(StoredAssistantConversation.self, from: data) else { return nil }
        guard now.timeIntervalSince(c.savedAt) < Self.ttl else { clear(); return nil }
        return c.uid == uid ? c : nil
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}
