import SwiftUI
import UIKit
import Shared

enum GymSendState { case idle, sending, sent, failed }

/// «Corregir datos» y «Añadir un muro que falta»: foto opcional (se sube primero) y envío al admin.
/// Espejo de `GymReportViewModel.kt`.
@MainActor
final class GymReportViewModel: ObservableObject {
    @Published var state: GymSendState = .idle
    private let c = AppDependencies.shared.container.gyms

    func reset() { state = .idle }

    func sendCorrection(gymId: Int64, issueCodes: Set<String>, comment: String, photo: UIImage?) {
        let issues = GymReportIssue.allCases.filter { issueCodes.contains($0.code) }
        let trimmed = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        send(photo) { url in
            _ = try await self.c.submitGymCorrection.execute(
                correction: GymCorrection(gymId: gymId, issues: issues, comment: trimmed.isEmpty ? nil : trimmed, photoUrl: url))
        }
    }

    func sendNewGym(name: String, lat: Double, lon: Double, kind: GymKind, boulder: Bool, rope: Bool, photo: UIImage?) {
        var discipline: GymDiscipline?
        if boulder && rope { discipline = .both } else if boulder { discipline = .boulder } else if rope { discipline = .rope }
        let draft = { (url: String?) in
            NewGymDraft(name: name.trimmingCharacters(in: .whitespacesAndNewlines), lat: lat, lon: lon, kind: kind,
                        discipline: discipline, outdoor: KotlinBoolean(bool: kind == .free), comment: nil, photoUrl: url)
        }
        send(photo) { url in _ = try await self.c.submitNewGym.execute(draft: draft(url)) }
    }

    private func send(_ photo: UIImage?, _ submit: @escaping (String?) async throws -> Void) {
        guard state != .sending else { return }
        state = .sending
        Task {
            do {
                var url: String?
                if let photo, let data = GymPhotoCompressor.jpeg(photo) {
                    url = try await c.uploadGymReportPhoto.execute(bytes: data.toKotlinByteArray())
                }
                try await submit(url)
                state = .sent
            } catch {
                state = .failed
            }
        }
    }
}

/// Reduce la foto a 2048 px (nunca amplía) y la comprime a JPEG. Al volver a dibujarla se hornea la rotación EXIF:
/// las fotos verticales no salen de lado (igual que `readImageCompressed` en Android).
enum GymPhotoCompressor {
    static func jpeg(_ image: UIImage, maxDimension: CGFloat = 2048, quality: CGFloat = 0.8) -> Data? {
        let w = image.size.width * image.scale
        let h = image.size.height * image.scale
        let ratio = min(1, maxDimension / max(w, h, 1))
        let size = CGSize(width: w * ratio, height: h * ratio)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let baked = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return baked.jpegData(compressionQuality: quality)
    }
}
