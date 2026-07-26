import XCTest
import CoreGraphics
@testable import MeteoMontana

/// Espejo iOS del parser de trazados (Android: LinePathTest.kt). `TopoParse.points`
/// y `pointsEqual` los usan 6 pantallas; si divergen del Kotlin, las líneas se
/// pintan mal. Más `TopoPainter.startLabel` (etiqueta del tipo de inicio).
final class TopoParseTests: XCTestCase {

    func testPointsParseaJsonNormalizado() {
        let pts = TopoParse.points("[{\"x\":0.1,\"y\":0.9},{\"x\":0.4,\"y\":0.3}]")
        XCTAssertEqual(pts.count, 2)
        XCTAssertEqual(pts[0].x, 0.1, accuracy: 1e-9)
        XCTAssertEqual(pts[0].y, 0.9, accuracy: 1e-9)
        XCTAssertEqual(pts[1].x, 0.4, accuracy: 1e-9)
    }

    func testPointsConNilOJsonInvalidoDevuelveVacio() {
        XCTAssertTrue(TopoParse.points(nil).isEmpty)
        XCTAssertTrue(TopoParse.points("no soy json").isEmpty)
        XCTAssertTrue(TopoParse.points("{\"x\":1}").isEmpty)   // objeto, no array
    }

    func testPointsEqualToleranciaMinima() {
        let a = [CGPoint(x: 0.10, y: 0.90), CGPoint(x: 0.40, y: 0.30)]
        let b = [CGPoint(x: 0.1005, y: 0.9004), CGPoint(x: 0.40, y: 0.30)]  // < 0.001
        XCTAssertTrue(TopoParse.pointsEqual(a, b))
        let c = [CGPoint(x: 0.20, y: 0.90), CGPoint(x: 0.40, y: 0.30)]      // difiere claramente
        XCTAssertFalse(TopoParse.pointsEqual(a, c))
        XCTAssertFalse(TopoParse.pointsEqual(a, [a[0]]))                    // distinto tamaño
    }

    func testStartLabelMapeaTodosLosTipos() {
        XCTAssertEqual(TopoPainter.startLabel("PIE"), "PIE")
        XCTAssertEqual(TopoPainter.startLabel("STAND"), "PIE")
        XCTAssertEqual(TopoPainter.startLabel("SIT"), "SIT")
        XCTAssertEqual(TopoPainter.startLabel("SEMI"), "SEM")
        XCTAssertEqual(TopoPainter.startLabel("LANCE"), "LAN")
        XCTAssertEqual(TopoPainter.startLabel("JUMP"), "LAN")
        XCTAssertEqual(TopoPainter.startLabel("TRAV"), "TRV")
        XCTAssertNil(TopoPainter.startLabel("desconocido"))
        XCTAssertNil(TopoPainter.startLabel(nil))
    }
}
