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

    func testTrasUnSilencioSeEnvia() {
        XCTAssertEqual(decide(5.0, ultimoTexto: 3.0, hayTexto: true), .send)
    }

    func testJustoAntesDelSilencioNoSeEnvia() {
        XCTAssertEqual(decide(4.4, ultimoTexto: 3.0, hayTexto: true), .keepListening)   // 1,4 s < 1,5 s
        XCTAssertEqual(decide(4.5, ultimoTexto: 3.0, hayTexto: true), .send)            // 1,5 s
    }

    func testSinOirNadaEsperaUnRatoYSeAbandona() {
        XCTAssertEqual(decide(3.0, ultimoTexto: nil, hayTexto: false), .keepListening)
        XCTAssertEqual(decide(7.0, ultimoTexto: nil, hayTexto: false), .giveUp)
    }

    func testElSilencioInicialNoEnviaNada() {
        // Callado desde el principio: nunca debe "enviar" una pregunta vacía.
        for t in stride(from: 0.0, through: 6.9, by: 0.5) {
            XCTAssertNotEqual(decide(t, ultimoTexto: nil, hayTexto: false), .send)
        }
    }

    func testRuidoContinuoTieneTope() {
        // El texto sigue cambiando (ruido de fondo): a los 25 s se envía lo que haya.
        XCTAssertEqual(decide(25.0, ultimoTexto: 24.9, hayTexto: true), .send)
        XCTAssertEqual(decide(25.0, ultimoTexto: nil, hayTexto: false), .giveUp)
    }
}
