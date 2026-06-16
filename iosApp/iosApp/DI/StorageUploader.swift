import Foundation
import FirebaseStorage
import UIKit

// Subida de imágenes a Firebase Storage (equivalente iOS de StorageUploadHelper
// de Android). Devuelve la URL de descarga pública para guardarla en el backend
// (foto de perfil, foto de nota…). Funciona en apps sideloaded (no requiere
// cuenta de pago, solo el SDK de Firebase ya incluido).
enum StorageUploader {
    /// Sube un JPEG comprimido a `path` y devuelve la URL de descarga.
    static func uploadJPEG(_ image: UIImage, path: String, quality: CGFloat = 0.8) async throws -> String {
        guard let data = image.jpegData(compressionQuality: quality) else {
            throw NSError(domain: "StorageUploader", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "No se pudo codificar la imagen"])
        }
        let ref = Storage.storage().reference().child(path)
        let meta = StorageMetadata(); meta.contentType = "image/jpeg"
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }
}
