import Foundation

// Estilos de tiles del mapa (sacado de MapLibreView.swift para que ese fichero no pase de 600 líneas).

// MARK: - Estilo de tiles (topo / satélite)

enum MapStyleKind: String, CaseIterable {
    case topo
    case satellite
    /// Papel claro (OpenStreetMap) y oscuro (CARTO): el mapa de rocódromos, igual que Android (MapStyles.osmPaper/darkPaper).
    case osmPaper
    case darkPaper

    /// Los chips «Topográfico / Satélite» solo ofrecen estos dos.
    static var allCases: [MapStyleKind] { [.topo, .satellite] }

    var label: String { self == .satellite ? L("Satélite") : L("Topográfico") }

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
              "layers": [{ "id": "bg", "type": "background", "paint": { "background-color": "#F4F1E9" } }, { "id": "topo", "type": "raster", "source": "topo" }] }
            """
        case .osmPaper:
            json = Self.rasterStyle(id: "osm", bg: "#F4F1E9", maxZoom: 19, attribution: "© OpenStreetMap",
                                    tiles: ["a", "b", "c"].map { "https://\($0).tile.openstreetmap.org/{z}/{x}/{y}.png" })
        case .darkPaper:
            json = Self.rasterStyle(id: "dark", bg: "#1C1B18", maxZoom: 20, attribution: "© CARTO © OpenStreetMap",
                                    tiles: ["a", "b", "c"].map { "https://\($0).basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png" })
        case .satellite:
            json = """
            { "version": 8, "sources": { "sat": { "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256, "maxzoom": 19, "attribution": "Tiles © Esri" } },
              "layers": [{ "id": "bg", "type": "background", "paint": { "background-color": "#F4F1E9" } }, { "id": "sat", "type": "raster", "source": "sat" }] }
            """
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("cumbre-style-\(rawValue).json")
        try? json.data(using: .utf8)?.write(to: url)
        return url
    }

    private static func rasterStyle(id: String, bg: String, maxZoom: Int, attribution: String, tiles: [String]) -> String {
        let list = tiles.map { "\"\($0)\"" }.joined(separator: ",")
        return """
        { "version": 8, "sources": { "\(id)": { "type": "raster", "tiles": [\(list)],
          "tileSize": 256, "maxzoom": \(maxZoom), "attribution": "\(attribution)" } },
          "layers": [{ "id": "bg", "type": "background", "paint": { "background-color": "\(bg)" } }, { "id": "\(id)", "type": "raster", "source": "\(id)" }] }
        """
    }
}
