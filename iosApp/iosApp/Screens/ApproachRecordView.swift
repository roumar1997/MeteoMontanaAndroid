import SwiftUI
import Shared
import CoreLocation

// Grabar una aproximación (camino) — APPROACH_DESIGN.md §6.2/§6.4. SOLO ADMIN
// por ahora (ver ApproachesSection): la pantalla en sí es la que verá
// cualquier usuario el día que se abra, no hay nada "provisional" en el diseño.
//
// Mapa REAL con la escuela (piedras/sectores/parkings) de fondo, no un mapa en
// blanco: sin contexto no se sabe hacia dónde vas ni dónde dejar la chincheta.
// Las chinchetas se pueden añadir MIENTRAS grabas (se guardan en memoria y se
// suben todas juntas al terminar, cuando ya existe el id de la aproximación).
//
// Sin permiso de segundo plano (§2.5): el GPS solo corre con la app abierta.

private struct PendingPin: Identifiable {
    let id = UUID()
    let coord: CLLocationCoordinate2D
    let kind: String
    let message: String?
    let image: UIImage?
}

struct ApproachRecordView: View {
    let school: School
    let blocks: [Block]
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var fromBlockId: String?
    @State private var toBlockId: String?
    @State private var recording = false
    @State private var paused = false
    @State private var points: [CLLocationCoordinate2D] = []
    @State private var distanceM: Double = 0
    @State private var elapsedSeconds = 0
    @State private var timer: Timer?
    @State private var savingStep = false
    @State private var name = ""
    @State private var saving = false
    @State private var errorMessage: String?
    @State private var userCoord: CLLocationCoordinate2D?

    @State private var placingPin = false
    @State private var newPinCoord: CLLocationCoordinate2D?
    @State private var pendingPins: [PendingPin] = []
    @State private var uploadProgress: String?

    private var parkings: [Block] { blocks.filter { $0.type.uppercased() == "PARKING" } }
    private var sectors: [Block] { blocks.filter { $0.type.uppercased() == "ZONE" || $0.type.uppercased() == "BLOCK" } }

    private var blockMarkers: [CumbreMarker] {
        blocks.map { b in
            let kind: MarkerKind = b.type.uppercased() == "PARKING" ? .parking
                : b.type.uppercased() == "ZONE" ? .zone : .block
            return CumbreMarker(id: "b:\(b.id)", coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                                 title: b.name, kind: kind, color: color(for: b.type))
        }
    }
    private var pinMarkers: [CumbreMarker] {
        pendingPins.map { p in
            CumbreMarker(id: "pending:\(p.id)", coordinate: p.coord, title: p.message ?? "", kind: .dot,
                         color: UIColor(Cumbre.terra))
        }
    }
    private var allMarkers: [CumbreMarker] {
        var ms = blockMarkers + pinMarkers
        if let u = userCoord { ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user)) }
        return ms
    }
    private var trackPolyline: CumbrePolyline? {
        guard points.count >= 2 else { return nil }
        return CumbrePolyline(id: "track", coordinates: points, color: UIColor(Cumbre.terra), width: 4, alpha: 1)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                if !savingStep {
                    recordingBody
                } else {
                    saveStep
                }
            }
            .background(Cumbre.bg)
            .navigationTitle(savingStep ? "Guardar camino" : "Grabar aproximación")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("CERRAR") {
                        AppDependencies.shared.locationBridge.stopStream()
                        timer?.invalidate()
                        dismiss()
                    }
                }
            }
        }
        .task {
            guard AppDependencies.shared.locationBridge.hasPermission() else { return }
            AppDependencies.shared.locationBridge.startStream { loc in
                DispatchQueue.main.async {
                    userCoord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
                }
            }
        }
        .onDisappear { AppDependencies.shared.locationBridge.stopStream() }
    }

    @ViewBuilder
    private var recordingBody: some View {
        VStack(spacing: 0) {
            if !recording {
                VStack(alignment: .leading, spacing: 10) {
                    Text("ORIGEN (parking)").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    blockPicker(options: parkings, selection: $fromBlockId, placeholder: "Elige un parking")
                    Text("DESTINO (sector/piedra)").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    blockPicker(options: sectors, selection: $toBlockId, placeholder: "Elige un sector")
                }
                .padding(16)
            }

            ZStack(alignment: .top) {
                MapLibreView(
                    center: userCoord ?? CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                    zoom: 15,
                    markers: allMarkers,
                    style: .satellite,
                    onMapTap: placingPin ? { coord in newPinCoord = coord } : nil,
                    polylines: trackPolyline.map { [$0] } ?? []
                )

                if recording {
                    HStack {
                        Spacer()
                        Button {
                            placingPin.toggle()
                        } label: {
                            Text(placingPin ? "TOCA EL MAPA" : "+ CHINCHETA")
                                .font(Cumbre.mono(10, .bold)).tracking(1)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12).padding(.vertical, 9)
                                .background(placingPin ? Cumbre.ink2 : Cumbre.terra)
                        }
                    }
                    .padding(10)
                }
            }
            .frame(maxHeight: .infinity)

            VStack(spacing: 10) {
                if !pendingPins.isEmpty {
                    Text("\(pendingPins.count) chincheta\(pendingPins.count == 1 ? "" : "s") en este camino")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
                VStack(spacing: 6) {
                    Text(formattedElapsed).font(.system(size: 32, weight: .bold, design: .monospaced))
                    Text(formattedDistance).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                }

                HStack(spacing: 12) {
                    if recording {
                        Button {
                            paused.toggle()
                            if paused { timer?.invalidate() } else { startTimer() }
                        } label: {
                            Text(paused ? "REANUDAR" : "PAUSAR")
                                .font(Cumbre.mono(11, .bold)).tracking(1)
                                .foregroundStyle(Cumbre.ink).padding(.vertical, 14).frame(maxWidth: .infinity)
                                .background(Cumbre.paper).overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                        }
                        Button {
                            stopRecording()
                        } label: {
                            Text("TERMINAR")
                                .font(Cumbre.mono(11, .bold)).tracking(1)
                                .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                                .background(Cumbre.terra)
                        }
                    } else {
                        Button {
                            startRecording()
                        } label: {
                            Text("INICIAR").font(Cumbre.mono(12, .bold)).tracking(1.4)
                                .foregroundStyle(.white).padding(.vertical, 16).frame(maxWidth: .infinity)
                                .background(fromBlockId != nil && toBlockId != nil ? Cumbre.terra : Cumbre.ink3)
                        }
                        .disabled(fromBlockId == nil || toBlockId == nil)
                    }
                }
            }
            .padding(16)
            .background(Cumbre.paper.opacity(0.98))
        }
        .sheet(item: $newPinCoordItem) { item in
            NewPinDraftSheet(coord: item.coord) { kind, message, image in
                pendingPins.append(PendingPin(coord: item.coord, kind: kind, message: message, image: image))
                placingPin = false
                newPinCoord = nil
            }
            .presentationDetents([.large])
        }
    }

    private var newPinCoordItem: Binding<CoordItem?> {
        Binding(get: { newPinCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { newPinCoord = nil } })
    }

    @ViewBuilder
    private func blockPicker(options: [Block], selection: Binding<String?>, placeholder: String) -> some View {
        Menu {
            ForEach(options, id: \.id) { b in
                Button(b.name.isEmpty ? placeholder : b.name) { selection.wrappedValue = b.id }
            }
        } label: {
            HStack {
                Text(options.first(where: { $0.id == selection.wrappedValue })?.name ?? placeholder)
                    .foregroundStyle(selection.wrappedValue == nil ? Cumbre.ink3 : Cumbre.ink)
                Spacer()
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
            }
            .padding(10)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    @ViewBuilder
    private var saveStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Nombre del camino").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
            TextField("p. ej. Parking alto → Sector Techos", text: $name)
                .padding(10)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))

            Text("\(formattedDistance) · \(formattedElapsed) · \(points.count) puntos"
                 + (pendingPins.isEmpty ? "" : " · \(pendingPins.count) chinchetas"))
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)

            if let err = errorMessage {
                Text(err).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
            }
            if let progress = uploadProgress {
                Text(progress).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }

            Spacer()

            Button {
                Task { await save() }
            } label: {
                if saving {
                    ProgressView().tint(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
                } else {
                    Text("GUARDAR").font(Cumbre.mono(12, .bold)).tracking(1.4)
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                }
            }
            .background(Cumbre.terra)
            .disabled(saving)
        }
        .padding(16)
    }

    private var formattedElapsed: String {
        let m = elapsedSeconds / 60, s = elapsedSeconds % 60
        return String(format: "%02d:%02d", m, s)
    }
    private var formattedDistance: String {
        distanceM >= 1000 ? String(format: "%.2f km", distanceM / 1000) : "\(Int(distanceM)) m"
    }

    private func startRecording() {
        recording = true
        points = []
        distanceM = 0
        elapsedSeconds = 0
        startTimer()
        // El .task de arriba ya está siguiendo userCoord; reutilizamos ese
        // stream para ir sumando puntos al camino sin abrir un 2º listener.
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            elapsedSeconds += 1
            if !paused, let u = userCoord {
                if let last = points.last {
                    let d = Geo.shared.haversineKm(lat1: last.latitude, lon1: last.longitude,
                                                    lat2: u.latitude, lon2: u.longitude) * 1000
                    guard d > 3 else { return }
                    distanceM += d
                }
                points.append(u)
            }
        }
    }

    private func stopRecording() {
        timer?.invalidate()
        recording = false
        placingPin = false
        savingStep = true
    }

    private func save() async {
        guard points.count >= 2 else {
            errorMessage = "El camino grabado es demasiado corto."
            return
        }
        saving = true
        errorMessage = nil
        let pathJson = "[" + points.map { "[\($0.latitude),\($0.longitude)]" }.joined(separator: ",") + "]"
        let req = CreateApproachRequest(
            fromBlockId: fromBlockId, toBlockId: toBlockId,
            name: name.isEmpty ? nil : name,
            pathJson: pathJson,
            distanceM: KotlinInt(int: Int32(distanceM)),
            ascentM: nil,
            durationMin: KotlinInt(int: Int32(elapsedSeconds / 60)),
            source: "RECORDED")
        do {
            let approachDto = try await AppDependencies.shared.container.approachApi.createApproach(schoolId: school.id, req: req)
            // Las chinchetas se subieron a memoria mientras grabábamos; ahora
            // que existe el id de la aproximación, se suben de una en una.
            for (idx, pin) in pendingPins.enumerated() {
                uploadProgress = "Subiendo chincheta \(idx + 1) de \(pendingPins.count)…"
                var photoPath: String?
                if let img = pin.image {
                    photoPath = try? await StorageUploader.uploadApproachPinPhoto(img)
                }
                let pinReq = AddApproachPinRequest(
                    lat: pin.coord.latitude, lon: pin.coord.longitude, positionIdx: Int32(idx),
                    kind: pin.kind, message: pin.message, photoPath: photoPath)
                _ = try? await AppDependencies.shared.container.approachApi.addPin(approachId: approachDto.id, req: pinReq)
            }
            uploadProgress = nil
            saving = false
            onSaved()
            dismiss()
        } catch {
            saving = false
            uploadProgress = nil
            errorMessage = "No se pudo guardar. Inténtalo de nuevo."
        }
    }

    private func color(for type: String) -> UIColor {
        switch type.uppercased() {
        case "PARKING": return UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
        case "ZONE": return UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
        default: return UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
        }
    }
}

private struct CoordItem: Identifiable {
    let coord: CLLocationCoordinate2D
    var id: String { "\(coord.latitude),\(coord.longitude)" }
}

/// Borrador de chincheta EN MEMORIA mientras se graba (no llega al servidor
/// hasta que se guarda la aproximación entera y existe su id).
private struct NewPinDraftSheet: View {
    let coord: CLLocationCoordinate2D
    let onDone: (String, String?, UIImage?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var kind = "LANDMARK"
    @State private var message = ""
    @State private var image: UIImage?

    private let kinds: [(String, String)] = [
        ("FORK", "◆ Bifurcación"), ("LANDMARK", "● Referencia"),
        ("HAZARD", "▲ Peligro"), ("KEY", "★ Paso clave")
    ]
    private var canSave: Bool { !message.trimmingCharacters(in: .whitespaces).isEmpty || image != nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("TIPO").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(kinds, id: \.0) { k, label in
                                Button { kind = k } label: {
                                    Text(label).font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(kind == k ? .white : Cumbre.terra)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(kind == k ? Cumbre.terra : Cumbre.paper)
                                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra, lineWidth: 1))
                                }
                            }
                        }
                    }

                    if let img = image {
                        Image(uiImage: img).resizable().scaledToFit()
                            .frame(maxHeight: 200).clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                    HStack(spacing: 8) {
                        Button {
                            presentSystemCamera(context: "approach-pin") { image = $0 }
                        } label: {
                            Text("📷 HACER FOTO").font(Cumbre.mono(10, .bold))
                                .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(Cumbre.paper).overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                        }
                        Button {
                            presentSystemPhotoPicker(context: "approach-pin") { image = $0 }
                        } label: {
                            Text("GALERÍA").font(Cumbre.mono(10, .bold))
                                .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(Cumbre.paper).overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                        }
                    }

                    Text("NOTA (opcional si hay foto)").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    TextField("p. ej. En la bifurcación, a la derecha", text: $message, axis: .vertical)
                        .lineLimit(3...6)
                        .padding(10)
                        .background(Cumbre.paper)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))

                    Button {
                        onDone(kind, message.trimmingCharacters(in: .whitespaces).isEmpty ? nil : message, image)
                        dismiss()
                    } label: {
                        Text("AÑADIR AL CAMINO").font(Cumbre.mono(11, .bold)).tracking(1)
                            .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13)
                    }
                    .background(canSave ? Cumbre.terra : Cumbre.ink3)
                    .disabled(!canSave)

                    if !canSave {
                        Text("Añade una foto o una nota.")
                            .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg)
            .navigationTitle("Nueva chincheta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("CANCELAR") { dismiss() }
                }
            }
        }
    }
}
