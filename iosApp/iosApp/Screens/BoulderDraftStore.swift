import Foundation
import UIKit

/// Piedras a medias: lo que llevabas escrito cuando cerraste el formulario.
/// Espejo de `BoulderDraftStore.kt` de Android.
///
/// Proponer una piedra es largo —nombre, modalidad, orientación, una foto por
/// cara y una línea dibujada por vía—, y hasta ahora cerrar por error, recibir
/// una llamada o quedarte sin batería significaba empezar de cero. Eso hace que
/// la gente no lo intente en el sitio, que es justo cuando tiene la roca
/// delante.
///
/// Se guarda **solo en este móvil**, nunca en el servidor: es tuyo, está a
/// medias y no tiene por qué verlo un admin hasta que lo envíes.
///
/// DIFERENCIA CON ANDROID: allí la foto es una referencia a la galería y basta
/// con guardar su dirección. Aquí la foto vive en memoria, así que hay que
/// escribirla a disco (JPEG al 85% en la carpeta de la app) o se pierde al
/// cerrar. A cambio, el borrador de iOS sobrevive aunque borres la foto del
/// carrete.
enum BoulderDraftStore {

    struct Draft {
        var schoolId: String
        var lat: Double
        var lon: Double
        var name: String
        var discipline: String
        var geometry: String
        var direction: String
        var sectorId: String?
        var orientation: String?
        var path: [[Double]]
        var faces: [BoulderFaceForm]
        var savedAt: Double
    }

    private static var carpeta: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("borradores-piedra", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    private static func ficheroJson(_ schoolId: String) -> URL {
        carpeta.appendingPathComponent(seguro(schoolId) + ".json")
    }

    /// El id de escuela va en el nombre del fichero: fuera todo lo que no sea
    /// letra, número o guion, no vaya a colarse una barra y escribir donde no toca.
    private static func seguro(_ s: String) -> String {
        String(s.map { $0.isLetter || $0.isNumber || $0 == "-" ? $0 : "_" })
    }

    // MARK: - Guardar / cargar / borrar

    static func save(_ d: Draft) {
        var caras: [[String: Any]] = []
        for (i, cara) in d.faces.enumerated() {
            var dicc: [String: Any] = [:]
            if let img = cara.photo, let data = img.jpegData(compressionQuality: 0.85) {
                let nombre = seguro(d.schoolId) + "-cara\(i).jpg"
                try? data.write(to: carpeta.appendingPathComponent(nombre))
                dicc["photoFile"] = nombre
            }
            if let o = cara.orientation { dicc["orientation"] = o }
            dicc["blocks"] = cara.blocks.map { b -> [String: Any] in
                var v: [String: Any] = ["name": b.name]
                if let g = b.grade { v["grade"] = g }
                if let s = b.startType { v["startType"] = s }
                if !b.descriptionText.isEmpty { v["descriptionText"] = b.descriptionText }
                if !b.variant.isEmpty { v["variant"] = b.variant }
                v["line"] = b.line.map { [Double($0.x), Double($0.y)] }
                return v
            }
            caras.append(dicc)
        }
        let raiz: [String: Any] = [
            "schoolId": d.schoolId, "lat": d.lat, "lon": d.lon,
            "name": d.name, "discipline": d.discipline,
            "geometry": d.geometry, "direction": d.direction,
            "sectorId": d.sectorId as Any, "orientation": d.orientation as Any,
            "path": d.path, "faces": caras, "savedAt": d.savedAt
        ]
        if let data = try? JSONSerialization.data(withJSONObject: raiz) {
            try? data.write(to: ficheroJson(d.schoolId))
        }
    }

    /// Devuelve nil ante CUALQUIER problema: un borrador viejo con un campo que
    /// ya no existe no puede impedir proponer una piedra nueva.
    static func load(schoolId: String) -> Draft? {
        guard let data = try? Data(contentsOf: ficheroJson(schoolId)),
              let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return nil }

        var caras: [BoulderFaceForm] = []
        for c in (o["faces"] as? [[String: Any]] ?? []) {
            var cara = BoulderFaceForm()
            if let f = c["photoFile"] as? String,
               let d = try? Data(contentsOf: carpeta.appendingPathComponent(f)) {
                cara.photo = UIImage(data: d)
            }
            cara.orientation = c["orientation"] as? String
            cara.blocks = (c["blocks"] as? [[String: Any]] ?? []).map { b in
                var v = BoulderBlockForm()
                v.name = b["name"] as? String ?? ""
                v.grade = b["grade"] as? String
                v.startType = b["startType"] as? String
                v.descriptionText = b["descriptionText"] as? String ?? ""
                v.variant = b["variant"] as? String ?? ""
                v.line = (b["line"] as? [[Double]] ?? []).map {
                    CGPoint(x: $0.first ?? 0, y: $0.count > 1 ? $0[1] : 0)
                }
                return v
            }
            if cara.blocks.isEmpty { cara.blocks = [BoulderBlockForm()] }
            caras.append(cara)
        }
        if caras.isEmpty { caras = [BoulderFaceForm()] }

        return Draft(
            schoolId: o["schoolId"] as? String ?? schoolId,
            lat: o["lat"] as? Double ?? 0, lon: o["lon"] as? Double ?? 0,
            name: o["name"] as? String ?? "",
            discipline: o["discipline"] as? String ?? "BOULDER",
            geometry: o["geometry"] as? String ?? "POINT",
            direction: o["direction"] as? String ?? "LTR",
            sectorId: o["sectorId"] as? String,
            orientation: o["orientation"] as? String,
            path: o["path"] as? [[Double]] ?? [],
            faces: caras,
            savedAt: o["savedAt"] as? Double ?? 0
        )
    }

    static func clear(schoolId: String) {
        try? FileManager.default.removeItem(at: ficheroJson(schoolId))
        // Las fotos de esa escuela se van con él.
        let prefijo = seguro(schoolId) + "-cara"
        let todos = (try? FileManager.default.contentsOfDirectory(atPath: carpeta.path)) ?? []
        for f in todos where f.hasPrefix(prefijo) {
            try? FileManager.default.removeItem(at: carpeta.appendingPathComponent(f))
        }
    }

    /// ¿Hay algo que merezca la pena guardar? Un formulario en blanco no.
    static func tieneContenido(_ d: Draft) -> Bool {
        if !d.name.trimmingCharacters(in: .whitespaces).isEmpty { return true }
        return d.faces.contains { cara in
            cara.photo != nil || cara.blocks.contains {
                !$0.name.trimmingCharacters(in: .whitespaces).isEmpty
                    || $0.grade != nil || !$0.line.isEmpty
            }
        }
    }
}
