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

        if let rows = try? await c.pendingBoulderContributions() {
            for row in rows { await flushBoulder(row, container: c) }
        }

        // Ediciones de piedras que YA existen (añadir vías, foto nueva de una
        // cara). Hasta 2026-08-17 iOS no las encolaba: sin cobertura solo decía
        // "no se pudo enviar" y había que repetirlo entero con red.
        if let edits = try? await c.pendingBlockEditContributions() {
            for row in edits { await flushBlockEdit(row, container: c) }
        }
    }

    /// Edición de una piedra existente: sube las fotos NUEVAS (las caras que no
    /// se tocaron conservan la suya) y manda el estado COMPLETO de las vías,
    /// para que el backend reconcilie por lineId y los enganches del diario
    /// sobrevivan.
    private static func flushBlockEdit(_ row: PendingContributionRow, container c: IosDependencyContainer) async {
        guard let data = row.payloadJson.data(using: .utf8),
              let q = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let targetBlockId = q["targetBlockId"] as? String,
              let lat = q["lat"] as? Double, let lon = q["lon"] as? Double,
              let facesArr = q["faces"] as? [[String: Any]] else { return }

        var bloques: [[String: Any]] = []
        var localPaths: [String] = []

        for face in facesArr {
            var fotoDeLaCara = face["existingPhotoPath"] as? String
            if let local = face["localPhotoPath"] as? String {
                // Si la foto encolada ya no está, se ABANDONA y la fila se
                // conserva para reintentar: mandar la cara sin foto colapsaría
                // sus vías en la portada, mezclándolas con las de otra cara.
                guard FileManager.default.fileExists(atPath: local),
                      let img = UIImage(contentsOfFile: local),
                      let url = try? await StorageUploader.uploadBoulderPhoto(img, schoolId: row.schoolId)
                else { return }
                fotoDeLaCara = url
                localPaths.append(local)
            }
            for via in (face["vias"] as? [[String: Any]] ?? []) {
                // DOS fallos aquí (Rodrigo, build 145 en producción — piedra 6
                // de Santa Gadea acabó con todas las vías en la foto de la
                // PRIMERA cara y desapareció una foto entera):
                //
                // 1) La clave iba como "facePhoto"; el backend
                //    (ContributionLineParser.facePhotoOf) lee "photoUrl", y si
                //    no la encuentra usa la portada del bloque COMO FOTO DE
                //    TODAS las vías, sin avisar de nada. `buildBloquesJson` (el
                //    camino online, que nunca falló) usa "photoUrl" — se copió
                //    mal al escribir el flusher offline.
                // 2) "points" es [[x,y],...] (pares sueltos); el backend
                //    (`node.path("linePath").asText`) espera un STRING JSON de
                //    objetos {"x":..,"y":..}, no un array crudo — con un array
                //    devolvía texto sin parsear y la vía se guardaba sin trazo.
                let puntos = (via["points"] as? [[Double]] ?? []).compactMap { par -> [String: Double]? in
                    guard par.count == 2 else { return nil }
                    return ["x": par[0], "y": par[1]]
                }
                let linePath = (try? JSONSerialization.data(withJSONObject: puntos))
                    .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
                bloques.append([
                    "name": via["name"] as? String ?? "",
                    "grade": via["grade"] as? String ?? NSNull(),
                    "startType": via["startType"] as? String ?? NSNull(),
                    "linePath": linePath,
                    "photoUrl": fotoDeLaCara ?? NSNull(),
                    "targetLineId": via["targetLineId"] as? String ?? NSNull()
                ])
            }
        }

        let bloquesJson = (try? JSONSerialization.data(withJSONObject: bloques))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
        let req = ContributionRequest(
            type: "BOULDER",
            name: nil,
            lat: lat, lon: lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: targetBlockId, targetLineId: nil,
            sectorBlockId: nil,
            photoUrl: nil,
            bloquesJson: bloquesJson, topoLinesJson: nil,
            discipline: nil,
            geometry: q["geometry"] as? String,
            path: q["pathJson"] as? String,
            direction: q["direction"] as? String,
            orientationsJson: nil)

        if (try? await c.submitContribution.invoke(schoolId: row.schoolId, req: req)) != nil {
            try? await c.deleteOutboxRow(id: row.id)
            for p in localPaths { try? FileManager.default.removeItem(atPath: p) }
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
            direction: q["direction"] as? String,
            orientationsJson: q["orientationsJson"] as? String)

        if (try? await c.submitContribution.invoke(schoolId: row.schoolId, req: req)) != nil {
            try? await c.deleteOutboxRow(id: row.id)
            for p in localPaths { try? FileManager.default.removeItem(atPath: p) }
        }
    }
}
