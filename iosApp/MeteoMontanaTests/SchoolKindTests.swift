import XCTest
@testable import MeteoMontana

/// El icono de la lista (mosquetón / crashpad / los dos) sale del campo `style`
/// del catálogo: "Bloque", "Vía" o "Bloque,Vía".
final class SchoolKindTests: XCTestCase {

    func testVia() {
        let k = SchoolKind(style: "Vía")
        XCTAssertTrue(k.hasRoutes)
        XCTAssertFalse(k.hasBoulders)
    }

    func testBloque() {
        let k = SchoolKind(style: "Bloque")
        XCTAssertTrue(k.hasBoulders)
        XCTAssertFalse(k.hasRoutes)
    }

    func testAmbos() {
        let k = SchoolKind(style: "Bloque,Vía")
        XCTAssertTrue(k.hasBoulders)
        XCTAssertTrue(k.hasRoutes)
    }

    func testSinTildeNiMayusculas() {
        XCTAssertTrue(SchoolKind(style: "VIA").hasRoutes)
        XCTAssertTrue(SchoolKind(style: "bloque").hasBoulders)
    }

    func testIsSingleSoloSiEsUnaDeLasDos() {
        XCTAssertTrue(SchoolKind(style: "Vía").isSingle)
        XCTAssertTrue(SchoolKind(style: "Bloque").isSingle)
        XCTAssertFalse(SchoolKind(style: "Bloque,Vía").isSingle)
        XCTAssertFalse(SchoolKind(style: nil).isSingle)
    }

    func testSinEstiloNoMuestraNada() {
        XCTAssertTrue(SchoolKind(style: nil).isEmpty)
        XCTAssertTrue(SchoolKind(style: "").isEmpty)
        XCTAssertTrue(SchoolKind(style: "otra cosa").isEmpty)
    }
}
