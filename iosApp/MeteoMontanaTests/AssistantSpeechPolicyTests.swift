import XCTest
@testable import MeteoMontana

/// Cuándo el modo "hablar" decide que has terminado. Solo tiempos: sin micrófono.
final class AssistantSpeechPolicyTests: XCTestCase {

    private let policy = AssistantSpeechPolicy()
    private let inicio = Date(timeIntervalSince1970: 1_800_000_000)

    private func decide(_ segundos: TimeInterval, ultimoTexto: TimeInterval?, hayTexto: Bool) -> AssistantSpeechPolicy.Decision {
        policy.decide(now: inicio.addingTimeInterval(segundos), startedAt: inicio,
                      lastTextAt: ultimoTexto.map { inicio.addingTimeInterval($0) }, hasText: hayTexto)
    }

    func testMientrasHablasSigueEscuchando() {
        // Oyó texto hace 0,5 s: aún no ha pasado el silencio.
        XCTAssertEqual(decide(3.0, ultimoTexto: 2.5, hayTexto: true), .keepListening)
    }

    func testUnaPausaParaPensarNoCorta() {
        // Con la pausa de antes (1,5 s) cortaba a media frase. Ahora una pausa de 2 s sigue escuchando.
        XCTAssertEqual(decide(5.0, ultimoTexto: 3.0, hayTexto: true), .keepListening)
    }

    func testTrasUnSilencioLargoSeEnvia() {
        XCTAssertEqual(decide(6.0, ultimoTexto: 3.0, hayTexto: true), .send)
    }

    func testJustoAntesDelSilencioNoSeEnvia() {
        let limite = policy.silenceAfterText
        XCTAssertEqual(decide(3.0 + limite - 0.1, ultimoTexto: 3.0, hayTexto: true), .keepListening)
        XCTAssertEqual(decide(3.0 + limite, ultimoTexto: 3.0, hayTexto: true), .send)
    }

    func testSinOirNadaEsperaUnRatoYSeAbandona() {
        XCTAssertEqual(decide(3.0, ultimoTexto: nil, hayTexto: false), .keepListening)
        XCTAssertEqual(decide(policy.noSpeechTimeout, ultimoTexto: nil, hayTexto: false), .giveUp)
    }

    func testElSilencioInicialNoEnviaNada() {
        // Callado desde el principio: nunca debe "enviar" una pregunta vacía.
        for t in stride(from: 0.0, through: policy.noSpeechTimeout - 0.1, by: 0.5) {
            XCTAssertNotEqual(decide(t, ultimoTexto: nil, hayTexto: false), .send)
        }
    }

    func testRuidoContinuoTieneTope() {
        // El texto sigue cambiando (ruido de fondo): a los 25 s se envía lo que haya.
        XCTAssertEqual(decide(25.0, ultimoTexto: 24.9, hayTexto: true), .send)
        XCTAssertEqual(decide(25.0, ultimoTexto: nil, hayTexto: false), .giveUp)
    }
}
