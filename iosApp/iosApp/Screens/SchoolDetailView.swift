import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth


// PANTALLA DE DETALLE de escuela (orquestador) — las piezas viven en:
// SchoolForecastViews / SchoolMapSection / BlockInfoSheet / ContributionSheets /
// SchoolNotesViews / SchoolDetailHelpers (espejo del reparto de Android).

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
        } catch {
            // Sin red: tira de la última previsión guardada/cacheada de esta escuela.
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
        await ImageCache.prefetch(blocks.compactMap { $0.photoPath })
    }

    func checkSaved(schoolId: String) async {
        guard let repo = savedSchools else { return }
        isSaved = (try? await repo.loadOffline(id: schoolId)) != nil
    }

    /// Guarda la escuela para OFFLINE: detalle + bloques + vías + forecast, y
    /// pre-descarga las fotos de las piedras a la caché de imágenes.
    func saveOffline(school: School) async {
        guard let repo = savedSchools else { return }
        savingOffline = true
        let blocks = (try? await getBlocks.invoke(schoolId: school.id)) ?? []
        try? await repo.saveOffline(school: school, blocks: blocks, forecast: forecast)
        await ImageCache.prefetch(blocks.compactMap { $0.photoPath })
        // Tiles del mapa offline (mismo punto que Android): sin esto, offline el
        // mapa solo mostraba los marcadores, no el mapa de fondo, si no se había
        // visitado antes esa zona con red.
        OfflineTileManager.downloadFor(schoolId: school.id, lat: school.lat, lon: school.lon)
        isSaved = true; savingOffline = false
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

    var body: some View {
        ScrollView {
            if vm.loading {
                ProgressView().padding(.top, 60)
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
                        MountainBulletinSection(lat: school.lat, lon: school.lon)
                        SchoolMapSection(school: school, openVia: openVia)
                    })
                )
            } else {
                // Sin previsión: el mapa (y el boletín) van igualmente.
                MountainBulletinSection(lat: school.lat, lon: school.lon)
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
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(school.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { HelpButton(topicKey: "detail") }
            // Compartir (icono, mismo estilo que la estrella) — solo si hay
            // forecast cargado para resumir las condiciones.
            ToolbarItem(placement: .topBarTrailing) {
                if let f = vm.forecast {
                    ShareLink(item: conditionsShareSummary(f)) {
                        Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            // Cómo llegar (Google Maps, cae a Apple Maps) — ruta directa a la escuela.
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(school.lat),\(school.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(school.lat),\(school.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: {
                    Image(systemName: "arrow.triangle.turn.up.right.diamond").foregroundStyle(Cumbre.ink3)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { vm.toggleFavorite(schoolId: school.id) } label: {
                    Image(systemName: vm.isFavorite ? "star.fill" : "star")
                        .foregroundStyle(vm.isFavorite ? Cumbre.terra : Cumbre.ink3)
                }
            }
            // Guardar para offline (detalle + mapa + piedras + fotos).
            ToolbarItem(placement: .topBarTrailing) {
                if vm.savingOffline {
                    ProgressView()
                } else {
                    Button {
                        Task {
                            if vm.isSaved { await vm.removeOffline(schoolId: school.id) }
                            else { await vm.saveOffline(school: school) }
                        }
                    } label: {
                        Image(systemName: vm.isSaved ? "arrow.down.circle.fill" : "arrow.down.circle")
                            .foregroundStyle(vm.isSaved ? Cumbre.terra : Cumbre.ink3)
                    }
                }
            }
        }
        .sheet(item: $selectedDay) { d in
            DayDetailView(day: d, allHours: vm.forecast?.hours ?? [])
        }
        .task { await vm.load(school: school) }
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
                                        style: "Boulder", rockType: "Caliza", lat: 0, lon: 0, source: nil))
    }
}

/// Polígono irregular con la silueta del marcador de piedra (botonera).
