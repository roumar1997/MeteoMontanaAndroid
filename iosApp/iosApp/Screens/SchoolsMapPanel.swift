import SwiftUI
import Shared
import CoreLocation

// Mapa de Escuelas: el panel desplegable y la tarjeta de la escuela tocada.

/// Panel (se abre con el botón del mapa de la fila Escuelas / Vías-Bloques) con todas las escuelas filtradas como marcadores
/// coloreados por score (tap → detalle). Espejo de SchoolsMapPanel.kt.
struct MapToggleAndPanel: View {
    @ObservedObject var vm: SchoolListViewModel
    let onOpen: (School) -> Void
    @State private var popup: School?
    // Satélite por defecto, paridad con el mapa de detalle de escuela
    // (Álvaro, 2026-09-01: "que se abra en satélite por defecto").
    @State private var mapStyle: MapStyleKind = .satellite
    @State private var zoom: Double = 8
    @State private var fullscreenMap = false

    private func mapBox(height: CGFloat, isFullscreen: Bool = false) -> some View {
        ZStack(alignment: .topLeading) {
            MapLibreView(center: center, zoom: vm.userLat != nil ? 8 : 6,
                         markers: markers, style: mapStyle,
                         autoFitToMarkers: true,
                         refitOnAnyChange: true,
                         onZoomChange: { zoom = $0 },
                         onTapMarker: { id in
                             popup = vm.filtered.first { $0.id == id }
                         },
                         // A pantalla completa los botones de arriba bajan más
                         // (bajo la isla/notch) — la brújula tiene que bajar con
                         // ellos o se solapan (Álvaro, 2026-09-01).
                         compassTopMargin: isFullscreen ? 106 : 56)
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
            // Ampliar / salir de pantalla completa — arriba a la izquierda,
            // misma posición y forma que en el detalle de escuela. En
            // pantalla completa se baja bajo la isla/notch (antes quedaba
            // debajo del reloj y no se podía pulsar — Álvaro, 2026-09-01).
            VStack {
                HStack {
                    Button { fullscreenMap.toggle() } label: {
                        Image(systemName: fullscreenMap
                              ? "arrow.down.right.and.arrow.up.left"
                              : "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    // Topo/satélite de un toque — mismo botón que el detalle
                    // de escuela.
                    Button {
                        mapStyle = (mapStyle == .satellite) ? .topo : .satellite
                    } label: {
                        Image(systemName: "square.3.layers.3d")
                            .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    // Botón de ubicación EN EL MAPA (Álvaro, 2026-10-01: "aún
                    // no he encontrado el botón") — el aviso de Filtros sigue
                    // existiendo, pero aquí es donde de verdad se espera un
                    // botón de "localízame" en cualquier mapa.
                    Button { vm.requestLocation() } label: {
                        Image(systemName: vm.needsLocationPermission ? "location.slash" : "location.fill")
                            .font(.system(size: 15)).foregroundStyle(vm.needsLocationPermission ? Cumbre.ink : Cumbre.terra)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, isFullscreen ? 50 : 0)
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
            .allowsHitTesting(true)
        }
        // Altura fija SOLO para la tarjeta inline (300pt); a pantalla
        // completa se deja que el ZStack tome el espacio que le proponga el
        // fullScreenCover (ya es toda la pantalla) — forzarla ANTES con
        // UIScreen.main.bounds.height y añadir DESPUÉS la barra de abajo con
        // safeAreaInset sumaba las dos alturas y desbordaba la pantalla: la
        // fila ESTILO quedaba cortada y los botones de arriba se dejaban de
        // poder pulsar bien (Álvaro, 2026-09-01: "ahora va mucho peor").
        .frame(height: isFullscreen ? nil : height)
        // A pantalla completa, hay demasiadas escuelas para verlas bien sin
        // filtrar — DISTANCIA y ESTILO en una barra fija ABAJO (no un panel
        // lateral que tapaba el mapa). safeAreaInset coloca la barra POR
        // ENCIMA del indicador de inicio y RESERVA su alto (el mapa no
        // desborda la pantalla).
        .safeAreaInset(edge: .bottom) {
            if isFullscreen { fullscreenFilters }
        }
        // Popup al tocar un marcador: nombre, score, tags, CÓMO LLEGAR + VER
        // DETALLE (espejo de SchoolsMapPanel.kt). Colgado de ESTA vista (mapBox)
        // y no del body de fuera: a pantalla completa el mapa vive dentro de un
        // fullScreenCover, una presentación aparte — un .sheet colgado de la
        // vista de DETRÁS del cover no puede aparecer mientras el cover está
        // abierto (iOS lo deja pendiente y solo lo muestra al cerrar el cover,
        // con el valor de `popup` que hubiera en ESE momento: por eso al pulsar
        // una escuela en pantalla completa "no pasaba nada" y al salir aparecía
        // otra). Mismo patrón que ya usa mapArea en SchoolMapSection.swift.
        .sheet(item: $popup) { s in
            SchoolMapPopup(school: s, score: vm.scores[s.id].map { Int($0.todayScore) }) {
                popup = nil; onOpen(s)
            }
            .presentationDetents([.height(280)])
        }
    }

    private var fullscreenFilters: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("DIST.").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                    ForEach(SchoolListViewModel.distanceOptions, id: \.self) { d in
                        filterPill(
                            label: d == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int(d!)) km",
                            selected: d == vm.maxDistanceKm) { vm.maxDistanceKm = d }
                    }
                }
            }
            HStack(spacing: 6) {
                Text("ESTILO").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                ForEach([String?.none] + vm.styles.map { Optional($0) }, id: \.self) { s in
                    filterPill(
                        label: s ?? NSLocalizedString("schools_filter_all", comment: ""),
                        selected: s == vm.style) { vm.style = s }
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        // Material de cristal nativo (blur real), no un color plano con
        // opacidad — Álvaro, 2026-09-01: "no está en liquid glass".
        .background(.ultraThinMaterial)
    }

    private func filterPill(label: String, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(Cumbre.mono(11, .bold))
                .foregroundStyle(selected ? .white : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(selected ? Cumbre.terraFill : Color.clear,
                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                    .stroke(selected ? Color.clear : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    var body: some View {
        VStack(spacing: 0) {
            // !fullscreenMap: si no, esta tarjeta se queda montada DETRÁS del
            // fullScreenCover de abajo mientras dura pantalla completa — dos
            // mapBox vivos a la vez, cada uno con su propio .sheet(item:
            // $popup) colgando del MISMO estado. Al tocar un marcador en
            // pantalla completa, los dos intentaban presentar el aviso a la
            // vez y SwiftUI resolvía el conflicto cerrando el fullScreenCover
            // para que el de detrás (el único no cubierto) pudiera mostrarlo
            // (Álvaro, 2026-09-30). Mismo guardado que ya usa mapArea en
            // SchoolMapSection.swift.
            if vm.showMap && !fullscreenMap {
                mapBox(height: 300)
                Divider().overlay(Cumbre.rule)
            }
        }
        .fullScreenCover(isPresented: $fullscreenMap) {
            ZStack {
                Cumbre.bg.ignoresSafeArea()
                mapBox(height: UIScreen.main.bounds.height, isFullscreen: true)
            }
        }
        // El popup de la escuela pulsada ahora cuelga de mapBox (ver ahí el
        // porqué) — a pantalla completa mapBox vive dentro del fullScreenCover
        // de arriba, así que su propio .sheet se presenta en ese contexto.
    }

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        // Punto azul de mi ubicación (confirma que se cogió la ubicación).
        if let la = vm.userLat, let lo = vm.userLon {
            ms.append(CumbreMarker(
                id: "__USER__",
                coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                title: "", kind: .user))
        }
        for s in vm.filtered.prefix(200) {
            let score = vm.scores[s.id].map { Int($0.todayScore) }
            ms.append(CumbreMarker(
                id: s.id,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                title: s.name,
                subtitle: score.map { "\($0)/100" },
                kind: .score,
                color: UIColor(score.map { Cumbre.score($0) } ?? Cumbre.rule),
                score: score,
                name: s.name,
                showName: zoom >= 8.5))
        }
        return ms
    }

    private var center: CLLocationCoordinate2D {
        if let la = vm.userLat, let lo = vm.userLon {
            return CLLocationCoordinate2D(latitude: la, longitude: lo)
        }
        let pts = vm.filtered
        if pts.isEmpty { return CLLocationCoordinate2D(latitude: 40.2, longitude: -3.7) }
        let lat = pts.map { $0.lat }.reduce(0, +) / Double(pts.count)
        let lon = pts.map { $0.lon }.reduce(0, +) / Double(pts.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

/// Popup de una escuela al tocar su marcador en el panel de mapa de la lista.
/// Nombre + score + tags + "CÓMO LLEGAR" y "VER DETALLE" (espejo de SchoolsMapPanel).
struct SchoolMapPopup: View {
    let school: School
    let score: Int?
    let onDetail: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                if let s = score {
                    // Chip redondeado (estilo mini-ficha, adiós al cuadrado duro).
                    VStack(spacing: 0) {
                        Text("\(s)").font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.score(s))
                        Text(Cumbre.scoreLabel(s)).font(.system(size: 8, weight: .bold)).foregroundStyle(Cumbre.score(s))
                    }
                    .frame(width: 60, height: 60)
                    .background(Cumbre.score(s).opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.score(s), lineWidth: 1.5))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(school.name).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                    Text(tags).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            HStack(spacing: 10) {
                DirectionsButton(lat: school.lat, lon: school.lon, label: school.name)
                Button(action: onDetail) {
                    Text("VER DETALLE ▸").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Cumbre.terraFill)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }.buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private var tags: String {
        [school.rockType.map { rockLabel($0).uppercased() }, school.region.map(regionLabel), school.style.map(styleLabel)]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
    }
}
