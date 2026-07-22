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
                // Piedra nueva: foto propuesta + sus líneas.
                if let photo = contribution.photoUrl, !photo.isEmpty {
                    ZoomableTopo(photoUrl: photo, lines: newLines)
                } else if !newLines.isEmpty {
                    Text("SIN FOTO — el proponente no adjuntó imagen")
                        .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                }
                ForEach(Array(newLines.enumerated()), id: \.offset) { _, l in
                    Text("NUEVA · \(lineSummary(l.name, l.grade, l.startType))")
                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
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
                        TopoPhotoView(photoUrl: photoUrl, lines: lines)
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
/// si se redibujó el trazado. Espejo de ViaChangeRows de ContributionDiff.kt.
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

func adminTypeLabel(_ t: String) -> String {
    switch t.uppercased() {
    case "PARKING": return "PARKING"
    case "BOULDER": return "PIEDRA"
    case "SECTOR": return "SECTOR"
    case "POSITION_CORRECTION": return "CORREGIR POSICIÓN"
    case "ASSIGN_SECTOR": return "ASIGNAR SECTOR"
    default: return t.uppercased()
    }
}

/// Resumen humano de UNA LÍNEA de qué cambia una propuesta — espejo de
/// contributionSummary de ContributionCard.kt (Android).
func contributionSummary(_ c: Contribution, blocks: [Block]) -> String {
    let target = blocks.first { $0.id == c.targetBlockId }
    switch c.type.uppercased() {
    case "PARKING":
        let nm = (c.name?.isEmpty == false) ? " «\(c.name!)»" : ""
        return "Añade un parking nuevo" + nm
    case "SECTOR":
        let nm = (c.name?.isEmpty == false) ? " «\(c.name!)»" : ""
        return "Añade un sector nuevo" + nm
    case "ASSIGN_SECTOR":
        let sector = blocks.first { $0.id == c.sectorBlockId }
        return "Mueve «\(target?.name ?? "una piedra")» al sector «\(sector?.name ?? "?")»"
    case "POSITION_CORRECTION":
        let what = c.targetBlockId == nil ? "la ESCUELA entera"
                   : "«\(target?.name ?? c.name ?? "un elemento")»"
        if let la = c.proposedLat?.doubleValue, let lo = c.proposedLon?.doubleValue {
            let meters = Int(Geo.shared.haversineKm(lat1: c.lat, lon1: c.lon, lat2: la, lon2: lo) * 1000)
            return "Mueve \(what) unos \(meters) m"
        }
        return "Mueve \(what)"
    case "BOULDER":
        let vias = TopoParse.proposedVias(c.bloquesJson)
        let corrige = vias.filter { $0.targetLineId != nil }.count
        let nuevas = max(vias.count - corrige, 0)
        let esMuro = (c.geometry ?? "").uppercased() == "LINE"
        if target == nil {
            return (esMuro ? "Muro NUEVO" : "Piedra NUEVA") + " con \(vias.count) vía\(vias.count == 1 ? "" : "s")"
        }
        var parts: [String] = []
        if nuevas > 0 { parts.append("añade \(nuevas) vía\(nuevas == 1 ? "" : "s")") }
        if corrige > 0 { parts.append("corrige \(corrige)") }
        if parts.isEmpty { parts.append("cambios en el trazado/orden") }
        return (esMuro ? "Muro" : "Piedra") + " «\(target!.name)»: " + parts.joined(separator: " y ")
    default:
        return "Propuesta de tipo \(c.type)"
    }
}


/// Color por tipo de bloque (parking azul, zona verde, piedra terra).
func blockTypeColor(_ t: String) -> UIColor {
    switch t.uppercased() {
    case "PARKING": return UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    case "ZONE":    return UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    default:        return UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    }
}
func markerKindFor(_ t: String) -> MarkerKind {
    switch t.uppercased() { case "PARKING": return .parking; case "ZONE": return .zone; default: return .block }
}

/// Bloques existentes de la escuela como CONTEXTO (atenuados), para que el admin
/// vea dónde cae la propuesta y si pisa algo. Excluye la piedra/sector implicados
/// (van resaltados en `contributionMarkers`).
func contextMarkers(_ c: Contribution, blocks: [Block]) -> [CumbreMarker] {
    blocks.filter { $0.id != c.targetBlockId && $0.id != c.sectorBlockId }.map { b in
        CumbreMarker(id: "ctx-\(b.id)",
            coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
            title: b.name.isEmpty ? b.type : b.name, kind: markerKindFor(b.type),
            color: blockTypeColor(b.type).withAlphaComponent(0.5), name: b.name)
    }
}

/// Marcadores RESALTADOS de la propuesta (lo que cambia):
/// CORREGIR = ✕ viejo gris + ★ nuevo ámbar; ASIGNAR SECTOR = piedra ★ ámbar +
/// sector viejo ✕ gris + sector nuevo ★ verde; resto = ★ ámbar en la propuesta.
func contributionMarkers(_ c: Contribution, blocks: [Block] = []) -> [CumbreMarker] {
    let amber = UIColor(hex: 0xF59E0B)
    let gray = UIColor(white: 0.55, alpha: 1)
    let green = UIColor(red: 0.20, green: 0.55, blue: 0.30, alpha: 1)
    switch c.type.uppercased() {
    case "POSITION_CORRECTION":
        var ms = [CumbreMarker(id: "old", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                               title: "Actual", kind: .block, color: gray, name: "✕")]
        if let la = c.proposedLat?.doubleValue, let lo = c.proposedLon?.doubleValue {
            ms.append(CumbreMarker(id: "new", coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                                   title: "Nueva", kind: .block, color: amber, name: "★"))
        }
        return ms
    case "ASSIGN_SECTOR":
        let stone = blocks.first { $0.id == c.targetBlockId }
        let stoneCoord = stone.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
            ?? CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon)
        var ms = [CumbreMarker(id: "stone", coordinate: stoneCoord, title: "Piedra", kind: .block,
                               color: amber, name: stone.flatMap { $0.name.isEmpty ? nil : $0.name } ?? "PIEDRA")]
        if let oldId = stone?.sectorBlockId, let oldSec = blocks.first(where: { $0.id == oldId }) {
            ms.append(CumbreMarker(id: "oldsec",
                coordinate: CLLocationCoordinate2D(latitude: oldSec.lat, longitude: oldSec.lon),
                title: "Sector actual", kind: .zone, color: gray, name: "✕ \(oldSec.name)"))
        }
        if let newSec = blocks.first(where: { $0.id == c.sectorBlockId }) {
            ms.append(CumbreMarker(id: "newsec",
                coordinate: CLLocationCoordinate2D(latitude: newSec.lat, longitude: newSec.lon),
                title: "Sector nuevo", kind: .zone, color: green, name: "★ \(newSec.name)"))
        }
        return ms
    default:
        return [CumbreMarker(id: "p", coordinate: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
                             title: c.name ?? adminTypeLabel(c.type), kind: markerKindFor(c.type),
                             color: amber, name: c.name.flatMap { $0.isEmpty ? nil : $0 } ?? "★")]
    }
}

/// Polilíneas de un cambio de sector: piedra → sector viejo (gris) y → nuevo (verde).
func contributionPolylines(_ c: Contribution, blocks: [Block]) -> [CumbrePolyline] {
    guard c.type.uppercased() == "ASSIGN_SECTOR",
          let stone = blocks.first(where: { $0.id == c.targetBlockId }) else { return [] }
    let stoneCoord = CLLocationCoordinate2D(latitude: stone.lat, longitude: stone.lon)
    var lines: [CumbrePolyline] = []
    if let oldId = stone.sectorBlockId, let oldSec = blocks.first(where: { $0.id == oldId }) {
        lines.append(CumbrePolyline(id: "old",
            coordinates: [stoneCoord, CLLocationCoordinate2D(latitude: oldSec.lat, longitude: oldSec.lon)],
            color: UIColor(white: 0.55, alpha: 1), width: 3, alpha: 0.9))
    }
    if let newSec = blocks.first(where: { $0.id == c.sectorBlockId }) {
        lines.append(CumbrePolyline(id: "new",
            coordinates: [stoneCoord, CLLocationCoordinate2D(latitude: newSec.lat, longitude: newSec.lon)],
            color: UIColor(red: 0.20, green: 0.55, blue: 0.30, alpha: 1), width: 4, alpha: 1))
    }
    return lines
}

/// Mini-mapa (no interactivo) dentro de la card del admin: contexto + cambio.
/// Espejo del mini-mapa de ContributionCard.kt. Para interactuar, "VER EN MAPA".
struct ContributionMiniMap: View {
    let contribution: Contribution
    let blocks: [Block]
    @State private var style: MapStyleKind = .topo
    private var proposed: [CumbreMarker] { contributionMarkers(contribution, blocks: blocks) }
    var body: some View {
        MapLibreView(
            center: CLLocationCoordinate2D(latitude: contribution.lat, longitude: contribution.lon),
            zoom: 16, markers: contextMarkers(contribution, blocks: blocks) + proposed, style: style,
            fitToCoordinatesOnLoad: proposed.count >= 2 ? proposed.map { $0.coordinate } : [],
            polylines: contributionPolylines(contribution, blocks: blocks))
        .frame(height: 200)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .allowsHitTesting(false)
    }
}

/// Mapa a pantalla completa de una propuesta (espejo de FullScreenMapDialog).
/// Carga los bloques de la escuela: pinta los existentes de CONTEXTO (atenuados)
/// y resalta lo que cambia (✕ viejo / ★ nuevo, o piedra + sector viejo/nuevo).
struct ContributionMapSheet: View {
    let contribution: Contribution
    @Environment(\.dismiss) private var dismiss
    @State private var style: MapStyleKind = .topo
    @State private var blocks: [Block] = []
    @State private var loaded = false

    private var proposed: [CumbreMarker] { contributionMarkers(contribution, blocks: blocks) }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                if loaded {
                    MapLibreView(
                        center: CLLocationCoordinate2D(latitude: contribution.lat, longitude: contribution.lon),
                        zoom: 16, markers: contextMarkers(contribution, blocks: blocks) + proposed, style: style,
                        // Encuadra los marcadores clave de la propuesta al cargar
                        // (✕/★ o piedra+sectores) para que todos entren en pantalla.
                        fitToCoordinatesOnLoad: proposed.count >= 2 ? proposed.map { $0.coordinate } : [],
                        polylines: contributionPolylines(contribution, blocks: blocks))
                    MapStyleChips(selection: $style)
                } else {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(adminTypeLabel(contribution.type))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task {
            blocks = (try? await AppDependencies.shared.container.getBlocks
                .invoke(schoolId: contribution.schoolId)) ?? []
            loaded = true
        }
    }
}

struct ReviewButtons: View {
    let busy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            if busy {
                ProgressView().frame(maxWidth: .infinity)
            } else {
                Button(action: onReject) {
                    Text(NSLocalizedString("admin_reject", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.bad)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                }.buttonStyle(.plain)
                Button(action: onApprove) {
                    Text(NSLocalizedString("admin_approve", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(Cumbre.ok)
                }.buttonStyle(.plain)
            }
        }
        .padding(.top, 4)
    }
}

/// Hoja para escribir el motivo del rechazo (opcional).
struct RejectReasonSheet: View {
    let onConfirm: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var reason = ""
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Motivo del rechazo (opcional)").eyebrow()
                TextField("ej: coordenadas incorrectas", text: $reason, axis: .vertical)
                    .lineLimit(2...5).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                    .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                Button {
                    onConfirm(reason.trimmingCharacters(in: .whitespaces).isEmpty ? nil : reason)
                    dismiss()
                } label: {
                    Text("RECHAZAR PROPUESTA").font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14).background(Cumbre.bad)
                }.buttonStyle(.plain)
                Spacer()
            }
            .padding(16)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Rechazar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_cancel", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) }
            }
        }
    }
}

enum AdminTab: String, CaseIterable {
    case propuestas = "PROPUESTAS"
    case denuncias = "DENUNCIAS"
    case gestionar = "GESTIONAR"
    case stats = "STATS"
    case actividad = "ACTIVIDAD"
    case push = "PUSH"
}
