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

struct OrientationTarget: Identifiable {
    let id = UUID()
    let photoIndex: Int?
}

struct GradeVoteTarget: Identifiable {
    var id: String { lineId }
    let lineId: String
    let canVote: Bool
}

struct BlockInfoSheet: View {
    // Votacion comunitaria (C2/C5): orientacion + sol/sombra + grado.
    @StateObject private var community = CommunityVoteStore()
    @State private var orientationTarget: OrientationTarget? = nil
    @State private var gradeTarget: GradeVoteTarget? = nil
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
    /// Filtro de grado activo en la escuela (BLOCK_SEARCH_DESIGN.md §7.3): las
    /// vías FUERA de rango se atenúan, nunca se ocultan — dentro de una piedra
    /// que sí se muestra, esconder vías haría perder el contexto de la pared.
    /// nil = sin filtro, todas a color pleno.
    var gradeMatchingLineIds: Set<String>? = nil
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    /// Caras de la piedra, SIEMPRE en el orden en que se introdujeron (FOTO 1,
    /// FOTO 2…). El deep-link del diario NO reordena: solo hace scroll a la cara
    /// que contiene la vía pulsada (ver `scrollFaceIndex`).
    private var orderedFaces: [BlockFace] { block.facesOrDerived() }

    /// Índice de la cara que contiene la vía del deep-link (para hacer scroll a
    /// ella al abrir). Nil si no hay deep-link o no se encuentra.
    ///
    /// Por ID primero, por NOMBRE si no: desde 2026-08-17 el feed manda el id
    /// de la vía (antes el nombre, que colisionaba entre piedras — "abría la
    /// piedra equivocada"). Si aquí solo se comparara por nombre, un id como
    /// "3c2cd1ea-..." nunca haría match con ningún `$0.name` y la ficha se
    /// quedaba siempre en FOTO 1 sin avisar — el mismo bug, una capa más
    /// adentro (Rodrigo, build 147: "solo me abre la primera").
    private var scrollFaceIndex: Int? {
        guard let via = highlightVia?.trimmingCharacters(in: .whitespaces), !via.isEmpty else { return nil }
        return orderedFaces.firstIndex { f in f.lines.contains { $0.id == via } }
            ?? orderedFaces.firstIndex { f in
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
    /// Cara marcada en las pestañas de salto (piedras con varias fotos).
    @State private var caraVisible = 0

    private var sectorName: String? {
        guard let sid = block.sectorBlockId else { return nil }
        return sectors.first(where: { $0.id == sid })?.name
    }

    /// ¿Esta vía queda fuera del filtro de grado activo? (nil = sin filtro).
    private func gradeDimmed(_ lineId: String) -> Bool {
        guard let matching = gradeMatchingLineIds else { return false }
        return !matching.contains(lineId)
    }

    /// Ancla de scroll de una cara. Con NOMBRE y no el número suelto: las
    /// pestañas de salto viven en el mismo `ScrollViewReader` y su `ForEach` ya
    /// usa 0,1,2… como identidad, así que `scrollTo(1)` era ambiguo y se iba a
    /// la PESTAÑA en vez de a la foto — al pulsar no pasaba nada visible
    /// (reportado por Rodrigo probando el build 139).
    private func anclaDeCara(_ idx: Int) -> String { "cara-\(idx)" }

    /// Misma lógica que DirectionsButton (SchoolDetailHelpers.swift), ahora
    /// disparada desde el icono de la barra de arriba.
    private func openDirections() {
        let g = URL(string: "comgooglemaps://?daddr=\(block.lat),\(block.lon)&directionsmode=driving")!
        let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(block.lat),\(block.lon)")!
        openURL(UIApplication.shared.canOpenURL(g) ? g : web)
    }

    /// N10: COMPARTIR PIEDRA — portada con TODAS sus vías. Antes botón grande
    /// al final del scroll, ahora icono en la barra de arriba.
    private func shareBlock() async {
        guard let firstLine = block.lines.first else { return }
        var badge = community.summaryFor(nil)?.consensus
        if badge == nil {
            badge = (try? await AppDependencies.shared.container
                .getOrientation.invoke(blockId: block.id))?
                .first(where: { $0.photoIndex == nil })?.consensus
        }
        await ShareLineImage.share(
            block: block, line: firstLine, schoolName: schoolName,
            tickedIds: tickedLines, projectIds: projectLines,
            sectorName: sectorName, orientationBadge: badge)
    }

    /// Pestaña para saltar a una cara — misma celda "mochila" que el Feed:
    /// plana, con borde fino; la activa marca borde y texto en terracota.
    private func caraTab(_ label: String, selected: Bool,
                         action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(Cumbre.mono(11, .bold))
                .tracking(0.8)
                .foregroundStyle(selected ? Cumbre.terra : Cumbre.ink)
                .padding(.horizontal, 12)
                .frame(height: 36)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(
                    selected ? Cumbre.terra : Cumbre.rule,
                    lineWidth: selected ? 1.5 : 0.5))
                .clipShape(RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        }
        .buttonStyle(.plain)
    }
    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            VStack(spacing: 0) {
            // Saltar de una cara a otra sin scrollear. El scroll sigue
            // funcionando igual: esto es un atajo, no un sustituto (petición de
            // Rodrigo, 2026-08-16). Mismo estilo "mochila" que las pestañas del
            // Feed. Solo aparece si de verdad hay varias fotos.
            if block.type.uppercased() == "BLOCK",
               orderedFaces.filter({ !($0.photoPath ?? "").isEmpty }).count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(orderedFaces.enumerated()), id: \.offset) { faceIdx, face in
                            if !(face.photoPath ?? "").isEmpty {
                                caraTab("FOTO \(faceIdx + 1)", selected: caraVisible == faceIdx) {
                                    caraVisible = faceIdx
                                    withAnimation { proxy.scrollTo(anclaDeCara(faceIdx), anchor: .top) }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    Text(block.name.isEmpty ? typeLabel : block.name)
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)

                    // C2: orientacion votable de la piedra/sector entero + tira de sol.
                    HStack(spacing: 8) {
                        VotableChip(text: community.summaryFor(nil)?.consensus.map { "PARED " + $0 } ?? "ORIENTACION") {
                            orientationTarget = OrientationTarget(photoIndex: nil)
                        }
                        let votesTotal = community.summaryFor(nil)?.votes.values
                            .reduce(0) { $0 + $1.intValue } ?? 0
                        if votesTotal > 0 {
                            Text("\(votesTotal) votos").font(.system(size: 11))
                                .foregroundStyle(Cumbre.ink3)
                        }
                    }
                    if let sun = community.sunByPhoto[-1] {
                        SunStripView(sun: sun)
                    }

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
                                    // iOS no reordena las caras (solo scroll) => el indice ya es el original.
                                    let originalIdx = faceIdx
                                    HStack(spacing: 8) {
                                        Text("FOTO \(faceIdx + 1)").eyebrow()
                                        // C2: cada cara de un muro vota su orientacion.
                                        VotableChip(text: community.summaryFor(originalIdx)?.consensus.map { "PARED " + $0 } ?? "ORIENTAR ESTA CARA") {
                                            orientationTarget = OrientationTarget(photoIndex: originalIdx)
                                            Task { await community.loadSun(blockId: block.id, photoIndex: originalIdx) }
                                        }
                                    }
                                    .padding(.top, 4)
                                }
                                TopoPhotoView(photoUrl: photo, lines: face.lines.map { TopoLineVM($0) },
                                              interactive: true)
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
                                            // C5: el grado es VOTABLE (chip discontinuo terra).
                                            VotableChip(text: g) {
                                                let canVote = tickedLines.contains(l.id) || projectLines.contains(l.id)
                                                gradeTarget = GradeVoteTarget(lineId: l.id, canVote: canVote)
                                                Task { await community.loadGrade(lineId: l.id) }
                                            }
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
                                            Task {
                                                // await no puede vivir en el autoclosure de ?? -> lets explicitos.
                                                var badge = community.summaryFor(nil)?.consensus
                                                if badge == nil {
                                                    badge = (try? await AppDependencies.shared.container
                                                        .getOrientation.invoke(blockId: block.id))?
                                                        .first(where: { $0.photoIndex == nil })?.consensus
                                                }
                                                await ShareLineImage.share(
                                                    block: block, line: l, schoolName: schoolName,
                                                    tickedIds: tickedLines, projectIds: projectLines,
                                                    sectorName: sectorName,
                                                    orientationBadge: badge,
                                                    setterGradeRef: {
                                                        guard let g = community.grade,
                                                              g.lineId == l.id,
                                                              let setter = g.setterGrade,
                                                              setter != g.displayedGrade else { return nil }
                                                        return setter
                                                    }())
                                            }
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
                                    // Filtro de grado: las vías fuera de rango se
                                    // atenúan (siguen pulsables, ver §7.3).
                                    .opacity(gradeDimmed(l.id) ? 0.35 : 1)
                                }
                            }
                          }
                          // Ancla con nombre PROPIO, no el número suelto: las
                          // pestañas de salto viven en el mismo ScrollViewReader
                          // y su ForEach ya usa 0,1,2… como identidad. Con ids
                          // duplicados, scrollTo(1) se iba a la PESTAÑA en vez
                          // de a la foto y no pasaba nada visible.
                          .id(anclaDeCara(faceIdx))
                        }
                    }

                    // (Comentarios solo en cada vía, no en la piedra entera.)

                    // Coordenadas (espejo de BlockDetailDialog).
                    Text(String(format: "%.5f, %.5f", block.lat, block.lon))
                        .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3).padding(.top, 2)

                    // Cómo llegar, Compartir y Opciones viven ahora en la barra de
                    // arriba (mismos iconos que ya usa la ficha de escuela) —
                    // siempre visibles, sin bajar a buscarlos (Álvaro, 2026-08-24).
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .task { await commentsStore.load(blockId: block.id) }
            .task(id: block.id) {
                await community.loadOrientation(blockId: block.id)
                await community.loadSun(blockId: block.id, photoIndex: nil)
            }
            .sheet(item: $orientationTarget) { target in
                OrientationVoteSheet(store: community, blockId: block.id,
                                     photoIndex: target.photoIndex)
            }
            .sheet(item: $gradeTarget) { target in
                GradeVoteSheet(store: community, lineId: target.lineId,
                               canVote: target.canVote)
            }
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) }
                // Cómo llegar / Compartir / Opciones siempre visibles arriba, en
                // vez de botones grandes al final del scroll (Álvaro, 2026-08-24).
                // Mismos iconos que ya usa la ficha de escuela (SchoolDetailView):
                // arrow.triangle.turn.up.right.diamond y square.and.arrow.up.
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { openDirections() } label: {
                        Image(systemName: "arrow.triangle.turn.up.right.diamond")
                    }.foregroundStyle(Cumbre.ink3)
                    if block.type.uppercased() == "BLOCK", block.lines.first != nil {
                        Button { Task { await shareBlock() } } label: {
                            Image(systemName: "square.and.arrow.up")
                        }.foregroundStyle(Cumbre.ink3)
                    }
                    if onEditLines != nil || onAssignSector != nil || onDelete != nil {
                        Menu {
                            if block.type.uppercased() == "BLOCK", let onEditLines {
                                Button { dismiss(); onEditLines() } label: {
                                    Label(block.lines.isEmpty ? NSLocalizedString("block_add_routes", comment: "") : NSLocalizedString("block_edit_routes", comment: ""), systemImage: "pencil")
                                }
                            }
                            if block.type.uppercased() == "BLOCK", let onAssignSector, !sectors.isEmpty {
                                Button { dismiss(); onAssignSector() } label: {
                                    Label(block.sectorBlockId == nil ? NSLocalizedString("propose_assign_sector", comment: "") : NSLocalizedString("propose_change_sector", comment: ""), systemImage: "square.dashed")
                                }
                            }
                            if onDelete != nil {
                                Button(role: .destructive) { showDeleteConfirm = true } label: {
                                    Label("ELIMINAR", systemImage: "trash")
                                }
                            }
                        } label: {
                            Image(systemName: "gearshape")
                        }.foregroundStyle(Cumbre.ink)
                    }
                }
            }
            .alert("¿Eliminar \(typeLabel.lowercased())?", isPresented: $showDeleteConfirm) {
                Button("Cancelar", role: .cancel) {}
                Button("Eliminar", role: .destructive) { if let onDelete { dismiss(); onDelete() } }
            } message: {
                Text("Se borrará del mapa para todos. No se puede deshacer.")
            }
            .task { await loadDone() }
            // Hoja de publicar el tick en el feed Comunidad (estilo Cumbre).
            // Cerrar la hoja = no marcar nada.
            .sheet(item: $pendingTick) { pt in
                FeedPublishSheet(
                    lineLabel: feedTickLabel(pt.line, index: pt.index),
                    wasProject: pt.wasProject,
                    onPublish: { always, caption, photo, sessionDate, aVista, alFlash in
                        if always { FeedPublishPrefs.mode = .always }
                        pendingTick = nil
                        Task {
                            await toggle(pt.line, index: pt.index, sessionDate: sessionDate,
                                         aVista: aVista, alFlash: alFlash)
                            publishTickToFeed(pt.line, wasProject: pt.wasProject,
                                              caption: caption, photo: photo)
                        }
                    },
                    onDiaryOnly: { sessionDate, aVista, alFlash in
                        pendingTick = nil
                        Task { await toggle(pt.line, index: pt.index, sessionDate: sessionDate,
                                            aVista: aVista, alFlash: alFlash) }
                    })
            }
            // Deep-link del diario: hace scroll a la cara que contiene la vía
            // pulsada (sin reordenar las caras → FOTO 1, FOTO 2… en su orden).
            .onAppear {
                guard let i = scrollFaceIndex, i > 0 else { return }
                // Sincroniza la pestaña visual con el salto: sin esto, el scroll
                // llegaba bien pero la pestaña seguía marcando "FOTO 1".
                caraVisible = i
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    withAnimation { proxy.scrollTo(anclaDeCara(i), anchor: .top) }
                }
            }
            }   // VStack: pestañas de cara + contenido
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
            guard let postId = await reporting("No se pudo publicar el ascenso", {
                try await container.publishFeedPost.invoke(
                    blockId: block.id, lineId: lineId, kind: kind, discipline: discipline,
                    caption: caption)
            }) else { return }
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
    private func toggle(_ line: BlockLine, index: Int, sessionDate: String? = nil,
                        aVista: Bool = false, alFlash: Bool = false) async {
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
                notes: nil, date: sessionDate ?? df.string(from: Date()),
                discipline: block.discipline,   // la vía hereda la modalidad de su piedra
                lineId: line.id,                // id estable → enganche del diario por muro
                status: "DONE",
                aVista: aVista, alFlash: alFlash)
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
                discipline: block.discipline, lineId: line.id, status: "PROJECT",
                aVista: false, alFlash: false)
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
