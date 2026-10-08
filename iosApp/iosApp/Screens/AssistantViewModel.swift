import SwiftUI
import Shared

/// Un mensaje del chat: del usuario o del asistente (con datos opcionales).
struct AssistantMessage: Identifiable {
    let id = UUID()
    let isUser: Bool
    let text: String
    var chips: [String] = []
    var recommendation: AssistantRecommendation? = nil
    var breakdown: AssistantBreakdown? = nil
    /// Vías o bloques encontrados por una búsqueda simple, y cuántos hubo en total.
    var hits: [LineSearchHit] = []
    var hitsTotal = 0
    /// Cuándo se escribió. Los mensajes recuperados de una sesión anterior enseñan sus tarjetas
    /// con un aviso de cuánto hace (el pronóstico puede haber cambiado).
    var createdAt = Date()
    var restored = false
    /// Opciones tocables de "¿a cuál te refieres?".
    var options: [AssistantOption] = []
}

/// ViewModel del asistente (burbuja flotante + chat). Recibe sus dependencias
/// por `init` con default (regla de ARCHITECTURE.md para VMs nuevos).
///
/// El servidor entiende la frase y calcula; aquí solo se guarda la
/// conversación y lo último entendido (`previous`) para poder refinar
/// ("ahora solo 7a") sin repetirlo todo.
@MainActor
final class AssistantViewModel: ObservableObject {
    /// Tope de caracteres de una pregunta (el servidor recorta a lo mismo).
    static let maxText = 200

    private let container: IosDependencyContainer
    private let locationBridge: LocationBridge
    private let store: AssistantTranscriptStore
    /// Cuenta a la que pertenece la conversación; nil = sin sesión.
    private var uid: String?

    init(container: IosDependencyContainer = AppDependencies.shared.container,
         locationBridge: LocationBridge = AppDependencies.shared.locationBridge,
         store: AssistantTranscriptStore = AssistantTranscriptStore()) {
        self.container = container
        self.locationBridge = locationBridge
        self.store = store
    }

    // MARK: - Conversación guardada en el móvil

    /// La app avisa de qué cuenta está activa. Sin sesión se borra todo; con sesión, si el chat
    /// está vacío, se recupera lo guardado de ESA cuenta (si no han pasado unas horas).
    func attachSession(uid newUid: String?) {
        guard newUid != uid else { return }
        uid = newUid
        guard let newUid else { messages = []; previous = nil; input = ""; store.clear(); return }
        messages = []
        previous = nil
        guard let saved = store.load(uid: newUid) else { return }
        messages = saved.messages.map {
            var m = AssistantMessage(isUser: $0.isUser, text: $0.text, chips: $0.chips)
            if let json = $0.payload, let p = try? AssistantSnapshotCodec.shared.decode(text: json) {
                m.recommendation = p.recommendation
                m.breakdown = p.breakdown
                m.hits = p.hits
                m.hitsTotal = Int(p.hitsTotal)
            }
            m.createdAt = $0.createdAt
            m.restored = true
            return m
        }
        previous = saved.previous?.toKotlin()
    }

    /// Tarjetas del mensaje como texto (vacío si no tiene). De las vías se guardan solo las que
    /// se enseñan (como mucho 5 escuelas x 4 filas), más el total, para no guardar cientos.
    private func payload(for m: AssistantMessage) -> String? {
        guard m.recommendation != nil || m.breakdown != nil || !m.hits.isEmpty else { return nil }
        let shown = AssistantPresenter.groupHits(m.hits).flatMap { $0.hits }
        let p = AssistantMessagePayload(
            recommendation: m.recommendation, breakdown: m.breakdown,
            hits: shown, hitsTotal: Int32(m.hitsTotal))
        return try? AssistantSnapshotCodec.shared.encode(payload: p)
    }

    /// Guarda la conversación (textos, etiquetas y tarjetas) unas horas, solo en el móvil.
    private func persist() {
        guard let uid, !messages.isEmpty else { return }
        store.save(StoredAssistantConversation(
            uid: uid, savedAt: Date(),
            messages: messages.map {
                StoredAssistantConversation.Message(
                    isUser: $0.isUser, text: $0.text, chips: $0.chips,
                    payload: payload(for: $0), createdAt: $0.createdAt)
            },
            previous: previous.map { StoredAssistantConversation.Understood($0) }))
    }

    @Published var messages: [AssistantMessage] = []
    @Published var input = ""
    @Published private(set) var isLoading = false

    /// Algo que el usuario ha tocado en el chat y hay que abrir en la app. Lo atiende la
    /// burbuja: cierra el chat, abre el destino y deja el círculo visible para volver.
    struct OpenRequest: Equatable {
        let schoolId: String
        let viaId: String?
    }
    @Published var openRequest: OpenRequest?

    func requestOpen(schoolId: String, viaId: String? = nil) {
        openRequest = OpenRequest(schoolId: schoolId, viaId: viaId)
    }

    /// Lo último que entendió el servidor; se devuelve en el mensaje siguiente.
    private var previous: AssistantUnderstood?

    /// Escuela que el usuario tiene abierta, si la hay (futuro: contexto de pantalla).
    var contextSchoolId: String?

    /// Frases de ejemplo para el chat vacío.
    var suggestions: [String] {
        [L("Bloques de 6a a 7a cerca de mí"),
         L("¿Dónde voy este sábado?"),
         L("Sector con más piedras a la sombra en Albarracín")]
    }

    var canSend: Bool {
        !isLoading && !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func send(_ text: String? = nil) async {
        let raw = (text ?? input).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty, !isLoading else { return }
        input = ""
        messages.append(AssistantMessage(isUser: true, text: raw))
        isLoading = true
        defer { isLoading = false; persist() }

        let location = await currentLocation()
        do {
            let answer = try await container.askAssistant.invoke(
                text: raw,
                previous: previous,
                lat: location.map { KotlinDouble(double: $0.lat) },
                lon: location.map { KotlinDouble(double: $0.lon) },
                schoolId: contextSchoolId)
            try await handle(answer, location: location)
        } catch {
            messages.append(AssistantMessage(
                isUser: false,
                text: L("No se pudo conectar. Inténtalo otra vez.")))
        }
    }

    func reset() {
        messages = []
        previous = nil
        input = ""
        store.clear()
    }

    // MARK: - Respuesta

    private func handle(_ answer: AssistantAnswer, location: UserLocation?) async throws {
        let chips = answer.understood.map {
            AssistantPresenter.chips($0, school: answer.resolvedSchool?.name, sector: answer.resolvedSector?.name)
        } ?? []

        switch answer.status {
        case .ok:
            previous = answer.understood ?? previous
            if let rec = answer.recommendation {
                messages.append(AssistantMessage(
                    isUser: false, text: AssistantPresenter.recommendationIntro(rec),
                    chips: chips, recommendation: rec))
            } else if let b = answer.breakdown {
                messages.append(AssistantMessage(
                    isUser: false, text: AssistantPresenter.breakdownIntro(b),
                    chips: chips, breakdown: b))
            } else {
                // Búsqueda simple: primero se ENSEÑAN las vías o bloques que cumplen, y cada
                // una se puede tocar para abrirla.
                let hits = try await loadHits(answer, location: location)
                let needsLocation = answer.understood?.maxDistanceKm != nil && location == nil
                messages.append(AssistantMessage(
                    isUser: false,
                    text: AssistantPresenter.hitsIntro(total: hits.count, needsLocationForDistance: needsLocation),
                    chips: chips, hits: hits, hitsTotal: hits.count))
            }
        case .needsInput:
            // Se conserva lo entendido: al contestar "Albarracín" no hay que repetir la pregunta.
            previous = answer.understood ?? previous
            if let c = answer.clarification {
                messages.append(AssistantMessage(
                    isUser: false, text: AssistantPresenter.clarification(c),
                    chips: chips, options: c.options))
            }
        default:
            messages.append(AssistantMessage(
                isUser: false,
                text: AssistantPresenter.message(for: answer.status) ?? L("Ahora mismo no puedo responder.")))
        }
    }

    // MARK: - Resultados de una búsqueda simple

    /// Pide al buscador de la app lo que entendió el asistente: con filtros (grado,
    /// modalidad, roca, orientación, escuela, distancia) usa el modo "explorar"; con
    /// solo un nombre, la búsqueda de siempre por nombre. Si la frase nombró un sector,
    /// se quedan los de ese sector.
    private func loadHits(_ answer: AssistantAnswer, location: UserLocation?) async throws -> [LineSearchHit] {
        guard let u = answer.understood else { return [] }
        let schoolId = answer.resolvedSchool?.id
        let hasFilter = u.gradeMin != nil || u.gradeMax != nil || u.discipline != nil
            || !u.rockTypes.isEmpty || !u.orientations.isEmpty || u.maxDistanceKm != nil || schoolId != nil

        var hits: [LineSearchHit] = []
        if hasFilter {
            let criteria = LineExploreCriteria(
                gradeMin: u.gradeMin, gradeMax: u.gradeMax, discipline: u.discipline,
                rockTypes: u.rockTypes.isEmpty ? nil : u.rockTypes,
                schoolIds: schoolId.map { [$0] },
                orientations: u.orientations.isEmpty ? nil : u.orientations,
                lat: location.map { KotlinDouble(double: $0.lat) },
                lon: location.map { KotlinDouble(double: $0.lon) },
                maxDistanceKm: u.maxDistanceKm,
                sort: "DISTANCE", offset: 0)
            hits = try await container.exploreLines.invoke(criteria: criteria)
        } else if let q = u.q, !q.isEmpty {
            hits = try await container.searchLines.invoke(query: q)
        }
        if let sector = answer.resolvedSector?.name {
            hits = hits.filter { ($0.sectorName ?? "").caseInsensitiveCompare(sector) == .orderedSame }
        }
        return hits
    }

    // MARK: - Ubicación

    /// Ubicación actual si hay permiso (la usa el servidor solo para medir distancias).
    private func currentLocation() async -> UserLocation? {
        guard locationBridge.hasPermission() else { return nil }
        return await withCheckedContinuation { cont in
            locationBridge.current { cont.resume(returning: $0) }
        }
    }
}
