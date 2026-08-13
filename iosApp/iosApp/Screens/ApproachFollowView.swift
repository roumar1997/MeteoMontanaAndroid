import SwiftUI
import Shared
import CoreLocation

// Pantalla "SEGUIR" de una aproximación — Fase 1 de APPROACH_DESIGN.md §6.3.
// Sin navegación giro a giro: solo la línea y tu punto azul. Línea continua
// si está VERIFICADA, discontinua si no (mismo lenguaje que el resto de la
// app para "sin verificar" — franjas/dash en topos, aquí la aproximación).

struct ApproachFollowView: View {
    let approach: Approach
    let schoolName: String
    @Environment(\.dismiss) private var dismiss

    @State private var userCoord: CLLocationCoordinate2D?
    @State private var selectedPin: ApproachPin?

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
                Text("Sigue la línea naranja. Si te alejas del camino, comprueba las chinchetas.")
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
