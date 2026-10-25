import UIKit
import Vision
import Shared

/// "¿Qué piedra es esta?" a partir de una foto. Dos pasos, y ninguno sale del móvil:
/// 1. DÓNDE: la posición que guarda la foto (o, si no la trae, donde está el usuario) da la escuela y las
///    piedras de alrededor, normalmente pocas.
/// 2. CUÁL: entre esas pocas, se compara la foto con la de cada piedra con Vision (la "huella" de la imagen
///    que trae iOS). No es infalible: por eso se enseñan las 3 más probables con su foto y elige el usuario.
enum AssistantPhotoMatcher {

    /// Escuelas que se miran alrededor de la foto (por su punto central). Las zonas grandes tienen piedras a varios
    /// km de ese punto (La Pedriza, hasta 3,6 km; La Hermida, 5,5 km), así que se mira con margen.
    static let searchRadiusKm = 25.0
    /// Cuántas de esas escuelas (las más cercanas) se cargan para buscar su piedra más próxima.
    static let maxSchoolsChecked = 6
    /// La foto es "de esa escuela" si alguna de sus piedras está a menos de esto.
    static let nearestStoneKm = 2.0
    /// Sin piedras cerca, una escuela cuyo punto esté a menos de esto sigue contando (para decir "no hay piedras").
    static let schoolPointKm = 3.0
    /// Radio de piedras candidatas alrededor del punto (el GPS falla 10-30 m entre piedras).
    static let stoneRadiusM = 120.0
    /// Máximo de piedras a comparar (cada una es una descarga de su foto).
    static let maxCompared = 15

    struct Candidate: Identifiable {
        let blockId: String
        let blockName: String
        let schoolId: String
        let schoolName: String
        let photoUrl: String?
        /// Primera vía de la piedra: abrir la escuela con ella abre la piedra.
        let lineId: String?
        /// Metros desde el punto de la foto.
        let meters: Double
        /// 0 (nada que ver) a 1 (idéntica). nil si la piedra no tiene foto con que comparar.
        let similarity: Double?
        var id: String { blockId }
    }

    enum Outcome {
        /// Las más probables, de más a menos. `sure` = la primera destaca claramente.
        case found([Candidate], sure: Bool, approximate: Bool)
        /// Ni la foto ni el móvil dicen dónde: hay que preguntar la escuela.
        case noLocation
        /// No hay ninguna escuela de Cumbre cerca de ese punto. Se dice cuál es la más cercana y a cuántos km, para
        /// poder ver si la ubicación de la foto se leyó bien.
        case noSchoolNear(closestName: String?, closestKm: Double?)
        /// No se pudo cargar la lista de escuelas (sin red).
        case noData
        /// La escuela no tiene piedras con posición cerca de ese punto.
        case noStones(schoolName: String)
    }

    /// Por qué paso va la búsqueda (se enseña bajo los puntos de "escribiendo" y, si algo se atasca, en el aviso).
    enum Stage {
        case schools, stones, comparing(Int)

        var text: String {
            switch self {
            case .schools: return L("Buscando escuelas cerca de la foto…")
            case .stones: return L("Buscando piedras cerca…")
            case .comparing(let n): return L("Comparando con %@ piedras…", n)
            }
        }
    }

    /// Tiempo máximo para cargar escuelas o piedras (red) y para cada foto de piedra.
    static let loadTimeout: Double = 15
    static let photoTimeout: Double = 8

    /// @param approximate true si la posición NO es de la foto sino de donde está el usuario ahora.
    /// @param onStage aviso de por qué paso va (en el hilo principal).
    static func match(image: UIImage, lat: Double?, lon: Double?, approximate: Bool,
                      container: IosDependencyContainer,
                      onStage: @escaping @MainActor (Stage) -> Void = { _ in }) async -> Outcome {
        guard let lat, let lon else { return .noLocation }
        await onStage(.schools)
        guard let schools = await AsyncTimeout.run(loadTimeout, {
                  try? await container.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                         lat: nil, lon: nil, radioKm: nil)
              }), !schools.isEmpty else { return .noData }
        let byDistance = schools.map { ($0, km(lat, lon, $0.lat, $0.lon)) }.sorted { $0.1 < $1.1 }
        let around = Array(byDistance.filter { $0.1 <= searchRadiusKm }.prefix(maxSchoolsChecked))

        // La escuela es la que tiene la PIEDRA más cercana a la foto, no la de punto central más cercano.
        // Las piedras de las escuelas de alrededor se cargan a la vez, cada una con su tiempo máximo.
        await onStage(.stones)
        var loaded: [(School, [(Block, Double)])] = []
        await withTaskGroup(of: (School, [(Block, Double)]).self) { group in
            for (school, _) in around {
                group.addTask {
                    let blocks = await AsyncTimeout.run(loadTimeout, {
                        try? await container.getBlocks.invoke(schoolId: school.id)
                    }) ?? []
                    let near = blocks.filter { $0.type == "BLOCK" }
                        .map { ($0, km(lat, lon, $0.lat, $0.lon) * 1000) }.sorted { $0.1 < $1.1 }
                    return (school, near)
                }
            }
            for await r in group { loaded.append(r) }
        }
        let best = loaded
            .filter { ($0.1.first?.1 ?? .infinity) <= nearestStoneKm * 1000 }
            .min { ($0.1.first?.1 ?? .infinity) < ($1.1.first?.1 ?? .infinity) }
        guard let best else {
            if let point = byDistance.first, point.1 <= schoolPointKm { return .noStones(schoolName: point.0.name) }
            return .noSchoolNear(closestName: byDistance.first?.0.name, closestKm: byDistance.first?.1)
        }
        let school = best.0
        let near = best.1
        var pool = near.filter { $0.1 <= stoneRadiusM }
        if pool.isEmpty { pool = Array(near.prefix(5)) }        // GPS muy desviado: las más cercanas igualmente
        pool = Array(pool.prefix(maxCompared))
        guard !pool.isEmpty else { return .noStones(schoolName: school.name) }

        await onStage(.comparing(pool.count))
        let mine = featurePrint(downscaled(image))
        var out: [Candidate] = []
        await withTaskGroup(of: Candidate.self) { group in
            for (block, meters) in pool {
                group.addTask {
                    let url = block.photoPath ?? block.faces.first?.photoPath
                    var similarity: Double? = nil
                    if let mine, let url,
                       let photo = await AsyncTimeout.run(photoTimeout, { await ImageCache.image(url) }),
                       let theirs = featurePrint(downscaled(photo)) {
                        similarity = similarityOf(mine, theirs)
                    }
                    return Candidate(blockId: block.id, blockName: block.name, schoolId: school.id,
                                     schoolName: school.name, photoUrl: url, lineId: block.lines.first?.id,
                                     meters: meters, similarity: similarity)
                }
            }
            for await c in group { out.append(c) }
        }
        let ranked = out.sorted { score($0) > score($1) }
        let top = Array(ranked.prefix(3))
        let sure = top.count == 1 || (top.count >= 2 && score(top[0]) - score(top[1]) >= 0.15)
        return .found(top, sure: sure, approximate: approximate)
    }

    /// La foto en pequeño (lado mayor 640 px): Vision la reduce igualmente y así va mucho más rápido y gasta menos.
    static func downscaled(_ image: UIImage, maxSide: CGFloat = 640) -> UIImage {
        let side = max(image.size.width, image.size.height)
        guard side > maxSide else { return image }
        let scale = maxSide / side
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    /// Mezcla del parecido de la foto (manda) y la cercanía (desempata). Sin foto, solo cuenta la cercanía.
    static func score(_ c: Candidate) -> Double {
        let closeness = max(0, 1 - c.meters / (stoneRadiusM * 2))
        guard let s = c.similarity else { return 0.35 * closeness }
        return 0.75 * s + 0.25 * closeness
    }

    // MARK: - Vision

    private static func featurePrint(_ image: UIImage) -> VNFeaturePrintObservation? {
        guard let cg = image.cgImage else { return nil }
        let request = VNGenerateImageFeaturePrintRequest()
        request.imageCropAndScaleOption = .scaleFill
        let handler = VNImageRequestHandler(cgImage: cg, orientation: cgOrientation(image.imageOrientation))
        try? handler.perform([request])
        return request.results?.first as? VNFeaturePrintObservation
    }

    /// Distancia de Vision (0 = igual; fotos sin relación suelen dar más de 1) pasada a un parecido de 0 a 1.
    private static func similarityOf(_ a: VNFeaturePrintObservation, _ b: VNFeaturePrintObservation) -> Double? {
        var distance: Float = 0
        guard (try? a.computeDistance(&distance, to: b)) != nil else { return nil }
        return Double(max(0, min(1, 1 - distance / 1.4)))
    }

    private static func cgOrientation(_ o: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch o {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }

    private static func km(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let r = 6371.0
        let dLat = (lat2 - lat1) * .pi / 180, dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/// Espera algo como mucho `seconds` segundos; si no ha llegado, devuelve nil y sigue (lo que tardaba se abandona).
/// No usa un grupo de tareas a propósito: un grupo espera a que acaben TODAS, así que algo colgado lo colgaría igual.
enum AsyncTimeout {
    static func run<T>(_ seconds: Double, _ operation: @escaping () async -> T?) async -> T? {
        await withCheckedContinuation { (cont: CheckedContinuation<T?, Never>) in
            let once = OnceFlag()
            Task {
                let value = await operation()
                if once.claim() { cont.resume(returning: value) }
            }
            Task {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                if once.claim() { cont.resume(returning: nil) }
            }
        }
    }
}

/// Se puede reclamar una sola vez (para reanudar una continuación solo una vez entre dos tareas que compiten).
final class OnceFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var claimed = false

    func claim() -> Bool {
        lock.lock(); defer { lock.unlock() }
        if claimed { return false }
        claimed = true
        return true
    }
}
