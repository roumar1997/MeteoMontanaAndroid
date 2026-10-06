import XCTest
import CoreLocation
@testable import MeteoMontana

/// Marcador de piedra: crashpad (bloque) / mosquetón (vía) con chapa de número.
final class MarkerRendererTests: XCTestCase {

    private func marker(_ discipline: String?, name: String = "7") -> CumbreMarker {
        CumbreMarker(id: "b1", coordinate: CLLocationCoordinate2D(latitude: 40, longitude: -3),
                     title: name, kind: .block, name: name, discipline: discipline)
    }

    func testLosIconosEstanEnLosAssets() {
        XCTAssertNotNil(UIImage(named: "kind_route"))
        XCTAssertNotNil(UIImage(named: "kind_boulder"))
    }

    func testBloqueYViaSeDibujanConIconoNoConPoligono() {
        let boulder = MarkerRenderer.image(for: marker("BOULDER"))
        let route = MarkerRenderer.image(for: marker("ROUTE"))
        let classic = MarkerRenderer.image(for: marker(nil))
        XCTAssertEqual(boulder.size, CGSize(width: 64, height: 58))
        XCTAssertEqual(route.size, CGSize(width: 64, height: 58))
        XCTAssertNotEqual(classic.size, boulder.size, "sin modalidad se mantiene el polígono clásico (52x52)")
    }

    func testLaModalidadCuentaParaLaFirmaDeDibujo() {
        XCTAssertNotEqual(marker("BOULDER").drawSignature, marker("ROUTE").drawSignature)
        XCTAssertNotEqual(marker(nil).drawSignature, marker("BOULDER").drawSignature)
    }
}
