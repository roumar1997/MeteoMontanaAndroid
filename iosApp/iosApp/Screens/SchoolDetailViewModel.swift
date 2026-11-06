import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Estado de la ficha de escuela y la oferta de fotos sin conexión (separados de la vista).

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
    /// Procesionaria: ¿alguna vez confirmó alguien haberla visto aquí? Para
    /// siempre. Sin datos fiables de dónde hay pinos, es la única señal real.
    @Published var hasKnownProcessionary = false
    /// ¿Toca avisar AHORA? (zona conocida + temporada dic-may).
    @Published var processionaryAlertActive = false
    /// ¿Alguien ha marcado "las he visto antes de tiempo" (aviso puntual, con caducidad)?
    @Published var processionaryActiveNowSet = false

    private let confirmProcessionaryUseCase = AppDependencies.shared.container.confirmProcessionary
    private let retractProcessionaryUseCase = AppDependencies.shared.container.retractProcessionary
    private let reportProcessionaryActiveNowUseCase = AppDependencies.shared.container.reportProcessionaryActiveNow
    private let clearProcessionaryActiveNowUseCase = AppDependencies.shared.container.clearProcessionaryActiveNow
    private let savedSchools = AppDependencies.shared.container.savedSchools
    private let getBlocks = AppDependencies.shared.container.getBlocks
    private let getForecast: GetForecastUseCase
    private let getMyFavorites: GetMyFavoritesUseCase
    private let addFavorite: AddFavoriteUseCase
    private let removeFavorite: RemoveFavoriteUseCase
    private let getNotes: GetNotesUseCase
    private let createNote: CreateNoteUseCase
    private let networkMonitor = AppDependencies.shared.container.networkMonitor

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
        hasKnownProcessionary = school.hasKnownProcessionary
        processionaryAlertActive = school.processionaryAlertActive
        processionaryActiveNowSet = school.processionaryActiveNowSet
        // Sin red EN ABSOLUTO (el sistema ya lo sabe): ni lo intentamos. Antes
        // se esperaba el timeout completo de Ktor (hasta 30s) para acabar
        // cayendo al mismo forecast cacheado de todas formas — Álvaro,
        // 2026-09-30: "cuando no tienes conexion, absolutamente nada, tarda
        // en entrar en la escuela". Espejo del fix en SchoolDetailLoader.kt
        // (Android), que usa el margen de 8s solo cuando SÍ hay red pero va mal.
        let sinRedConocida = networkMonitor.map { !$0.isOnlineNow() } ?? false
        do {
            guard !sinRedConocida else { throw KnownOfflineError() }
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
                errorText = L("Sin conexión. Conéctate a internet para ver la previsión (esta escuela no tiene previsión guardada).")
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
        // El mapa general de la GUÍA (imagen), para verla entera sin conexión.
        _ = await GuideOverviewStore.generate(schoolId: school.id, blocks: blocks)
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
    /// La oferta llega POR PARÁMETRO, no se lee de `ofertaFotos`: al pulsar el
    /// botón del aviso SwiftUI cierra el alert y su Binding pone `ofertaFotos`
    /// a nil antes de que arranque esta tarea — con el guard de antes salía
    /// sin descargar nada ni enseñar la ruedecita (Álvaro, 2026-10-02).
    func descargarFotosOffline(_ oferta: OfertaFotosOffline) async {
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

    /// "Sí que hay en este sector": marca la escuela para siempre. Toggleable.
    func confirmProcessionary(schoolId: String) {
        guard !hasKnownProcessionary else { return }
        Task {
            do {
                try await confirmProcessionaryUseCase.invoke(schoolId: schoolId)
                hasKnownProcessionary = true
                processionaryAlertActive = ProcessionarySeason.shared.isInSeason() || processionaryActiveNowSet
                ProcessionaryOverrideStore.shared.set(schoolId: schoolId, alertActive: processionaryAlertActive)
            } catch {}
        }
    }

    /// Deshace "Sí que hay en este sector". Toggleable.
    func retractProcessionary(schoolId: String) {
        guard hasKnownProcessionary else { return }
        Task {
            do {
                try await retractProcessionaryUseCase.invoke(schoolId: schoolId)
                hasKnownProcessionary = false
                processionaryAlertActive = false
                processionaryActiveNowSet = false
                ProcessionaryOverrideStore.shared.set(schoolId: schoolId, alertActive: false)
            } catch {}
        }
    }

    /// "Las he visto antes de tiempo": activa la alarma ya, con caducidad. Toggleable.
    func reportProcessionaryActiveNow(schoolId: String) {
        Task {
            do {
                try await reportProcessionaryActiveNowUseCase.invoke(schoolId: schoolId)
                hasKnownProcessionary = true
                processionaryAlertActive = true
                processionaryActiveNowSet = true
                ProcessionaryOverrideStore.shared.set(schoolId: schoolId, alertActive: true)
            } catch {}
        }
    }

    /// Deshace solo "Las he visto antes de tiempo" (no toca la confirmación permanente). Toggleable.
    func clearProcessionaryActiveNow(schoolId: String) {
        guard processionaryActiveNowSet else { return }
        Task {
            do {
                try await clearProcessionaryActiveNowUseCase.invoke(schoolId: schoolId)
                processionaryActiveNowSet = false
                processionaryAlertActive = hasKnownProcessionary && ProcessionarySeason.shared.isInSeason()
                ProcessionaryOverrideStore.shared.set(schoolId: schoolId, alertActive: processionaryAlertActive)
            } catch {}
        }
    }
}
