import SwiftUI
import MapKit
import Shared

/// Mapa general de la escuela como IMAGEN (satélite + piedras, parkings y sectores), para la GUÍA y su PDF.
/// Se genera con conexión y se guarda en disco: sin conexión se ve la última copia (al descargar la escuela se
/// genera también). Los nombres de los sectores NO van pintados en la imagen: van encima como botones, en
/// coordenadas 0–1 de la imagen, para poder tocarlos y saltar a ese sector de la guía.
struct GuideOverview {
    struct Place: Codable, Hashable {
        let name: String
        /// Posición en la imagen, de 0 a 1.
        let x: Double
        let y: Double
    }
    let image: UIImage
    let sectors: [Place]
}

enum GuideOverviewStore {
    private static let dir: URL = {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("guide-overview", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    private static func imageURL(_ schoolId: String) -> URL { dir.appendingPathComponent("\(schoolId).png") }
    private static func placesURL(_ schoolId: String) -> URL { dir.appendingPathComponent("\(schoolId).json") }

    /// La última copia guardada, o nil si nunca se generó.
    static func cached(schoolId: String) -> GuideOverview? {
        guard let img = UIImage(contentsOfFile: imageURL(schoolId).path) else { return nil }
        let places = (try? Data(contentsOf: placesURL(schoolId)))
            .flatMap { try? JSONDecoder().decode([GuideOverview.Place].self, from: $0) } ?? []
        return GuideOverview(image: img, sectors: places)
    }

    /// Genera el mapa (hace falta conexión para el fondo) y lo guarda. nil si no hay nada que pintar o no hay red.
    static func generate(schoolId: String, blocks: [Block]) async -> GuideOverview? {
        let stones = blocks.filter { !["ZONE", "PARKING"].contains($0.type.uppercased()) }
        let zones = blocks.filter { $0.type.uppercased() == "ZONE" }
        let parkings = blocks.filter { $0.type.uppercased() == "PARKING" }
        let all = stones + zones + parkings
        guard !all.isEmpty else { return nil }

        // Encuadre: todo lo que hay, con margen; un mínimo para que una escuela de 3 piedras no salga a ras de roca.
        let lats = all.map(\.lat), lons = all.map(\.lon)
        let center = CLLocationCoordinate2D(latitude: (lats.min()! + lats.max()!) / 2,
                                            longitude: (lons.min()! + lons.max()!) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max((lats.max()! - lats.min()!) * 1.35, 0.0025),
                                    longitudeDelta: max((lons.max()! - lons.min()!) * 1.35, 0.0025))
        let options = MKMapSnapshotter.Options()
        options.region = MKCoordinateRegion(center: center, span: span)
        options.size = CGSize(width: 1100, height: 800)
        options.mapType = .satellite
        options.pointOfInterestFilter = .excludingAll

        guard let snap = try? await MKMapSnapshotter(options: options).start() else { return nil }
        let size = snap.image.size

        let image = UIGraphicsImageRenderer(size: size).image { ctx in
            snap.image.draw(at: .zero)
            let cg = ctx.cgContext
            // Piedras: punto terracota con borde blanco y su nombre al lado (en pequeño, con sombra para leerse
            // sobre el satélite). Tienen que verse también en el PDF, que reduce la imagen a la mitad.
            let nameAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 15, weight: .bold), .foregroundColor: UIColor.white,
                .strokeColor: UIColor.black, .strokeWidth: -3.5
            ]
            // Piedras muy juntas (se pisarían) van en UN icono con cuántas son, en vez de un montón de puntos.
            var groups: [(center: CGPoint, stones: [Block])] = []
            for b in stones {
                let p = snap.point(for: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon))
                if let i = groups.firstIndex(where: { hypot($0.center.x - p.x, $0.center.y - p.y) < 34 }) {
                    groups[i].stones.append(b)
                } else {
                    groups.append((p, [b]))
                }
            }
            for g in groups {
                let p = g.center, b = g.stones[0]
                // Crashpad (bloque) o mosquetón (vía) en un círculo blanco con borde terracota.
                let r = CGRect(x: p.x - 17, y: p.y - 17, width: 34, height: 34)
                cg.setFillColor(UIColor.white.cgColor)
                cg.fillEllipse(in: r)
                cg.setStrokeColor(UIColor(Cumbre.terraFill).cgColor)
                cg.setLineWidth(2.5)
                cg.strokeEllipse(in: r)
                // El icono es de tipo «plantilla» (sin color propio): se tiñe o sale blanco sobre blanco.
                UIImage(named: stoneIconName(b))?
                    .withTintColor(UIColor(Cumbre.terraFill), renderingMode: .alwaysOriginal)
                    .draw(in: r.insetBy(dx: 6, dy: 6))
                if g.stones.count > 1 {
                    // Globo terracota con el número de piedras.
                    let badge = CGRect(x: r.maxX - 12, y: r.minY - 6, width: 20, height: 20)
                    cg.setFillColor(UIColor(Cumbre.terraFill).cgColor)
                    cg.fillEllipse(in: badge)
                    cg.setStrokeColor(UIColor.white.cgColor)
                    cg.setLineWidth(1.5)
                    cg.strokeEllipse(in: badge)
                    let n = NSAttributedString(string: "\(g.stones.count)", attributes: [
                        .font: UIFont.systemFont(ofSize: 11, weight: .bold), .foregroundColor: UIColor.white
                    ])
                    n.draw(at: CGPoint(x: badge.midX - n.size().width / 2, y: badge.midY - n.size().height / 2))
                } else if !b.name.isEmpty {
                    let s = NSAttributedString(string: b.name, attributes: nameAttrs)
                    s.draw(at: CGPoint(x: p.x + 20, y: p.y - s.size().height / 2))
                }
            }
            // Parkings: cuadrado azul con "P".
            for b in parkings {
                let p = snap.point(for: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon))
                let r = CGRect(x: p.x - 16, y: p.y - 16, width: 32, height: 32)
                UIColor(red: 0.11, green: 0.40, blue: 0.84, alpha: 1).setFill()
                UIBezierPath(roundedRect: r, cornerRadius: 7).fill()
                UIColor.white.setStroke()
                let border = UIBezierPath(roundedRect: r, cornerRadius: 7); border.lineWidth = 2.5; border.stroke()
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 19, weight: .heavy), .foregroundColor: UIColor.white
                ]
                let s = NSAttributedString(string: "P", attributes: attrs)
                let t = s.size()
                s.draw(at: CGPoint(x: p.x - t.width / 2, y: p.y - t.height / 2))
            }
        }

        // Cada sector, donde está su marca; si no tiene, en el centro de sus piedras.
        var places: [GuideOverview.Place] = []
        for z in zones where !z.name.isEmpty {
            let inside = stones.filter { $0.sectorBlockId == z.id }
            let coord = inside.isEmpty
                ? CLLocationCoordinate2D(latitude: z.lat, longitude: z.lon)
                : CLLocationCoordinate2D(latitude: inside.map(\.lat).reduce(0, +) / Double(inside.count),
                                         longitude: inside.map(\.lon).reduce(0, +) / Double(inside.count))
            let p = snap.point(for: coord)
            // El nombre va ENCIMA de sus piedras (no tapándolas).
            places.append(.init(name: z.name, x: Double(p.x / size.width), y: Double((p.y - 34) / size.height)))
        }
        places = separated(places, imageSize: size)

        try? image.pngData()?.write(to: imageURL(schoolId))
        try? JSONEncoder().encode(places).write(to: placesURL(schoolId))
        return GuideOverview(image: image, sectors: places)
    }

    /// Separa los nombres de sector que se pisarían (dos sectores juntos, p. ej. «Pradera» y «Pradera inferior»):
    /// el que choca con uno ya colocado baja lo justo para quedar debajo.
    private static func separated(_ places: [GuideOverview.Place], imageSize: CGSize) -> [GuideOverview.Place] {
        func rect(_ p: GuideOverview.Place) -> CGRect {
            let w = CGFloat(p.name.count) * 10 + 22, h: CGFloat = 30   // ancho aproximado de la etiqueta, en px
            return CGRect(x: p.x * imageSize.width - w / 2, y: p.y * imageSize.height - h / 2, width: w, height: h)
        }
        var out: [GuideOverview.Place] = []
        for p in places.sorted(by: { $0.y < $1.y }) {
            var q = p
            while let hit = out.first(where: { rect($0).intersects(rect(q)) }) {
                let y = (rect(hit).maxY + 16) / imageSize.height
                q = .init(name: q.name, x: q.x, y: Double(y))
            }
            out.append(q)
        }
        return out
    }
}

/// La imagen del mapa con los sectores encima como botones. Se usa en la tarjeta de la guía y a pantalla completa.
struct GuideOverviewCanvas: View {
    let overview: GuideOverview
    var labelScale: CGFloat = 1
    let onSector: (String) -> Void

    var body: some View {
        Image(uiImage: overview.image).resizable()
            .aspectRatio(overview.image.size.width / max(overview.image.size.height, 1), contentMode: .fit)
            .overlay {
                GeometryReader { geo in
                    ForEach(overview.sectors, id: \.self) { s in
                        Button { onSector(s.name) } label: {
                            Text(s.name.uppercased())
                                .font(Cumbre.mono(8.5 * labelScale, .bold)).tracking(0.4)
                                .foregroundStyle(Cumbre.ink).lineLimit(1)
                                .padding(.horizontal, 5 * labelScale).padding(.vertical, 2.5 * labelScale)
                                .background(Color.white.opacity(0.85), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .position(x: s.x * geo.size.width, y: s.y * geo.size.height)
                    }
                }
            }
    }
}

/// Icono de la piedra: crashpad (bloque) o mosquetón (vía), en un círculo blanco para que se lea sobre el satélite.
private func stoneIconName(_ b: Block) -> String { b.discipline.uppercased() == "ROUTE" ? "kind_route" : "kind_boulder" }

/// El mapa general a pantalla completa como MAPA REAL (con conexión): al acercarte el satélite gana detalle, los
/// iconos crecen y aparecen los nombres de las piedras. Tocar un sector = ir a él en la guía.
struct GuideLiveMap: View {
    let blocks: [Block]
    let onSector: (String) -> Void
    /// Dentro de la tarjeta de la guía (sin botón de cerrar). false = a pantalla completa.
    var embedded = false
    @Environment(\.dismiss) private var dismiss
    @State private var position: MapCameraPosition = .automatic
    /// Distancia de la cámara (m): decide el tamaño de los iconos y si se ven los nombres.
    @State private var distance: Double = 2000

    private var stones: [Block] { blocks.filter { !["ZONE", "PARKING"].contains($0.type.uppercased()) } }
    private var parkings: [Block] { blocks.filter { $0.type.uppercased() == "PARKING" } }
    private var zones: [Block] { blocks.filter { $0.type.uppercased() == "ZONE" && !$0.name.isEmpty } }

    private var iconSize: CGFloat { distance < 250 ? 30 : distance < 700 ? 22 : distance < 1600 ? 16 : 12 }
    private var showNames: Bool { distance < 450 }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Map(position: $position) {
                ForEach(stones, id: \.id) { b in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon), anchor: .center) {
                        VStack(spacing: 2) {
                            Image(stoneIconName(b)).resizable().scaledToFit()
                                .frame(width: iconSize, height: iconSize)
                                .padding(iconSize * 0.18)
                                .background(Circle().fill(.white))
                                .overlay(Circle().stroke(Cumbre.terraFill, lineWidth: 1.5))
                                .shadow(color: .black.opacity(0.4), radius: 2, y: 1)
                            if showNames && !b.name.isEmpty {
                                Text(b.name).font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                                    .shadow(color: .black, radius: 2).lineLimit(1)
                            }
                        }
                        .animation(.snappy(duration: 0.2), value: iconSize)
                    }
                }
                ForEach(parkings, id: \.id) { b in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon), anchor: .center) {
                        Text("P").font(.system(size: 15, weight: .heavy)).foregroundStyle(.white)
                            .frame(width: 28, height: 28)
                            .background(Color(red: 0.11, green: 0.40, blue: 0.84), in: RoundedRectangle(cornerRadius: 7))
                            .overlay(RoundedRectangle(cornerRadius: 7).stroke(.white, lineWidth: 2))
                            .shadow(color: .black.opacity(0.4), radius: 2, y: 1)
                    }
                }
                ForEach(zones, id: \.id) { z in
                    Annotation("", coordinate: sectorCoord(z), anchor: .bottom) {
                        Button { if !embedded { dismiss() }; onSector(z.name) } label: {
                            Text(z.name.uppercased()).font(Cumbre.mono(9, .bold)).tracking(0.5)
                                .foregroundStyle(Cumbre.ink).lineLimit(1)
                                .padding(.horizontal, 6).padding(.vertical, 3)
                                .background(Color.white.opacity(0.88), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .padding(.bottom, iconSize + 10)   // encima de sus piedras, sin taparlas
                    }
                }
            }
            .mapStyle(.imagery(elevation: .flat))
            .onMapCameraChange(frequency: .continuous) { ctx in distance = ctx.camera.distance }
            .ignoresSafeArea(edges: embedded ? [] : .all)

            if !embedded {
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 36, height: 36).background(Color.black.opacity(0.55), in: Circle())
            }
            .padding(16)
            .accessibilityLabel(L("Cerrar"))
            }
        }
    }

    /// El sector, en el centro de sus piedras (su marca puede estar lejos de ellas).
    private func sectorCoord(_ z: Block) -> CLLocationCoordinate2D {
        let inside = stones.filter { $0.sectorBlockId == z.id }
        guard !inside.isEmpty else { return CLLocationCoordinate2D(latitude: z.lat, longitude: z.lon) }
        return CLLocationCoordinate2D(latitude: inside.map(\.lat).reduce(0, +) / Double(inside.count),
                                      longitude: inside.map(\.lon).reduce(0, +) / Double(inside.count))
    }
}

/// El mapa general a pantalla completa (SIN conexión, con la imagen guardada): pellizcar para acercar, arrastrar
/// para moverse, doble toque para volver.
struct GuideOverviewFullScreen: View {
    let overview: GuideOverview
    let onSector: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            GuideOverviewCanvas(overview: overview, labelScale: 1 / scale) { name in
                dismiss()
                onSector(name)
            }
            .scaleEffect(scale)
            .offset(offset)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .gesture(
                MagnifyGesture()
                    .onChanged { scale = min(max(baseScale * $0.magnification, 1), 6) }
                    .onEnded { _ in baseScale = scale; if scale == 1 { offset = .zero; baseOffset = .zero } }
                    .simultaneously(with: DragGesture()
                        .onChanged { v in
                            guard scale > 1 else { return }
                            offset = CGSize(width: baseOffset.width + v.translation.width,
                                            height: baseOffset.height + v.translation.height)
                        }
                        .onEnded { _ in baseOffset = offset })
            )
            .onTapGesture(count: 2) {
                withAnimation(.snappy) { scale = 1; baseScale = 1; offset = .zero; baseOffset = .zero }
            }
            VStack(alignment: .trailing, spacing: 8) {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 34, height: 34).background(Color.white.opacity(0.2), in: Circle())
                }
                .accessibilityLabel(L("Cerrar"))
                Text(L("Toca un sector para ir a él"))
                    .font(.system(size: 11.5, weight: .semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color.black.opacity(0.5), in: Capsule())
            }
            .padding(16)
        }
    }
}
