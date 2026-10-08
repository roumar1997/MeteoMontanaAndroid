import XCTest
@testable import MeteoMontana

/// La conversación del asistente se guarda unas horas en el móvil. Se comprueba que
/// caduca, que es de una sola cuenta y que no acumula mensajes sin límite.
final class AssistantTranscriptStoreTests: XCTestCase {

    private var defaults: UserDefaults!
    private var store: AssistantTranscriptStore!
    private let ahora = Date(timeIntervalSince1970: 1_800_000_000)

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: "AssistantTranscriptStoreTests")!
        defaults.removePersistentDomain(forName: "AssistantTranscriptStoreTests")
        store = AssistantTranscriptStore(defaults: defaults)
    }

    private func conversacion(uid: String = "u1", guardada: Date, mensajes: Int = 2) -> StoredAssistantConversation {
        StoredAssistantConversation(
            uid: uid, savedAt: guardada,
            messages: (0..<mensajes).map {
                StoredAssistantConversation.Message(
                    isUser: $0 % 2 == 0, text: "mensaje \($0)", chips: ["6A – 7A"],
                    payload: $0 % 2 == 1 ? "{\"hitsTotal\":3}" : nil, createdAt: guardada)
            },
            previous: StoredAssistantConversation.Understood(intent: "SEARCH", dateFrom: nil, dateTo: nil, gradeMin: "6A", gradeMax: "7A",
                            discipline: "BOULDER", rockTypes: [], orientations: [], maxDistanceKm: 50,
                            q: nil, schoolMention: nil, sectorMention: nil, sun: nil, dayPart: nil))
    }

    func testSeRecuperaLoGuardadoDeLaMismaCuenta() {
        store.save(conversacion(guardada: ahora))
        let c = store.load(uid: "u1", now: ahora.addingTimeInterval(3600))
        XCTAssertEqual(c?.messages.count, 2)
        XCTAssertEqual(c?.previous?.gradeMin, "6A")           // el hilo ("ahora solo 7a") sobrevive
        XCTAssertEqual(c?.previous?.maxDistanceKm, 50)
        XCTAssertEqual(c?.messages[1].payload, "{\"hitsTotal\":3}")   // las tarjetas también vuelven
        XCTAssertNil(c?.messages[0].payload)
    }

    func testCaducaPasadasLasHoras() {
        store.save(conversacion(guardada: ahora))
        let casi = ahora.addingTimeInterval(AssistantTranscriptStore.ttl - 60)
        let pasada = ahora.addingTimeInterval(AssistantTranscriptStore.ttl + 60)
        XCTAssertNotNil(store.load(uid: "u1", now: casi))
        XCTAssertNil(store.load(uid: "u1", now: pasada))
        XCTAssertNil(store.load(uid: "u1", now: casi), "lo caducado se borra y no vuelve")
    }

    func testOtraCuentaNoVeLaConversacionAjena() {
        store.save(conversacion(uid: "ana", guardada: ahora))
        XCTAssertNil(store.load(uid: "luis", now: ahora))
        XCTAssertNotNil(store.load(uid: "ana", now: ahora))
    }

    func testClearLaBorra() {
        store.save(conversacion(guardada: ahora))
        store.clear()
        XCTAssertNil(store.load(uid: "u1", now: ahora))
    }

    func testSoloSeGuardanLosUltimosMensajes() {
        store.save(conversacion(guardada: ahora, mensajes: 80))
        let c = store.load(uid: "u1", now: ahora)
        XCTAssertEqual(c?.messages.count, AssistantTranscriptStore.maxMessages)
        XCTAssertEqual(c?.messages.last?.text, "mensaje 79")   // se queda con los más recientes
    }
}
