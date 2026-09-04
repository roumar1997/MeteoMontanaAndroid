import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth


// PANTALLA DE DETALLE de escuela (orquestador) — las piezas viven en:
// SchoolForecastViews / SchoolMapSection / BlockInfoSheet / ContributionSheets /
// SchoolNotesViews / SchoolDetailHelpers (espejo del reparto de Android).

/// Lo que se ofrece descargar para ver la escuela sin cobertura. Espejo de
/// `OfertaFotosOffline` en Android.
struct OfertaFotosOffline {
    let urls: [String]
    let bytes: Int64

    /// "7,0 MB" / "820 KB" — la unidad que se lee de un vistazo.
    var pesoTexto: String {
        let mb = Double(bytes) / (1024 * 1024)
        if mb < 1 { return "\(Int(Double(bytes) / 1024)) KB" }
        return String(format: "%.1f MB", mb).replacingOccurrences(of: ".", with: ",")
    }
}

@MainActor
final class SchoolDetailViewModel: ObservableObject {
    @Published var forecast: Forecast?
    @Published var loading = true
    @Published var errorText: String?
    @Published var isFavorite = false
    @Published var notes: [Note] = []
    @Published var publishing = false
    @Published var monthlyScores: [Int] = []
    @Published var monthlyBestRange: String?
    @Published var isSaved = false          // guardada para offline
    @Published var savingOffline = false
    /// Si != nil, hay que preguntar al usuario si baja las fotos de la escuela.
    @Published var ofertaFotos: OfertaFotosOffline?
    @Published var descargandoFotos = false
    @Published var offlineForecast = false  // previsión mostrada desde caché (sin red)
    @Published var offlineSince: Int64?      // epoch ms de la última actualización cacheada

    private let savedSchools = AppDependencies.shared.container.savedSchools
    private let getBlocks = AppDependencies.shared.container.getBlocks
    private let getForecast: GetForecastUseCase
    private let getMyFavorites: GetMyFavoritesUseCase
    private let addFavorite: AddFavoriteUseCase
    private let removeFavorite: RemoveFavoriteUseCase
    private let getNotes: GetNotesUseCase
    private let createNote: CreateNoteUseCase

    init(
        getForecast: GetForecastUseCase = AppDependencies.shared.container.getForecast,
        getMyFavorites: GetMyFavoritesUseCase = AppDependencies.shared.container.getMyFavorites,
        addFavorite: AddFavoriteUseCase = AppDependencies.shared.container.addFavorite,
        removeFavorite: RemoveFavoriteUseCase = AppDependencies.shared.container.removeFavorite,
        getNotes: GetNotesUseCase = AppDependencies.shared.container.getNotes,
        createNote: CreateNoteUseCase = AppDependencies.shared.container.createNote
    ) {
        self.getForecast = getForecast
        self.getMyFavorites = getMyFavorites
        self.addFavorite = addFavorite
        self.removeFavorite = removeFavorite
        self.getNotes = getNotes
        self.createNote = createNote
    }

    func load(school: School) async {
        let schoolId = school.id
        loading = true; errorText = nil; offlineForecast = false; offlineSince = nil
        do {
            let f = try await getForecast.invoke(schoolId: schoolId)
            forecast = f
            // Cachea para verlo offline más tarde (stale-while-revalidate, como Android).
            try? await savedSchools?.cacheForecast(schoolId: schoolId, forecast: f)
        } catch is CancellationError {
            // El propio `.refreshable` cancela esta tarea si el gesto de
            // deslizar termina antes de que acabe la petición — no es un
            // fallo de red real, así que no se toca nada (Álvaro, 2026-09-04:
            // "por qué al recargar pone sin conexión" con WiFi funcionando).
            loading = false
            return
        } catch {
            // Sin red de verdad: tira de la última previsión guardada/cacheada.
            if let cached = try? await savedSchools?.cachedForecast(schoolId: schoolId) {
                forecast = cached.forecast
                offlineSince = cached.fetchedAtMillis
                offlineForecast = true
            } else {
                errorText = "Sin conexión. Conéctate a internet para ver la previsión (esta escuela no tiene previsión guardada)."
            }
        }
        loading = false
        let favs = try? await getMyFavorites.invoke()
        isFavorite = (favs ?? []).contains { $0.id == schoolId }
        await loadNotes(schoolId: schoolId)
        await loadMonthly(schoolId: schoolId, lat: school.lat, lon: school.lon, rockType: school.rockType)
        await checkSaved(schoolId: schoolId)
        // Si está guardada offline y hemos cargado CON red, refresca el snapshot
        // (sectores + previsión + fotos) para que el offline esté lo más al día.
        if isSaved && !offlineForecast {
            await refreshOffline(school: school)
        }
    }

    /// Re-guarda en silencio el snapshot offline con los datos frescos ya cargados.
    func refreshOffline(school: School) async {
        guard let repo = savedSchools, let f = forecast else { return }
        let blocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
        try? await repo.saveOffline(school: school, blocks: blocks, forecast: f)
        await ImageCache.prefetch(FotosDeEscuela.shared.urlsParaGuardar(blocks: blocks))
    }

    func checkSaved(schoolId: String) async {
        guard let repo = savedSchools else { return }
        isSaved = (try? await repo.loadOffline(id: schoolId)) != nil
    }

    /// Guarda la escuela para OFFLINE: detalle + bloques + vías + forecast. Las
    /// FOTOS se preguntan aparte (ver `ofertaFotos`).
    func saveOffline(school: School) async {
        guard let repo = savedSchools else { return }
        savingOffline = true
        let blocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
        try? await repo.saveOffline(school: school, blocks: blocks, forecast: forecast)
        // Tiles del mapa offline (mismo punto que Android): sin esto, offline el
        // mapa solo mostraba los marcadores, no el mapa de fondo, si no se había
        // visitado antes esa zona con red.
        OfflineTileManager.downloadFor(schoolId: school.id, lat: school.lat, lon: school.lon)
        isSaved = true; savingOffline = false
        // Las fotos se PREGUNTAN (paridad con Android; petición de Rodrigo
        // 2026-08-16: "que avise, así sienten que tienen el control"). Los datos
        // pesan poco y se guardan sin molestar; las fotos son casi todo el peso
        // y pueden estar gastando datos móviles.
        let fotos = FotosDeEscuela.shared.urlsParaGuardar(blocks: blocks)
        if !fotos.isEmpty {
            ofertaFotos = OfertaFotosOffline(
                urls: fotos,
                bytes: FotosDeEscuela.shared.pesoEstimadoBytes(urls: fotos)
            )
        }
    }

    /// El usuario aceptó bajarse las fotos.
    func descargarFotosOffline() async {
        guard let oferta = ofertaFotos else { return }
        ofertaFotos = nil
        descargandoFotos = true
        await ImageCache.prefetch(oferta.urls)
        descargandoFotos = false
    }

    func removeOffline(schoolId: String) async {
        guard let repo = savedSchools else { return }
        try? await repo.remove(id: schoolId)
        OfflineTileManager.removeFor(schoolId: schoolId)
        isSaved = false
    }

    /// Stats mensuales (mejores meses del año). Cache-backed; null si no hay BD.
    func loadMonthly(schoolId: String, lat: Double, lon: Double, rockType: String?) async {
        guard let repo = AppDependencies.shared.container.monthlyStats else { return }
        if let m = try? await repo.get(schoolId: schoolId, lat: lat, lon: lon, rockType: rockType) {
            monthlyScores = m.scores.map { $0.intValue }
            monthlyBestRange = m.bestRange
        }
    }

    func loadNotes(schoolId: String) async {
        notes = (try? await getNotes.invoke(schoolId: schoolId)) ?? []
    }

    /** Voto de utilidad (1/-1; repetir retira) y recarga: vuelve ordenado por utilidad. */
    func voteNote(_ note: Note, value: Int) async {
        _ = try? await AppDependencies.shared.container.noteApi
            .voteNote(noteId: note.id, value: Int32(value))
        notes = (try? await getNotes.invoke(schoolId: note.schoolId)) ?? notes
    }

    /// Publica una nota (texto + foto opcional) y refresca la lista. Si hay
    /// imagen, la sube a Firebase Storage y adjunta su URL.
    func publishNote(schoolId: String, text: String, image: UIImage?) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        publishing = true
        var photoUrl: String?
        if let img = image {
            photoUrl = try? await StorageUploader.uploadNotePhoto(img, schoolId: schoolId)
        }
        _ = try? await createNote.invoke(schoolId: schoolId, text: trimmed, photoUrl: photoUrl)
        await loadNotes(schoolId: schoolId)
        publishing = false
    }

    /// Toggle optimista con revert si falla (espejo de Android).
    func toggleFavorite(schoolId: String) {
        let was = isFavorite
        isFavorite = !was
        Task {
            do {
                if was { try await removeFavorite.invoke(schoolId: schoolId) }
                else { try await addFavorite.invoke(schoolId: schoolId) }
            } catch { isFavorite = was }
        }
    }
}

struct SchoolDetailView: View {
    let school: School
    /// Si se indica, al abrir se despliega el mapa y se abre la piedra que
    /// contiene esa vía (deep-link desde el diario).
    var openVia: String? = nil
    @StateObject private var vm = SchoolDetailViewModel()
    @State private var factorsExpanded = false
    @State private var selectedDay: DayForecast?
    @Environment(\.dismiss) private var dismiss
    // Deslizar hacia abajo pide otra vez quién hay en "Estoy aquí" — mejor a
    // demanda que sondear sola cada X segundos (Álvaro, 2026-09-04).
    @State private var presenceRefreshTrigger = 0

    var body: some View {
        VStack(spacing: 0) {
            headerRow
            // Fijo, fuera del scroll: si no se ve nada más que el título hasta
            // que bajas, nadie sabe que hay alguien ahí (Álvaro, 2026-09-03,
            // tras verlo enterrado bajo el pronóstico en la primera prueba).
            SchoolPresenceRow(schoolId: school.id, schoolName: school.name,
                              myUid: AppDependencies.shared.authBridge.currentUid(),
                              refreshTrigger: presenceRefreshTrigger)
            scrollContent
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarHidden(true)
        .sheet(item: $selectedDay) { d in
            DayDetailView(day: d, allHours: vm.forecast?.hours ?? [])
        }
        .task { await vm.load(school: school) }
        .alert("¿Guardar también las fotos?",
               isPresented: Binding(get: { vm.ofertaFotos != nil },
                                    set: { if !$0 { vm.ofertaFotos = nil } })) {
            Button("Descargar") { Task { await vm.descargarFotosOffline() } }
            Button("Ahora no", role: .cancel) { vm.ofertaFotos = nil }
        } message: {
            if let o = vm.ofertaFotos {
                Text("La escuela ya está guardada. Bajar sus \(o.urls.count) fotos (\(o.pesoTexto)) "
                     + "te deja ver los topos en la roca aunque no haya cobertura.\n\n"
                     + "Si dices que no, tendrás los nombres, los grados y las líneas, "
                     + "pero no las fotos sobre las que van dibujadas.")
            }
        }
        .overlay {
            if vm.descargandoFotos {
                ProgressView("Guardando las fotos…")
                    .padding(20)
                    .background(Cumbre.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }

    /// Cabecera propia (no la barra nativa de iOS): con 5 acciones a la
    /// derecha, la barra nativa podía comprimir el título hasta hacerlo
    /// desaparecer del todo en nombres cortos como "Proaza" o "Cabo Negro"
    /// (Rodrigo, con capturas, 2026-08-21). Android ya resolvía esto con una
    /// fila propia donde el título se queda con el hueco sobrante
    /// (`weight(1f)` + ellipsis) — mismo patrón aquí, dos píldoras como en
    /// el resto de cabeceras de esta sesión.
    private var headerRow: some View {
        HStack(spacing: 8) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                    .frame(width: 36, height: 36)
            }
            .background(Cumbre.paper, in: Circle())
            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))

            Text(school.name)
                .font(.system(size: 19, weight: .semibold))
                .lineLimit(1)
                .truncationMode(.tail)
                .foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 0) {
                HelpButton(topicKey: "detail")
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: {
                    Image(systemName: "arrow.triangle.turn.up.right.diamond").foregroundStyle(Cumbre.ink3)
                        .frame(width: 34, height: 34)
                }
                if let f = vm.forecast {
                    ShareLink(item: conditionsShareSummary(f)) {
                        Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink3)
                            .frame(width: 34, height: 34)
                    }
                }
                Button { vm.toggleFavorite(schoolId: school.id) } label: {
                    Image(systemName: vm.isFavorite ? "star.fill" : "star")
                        .foregroundStyle(vm.isFavorite ? Cumbre.terra : Cumbre.ink3)
                        .frame(width: 34, height: 34)
                }
                if vm.savingOffline {
                    ProgressView().frame(width: 34, height: 34)
                } else {
                    Button {
                        Task {
                            if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                            else { await vm.saveOffline(school: school) }
                        }
                    } label: {
                        Image(systemName: vm.isSaved ? "arrow.down.circle.fill" : "arrow.down.circle")
                            .foregroundStyle(vm.isSaved ? Cumbre.terra : Cumbre.ink3)
                            .frame(width: 34, height: 34)
                    }
                }
            }
            .padding(.horizontal, 2)
            .background(Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    private var scrollContent: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 60)
                    .frame(maxWidth: .infinity, minHeight: 320)
            } else if let err = vm.errorText {
                ContentUnavailableView("Sin previsión", systemImage: "cloud.slash", description: Text(err))
                    .padding(.top, 60)
            } else if let f = vm.forecast {
                FirstTimeHint(
                    hintKey: "detail_offline",
                    text: "Toca ↓ (arriba) para guardar esta escuela y verla sin conexión, incluyendo el mapa y las piedras."
                )
                FirstTimeHint(
                    hintKey: "detail_propose",
                    text: "Despliega el mapa de abajo y usa + PROPONER para añadir piedras, parkings o sectores que falten. Un admin lo revisa."
                )
                FirstTimeHint(
                    hintKey: "detail_tick",
                    text: "Toca una piedra en el mapa para ver sus vías. El círculo ○ marca una vía como hecha y la guarda en tu diario."
                )
                if vm.offlineForecast {
                    HStack(spacing: 6) {
                        Image(systemName: "wifi.slash").font(.system(size: 11))
                        Text(vm.offlineSince.map { "SIN CONEXIÓN · ACTUALIZADO \(relativeUpdated($0).uppercased())" }
                             ?? "SIN CONEXIÓN · PREVISIÓN GUARDADA").eyebrow()
                    }
                    .foregroundStyle(Cumbre.terra)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 8)
                    .background(Cumbre.terraBg)
                }
                ForecastBodyView(
                    forecast: f,
                    directions: (lat: school.lat, lon: school.lon, label: school.name),
                    factorsExpanded: $factorsExpanded,
                    onSelectDay: { selectedDay = $0 },
                    // Boletín de montaña ENCIMA del mapa (paridad con Android).
                    mapSlot: AnyView(VStack(spacing: 0) {
                        MountainBulletinSection(lat: school.lat, lon: school.lon, country: school.country)
                        SchoolMapSection(school: school, openVia: openVia)
                    })
                )
            } else {
                // Sin previsión: el mapa (y el boletín) van igualmente.
                MountainBulletinSection(lat: school.lat, lon: school.lon, country: school.country)
                SchoolMapSection(school: school, openVia: openVia)
            }
            // Notas comunitarias — ahora ENCIMA de "mejores meses".
            NotesSectionView(
                notes: vm.notes,
                publishing: vm.publishing,
                onPublish: { text, image in Task { await vm.publishNote(schoolId: school.id, text: text, image: image) } },
                onVote: { note, v in Task { await vm.voteNote(note, value: v) } }
            )
            // Mejores meses del año (stats mensuales del backend, cacheadas).
            if !vm.monthlyScores.isEmpty {
                MonthlyStatsSection(scores: vm.monthlyScores, bestRange: vm.monthlyBestRange)
            }
        }
        // Deslizar hacia abajo recarga el tiempo Y pide otra vez quién hay en
        // "Estoy aquí" (Álvaro, 2026-09-04: mejor a demanda que sondear sola).
        .refreshable {
            await vm.load(school: school)
            presenceRefreshTrigger += 1
        }
    }
}

/// "Mejores meses del año" — barras de score medio por mes (3 años de histórico,
/// calculado en el backend). Espejo de la sección mensual de Android.
private struct MonthlyStatsSection: View {
    let scores: [Int]
    let bestRange: String?
    private let months = ["E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle("MEJORES MESES")
            if let r = bestRange, !r.isEmpty {
                Text("Mejor época: \(r)")
                    .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 16)
            }
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(scores.prefix(12).enumerated()), id: \.offset) { i, s in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Cumbre.score(s))
                            .frame(height: max(4, CGFloat(s) * 0.7))
                        Text(months[i % 12]).font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 90, alignment: .bottom)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
    }
}

/// Sección de mapa de la escuela (plegable). Muestra un MapLibre con tiles
/// topográficos, un marcador en la escuela y el botón "CÓMO LLEGAR". Los bloques
/// (parking/piedras/zonas) se añadirán como marcadores en una iteración siguiente.

struct SchoolDetailLoaderView: View {
    let schoolId: String
    @State private var school: School?
    @State private var failed = false

    var body: some View {
        Group {
            if let s = school {
                SchoolDetailView(school: s)
            } else if failed {
                ContentUnavailableView("Escuela no encontrada", systemImage: "mappin.slash")
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: schoolId) {
                school = s
            } else { failed = true }
        }
    }
}

/// Cuerpo del forecast (réplica de ForecastBody.kt). Reutilizado por el detalle
/// de escuela y por el tab Tiempo (en tu ubicación). `directions` es opcional:
/// el tab Tiempo no muestra "CÓMO LLEGAR" (no hay escuela destino).

#Preview {
    NavigationStack {
        SchoolDetailView(school: School(id: "x", name: "Demo", location: "Demo", region: "Aragón",
                                        style: "Boulder", rockType: "Caliza", lat: 0, lon: 0, source: nil,
                                        country: "ES"))
    }
}

/// Polígono irregular con la silueta del marcador de piedra (botonera).
