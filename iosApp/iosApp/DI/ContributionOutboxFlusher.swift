import Foundation
import UIKit
import Shared

/// Drena las contribuciones guardadas sin red (espejo de OutboxFlusher.kt):
/// - Las SIMPLES (parking/sector) las envía el contenedor Kotlin.
/// - Las de PIEDRA llevan fotos en rutas locales → aquí se suben con
///   StorageUploader (nativo), se monta el request y se envía.
/// Se llama al arrancar y al volver a primer plano (junto al flush del diario).
enum ContributionOutboxFlusher {

    static func flush() async {
        let c = AppDependencies.shared.container
        _ = try? await c.flushSimpleContributions()

        guard let rows = try? await c.pendingBoulderContributions() else { return }
        for row in rows {
            await flushBoulder(row, container: c)
        }
    }

    private static func flushBoulder(_ row: PendingContributionRow, container c: IosDependencyContainer) async {
        guard let data = row.payloadJson.data(using: .utf8),
              let q = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let lat = q["lat"] as? Double, let lon = q["lon"] as? Double,
              let facesArr = q["faces"] as? [[String: Any]] else { return }

        var bloques: [[String: Any]] = []
        var idx = 0
        var cover: String? = nil
        var localPaths: [String] = []

        for (i, f) in facesArr.enumerated() {
            var faceUrl: String? = nil
            if let p = f["localPhotoPath"] as? String {
                localPaths.append(p)
                guard let img = UIImage(contentsOfFile: p),
                      let url = try? await StorageUploader.uploadBoulderPhoto(img, schoolId: row.schoolId, index: i)
                else { return } // sin foto no se envía a medias; se reintentará
                faceUrl = url
            }
            if cover == nil { cover = faceUrl }
            for v in (f["vias"] as? [[String: Any]] ?? []) {
                let pts = (v["points"] as? [[Double]] ?? []).map { ["x": $0[0], "y": $0[1]] }
                let lineJson = (try? JSONSerialization.data(withJSONObject: pts))
                    .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
                bloques.append([
                    "idx": idx,
                    "name": v["name"] as? String ?? "",
                    "grade": v["grade"] ?? NSNull(),
                    "startType": v["startType"] ?? NSNull(),
                    "linePath": lineJson,
                    "targetLineId": v["targetLineId"] ?? NSNull(),
                    "photoUrl": faceUrl as Any? ?? NSNull()
                ])
                idx += 1
            }
        }

        let bloquesJson = (try? JSONSerialization.data(withJSONObject: bloques))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
        let req = ContributionRequest(
            type: "BOULDER",
            name: q["name"] as? String,
            lat: lat, lon: lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil,
            sectorBlockId: q["sectorBlockId"] as? String,
            photoUrl: cover,
            bloquesJson: bloquesJson, topoLinesJson: nil,
            discipline: q["discipline"] as? String,
            geometry: q["geometry"] as? String,
            path: q["pathJson"] as? String,
            direction: q["direction"] as? String)

        if (try? await c.submitContribution.invoke(schoolId: row.schoolId, req: req)) != nil {
            try? await c.deleteOutboxRow(id: row.id)
            for p in localPaths { try? FileManager.default.removeItem(atPath: p) }
        }
    }
}
