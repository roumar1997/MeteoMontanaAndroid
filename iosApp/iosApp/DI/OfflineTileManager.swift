import Foundation
import MapLibre
import CoreLocation

/// Descarga y elimina regiones de tiles de mapa offline por escuela.
/// Equivalente iOS de OfflineTileManager.kt (Android usa MapLibre
/// OfflineManager; aquí el mismo SDK expone MLNOfflineStorage).
///
/// - Bounding box: ~2 km cuadrados alrededor del centro (mismos deltas que Android).
/// - Zoom: 10..16.
/// - Tile source: OpenTopoMap (mismo estilo que MapStyleKind.topo — el que usa
///   SchoolMapSection en modo TOPO).
///
/// Sin esto, offline el mapa solo mostraba los marcadores sin el mapa de fondo
/// si no se había visitado antes esa zona con red (Android sí cacheaba tiles).
enum OfflineTileManager {

    private static func contextTag(_ schoolId: String) -> Data {
        "school:\(schoolId)".data(using: .utf8) ?? Data()
    }

    static func downloadFor(schoolId: String, lat: Double, lon: Double) {
        let deltaLat = 0.018
        let deltaLon = 0.024
        let bounds = MLNCoordinateBounds(
            sw: CLLocationCoordinate2D(latitude: lat - deltaLat, longitude: lon - deltaLon),
            ne: CLLocationCoordinate2D(latitude: lat + deltaLat, longitude: lon + deltaLon))
        let region = MLNTilePyramidOfflineRegion(
            styleURL: MapStyleKind.topo.styleURL(),
            bounds: bounds,
            fromZoomLevel: 10,
            toZoomLevel: 16)
        MLNOfflineStorage.shared.addPack(for: region, withContext: contextTag(schoolId)) { pack, error in
            if let error {
                print("OfflineTiles: error creando región para \(schoolId): \(error.localizedDescription)")
                return
            }
            pack?.resume()
        }
    }

    static func removeFor(schoolId: String) {
        let tag = contextTag(schoolId)
        for pack in MLNOfflineStorage.shared.packs ?? [] {
            if pack.context == tag {
                MLNOfflineStorage.shared.removePack(pack) { error in
                    if let error {
                        print("OfflineTiles: error borrando región de \(schoolId): \(error.localizedDescription)")
                    }
                }
            }
        }
    }
}
