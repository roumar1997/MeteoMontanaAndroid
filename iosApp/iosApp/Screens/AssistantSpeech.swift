import AVFoundation
import Foundation

/// Cuándo contesta el asistente en voz alta. Se guarda en el móvil (Ajustes del chat).
enum AssistantSpeechMode: String, CaseIterable, Identifiable {
    /// Si preguntas hablando, contesta hablando (lo natural en la roca, con las manos ocupadas).
    case whenSpoken
    case always
    case never

    var id: String { rawValue }

    static let storageKey = "assistant_speech_mode"

    static var current: AssistantSpeechMode {
        AssistantSpeechMode(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .whenSpoken
    }

    var title: String {
        switch self {
        case .whenSpoken: return L("Si pregunto hablando")
        case .always: return L("Siempre")
        case .never: return L("Nunca")
        }
    }

    /// ¿Hay que leer esta respuesta? `spokenQuestion` = la pregunta se dictó.
    func shouldSpeak(spokenQuestion: Bool) -> Bool {
        switch self {
        case .always: return true
        case .whenSpoken: return spokenQuestion
        case .never: return false
        }
    }
}

/// Lee en voz alta las respuestas con las voces del propio iPhone: gratis, sin red y sin enviar nada fuera.
/// Usa la mejor voz instalada del idioma de la app (si el usuario descargó una "mejorada" o "premium", esa).
@MainActor
final class AssistantSpeaker: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = AssistantSpeaker()

    private let synth = AVSpeechSynthesizer()
    /// Id del mensaje que se está leyendo (el altavoz de esa burbuja se pinta activo).
    @Published private(set) var speakingId: UUID?

    override private init() {
        super.init()
        synth.delegate = self
    }

    func speak(_ text: String, id: UUID) {
        stop()
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        // Tras dictar, la sesión de audio queda en modo grabación: se pasa a reproducir (bajando otra música).
        guard !AssistantVoiceInput.isRecording else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: .duckOthers)
        try? AVAudioSession.sharedInstance().setActive(true)
        let utterance = AVSpeechUtterance(string: clean)
        utterance.voice = Self.bestVoice()
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 1.02
        speakingId = id
        synth.speak(utterance)
    }

    func stop() {
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        speakingId = nil
    }

    func toggle(_ text: String, id: UUID) {
        if speakingId == id { stop() } else { speak(text, id: id) }
    }

    /// La voz de más calidad instalada para el idioma de la app (premium > mejorada > normal).
    static func bestVoice() -> AVSpeechSynthesisVoice? {
        let lang = LanguageManager.shared.effectiveCode == "en" ? "en" : "es"
        let preferred = lang == "en" ? "en-GB" : "es-ES"
        let voices = AVSpeechSynthesisVoice.speechVoices().filter { $0.language.hasPrefix(lang) }
        func rank(_ v: AVSpeechSynthesisVoice) -> Int {
            let quality: Int
            switch v.quality {
            case .premium: quality = 3
            case .enhanced: quality = 2
            default: quality = 1
            }
            return quality * 10 + (v.language == preferred ? 1 : 0)
        }
        return voices.max(by: { rank($0) < rank($1) }) ?? AVSpeechSynthesisVoice(language: preferred)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.speakingId = nil
            if !AssistantVoiceInput.isRecording {
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            }
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in self.speakingId = nil }
    }
}
