import SwiftUI
import Shared
import CoreLocation

// Resumen y mapa de una aportación (marcadores, encuadre, mapa mini y a pantalla completa) y botones de revisar.

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

/// Coordenadas para encuadrar el mapa de una propuesta: si es MURO, incluye el
/// TRAZADO (a zoom fijo un muro de metros queda sub-píxel bajo el marcador y
/// parece "solo el punto"). Espejo del fix de ContributionCard.kt (Android).
/// Vacío si hay menos de 2 puntos (MapLibreView solo encuadra con ≥2).
func contributionFitCoords(_ c: Contribution, _ proposed: [CumbreMarker]) -> [CLLocationCoordinate2D] {
    let wall = (c.geometry ?? "").uppercased() == "LINE" ? parseWallPath(c.path) : []
    let all = wall + proposed.map { $0.coordinate }
    return all.count >= 2 ? all : []
}

/// Polilíneas de una propuesta: el TRAZADO del muro (LINE) o un cambio de sector.
func contributionPolylines(_ c: Contribution, blocks: [Block]) -> [CumbrePolyline] {
    // Muro (geometry LINE): trazado viejo (gris, si corrige) + nuevo (terra). Antes
    // NO se dibujaba en la revisión de admin en iOS — solo se veía el punto.
    if (c.geometry ?? "").uppercased() == "LINE" {
        var wallLines: [CumbrePolyline] = []
        if let tb = c.targetBlockId.flatMap({ id in blocks.first(where: { $0.id == id }) }) {
            let old = parseWallPath(tb.path)
            if old.count >= 2 {
                wallLines.append(CumbrePolyline(id: "wallOld", coordinates: old,
                    color: UIColor(white: 0.54, alpha: 1), width: 5, alpha: 0.6))
            }
        }
        let new = parseWallPath(c.path)
        if new.count >= 2 {
            wallLines.append(CumbrePolyline(id: "wallNew", coordinates: new,
                color: UIColor(Cumbre.terra), width: 5, alpha: 1))
        }
        return wallLines
    }
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
            fitToCoordinatesOnLoad: contributionFitCoords(contribution, proposed),
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
                        // Encuadra lo clave de la propuesta (✕/★, piedra+sectores, o el
                        // TRAZADO del muro) para que todo entre en pantalla.
                        fitToCoordinatesOnLoad: contributionFitCoords(contribution, proposed),
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
