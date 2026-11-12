import UIKit
import ImageIO

/// Caché de imágenes en disco para ver fotos SIN conexión. Equivalente iOS de la
/// caché de disco de Coil en Android. Al guardar una escuela offline se
/// pre-descargan las fotos de sus piedras; al mostrarlas se sirven del disco.
///
/// Encima del disco hay una caché en memoria (`NSCache`, la vacía el sistema si falta memoria) y la
/// decodificación se hace fuera del hilo principal. Con `maxPixel` la foto se decodifica ya reducida
/// (miniatura de ImageIO): para mostrar en pantalla sobra con ~1200 px y se ahorra mucha memoria.
enum ImageCache {
    /// Lado mayor para fotos que solo se muestran en pantalla.
    static let displayMaxPixel = 1200
    /// Topos y editor de líneas: se puede ampliar, así que algo más de detalle.
    static let editorMaxPixel = 2048

    private static let dir: URL = {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("photo-cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    private static let memory: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.totalCostLimit = 96 * 1024 * 1024
        return c
    }()

    /// Nombre de fichero ESTABLE por URL (FNV-1a; `String.hashValue` se aleatoriza
    /// por proceso y no serviría entre lanzamientos).
    private static func key(_ s: String) -> String {
        var h: UInt64 = 1469598103934665603
        for b in s.utf8 { h = (h ^ UInt64(b)) &* 1099511628211 }
        return String(h, radix: 16) + ".img"
    }

    private static func fileURL(_ url: String) -> URL { dir.appendingPathComponent(key(url)) }

    private static func memoryKey(_ url: String, _ maxPixel: Int?) -> NSString {
        "\(maxPixel ?? 0)|\(url)" as NSString
    }

    /// Imagen ya en memoria (sin tocar disco ni red), para pintarla al instante.
    static func cached(_ url: String, maxPixel: Int? = nil) -> UIImage? {
        memory.object(forKey: memoryKey(url, maxPixel))
    }

    /// Imagen de una URL: usa la copia en disco si existe; si no, la descarga y la
    /// cachea. Devuelve nil si no hay red ni caché (offline sin guardar).
    /// `maxPixel` nil = resolución completa (compartir, PDF); con valor, reducida al decodificar.
    static func image(_ url: String, maxPixel: Int? = nil) async -> UIImage? {
        let mk = memoryKey(url, maxPixel)
        if let hit = memory.object(forKey: mk) { return hit }
        let local = fileURL(url)
        let fromDisk = await Task.detached(priority: .userInitiated) { diskImage(local, maxPixel: maxPixel) }.value
        if let fromDisk {
            memory.setObject(fromDisk, forKey: mk, cost: cost(fromDisk))
            return fromDisk
        }
        // Sin copia en disco, o copia dañada (escritura a medias): se baja de nuevo y se sustituye.
        guard let u = URL(string: url), let (fresh, _) = try? await URLSession.shared.data(from: u) else { return nil }
        let img = await Task.detached(priority: .userInitiated) { decode(fresh, maxPixel: maxPixel) }.value
        guard let img else { return nil }
        try? fresh.write(to: local, options: .atomic)
        memory.setObject(img, forKey: mk, cost: cost(img))
        return img
    }

    /// La copia en disco ya decodificada, o nil si no existe o no se puede leer como imagen.
    private static func diskImage(_ local: URL, maxPixel: Int?) -> UIImage? {
        guard let data = try? Data(contentsOf: local) else { return nil }
        return decode(data, maxPixel: maxPixel)
    }

    /// Pre-descarga (al guardar offline) para que luego se vean sin red. Varias a la vez, con límite.
    static func prefetch(_ urls: [String], parallel: Int = 4) async {
        let pending = urls.filter { !$0.isEmpty && !FileManager.default.fileExists(atPath: fileURL($0).path) }
        await withTaskGroup(of: Void.self) { group in
            var it = pending.makeIterator()
            for _ in 0..<max(1, parallel) {
                guard let u = it.next() else { break }
                group.addTask { await download(u) }
            }
            while await group.next() != nil {
                if let u = it.next() { group.addTask { await download(u) } }
            }
        }
    }

    /// Solo baja el fichero al disco (sin decodificar): lo justo para tenerlo offline.
    private static func download(_ url: String) async {
        let local = fileURL(url)
        guard let u = URL(string: url), let (data, _) = try? await URLSession.shared.data(from: u),
              UIImage(data: data) != nil else { return }
        try? data.write(to: local, options: .atomic)
    }

    /// Decodifica unos bytes (reducidos si hay `maxPixel`). Llamar fuera del hilo principal.
    static func decode(_ data: Data, maxPixel: Int?) -> UIImage? {
        guard let maxPixel else { return UIImage(data: data) }
        let opts = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let src = CGImageSourceCreateWithData(data as CFData, opts) else { return UIImage(data: data) }
        let thumbOpts = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel
        ] as [CFString: Any] as CFDictionary
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, thumbOpts) else { return UIImage(data: data) }
        return UIImage(cgImage: cg)
    }

    private static func cost(_ img: UIImage) -> Int {
        guard let cg = img.cgImage else { return 1 }
        return cg.bytesPerRow * cg.height
    }
}
