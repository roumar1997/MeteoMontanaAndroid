import SwiftUI
import PhotosUI
import ImageIO
import Shared

/// "Enviar piedra": eliges una foto y la app deduce en qué escuela se hizo.
///
/// La cámara guarda las coordenadas dentro de la foto, así que no hace falta
/// buscar la escuela en la lista. A partir de ahí sigue el flujo de proponer
/// piedra de siempre, con la foto ya puesta como primera cara.
///
/// Lo que NO hace, y es a propósito: **elegir la piedra**. El GPS de un móvil se
/// equivoca entre 10 y 30 metros en un canchal y las piedras están a metros unas
/// de otras. Sirve para acertar la escuela; el punto exacto lo pone el usuario
/// sobre el mapa, centrado donde se hizo la foto.
///
/// Espejo de `SubmitBlockPhotoFlow.kt` en Android.

/// Dónde se hizo una foto, leído de la fototeca y de su EXIF.
struct PhotoWhere {
    let image: UIImage
    let lat: Double
    let lon: Double
    /// Hacia dónde apuntaba la cámara, si la foto lo trae. La mayoría no.
    let cameraDegrees: Float?
}

/// Lo que arrastra la foto hasta que se abre el flujo dentro de su escuela.
///
/// Se consume UNA vez: si se quedara puesto, volver a entrar en la escuela
/// reabriría el flujo de proponer sin que nadie lo haya pedido.
@MainActor
final class PhotoProposalSeedStore {
    static let shared = PhotoProposalSeedStore()

    struct Seed {
        let schoolId: String
        let image: UIImage
        let lat: Double
        let lon: Double
        let aspect: String?
    }

    private var pendiente: Seed?

    func put(_ seed: Seed) { pendiente = seed }

    func take(schoolId: String) -> Seed? {
        guard let s = pendiente, s.schoolId == schoolId else { return nil }
        pendiente = nil
        return s
    }

    func clear() { pendiente = nil }
}

/// Lee la foto elegida y de dónde es.
///
/// En iOS **el selector borra la ubicación** salvo que se pida el asset real de
/// la fototeca, y eso exige autorización de lectura. Sin ese paso, esto
/// devolvería siempre nil en fotos que sí tienen coordenadas.
enum PhotoExifReader {

    static func read(_ result: PHPickerResult) async -> PhotoWhere? {
        guard let id = result.assetIdentifier else { return nil }
        guard await autorizado() else { return nil }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              let loc = asset.location else { return nil }

        guard let (imagen, rumbo) = await imagenYRumbo(asset) else { return nil }
        return PhotoWhere(image: imagen,
                          lat: loc.coordinate.latitude,
                          lon: loc.coordinate.longitude,
                          cameraDegrees: rumbo)
    }

    private static func autorizado() async -> Bool {
        let actual = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if actual == .authorized || actual == .limited { return true }
        return await withCheckedContinuation { cont in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { nuevo in
                cont.resume(returning: nuevo == .authorized || nuevo == .limited)
            }
        }
    }

    /// La imagen y, del EXIF, hacia dónde apuntaba la cámara.
    private static func imagenYRumbo(_ asset: PHAsset) async -> (UIImage, Float?)? {
        await withCheckedContinuation { cont in
            let opciones = PHImageRequestOptions()
            opciones.isNetworkAccessAllowed = true   // fotos en iCloud
            opciones.isSynchronous = false
            PHImageManager.default().requestImageDataAndOrientation(
                for: asset, options: opciones
            ) { data, _, _, _ in
                guard let data, let img = UIImage(data: data) else {
                    cont.resume(returning: nil); return
                }
                cont.resume(returning: (img, rumboDe(data)))
            }
        }
    }

    private static func rumboDe(_ data: Data) -> Float? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any],
              let gps = props[kCGImagePropertyGPSDictionary] as? [CFString: Any],
              let dir = gps[kCGImagePropertyGPSImgDirection] as? NSNumber else { return nil }
        return dir.floatValue
    }
}

/// Presenta el selector y resuelve la escuela.
struct SubmitBlockPhotoFlow: View {
    let schools: [School]
    /// Si se entra DESDE una escuela (su mapa -> PROPONER), ya sabemos a cual
    /// pertenece: no hay que buscarla por cercania ni rechazar la foto por
    /// estar lejos del catalogo. Solo hace falta que la foto sepa donde se
    /// hizo, para colocar la piedra en el punto.
    ///
    /// Se resuelve con un parametro en vez de un flujo aparte porque el resto
    /// -selector, lectura de EXIF, semilla- es identico, y dos copias acaban
    /// divergiendo.
    var escuelaFijada: School? = nil
    var onOpenSchool: (String) -> Void
    var onDismiss: () -> Void

    @State private var aviso: String?
    @State private var lanzado = false

    var body: some View {
        Color.clear
            .onAppear {
                guard !lanzado else { return }
                lanzado = true
                presentaSelector()
            }
            .alert("No se puede ubicar la foto", isPresented: Binding(
                get: { aviso != nil }, set: { if !$0 { aviso = nil; onDismiss() } })
            ) {
                Button("ENTENDIDO") { aviso = nil; onDismiss() }
            } message: {
                Text(aviso ?? "")
            }
    }

    private func presentaSelector() {
        presentPhotoPickerResult { result in
            guard let result else { onDismiss(); return }
            Task { @MainActor in
                guard let donde = await PhotoExifReader.read(result) else {
                    aviso = "Esta foto no guarda dónde se hizo, así que no se puede saber a qué escuela pertenece. Prueba con otra: tiene que ser una foto tomada por ti, con la ubicación activada en la cámara."
                    return
                }
                // Desde el mapa de una escuela: la escuela ya esta decidida.
                if let fijada = escuelaFijada {
                    PhotoProposalSeedStore.shared.put(.init(
                        schoolId: fijada.id,
                        image: donde.image,
                        lat: donde.lat, lon: donde.lon,
                        aspect: PhotoPlacement.shared.aspectFromCameraDirection(
                            cameraDegrees: donde.cameraDegrees.map { KotlinFloat(float: $0) })))
                    onOpenSchool(fijada.id)
                    return
                }
                // Versión PLANA a propósito: una clase sellada cruza mal la
                // frontera con Swift y aquí no aporta nada.
                guard let escuela = PhotoPlacement.shared.nearestSchoolWithin(
                    lat: donde.lat, lon: donde.lon, schools: schools,
                    radiusKm: PhotoPlacement.shared.RADIO_ESCUELA_KM) else {
                    let km = PhotoPlacement.shared.nearestSchoolKm(
                        lat: donde.lat, lon: donde.lon, schools: schools)?.doubleValue
                    let lejos = km.map { $0 >= 10 ? "a \(Int($0)) km" : String(format: "a %.1f km", $0) }
                        ?? "no hay ninguna en el catálogo"
                    aviso = "La foto se hizo lejos de cualquier escuela del catálogo (\(lejos)). Si la escuela no existe todavía, puedes proponerla desde «Enviar escuela»."
                    return
                }
                PhotoProposalSeedStore.shared.put(.init(
                    schoolId: escuela.id,
                    image: donde.image,
                    lat: donde.lat, lon: donde.lon,
                    aspect: PhotoPlacement.shared.aspectFromCameraDirection(
                        cameraDegrees: donde.cameraDegrees.map { KotlinFloat(float: $0) })))
                onOpenSchool(escuela.id)
            }
        }
    }
}

private var pickerResultCoordKey: UInt8 = 0

/// Selector de fotos que devuelve el RESULTADO, no solo la imagen: hace falta
/// su identificador para poder preguntarle a la fototeca dónde se hizo.
/// - Parameter intentos: reintentos mientras UIKit no pueda presentar.
///
/// Hace falta porque a esto se llega justo despues de CERRAR la hoja de
/// "Aportar": mientras la hoja se esta cerrando, presentar encima no hace nada
/// -- silenciosamente. Se espera a que la animacion termine.
@MainActor
func presentPhotoPickerResult(intentos: Int = 15,
                              onPick: @escaping (PHPickerResult?) -> Void) {
    let libre = topPresentedViewController().map { !$0.isBeingDismissed && $0.presentedViewController == nil } ?? false
    guard let presenter = topPresentedViewController(), libre else {
        guard intentos > 0 else { onPick(nil); return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
            presentPhotoPickerResult(intentos: intentos - 1, onPick: onPick)
        }
        return
    }
    var config = PHPickerConfiguration(photoLibrary: .shared())
    config.filter = .images
    config.selectionLimit = 1
    let picker = PHPickerViewController(configuration: config)
    let coord = PickerResultCoordinator(onPick: onPick)
    picker.delegate = coord
    objc_setAssociatedObject(picker, &pickerResultCoordKey, coord, .OBJC_ASSOCIATION_RETAIN)
    presenter.present(picker, animated: true)
}

private final class PickerResultCoordinator: NSObject, PHPickerViewControllerDelegate {
    private let onPick: (PHPickerResult?) -> Void
    init(onPick: @escaping (PHPickerResult?) -> Void) { self.onPick = onPick }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        // La foto se entrega AL CERRAR: si se hace antes, la vista de debajo se
        // recompone con el selector aún encima (lección de la cámara de
        // celebración, build 75).
        picker.dismiss(animated: true) { [onPick] in onPick(results.first) }
    }
}
