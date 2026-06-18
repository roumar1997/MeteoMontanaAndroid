import Foundation
import FirebaseStorage
import FirebaseAuth
import UIKit

// Subida de imágenes a Firebase Storage (equivalente iOS de StorageUploadHelper
// de Android). Devuelve la URL de descarga pública para guardarla en el backend
// (foto de perfil, foto de nota, foto de piedra…). Funciona en apps sideloaded
// (no requiere cuenta de pago, solo el SDK de Firebase ya incluido).
//
// ⚠️ Las reglas de Firebase Storage SOLO permiten subir a rutas concretas y con
// el nombre del fichero empezando por el uid del autor. Por eso NO se debe subir
// a rutas inventadas (p.ej. "contribution-photos/"): la subida falla en silencio
// (denegada) y la foto se pierde. Usa SIEMPRE los helpers de aquí abajo, que
// replican EXACTAMENTE las rutas que usa Android (y que las reglas permiten).
enum StorageUploader {
    enum UploadError: Error { case notAuthenticated, encode }

    /// Sube un JPEG comprimido a `path` y devuelve la URL de descarga.
    static func uploadJPEG(_ image: UIImage, path: String, quality: CGFloat = 0.8) async throws -> String {
        guard let data = image.jpegData(compressionQuality: quality) else {
            throw UploadError.encode
        }
        let ref = Storage.storage().reference().child(path)
        let meta = StorageMetadata(); meta.contentType = "image/jpeg"
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// uid del usuario actual. Las reglas exigen que el nombre del fichero
    /// empiece por el uid → solo subes ficheros "tuyos".
    private static func requireUid() throws -> String {
        guard let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else {
            throw UploadError.notAuthenticated
        }
        return uid
    }

    /// Foto de una CARA de piedra (propuesta BOULDER). Misma ruta que Android:
    /// `piedra-photos-pending/{uid}_{schoolId}_{ts}[_n].jpg`. `index` distingue
    /// las fotos de varias caras subidas en el mismo segundo.
    static func uploadBoulderPhoto(_ image: UIImage, schoolId: String, index: Int = 0) async throws -> String {
        let uid = try requireUid()
        let ts = Int(Date().timeIntervalSince1970)
        return try await uploadJPEG(image, path: "piedra-photos-pending/\(uid)_\(schoolId)_\(ts)_\(index).jpg")
    }

    /// Foto adjunta a una nota comunitaria. Misma ruta que Android:
    /// `note-photos/{uid}_{schoolId}_{ts}.jpg`.
    static func uploadNotePhoto(_ image: UIImage, schoolId: String) async throws -> String {
        let uid = try requireUid()
        let ts = Int(Date().timeIntervalSince1970)
        return try await uploadJPEG(image, path: "note-photos/\(uid)_\(schoolId)_\(ts).jpg")
    }

    /// Foto de perfil del usuario actual. Misma ruta que Android:
    /// `profile-photos/{uid}.jpg`.
    static func uploadProfilePhoto(_ image: UIImage) async throws -> String {
        let uid = try requireUid()
        return try await uploadJPEG(image, path: "profile-photos/\(uid).jpg")
    }
}
