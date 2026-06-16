import SwiftUI
import MapLibre
import CoreLocation

// Equivalente iOS del mapa MapLibre de Android. Envuelve MLNMapView en un
// UIViewRepresentable con tiles topográficos (OpenTopoMap, sin API key) y
// marcadores. Punto de partida reutilizable para el mapa de escuela, el panel
// de la lista y (con más trabajo) proponer/topo.

/// Marcador del mapa.
struct CumbreMarker: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let title: String
    var subtitle: String? = nil
    /// Color del pin (por tipo). nil = terracota por defecto.
    var color: UIColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1) // ~Terra
}

struct MapLibreView: UIViewRepresentable {
    let center: CLLocationCoordinate2D
    var zoom: Double = 12
    var markers: [CumbreMarker] = []
    /// Se llama al tocar un marcador (por id).
    var onTapMarker: ((String) -> Void)? = nil
    /// Si está presente, un tap en el mapa (no en un marcador) devuelve la
    /// coordenada — para fijar la posición al proponer una mejora.
    var onMapTap: ((CLLocationCoordinate2D) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero)
        map.styleURL = Self.topoStyleURL()
        map.setCenter(center, zoomLevel: zoom, animated: false)
        map.delegate = context.coordinator
        map.logoView.isHidden = false
        // Tap para fijar coordenada (no interfiere con la selección de markers).
        let tap = UITapGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.handleTap(_:)))
        tap.cancelsTouchesInView = false
        map.addGestureRecognizer(tap)
        context.coordinator.mapView = map
        applyMarkers(to: map)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.parent = self
        // Re-sincroniza marcadores si cambian.
        if let existing = map.annotations, !existing.isEmpty { map.removeAnnotations(existing) }
        applyMarkers(to: map)
        map.setCenter(center, zoomLevel: zoom, animated: false)
    }

    private func applyMarkers(to map: MLNMapView) {
        for m in markers {
            let a = MLNPointAnnotation()
            a.coordinate = m.coordinate
            a.title = m.title
            a.subtitle = m.subtitle
            map.addAnnotation(a)
        }
    }

    /// Estilo raster con tiles topográficos de OpenTopoMap (sin clave). Se escribe
    /// a un fichero temporal y se sirve por file URL (MLNMapView pide una URL).
    static func topoStyleURL() -> URL {
        let json = """
        {
          "version": 8,
          "sources": {
            "topo": {
              "type": "raster",
              "tiles": ["https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 17,
              "attribution": "© OpenTopoMap (CC-BY-SA)"
            }
          },
          "layers": [{ "id": "topo", "type": "raster", "source": "topo" }]
        }
        """
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("cumbre-topo-style.json")
        try? json.data(using: .utf8)?.write(to: url)
        return url
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var parent: MapLibreView
        weak var mapView: MLNMapView?
        init(_ parent: MapLibreView) { self.parent = parent }

        @objc func handleTap(_ g: UITapGestureRecognizer) {
            guard let onTap = parent.onMapTap, let map = mapView else { return }
            let point = g.location(in: map)
            let coord = map.convert(point, toCoordinateFrom: map)
            onTap(coord)
        }

        // Pin coloreado por tipo (busca el marcador por título y genera un punto
        // del color correspondiente, cacheado por color).
        func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
            guard let title = annotation.title ?? nil,
                  let marker = parent.markers.first(where: { $0.title == title }) else { return nil }
            let hex = marker.color.hexKey
            if let cached = mapView.dequeueReusableAnnotationImage(withIdentifier: hex) { return cached }
            return MLNAnnotationImage(image: Coordinator.dot(color: marker.color), reuseIdentifier: hex)
        }

        /// Punto circular con borde blanco como imagen de marcador.
        static func dot(color: UIColor, size: CGFloat = 22) -> UIImage {
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
            return renderer.image { ctx in
                let rect = CGRect(x: 1, y: 1, width: size - 2, height: size - 2)
                ctx.cgContext.setFillColor(color.cgColor)
                ctx.cgContext.fillEllipse(in: rect)
                ctx.cgContext.setStrokeColor(UIColor.white.cgColor)
                ctx.cgContext.setLineWidth(2)
                ctx.cgContext.strokeEllipse(in: rect)
            }
        }

        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            if let title = annotation.title ?? nil,
               let marker = parent.markers.first(where: { $0.title == title }) {
                parent.onTapMarker?(marker.id)
            }
            mapView.deselectAnnotation(annotation, animated: false)
        }

        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool { true }
    }
}

private extension UIColor {
    /// Clave estable por color (para cachear la imagen del marcador).
    var hexKey: String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "dot-%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
