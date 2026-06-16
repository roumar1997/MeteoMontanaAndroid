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
                    onSelectDay: { selectedDay = $0 }
                )
            }
            // Mapa de la escuela (MapLibre + topo). Plegable.
            SchoolMapSection(school: school)
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
                MapLibreView(
                    center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                    zoom: 14,
                    markers: markers,
                    onTapMarker: { id in selectedBlock = blocks.first { $0.id == id } }
                )
                .frame(height: 280)
                legend
                DirectionsButton(lat: school.lat, lon: school.lon, label: school.name)
                    .padding(.horizontal, 16).padding(.vertical, 8)
            }
        }
        .task(id: expanded) {
            if expanded && blocks.isEmpty {
                blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
            }
        }
        .sheet(item: $selectedBlock) { b in BlockInfoSheet(block: b) }
    }

    private var markers: [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, color: schoolColor)]
        for b in blocks {
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                color: color(for: b.type)))
        }
        return ms
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

/// Hoja con la info de un bloque tocado en el mapa (nombre, tipo, descripción,
/// vías y "CÓMO LLEGAR"). Espejo simplificado de BlockDetailDialog.kt.
private struct BlockInfoSheet: View {
    let block: Block
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    Text(block.name.isEmpty ? typeLabel : block.name)
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                    if !block.lines.isEmpty {
                        Text("VÍAS").eyebrow().padding(.top, 4)
                        ForEach(block.lines, id: \.id) { l in
                            HStack(spacing: 10) {
                                if let g = l.grade, !g.isEmpty {
                                    Text(g).font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                                        .frame(width: 40, height: 26).background(GradeColor.color(g))
                                }
                                Text(l.name.isEmpty ? "Vía \(l.sortOrder + 1)" : l.name)
                                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                if let st = l.startType, !st.isEmpty {
                                    Text(st).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                            }
                        }
                    }
                    DirectionsButton(lat: block.lat, lon: block.lon, label: block.name).padding(.top, 8)
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
