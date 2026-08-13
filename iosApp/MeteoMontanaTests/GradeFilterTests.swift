import XCTest
import Shared
@testable import MeteoMontana

/// GradeFilter.swift es un ESPEJO MANUAL de GradeFilter.kt (shared). Si
/// divergen, el mismo filtro daría resultados distintos en Android y iOS.
/// Estos tests son los MISMOS casos que GradeFilterTest.kt (commonTest).
final class GradeFilterTests: XCTestCase {

    private func line(_ id: String, _ grade: String?) -> BlockLine {
        BlockLine(id: id, name: id, grade: grade, startType: nil, linePath: nil,
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

    func testSoloLaPiedraConViaEnRangoQuedaMarcada() {
        let alunecer = block("alunecer", [line("l1", "7A"), line("l2", "6A")])
        let mordor = block("mordor", [line("l3", "6B")])
        let result = filterBlocksByGrade([alunecer, mordor], minGrade: "7A", maxGrade: "7B")

        XCTAssertEqual(result.matchingBlockIds, ["alunecer"])
        XCTAssertEqual(result.matchingLineIds, ["l1"])
        XCTAssertEqual(result.totalLines, 3)
        XCTAssertEqual(result.matchingLines, 1)
    }

    func testSinMinimoOSinMaximoElRangoQuedaAbierto() {
        let b = block("b", [line("l1", "3A"), line("l2", "8A+")])
        XCTAssertEqual(filterBlocksByGrade([b], minGrade: "7A", maxGrade: nil).matchingLineIds, ["l2"])
        XCTAssertEqual(filterBlocksByGrade([b], minGrade: nil, maxGrade: "4A").matchingLineIds, ["l1"])
    }

    func testViaConGradoNoReconocibleNuncaEntraEnRango() {
        let b = block("b", [line("l1", "PROY")])
        let result = filterBlocksByGrade([b], minGrade: nil, maxGrade: nil)
        XCTAssertTrue(result.matchingLineIds.isEmpty)
    }
}
