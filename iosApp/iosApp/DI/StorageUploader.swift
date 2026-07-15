import Foundation
import FirebaseAuth
import UIKit

// Subida de imágenes Tipo A al BACKEND (que las guarda en Cloudflare R2, egress
// gratis) en vez de directas a Firebase. Devuelve la URL permanente
// `.../api/photo/{key}` para guardarla en el backend (perfil/nota/piedra/quedada).
// Mantiene el downscale a 2048 px antes de subir.
enum StorageUploader {
    enum UploadError: Error { case notAuthenticated, encode }

    /// Reduce la imagen a `maxDimension` px en su lado largo (nunca amplía). Sin
    /// esto, una foto del móvil a resolución completa (12MP, varios MB) supera el
    /// límite de tamaño de las reglas de Storage y la subida se DENIEGA — pasaba
    /// con la 2ª foto de una piedra multi-cara aunque hubiera 5G.
    private static func downscaled(_ image: UIImage, maxDimension: CGFloat = 2048) -> UIImage {
        // OJO: image.size está en PUNTOS y UIGraphicsImageRenderer usa por defecto
        // la escala del dispositivo (2x/3x), así que sin forzar scale=1 el
        // resultado salía a 2048×3 px → MÁS grande → seguía pasando el límite de
        // 5 MB de Storage. Trabajamos en píxeles reales y con scale=1.
        let pxW = image.size.width * image.scale
        let pxH = image.size.height * image.scale
        let longSide = max(pxW, pxH)
        guard longSide > maxDimension else { return image }
        let ratio = maxDimension / longSide
        let newSize = CGSize(width: pxW * ratio, height: pxH * ratio)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
        return renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
    }

    /// Sube un JPEG comprimido al backend (R2) y devuelve la URL `.../api/photo/{key}`.
    private static func uploadJPEG(_ image: UIImage, category: String,
                                  schoolId: String? = nil, meetupId: String? = nil,
                                  quality: CGFloat = 0.8) async throws -> String {
        guard let data = downscaled(image).jpegData(compressionQuality: quality) else {
            throw UploadError.encode
        }
        _ = try requireUid()   // exige sesión (el backend saca el uid del token)
        return try await AppDependencies.shared.container.photoApi.upload(
            category: category,
            bytes: data.toKotlinByteArray(),
            schoolId: schoolId,
            meetupId: meetupId,
            contentType: "image/jpeg")
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
        try await uploadJPEG(image, category: "boulder", schoolId: schoolId)
    }

    /// Foto adjunta a una nota comunitaria.
    static func uploadNotePhoto(_ image: UIImage, schoolId: String) async throws -> String {
        try await uploadJPEG(image, category: "note", schoolId: schoolId)
    }

    /// Foto de perfil del usuario actual.
    static func uploadProfilePhoto(_ image: UIImage) async throws -> String {
        try await uploadJPEG(image, category: "profile")
    }

    /// Foto de una quedada.
    static func uploadMeetupPhoto(_ image: UIImage, tempId: String) async throws -> String {
        try await uploadJPEG(image, category: "meetup", meetupId: tempId)
    }
}
