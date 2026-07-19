import XCTest
import SwiftUI
@testable import MeteoMontana

/// TopoSharedSegments.swift (imán + tramos compartidos + suavizado + abanico) es
/// un ESPEJO MANUAL de TopoRenderer.kt. Estos tests reproducen los MISMOS casos
/// que SharedSegmentsTest.kt de Android: si un espejo diverge del Kotlin, la
/// paridad del editor de topos se rompe y aquí salta en rojo.
final class TopoSharedTests: XCTestCase {

    // Vía existente: diagonal con 4 vértices (idéntico al test Kotlin).
    private let existing: [CGPoint] = [
        CGPoint(x: 0.10, y: 0.90), CGPoint(x: 0.20, y: 0.70),
        CGPoint(x: 0.30, y: 0.50), CGPoint(x: 0.40, y: 0.30)]

    // ── Imán ──────────────────────────────────────────────────────────────

    func testMagnetizeSnapsNearPointsToExactVertices() {
        let drawn = [CGPoint(x: 0.101, y: 0.899), CGPoint(x: 0.201, y: 0.701),
                     CGPoint(x: 0.80, y: 0.20)]
        let out = TopoShared.magnetizeStroke(drawn, others: [existing])
        XCTAssertEqual(out[0], existing[0])
        XCTAssertEqual(out[1], existing[1])
        XCTAssertEqual(out.last, CGPoint(x: 0.80, y: 0.20))  // lejos → no imanta
    }

    func testMagnetizeInsertsSkippedVertices() {
        // El dedo solo toca el vértice 0 y el 3 → deben insertarse el 1 y el 2.
        let drawn = [CGPoint(x: 0.101, y: 0.899), CGPoint(x: 0.401, y: 0.301),
                     CGPoint(x: 0.80, y: 0.20)]
        let out = TopoShared.magnetizeStroke(drawn, others: [existing])
        XCTAssertEqual(out, [existing[0], existing[1], existing[2], existing[3],
                             CGPoint(x: 0.80, y: 0.20)])
    }

    func testMagnetizeLeavesFarStrokesUntouched() {
        let drawn = [CGPoint(x: 0.80, y: 0.10), CGPoint(x: 0.90, y: 0.20)]
        XCTAssertEqual(TopoShared.magnetizeStroke(drawn, others: [existing]), drawn)
    }

    // ── Tramos compartidos ─────────────────────────────────────────────────

    func testIdenticalSegmentsDetectedAsShared() {
        let a = [CGPoint(x: 0.5, y: 0.9), CGPoint(x: 0.5, y: 0.5), CGPoint(x: 0.3, y: 0.2)]
        let b = [CGPoint(x: 0.5, y: 0.9), CGPoint(x: 0.5, y: 0.5), CGPoint(x: 0.7, y: 0.2)]
        let shared = TopoShared.sharedSegmentLines([a, b])
        // El tramo (0.5,0.9)-(0.5,0.5) lo comparten la 0 y la 1; el resto no.
        XCTAssertEqual(shared.count, 1)
        XCTAssertEqual(shared.values.first, [0, 1])
    }

    func testSingleLineSharesNothing() {
        XCTAssertTrue(TopoShared.sharedSegmentLines([existing]).isEmpty)
    }

    // ── Suavizado (Douglas-Peucker) ────────────────────────────────────────

    func testSimplifyKeepsCornersDropsTremor() {
        // Una esquina en (0.5,0.5) con un punto de temblor casi colineal.
        let pts = [
            CGPoint(x: 0.0, y: 0.0),
            CGPoint(x: 0.25, y: 0.001),  // temblor: casi sobre la recta 0→0.5
            CGPoint(x: 0.5, y: 0.0),
            CGPoint(x: 0.5, y: 0.5)]     // esquina real
        let out = TopoShared.simplifyStroke(pts)
        XCTAssertTrue(out.contains(CGPoint(x: 0.5, y: 0.5)), "la esquina se conserva")
        XCTAssertFalse(out.contains(CGPoint(x: 0.25, y: 0.001)), "el temblor se quita")
        XCTAssertEqual(out.first, pts.first)
        XCTAssertEqual(out.last, pts.last)
    }

    // ── Abanico de badges ──────────────────────────────────────────────────

    func testFanOffsetsSeparatesCoincidentAnchorsCentered() {
        let p = CGPoint(x: 0.5, y: 0.5)
        let offsets = TopoShared.fanOffsets([p, p, CGPoint(x: 0.9, y: 0.9)], spacing: 10)
        // Los dos que coinciden se reparten centrados: -5 y +5; el tercero 0.
        XCTAssertEqual(offsets[0], -5, accuracy: 0.001)
        XCTAssertEqual(offsets[1], 5, accuracy: 0.001)
        XCTAssertEqual(offsets[2], 0, accuracy: 0.001)
    }
}
