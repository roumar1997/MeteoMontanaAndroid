import SwiftUI
import Shared

/// Una vía tal como la necesita el diario para apuntarla o borrarla.
struct JournalLineRef {
    let schoolId: String
    let schoolName: String?
    let sectorName: String?
    /// "BOULDER" / "ROUTE": la vía hereda la modalidad de su piedra.
    let discipline: String
    let blockId: String
    let lineId: String
    /// Nombre que se guarda en el diario ("Vía N" si la vía no tiene nombre).
    let viaName: String
    let grade: String?

    /// Clave por lineId (fix homónimas "La ola").
    var key: String { "\(schoolId)|#\(lineId)" }
    /// Clave LEGADO por nombre (entradas antiguas sin lineId).
    var legacyKey: String { "\(schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())" }
}

/// Apuntar y borrar vías del diario (hecha / proyecto) y publicar el ascenso en Comunidad. Funciona sin conexión:
/// lo que no se puede subir se encola. Lo usan la ficha de la piedra y la GUÍA de BLOQUES, así que las dos marcan
/// exactamente igual. Las marcas locales (`JournalDoneStore` / `JournalProjectStore`) se actualizan aquí.
@MainActor
enum JournalTicker {
    private static var container: IosDependencyContainer { AppDependencies.shared.container }

    private static func today() -> String {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    /// Apunta la vía como HECHA. Si era proyecto, primero quita el proyecto (pasa de Proyectos a Vías/Bloques).
    static func markDone(_ ref: JournalLineRef, wasProject: Bool, sessionDate: String? = nil,
                         aVista: Bool = false, alFlash: Bool = false) async {
        if wasProject { await removeProject(ref) }
        JournalDoneStore.shared.add(ref.key)
        try? await container.dequeueJournalDelete(key: ref.key)   // cancela borrado pendiente
        // No guardamos "Piedra: N" (el número se recicla/borra → quedaría obsoleto).
        let req = CreateJournalRequest(
            schoolId: ref.schoolId, schoolName: ref.schoolName, sector: ref.sectorName,
            blockName: ref.viaName, grade: ref.grade,
            notes: nil, date: sessionDate ?? today(),
            discipline: ref.discipline,
            lineId: ref.lineId,             // id estable → enganche del diario por muro
            status: "DONE",
            aVista: aVista, alFlash: alFlash)
        let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
        if !ok { try? await container.enqueueJournal(req: req) }   // sin red → cola
    }

    /// Quita la vía HECHA del diario (también la clave legado, por si el ✓ venía de una entrada antigua sin lineId).
    static func unmarkDone(_ ref: JournalLineRef) async {
        JournalDoneStore.shared.remove(ref.key)
        JournalDoneStore.shared.remove(ref.legacyKey)
        await removeEntry(ref, status: nil)
    }

    /// Apunta la vía como PROYECTO (la estás probando).
    static func markProject(_ ref: JournalLineRef) async {
        JournalProjectStore.shared.add(ref.key)
        try? await container.dequeueJournalDelete(key: ref.key)
        let req = CreateJournalRequest(
            schoolId: ref.schoolId, schoolName: ref.schoolName, sector: ref.sectorName,
            blockName: ref.viaName, grade: ref.grade,
            notes: nil, date: today(),
            discipline: ref.discipline, lineId: ref.lineId, status: "PROJECT",
            aVista: false, alFlash: false)
        let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
        if !ok { try? await container.enqueueJournal(req: req) }
    }

    /// Quita el PROYECTO (cola pendiente o ya subido).
    static func removeProject(_ ref: JournalLineRef) async {
        JournalProjectStore.shared.remove(ref.key)
        JournalProjectStore.shared.remove(ref.legacyKey)
        await removeEntry(ref, status: "PROJECT")
    }

    /// Borra la entrada: si solo estaba ENCOLADA, cancela la creación; si está (o estará) en el servidor, la borra ya
    /// o encola el borrado. Se localiza POR lineId; solo si no hay ninguna, por nombre entre las SIN lineId — nunca
    /// borra la entrada de una homónima distinta. `status` = solo entradas de ese estado (nil = cualquiera).
    private static func removeEntry(_ ref: JournalLineRef, status: String?) async {
        let hadPending = ((try? await container.dequeueJournal(key: ref.key))?.boolValue) ?? false
        guard !hadPending else { return }
        var deleted = false
        let journal = (try? await container.getMyJournal.invoke()) ?? []
        let ofStatus: (JournalSession) -> Bool = { status == nil || $0.status == status }
        let j = journal.first(where: { ofStatus($0) && $0.lineId == ref.lineId })
            ?? journal.first(where: {
                ofStatus($0) && $0.lineId == nil && $0.schoolId == ref.schoolId &&
                $0.blockName.caseInsensitiveCompare(ref.viaName) == .orderedSame
            })
        if let j {
            deleted = ((try? await container.deleteJournalEntry.invoke(id: j.id)) != nil)
        }
        if !deleted {
            // Payload del borrado = la clave de LA ENTRADA encontrada (id o legado) para que el filtrado offline case.
            let delKey: String
            if let j, j.lineId == nil, let sid = j.schoolId {
                delKey = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
            } else { delKey = ref.key }
            try? await container.enqueueJournalDelete(key: delKey)
        }
    }

    /// Publica el ascenso en Comunidad (no bloquea ni deshace el diario si falla). kind = PROJECT_DONE si estaba en
    /// proyectos; TICK en el resto. Foto de celebración opcional (si falla, el post queda sin foto y se avisa).
    static func publish(_ ref: JournalLineRef, wasProject: Bool, caption: String? = nil, photo: UIImage? = nil) {
        let kind = wasProject ? "PROJECT_DONE" : "TICK"
        let discipline = ref.discipline.uppercased() == "ROUTE" ? "ROUTE" : "BOULDER"
        let lineId: String? = ref.lineId.isEmpty ? nil : ref.lineId
        Task {
            guard let postId = await reporting(L("No se pudo publicar el ascenso"), {
                try await container.publishFeedPost.invoke(
                    blockId: ref.blockId, lineId: lineId, kind: kind, discipline: discipline,
                    caption: caption)
            }) else { return }   // `reporting` enseña el aviso de «en pausa» si el servidor devuelve 503 FEATURE_DISABLED
            guard let photo else { return }
            guard let data = feedPhotoJPEGData(photo) else {
                await showFeedPhotoUploadFailedAlert()
                return
            }
            do {
                _ = try await container.uploadFeedPhoto.invoke(
                    postId: postId.int64Value, bytes: data.toKotlinByteArray(),
                    contentType: "image/jpeg")
            } catch {
                await showFeedPhotoUploadFailedAlert()
            }
        }
    }
}
