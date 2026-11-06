import SwiftUI
import PhotosUI
import ImageIO
import UniformTypeIdentifiers
import Shared

// Mensajes del chat del asistente y acciones pendientes de confirmar.

/// Un mensaje del chat: del usuario o del asistente (con datos opcionales).
struct AssistantMessage: Identifiable {
    let id = UUID()
    let isUser: Bool
    let text: String
    var chips: [String] = []
    var recommendation: AssistantRecommendation? = nil
    var breakdown: AssistantBreakdown? = nil
    /// El tiempo de un sitio (pregunta de lluvia, viento, humedad…).
    var weather: AssistantWeather? = nil
    /// Si la respuesta fue un fallo pasajero (ocupado, sin conexión…): la pregunta, para ofrecer "REINTENTAR".
    var retryText: String? = nil
    /// Vías o bloques encontrados por una búsqueda simple, y cuántos hubo en total.
    var hits: [LineSearchHit] = []
    var hitsTotal = 0
    /// Cuándo se escribió. Los mensajes recuperados de una sesión anterior enseñan sus tarjetas
    /// con un aviso de cuánto hace (el pronóstico puede haber cambiado).
    var createdAt = Date()
    var restored = false
    /// Opciones tocables de "¿a cuál te refieres?".
    var options: [AssistantOption] = []
    /// Entradas de su diario que se enseñan como lista tocable ("lo que hice la última semana"), y cuántas
    /// cumplen en total (la lista enseña solo las más recientes).
    var journalEntries: [JournalSession] = []
    var journalTotal = 0
    /// Lo que la app hará por el usuario SOLO si lo confirma (favoritas, abrir una escuela, apuntar en el
    /// diario, aviso de fin de semana). Varias cuando el nombre de una vía es ambiguo: elige una.
    var pendingActions: [PendingAction] = []
    /// Varias vías del día a la vez: un solo botón "Confirmar todas" en vez de elegir una.
    var confirmAll = false
    /// "¿Se puede escalar en X?": restricciones con datos reales.
    var restrictions: AssistantRestrictions? = nil
    /// Viaje de varios días: la mejor escuela de cada día.
    var trip: [AssistantPresenter.TripDay] = []
    /// "¿Qué piedra es esta?": las más probables, con su foto.
    var photoMatches: [AssistantPhotoMatcher.Candidate] = []
}

/// Una acción propuesta en el chat. Nunca se ejecuta sin que el usuario la confirme con un toque.
struct PendingAction: Identifiable, Equatable {
    enum State { case waiting, done, cancelled }
    let id = UUID()
    /// ADD_FAVORITE | REMOVE_FAVORITE | OPEN_SCHOOL | ENABLE_WEEKEND_ALERT | LOG_DONE | LOG_PROJECT |
    /// CREATE_MEETUP | ENABLE_MORNING_ALERT | SET_HOME
    let kind: String
    let schoolId: String
    let schoolName: String
    /// Para apuntar en el diario: la vía encontrada y el día ("yyyy-MM-dd").
    var hit: LineSearchHit? = nil
    var date: String? = nil
    /// Quedada: hora "HH:mm" dicha.
    var time: String? = nil
    var state: State = .waiting
}
