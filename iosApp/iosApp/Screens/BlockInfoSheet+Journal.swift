import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Ficha de piedra: cargar lo hecho y apuntar o quitar vías del diario (hecha y proyecto).

extension BlockInfoSheet {
    /// Al abrir, marca como HECHAS (✓) las vías que ya están en tu diario, para
    /// que el tic quede persistente entre sesiones. Match por escuela + nombre de
    /// la vía (mismo nombre que se guardó al dar el tic).
    func loadDone() async {
        let container = AppDependencies.shared.container
        // Claves pendientes en la cola offline, separadas por estado (la cola
        // JOURNAL guarda tanto "hechas" como "proyecto" bajo el mismo tipo).
        let pendingDoneKeys: Set<String> = (try? await container.pendingJournalKeysByStatus(status: "DONE")) ?? []
        let pendingProjectKeys: Set<String> = (try? await container.pendingJournalKeysByStatus(status: "PROJECT")) ?? []
        let pendingDeletes: Set<String> = (try? await container.pendingJournalDeleteKeys()) ?? []
        // Con red: sincroniza el registro local con la verdad del servidor
        // (descontando las que tienen borrado pendiente). Separamos por status:
        // solo DONE cuenta como "hecha"; solo PROJECT cuenta como "proyecto".
        if let journal = try? await container.getMyJournal.invoke() {
            var serverDoneKeys = Set<String>()
            var serverProjectKeys = Set<String>()
            for j in journal {
                guard let sid = j.schoolId else { continue }
                // Clave por lineId (aguanta homónimas — fix "La ola"); por
                // nombre solo para entradas antiguas sin lineId. Mismo formato
                // que journalViaKey de Android y los helpers del container.
                let key: String
                if let lid = j.lineId, !lid.isEmpty {
                    key = "\(sid)|#\(lid)"
                } else {
                    key = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                }
                if j.status == "PROJECT" { serverProjectKeys.insert(key) } else { serverDoneKeys.insert(key) }
            }
            JournalDoneStore.shared.sync(server: serverDoneKeys.subtracting(pendingDeletes), pending: pendingDoneKeys)
            JournalProjectStore.shared.sync(server: serverProjectKeys.subtracting(pendingDeletes), pending: pendingProjectKeys)
        }
        // El registro local (UserDefaults) funciona también SIN conexión → evita
        // duplicar al volver a entrar offline en la misma piedra.
        let storeKeys = JournalDoneStore.shared.all
        let projectKeys = JournalProjectStore.shared.all
        var done = Set<String>()
        var projects = Set<String>()
        for (idx, l) in block.lines.enumerated() {
            let viaName = l.name.isEmpty ? L("Vía %@", idx + 1) : l.name
            // Clave por id + clave por nombre (LEGADO: entradas sin lineId).
            let idKey = "\(block.schoolId)|#\(l.id)"
            let nameKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"
            let isDone = (storeKeys.contains(idKey) || storeKeys.contains(nameKey)
                          || pendingDoneKeys.contains(idKey) || pendingDoneKeys.contains(nameKey))
                && !pendingDeletes.contains(idKey) && !pendingDeletes.contains(nameKey)
            if isDone { done.insert(l.id) }
            let isProject = (projectKeys.contains(idKey) || projectKeys.contains(nameKey)
                             || pendingProjectKeys.contains(idKey) || pendingProjectKeys.contains(nameKey))
                && !pendingDeletes.contains(idKey) && !pendingDeletes.contains(nameKey)
            if isProject && !done.contains(l.id) { projects.insert(l.id) }
        }
        tickedLines = done
        projectLines = projects
    }

    var typeLabel: String {
        switch block.type.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return L("ZONA")
        default: return L("PIEDRA")
        }
    }

    /// Toque en el tic: desmarcar va directo; marcar abre la hoja de ascenso
    /// (fecha, estilo, grado y el interruptor de Comunidad). Cerrarla = no marcar nada.
    func onTickTapped(_ line: BlockLine, index: Int) {
        if tickedLines.contains(line.id) {
            Task { await toggle(line, index: index) }
            return
        }
        // wasProject ANTES del toggle (marcar la quita de proyectos).
        let wasProject = projectLines.contains(line.id)
        pendingTick = PendingFeedTick(line: line, index: index, wasProject: wasProject)
    }

    /// Etiqueta "vía · grado" de la hoja de publicar.
    func feedTickLabel(_ line: BlockLine, index: Int) -> String {
        var label = line.name.isEmpty ? L("Vía %@", index + 1) : line.name
        if let g = line.grade, !g.isEmpty { label += " · \(g)" }
        return label
    }

    /// La vía tal como la necesita el diario ("Vía N" si no tiene nombre).
    func journalRef(_ line: BlockLine, index: Int) -> JournalLineRef {
        JournalLineRef(
            schoolId: block.schoolId, schoolName: schoolName, sectorName: sectorName,
            discipline: block.discipline, blockId: block.id, lineId: line.id,
            viaName: line.name.isEmpty ? L("Vía %@", index + 1) : line.name, grade: line.grade)
    }

    /// Publica el tick en el feed Comunidad (fire-and-forget). Lógica en `JournalTicker` (compartida con la guía).
    func publishTickToFeed(_ line: BlockLine, wasProject: Bool,
                                   caption: String? = nil, photo: UIImage? = nil) {
        JournalTicker.publish(journalRef(line, index: 0), wasProject: wasProject, caption: caption, photo: photo)
    }

    /// Marca/DESMARCA la vía en tu diario (toggle). La lógica (cola sin red, legado por nombre…) vive en
    /// `JournalTicker`; aquí solo se actualiza el estado de la hoja. Espejo del toggle de Android.
    func toggle(_ line: BlockLine, index: Int, sessionDate: String? = nil,
                        aVista: Bool = false, alFlash: Bool = false) async {
        tickingLine = line.id
        let ref = journalRef(line, index: index)
        if tickedLines.contains(line.id) {
            tickedLines.remove(line.id)
            await JournalTicker.unmarkDone(ref)
        } else {
            let wasProject = projectLines.contains(line.id)
            projectLines.remove(line.id)
            tickedLines.insert(line.id)
            await JournalTicker.markDone(ref, wasProject: wasProject, sessionDate: sessionDate,
                                         aVista: aVista, alFlash: alFlash)
        }
        tickingLine = nil
    }

    /// Marca/DESMARCA la vía como PROYECTO. No hace nada si ya está HECHA (la UI ya oculta el botón).
    func toggleProject(_ line: BlockLine, index: Int) async {
        guard !tickedLines.contains(line.id) else { return }
        togglingProject = line.id
        let ref = journalRef(line, index: index)
        if projectLines.contains(line.id) {
            projectLines.remove(line.id)
            await JournalTicker.removeProject(ref)
        } else {
            projectLines.insert(line.id)
            await JournalTicker.markProject(ref)
        }
        togglingProject = nil
    }
}
