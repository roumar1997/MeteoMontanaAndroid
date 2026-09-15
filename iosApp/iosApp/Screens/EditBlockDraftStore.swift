import Foundation
import UIKit

/// Ediciones de una piedra YA EXISTENTE a medias: lo que llevabas cambiado
/// cuando cerraste el editor sin enviar. Espejo del patrón de
/// `BoulderDraftStore` (piedras NUEVAS), pero clave por `blockId` en vez de
/// `schoolId` — aquí puede haber varias piedras con un cambio a medias a la
/// vez, cada una la suya.
///
/// Petición de Rodrigo (2026-08-21): "desde editar una piedra también te
/// deja darle a guardar y terminar luego?" — ya existía al CREAR una piedra
/// nueva, faltaba al EDITAR una existente.
enum EditBlockDraftStore {

    struct Draft {
        var blockId: String
        /// Vías de cada cara, en el mismo formato que usa el editor.
        var faceBlocks: [[BoulderBlockForm]]
        /// Índice de cara -> foto local elegida (aún no enviada).
        var facePicked: [Int: UIImage]
        var savedAt: Double
    }

    private static var carpeta: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("borradores-edicion-piedra", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    private static func seguro(_ s: String) -> String {
        String(s.map { $0.isLetter || $0.isNumber || $0 == "-" ? $0 : "_" })
    }

    private static func ficheroJson(_ blockId: String) -> URL {
        carpeta.appendingPathComponent(seguro(blockId) + ".json")
    }

    static func save(_ d: Draft) {
        var fotos: [String: String] = [:]
        for (idx, img) in d.facePicked {
            if let data = img.jpegData(compressionQuality: 0.85) {
                let nombre = seguro(d.blockId) + "-cara\(idx).jpg"
                try? data.write(to: carpeta.appendingPathComponent(nombre))
                fotos[String(idx)] = nombre
            }
        }
        let carasJson = d.faceBlocks.map { vias in
            vias.map { b -> [String: Any] in
                var v: [String: Any] = ["name": b.name]
                if let g = b.grade { v["grade"] = g }
                if let s = b.startType { v["startType"] = s }
                if !b.descriptionText.isEmpty { v["descriptionText"] = b.descriptionText }
                if !b.variant.isEmpty { v["variant"] = b.variant }
                v["line"] = b.line.map { [Double($0.x), Double($0.y)] }
                if let t = b.existingLineId { v["targetLineId"] = t }
                return v
            }
        }
        let raiz: [String: Any] = [
            "blockId": d.blockId, "faces": carasJson, "fotos": fotos, "savedAt": d.savedAt
        ]
        if let data = try? JSONSerialization.data(withJSONObject: raiz) {
            try? data.write(to: ficheroJson(d.blockId))
        }
    }

    /// nil ante cualquier problema: un borrador roto no puede bloquear editar.
    static func load(blockId: String) -> Draft? {
        guard let data = try? Data(contentsOf: ficheroJson(blockId)),
              let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return nil }

        let carasJson = o["faces"] as? [[[String: Any]]] ?? []
        let faceBlocks: [[BoulderBlockForm]] = carasJson.map { vias in
            vias.map { b in
                var v = BoulderBlockForm()
                v.name = b["name"] as? String ?? ""
                v.grade = b["grade"] as? String
                v.startType = b["startType"] as? String
                v.descriptionText = b["descriptionText"] as? String ?? ""
                v.variant = b["variant"] as? String ?? ""
                v.line = (b["line"] as? [[Double]] ?? []).map {
                    CGPoint(x: $0.first ?? 0, y: $0.count > 1 ? $0[1] : 0)
                }
                v.existingLineId = b["targetLineId"] as? String
                return v
            }
        }
        var facePicked: [Int: UIImage] = [:]
        for (idxStr, nombre) in (o["fotos"] as? [String: String] ?? [:]) {
            if let idx = Int(idxStr),
               let d = try? Data(contentsOf: carpeta.appendingPathComponent(nombre)) {
                facePicked[idx] = UIImage(data: d)
            }
        }
        return Draft(blockId: blockId, faceBlocks: faceBlocks, facePicked: facePicked,
                     savedAt: o["savedAt"] as? Double ?? 0)
    }

    static func clear(blockId: String) {
        try? FileManager.default.removeItem(at: ficheroJson(blockId))
        let prefijo = seguro(blockId) + "-cara"
        let todos = (try? FileManager.default.contentsOfDirectory(atPath: carpeta.path)) ?? []
        for f in todos where f.hasPrefix(prefijo) {
            try? FileManager.default.removeItem(at: carpeta.appendingPathComponent(f))
        }
    }
}
