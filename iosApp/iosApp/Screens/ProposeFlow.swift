import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Flujo de proponer PIEDRA (BOULDER) en iOS — espejo de BoulderFormDialog +
// ContributionTopoDialog de Android. Nombre + sector opcional + lista de bloques
// (grado + tipo de inicio) + foto + editor de líneas (arrastrar sobre la foto).
// Envía POST /contributions con photoUrl + bloquesJson.

/// Selector de modalidad de la piedra: BLOQUE (BOULDER) o VÍA (ROUTE).
/// Reutilizado al proponer/crear piedra y al editarla (admin). Espejo del de Android.
struct DisciplineSelector: View {
    @Binding var selected: String
    var body: some View {
        HStack(spacing: 8) {
            ForEach([("BOULDER", "BLOQUE"), ("ROUTE", "VÍA")], id: \.0) { value, label in
                let on = selected == value
                Button { selected = value } label: {
                    Text(label).font(Cumbre.mono(12, .bold)).tracking(0.6)
                        .foregroundStyle(on ? .white : Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(on ? Cumbre.terra : Color.clear)
                        .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
    }
}

let BOULDER_GRADES = ["3", "4", "5", "5+",
                      "6a", "6a+", "6b", "6b+", "6c", "6c+",
                      "7a", "7a+", "7b", "7b+", "7c", "7c+",
                      "8a", "8a+", "8b", "8b+", "8c", "8c+",
                      "9a", "PROY"]

let START_TYPES = ["PIE", "SIT", "SEMI", "LANCE", "TRAV"]

/// Un bloque/vía de la piedra propuesta.
struct BoulderBlockForm: Identifiable {
    let id = UUID()
    var name = ""
    var grade: String? = nil
    var startType: String? = nil
    var line: [CGPoint] = []   // puntos normalizados 0..1
    /// id de la vía existente que representa esta fila (nil = vía nueva). Usado por
    /// el editor unificado para distinguir "corregir existente" de "añadir nueva".
    var existingLineId: String? = nil
    /// Foto (cara) a la que pertenece esta vía. Al corregir una piedra multi-foto,
    /// mantiene cada vía en SU cara (no las mezcla todas en la portada).
    var facePhoto: String? = nil
    /// Beta/detalle opcional de la vía (se muestra en su ficha).
    var descriptionText: String = ""
    /// Variante opcional ("directa", "extensión"...) — distingue vías homónimas.
    var variant: String = ""
}

/// Serializa los bloques al formato que espera el backend (espejo de
/// List<BoulderBloqueForm>.toBloquesJson de Android): linePath es un STRING JSON.
func buildBloquesJson(_ blocks: [BoulderBlockForm]) -> String {
    let arr: [[String: Any]] = blocks.enumerated().map { idx, b in
        let pts = b.line.map { ["x": $0.x, "y": $0.y] }
        let linePath = (try? JSONSerialization.data(withJSONObject: pts))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
        return ["idx": idx,
                "name": b.name,
                "grade": b.grade as Any? ?? NSNull(),
                "startType": b.startType as Any? ?? NSNull(),
                "linePath": linePath,
                // Si la fila representa una vía existente, el backend la CORRIGE
                // (en vez de añadir) — permite editar varias en una sola propuesta.
                "targetLineId": b.existingLineId as Any? ?? NSNull(),
                // Cara (foto) a la que pertenece → el backend la mantiene en su cara.
                "photoUrl": b.facePhoto as Any? ?? NSNull(),
                "description": b.descriptionText.isEmpty ? NSNull() : b.descriptionText,
                "variant": b.variant.isEmpty ? NSNull() : b.variant]
    }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}

/// Una CARA de la piedra al proponer: una foto y las vías dibujadas sobre ella.
struct BoulderFaceForm: Identifiable {
    let id = UUID()
    var photo: UIImage? = nil
    var blocks: [BoulderBlockForm] = [BoulderBlockForm()]
}

/// Serializa VARIAS caras a un único `bloquesJson` donde cada vía lleva el
/// `photoUrl` de su cara (el backend agrupa por foto en caras). `photoByFace` =
/// URL ya subida de cada cara (por id).
func buildFacesBloquesJson(_ faces: [BoulderFaceForm], photoByFace: [UUID: String?]) -> String {
    var arr: [[String: Any]] = []
    var idx = 0
    for face in faces {
        let facePhoto = photoByFace[face.id] ?? nil
        for b in face.blocks {
            let pts = b.line.map { ["x": $0.x, "y": $0.y] }
            let linePath = (try? JSONSerialization.data(withJSONObject: pts))
                .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
            arr.append(["idx": idx,
                        "name": b.name,
                        "grade": b.grade as Any? ?? NSNull(),
                        "startType": b.startType as Any? ?? NSNull(),
                        "linePath": linePath,
                        "targetLineId": b.existingLineId as Any? ?? NSNull(),
                        "photoUrl": facePhoto as Any? ?? NSNull(),
                        "description": b.descriptionText.isEmpty ? NSNull() : b.descriptionText,
                        "variant": b.variant.isEmpty ? NSNull() : b.variant])
            idx += 1
        }
    }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}

// ─── Etiquetas de tipo de inicio ───

let START_TYPE_LABELS: [(String, String)] = [
    ("PIE", "De pie"), ("SIT", "Sentado"), ("SEMI", "Semi-sit"),
    ("LANCE", "Lance"), ("TRAV", "Travesía")
]


/// Mapea el tipo de inicio del backend (STAND/JUMP) a la UI (PIE/LANCE).
func startTypeForUi(_ t: String?) -> String? {
    switch t?.uppercased() {
    case "STAND", "PIE": return "PIE"
    case "SIT": return "SIT"
    case "JUMP", "LANCE": return "LANCE"
    case "TRAV": return "TRAV"
    default: return nil
    }
}


/// Flujo "+ ASIGNAR SECTOR" — espejo de la contribución ASSIGN_SECTOR. Elige una
/// zona y propone que esta piedra pertenezca a ese sector.

// ─── Helpers de polilinea del muro ───

/// Parsea el `path` de un muro ("[[lat,lon],...]") a coordenadas.
func parseWallPath(_ json: String?) -> [CLLocationCoordinate2D] {
    guard let json, !json.isEmpty, let data = json.data(using: .utf8),
          let arr = try? JSONSerialization.jsonObject(with: data) as? [[Double]] else { return [] }
    return arr.compactMap { $0.count >= 2 ? CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) : nil }
}

/// Serializa la polilínea del muro a "[[lat,lon],...]" (formato de Block.path).
func buildPathJson(_ pts: [CLLocationCoordinate2D]) -> String {
    let arr = pts.map { [$0.latitude, $0.longitude] }
    return (try? JSONSerialization.data(withJSONObject: arr))
        .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
}
