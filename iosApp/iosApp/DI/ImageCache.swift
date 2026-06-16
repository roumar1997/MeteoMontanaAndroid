import UIKit

/// Caché de imágenes en disco para ver fotos SIN conexión. Equivalente iOS de la
/// caché de disco de Coil en Android. Al guardar una escuela offline se
/// pre-descargan las fotos de sus piedras; al mostrarlas se sirven del disco.
enum ImageCache {
    private static let dir: URL = {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("photo-cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    /// Nombre de fichero ESTABLE por URL (FNV-1a; `String.hashValue` se aleatoriza
    /// por proceso y no serviría entre lanzamientos).
    private static func key(_ s: String) -> String {
        var h: UInt64 = 1469598103934665603
        for b in s.utf8 { h = (h ^ UInt64(b)) &* 1099511628211 }
        return String(h, radix: 16) + ".img"
    }

    private static func fileURL(_ url: String) -> URL { dir.appendingPathComponent(key(url)) }

    /// Imagen de una URL: usa la copia en disco si existe; si no, la descarga y la
    /// cachea. Devuelve nil si no hay red ni caché (offline sin guardar).
    static func image(_ url: String) async -> UIImage? {
        let local = fileURL(url)
        if let data = try? Data(contentsOf: local), let img = UIImage(data: data) { return img }
        guard let u = URL(string: url),
              let (data, _) = try? await URLSession.shared.data(from: u),
              let img = UIImage(data: data) else { return nil }
        try? data.write(to: local)
        return img
    }

    /// Pre-descarga (al guardar offline) para que luego se vean sin red.
    static func prefetch(_ urls: [String]) async {
        for u in urls where !u.isEmpty { _ = await image(u) }
    }
}
