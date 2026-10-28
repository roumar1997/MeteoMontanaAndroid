import SwiftUI
import PhotosUI
import Shared

/// "¿Qué piedra es esta?" desde la pantalla de una escuela: una foto (hecha ahora o de la galería) y la app dice cuál
/// de las piedras de ESA escuela es. Todo se hace en el móvil: la foto y la ubicación no se envían a ningún sitio y no
/// hay IA de por medio, así que no gasta cupo ni cuesta dinero (solo se descargan las fotos de las piedras que se
/// comparan, y quedan guardadas en el móvil).
@MainActor
final class SchoolPhotoFinder: ObservableObject {
    enum State {
        case idle
        case working(String)
        /// `weak` = ni la más parecida se parece de verdad: puede que esa piedra aún no esté en Cumbre.
        case found([AssistantPhotoMatcher.Candidate], sure: Bool, approximate: Bool, weak: Bool)
        case failed(String)
    }

    @Published private(set) var state: State = .idle
    @Published var showSheet = false

    private let container = AppDependencies.shared.container
    private let locationBridge = AppDependencies.shared.locationBridge
    /// Sube cada vez que se cierra o se para: lo que estaba en marcha ve que ya no es lo actual y no pinta nada.
    private var generation = 0

    /// Cierra la hoja y descarta lo que estuviera calculándose.
    func cancel() {
        generation += 1
        state = .idle
        showSheet = false
    }

    func fail(_ text: String) {
        generation += 1
        state = .failed(text)
        showSheet = true
    }

    /// Foto de la galería. La ubicación sale de la fototeca, del propio archivo o, si no la trae, de donde estás ahora.
    func identify(result: PHPickerResult, school: School) async {
        generation += 1
        let gen = generation
        state = .working(L("Leyendo dónde se hizo la foto…"))
        showSheet = true
        var image: UIImage?
        var lat: Double?, lon: Double?
        // Cada lectura con tiempo máximo: una foto en iCloud o un permiso a medias no pueden dejar la hoja colgada.
        if let found = await AsyncTimeout.run(20, { await PhotoExifReader.read(result) }) {
            image = found.image; lat = found.lat; lon = found.lon
        } else if let only = await AsyncTimeout.run(20, { await PhotoExifReader.readImagen(result) }) {
            image = only.image
            if let gps = await AsyncTimeout.run(10, { await PhotoExifReader.gpsFromFile(result) }) { lat = gps.lat; lon = gps.lon }
        }
        guard gen == generation else { return }
        guard let image else {
            state = .failed(L("No he podido abrir esa foto (si está en iCloud, puede que no se haya descargado). Prueba con otra o hazla con la cámara."))
            return
        }
        var approximate = false
        if lat == nil, let here = await AsyncTimeout.run(10, { await self.currentLocation() }) {
            lat = here.lat; lon = here.lon; approximate = true
        }
        guard gen == generation else { return }
        await run(image: image, lat: lat, lon: lon, approximate: approximate, school: school, gen: gen)
    }

    /// Foto hecha ahora con la cámara: está delante de la piedra, así que la ubicación de este momento es la buena.
    func identify(camera image: UIImage, school: School) async {
        generation += 1
        let gen = generation
        state = .working(L("Mirando dónde estás…"))
        showSheet = true
        let here = await AsyncTimeout.run(10, { await self.currentLocation() })
        guard gen == generation else { return }
        await run(image: image, lat: here?.lat, lon: here?.lon, approximate: false, school: school, gen: gen)
    }

    private func run(image: UIImage, lat: Double?, lon: Double?, approximate: Bool, school: School, gen: Int) async {
        let outcome = await AsyncTimeout.run(45, {
            await AssistantPhotoMatcher.matchInSchool(
                image: image, lat: lat, lon: lon, approximate: approximate, school: school,
                container: self.container,
                onStage: { stage in if gen == self.generation { self.state = .working(stage.text) } })
        })
        guard gen == generation else { return }
        guard let outcome else {
            state = .failed(L("Ha tardado demasiado. Inténtalo otra vez con buena cobertura."))
            return
        }
        switch outcome {
        case let .found(candidates, sure, approx):
            let weak = (candidates.first?.similarity ?? 0) < 0.35
            state = .found(candidates, sure: sure && !weak, approximate: approx, weak: weak)
        case .noStones(let name):
            state = .failed(L("En %@ aún no hay piedras con foto para comparar.", name))
        case .noData:
            state = .failed(L("No he podido cargar las piedras de esta escuela. Comprueba la conexión e inténtalo de nuevo."))
        case .noLocation, .noSchoolNear:
            state = .failed(L("No he podido situar la foto."))     // no se dan dentro de una escuela
        }
    }

    private func currentLocation() async -> (lat: Double, lon: Double)? {
        guard locationBridge.hasPermission() else { return nil }
        let loc: UserLocation? = await withCheckedContinuation { cont in
            locationBridge.current { cont.resume(returning: $0) }
        }
        return loc.map { ($0.lat, $0.lon) }
    }
}

/// Icono de cámara junto al botón AYUDA del título de la escuela: menú con "Hacer una foto" y "Elegir de la galería".
struct SchoolPhotoButton: View {
    @ObservedObject var finder: SchoolPhotoFinder
    let school: School

    var body: some View {
        Menu {
            Button { takePhoto() } label: { Label(L("Hacer una foto"), systemImage: "camera") }
            Button { pickPhoto() } label: { Label(L("Elegir de la galería"), systemImage: "photo.on.rectangle") }
        } label: {
            Image(systemName: "camera.viewfinder")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Cumbre.terra)
                .frame(width: 34, height: 34)
                .background(Cumbre.terra.opacity(0.10), in: Circle())
                .overlay(Circle().stroke(Cumbre.terra, lineWidth: 1.2))
        }
        .accessibilityLabel(L("¿Qué piedra es esta?"))
    }

    private func takePhoto() {
        CameraAccess.request { granted in
            guard granted else {
                finder.fail(L("Para hacer la foto necesito permiso de cámara. Puedes dárselo en Ajustes."))
                return
            }
            presentSystemCamera(context: "school_photo") { image in
                Task { await finder.identify(camera: image, school: school) }
            }
        }
    }

    private func pickPhoto() {
        presentPhotoPickerResult { result in
            guard let result else { return }
            Task { await finder.identify(result: result, school: school) }
        }
    }
}

/// Hoja con el resultado: las 3 piedras más probables con su foto. Tocar una abre esa piedra en el mapa de la escuela.
struct SchoolPhotoSheet: View {
    @ObservedObject var finder: SchoolPhotoFinder
    let schoolName: String
    /// Abre la piedra (por su primera vía) en el mapa de la escuela.
    let onOpen: (String) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) { content }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(L("¿Qué piedra es esta?"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(NSLocalizedString("common_close", comment: "")) { finder.cancel() }
                        .foregroundStyle(Cumbre.terra)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder private var content: some View {
        switch finder.state {
        case .idle:
            EmptyView()
        case .working(let note):
            HStack(spacing: 12) {
                ProgressView().tint(Cumbre.terra)
                Text(note).font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
            }
            .padding(.top, 8)
            Button { finder.cancel() } label: {
                HStack(spacing: 5) {
                    Image(systemName: "stop.fill").font(.system(size: 10, weight: .bold))
                    Text(L("Parar")).font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(Cumbre.ink)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
        case .failed(let text):
            Text(text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
        case let .found(candidates, sure, approximate, weak):
            Text(AssistantPresenter.photoIntro(sure: sure, approximate: approximate, school: schoolName, weak: weak))
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
            if (candidates.first?.meters ?? 0) < 0 {
                Text(L("La foto no dice dónde se hizo: la he comparado con las piedras de la escuela, solo por cómo se ven."))
                    .font(.system(size: 12.5)).foregroundStyle(Cumbre.ink3)
            }
            ForEach(candidates) { row($0) }
        }
    }

    private func row(_ c: AssistantPhotoMatcher.Candidate) -> some View {
        Button { if let id = c.lineId { onOpen(id) } } label: {
            HStack(spacing: 12) {
                if let url = c.photoUrl {
                    MiniTopoThumbnail(photoUrl: url, points: [], grade: nil)
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                } else {
                    Image(systemName: "photo")
                        .font(.system(size: 18)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 56, height: 56)
                        .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 10))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(c.blockName).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    Text(c.lineId == nil ? L("Esta piedra aún no tiene vías para abrir.") : AssistantPresenter.photoDetail(c))
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                if c.lineId != nil {
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(10)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(c.lineId == nil)
    }
}
