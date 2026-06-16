import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).
extension Block: Identifiable {}

// Detalle de escuela — réplica fiel de ForecastBody.kt de Android:
// veredicto SÍ/NO "¿PUEDO ESCALAR HOY?" + ÍNDICE, banda de roca, heatmap,
// desglose de factores, tiempo actual, próximas 16 h, condiciones (8 celdas),
// próximos 7 días y mejor día. Datos del GetForecastUseCase compartido.

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

    func load(schoolId: String, lat: Double, lon: Double, rockType: String?) async {
        loading = true; errorText = nil
        do { forecast = try await getForecast.invoke(schoolId: schoolId) }
        catch { errorText = error.localizedDescription }
        loading = false
        let favs = try? await getMyFavorites.invoke()
        isFavorite = (favs ?? []).contains { $0.id == schoolId }
        await loadNotes(schoolId: schoolId)
        await loadMonthly(schoolId: schoolId, lat: lat, lon: lon, rockType: rockType)
        await checkSaved(schoolId: schoolId)
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
        isSaved = true; savingOffline = false
    }

    func removeOffline(schoolId: String) async {
        guard let repo = savedSchools else { return }
        try? await repo.remove(id: schoolId)
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

    /// Publica una nota (texto + foto opcional) y refresca la lista. Si hay
    /// imagen, la sube a Firebase Storage y adjunta su URL.
    func publishNote(schoolId: String, text: String, image: UIImage?) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        publishing = true
        var photoUrl: String?
        if let img = image {
            let path = "note-photos/\(schoolId)-\(Int(Date().timeIntervalSince1970)).jpg"
            photoUrl = try? await StorageUploader.uploadJPEG(img, path: path)
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
                ForecastBodyView(
                    forecast: f,
                    directions: (lat: school.lat, lon: school.lon, label: school.name),
                    factorsExpanded: $factorsExpanded,
                    onSelectDay: { selectedDay = $0 },
                    mapSlot: AnyView(SchoolMapSection(school: school))
                )
            } else {
                // Sin previsión: el mapa va igualmente.
                SchoolMapSection(school: school)
            }
            // Mejores meses del año (stats mensuales del backend, cacheadas).
            if !vm.monthlyScores.isEmpty {
                MonthlyStatsSection(scores: vm.monthlyScores, bestRange: vm.monthlyBestRange)
            }
            // Notas comunitarias — bajo el forecast, también si no hubo previsión.
            NotesSectionView(
                notes: vm.notes,
                publishing: vm.publishing,
                onPublish: { text, image in Task { await vm.publishNote(schoolId: school.id, text: text, image: image) } }
            )
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(school.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // Compartir (icono, mismo estilo que la estrella) — solo si hay
            // forecast cargado para resumir las condiciones.
            ToolbarItem(placement: .topBarTrailing) {
                if let f = vm.forecast {
                    ShareLink(item: conditionsShareSummary(f)) {
                        Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink3)
                    }
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
        .task { await vm.load(schoolId: school.id, lat: school.lat, lon: school.lon, rockType: school.rockType) }
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
private struct SchoolMapSection: View {
    let school: School
    @State private var expanded = false
    @State private var blocks: [Block] = []
    @State private var selectedBlock: Block?
    // Proponer mejora: tap en el mapa fija coords → formulario → envío.
    @State private var waitingTap = false
    @State private var showTypePicker = false
    @State private var formCoord: CLLocationCoordinate2D?
    @State private var proposeType = "PARKING"
    @State private var showSuccess = false
    @State private var mapStyle: MapStyleKind = .topo
    @State private var boulderCoord: CLLocationCoordinate2D?
    // Corregir posición: seleccionar un marcador y fijar su nueva posición.
    @State private var correctionMode = false
    @State private var corrActive = false        // ya hay un target elegido
    @State private var corrTargetId: String?     // nil + corrActive ⇒ la escuela
    @State private var corrTargetName = ""
    @State private var corrOld: CLLocationCoordinate2D?
    @State private var corrNew: CLLocationCoordinate2D?
    @State private var userCoord: CLLocationCoordinate2D?   // mi ubicación en el sector
    @State private var collapsedSectors: Set<String> = []   // zonas con piedras ocultas
    @State private var editLinesBlock: Block?
    @State private var assignSectorBlock: Block?

    // Colores de marcador por tipo (espejo de Android: parking azul, piedra
    // terra, zona verde; la escuela en tinta oscura).
    private let parkingColor = UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    private let blockColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    private let zoneColor = UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    private let schoolColor = UIColor(red: 0.11, green: 0.11, blue: 0.10, alpha: 1)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("VER MAPA").eyebrow()
                    if !blocks.isEmpty {
                        Text("· \(blocks.count)").font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                ZStack(alignment: .bottomTrailing) {
                    MapLibreView(
                        center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                        zoom: 14,
                        markers: markers,
                        style: mapStyle,
                        onTapMarker: { id in
                            if correctionMode && !corrActive {
                                selectCorrectionTarget(id)
                            } else if !waitingTap && !correctionMode {
                                // Tocar una ZONA con piedras → colapsa/expande sus piedras;
                                // el resto de marcadores abre su ficha.
                                if let b = blocks.first(where: { $0.id == id }),
                                   b.type.uppercased() == "ZONE",
                                   blocks.contains(where: { $0.sectorBlockId == b.id }) {
                                    if collapsedSectors.contains(b.id) { collapsedSectors.remove(b.id) }
                                    else { collapsedSectors.insert(b.id) }
                                } else {
                                    selectedBlock = blocks.first { $0.id == id }
                                }
                            }
                        },
                        onMapTap: (waitingTap || (correctionMode && corrActive)) ? { coord in
                            if waitingTap {
                                waitingTap = false
                                if proposeType == "BOULDER" { boulderCoord = coord } else { formCoord = coord }
                            } else {
                                corrNew = coord
                            }
                        } : nil
                    )
                    .frame(height: 280)

                    // Chips topográfico / satélite (esquina superior izquierda).
                    if !waitingTap && !correctionMode {
                        VStack { HStack { MapStyleChips(selection: $mapStyle); Spacer() }; Spacer() }
                            .frame(height: 280)
                    }

                    if waitingTap {
                        // Banner "PULSA EN EL MAPA" (parking/sector/piedra).
                        mapBanner("PULSA EN EL MAPA PARA FIJAR LA POSICIÓN",
                                  cancel: { waitingTap = false })
                    } else if correctionMode {
                        mapBanner(correctionBannerText,
                                  accept: (corrActive && corrNew != nil) ? { Task { await submitCorrection() } } : nil,
                                  cancel: cancelCorrection)
                    } else {
                        // Botón "+ PROPONER" (esquina inferior derecha).
                        Button { showTypePicker = true } label: {
                            Text("+ PROPONER").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                .background(Cumbre.terra)
                        }
                        .buttonStyle(.plain).padding(12)
                    }
                }
                legend
                DirectionsButton(lat: school.lat, lon: school.lon, label: school.name)
                    .padding(.horizontal, 16).padding(.vertical, 8)
            }
        }
        .task(id: expanded) {
            guard expanded else { return }
            if blocks.isEmpty {
                blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
            }
            // Mi ubicación (para verme mientras ando por el sector).
            if AppDependencies.shared.locationBridge.hasPermission(),
               let loc = try? await AppDependencies.shared.container.locationProvider?.current() {
                userCoord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
            }
        }
        .sheet(item: $selectedBlock) { b in
            BlockInfoSheet(
                block: b,
                sectors: blocks.filter { $0.type.uppercased() == "ZONE" },
                onEditLines: { editLinesBlock = b },
                onAssignSector: { assignSectorBlock = b })
        }
        .sheet(item: $editLinesBlock) { b in
            EditLinesSheet(block: b, schoolId: school.id) { ok in
                editLinesBlock = nil
                if ok { afterSubmit() }
            }
        }
        .sheet(item: $assignSectorBlock) { b in
            AssignSectorSheet(block: b, schoolId: school.id,
                              sectors: blocks.filter { $0.type.uppercased() == "ZONE" }) { ok in
                assignSectorBlock = nil
                if ok { afterSubmit() }
            }
        }
        .sheet(isPresented: $showTypePicker) {
            ContributionTypePicker { type in
                showTypePicker = false
                switch type {
                case "PARKING", "SECTOR", "BOULDER": proposeType = type; waitingTap = true
                case "CORRECTION": correctionMode = true; corrActive = false; corrNew = nil
                default: break
                }
            }
        }
        .sheet(item: coordItem) { item in
            ContributionFormSheet(type: proposeType, schoolId: school.id, coord: item.coord) { ok in
                formCoord = nil
                if ok { afterSubmit() }
            }
        }
        .sheet(item: boulderCoordItem) { item in
            BoulderFormSheet(schoolId: school.id, coord: item.coord,
                             sectors: blocks.filter { $0.type.uppercased() == "ZONE" }) { ok in
                boulderCoord = nil
                if ok { afterSubmit() }
            }
        }
        .sheet(isPresented: $showSuccess) { ContributionSuccessSheet() }
    }

    private func afterSubmit() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            showSuccess = true
            await reloadBlocks()
        }
    }

    // Wrapper Identifiable para presentar el formulario de piedra.
    private var boulderCoordItem: Binding<CoordItem?> {
        Binding(get: { boulderCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { boulderCoord = nil } })
    }

    // MARK: - Corrección de posición

    private func selectCorrectionTarget(_ id: String) {
        if let b = blocks.first(where: { $0.id == id }) {
            corrTargetId = b.id
            corrTargetName = b.name.isEmpty ? typeLabel(b.type) : b.name
            corrOld = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
        } else if id == school.id {
            corrTargetId = nil
            corrTargetName = "la escuela"
            corrOld = CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)
        } else { return }
        corrActive = true
        corrNew = nil
    }

    private func cancelCorrection() {
        correctionMode = false; corrActive = false; corrTargetId = nil; corrNew = nil
    }

    private func submitCorrection() async {
        guard let old = corrOld, let nw = corrNew else { return }
        let req = ContributionRequest(
            type: "POSITION_CORRECTION", name: corrTargetName,
            lat: old.latitude, lon: old.longitude,
            notes: nil, description: nil,
            proposedLat: KotlinDouble(double: nw.latitude),
            proposedLon: KotlinDouble(double: nw.longitude), correctionReason: nil,
            targetBlockId: corrTargetId, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: school.id, req: req)) != nil
        cancelCorrection()
        if ok { afterSubmit() }
    }

    private var correctionBannerText: String {
        if !corrActive { return "PULSA EL MARCADOR QUE QUIERES MOVER" }
        if corrNew == nil { return "MOVIENDO «\(corrTargetName)» · PULSA LA NUEVA POSICIÓN" }
        return "POSICIÓN FIJADA · PULSA OTRA VEZ PARA RECORREGIR O ACEPTA"
    }

    /// Banner superior + botón cancelar (y aceptar opcional) sobre el mapa.
    private func mapBanner(_ text: String, accept: (() -> Void)? = nil,
                           cancel: @escaping () -> Void) -> some View {
        VStack {
            Text(text)
                .font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .frame(maxWidth: .infinity).background(Cumbre.terra)
            Spacer()
            HStack(spacing: 8) {
                Button("CANCELAR", action: cancel)
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(Cumbre.ink)
                if let accept {
                    Button("✓ ACEPTAR", action: accept)
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                        .padding(.horizontal, 12).padding(.vertical, 6).background(.white)
                }
            }.padding(.bottom, 8)
        }
        .frame(height: 280)
    }

    // Wrapper Identifiable para presentar el formulario con la coordenada fijada.
    private var coordItem: Binding<CoordItem?> {
        Binding(get: { formCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { formCoord = nil } })
    }

    private func reloadBlocks() async {
        blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
    }

    private var markers: [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in blocks {
            // Piedra oculta si pertenece a un sector colapsado.
            if b.type.uppercased() == "BLOCK", let sid = b.sectorBlockId, collapsedSectors.contains(sid) {
                continue
            }
            // Zona colapsada: muestra cuántas piedras tiene ocultas.
            let collapsed = b.type.uppercased() == "ZONE" && collapsedSectors.contains(b.id)
            let hidden = collapsed ? blocks.filter { $0.sectorBlockId == b.id }.count : 0
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type),
                color: color(for: b.type),
                name: collapsed && hidden > 0 ? "\(b.name) (+\(hidden))" : b.name))
        }
        // Mi ubicación (punto azul) para orientarme en el sector.
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user))
        }
        // Fantasma de la nueva posición al corregir.
        if let nw = corrNew {
            ms.append(CumbreMarker(
                id: "__GHOST__", coordinate: nw, title: "Nueva posición",
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        return ms
    }

    private func markerKind(for type: String) -> MarkerKind {
        switch type.uppercased() {
        case "PARKING": return .parking
        case "ZONE": return .zone
        default: return .block
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem("Parking", Color(parkingColor))
            legendItem("Piedra", Color(blockColor))
            legendItem("Zona", Color(zoneColor))
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }
    private func legendItem(_ t: String, _ c: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(c).frame(width: 9, height: 9).overlay(Circle().stroke(.white, lineWidth: 1))
            Text(t).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
        }
    }

    private func color(for type: String) -> UIColor {
        switch type.uppercased() {
        case "PARKING": return parkingColor
        case "ZONE": return zoneColor
        default: return blockColor
        }
    }
    private func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }
}

/// Coordenada Identifiable para presentar el formulario por sheet(item:).
private struct CoordItem: Identifiable {
    let coord: CLLocationCoordinate2D
    var id: String { "\(coord.latitude),\(coord.longitude)" }
}

/// Selector de tipo de propuesta — espejo de TypePickerDialog.kt. PARKING está
/// activo; el resto (piedra/sector/corregir) llegará en próximas iteraciones.
private struct ContributionTypePicker: View {
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                row("PIEDRA", "Añadir un bloque y sus vías", "mountain.2.fill", enabled: true) { onPick("BOULDER") }
                row("SECTOR", "Añadir una zona", "square.dashed", enabled: true) { onPick("SECTOR") }
                row("PARKING", "Añadir un aparcamiento", "car.fill", enabled: true) { onPick("PARKING") }
                row("CORREGIR", "Mover una posición existente", "mappin.and.ellipse", enabled: true) { onPick("CORRECTION") }
                Spacer()
            }
            .padding(16)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Proponer mejora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
    }
    private func row(_ t: String, _ sub: String, _ icon: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(enabled ? Cumbre.terra : Cumbre.ink3).frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(t).font(Cumbre.mono(13, .bold)).tracking(0.6).foregroundStyle(enabled ? Cumbre.ink : Cumbre.ink3)
                    Text(enabled ? sub : "\(sub) · próximamente").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// Formulario de propuesta PARKING/SECTOR — espejo de ParkingFormDialog.kt.
/// Coords fijadas por el tap en el mapa. Envía POST /contributions y devuelve ok.
private struct ContributionFormSheet: View {
    let type: String           // "PARKING" | "SECTOR"
    let schoolId: String
    let coord: CLLocationCoordinate2D
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var notes = ""
    @State private var sending = false

    private var isSector: Bool { type == "SECTOR" }
    private var title: String { isSector ? "Nueva zona" : "Nuevo parking" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    field("NOMBRE (opcional)", $name, isSector ? "ej: Sector Bajo" : "ej: Parking de arriba")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }
                    field("NOTAS (opcional)", $notes, isSector ? "Descripción de la zona" : "Cómo es el acceso, etc.")
                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text("ENVIAR PROPUESTA").font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
    }

    private func send() async {
        sending = true
        let req = ContributionRequest(
            type: type,
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? nil : name,
            lat: coord.latitude, lon: coord.longitude,
            notes: notes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : notes,
            description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        dismiss()
        onDone(ok)
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Confirmación tras enviar una propuesta — espejo de SuccessDialog.kt.
private struct ContributionSuccessSheet: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.seal.fill").font(.system(size: 56)).foregroundStyle(Cumbre.ok)
            Text("¡Propuesta enviada!").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text("La revisaremos en 24-48 h. Gracias por mejorar el mapa de la comunidad.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).padding(.horizontal, 32)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1)).padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
        .background(Cumbre.bg.ignoresSafeArea())
    }
}

/// Hoja con la info de un bloque tocado en el mapa (nombre, tipo, descripción,
/// vías y "CÓMO LLEGAR"). Espejo simplificado de BlockDetailDialog.kt.
private struct BlockInfoSheet: View {
    let block: Block
    var sectors: [Block] = []
    var onEditLines: (() -> Void)? = nil
    var onAssignSector: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    private var sectorName: String? {
        guard let sid = block.sectorBlockId else { return nil }
        return sectors.first(where: { $0.id == sid })?.name
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    Text(block.name.isEmpty ? typeLabel : block.name)
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)

                    // Sector al que pertenece (si lo tiene).
                    if let sn = sectorName, !sn.isEmpty {
                        Text("SECTOR · \(sn.uppercased())").font(Cumbre.mono(10, .bold))
                            .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Cumbre.ok)
                    }

                    // Foto con las vías dibujadas encima (solo PIEDRA con foto).
                    if block.type.uppercased() == "BLOCK",
                       let photo = block.photoPath, !photo.isEmpty {
                        TopoPhotoView(photoUrl: photo, lines: block.lines.map { TopoLineVM($0) })
                            .padding(.top, 4)
                    }

                    if !block.lines.isEmpty {
                        Text("VÍAS (\(block.lines.count))").eyebrow().padding(.top, 4)
                        ForEach(Array(block.lines.enumerated()), id: \.element.id) { idx, l in
                            HStack(spacing: 10) {
                                // Número coloreado por grado (espejo del badge del topo).
                                Text("\(idx + 1)").font(Cumbre.mono(11, .bold))
                                    .foregroundStyle(GradeColor.style(l.grade).dark ? .black : .white)
                                    .frame(width: 24, height: 24)
                                    .background(Circle().fill(GradeColor.color(l.grade)))
                                if let g = l.grade, !g.isEmpty {
                                    Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink)
                                        .frame(width: 38, alignment: .leading)
                                }
                                Text(l.name.isEmpty ? "Vía \(idx + 1)" : l.name)
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                if let st = l.startType, !st.isEmpty {
                                    Text(st).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                            }
                        }
                    }

                    // Coordenadas (espejo de BlockDetailDialog).
                    Text(String(format: "%.5f, %.5f", block.lat, block.lon))
                        .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3).padding(.top, 2)

                    DirectionsButton(lat: block.lat, lon: block.lon, label: block.name).padding(.top, 8)

                    // Editor unificado de vías (corregir existentes + añadir nuevas).
                    if block.type.uppercased() == "BLOCK", let onEditLines {
                        Button { dismiss(); onEditLines() } label: {
                            Text(block.lines.isEmpty ? "+ AÑADIR VÍAS" : "✎ EDITAR / AÑADIR VÍAS")
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // Asignar / cambiar sector (piedra, si la escuela tiene algún
                    // sector). Si ya tiene sector → "CAMBIAR SECTOR" (el picker
                    // muestra los demás; si no hay otro, lo avisa).
                    if block.type.uppercased() == "BLOCK", let onAssignSector, !sectors.isEmpty {
                        Button { dismiss(); onAssignSector() } label: {
                            Text(block.sectorBlockId == nil ? "+ ASIGNAR SECTOR" : "CAMBIAR SECTOR")
                                .font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra) }
            }
        }
    }
    private var typeLabel: String {
        switch block.type.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }
}

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el

/// Carga una escuela por id y muestra su detalle. Útil cuando solo tenemos el
/// schoolId (p. ej. al tocar una notificación con targetType "school").
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
struct ForecastBodyView: View {
    let forecast: Forecast
    var directions: (lat: Double, lon: Double, label: String)? = nil
    @Binding var factorsExpanded: Bool
    var onSelectDay: ((DayForecast) -> Void)? = nil
    /// Mapa de la escuela, insertado entre el tiempo actual y "Próximas 16 h"
    /// (el tab Tiempo no lo pasa). AnyView para no acoplar el tipo.
    var mapSlot: AnyView? = nil

    var body: some View {
        let f = forecast
        VStack(alignment: .leading, spacing: 0) {
            HeroSection(forecast: f)
            // El compartir vive ahora como icono junto a la estrella del toolbar
            // (el detalle ya tiene muchos datos). "CÓMO LLEGAR" volverá con mapas.
            RockStatusBand(current: f.current).padding(.horizontal, 16).padding(.bottom, 8)
            HeatmapStrip(hours: upcomingHours(f.hours, 24)).padding(16)
            FactorsAccordion(current: f.current, expanded: $factorsExpanded)
            rule
            CurrentWeather(current: f.current)
            rule
            if let mapSlot {
                mapSlot
                rule
            }
            SectionTitle("PRÓXIMAS 16 HORAS")
            HoursGrid(hours: upcomingHours(f.hours, 16)).padding(.vertical, 8)
            ConditionsGrid(current: f.current)
            rule
            SectionTitle("PRÓXIMOS 7 DÍAS")
            ForEach(Array(f.days.prefix(7).enumerated()), id: \.offset) { _, d in
                Button { onSelectDay?(d) } label: { DayRow(day: d) }.buttonStyle(.plain)
                rule
            }
            BestDayBar(forecast: f)
        }
    }

    private var rule: some View { Divider().overlay(Cumbre.rule) }
}

// MARK: - Hero (¿PUEDO ESCALAR HOY?)

private struct HeroSection: View {
    let forecast: Forecast
    var body: some View {
        let c = forecast.current
        let score = Int(c.score)
        let yes = score >= 55
        HStack(alignment: .top, spacing: 12) {
            Text(yes ? "SÍ" : "NO")
                .font(Cumbre.serif(56, .bold))
                .foregroundStyle(yes ? Cumbre.ok : Cumbre.bad)
            VStack(alignment: .leading, spacing: 4) {
                Text("¿PUEDO ESCALAR HOY?").eyebrow()
                if let w = forecast.bestWindow {
                    Text("Óptimo entre \(short(w.start))–\(short(w.end))")
                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("ÍNDICE").eyebrow()
                HStack(alignment: .bottom, spacing: 2) {
                    Text("\(score)")
                        .font(Cumbre.serif(40, .bold))
                        .foregroundStyle(Cumbre.score(score))
                    Text("/100").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        .padding(.bottom, 6)
                }
            }
        }
        .padding(16)
    }
}

// MARK: - Banda de roca

private struct RockStatusBand: View {
    let current: Current
    var body: some View {
        let dry = current.dryRock
        let accent = dry ? Cumbre.ok : Cumbre.bad
        let subtitle = current.drying?.message ?? (dry ? "Lista para escalar" : "Mejor esperar a que seque")
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(dry ? "● ROCA SECA" : "● ROCA HÚMEDA")
                    .font(Cumbre.mono(13, .bold)).foregroundStyle(accent)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
            Spacer()
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(accent.opacity(0.12))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(accent.opacity(0.45), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Heatmap (tira ancha)

private struct HeatmapStrip: View {
    let hours: [HourForecast]
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                Rectangle().fill(Cumbre.score(Int(h.score)))
            }
        }
        .frame(height: 18)
        .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

// MARK: - Desglose de factores

private struct FactorsAccordion: View {
    let current: Current
    @Binding var expanded: Bool
    var body: some View {
        VStack(spacing: 0) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("¿POR QUÉ ESTE ÍNDICE?").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(Cumbre.ink3)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if expanded {
                VStack(spacing: 0) {
                    ForEach(Array(current.factors.enumerated()), id: \.offset) { _, f in
                        HStack(spacing: 10) {
                            Image(systemName: f.passes ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(f.passes ? Cumbre.ok : Cumbre.bad)
                            Text(f.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                            Spacer()
                            Text(f.display).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
                .padding(.bottom, 8)
            }
        }
    }
}

// MARK: - Tiempo actual

private struct CurrentWeather: View {
    let current: Current
    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            Text("\(Int(current.temperature))°")
                .font(Cumbre.serif(56, .bold)).foregroundStyle(Cumbre.ink)
            VStack(alignment: .leading, spacing: 2) {
                Text(cloudLabel(Int(current.cloudCover)))
                    .font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("VIENTO \(Int(current.windSpeed)) km/h  ·  HUM \(Int(current.humidity))%")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(16)
    }
}

// MARK: - Próximas 16 h

private struct HoursGrid: View {
    let hours: [HourForecast]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, h in
                    VStack(spacing: 6) {
                        Text(short(h.time)).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                        WmoIcon(code: Int(h.weatherCode), size: 22, tint: Cumbre.ink2)
                        RoundedRectangle(cornerRadius: 2).fill(Cumbre.score(Int(h.score)))
                            .frame(width: 30, height: 30)
                            .overlay(Text("\(Int(h.score))").font(Cumbre.mono(11, .bold)).foregroundStyle(.white))
                        Text("\(Int(h.temperature))°").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink2)
                        // Lluvia en mm (solo si la hay) — paridad con ForecastBody.
                        if h.precipitation > 0 {
                            Text("\(String(format: "%.1f", h.precipitation)) mm")
                                .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.rain)
                        } else {
                            Text(" ").font(Cumbre.mono(9))
                        }
                        // Viento km/h (siempre) — debajo de la lluvia, como Android.
                        Text("\(Int(h.windSpeed)) km/h")
                            .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

// MARK: - Condiciones ahora (8 celdas)

private struct ConditionsGrid: View {
    let current: Current
    var body: some View {
        let c = current
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle("CONDICIONES AHORA").padding(.horizontal, 0)
            HStack(spacing: 8) {
                cell("HUMEDAD", "\(Int(c.humidity))", "%")
                cell("VIENTO", "\(Int(c.windSpeed))", "km/h")
                cell("LLUVIA 24H", trim(c.precip24h), "mm")
                cell("NUBES", "\(Int(c.cloudCover))", "%")
            }
            HStack(spacing: 8) {
                cell("LLUVIA 72H", trim(c.precip72h), "mm")
                cell("ROCÍO", c.dewPoint.map { "\(Int(truncating: $0))" } ?? "—", "°")
                cell("PROB LLUVIA", "\(Int(c.precipitationProbability))", "%")
                cell("ROCA", c.dryRock ? "SECA" : "HÚM", "")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func cell(_ label: String, _ value: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Cumbre.mono(9, .bold)).tracking(0.5).foregroundStyle(Cumbre.ink3)
            HStack(alignment: .bottom, spacing: 2) {
                Text(value).font(.system(size: 20, weight: .semibold)).foregroundStyle(Cumbre.ink)
                if !unit.isEmpty { Text(unit).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3).padding(.bottom, 3) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }
}

// MARK: - Día y mejor día

private struct DayRow: View {
    let day: DayForecast
    var body: some View {
        HStack(spacing: 12) {
            Text("\(Int(day.avgScore))")
                .font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.score(Int(day.avgScore)))
                .frame(width: 40, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(dayLabel(day.date)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("MÁX \(Int(day.tempMax))°  ·  MÍN \(Int(day.tempMin))°  ·  \(trim(day.precipitationTotal)) mm")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

private struct BestDayBar: View {
    let forecast: Forecast
    var body: some View {
        if let b = forecast.bestDay {
            VStack(alignment: .leading, spacing: 2) {
                Text("★ MEJOR DÍA").eyebrow()
                Text("En \(Int(b.daysFromToday))d (\(Int(b.score)))")
                    .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
            }
            .padding(16)
        }
    }
}

/// Botón "CÓMO LLEGAR" → abre Google Maps con la ruta al destino (mismo
/// patrón que Android: dir/?api=1&destination=lat,lon). Cae a Apple Maps si
/// Google Maps no está instalado.
struct DirectionsButton: View {
    let lat: Double
    let lon: Double
    let label: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            let g = URL(string: "comgooglemaps://?daddr=\(lat),\(lon)&directionsmode=driving")!
            let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)")!
            openURL(UIApplication.shared.canOpenURL(g) ? g : web)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.turn.up.right.diamond")
                Text("CÓMO LLEGAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.terra)
        }
        .buttonStyle(.plain)
    }
}

/// Botón COMPARTIR — comparte un resumen de condiciones por el share sheet del
/// sistema (paridad con Android, que comparte una tarjeta). De momento texto;
/// la imagen-tarjeta es una mejora posterior.
/// Resumen de condiciones para compartir (texto). Reutilizado por el icono del
/// toolbar del detalle.
func conditionsShareSummary(_ forecast: Forecast) -> String {
    let c = forecast.current
    let score = Int(c.score)
    return "\(forecast.schoolName) — Índice \(score)/100 (\(c.scoreLabel)) hoy. "
        + "\(Int(c.temperature))°, viento \(Int(c.windSpeed)) km/h, roca \(c.dryRock ? "seca" : "mojada"). "
        + "Tiempo para escalar · MeteoMontana"
}

struct ShareConditionsButton: View {
    let forecast: Forecast

    private var summary: String { conditionsShareSummary(forecast) }

    var body: some View {
        ShareLink(item: summary) {
            HStack(spacing: 8) {
                Image(systemName: "square.and.arrow.up")
                Text("COMPARTIR").font(Cumbre.mono(12, .bold)).tracking(0.8)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Cumbre.terra)
        }
        .buttonStyle(.plain)
    }
}

private struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.ink2)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Helpers

private func short(_ iso: String) -> String {
    if let t = iso.firstIndex(of: "T") { return String(iso[iso.index(after: t)...].prefix(5)) }
    return iso
}

/// Devuelve las próximas `count` horas A PARTIR DE LA HORA ACTUAL (no desde el
/// inicio del día). Espejo de "PRÓXIMAS 16 HORAS desde ahora" de Android.
private func upcomingHours(_ hours: [HourForecast], _ count: Int) -> [HourForecast] {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd'T'HH:mm"
    let now = Date()
    let startIdx = hours.firstIndex { h in
        guard let d = f.date(from: String(h.time.prefix(16))) else { return false }
        return d >= now.addingTimeInterval(-3600) // incluye la hora en curso
    } ?? 0
    return Array(hours[startIdx...].prefix(count))
}

private func trim(_ d: Double) -> String {
    d == d.rounded() ? "\(Int(d))" : String(format: "%.1f", d)
}

private func cloudLabel(_ c: Int) -> String {
    switch c {
    case ..<20: return "Despejado"
    case ..<50: return "Parcialmente nublado"
    case ..<80: return "Mayormente nublado"
    default:    return "Cubierto"
    }
}

private func dayLabel(_ iso: String) -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
    guard let d = f.date(from: String(iso.prefix(10))) else { return iso }
    let out = DateFormatter(); out.locale = Locale(identifier: "es_ES"); out.dateFormat = "EEE d MMM"
    return out.string(from: d).capitalized
}

// `Note` (clase Kotlin vía SKIE) ya tiene `id: String`; con esto vale para
// `.sheet(item:)` y `ForEach(id:)` sin envoltorios.
extension Note: Identifiable {}

// MARK: - Notas comunitarias (réplica de NotesSection.kt)

/// Sección de notas del detalle de escuela. Leer es público; publicar requiere
/// sesión (garantizada por el gate de login). La foto se muestra con AsyncImage
/// nativo; subir foto necesita el bridge de Firebase Storage (pendiente).
struct NotesSectionView: View {
    let notes: [Note]
    let publishing: Bool
    let onPublish: (String, UIImage?) -> Void

    @State private var draft = ""
    @State private var photoNote: Note?
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedImage: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("NOTAS COMUNITARIAS").eyebrow()
                .padding(.top, 8)

            if notes.isEmpty {
                Text("Sin notas aún. ¡Sé el primero!")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(.vertical, 4)
            } else {
                ForEach(notes, id: \.id) { n in
                    NoteRowView(note: n) { photoNote = n }
                    Divider().overlay(Cumbre.rule)
                }
            }

            // Composer
            VStack(alignment: .trailing, spacing: 8) {
                TextField("Escribe una nota…", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.system(size: 15))
                    .foregroundStyle(Cumbre.ink)
                    .padding(10)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))

                // Vista previa de la foto elegida (con opción de quitarla).
                if let img = pickedImage {
                    HStack(spacing: 8) {
                        Image(uiImage: img).resizable().scaledToFill()
                            .frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 4))
                        Button { pickedImage = nil; pickerItem = nil } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                        Spacer()
                    }
                }

                HStack(spacing: 10) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "camera").font(.system(size: 16)).foregroundStyle(Cumbre.terra)
                            .frame(width: 40, height: 40).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    Spacer()
                    Button {
                        let text = draft; let img = pickedImage
                        draft = ""; pickedImage = nil; pickerItem = nil
                        onPublish(text, img)
                    } label: {
                        HStack(spacing: 6) {
                            if publishing { ProgressView().tint(.white) }
                            Text("PUBLICAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(canPublish ? Cumbre.terra : Cumbre.ink3)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canPublish)
                }
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) { pickedImage = img }
            }
        }
        .sheet(item: $photoNote) { n in
            NotePhotoSheet(note: n)
        }
    }

    private var canPublish: Bool {
        !publishing && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

private struct NoteRowView: View {
    let note: Note
    let onPhotoTap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(note.author ?? "Anónimo")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Spacer()
                Text(shortDate(note.createdAt))
                    .font(Cumbre.mono(11))
                    .foregroundStyle(Cumbre.ink3)
            }
            Text(note.text)
                .font(.system(size: 15))
                .foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let url = note.photoUrl, let u = URL(string: url) {
                Button(action: onPhotoTap) {
                    AsyncImage(url: u) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(Cumbre.paper)
                    }
                    .frame(height: 120)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 8)
    }
}

/// Foto de la nota a pantalla completa. `Note` es Identifiable vía su `id`.
private struct NotePhotoSheet: View {
    let note: Note
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let url = note.photoUrl, let u = URL(string: url) {
                        AsyncImage(url: u) { img in
                            img.resizable().scaledToFit()
                        } placeholder: { ProgressView() }
                    }
                    Text(note.text).font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                        .padding(.horizontal, 16)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }
}

/// Extrae la fecha (YYYY-MM-DD) de un timestamp ISO; si no encaja, devuelve tal cual.
private func shortDate(_ iso: String) -> String {
    if iso.count >= 10 { return String(iso.prefix(10)) }
    return iso
}

#Preview {
    NavigationStack {
        SchoolDetailView(school: School(id: "x", name: "Demo", location: "Demo", region: "Aragón",
                                        style: "Boulder", rockType: "Caliza", lat: 0, lon: 0, source: nil))
    }
}
