import SwiftUI
import UIKit
import AVFoundation
import Shared

// Foto de celebración del feed — utilidades compartidas (cámara, compresión,
// miniatura, visor a pantalla completa). Espejo de la ronda "foto de
// celebración" de Android (FeedPublishSheet en SchoolMap.kt +
// FullScreenPhotoDialog.kt).

// MARK: - Cámara del sistema (UIImagePickerController .camera)

/// Picker de CÁMARA (la foto se hace en el momento; el usuario cambia
/// frontal/trasera dentro de la propia cámara). No existe otro picker de
/// cámara en la app (el resto usan PhotosPicker de galería) → este es el
/// wrapper reutilizable.
struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ picker: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate,
                             UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let img = info[.originalImage] as? UIImage {
                parent.onCapture(img)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

/// Estado del permiso de cámara resuelto para la UI.
enum CameraAccess {
    /// Comprueba/pide el permiso de cámara y llama al callback en main:
    /// granted=true → abrir la cámara; false → enseñar la alerta de AJUSTES.
    /// En dispositivos sin cámara (simulador) no hace nada.
    static func request(_ completion: @escaping (Bool) -> Void) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        default:
            // .denied / .restricted → alerta con botón AJUSTES.
            completion(false)
        }
    }
}

// MARK: - Compresión (mismo pipeline que las subidas de fotos existentes)

/// Redimensiona a máx `maxDimension` px en el lado largo (nunca amplía) y
/// comprime a JPEG. Réplica del `downscaled` privado de StorageUploader
/// (píxeles reales, scale=1 — sin forzarlo el renderer multiplica por 2x/3x).
func feedPhotoJPEGData(_ image: UIImage, maxDimension: CGFloat = 1024,
                       quality: CGFloat = 0.8) -> Data? {
    let pxW = image.size.width * image.scale
    let pxH = image.size.height * image.scale
    let longSide = max(pxW, pxH)
    var out = image
    if longSide > maxDimension {
        let ratio = maxDimension / longSide
        let newSize = CGSize(width: pxW * ratio, height: pxH * ratio)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
        out = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
    }
    return out.jpegData(compressionQuality: quality)
}

extension Data {
    /// Data → KotlinByteArray (para pasar la foto al use case compartido).
    /// Inversa del `KotlinByteArray.toData()` del radar.
    func toKotlinByteArray() -> KotlinByteArray {
        let arr = KotlinByteArray(size: Int32(count))
        for (i, b) in enumerated() {
            arr.set(index: Int32(i), value: Int8(bitPattern: b))
        }
        return arr
    }
}

/// Aviso discreto (alert con OK) de que la foto no se pudo subir: el post
/// queda publicado sin ella. Paridad con el Toast de Android.
@MainActor
func showFeedPhotoUploadFailedAlert() {
    guard let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
          let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
    else { return }
    var top = root
    while let presented = top.presentedViewController { top = presented }
    let alert = UIAlertController(
        title: nil,
        message: "No se pudo subir la foto; publicado sin ella",
        preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default))
    top.present(alert, animated: true)
}

// MARK: - Miniatura (tarjeta del feed / hoja de publicar)

/// Miniatura 88×110 de la foto de celebración (radius 6, borde papel).
/// Carga por ImageCache — el mismo mecanismo que las fotos firmadas de las
/// piedras (TopoPhotoView): si la URL firmada caduca, se re-descarga la nueva.
struct FeedCelebrationThumb: View {
    let photoUrl: String
    /// Grosor del borde (2 superpuesta sobre el topo, 1 suelta en la hoja).
    var borderWidth: CGFloat = 2

    @State private var image: UIImage? = nil

    var body: some View {
        ZStack {
            Cumbre.paper
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                ProgressView().tint(Cumbre.ink3)
            }
        }
        .frame(width: 88, height: 110)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Cumbre.bg, lineWidth: borderWidth))
        .task(id: photoUrl) { image = await ImageCache.image(photoUrl) }
    }
}

/// Foto de celebración como IMAGEN PRINCIPAL de la tarjeta (post sin topo):
/// ancho completo respetando el aspect real de la foto.
struct FeedMainCelebrationImage: View {
    let photoUrl: String

    @State private var image: UIImage? = nil
    @State private var ratio: CGFloat = 4.0 / 3.0

    var body: some View {
        ZStack {
            Color.black
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ProgressView().tint(.white)
            }
        }
        .aspectRatio(ratio, contentMode: .fit)
        .clipped()
        .task(id: photoUrl) {
            if let img = await ImageCache.image(photoUrl) {
                image = img
                let w = img.size.width, h = img.size.height
                if w > 0, h > 0 { ratio = min(max(w / h, 0.55), 2.2) }
            }
        }
    }
}

// MARK: - Visor a pantalla completa

/// Visor de foto a pantalla completa (fondo negro, ✕, zoom pinch básico) —
/// espejo de FullScreenPhotoDialog.kt. Reutilizable para cualquier URL.
struct FullScreenPhotoView: View {
    let photoUrl: String
    let onDismiss: () -> Void

    @State private var image: UIImage? = nil
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .scaleEffect(scale)
                    .gesture(
                        MagnificationGesture()
                            .onChanged { value in
                                scale = min(max(lastScale * value, 1), 5)
                            }
                            .onEnded { _ in lastScale = scale }
                    )
            } else {
                ProgressView().tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            // ✕ cerrar (esquina superior derecha).
            Button(action: onDismiss) {
                Text("✕")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .background(Color.white.opacity(0.15))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(12)
        }
        .task(id: photoUrl) { image = await ImageCache.image(photoUrl) }
    }
}
