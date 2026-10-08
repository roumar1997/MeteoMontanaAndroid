import AVFoundation
import Speech
import SwiftUI

/// Dictado por voz del asistente: el usuario toca el micrófono, habla y el
/// texto aparece en la caja del chat (como el dictado del teclado, pero con
/// botón propio dentro del chat).
///
/// Privacidad: si el dispositivo lo permite, el reconocimiento se hace EN EL
/// MÓVIL (`requiresOnDeviceRecognition`) y el audio no sale de él. Si no, iOS
/// puede procesarlo en los servidores de Apple — por eso el texto del permiso
/// lo dice. Nunca se envía audio a nuestro servidor: solo el texto.
@MainActor
final class AssistantVoiceInput: ObservableObject {
    @Published private(set) var isListening = false
    /// true si el usuario negó el permiso de micrófono o de reconocimiento de voz.
    @Published private(set) var permissionDenied = false

    /// Recibe el texto reconocido hasta el momento (se llama varias veces mientras habla).
    var onText: ((String) -> Void)?

    private let recognizer = SFSpeechRecognizer(locale: LanguageManager.shared.locale)
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func toggle() {
        if isListening { stop() } else { start() }
    }

    func start() {
        guard !isListening else { return }
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Task { @MainActor in
                guard let self else { return }
                guard status == .authorized else { self.permissionDenied = true; return }
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    Task { @MainActor in
                        guard granted else { self.permissionDenied = true; return }
                        self.permissionDenied = false
                        self.beginRecognition()
                    }
                }
            }
        }
    }

    /// Idempotente: se llama al terminar, al fallar y al tocar el botón; limpiar dos veces no hace daño.
    func stop() {
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.finish()
        request = nil
        task = nil
        isListening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func beginRecognition() {
        guard let recognizer, recognizer.isAvailable else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
            request = req

            task = recognizer.recognitionTask(with: req) { [weak self] result, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let result { self.onText?(result.bestTranscription.formattedString) }
                    if error != nil || (result?.isFinal ?? false) { self.stop() }
                }
            }

            let node = engine.inputNode
            node.removeTap(onBus: 0)
            node.installTap(onBus: 0, bufferSize: 1024, format: node.outputFormat(forBus: 0)) { buffer, _ in
                req.append(buffer)
            }
            engine.prepare()
            try engine.start()
            isListening = true
        } catch {
            stop()
        }
    }
}
