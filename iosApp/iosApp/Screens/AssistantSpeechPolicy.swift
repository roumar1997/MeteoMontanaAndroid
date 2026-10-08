import Foundation

/// Cuándo el modo "hablar" decide que has terminado y manda la pregunta solo.
/// Es lógica de tiempos pura (sin micrófono) para poder probarla sin dispositivo.
///
/// - Después de oír algo, ~1,5 s de silencio = has acabado → se envía.
/// - Si no se oye nada en unos segundos → se abandona sin enviar (no se gasta cupo).
/// - Tope de duración, por si hay ruido de fondo que mantiene vivo el reconocimiento.
struct AssistantSpeechPolicy {
    var silenceAfterText: TimeInterval = 1.5
    var noSpeechTimeout: TimeInterval = 7
    var maxDuration: TimeInterval = 25

    enum Decision: Equatable {
        case keepListening
        /// Hay texto y has dejado de hablar (o se alcanzó el tope): enviarlo.
        case send
        /// No se oyó nada: parar sin enviar.
        case giveUp
    }

    /// - Parameters:
    ///   - startedAt: cuándo empezó a escuchar.
    ///   - lastTextAt: cuándo CAMBIÓ por última vez lo reconocido (nil si aún nada).
    func decide(now: Date, startedAt: Date, lastTextAt: Date?, hasText: Bool) -> Decision {
        if now.timeIntervalSince(startedAt) >= maxDuration {
            return hasText ? .send : .giveUp
        }
        guard hasText, let lastTextAt else {
            return now.timeIntervalSince(startedAt) >= noSpeechTimeout ? .giveUp : .keepListening
        }
        return now.timeIntervalSince(lastTextAt) >= silenceAfterText ? .send : .keepListening
    }
}
