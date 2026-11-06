import SwiftUI
import Shared
import CoreLocation

// Cards de revision de propuestas (escuelas nuevas + mejoras) con su diff,
// mini-mapa, topo ampliable y botones. Reparto del antiguo AdminView.swift de 1.789 lineas.

struct SubmissionAdminCard: View {
    let submission: Submission
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(submission.proposedName).font(Cumbre.serif(17, .bold)).foregroundStyle(Cumbre.ink)
            let sub = [submission.proposedRockType?.uppercased(), submission.proposedRegion, submission.proposedLocation]
                .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
            if !sub.isEmpty { Text(sub).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3) }
            Text(String(format: "%.5f, %.5f", submission.proposedLat, submission.proposedLon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            if let n = submission.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

struct ContributionAdminCard: View {
    let contribution: Contribution
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    /// "EDITAR Y APROBAR": (bloquesJson retocado) → aprueba con los cambios.
    var onApproveEdited: ((String) -> Void)? = nil
    @State private var showMap = false
    @State private var showEditApprove = false
    // Bloques de la escuela (para resolver la piedra/sector destino al revisar
    // corregir/añadir vías o asignar sector — el admin debe ver QUÉ se cambia).
    @State private var schoolBlocks: [Block] = []
    @State private var blocksLoaded = false
    private var targetBlock: Block? { schoolBlocks.first { $0.id == contribution.targetBlockId } }

    private var isCorrection: Bool { contribution.type.uppercased() == "POSITION_CORRECTION" }
    private var newCoord: CLLocationCoordinate2D? {
        guard let la = contribution.proposedLat?.doubleValue,
              let lo = contribution.proposedLon?.doubleValue else { return nil }
        return CLLocationCoordinate2D(latitude: la, longitude: lo)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(adminTypeLabel(contribution.type)).font(Cumbre.mono(11, .bold)).tracking(0.8)
                    .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 3)
                    .background(isCorrection ? Cumbre.bad : Cumbre.terra)
                Spacer()
                if let a = contribution.submittedByName, !a.isEmpty {
                    Text(a).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Text(contribution.schoolName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)

            // QUÉ CAMBIA en una línea — se entiende la propuesta sin scrollear
            // (el detalle foto a foto sigue debajo). Espejo de ContributionCard.kt.
            Text(contributionSummary(contribution, blocks: schoolBlocks))
                .font(.system(size: 13, weight: .medium)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Cumbre.terraBg.opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.terra.opacity(0.4), lineWidth: 1))

            if let nm = contribution.name, !nm.isEmpty { Text(nm).font(.system(size: 14)).foregroundStyle(Cumbre.ink) }
            if let n = contribution.notes, !n.isEmpty { Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }

            // QUÉ CAMBIA — visualización por tipo (clave para revisar bien).
            switch contribution.type.uppercased() {
            case "POSITION_CORRECTION":
                VStack(alignment: .leading, spacing: 2) {
                    // targetBlockId == nil ⇒ el backend mueve la escuela entera;
                    // si hay id, mueve ese bloque concreto (espejo de la materialización).
                    Text(contribution.targetBlockId == nil
                         ? "MUEVE LA ESCUELA ENTERA"
                         : "MUEVE «\((contribution.name ?? "bloque").uppercased())»")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    Text("✕ actual: \(coordStr(contribution.lat, contribution.lon))")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    if let nc = newCoord {
                        Text("★ nueva: \(coordStr(nc.latitude, nc.longitude))")
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    }
                }
            case "BOULDER":
                BoulderReviewView(contribution: contribution, targetBlock: targetBlock)
            case "ASSIGN_SECTOR":
                let sector = schoolBlocks.first { $0.id == contribution.sectorBlockId }
                VStack(alignment: .leading, spacing: 2) {
                    Text("ASIGNAR SECTOR A PIEDRA").font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)
                    Text("PIEDRA · \((targetBlock?.name ?? contribution.name ?? "?").uppercased())")
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    Text("→ SECTOR · \((sector?.name ?? "?").uppercased())")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                }
            default:
                EmptyView()
            }

            Text(coordStr(contribution.lat, contribution.lon))
                .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)

            // Mini-mapa con el cambio en contexto (existentes atenuados + propuesta
            // resaltada). El admin ve directamente dónde cae y si pisa algo.
            if blocksLoaded {
                ContributionMiniMap(contribution: contribution, blocks: schoolBlocks)
            }

            Button { showMap = true } label: {
                Text(NSLocalizedString("admin_view_map", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }.buttonStyle(.plain)

            ReviewButtons(busy: busy, onApprove: onApprove, onReject: onReject)

            // EDITAR Y APROBAR: el admin retoca la propuesta en el editor
            // normal (imán, toques, franjas) y se aprueba con SUS cambios.
            if onApproveEdited != nil, !AdminEditApprove.editableFaces(of: contribution).isEmpty {
                Button { showEditApprove = true } label: {
                    Text("✎ EDITAR Y APROBAR").font(Cumbre.mono(11, .bold)).tracking(0.8)
                        .foregroundStyle(Cumbre.terra)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                }.buttonStyle(.plain).disabled(busy)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .sheet(isPresented: $showMap) {
            ContributionMapSheet(contribution: contribution)
        }
        .sheet(isPresented: $showEditApprove) {
            AdminEditApproveSheet(contribution: contribution) { edited in
                showEditApprove = false
                onApproveEdited?(edited)
            }
        }
        .task(id: contribution.id) {
            guard !blocksLoaded else { return }
            schoolBlocks = (try? await AppDependencies.shared.container.getBlocks
                .invoke(schoolId: contribution.schoolId)) ?? []
            blocksLoaded = true
        }
    }

    private func coordStr(_ lat: Double, _ lon: Double) -> String {
        String(format: "%.5f, %.5f", lat, lon)
    }
}

/// Visualización de una contribución BOULDER para el admin: piedra nueva, añadir
/// vías, o corregir una vía. Dibuja existentes (difuminadas) + nuevas sobre la
/// foto y resume ORIGINAL vs PROPUESTA. Espejo de ContributionCard.kt.
struct BoulderReviewView: View {
    let contribution: Contribution
    let targetBlock: Block?

    private var isNewBoulder: Bool { contribution.targetBlockId == nil }
    private var newLines: [TopoLineVM] { TopoParse.lines(contribution.bloquesJson) }
    // ids de vías que esta propuesta corrige: targetLineId de la contribución
    // (corrección individual) + targetLineId por entrada (editor unificado).
    private var correctedIds: Set<String> {
        var ids = TopoParse.targetLineIds(contribution.bloquesJson)
        if let t = contribution.targetLineId { ids.insert(t) }
        return ids
    }
    private var isEditLine: Bool { !correctedIds.isEmpty }
    // Existentes que NO cambian → normales.
    private var existingNormal: [TopoLineVM] {
        (targetBlock?.lines ?? []).filter { !correctedIds.contains($0.id) }.map { TopoLineVM($0) }
    }
    // Vías viejas que se corrigen (difuminadas), para distinguir el cambio.
    private var oldEdited: [TopoLineVM] {
        (targetBlock?.lines ?? []).filter { correctedIds.contains($0.id) }.map { TopoLineVM($0) }
    }
    private var photo: String? { isNewBoulder ? contribution.photoUrl : targetBlock?.photoPath }
    private var editedOriginals: [BlockLine] {
        (targetBlock?.lines ?? []).filter { correctedIds.contains($0.id) }
    }

    // Propuesta agrupada por cara (foto) para comparar ACTUAL vs PROPUESTA.
    private var proposed: [TopoParse.ProposedVia] { TopoParse.proposedVias(contribution.bloquesJson) }
    private var faceGroups: [(key: String, vias: [TopoParse.ProposedVia])] {
        // Agrupa por photoUrl (clave "" si nil) preservando el orden de aparición.
        var order: [String] = []
        var map: [String: [TopoParse.ProposedVia]] = [:]
        for p in proposed {
            let k = p.photoUrl ?? ""
            if map[k] == nil { order.append(k) }
            map[k, default: []].append(p)
        }
        return order.map { (key: $0, vias: map[$0] ?? []) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(header).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.ink2)

            if isNewBoulder {
                // Piedra nueva: UNA sección por CARA (foto) con SUS líneas — no todas
                // amontonadas en la portada.
                if faceGroups.contains(where: { !$0.key.isEmpty }) {
                    ForEach(Array(faceGroups.enumerated()), id: \.offset) { _, group in
                        Divider().overlay(Cumbre.rule)
                        let photo = group.key.isEmpty ? (contribution.photoUrl ?? "") : group.key
                        if !photo.isEmpty {
                            Text("FOTO").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                            ZoomableTopo(photoUrl: photo, lines: group.vias.map { $0.line })
                        }
                        ForEach(Array(group.vias.enumerated()), id: \.offset) { _, v in
                            Text("NUEVA · \(lineSummary(v.line.name, v.line.grade, v.line.startType))")
                                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
                } else if let photo = contribution.photoUrl, !photo.isEmpty {
                    ZoomableTopo(photoUrl: photo, lines: newLines)
                    ForEach(Array(newLines.enumerated()), id: \.offset) { _, l in
                        Text("NUEVA · \(lineSummary(l.name, l.grade, l.startType))")
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                    }
                } else if !newLines.isEmpty {
                    Text("SIN FOTO — el proponente no adjuntó imagen")
                        .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    ForEach(Array(newLines.enumerated()), id: \.offset) { _, l in
                        Text("NUEVA · \(lineSummary(l.name, l.grade, l.startType))")
                            .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                    }
                }
            } else {
                // Corrección/edición: una sección por cara. Solo enseña las fotos
                // si cambia el dibujo/foto; si solo cambian textos, el diff de campos.
                let faces = targetBlock?.facesOrDerived() ?? []
                ForEach(Array(faceGroups.enumerated()), id: \.offset) { _, group in
                    let targetIds = Set(group.vias.compactMap { $0.targetLineId })
                    let oldFace = faces.first { f in f.lines.contains { targetIds.contains($0.id) } }
                        ?? faces.first { ($0.photoPath ?? "") == group.key }
                        ?? faces.first
                    let oldPhoto = oldFace?.photoPath ?? targetBlock?.photoPath
                    let photoChanged = !group.key.isEmpty && group.key != (oldPhoto ?? "")
                    let proposalPhoto = group.key.isEmpty ? oldPhoto : group.key
                    let isNewFace = oldFace == nil
                    let drawingChanged = isNewFace || photoChanged || group.vias.contains { v in
                        guard let tid = v.targetLineId,
                              let o = oldFace?.lines.first(where: { $0.id == tid }) else { return true }
                        return !TopoParse.pointsEqual(v.line.points, TopoParse.points(o.linePath))
                    }

                    Divider().overlay(Cumbre.rule)
                    if drawingChanged {
                        if let op = oldPhoto, !op.isEmpty {
                            Text(photoChanged ? "FOTO ACTUAL" : "ACTUAL")
                                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                            ZoomableTopo(photoUrl: op, lines: (oldFace?.lines ?? []).map { TopoLineVM($0) })
                        }
                        Text(photoChanged ? "FOTO PROPUESTA (NUEVA)" : "PROPUESTA")
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
                        if let pp = proposalPhoto, !pp.isEmpty {
                            let keep = photoChanged ? [] :
                                (oldFace?.lines ?? []).filter { !targetIds.contains($0.id) }.map { TopoLineVM($0) }
                            ZoomableTopo(photoUrl: pp, lines: keep + group.vias.map { $0.line })
                        }
                    } else {
                        Text("SOLO TEXTO · el dibujo y la foto no cambian")
                            .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                    }
                    // Diff por vía: qué CAMPOS cambian (nombre/grado/variante/tipo/desc).
                    ForEach(Array(group.vias.enumerated()), id: \.offset) { _, v in
                        ViaChangeRowsView(
                            orig: v.targetLineId.flatMap { tid in oldFace?.lines.first(where: { $0.id == tid }) },
                            v: v)
                    }
                }
            }
        }
    }

    private var header: String {
        if isNewBoulder { return "PIEDRA NUEVA" }
        let nm = (targetBlock?.name ?? contribution.name ?? "piedra").uppercased()
        if isEditLine && !newLines.isEmpty && newLines.count > oldEdited.count {
            return "EDITA / AÑADE VÍAS DE «\(nm)»"
        }
        return isEditLine ? "CORRIGE VÍA(S) DE «\(nm)»" : "AÑADE VÍAS A «\(nm)»"
    }
}

/// Topo pulsable: tocar la foto la abre a pantalla completa con las vías
/// pintadas encima (para revisar cómo las trazó el proponente).
struct ZoomableTopo: View {
    let photoUrl: String
    let lines: [TopoLineVM]
    @State private var open = false
    var body: some View {
        TopoPhotoView(photoUrl: photoUrl, lines: lines)
            .overlay(alignment: .bottomTrailing) {
                Text("TOCA PARA AMPLIAR")
                    .font(Cumbre.mono(8, .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 5).padding(.vertical, 3)
                    .background(Color.black.opacity(0.55))
                    .padding(4)
            }
            .contentShape(Rectangle())
            .onTapGesture { open = true }
            .fullScreenCover(isPresented: $open) {
                ZStack(alignment: .topTrailing) {
                    Color.black.ignoresSafeArea()
                        .onTapGesture { open = false }
                    VStack {
                        Spacer()
                        // Con zoom y foco: es donde el admin decide si una
                        // linea sigue la via o va por el aire, y a tamano de
                        // tarjeta eso no se ve.
                        TopoPhotoView(photoUrl: photoUrl, lines: lines, interactive: true)
                        Spacer()
                    }
                    Button("✕ CERRAR") { open = false }
                        .font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                        .padding(16)
                }
            }
    }
}

/// Resumen de una vía (nombre · grado · tipo) para el admin.
func lineSummary(_ name: String?, _ grade: String?, _ start: String?) -> String {
    [name?.isEmpty == false ? name : nil, grade, start]
        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
}

/// Diff de UNA vía corregida/nueva: si es nueva, sus campos; si corrige, SOLO los
/// campos que cambian (nombre/grado/variante/tipo/desc) viejo→nuevo, más una nota
/// si se redibujó el trazado.
///
/// ESPEJO de `computeLineDiff` (shared/domain/usecase/contributions/LineDiff.kt),
/// que es la fuente de verdad y está cubierta por LineDiffTest. Si tocas la lógica
/// de qué cuenta como cambio, tócala en el Kotlin Y aquí (patrón GradeColor/
/// TopoParse del proyecto: la lógica pura se espeja, no se puentea por SKIE).
struct ViaChangeRowsView: View {
    let orig: BlockLine?
    let v: TopoParse.ProposedVia

    var body: some View {
        if let o = orig {
            let name = (v.line.name?.isEmpty == false ? v.line.name : o.name) ?? "(sin nombre)"
            let redrawn = !TopoParse.pointsEqual(v.line.points, TopoParse.points(o.linePath))
            let anyChange = o.name != v.line.name || o.grade != v.line.grade
                || o.variant != v.line.variant || o.startType != v.line.startType
                || o.lineDescription != v.line.desc || redrawn
            VStack(alignment: .leading, spacing: 1) {
                Text("• \(name)" + (anyChange ? "" : "  (sin cambios)"))
                    .font(Cumbre.mono(10)).foregroundStyle(anyChange ? Cumbre.ink : Cumbre.ink3)
                fieldRow("Nombre", o.name, v.line.name)
                fieldRow("Grado", o.grade, v.line.grade)
                fieldRow("Variante", o.variant, v.line.variant)
                fieldRow("Tipo", o.startType, v.line.startType)
                fieldRow("Descripción", o.lineDescription, v.line.desc)
                if redrawn {
                    Text("    Trazado: redibujado (ver foto)")
                        .font(Cumbre.mono(10)).foregroundStyle(Cumbre.terra)
                }
            }
        } else {
            Text("• NUEVA · \(newSummary)")
                .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
        }
    }

    private var newSummary: String {
        [v.line.name?.isEmpty == false ? v.line.name : nil, v.line.grade,
         v.line.variant.map { "(\($0))" }, v.line.startType, v.line.desc]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    @ViewBuilder
    private func fieldRow(_ label: String, _ old: String?, _ new: String?) -> some View {
        let o = (old?.isEmpty == false) ? old : nil
        let n = (new?.isEmpty == false) ? new : nil
        if o != n {
            HStack(spacing: 6) {
                Text("\(label):").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                Text("\(o ?? "—") → \(n ?? "—")").font(Cumbre.mono(10)).foregroundStyle(Cumbre.terra)
            }.padding(.leading, 10)
        }
    }
}

enum AdminTab: String, CaseIterable {
    case propuestas = "PROPUESTAS"
    case denuncias = "DENUNCIAS"
    case gestionar = "GESTIONAR"
    case stats = "STATS"
    case actividad = "ACTIVIDAD"
    case sugerencias = "SUGERENCIAS"
    case push = "PUSH"
    case historial = "HISTORIAL"
    case aportaciones = "APORTACIONES"
    case salud = "SALUD"

    /// Nombre en el menú vertical del panel.
    var title: String {
        switch self {
        case .propuestas: return "Propuestas"
        case .denuncias: return "Denuncias"
        case .gestionar: return "Escuelas"
        case .stats: return "Estadísticas"
        case .actividad: return "Actividad"
        case .sugerencias: return "Sugerencias"
        case .push: return "Notificaciones"
        case .historial: return "Historial de cambios"
        case .aportaciones: return "Aportaciones y confianza"
        case .salud: return "Salud del servidor"
        }
    }

    /// Una línea que dice qué hay dentro.
    var hint: String {
        switch self {
        case .propuestas: return "Piedras, sectores y correcciones por aprobar"
        case .denuncias: return "Contenido y personas denunciadas"
        case .gestionar: return "Editar escuelas, bloques, restricciones"
        case .stats: return "Usuarios, escuelas y actividad en números"
        case .actividad: return "Lo que han hecho los admins"
        case .sugerencias: return "Lo que cuentan los usuarios"
        case .push: return "Enviar un aviso a los usuarios"
        case .historial: return "Quién cambió qué, con restaurar"
        case .aportaciones: return "Cuánto aporta cada persona"
        case .salud: return "Errores, usuarios hoy y asistente"
        }
    }
}
