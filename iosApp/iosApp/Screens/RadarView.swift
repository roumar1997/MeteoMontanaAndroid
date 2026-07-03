import SwiftUI
import CoreLocation
import Shared

// Pestaña Radar — réplica verbatim de RadarScreen.kt (Android):
// "el mapa es la pantalla": compuesto España de AEMET (cocinado por el
// backend en azules Cumbre, alcance completo mar/Portugal/Francia) animado
// sobre nuestro MapLibre; player flotante con hora grande y chips HOY/AYER;
// botonera lateral (capas, escuelas, opacidad, ubicación). Autoplay al abrir.

enum RadarDay { case hoy, ayer }

struct RadarFrameUi: Identifiable {
    let ts: String
    let capturedAt: String     // "2026-07-03T18:40"
    var image: UIImage? = nil
    var id: String { ts }
    var timeLabel: String {
        let t = capturedAt.split(separator: "T").last.map(String.init) ?? ""
        return String(t.prefix(5))
    }
}

struct RadarView: View {
    private let radarApi = AppDependencies.shared.container.radarApi
    private let getSchools = AppDependencies.shared.container.getSchools
    private let getTodayScores = AppDependencies.shared.container.getTodayScores

    @State private var frames: [RadarFrameUi] = []
    @State private var bounds: RadarOverlayBounds? = nil
    @State private var radarCode = "es"
    @State private var day: RadarDay = .hoy
    @State private var errorText: String? = nil

    @State private var frameIndex = 0
    @State private var playing = true          // autoplay al abrir
    @State private var opacity: Double = 1.0   // 100% por defecto
    @State private var opacityPanel = false
    @State private var mapStyle: MapStyleKind = .topo
    @State private var showSchools = true
    @State private var zoomLevel: Double = 4.6

    @State private var schools: [School] = []
    @State private var scores: [String: Int] = [:]
    @State private var selectedSchool: School? = nil
    @State private var userCoord: CLLocationCoordinate2D? = nil
    @State private var focusCoord: CLLocationCoordinate2D? = nil
    @State private var focusToken = 0

    @State private var animTask: Task<Void, Never>? = nil
    @State private var loadKey = ""

    private var readyFrames: [RadarFrameUi] { frames.filter { $0.image != nil } }
    private var currentLabel: String {
        let rf = readyFrames
        guard !rf.isEmpty else { return "--:--" }
        return rf[min(max(frameIndex, 0), rf.count - 1)].timeLabel
    }
    private var isNow: Bool {
        day == .hoy && !readyFrames.isEmpty && frameIndex == readyFrames.count - 1
    }

    var body: some View {
        NavigationStack {
            content
        }
    }

    private var content: some View {
        ZStack {
            MapLibreView(
                center: CLLocationCoordinate2D(latitude: 39.6, longitude: -3.6),
                zoom: 4.6,
                markers: markers,
                style: mapStyle,
                onZoomChange: { z in zoomLevel = z },
                onTapMarker: { id in
                    selectedSchool = schools.first { $0.id == id }
                },
                focusCoordinate: focusCoord,
                focusZoom: 9,
                focusToken: focusToken,
                radarImage: readyFrames.isEmpty ? nil
                    : readyFrames[min(max(frameIndex, 0), readyFrames.count - 1)].image,
                radarBounds: bounds,
                radarOpacity: opacity
            )
            .ignoresSafeArea(edges: .top)

            // Título flotante + crédito AEMET (licencia)
            VStack {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("RADAR").font(Cumbre.mono(10, .bold)).tracking(1.8)
                            .foregroundStyle(Cumbre.terra)
                        Text("Lluvia en directo").font(Cumbre.serif(17, .semibold))
                            .foregroundStyle(Cumbre.ink)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(Cumbre.bg.opacity(0.92))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))

                    Spacer()

                    Text("AEMET").font(Cumbre.mono(9, .bold)).tracking(1.2)
                        .foregroundStyle(Cumbre.ink3)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Cumbre.bg.opacity(0.85))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .padding(10)
                Spacer()
            }

            // Botonera lateral (derecha): botones directos de un toque.
            HStack {
                Spacer()
                VStack(spacing: 8) {
                    sideButton("square.3.layers.3d",
                               tint: mapStyle == .satellite ? Cumbre.terra : Cumbre.ink2) {
                        mapStyle = mapStyle == .topo ? .satellite : .topo
                    }
                    sideButton("mappin.and.ellipse",
                               tint: showSchools ? Cumbre.terra : Cumbre.ink2) {
                        showSchools.toggle()
                    }
                    sideButton("drop", tint: Color(red: 0.17, green: 0.43, blue: 0.89)) {
                        opacityPanel.toggle()
                    }
                    if userCoord != nil {
                        sideButton("location", tint: Color(red: 0.10, green: 0.34, blue: 0.86)) {
                            if let u = userCoord {
                                focusCoord = u
                                focusToken += 1
                            }
                        }
                    }
                }
                .padding(.trailing, 10)
            }

            // Panel pequeño de opacidad (desde la gota)
            if opacityPanel {
                HStack {
                    Spacer()
                    VStack(alignment: .leading, spacing: 4) {
                        Text("LLUVIA \(Int(opacity * 100))%")
                            .font(Cumbre.mono(10, .bold)).tracking(1.4)
                            .foregroundStyle(Cumbre.ink2)
                        Slider(value: $opacity, in: 0.2...1.0)
                            .tint(Cumbre.terra)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .frame(width: 180)
                    .background(Cumbre.bg.opacity(0.97))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
                    .padding(.trailing, 62)
                }
            }

            // Leyenda + mini-ficha + player, anclados abajo.
            VStack(spacing: 8) {
                Spacer()

                HStack {
                    HStack(spacing: 5) {
                        legendDot(Color(red: 0.40, green: 0.66, blue: 0.96)); legendText("DÉBIL")
                        legendDot(Color(red: 0.17, green: 0.43, blue: 0.89)); legendText("MEDIA")
                        legendDot(Color(red: 0.05, green: 0.23, blue: 0.61)); legendText("FUERTE")
                    }
                    .padding(.horizontal, 8).padding(.vertical, 5)
                    .background(Cumbre.bg.opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
                    Spacer()
                }
                .padding(.horizontal, 10)

                if let school = selectedSchool {
                    schoolCard(school)
                        .padding(.horizontal, 10)
                }

                if let e = errorText {
                    Text(e).font(.system(size: 14)).foregroundStyle(.red)
                        .padding(10)
                        .background(Cumbre.bg.opacity(0.95))
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                }

                player
                    .padding(.horizontal, 10)
                    .padding(.bottom, 6)
            }
        }
        .task { await loadAll() }
        .onDisappear { animTask?.cancel() }
    }

    // MARK: - Player flotante

    private var player: some View {
        VStack(spacing: 6) {
            HStack {
                dayChip("HOY", selected: day == .hoy) { switchDay(.hoy) }
                dayChip("AYER", selected: day == .ayer) { switchDay(.ayer) }
                Spacer()
                Text(currentLabel).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                if isNow {
                    Text("AHORA").font(Cumbre.mono(9, .bold)).tracking(1.4)
                        .foregroundStyle(Cumbre.terra)
                }
            }
            HStack(spacing: 10) {
                Button {
                    if playing { stopAnim() } else { startAnim() }
                } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(.white)
                        .frame(width: 46, height: 46)
                        .background(Cumbre.terra)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(readyFrames.count < 2)

                VStack(spacing: 2) {
                    Slider(
                        value: Binding(
                            get: { Double(frameIndex) },
                            set: { v in
                                stopAnim()
                                frameIndex = min(max(Int(v.rounded()), 0),
                                                 max(readyFrames.count - 1, 0))
                            }),
                        in: 0...Double(max(readyFrames.count - 1, 1)))
                        .tint(Cumbre.terra)
                    HStack {
                        Text(readyFrames.first?.timeLabel ?? "")
                            .font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                        Spacer()
                        Text(readyFrames.last?.timeLabel ?? "")
                            .font(Cumbre.mono(9))
                            .foregroundStyle(isNow ? Cumbre.terra : Cumbre.ink3)
                    }
                }
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Cumbre.bg.opacity(0.97))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Cumbre.rule, lineWidth: 1))
    }

    // MARK: - Marcadores (puntito de lejos, diamante con score de cerca)

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user))
        }
        guard showSchools else { return ms }
        let tiny = zoomLevel < 6.5
        let labels = zoomLevel >= 8.5
        for s in schools {
            let score = scores[s.id]
            ms.append(CumbreMarker(
                id: s.id,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                title: s.name,
                kind: tiny ? .dot : .score,
                color: UIColor(score.map { Cumbre.score($0) } ?? Cumbre.rule),
                score: score,
                name: s.name,
                showName: labels))
        }
        return ms
    }

    // MARK: - Carga

    private func loadAll() async {
        await loadLocation()
        await loadFrames()
        await loadSchools()
    }

    private func switchDay(_ d: RadarDay) {
        stopAnim()
        day = d
        frameIndex = 0
        Task { await loadFrames() }
    }

    private func loadFrames() async {
        let df = DateFormatter()
        df.dateFormat = "yyyyMMdd"
        let date = day == .hoy ? Date() : Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        let dateStr = df.string(from: date)
        if loadKey == dateStr && errorText == nil { return }
        loadKey = dateStr
        errorText = nil
        do {
            let dto = try await radarApi.getFrames(lat: nil, lon: nil, hours: 2, date: dateStr)
            radarCode = dto.radar
            bounds = RadarOverlayBounds(
                north: dto.bounds.north, south: dto.bounds.south,
                west: dto.bounds.west, east: dto.bounds.east)
            // Un día entero son hasta 144 ciclos: pasos de ~30 min salvo la
            // última hora, que va completa (igual que Android).
            let all = dto.frames
            let cut = max(all.count - 7, 0)
            let thinned = all.enumerated()
                .filter { $0.offset >= cut || $0.offset % 3 == 0 }
                .map { $0.element }
            frames = thinned.map { RadarFrameUi(ts: $0.ts, capturedAt: $0.capturedAt) }
            for ref in thinned {
                if let bytes = try? await radarApi.getFramePng(radar: dto.radar, ts: ref.ts) {
                    if let img = UIImage(data: bytes.toData()),
                       let idx = frames.firstIndex(where: { $0.ts == ref.ts }) {
                        frames[idx].image = img
                    }
                }
            }
            if playing { startAnim() } else { frameIndex = max(readyFrames.count - 1, 0) }
        } catch {
            errorText = "No se pudo cargar el radar. Comprueba tu conexión."
        }
    }

    private func loadSchools() async {
        guard schools.isEmpty else { return }
        guard let list = try? await getSchools.invoke(
            region: nil, style: nil, rockType: nil, lat: nil, lon: nil, radioKm: nil) else { return }
        schools = list
        // Scores del día para colorear los pines (cacheado en el back).
        let ids = list.map { $0.id }
        var acc: [String: Int] = [:]
        var start = 0
        while start < ids.count {
            let slice = Array(ids[start..<min(start + 60, ids.count)])
            if let batch = try? await getTodayScores.invoke(ids: slice) {
                for s in batch { acc[s.id] = Int(s.todayScore) }
            }
            start += 60
        }
        scores = acc
    }

    private func loadLocation() async {
        if let loc = try? await AppDependencies.shared.container.locationProvider?.current() {
            userCoord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
        }
    }

    // MARK: - Animación

    private func startAnim() {
        animTask?.cancel()
        playing = true
        animTask = Task {
            if frameIndex >= readyFrames.count - 1 { frameIndex = 0 }
            while !Task.isCancelled && playing && frameIndex < readyFrames.count - 1 {
                try? await Task.sleep(nanoseconds: 420_000_000)
                if Task.isCancelled { break }
                frameIndex += 1
            }
            playing = false
        }
    }

    private func stopAnim() {
        animTask?.cancel()
        playing = false
    }

    // MARK: - Piezas pequeñas

    private func sideButton(_ system: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.system(size: 17))
                .foregroundStyle(tint)
                .frame(width: 42, height: 42)
                .background(Cumbre.bg.opacity(0.95))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func dayChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(10, .bold)).tracking(1.2)
                .foregroundStyle(selected ? Cumbre.bg : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(selected ? Cumbre.ink : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8)
                    .stroke(selected ? Cumbre.ink : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func legendDot(_ c: Color) -> some View {
        RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 9, height: 9)
    }

    private func legendText(_ t: String) -> some View {
        Text(t).font(Cumbre.mono(9, .bold)).tracking(1).foregroundStyle(Cumbre.ink2)
    }

    private func schoolCard(_ school: School) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(school.name).font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                if let region = school.region {
                    Text(region).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                }
            }
            Spacer()
            NavigationLink {
                SchoolDetailView(school: school)
            } label: {
                Text("VER DETALLE").font(Cumbre.mono(10, .bold)).tracking(1.2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Cumbre.terra)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            Button { selectedSchool = nil } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(4)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Cumbre.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
    }
}

extension KotlinByteArray {
    /// ByteArray de Kotlin → Data (los PNG del radar).
    func toData() -> Data {
        var data = Data(count: Int(size))
        for i in 0..<Int(size) {
            data[i] = UInt8(bitPattern: get(index: Int32(i)))
        }
        return data
    }
}
