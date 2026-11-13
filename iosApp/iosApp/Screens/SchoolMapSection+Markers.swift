import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Mapa de una escuela: muros, marcadores, filtros de estilo y grado, leyenda e ir a un parking.

extension SchoolMapSection {
    var wallPolylines: [CumbrePolyline] {
        vm.blocks.compactMap { b in
            // Solo PIEDRAS: un parking/zona con path corrupto no debe estirarse
            // en una línea (se veía azul, color del tipo).
            guard b.type.uppercased() == "BLOCK", b.geometry.uppercased() == "LINE" else { return nil }
            if vm.hiddenTypes.contains("BLOCK") { return nil }
            if let sid = b.sectorBlockId, vm.collapsedSectors.contains(sid) { return nil }
            // Filtro de orientación: los muros se ocultan igual que sus pines.
            if let f = orientationFilter, blockOrientations[b.id] != f { return nil }
            let pts = parseWallPath(b.path)
            guard pts.count >= 2 else { return nil }
            return CumbrePolyline(id: "wall-\(b.id)", coordinates: pts, color: blockColor, width: 5)
        }
    }

    /// Coordenada del marcador de una piedra: si es MURO, el centro de la
    /// polilínea (no la esquina donde se fijó al crearla → salía en un lateral).
    func blockMarkerCoord(_ b: Block) -> CLLocationCoordinate2D {
        if b.geometry.uppercased() == "LINE" {
            let pts = parseWallPath(b.path)
            if pts.count >= 2 { return pts[pts.count / 2] }
        }
        return CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
    }

    /// Resultado del filtro de grado sobre las piedras cargadas. Espejo de
    /// `filterBlocksByGrades` en shared (GradeFilter.kt).
    var gradeFilter: GradeFilterResult {
        filterBlocksByGrades(vm.blocks, selectedGrades: selectedGrades)
    }

    /// ¿Hay filtro de grado puesto? Si no, nada se atenúa.
    var gradeFilterActive: Bool { !selectedGrades.isEmpty }

    /// Solo tiene sentido el filtro Vía/Bloque si la escuela es de AMBOS
    /// estilos a la vez (feature 2: school.style = "Vía,Bloque").
    var schoolHasBothStyles: Bool {
        Set(school.style?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } ?? []).count > 1
    }

    var styleFilterRow: some View {
        HStack(spacing: 8) {
            ForEach(["Vía", "Bloque"], id: \.self) { opt in
                let active = styleFilter.contains(opt)
                Button {
                    if active { styleFilter.remove(opt) } else { styleFilter.insert(opt) }
                } label: {
                    Text(L(opt).uppercased()).font(Cumbre.mono(11, .bold)).tracking(0.5)
                        .foregroundStyle(active ? .white : Cumbre.ink2)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(active ? Cumbre.terra : Cumbre.paper)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        // Centrado y con aire arriba y abajo (Álvaro, 2026-11-13: iba pegado a la izquierda).
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, 16).padding(.vertical, 6)
    }

    /// "ROUTE" en el backend = estilo "Vía"; "BOULDER" = "Bloque".
    func styleLabel(_ discipline: String) -> String {
        discipline.uppercased() == "ROUTE" ? "Vía" : "Bloque"
    }

    /// ¿Esta piedra/sector pasa el filtro Vía/Bloque? Vacío = sin filtrar.
    /// Un sector sin piedras dentro (sectorDisciplines vacío) siempre se ve —
    /// no hay disciplina que comparar todavía (evita que desaparezca).
    func matchesStyleFilter(_ b: Block) -> Bool {
        if styleFilter.isEmpty { return true }
        switch b.type.uppercased() {
        case "BLOCK": return styleFilter.contains(styleLabel(b.discipline))
        case "ZONE":
            let disciplines = b.sectorDisciplines ?? []
            if disciplines.isEmpty { return true }
            return disciplines.contains { styleFilter.contains(styleLabel($0)) }
        default: return true
        }
    }

    /// Color del pin de una piedra, atenuado si el filtro de grado está puesto
    /// y esa piedra no tiene ninguna vía en rango. Se ATENÚA, no se oculta:
    /// así se conserva el contexto del sector (decisión de diseño §7.3).
    func blockColorFiltered(_ b: Block) -> UIColor {
        let base = color(for: b.type)
        guard gradeFilterActive, b.type.uppercased() == "BLOCK",
              !gradeFilter.matchingBlockIds.contains(b.id) else { return base }
        return base.withAlphaComponent(0.35)
    }

    var markers: [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in vm.blocks {
            // Capa apagada (botonera lateral) → no se pinta.
            if vm.hiddenTypes.contains(b.type.uppercased()) { continue }
            // Piedra oculta si pertenece a un sector colapsado.
            if b.type.uppercased() == "BLOCK", let sid = b.sectorBlockId, vm.collapsedSectors.contains(sid) {
                continue
            }
            // Filtro de orientación activo: solo piedras con ESE consenso.
            if b.type.uppercased() == "BLOCK", let f = orientationFilter,
               blockOrientations[b.id] != f {
                continue
            }
            // Filtro Vía/Bloque (solo en escuelas con ambos estilos).
            if !matchesStyleFilter(b) { continue }
            // Zona colapsada: muestra cuántas piedras tiene ocultas.
            let collapsed = b.type.uppercased() == "ZONE" && vm.collapsedSectors.contains(b.id)
            let hidden = collapsed ? vm.blocks.filter { $0.sectorBlockId == b.id }.count : 0
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: blockMarkerCoord(b),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type),
                color: blockColorFiltered(b),
                name: collapsed && hidden > 0 ? "\(b.name) (+\(hidden))" : b.name,
                // Nombre del sector visible al acercar (sin tener que pulsarlo).
                showName: b.type.uppercased() == "ZONE" && !b.name.isEmpty && mapZoom >= 13.5,
                // Piedra: crashpad (bloque) o mosquetón (vía) según su modalidad.
                discipline: b.type.uppercased() == "BLOCK" ? b.discipline : nil))
        }
        // Orden de pintado: piedras primero → sectores/parkings/escuela ENCIMA
        // (no quedan tapados por los pines de piedra).
        ms.sort { a, b in
            func rank(_ k: MarkerKind) -> Int {
                switch k { case .block: return 0; case .parking: return 1
                case .zone: return 2; case .school: return 3; default: return 4 }
            }
            return rank(a.kind) < rank(b.kind)
        }
        // Mi ubicación (punto azul) para orientarme en el sector.
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user,
                                   score: headingProvider.heading))
        }
        // Punto donde se hizo la foto, pendiente de confirmar. Sin marcador, la
        // pregunta "¿es el sitio?" no se puede responder: no se ve dónde cae.
        if let f = confirmandoFoto {
            ms.append(CumbreMarker(
                id: "__FOTO__", coordinate: f, title: L("Aquí se hizo la foto"),
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        // Sitio nuevo elegido con «Mover» (admin), aún sin guardar.
        if let p = movePending {
            ms.append(CumbreMarker(
                id: "__MOVE__", coordinate: p, title: L("Nueva posición"),
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        // Fantasma de la nueva posición al corregir.
        if let nw = flow.corrNew {
            ms.append(CumbreMarker(
                id: "__GHOST__", coordinate: nw, title: L("Nueva posición"),
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        return ms
    }

    func markerKind(for type: String) -> MarkerKind {
        switch type.uppercased() {
        case "PARKING": return .parking
        case "ZONE": return .zone
        default: return .block
        }
    }

    /// Contexto de solo lectura para el mapa de trazar muro: escuela + todas las
    /// piedras/sectores/parkings + mi ubicación. Sin overlays del trazo ni
    /// fantasmas de corrección (eso lo pinta WallTraceSheet aparte).
    var traceContextMarkers: [CumbreMarker] {
        var ms: [CumbreMarker] = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in vm.blocks {
            ms.append(CumbreMarker(
                id: b.id, coordinate: blockMarkerCoord(b),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type), color: color(for: b.type), name: b.name,
                discipline: b.type.uppercased() == "BLOCK" ? b.discipline : nil))
        }
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user,
                                   score: headingProvider.heading))
        }
        return ms
    }

    var legend: some View {
        HStack(spacing: 14) {
            legendItem(L("Parking"), Color(parkingColor))
            legendItem(L("Piedra"), Color(blockColor))
            legendItem(L("Zona"), Color(zoneColor))
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }
    func legendItem(_ t: String, _ c: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(c).frame(width: 9, height: 9).overlay(Circle().stroke(.white, lineWidth: 1))
            Text(t).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
        }
    }

    // Lo importante para llegar: desde qué parking se va andando. Orden
    // alfabético (NO por cercanía — debe tener sentido también viéndolo desde
    // casa, lejos de la escuela); la distancia es solo dato informativo. Tocar
    // uno centra el mapa en esa zona (para ver qué sectores/piedras hay
    // alrededor) y abre su ficha, que ya tiene "CÓMO LLEGAR".
    /// Vuela a la "zona" de un parking: encuadra parking + sectores/piedras a
    /// ≤800 m (expandiendo colapsados) — vista de ~1,5-2 km. Sin nada cerca,
    /// zoom equivalente. Espejo de flyToParkingZone de Android.
    func flyToParkingZone(_ p: Block) {
        let near = vm.blocks.filter { b in
            b.id != p.id &&
            Geo.shared.haversineKm(lat1: p.lat, lon1: p.lon, lat2: b.lat, lon2: b.lon) <= 0.8
        }
        for b in near {
            if b.type.uppercased() == "ZONE" { vm.collapsedSectors.remove(b.id) }
            if let sid = b.sectorBlockId { vm.collapsedSectors.remove(sid) }
        }
        // Centrar EN el parking pulsado (no en el centroide del parking + cercanos,
        // que dejaba el parking descentrado). Paridad con Android.
        focusFit = []
        focusCoord = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        focusToken += 1
    }
}
