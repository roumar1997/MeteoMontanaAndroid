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

    /// @param approximate true si la posición NO es de la foto sino de donde está el usuario ahora.
    static func match(image: UIImage, lat: Double?, lon: Double?, approximate: Bool,
                      container: IosDependencyContainer) async -> Outcome {
        guard let lat, let lon else { return .noLocation }
        guard let schools = try? await container.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                                  lat: nil, lon: nil, radioKm: nil),
              !schools.isEmpty else { return .noData }
        let byDistance = schools.map { ($0, km(lat, lon, $0.lat, $0.lon)) }.sorted { $0.1 < $1.1 }
        let around = byDistance.filter { $0.1 <= searchRadiusKm }.prefix(maxSchoolsChecked)

        // La escuela es la que tiene la PIEDRA más cercana a la foto, no la de punto central más cercano.
        var best: (school: School, blocks: [(Block, Double)])? = nil
        for (school, _) in around {
            let blocks = ((try? await container.getBlocks.invoke(schoolId: school.id)) ?? []).filter { $0.type == "BLOCK" }
            let near = blocks.map { ($0, km(lat, lon, $0.lat, $0.lon) * 1000) }.sorted { $0.1 < $1.1 }
            guard let first = near.first, first.1 <= nearestStoneKm * 1000 else { continue }
            if best == nil || first.1 < (best?.blocks.first?.1 ?? .infinity) { best = (school, near) }
        }
        guard let best else {
            if let point = byDistance.first, point.1 <= schoolPointKm { return .noStones(schoolName: point.0.name) }
            return .noSchoolNear(closestName: byDistance.first?.0.name, closestKm: byDistance.first?.1)
        }
        let school = best.school
        let near = best.blocks
        var pool = near.filter { $0.1 <= stoneRadiusM }
        if pool.isEmpty { pool = Array(near.prefix(5)) }        // GPS muy desviado: las más cercanas igualmente
        pool = Array(pool.prefix(maxCompared))
        guard !pool.isEmpty else { return .noStones(schoolName: school.name) }

        let mine = featurePrint(image)
        var out: [Candidate] = []
        await withTaskGroup(of: Candidate.self) { group in
            for (block, meters) in pool {
                group.addTask {
                    let url = block.photoPath ?? block.faces.first?.photoPath
                    var similarity: Double? = nil
                    if let mine, let url, let photo = await ImageCache.image(url), let theirs = featurePrint(photo) {
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
