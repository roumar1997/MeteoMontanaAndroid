import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// FICHA DE PIEDRA — espejo de BlockDetailDialog.kt. Incluye el flujo de
// diario/ticks/proyectos con claves duales (fix homónimas «La ola») y las
// colas offline: bloque MUY sensible, movido intacto.

struct BlockInfoSheet: View {
    let block: Block
    var sectors: [Block] = []
    var schoolName: String? = nil
    /// Vía objetivo (deep-link del diario): su cara/foto se muestra la primera.
    var highlightVia: String? = nil
    var onEditLines: (() -> Void)? = nil
    var onAssignSector: (() -> Void)? = nil
    /// Admin: borrar este bloque (piedra/zona/parking) directamente.
    var onDelete: (() -> Void)? = nil
    /// Valorar una vía. nil = no mostrar estrellas.
    var onRateLine: ((String, Int) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    /// Caras de la piedra, SIEMPRE en el orden en que se introdujeron (FOTO 1,
    /// FOTO 2…). El deep-link del diario NO reordena: solo hace scroll a la cara
    /// que contiene la vía pulsada (ver `scrollFaceIndex`).
    private var orderedFaces: [BlockFace] { block.facesOrDerived() }

    /// Índice de la cara que contiene la vía del deep-link (para hacer scroll a
    /// ella al abrir). Nil si no hay deep-link o no se encuentra.
    private var scrollFaceIndex: Int? {
        guard let via = highlightVia?.trimmingCharacters(in: .whitespaces), !via.isEmpty else { return nil }
        return orderedFaces.firstIndex { f in
            f.lines.contains { $0.name.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(via) == .orderedSame }
        }
    }
    @State private var tickedLines: Set<String> = []   // vías marcadas como hechas en esta sesión
    @State private var tickingLine: String?            // vía guardándose ahora
    @State private var projectLines: Set<String> = []  // vías marcadas como PROYECTO
    @State private var togglingProject: String?         // vía de proyecto guardándose ahora
    @State private var showDeleteConfirm = false
    // Tick pendiente de confirmar: hoja "Publicar en el feed" (desmarcar sigue
    // siendo toggle directo, sin hoja). Espejo del flujo de SchoolMap.kt.
    @State private var pendingTick: PendingFeedTick? = nil
    // Comentarios de la piedra/vías (un fetch por piedra; los hilos filtran).
    @StateObject private var commentsStore = LineCommentsStore()
    // Desplegable OPCIONES: agrupa editar vías / sector / eliminar.
    @State private var optionsOpen = false

    private var sectorName: String? {
        guard let sid = block.sectorBlockId else { return nil }
        return sectors.first(where: { $0.id == sid })?.name
    }
    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    Text(block.name.isEmpty ? typeLabel : block.name)
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)

                    // Sector al que pertenece (si lo tiene).
                    if let sn = sectorName, !sn.isEmpty {
                        Text("SECTOR · \(sn.uppercased())").font(Cumbre.mono(10, .bold))
                            .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Cumbre.ok)
                    }

                    // CARAS: una piedra grande se enseña con varias fotos. Cada cara
                    // es una foto con sus vías dibujadas y, debajo, sus vías
                    // marcables. Una piedra de una sola foto tiene una única cara.
                    if block.type.uppercased() == "BLOCK" {
                        if !block.lines.isEmpty {
                            FirstTimeHint(
                                hintKey: "via_tick",
                                text: "Toca el círculo de una vía para apuntarla como hecha en tu diario."
                            )
                            FirstTimeHint(
                                hintKey: "via_project",
                                text: "Toca la P de una vía para marcarla como PROYECTO (la estás probando, aún no te ha salido)."
                            )
                        }
                        ForEach(Array(orderedFaces.enumerated()), id: \.offset) { faceIdx, face in
                          VStack(alignment: .leading, spacing: 12) {
                            if let photo = face.photoPath, !photo.isEmpty {
                                if orderedFaces.count > 1 {
                                    Text("FOTO \(faceIdx + 1)").eyebrow().padding(.top, 4)
                                }
                                TopoPhotoView(photoUrl: photo, lines: face.lines.map { TopoLineVM($0) })
                                    .padding(.top, 4)
                            }
                            if !face.lines.isEmpty {
                                Text("VÍAS (\(face.lines.count))").eyebrow().padding(.top, 4)
                                ForEach(Array(face.lines.enumerated()), id: \.element.id) { idx, l in
                                    VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 10) {
                                        Text("\(idx + 1)").font(Cumbre.mono(11, .bold))
                                            .foregroundStyle(GradeColor.style(l.grade).dark ? .black : .white)
                                            .frame(width: 24, height: 24)
                                            .background(Circle().fill(GradeColor.color(l.grade)))
                                        if let g = l.grade, !g.isEmpty {
                                            Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                                                .frame(width: 38, alignment: .leading)
                                        }
                                        Text(l.name.isEmpty ? "Vía \(idx + 1)" : l.displayName)
                                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                        Spacer()
                                        if let st = l.startType, !st.isEmpty {
                                            Text(st).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                        }
                                        // Compartir esta vía como IMAGEN (foto + líneas,
                                        // formato historia) → Instagram/WhatsApp; si no tiene
                                        // foto/dibujo cae al texto (espejo de Android).
                                        Button {
                                            Task { await ShareLineImage.share(block: block, line: l, schoolName: schoolName, tickedIds: tickedLines, projectIds: projectLines, sectorName: sectorName) }
                                        } label: {
                                            Image(systemName: "square.and.arrow.up")
                                                .font(.system(size: 16, weight: .medium))
                                                .foregroundStyle(Cumbre.ink2)
                                                .frame(width: 28, height: 28)
                                        }
                                        .buttonStyle(.plain)
                                        // Proyecto: la estás probando, aún no te ha salido. Oculto
                                        // si ya está hecha (no tiene sentido marcarla como proyecto).
                                        if !tickedLines.contains(l.id) {
                                            Button { Task { await toggleProject(l, index: idx) } } label: {
                                                if togglingProject == l.id {
                                                    ProgressView().scaleEffect(0.7).frame(width: 24, height: 24)
                                                } else {
                                                    let isProject = projectLines.contains(l.id)
                                                    Text("P")
                                                        .font(.system(size: 13, weight: .bold))
                                                        .foregroundStyle(isProject ? .white : Cumbre.ink3.opacity(0.4))
                                                        .frame(width: 24, height: 24)
                                                        .background(
                                                            Circle().fill(isProject ? Cumbre.terra : Color.clear)
                                                        )
                                                        .overlay(
                                                            Circle().stroke(isProject ? Color.clear : Cumbre.ink3.opacity(0.4), lineWidth: 1)
                                                        )
                                                }
                                            }
                                            .buttonStyle(.plain)
                                            .disabled(tickingLine != nil || togglingProject != nil)
                                        }
                                        // Tic: marca/desmarca la vía en tu diario (toggle).
                                        // Al MARCAR, según la preferencia "Publicar
                                        // ascensos en el feed": ASK → hoja de publicar;
                                        // ALWAYS → publica directo; NEVER → solo diario.
                                        Button { onTickTapped(l, index: idx) } label: {
                                            if tickingLine == l.id {
                                                ProgressView().scaleEffect(0.7).frame(width: 28, height: 28)
                                            } else {
                                                Image(systemName: tickedLines.contains(l.id) ? "checkmark.circle.fill" : "checkmark.circle")
                                                    .font(.system(size: 20))
                                                    .foregroundStyle(tickedLines.contains(l.id) ? Cumbre.ok : Cumbre.ink3)
                                                    .frame(width: 28, height: 28)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(tickingLine != nil || togglingProject != nil)
                                    }
                                    // Estrellas de valoración
                                    if onRateLine != nil {
                                        LineStarsRow(
                                            lineId: l.id,
                                            avgStars: l.avgStars.map { Float($0) },
                                            myStars: Int(l.myStars?.int32Value ?? 0)
                                        ) { stars in onRateLine?(l.id, stars) }
                                    }
                                    // Descripción/beta de la vía (si la tiene).
                                    if let d = l.lineDescription, !d.isEmpty {
                                        Text(d).font(.system(size: 12))
                                            .foregroundStyle(Cumbre.ink3)
                                    }
                                    // Comentarios de ESTA vía (desplegable).
                                    LineCommentsThreadView(store: commentsStore,
                                                           blockId: block.id, lineId: l.id)
                                    } // VStack
                                }
                            }
                          }
                          .id(faceIdx)
                        }
                    }

                    // (Comentarios solo en cada vía, no en la piedra entera.)

                    // Coordenadas (espejo de BlockDetailDialog).
                    Text(String(format: "%.5f, %.5f", block.lat, block.lon))
                        .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3).padding(.top, 2)

                    DirectionsButton(lat: block.lat, lon: block.lon, label: block.name).padding(.top, 8)

                    // Desplegable OPCIONES (editar vías / sector / eliminar).
                    if onEditLines != nil || onAssignSector != nil || onDelete != nil {
                        Button { withAnimation { optionsOpen.toggle() } } label: {
                            HStack {
                                Text("OPCIONES").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                    .foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: optionsOpen ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(.vertical, 12).padding(.horizontal, 12)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                    }

                    // Editor unificado de vías (corregir existentes + añadir nuevas).
                    if optionsOpen, block.type.uppercased() == "BLOCK", let onEditLines {
                        Button { dismiss(); onEditLines() } label: {
                            Text(block.lines.isEmpty ? NSLocalizedString("block_add_routes", comment: "") : NSLocalizedString("block_edit_routes", comment: ""))
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Asignar / cambiar sector (piedra, si la escuela tiene algún
                    // sector). Si ya tiene sector → "CAMBIAR SECTOR" (el picker
                    // muestra los demás; si no hay otro, lo avisa).
                    if optionsOpen, block.type.uppercased() == "BLOCK", let onAssignSector, !sectors.isEmpty {
                        Button { dismiss(); onAssignSector() } label: {
                            Text(block.sectorBlockId == nil ? NSLocalizedString("propose_assign_sector", comment: "") : NSLocalizedString("propose_change_sector", comment: ""))
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Admin: eliminar este bloque (piedra/zona/parking).
                    if optionsOpen, let onDelete {
                        Button(role: .destructive) { showDeleteConfirm = true } label: {
                            Text("ELIMINAR").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.bad).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                        }.buttonStyle(.plain)
                        .alert("¿Eliminar \(typeLabel.lowercased())?", isPresented: $showDeleteConfirm) {
                            Button("Cancelar", role: .cancel) {}
                            Button("Eliminar", role: .destructive) { dismiss(); onDelete() }
                        } message: {
                            Text("Se borrará del mapa para todos. No se puede deshacer.")
                        }
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .task { await commentsStore.load(blockId: block.id) }
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) }
            }
            .task { await loadDone() }
            // Hoja de publicar el tick en el feed Comunidad (estilo Cumbre).
            // Cerrar la hoja = no marcar nada.
            .sheet(item: $pendingTick) { pt in
                FeedPublishSheet(
                    lineLabel: feedTickLabel(pt.line, index: pt.index),
                    wasProject: pt.wasProject,
                    onPublish: { always, caption, photo in
                        if always { FeedPublishPrefs.mode = .always }
                        pendingTick = nil
                        Task {
                            await toggle(pt.line, index: pt.index)
                            publishTickToFeed(pt.line, wasProject: pt.wasProject,
                                              caption: caption, photo: photo)
                        }
                    },
                    onDiaryOnly: {
                        pendingTick = nil
                        Task { await toggle(pt.line, index: pt.index) }
                    })
            }
            // Deep-link del diario: hace scroll a la cara que contiene la vía
            // pulsada (sin reordenar las caras → FOTO 1, FOTO 2… en su orden).
            .onAppear {
                guard let i = scrollFaceIndex, i > 0 else { return }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    withAnimation { proxy.scrollTo(i, anchor: .top) }
                }
            }
            }
        }
    }

    /// Al abrir, marca como HECHAS (✓) las vías que ya están en tu diario, para
    /// que el tic quede persistente entre sesiones. Match por escuela + nombre de
    /// la vía (mismo nombre que se guardó al dar el tic).
    private func loadDone() async {
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
            let viaName = l.name.isEmpty ? "Vía \(idx + 1)" : l.name
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

    private var typeLabel: String {
        switch block.type.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }


    /// Toque en el tic: desmarcar va directo; marcar pasa por la preferencia
    /// "Publicar ascensos en el feed" (ASK/ALWAYS/NEVER) — espejo de Android.
    private func onTickTapped(_ line: BlockLine, index: Int) {
        if tickedLines.contains(line.id) {
            Task { await toggle(line, index: index) }
            return
        }
        // wasProject ANTES del toggle (marcar la quita de proyectos).
        let wasProject = projectLines.contains(line.id)
        switch FeedPublishPrefs.mode {
        case .ask:
            pendingTick = PendingFeedTick(line: line, index: index, wasProject: wasProject)
        case .always:
            Task {
                await toggle(line, index: index)
                publishTickToFeed(line, wasProject: wasProject)
            }
        case .never:
            Task { await toggle(line, index: index) }
        }
    }

    /// Etiqueta "vía · grado" de la hoja de publicar.
    private func feedTickLabel(_ line: BlockLine, index: Int) -> String {
        var label = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        if let g = line.grade, !g.isEmpty { label += " · \(g)" }
        return label
    }

    /// Publica el tick en el feed Comunidad (fire-and-forget: si falla no
    /// bloquea ni deshace el diario). kind = PROJECT_DONE si la vía estaba en
    /// proyectos; TICK en el resto. Ids del backend = String (UUID) tal cual.
    private func publishTickToFeed(_ line: BlockLine, wasProject: Bool,
                                   caption: String? = nil, photo: UIImage? = nil) {
        let kind = wasProject ? "PROJECT_DONE" : "TICK"
        let discipline = block.discipline.uppercased() == "ROUTE" ? "ROUTE" : "BOULDER"
        let lineId: String? = line.id.isEmpty ? nil : line.id
        Task {
            let container = AppDependencies.shared.container
            guard let postId = try? await container.publishFeedPost.invoke(
                blockId: block.id, lineId: lineId, kind: kind, discipline: discipline,
                caption: caption)
            else { return }
            // Foto de celebración (opcional): comprimir (máx 1024 px, JPEG 0.8,
            // mismo pipeline que StorageUploader) y subirla como multipart. Si
            // falla, el post queda publicado sin foto (aviso discreto).
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

    /// Marca/DESMARCA la vía en tu diario (toggle). Si no estaba hecha la añade
    /// (POST, o cola sin red); si ya estaba, la quita (borra la subida y/o la
    /// pendiente). No se puede añadir dos veces. Espejo del toggle de Android.
    private func toggle(_ line: BlockLine, index: Int) async {
        tickingLine = line.id
        let container = AppDependencies.shared.container
        let viaName = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        // Clave por lineId (fix homónimas "La ola") + legado por nombre.
        let key = "\(block.schoolId)|#\(line.id)"
        let legacyKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"

        if tickedLines.contains(line.id) {
            // DESMARCAR (quita también la clave legado, por si el ✓ venía de
            // una entrada antigua sin lineId).
            tickedLines.remove(line.id)
            JournalDoneStore.shared.remove(key)
            JournalDoneStore.shared.remove(legacyKey)
            // 1) Si solo estaba ENCOLADA (sin subir) → cancela la creación y listo.
            let hadPending = ((try? await container.dequeueJournal(key: key))?.boolValue) ?? false
            if !hadPending {
                // 2) Está (o estará) en el servidor: borra ya si hay red; si no,
                //    ENCOLA el borrado. La entrada se localiza POR lineId; solo
                //    si no hay ninguna con ese id, por nombre entre las SIN
                //    lineId — nunca borra la entrada de una homónima distinta.
                var deleted = false
                let journal = (try? await container.getMyJournal.invoke()) ?? []
                let j = journal.first(where: { $0.lineId == line.id })
                    ?? journal.first(where: {
                        $0.lineId == nil && $0.schoolId == block.schoolId &&
                        $0.blockName.caseInsensitiveCompare(viaName) == .orderedSame
                    })
                if let j {
                    deleted = ((try? await container.deleteJournalEntry.invoke(id: j.id)) != nil)
                }
                if !deleted {
                    // Payload del borrado = la clave de LA ENTRADA encontrada
                    // (id o legado) para que el filtrado offline case.
                    let delKey: String
                    if let j, j.lineId == nil, let sid = j.schoolId {
                        delKey = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                    } else { delKey = key }
                    try? await container.enqueueJournalDelete(key: delKey)
                }
            }
        } else {
            // Si era un PROYECTO, primero lo quitamos (local + servidor/cola): al
            // conseguirla, desaparece de Proyectos y pasa a Vías/Bloques.
            if projectLines.contains(line.id) {
                await removeProjectEntry(key: key, legacyKey: legacyKey, line: line, viaName: viaName)
                projectLines.remove(line.id)
            }
            // MARCAR HECHA (dedup: no estaba hecha)
            tickedLines.insert(line.id)
            JournalDoneStore.shared.add(key)
            try? await container.dequeueJournalDelete(key: key)   // cancela borrado pendiente
            let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
            // No guardamos "Piedra: N" (el número se recicla/borra → quedaría obsoleto).
            let req = CreateJournalRequest(
                schoolId: block.schoolId, schoolName: schoolName, sector: sectorName,
                blockName: viaName, grade: line.grade,
                notes: nil, date: df.string(from: Date()),
                discipline: block.discipline,   // la vía hereda la modalidad de su piedra
                lineId: line.id,                // id estable → enganche del diario por muro
                status: "DONE")
            let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
            if !ok { try? await container.enqueueJournal(req: req) }   // sin red → cola
        }
        tickingLine = nil
    }

    /// Marca/DESMARCA la vía como PROYECTO (la estás probando, aún no te ha
    /// salido). Espejo de [toggle], pero con status="PROJECT". No hace nada si
    /// la vía ya está HECHA (la UI ya oculta el botón en ese caso).
    private func toggleProject(_ line: BlockLine, index: Int) async {
        guard !tickedLines.contains(line.id) else { return }
        togglingProject = line.id
        let container = AppDependencies.shared.container
        let viaName = line.name.isEmpty ? "Vía \(index + 1)" : line.name
        // Clave por lineId + legado por nombre (ver toggle).
        let key = "\(block.schoolId)|#\(line.id)"
        let legacyKey = "\(block.schoolId)|\(viaName.trimmingCharacters(in: .whitespaces).lowercased())"

        if projectLines.contains(line.id) {
            // DESMARCAR proyecto
            projectLines.remove(line.id)
            await removeProjectEntry(key: key, legacyKey: legacyKey, line: line, viaName: viaName)
        } else {
            // MARCAR proyecto
            projectLines.insert(line.id)
            JournalProjectStore.shared.add(key)
            try? await container.dequeueJournalDelete(key: key)
            let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
            let req = CreateJournalRequest(
                schoolId: block.schoolId, schoolName: schoolName, sector: sectorName,
                blockName: viaName, grade: line.grade,
                notes: nil, date: df.string(from: Date()),
                discipline: block.discipline, lineId: line.id, status: "PROJECT")
            let ok = (try? await container.createJournalEntry.invoke(req: req)) != nil
            if !ok { try? await container.enqueueJournal(req: req) }
        }
        togglingProject = nil
    }

    /// Cancela/borra la entrada PROYECTO de [key] (cola pendiente o ya subida al
    /// servidor). Compartido por toggleProject (desmarcar) y toggle (promoción
    /// proyecto→hecha).
    private func removeProjectEntry(key: String, legacyKey: String,
                                    line: BlockLine, viaName: String) async {
        let container = AppDependencies.shared.container
        JournalProjectStore.shared.remove(key)
        JournalProjectStore.shared.remove(legacyKey)
        let hadPending = ((try? await container.dequeueJournal(key: key))?.boolValue) ?? false
        if !hadPending {
            var deleted = false
            let journal = (try? await container.getMyJournal.invoke()) ?? []
            // Por lineId; fallback por nombre SOLO entre entradas sin lineId.
            let j = journal.first(where: { $0.status == "PROJECT" && $0.lineId == line.id })
                ?? journal.first(where: {
                    $0.status == "PROJECT" && $0.lineId == nil &&
                    $0.schoolId == block.schoolId &&
                    $0.blockName.caseInsensitiveCompare(viaName) == .orderedSame
                })
            if let j {
                deleted = ((try? await container.deleteJournalEntry.invoke(id: j.id)) != nil)
            }
            if !deleted {
                let delKey: String
                if let j, j.lineId == nil, let sid = j.schoolId {
                    delKey = "\(sid)|\(j.blockName.trimmingCharacters(in: .whitespaces).lowercased())"
                } else { delKey = key }
                try? await container.enqueueJournalDelete(key: delKey)
            }
        }
    }
}

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el
/// schoolId (p. ej. al tocar una notificación con targetType "school").

struct LineStarsRow: View {
    let lineId: String
    let avgStars: Float?
    let myStars: Int
    let onRate: (Int) -> Void

    // Estilo Google Play: las estrellas muestran la MEDIA (amarillo) y son
    // tocables para votar; el toque se ve al instante y luego la media se
    // recalcula con el dato refrescado.
    @State private var pending: Int? = nil
    private let amber = Color(red: 0.96, green: 0.62, blue: 0.04)

    private var shown: Int { pending ?? Int((avgStars ?? 0).rounded()) }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                Button {
                    let newStars = myStars == i ? 0 : i   // re-tocar tu voto → quitarlo
                    pending = newStars > 0 ? newStars : nil
                    onRate(newStars)
                } label: {
                    Image(systemName: i <= shown ? "star.fill" : "star")
                        .font(.system(size: 13))
                        .foregroundStyle(i <= shown ? amber : Cumbre.ink3)
                }
                .buttonStyle(.plain)
            }
            if let avg = avgStars, avg > 0 {
                Text(String(format: "%.1f", avg))
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    .padding(.leading, 4)
            }
            if myStars > 0 {
                Text("· tu voto \(myStars)★")
                    .font(Cumbre.mono(11)).foregroundStyle(amber)
                    .padding(.leading, 4)
            }
        }
        .padding(.leading, 34)
        .onChange(of: avgStars) { _, _ in pending = nil }
        .onChange(of: myStars) { _, _ in pending = nil }
    }
}

/// Extrae la fecha (YYYY-MM-DD) de un timestamp ISO; si no encaja, devuelve tal cual.
