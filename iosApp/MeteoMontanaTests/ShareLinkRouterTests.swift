import XCTest
@testable import MeteoMontana

/// Espejo iOS del parseo de deep-links de Android (DeepLinkParserTest.kt).
/// Cubre los casos SÍNCRONOS de `ShareLinkRouter.handle` (q/u/p) y el rechazo
/// de URLs ajenas. Los casos e/v son asíncronos (cargan la escuela) → solo se
/// comprueba que se consumen (return true).
@MainActor
final class ShareLinkRouterTests: XCTestCase {

    private func url(_ s: String) -> URL { URL(string: "https://climbingteams.com/\(s)")! }

    func testQuedadaConTokenFijaMeetupTarget() {
        let router = ShareLinkRouter()
        let consumed = router.handle(url("s/q/m123?i=tok-abc"))
        XCTAssertTrue(consumed)
        XCTAssertEqual(router.target?.meetupId, "m123")
    }

    func testPerfilFijaUserHandle() {
        let router = ShareLinkRouter()
        XCTAssertTrue(router.handle(url("s/u/jara")))
        XCTAssertEqual(router.target?.userHandle, "jara")
    }

    func testPublicacionFijaFeedPostId() {
        let router = ShareLinkRouter()
        XCTAssertTrue(router.handle(url("s/p/42")))
        XCTAssertEqual(router.target?.feedPostId, "42")
    }

    func testUrlAjenaNoSeConsume() {
        XCTAssertFalse(ShareLinkRouter().handle(url("otra/cosa")))
    }

    func testSSinSegmentosSuficientesNoSeConsume() {
        XCTAssertFalse(ShareLinkRouter().handle(url("s/q")))   // faltan segmentos (< 3)
    }
}
