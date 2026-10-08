import XCTest
import Shared
@testable import MeteoMontana

/// Los textos y etiquetas del asistente salen de datos del servidor. Se
/// comprueban solo los trozos que no dependen del idioma del simulador
/// (números, grados, rangos), para que el test no cambie si el CI corre en inglés.
final class AssistantPresenterTests: XCTestCase {

    private func understood(
        gradeMin: String? = nil, gradeMax: String? = nil, discipline: String? = nil,
        km: Double? = nil, dateFrom: String? = nil, dateTo: String? = nil
    ) -> AssistantUnderstood {
        AssistantUnderstood(
            intent: "SEARCH", dateFrom: dateFrom, dateTo: dateTo,
            gradeMin: gradeMin, gradeMax: gradeMax, discipline: discipline,
            rockTypes: [], orientations: [],
            maxDistanceKm: km.map { KotlinDouble(double: $0) },
            q: nil, schoolMention: nil, sectorMention: nil, sun: nil, dayPart: nil)
    }

    func testRangoDeGradosYDistanciaSalenComoChips() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "6A", gradeMax: "7A", km: 50),
                                             school: nil, sector: nil)
        XCTAssertTrue(chips.contains("6A – 7A"))
        XCTAssertTrue(chips.contains("< 50 km"))
    }

    func testUnSoloGradoNoSeRepite() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "7A", gradeMax: "7A"), school: nil, sector: nil)
        XCTAssertTrue(chips.contains("7A"))
        XCTAssertFalse(chips.contains("7A – 7A"))
    }

    func testEscuelaYSectorResueltosSalenPrimero() {
        let chips = AssistantPresenter.chips(understood(gradeMin: "6A", gradeMax: "7A"),
                                             school: "Albarracín", sector: "Techos")
        XCTAssertEqual(Array(chips.prefix(2)), ["Albarracín", "Techos"])
    }

    func testRangoDeFechasDeUnMes() {
        let r = AssistantPresenter.dateRange("2026-12-05", "2026-12-08")
        XCTAssertTrue(r.hasPrefix("5 – 8 "), r)
    }

    func testUnSoloDiaNoLlevaRango() {
        let r = AssistantPresenter.dateRange("2026-12-05", "2026-12-05")
        XCTAssertTrue(r.hasPrefix("5 "), r)
        XCTAssertFalse(r.contains("–"), r)
    }

    func testEtiquetaDeDiaTerminaEnElNumero() {
        XCTAssertTrue(AssistantPresenter.dayLabel("2026-12-06").hasSuffix(" 6"))
        // Una fecha rota se devuelve tal cual, sin romper la pantalla.
        XCTAssertEqual(AssistantPresenter.dayLabel("no-es-fecha"), "no-es-fecha")
    }

    func testResumenDeSectorIncluyeLasCuentas() {
        let sector = AssistantSector(
            sectorId: "z1", name: "Techos", total: 5, matching: 2, unknownOrientation: 1,
            stones: [], unknownStones: [])
        let text = AssistantPresenter.sectorSummary(sector, sun: "SHADE")
        XCTAssertTrue(text.contains("2"), text)
        XCTAssertTrue(text.contains("1"), text)
    }

    func testPiedraSinOrientacionNoMuestraUnRumbo() {
        let sin = AssistantStone(blockId: "b", name: "P", aspect: nil, sun: nil, lineCount: 1, firstLineId: nil)
        let con = AssistantStone(blockId: "b", name: "P", aspect: "N", sun: "SHADE", lineCount: 1, firstLineId: "l1")
        XCTAssertFalse(AssistantPresenter.stoneLabel(sin).contains("N"), "sin dato no debe inventar un rumbo")
        XCTAssertTrue(AssistantPresenter.stoneLabel(con).hasPrefix("N"))
    }

    private func hit(_ school: String, _ name: String) -> LineSearchHit {
        LineSearchHit(
            schoolId: school, schoolName: "Escuela \(school)", blockId: "b-\(name)", blockName: "Piedra",
            lineId: "l-\(name)", lineName: name, grade: "6A", sectorName: nil, photoPath: nil, linePath: nil,
            startType: nil, lat: nil, lon: nil, orientation: nil, discipline: "BOULDER")
    }

    func testAgrupaPorEscuelaConservandoElOrden() {
        let hits = [hit("b", "1"), hit("a", "2"), hit("b", "3"), hit("a", "4")]
        let groups = AssistantPresenter.groupHits(hits)
        XCTAssertEqual(groups.map { $0.id }, ["b", "a"])          // el primero en llegar va primero
        XCTAssertEqual(groups[0].hits.count, 2)
        XCTAssertEqual(groups[0].total, 2)
    }

    func testRecortaEscuelasYFilasPeroCuentaElTotal() {
        var hits: [LineSearchHit] = []
        for s in 0..<8 { for i in 0..<6 { hits.append(hit("s\(s)", "v\(s)-\(i)")) } }
        let groups = AssistantPresenter.groupHits(hits, maxSchools: 5, perSchool: 4)
        XCTAssertEqual(groups.count, 5)
        XCTAssertEqual(groups[0].hits.count, 4)
        XCTAssertEqual(groups[0].total, 6)                         // el total real, no el recortado
    }

    func testAvisoDeMasResultadosSoloSiFaltan() {
        XCTAssertNil(AssistantPresenter.moreNote(total: 8, shown: 8))
        XCTAssertNotNil(AssistantPresenter.moreNote(total: 30, shown: 20))
        XCTAssertTrue(AssistantPresenter.moreNote(total: 30, shown: 20)!.contains("10"))
    }

    func testIntroDeResultadosCuentaLosHallazgos() {
        XCTAssertTrue(AssistantPresenter.hitsIntro(total: 12, needsLocationForDistance: false).contains("12"))
        XCTAssertFalse(AssistantPresenter.hitsIntro(total: 0, needsLocationForDistance: false).contains("0"))
    }

    func testAvisoDeAntiguedadSoloSiHaPasadoUnRato() {
        let ahora = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertNil(AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-600), now: ahora))
        let tres = AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-3 * 3600), now: ahora)
        XCTAssertNotNil(tres)
        XCTAssertTrue(tres!.contains("3"), tres!)
        // Entre media hora y una hora se dice "1 h", nunca "0 h".
        XCTAssertTrue(AssistantPresenter.savedNote(createdAt: ahora.addingTimeInterval(-2400), now: ahora)!.contains("1"))
    }

    func testSoloLosEstadosSinDatosTienenMensaje() {
        XCTAssertNil(AssistantPresenter.message(for: .ok))
        XCTAssertNil(AssistantPresenter.message(for: .needsInput))
        XCTAssertNotNil(AssistantPresenter.message(for: .busy))
        XCTAssertNotNil(AssistantPresenter.message(for: .userLimit))
        XCTAssertNotNil(AssistantPresenter.message(for: .unavailable))
    }
}
