import XCTest
import Shared
@testable import MeteoMontana

/// GradeFilter.swift es un ESPEJO MANUAL de GradeFilter.kt (shared). Si
/// divergen, el mismo filtro daría resultados distintos en Android y iOS.
/// Estos tests son los MISMOS casos que GradeFilterTest.kt (commonTest).
final class GradeFilterTests: XCTestCase {

    private func line(_ id: String, _ grade: String?, name: String? = nil) -> BlockLine {
        BlockLine(id: id, name: name ?? id, grade: grade, startType: nil, linePath: nil,
                  sortOrder: 0, photoPath: nil, faceOrder: 0, avgStars: nil,
                  myStars: nil, lineDescription: nil, variant: nil)
    }

    private func block(_ id: String, _ lines: [BlockLine]) -> Block {
        Block(id: id, schoolId: "s", type: "BLOCK", name: id, lat: 0, lon: 0,
              photoPath: nil, description: nil, createdByUid: "u", createdAt: "",
              lines: lines, sectorBlockId: nil, discipline: "BOULDER",
              geometry: "POINT", path: nil, direction: "LTR", faces: [])
    }

    func testGradeScoreOrdenaIgualQueGradeColor() {
        XCTAssertEqual(gradeScore("6A"), 600)
        XCTAssertEqual(gradeScore("6A+"), 601)
        XCTAssertEqual(gradeScore("7B+"), 711)
        XCTAssertNil(gradeScore("PROY"))
        XCTAssertNil(gradeScore(""))
        XCTAssertNil(gradeScore(nil))
    }

    func testAvailableGradesSoloListaLosQueExistenDeDificilAFacil() {
        let b = block("b", [line("l1", "6A"), line("l2", "7B+"), line("l3", "PROY"), line("l4", "6A")])
        XCTAssertEqual(availableGrades([b]), ["7B+", "6A"])
    }

    func testSoloLaPiedraConViaEnLaSeleccionQuedaMarcadaAgrupadaPorGrado() {
        let alunecer = block("alunecer", [line("l1", "7A", name: "Via A"), line("l2", "6A", name: "Via B")])
        let mordor = block("mordor", [line("l3", "6B", name: "Via C")])
        let result = filterBlocksByGrades([alunecer, mordor], selectedGrades: ["7A"])

        XCTAssertEqual(result.matchingBlockIds, ["alunecer"])
        XCTAssertEqual(result.matchingLineIds, ["l1"])
        XCTAssertEqual(result.totalLines, 3)
        XCTAssertEqual(result.matchingLines, 1)
        XCTAssertEqual(result.groups.map { $0.0 }, ["7A"])
        XCTAssertEqual(result.groups[0].1[0].lineName, "Via A")
    }

    func testVariosGradosAgrupanCadaUnoPorSeparadoDeDificilAFacil() {
        let b = block("b", [line("l1", "6A"), line("l2", "8A+"), line("l3", "6A")])
        let result = filterBlocksByGrades([b], selectedGrades: ["6A", "8A+"])
        XCTAssertEqual(result.groups.map { $0.0 }, ["8A+", "6A"])
        XCTAssertEqual(result.groups.last?.1.count, 2)
    }

    func testViaConGradoNoReconocibleNuncaEntraEnLaSeleccion() {
        let b = block("b", [line("l1", "PROY")])
        let result = filterBlocksByGrades([b], selectedGrades: [])
        XCTAssertTrue(result.matchingLineIds.isEmpty)
    }
}
