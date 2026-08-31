import XCTest
import SwiftUI
@testable import MeteoMontana

/// GradeColor.swift es un ESPEJO MANUAL de GradeColor.kt (Android). Si divergen,
/// la misma vía saldría de un color en Android y otro en iOS. Estos tests clavan
/// el mapeo grado→color con los MISMOS umbrales que el test Kotlin
/// (TopoRendererTest `gradeArgb coincide con palette de la PWA`).
final class GradeColorTests: XCTestCase {

    /// Extrae RGB (0..255) de un Color de SwiftUI para comparar con el hex exacto.
    private func rgb(_ c: Color) -> (Int, Int, Int) {
        let ui = UIColor(c)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
    }

    private func assertColor(_ grade: String?, _ hex: (Int, Int, Int),
                             file: StaticString = #file, line: UInt = #line) {
        let got = rgb(GradeColor.color(grade))
        XCTAssertEqual(got.0, hex.0, accuracy: 1, "R de \(grade ?? "nil")", file: file, line: line)
        XCTAssertEqual(got.1, hex.1, accuracy: 1, "G de \(grade ?? "nil")", file: file, line: line)
        XCTAssertEqual(got.2, hex.2, accuracy: 1, "B de \(grade ?? "nil")", file: file, line: line)
    }

    func testTiersMatchPalette() {
        assertColor("5c", (0xFF, 0xFF, 0xFF))   // ≤5c+ blanco
        assertColor("6a", (0x1F, 0xA8, 0x4E))   // 6a-6b+ verde
        assertColor("6b+", (0x1F, 0xA8, 0x4E))
        assertColor("6c", (0x1D, 0x6D, 0xD6))   // 6c-6c+ azul
        assertColor("7a", (0x8E, 0x3F, 0xBF))   // 7a-7a+ morado
        assertColor("7b", (0xD6, 0x28, 0x28))   // 7b-7c+ rojo
        assertColor("8a", (0x11, 0x11, 0x11))   // ≥8a negro
    }

    func testProjectAndInvalidAreDashedPink() {
        for g in [nil, "", "PROY", "PROYECTO", "?", "hola", "10c"] {
            let s = GradeColor.style(g)
            XCTAssertTrue(s.dashed, "\(g ?? "nil") debe ser proyecto (rosa discontinuo)")
            XCTAssertEqual(rgb(s.stroke).0, 0xFF, accuracy: 1)  // rosa 0xFF4FA3
        }
    }

    func testWhiteTierGetsDarkFlag() {
        // ≤5c+ blanco necesita halo/borde oscuro (dark=true) para verse sobre roca clara.
        XCTAssertTrue(GradeColor.style("4a").dark)
        XCTAssertFalse(GradeColor.style("6a").dark)
    }
}
