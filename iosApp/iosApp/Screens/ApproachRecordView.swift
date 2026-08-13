import SwiftUI
import Shared
import CoreLocation

// Grabar una aproximación (camino) — APPROACH_DESIGN.md §6.2. SOLO ADMIN por
// ahora (ver ApproachesSection): la pantalla en sí es la que verá cualquier
// usuario el día que se abra, no hay nada "provisional" en el diseño.
//
// Sin permiso de segundo plano (§2.5): el GPS solo corre con la app abierta,
// mismo servicio en primer plano que ya usa el punto azul del mapa normal.

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

    private var parkings: [Block] { blocks.filter { $0.type.uppercased() == "PARKING" } }
    private var sectors: [Block] { blocks.filter { $0.type.uppercased() == "ZONE" || $0.type.uppercased() == "BLOCK" } }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
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
    }

    @ViewBuilder
    private var recordingBody: some View {
        VStack(spacing: 16) {
            if !recording {
                VStack(alignment: .leading, spacing: 10) {
                    Text("ORIGEN (parking)").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    blockPicker(options: parkings, selection: $fromBlockId, placeholder: "Elige un parking")
                    Text("DESTINO (sector/piedra)").font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                    blockPicker(options: sectors, selection: $toBlockId, placeholder: "Elige un sector")
                }
                .padding(16)
            }

            Spacer()

            VStack(spacing: 6) {
                Text(formattedElapsed).font(.system(size: 40, weight: .bold, design: .monospaced))
                Text(formattedDistance).font(.system(size: 16)).foregroundStyle(Cumbre.ink2)
            }

            Spacer()

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
            .padding(16)
        }
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

            Text("\(formattedDistance) · \(formattedElapsed) · \(points.count) puntos")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)

            if let err = errorMessage {
                Text(err).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
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
        guard AppDependencies.shared.locationBridge.hasPermission() else { return }
        AppDependencies.shared.locationBridge.startStream { loc in
            DispatchQueue.main.async {
                let coord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
                if let last = points.last {
                    let d = Geo.shared.haversineKm(lat1: last.latitude, lon1: last.longitude,
                                                    lat2: coord.latitude, lon2: coord.longitude) * 1000
                    // Filtra ruido bajo arbolado: descarta puntos casi idénticos (< 3 m).
                    guard d > 3 else { return }
                    distanceM += d
                }
                points.append(coord)
            }
        }
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            elapsedSeconds += 1
        }
    }

    private func stopRecording() {
        AppDependencies.shared.locationBridge.stopStream()
        timer?.invalidate()
        recording = false
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
            _ = try await AppDependencies.shared.container.approachApi.createApproach(schoolId: school.id, req: req)
            saving = false
            onSaved()
            dismiss()
        } catch {
            saving = false
            errorMessage = "No se pudo guardar. Inténtalo de nuevo."
        }
    }
}
