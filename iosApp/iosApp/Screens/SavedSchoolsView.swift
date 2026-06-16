import SwiftUI
import Shared
import CoreLocation

/// Escuelas guardadas para offline (SavedSchoolRepository de shared). Espejo de
/// SavedSchoolsScreen.kt. Tocar una abre su vista offline (detalle + mapa +
/// piedras + fotos desde la caché, sin conexión).
@MainActor
final class SavedVM: ObservableObject {
    @Published var schools: [SavedSchool] = []
    private var task: Task<Void, Never>?
    private let repo = AppDependencies.shared.container.savedSchools

    func start() {
        guard task == nil, let repo else { return }
        task = Task { [weak self] in
            for await list in repo.observeSaved() { self?.schools = list }
        }
    }
    func stop() { task?.cancel(); task = nil }
}

struct SavedSchoolsView: View {
    @StateObject private var vm = SavedVM()

    var body: some View {
        Group {
            if vm.schools.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "arrow.down.circle").font(.system(size: 36)).foregroundStyle(Cumbre.ink3)
                    Text("No tienes escuelas guardadas.").font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    Text("Abre una escuela y pulsa el icono de descarga para verla sin conexión.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        .multilineTextAlignment(.center).padding(.horizontal, 32)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
            } else {
                List(vm.schools, id: \.id) { s in
                    NavigationLink(destination: OfflineSchoolView(schoolId: s.id)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(s.name).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                            let sub = [s.region, s.rockType].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                            if !sub.isEmpty { Text(sub).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3) }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Guardadas (offline)")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }
}

/// Vista OFFLINE de una escuela guardada: lee loadOffline (detalle + bloques +
/// vías + forecast) y muestra forecast + mapa con marcadores + fotos (caché).
/// Los tiles del mapa sí necesitan red (cachearlos es un extra futuro).
struct OfflineSchoolView: View {
    let schoolId: String
    @State private var snapshot: OfflineSnapshot?
    @State private var blocks: [Block] = []
    @State private var loading = true
    @State private var factorsExpanded = false
    @State private var selectedDay: DayForecast?

    private let repo = AppDependencies.shared.container.savedSchools

    var body: some View {
        ScrollView {
            if loading {
                ProgressView().padding(.top, 60)
            } else if let s = snapshot {
                if let f = s.forecast {
                    ForecastBodyView(
                        forecast: f,
                        directions: (lat: s.school.lat, lon: s.school.lon, label: s.school.name),
                        factorsExpanded: $factorsExpanded,
                        onSelectDay: { selectedDay = $0 },
                        mapSlot: AnyView(offlineMap(s.school)))
                } else {
                    offlineMap(s.school)
                }
            } else {
                ContentUnavailableView("No guardada", systemImage: "tray",
                    description: Text("Esta escuela no está guardada offline."))
                    .padding(.top, 60)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(snapshot?.school.name ?? "Offline")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $selectedDay) { d in DayDetailView(day: d, allHours: snapshot?.forecast?.hours ?? []) }
        .task { await load() }
    }

    private func load() async {
        guard let repo else { loading = false; return }
        let snap = try? await repo.loadOffline(id: schoolId)
        snapshot = snap
        if let snap {
            blocks = snap.blocks.map { repo.toBlock(entity: $0, lines: snap.lines) }
        }
        loading = false
    }

    @ViewBuilder private func offlineMap(_ school: SavedSchool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("MAPA").eyebrow().padding(.horizontal, 16).padding(.top, 8)
            MapLibreView(
                center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                zoom: 15, markers: offlineMarkers(school))
                .frame(height: 260)
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .padding(.horizontal, 16)
            ForEach(blocks.filter { $0.type.uppercased() == "BLOCK" }, id: \.id) { b in
                if let p = b.photoPath, !p.isEmpty {
                    Text(b.name.isEmpty ? "PIEDRA" : b.name)
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                        .padding(.horizontal, 16)
                    TopoPhotoView(photoUrl: p, lines: b.lines.map { TopoLineVM($0) })
                        .padding(.horizontal, 16)
                }
            }
        }
    }

    private func offlineMarkers(_ school: SavedSchool) -> [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school)]
        for b in blocks {
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                title: b.name, kind: markerKindFor(b.type),
                color: blockTypeColor(b.type), name: b.name))
        }
        return ms
    }
}
