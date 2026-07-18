import SwiftUI
import UIKit
import AVFoundation
import PhotosUI
import Shared

// Foto de celebración del feed — utilidades compartidas (cámara, compresión,
// miniatura, visor a pantalla completa). Espejo de la ronda "foto de
// celebración" de Android (FeedPublishSheet en SchoolMap.kt +
// FullScreenPhotoDialog.kt).

// MARK: - Cámara del sistema (presentada por UIKit + estado observable)

/// Contenedor observable de la foto capturada. Es una CLASE (referencia) a
/// propósito: al presentar la cámara por UIKit (fuera del árbol SwiftUI), un
/// `@State` (valor) captado en el closure del callback NO refresca la vista
/// (builds 71/72 → la publicación salía SIN foto). Un ObservableObject sí
/// propaga el cambio a la hoja. Y presentar por UIKit evita el parpadeo del
/// fullScreenCover-dentro-de-sheet.
@MainActor
final class CapturedPhotoStore: ObservableObject {
    @Published var image: UIImage?

    /// RED DE SEGURIDAD (bug real 2026-07-18): si SwiftUI recrea la hoja de
    /// publicar mientras la cámara está abierta (la cámara pasa la app por
    /// .inactive), el closure de captura escribe en el store VIEJO ya muerto y
    /// la foto "desaparecía" sin error. La última captura se guarda también
    /// aquí (global, con la vía a la que pertenece) y la hoja recreada la
    /// adopta al aparecer si la suya está vacía Y es de la MISMA vía.
    static var lastCaptured: (image: UIImage, context: String, at: Date)?

    /// Ventana en la que una captura huérfana se considera "de esta publicación".
    static let recoveryWindow: TimeInterval = 180

    static func remember(_ image: UIImage, for context: String) {
        lastCaptured = (image, context, Date())
    }

    /// Última captura reciente de ESA vía aún no consumida (nil si no hay,
    /// es vieja, o era de otra vía — nunca adoptar la foto de otro ascenso).
    static func recentOrphan(for context: String) -> UIImage? {
        guard let last = lastCaptured, last.context == context,
              Date().timeIntervalSince(last.at) < recoveryWindow else { return nil }
        return last.image
    }

    /// Olvida el búfer (al publicar, quitar la foto con ✕ o cerrar la hoja),
    /// para que una publicación posterior no adopte una foto vieja.
    static func forget() {
        lastCaptured = nil
    }
}

/// Delegate del picker, retenido por el propio picker (associated object) para
/// que viva mientras la cámara está abierta.
private final class CameraCoordinator: NSObject, UIImagePickerControllerDelegate,
                                       UINavigationControllerDelegate {
    let onCapture: (UIImage) -> Void
    /// Vía/bloque al que pertenece la captura (clave del búfer de rescate).
    let context: String
    init(context: String, onCapture: @escaping (UIImage) -> Void) {
        self.context = context
        self.onCapture = onCapture
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        var captured: UIImage?
        if let img = (info[.originalImage] ?? info[.editedImage]) as? UIImage {
            // La cámara FRONTAL del picker devuelve el selfie volteado respecto
            // a la vista previa (efecto espejo horneado en los píxeles). Lo
            // volteamos para que quede como se vio. La trasera no se toca.
            captured = img.baked(flipHorizontally: picker.cameraDevice == .front)
        }
        if let captured {
            // Doble red de seguridad ANTES de cerrar la cámara:
            // 1) búfer global — sobrevive a que la hoja se recree por debajo.
            CapturedPhotoStore.remember(captured, for: context)
            // 2) carrete del usuario — aunque TODO fallara, la foto del grupo
            //    existe en Fotos y se puede elegir desde la galería. iOS pide
            //    el permiso "añadir a Fotos" la primera vez; si lo deniegan,
            //    falla en silencio (la foto sigue llegando por el flujo normal).
            UIImageWriteToSavedPhotosAlbum(captured, nil, nil, nil)
        }
        // Entregar la foto DESPUÉS de cerrar la cámara: si se entrega antes, el
        // @Published cambia mientras la cámara todavía cubre la hoja y la
        // miniatura no se refresca hasta el siguiente redibujado (bug: la foto
        // no aparecía hasta escribir en la descripción).
        let cb = onCapture
        picker.dismiss(animated: true) {
            if let captured {
                cb(captured)
            } else {
                // NUNCA en silencio: iOS no entregó la imagen (presión de
                // memoria u otro fallo del picker) → decirlo al momento para
                // repetir la foto YA, no descubrirlo con la gente ya dispersa.
                showPhotoCaptureFailedAlert()
            }
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
}

private var cameraCoordinatorKey: UInt8 = 0

/// View-controller superior actualmente presentado (para presentar sobre él).
@MainActor
private func topPresentedViewController() -> UIViewController? {
    let keyWindow = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }
        .first { $0.isKeyWindow }
    var top = keyWindow?.rootViewController
    while let presented = top?.presentedViewController { top = presented }
    return top
}

/// Presenta la cámara del sistema por UIKit (sin el parpadeo del
/// fullScreenCover-dentro-de-sheet). El callback debe actualizar un
/// ObservableObject (ver CapturedPhotoStore) para que la vista se refresque.
@MainActor
func presentSystemCamera(context: String, onCapture: @escaping (UIImage) -> Void) {
    guard UIImagePickerController.isSourceTypeAvailable(.camera),
          let presenter = topPresentedViewController() else { return }
    let picker = UIImagePickerController()
    picker.sourceType = .camera
    let coord = CameraCoordinator(context: context, onCapture: onCapture)
    picker.delegate = coord
    objc_setAssociatedObject(picker, &cameraCoordinatorKey, coord, .OBJC_ASSOCIATION_RETAIN)
    presenter.present(picker, animated: true)
}

// MARK: - Galería del sistema (PHPicker, sin permiso — el picker corre fuera
// del proceso y solo entrega lo elegido)

/// Delegate del PHPicker, retenido por el propio picker (mismo patrón que la
/// cámara). Entrega la imagen tras cerrar; si la carga falla, avisa.
private final class GalleryCoordinator: NSObject, PHPickerViewControllerDelegate {
    let onPick: (UIImage) -> Void
    /// Vía/bloque al que pertenece la foto (clave del búfer de rescate).
    let context: String
    init(context: String, onPick: @escaping (UIImage) -> Void) {
        self.context = context
        self.onPick = onPick
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        let provider = results.first?.itemProvider
        let cb = onPick
        picker.dismiss(animated: true) {
            guard let provider, provider.canLoadObject(ofClass: UIImage.self) else {
                // Cerrar sin elegir nada = cancelación normal, sin aviso.
                if provider != nil { Task { @MainActor in showPhotoCaptureFailedAlert() } }
                return
            }
            let ctx = self.context
            provider.loadObject(ofClass: UIImage.self) { object, _ in
                DispatchQueue.main.async {
                    if let img = object as? UIImage {
                        // La galería ya tiene el original — solo hace falta el
                        // búfer anti-recreación de la hoja, no re-guardarla.
                        CapturedPhotoStore.remember(img, for: ctx)
                        cb(img)
                    } else {
                        // NUNCA en silencio (p.ej. un RAW/formato no cargable).
                        showPhotoCaptureFailedAlert()
                    }
                }
            }
        }
    }
}

private var galleryCoordinatorKey: UInt8 = 0

/// Presenta el selector de fotos del sistema por UIKit (mismo patrón que
/// `presentSystemCamera`: sin parpadeo del fullScreenCover dentro de la hoja).
@MainActor
func presentSystemPhotoPicker(context: String, onPick: @escaping (UIImage) -> Void) {
    guard let presenter = topPresentedViewController() else { return }
    var config = PHPickerConfiguration(photoLibrary: .shared())
    config.filter = .images
    config.selectionLimit = 1
    let picker = PHPickerViewController(configuration: config)
    let coord = GalleryCoordinator(context: context, onPick: onPick)
    picker.delegate = coord
    objc_setAssociatedObject(picker, &galleryCoordinatorKey, coord, .OBJC_ASSOCIATION_RETAIN)
    presenter.present(picker, animated: true)
}

/// Aviso inmediato de que la foto NO se capturó/cargó (nunca fallar en
/// silencio: mejor repetirla con la gente aún reunida).
@MainActor
func showPhotoCaptureFailedAlert() {
    guard let top = topPresentedViewController() else { return }
    let alert = UIAlertController(
        title: "La foto no se ha guardado",
        message: "Inténtalo de nuevo o elígela de la galería.",
        preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default))
    top.present(alert, animated: true)
}

extension UIImage {
    /// Hornea la orientación a `.up` en los píxeles y, si `flipHorizontally`,
    /// voltea en horizontal para QUITAR el espejo del selfie de la cámara
    /// frontal (el picker lo devuelve reflejado con la orientación ya normal,
    /// así que no basta con mirar imageOrientation — hay que voltear siempre
    /// que la captura sea frontal). La cámara trasera pasa `false` y no cambia.
    func baked(flipHorizontally: Bool) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = scale
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            if flipHorizontally {
                ctx.cgContext.translateBy(x: size.width, y: 0)
                ctx.cgContext.scaleBy(x: -1, y: 1)
            }
            draw(in: CGRect(origin: .zero, size: size))
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
