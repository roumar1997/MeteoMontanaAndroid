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

    /// Solo la IMAGEN (y el rumbo de la cámara si la foto lo trae), SIN exigir
    /// que tenga ubicación.
    ///
    /// `read` la exige a propósito, porque en "aportar una piedra desde una
    /// foto" la ubicación ES el dato que se busca. Pero al añadir una foto a
    /// mano a una piedra que YA has colocado tú en el mapa, la ubicación no
    /// pinta nada — y exigirla rechazaba fotos perfectamente válidas, como las
    /// que te pasa otra persona (Rodrigo, build 143: "no se pudo cargar la foto"
    /// con una foto que le habían enviado).
    ///
    /// Si la foto no está en la fototeca (sin identificador o sin permiso), se
    /// cae a leer los bytes del propio selector: se pierde el rumbo, pero
    /// funciona — que es lo que importa aquí.
    static func readImagen(_ result: PHPickerResult) async -> (image: UIImage, cameraDegrees: Float?)? {
        if let id = result.assetIdentifier, await autorizado(),
           let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
           let (imagen, rumbo) = await imagenYRumbo(asset) {
            return (imagen, rumbo)
        }
        return await withCheckedContinuation { cont in
            let provider = result.itemProvider
            guard provider.canLoadObject(ofClass: UIImage.self) else {
                cont.resume(returning: nil); return
            }
            provider.loadObject(ofClass: UIImage.self) { objeto, _ in
                cont.resume(returning: (objeto as? UIImage).map { ($0, nil) })
            }
        }
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
    // Sin EXIF (o lejos de cualquier escuela) y SIN escuela fijada: no podemos
    // adivinar dónde fue, pero el usuario sí lo sabe — que la elija de una
    // lista en vez de bloquear sin alternativa (Rodrigo, 2026-08-21: caso
    // real, foto reenviada por WhatsApp — WhatsApp borra el EXIF de TODAS
    // las fotos que reenvía, así que ni Cumbre ni el propio Android pueden
    // hacer nada con esa copia).
    @State private var eligiendoEscuela = false
    @State private var pendienteImagen: UIImage?
    @State private var pendienteAspect: String?
    // Escuela fijada + sin ubicación: colocar en el centro SIN avisar confunde
    // (Rodrigo, 2026-08-21: "que te diga que puedes ponerlo a mano pero que
    // esa foto no tiene ubicación"). Se abre la escuela solo tras "ENTENDIDO".
    @State private var sinUbicacionEnEscuela: School?
    @State private var sinUbicacionImagen: UIImage?
    // Elegir origen ANTES de lanzar nada: cámara en el momento o galería
    // (Rodrigo, 2026-08-21: "que te permita hacerla en ese mismo momento").
    @State private var eligiendoOrigen = true

    var body: some View {
        Color.clear
            .confirmationDialog("¿Cómo quieres la foto?", isPresented: $eligiendoOrigen) {
                Button("Hacer foto ahora") { presentaCamara() }
                Button("Elegir de galería") { presentaSelector() }
                Button("Cancelar", role: .cancel) { onDismiss() }
            }
            .alert("No se puede ubicar la foto", isPresented: Binding(
                get: { aviso != nil }, set: { if !$0 { aviso = nil; onDismiss() } })
            ) {
                Button("ENTENDIDO") { aviso = nil; onDismiss() }
            } message: {
                Text(aviso ?? "")
            }
            .alert("Foto sin ubicación", isPresented: Binding(
                get: { sinUbicacionEnEscuela != nil }, set: { if !$0 { sinUbicacionEnEscuela = nil } })
            ) {
                Button("ENTENDIDO") {
                    if let fijada = sinUbicacionEnEscuela, let img = sinUbicacionImagen {
                        PhotoProposalSeedStore.shared.put(.init(
                            schoolId: fijada.id, image: img,
                            lat: fijada.lat, lon: fijada.lon, aspect: nil))
                        onOpenSchool(fijada.id)
                    }
                    sinUbicacionEnEscuela = nil
                }
            } message: {
                Text("Esta foto no trae ubicación: coloca tú el punto en el mapa.")
            }
            .sheet(isPresented: $eligiendoEscuela, onDismiss: { onDismiss() }) {
                SchoolPickerForPhoto(
                    schools: schools,
                    onPick: { escuela in
                        eligiendoEscuela = false
                        guard let img = pendienteImagen else { return }
                        PhotoProposalSeedStore.shared.put(.init(
                            schoolId: escuela.id, image: img,
                            lat: escuela.lat, lon: escuela.lon, aspect: pendienteAspect))
                        onOpenSchool(escuela.id)
                    },
                    onCancel: { eligiendoEscuela = false })
            }
    }

    /// Cámara en el momento: la ubicación NO sale del EXIF —una foto recién
    /// hecha por `UIImagePickerController` no lo trae fiable— sino del GPS
    /// del móvil AHORA MISMO, que es justo lo que hace falta al fotografiar
    /// en la roca.
    private func presentaCamara() {
        presentSystemCamera(context: "proponer-piedra") { image in
            let bridge = AppDependencies.shared.locationBridge
            @MainActor func continuar(_ loc: UserLocation?) {
                if let fijada = escuelaFijada {
                    if loc == nil {
                        sinUbicacionImagen = image
                        sinUbicacionEnEscuela = fijada
                    } else {
                        PhotoProposalSeedStore.shared.put(.init(
                            schoolId: fijada.id, image: image,
                            lat: loc!.lat, lon: loc!.lon, aspect: nil))
                        onOpenSchool(fijada.id)
                    }
                    return
                }
                guard let loc else {
                    pendienteImagen = image
                    pendienteAspect = nil
                    eligiendoEscuela = true
                    return
                }
                guard let escuela = PhotoPlacement.shared.nearestSchoolWithin(
                    lat: loc.lat, lon: loc.lon, schools: schools,
                    radiusKm: PhotoPlacement.shared.RADIO_ESCUELA_KM) else {
                    pendienteImagen = image
                    pendienteAspect = nil
                    eligiendoEscuela = true
                    return
                }
                PhotoProposalSeedStore.shared.put(.init(
                    schoolId: escuela.id, image: image,
                    lat: loc.lat, lon: loc.lon, aspect: nil))
                onOpenSchool(escuela.id)
            }
            if bridge.hasPermission() {
                bridge.current(callback: { loc in Task { @MainActor in continuar(loc) } })
            } else {
                bridge.requestPermission()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    bridge.current(callback: { loc in Task { @MainActor in continuar(loc) } })
                }
            }
        }
    }

    private func presentaSelector() {
        presentPhotoPickerResult { result in
            guard let result else { onDismiss(); return }
            Task { @MainActor in
                guard let donde = await PhotoExifReader.read(result) else {
                    // Desde el mapa de una escuela: ya la sabemos, no hace falta
                    // la ubicación de la foto — se usa el centro de la escuela
                    // como semilla y el punto se ajusta a mano, como siempre.
                    if let fijada = escuelaFijada, let img = await PhotoExifReader.readImagen(result) {
                        sinUbicacionImagen = img.image
                        sinUbicacionEnEscuela = fijada
                        return
                    }
                    guard let img = await PhotoExifReader.readImagen(result) else {
                        aviso = "No se pudo leer esta foto. Prueba con otra."
                        return
                    }
                    pendienteImagen = img.image
                    pendienteAspect = nil
                    eligiendoEscuela = true
                    return
                }
                // Desde el mapa de una escuela: la escuela ya esta decidida.
                if let fijada = escuelaFijada {
                    // La piedra se coloca DONDE SE HIZO LA FOTO. Si la foto es
                    // de otro sitio, acabaria en el mapa de esta escuela a
                    // kilometros de ella. Este control lo quite al entrar desde
                    // la escuela pensando que "ya sabemos cual es" — y Rodrigo
                    // colo una foto de Valsain en Zarzalejo, a 32 km.
                    let km = PhotoPlacement.shared.kmBetween(
                        lat1: donde.lat, lon1: donde.lon,
                        lat2: fijada.lat, lon2: fijada.lon)
                    if km > PhotoPlacement.shared.RADIO_ESCUELA_KM {
                        aviso = "Esa foto se hizo a \(Int(km)) km de \(fijada.name). Elige una foto tomada en esta escuela."
                        return
                    }
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
                    // Trae ubicación pero lejos de cualquier escuela del
                    // catálogo: mismo salvavidas que sin EXIF, elegirla a mano.
                    pendienteImagen = donde.image
                    pendienteAspect = PhotoPlacement.shared.aspectFromCameraDirection(
                        cameraDegrees: donde.cameraDegrees.map { KotlinFloat(float: $0) })
                    eligiendoEscuela = true
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

/// Selector de escuela a mano, cuando la foto no trae ubicación utilizable.
private struct SchoolPickerForPhoto: View {
    let schools: [School]
    let onPick: (School) -> Void
    let onCancel: () -> Void
    @State private var query = ""

    private var filtradas: [School] {
        query.isEmpty ? schools : schools.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Esta foto no trae ubicación (frecuente si llegó por WhatsApp — "
                     + "borra esos datos al reenviarla). Elige la escuela y coloca "
                     + "el punto a mano en el mapa.")
                    .font(.system(size: 13))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 16).padding(.top, 8)
                List(filtradas, id: \.id) { escuela in
                    Text(escuela.name)
                        .onTapGesture { onPick(escuela) }
                }
                .searchable(text: $query, prompt: "Buscar escuela…")
                .listStyle(.plain)
            }
            .navigationTitle("¿En qué escuela es?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: onCancel)
                }
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
