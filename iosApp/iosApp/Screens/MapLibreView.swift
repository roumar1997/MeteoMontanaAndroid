import SwiftUI
import MapLibre
import CoreLocation

// Equivalente iOS del mapa MapLibre de Android. Envuelve MLNMapView en un
// UIViewRepresentable. Soporta dos estilos de tiles (topográfico OpenTopoMap /
// satélite Esri World Imagery, igual que Android) y marcadores con FORMA por
// tipo (parking cuadrado "P", zona pin "Z", piedra polígono de roca con su
// nombre, escuela triángulo, usuario punto azul) y, en la lista, un diamante
// coloreado por score con el número y el nombre debajo.

// MARK: - Estilo de tiles (topo / satélite)

enum MapStyleKind: String, CaseIterable {
    case topo
    case satellite

    var label: String { self == .topo ? "Topográfico" : "Satélite" }

    /// JSON de estilo MapLibre v8 escrito a un fichero temporal (MLNMapView pide
    /// una URL). Espejo exacto de las fuentes que usa Android.
    func styleURL() -> URL {
        let json: String
        switch self {
        case .topo:
            json = """
            { "version": 8, "sources": { "topo": { "type": "raster",
              "tiles": ["https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],
              "tileSize": 256, "maxzoom": 17, "attribution": "© OpenTopoMap (CC-BY-SA)" } },
              "layers": [{ "id": "topo", "type": "raster", "source": "topo" }] }
            """
        case .satellite:
            json = """
            { "version": 8, "sources": { "sat": { "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256, "attribution": "Tiles © Esri" } },
              "layers": [{ "id": "sat", "type": "raster", "source": "sat" }] }
            """
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("cumbre-style-\(rawValue).json")
        try? json.data(using: .utf8)?.write(to: url)
        return url
    }
}

// MARK: - Marcador

enum MarkerKind {
    case parking      // cuadrado azul "P"
    case zone         // pin verde "Z"
    case block        // polígono de roca terra con nombre corto
    case school       // triángulo oscuro
    case user         // punto azul con halo (mi ubicación)
    case score        // diamante coloreado por score (lista de escuelas)
}

/// Marcador del mapa. `kind` decide la forma; `label`, `score` y `name`
/// alimentan el dibujo.
struct CumbreMarker: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let title: String
    var subtitle: String? = nil
    var kind: MarkerKind = .block
    /// Color del relleno (por tipo / por score).
    var color: UIColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    /// Score para el diamante de la lista (nil = sin datos → punto).
    var score: Int? = nil
    /// Nombre a pintar debajo del diamante (solo lista, con zoom suficiente).
    var name: String? = nil
    /// Si se pinta el nombre debajo (depende del zoom en la lista).
    var showName: Bool = false

    /// Firma estable para cachear/diff (incluye todo lo que afecta al dibujo).
    var drawSignature: String {
        "\(id)|\(kindKey)|\(color.hexKey)|\(score ?? -1)|\(showName ? (name ?? "") : "")"
    }
    private var kindKey: String {
        switch kind {
        case .parking: return "p"; case .zone: return "z"; case .block: return "b"
        case .school: return "s"; case .user: return "u"; case .score: return "d"
        }
    }
}

struct MapLibreView: UIViewRepresentable {
    let center: CLLocationCoordinate2D
    var zoom: Double = 12
    var markers: [CumbreMarker] = []
    var style: MapStyleKind = .topo
    /// Se llama al tocar un marcador (por id).
    var onTapMarker: ((String) -> Void)? = nil
    /// Si está presente, un tap en el mapa (no en un marcador) devuelve la
    /// coordenada — para fijar la posición al proponer una mejora.
    var onMapTap: ((CLLocationCoordinate2D) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero)
        map.styleURL = style.styleURL()
        map.setCenter(center, zoomLevel: zoom, animated: false)
        map.delegate = context.coordinator
        let tap = UITapGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.handleTap(_:)))
        tap.cancelsTouchesInView = false
        map.addGestureRecognizer(tap)
        context.coordinator.mapView = map
        context.coordinator.currentStyle = style
        context.coordinator.applyMarkersIfChanged(to: map, markers: markers, force: true)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.parent = self
        // Cambiar estilo de tiles si el usuario tocó topo/satélite.
        if context.coordinator.currentStyle != style {
            context.coordinator.currentStyle = style
            map.styleURL = style.styleURL()
            // Tras recargar el estilo re-aplicamos los marcadores.
            context.coordinator.applyMarkersIfChanged(to: map, markers: markers, force: true)
        } else {
            // Solo re-sincroniza si los marcadores cambian de verdad (evita el
            // flicker y que el mapa se "reinicie" en cada update de SwiftUI).
            context.coordinator.applyMarkersIfChanged(to: map, markers: markers, force: false)
        }
        // OJO: no re-centramos aquí a propósito — re-centrar en cada update era
        // la causa de que el mapa de la lista se "perdiera" al cambiar filtros.
        // El encuadre programático se hace vía fitBounds(...) cuando procede.
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
        weak var mapView: MLNMapView?
        var currentStyle: MapStyleKind = .topo
        private var lastSignature: String = ""
        /// Mapa annotation→marker para resolver taps sin depender del título.
        private var byAnnotation: [ObjectIdentifier: CumbreMarker] = [:]

        init(_ parent: MapLibreView) { self.parent = parent }

        @objc func handleTap(_ g: UITapGestureRecognizer) {
            guard let onTap = parent.onMapTap, let map = mapView else { return }
            let point = g.location(in: map)
            let coord = map.convert(point, toCoordinateFrom: map)
            onTap(coord)
        }

        func applyMarkersIfChanged(to map: MLNMapView, markers: [CumbreMarker], force: Bool) {
            let sig = markers.map { $0.drawSignature }.joined(separator: ";")
            if !force && sig == lastSignature { return }
            lastSignature = sig
            if let existing = map.annotations, !existing.isEmpty { map.removeAnnotations(existing) }
            byAnnotation.removeAll()
            for m in markers {
                let a = CumbreAnnotation()
                a.coordinate = m.coordinate
                a.title = m.title
                a.subtitle = m.subtitle
                a.marker = m
                byAnnotation[ObjectIdentifier(a)] = m
                map.addAnnotation(a)
            }
        }

        // Imagen del marcador por tipo (cacheada por firma de dibujo).
        func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
            guard let a = annotation as? CumbreAnnotation else { return nil }
            let key = a.marker.drawSignature
            if let cached = mapView.dequeueReusableAnnotationImage(withIdentifier: key) { return cached }
            return MLNAnnotationImage(image: MarkerRenderer.image(for: a.marker),
                                      reuseIdentifier: key)
        }

        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            if let a = annotation as? CumbreAnnotation { parent.onTapMarker?(a.marker.id) }
            mapView.deselectAnnotation(annotation, animated: false)
        }

        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool { false }
    }
}

/// Annotation con el marcador adjunto (evita resolver por título, que colisiona
/// cuando dos elementos comparten nombre).
final class CumbreAnnotation: MLNPointAnnotation {
    var marker: CumbreMarker!
}

/// Chips "Topográfico / Satélite" para superponer sobre cualquier mapa (espejo
/// de los chips de Android). El padre mantiene el @State y lo pasa a MapLibreView.
struct MapStyleChips: View {
    @Binding var selection: MapStyleKind
    var body: some View {
        HStack(spacing: 6) {
            ForEach(MapStyleKind.allCases, id: \.self) { kind in
                let on = selection == kind
                Button { selection = kind } label: {
                    Text(kind.label)
                        .font(Cumbre.mono(11, .bold)).tracking(0.4)
                        .foregroundStyle(on ? Color.black : .white)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(on ? Color.white : Color.black.opacity(0.55))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(8)
    }
}

extension UIColor {
    /// Clave estable por color.
    var hexKey: String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
