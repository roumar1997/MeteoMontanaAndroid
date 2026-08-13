import SwiftUI
import Shared
import CoreLocation

// Pantalla "SEGUIR" de una aproximación — Fase 1/2 de APPROACH_DESIGN.md
// §6.3/§6.4. Sin navegación giro a giro: solo la línea y tu punto azul.
// Línea continua si está VERIFICADA, discontinua si no.
//
// "+ CHINCHETA" (SOLO ADMIN por ahora): la pantalla en sí es la definitiva —
// el día que se abra a cualquier usuario es quitar el gate de `isAdmin`.

struct ApproachFollowView: View {
    let approach: Approach
    let schoolName: String
    var isAdmin: Bool = false
    var onPinAdded: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    @State private var userCoord: CLLocationCoordinate2D?
    @State private var selectedPin: ApproachPin?
    @State private var placingPin = false
    @State private var newPinCoord: CLLocationCoordinate2D?
    @State private var confirmDelete = false
    @State private var deleting = false

    private var pathCoords: [CLLocationCoordinate2D] { parseWallPath(approach.pathJson) }

    private var pinMarkers: [CumbreMarker] {
        approach.pins.map { p in
            CumbreMarker(
                id: "pin:\(p.id)",
                coordinate: CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon),
                title: p.message ?? "",
                kind: .dot,
                color: colorForPinKind(p.kind)
            )
        }
    }

    private var allMarkers: [CumbreMarker] {
        var ms = pinMarkers
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user))
        }
        return ms
    }

    var body: some View {
        ZStack(alignment: .top) {
            MapLibreView(
                center: pathCoords.first ?? CLLocationCoordinate2D(latitude: 0, longitude: 0),
                zoom: 15,
                markers: allMarkers,
                style: .satellite,
                onTapMarker: { id in
                    guard id.hasPrefix("pin:") else { return }
                    let pinId = String(id.dropFirst(4))
                    selectedPin = approach.pins.first { $0.id == pinId }
                },
                onMapTap: placingPin ? { coord in
                    newPinCoord = coord
                } : nil,
                fitToCoordinatesOnLoad: pathCoords,
                polylines: [CumbrePolyline(
                    id: "approach",
                    coordinates: pathCoords,
                    color: UIColor(Cumbre.terra),
                    width: 4,
                    alpha: 1
                )]
            )
            .ignoresSafeArea(edges: .top)

            VStack(spacing: 8) {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(Cumbre.ink)
                            .padding(10)
                            .background(Circle().fill(Cumbre.paper))
                    }
                    Spacer()
                    if isAdmin {
                        Button {
                            confirmDelete = true
                        } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(Cumbre.bad)
                                .padding(10)
                                .background(Circle().fill(Cumbre.paper))
                        }
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
                }
                .padding(.horizontal, 12).padding(.top, 8)

                HStack(spacing: 6) {
                    Text(approach.name ?? "\(schoolName): aproximación")
                        .font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Text(approach.isVerified ? "✓ VERIFICADA" : "⚠ SIN VERIFICAR")
                        .font(Cumbre.mono(9, .bold)).tracking(1)
                        .foregroundStyle(approach.isVerified ? Color(hex: 0x3F6B4A) : Color(hex: 0xB45309))
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Cumbre.paper.opacity(0.96))
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .padding(.horizontal, 12)
            }

            VStack {
                Spacer()
                Text(placingPin
                     ? "Toca el mapa donde quieras dejar la chincheta."
                     : "Sigue la línea naranja. Si te alejas del camino, comprueba las chinchetas.")
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Cumbre.paper.opacity(0.96))
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
        .sheet(item: $selectedPin) { pin in
            ApproachPinDetailSheet(pin: pin)
                .presentationDetents([.medium])
        }
        .sheet(item: newPinCoordItem) { item in
            NewApproachPinSheet(approachId: approach.id, coord: item.coord) {
                placingPin = false
                newPinCoord = nil
                onPinAdded?()
            }
            .presentationDetents([.large])
        }
        .alert("¿Borrar «\(approach.name ?? "esta aproximación")»?", isPresented: $confirmDelete) {
            Button("CANCELAR", role: .cancel) {}
            Button("BORRAR", role: .destructive) {
                Task {
                    deleting = true
                    _ = try? await AppDependencies.shared.container.approachApi.deleteApproach(approachId: approach.id)
                    deleting = false
                    onPinAdded?()
                    dismiss()
                }
            }
        } message: {
            Text("Se borra el camino y todas sus chinchetas. No se puede deshacer.")
        }
    }

    // .sheet(item:) exige Identifiable — envolvemos la coordenada suelta.
    private var newPinCoordItem: Binding<CoordItem?> {
        Binding(
            get: { newPinCoord.map { CoordItem(coord: $0) } },
            set: { if $0 == nil { newPinCoord = nil } }
        )
    }

    private func colorForPinKind(_ kind: String) -> UIColor {
        switch kind {
        case "FORK": return UIColor(Cumbre.terra)
        case "HAZARD": return UIColor(Color(hex: 0xB45309))
        case "KEY": return UIColor(Color(hex: 0x8E3FBF))
        default: return UIColor(Cumbre.ink2) // LANDMARK
        }
    }
}

private struct CoordItem: Identifiable {
    let coord: CLLocationCoordinate2D
    var id: String { "\(coord.latitude),\(coord.longitude)" }
}

extension ApproachPin: Identifiable {}

private struct ApproachPinDetailSheet: View {
    let pin: ApproachPin

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let photo = pin.photoPath, !photo.isEmpty {
                TopoPhotoView(photoUrl: photo, lines: [])
                    .frame(height: 220)
                    .clipped()
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text(pin.kind)
                        .font(Cumbre.mono(10, .bold)).tracking(1)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Cumbre.terra)
                    if !pin.isVerifiedFlag {
                        Text("SIN VERIFICAR")
                            .font(Cumbre.mono(9, .bold)).tracking(1)
                            .foregroundStyle(Color(hex: 0xB45309))
                    }
                }
                if let msg = pin.message, !msg.isEmpty {
                    Text(msg).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                }
            }
            .padding(16)
            Spacer()
        }
    }
}

private extension ApproachPin {
    var isVerifiedFlag: Bool { status == "VERIFIED" }
}

/// Alta de una chincheta (foto y/o texto, nunca vacía — APPROACH_DESIGN.md
/// §2.3). Misma pantalla que verá cualquier usuario cuando se abra; hoy solo
/// la lanza un admin (ver ApproachFollowView).
private struct NewApproachPinSheet: View {
    let approachId: String
    let coord: CLLocationCoordinate2D
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var kind = "LANDMARK"
    @State private var message = ""
    @State private var image: UIImage?
    @State private var uploading = false
    @State private var errorMessage: String?

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
                                Button {
                                    kind = k
                                } label: {
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

                    if let err = errorMessage {
                        Text(err).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }

                    Button {
                        Task { await save() }
                    } label: {
                        if uploading {
                            ProgressView().tint(.white).frame(maxWidth: .infinity).padding(.vertical, 13)
                        } else {
                            Text("GUARDAR CHINCHETA").font(Cumbre.mono(11, .bold)).tracking(1)
                                .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13)
                        }
                    }
                    .background(canSave ? Cumbre.terra : Cumbre.ink3)
                    .disabled(!canSave || uploading)

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
                    Button("CERRAR") { dismiss() }
                }
            }
        }
    }

    private func save() async {
        uploading = true
        errorMessage = nil
        do {
            var photoPath: String?
            if let img = image {
                photoPath = try await StorageUploader.uploadApproachPinPhoto(img)
            }
            let req = AddApproachPinRequest(
                lat: coord.latitude, lon: coord.longitude, positionIdx: 0,
                kind: kind,
                message: message.trimmingCharacters(in: .whitespaces).isEmpty ? nil : message,
                photoPath: photoPath)
            _ = try await AppDependencies.shared.container.approachApi.addPin(approachId: approachId, req: req)
            uploading = false
            onSaved()
            dismiss()
        } catch {
            uploading = false
            errorMessage = "No se pudo guardar. Inténtalo de nuevo."
        }
    }
}
