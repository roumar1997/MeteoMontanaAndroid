import AVFoundation
import Speech
import SwiftUI

/// Modo "hablar" del asistente: tocas el micrófono (o mantienes pulsada la burbuja),
/// hablas, y al quedarte en silencio la pregunta se envía sola. El texto va apareciendo
/// en la caja mientras hablas; si tocas el micrófono otra vez antes de que se envíe,
/// se para SIN enviar y el texto queda en la caja para corregirlo.
///
/// Privacidad: si el dispositivo lo permite, el reconocimiento se hace EN EL MÓVIL
/// (`requiresOnDeviceRecognition`) y el audio no sale de él. Si no, iOS puede procesarlo
/// en los servidores de Apple — por eso el texto del permiso lo dice. Nunca se envía
/// audio a nuestro servidor: solo el texto.
@MainActor
final class AssistantVoiceInput: ObservableObject {
    @Published private(set) var isListening = false
    /// true si el usuario negó el permiso de micrófono o de reconocimiento de voz.
    @Published private(set) var permissionDenied = false
    /// true si se escuchó un rato sin oír nada: la pantalla avisa en vez de callar.
    @Published private(set) var heardNothing = false

    /// Recibe el texto reconocido hasta el momento (se llama varias veces mientras habla).
    var onText: ((String) -> Void)?
    /// Se llama UNA vez cuando has dejado de hablar, con la pregunta lista para enviar.
    var onFinished: ((String) -> Void)?

    private let policy = AssistantSpeechPolicy()
    private let recognizer = SFSpeechRecognizer(locale: LanguageManager.shared.locale)
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var timer: Timer?

    private var startedAt = Date()
    private var lastTextAt: Date?
    private var text = ""

    func toggle() {
        if isListening { stop() } else { start() }
    }

    func start() {
        guard !isListening else { return }
        heardNothing = false
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

    /// Para de escuchar SIN enviar. Idempotente: se llama al terminar, al fallar y al tocar el
    /// botón; limpiar dos veces no hace daño.
    func stop() {
        timer?.invalidate()
        timer = nil
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.finish()
        request = nil
        task = nil
        isListening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// Has terminado de hablar: para y entrega la pregunta una sola vez.
    private func finishAndSend() {
        guard isListening else { return }
        let spoken = text.trimmingCharacters(in: .whitespacesAndNewlines)
        stop()
        if !spoken.isEmpty { onFinished?(spoken) }
    }

    private func tick() {
        guard isListening else { return }
        switch policy.decide(now: Date(), startedAt: startedAt, lastTextAt: lastTextAt,
                             hasText: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
        case .keepListening: break
        case .send: finishAndSend()
        case .giveUp: stop(); heardNothing = true
        }
    }

    private func beginRecognition() {
        guard let recognizer, recognizer.isAvailable else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            text = ""
            startedAt = Date()
            lastTextAt = nil

            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
            request = req

            task = recognizer.recognitionTask(with: req) { [weak self] result, error in
                Task { @MainActor in
                    guard let self, self.isListening else { return }
                    if let result {
                        let heard = result.bestTranscription.formattedString
                        // Solo cuenta como "sigues hablando" si lo reconocido CAMBIA.
                        if heard != self.text { self.text = heard; self.lastTextAt = Date() }
                        self.onText?(heard)
                        if result.isFinal { self.finishAndSend(); return }
                    }
                    if error != nil {
                        // El reconocimiento terminó solo: con texto se envía, sin texto se abandona.
                        if self.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            self.stop()
                        } else {
                            self.finishAndSend()
                        }
                    }
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

            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.tick() }
            }
        } catch {
            stop()
        }
    }
}
