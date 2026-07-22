import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Hojas auxiliares del flujo de proponer: asignar sector + trazar muro.
// Reparto de ProposeFlow.swift.

struct AssignSectorSheet: View {
    let block: Block
    let schoolId: String
    let sectors: [Block]
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var sending = false
    @State private var sendError: String? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    let options = sectors.filter { $0.id != block.sectorBlockId }
                    Text(block.sectorBlockId == nil
                         ? "Elige el sector (zona) al que pertenece «\(block.name)». Un admin lo revisará."
                         : "Elige el nuevo sector (zona) de «\(block.name)». Un admin lo revisará.")
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    if options.isEmpty {
                        Text("Esta escuela solo tiene este sector. Crea otro con «+ PROPONER → SECTOR» para poder cambiarlo.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    }
                    ForEach(options, id: \.id) { s in
                        Button { Task { await assign(s.id) } } label: {
                            HStack {
                                Text(s.name.isEmpty ? "Zona" : s.name).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            .padding(12).frame(maxWidth: .infinity)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain).disabled(sending)
                    }
                    if let sendError {
                        Text(sendError + " Toca un sector para reintentar.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                    }
                    if sending { ProgressView().frame(maxWidth: .infinity) }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Asignar sector")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
        }
    }

    private func assign(_ sectorId: String) async {
        sending = true
        let req = ContributionRequest(
            type: "ASSIGN_SECTOR", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: sectorId,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok { dismiss(); onDone(true) }
        else { sendError = "No se pudo enviar. Revisa la conexión." }
    }
}

/// Editor UNIFICADO de vías ("✎ EDITAR / AÑADIR VÍAS"): muestra TODAS las vías de
/// la piedra (existentes precargadas + las nuevas que añadas). Tocas cualquiera
/// para cambiar nombre/grado/tipo o redibujarla sobre la foto, y al enviar manda
/// una corrección por cada vía existente modificada y una propuesta con las nuevas.

/// Sheet para trazar/re-trazar el muro en su propio mapa (encima del editor, así
/// no se pierde lo editado). Cada tap añade un punto; DESHACER/LISTO. Devuelve la
/// polilínea por `onDone`. Espejo del modo "traza el muro" de Android.
struct WallTraceSheet: View {
    let center: CLLocationCoordinate2D
    let initial: [CLLocationCoordinate2D]
    /// Marcadores de contexto (otras piedras/sectores/parkings + mi ubicación)
    /// para ubicarte mientras trazas — opcional (los callers pasan lo que tengan).
    /// Declarado ANTES de `onDone` para que el closure final del caller enlace
    /// sin ambigüedad con `onDone` (último parámetro de tipo función).
    var contextMarkers: [CumbreMarker] = []
    let onDone: ([CLLocationCoordinate2D]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var points: [CLLocationCoordinate2D] = []
    @State private var started = false
    // El usuario elige satélite/topo (antes estaba fijo). Satélite por defecto:
    // ver la base real del muro es lo más útil, igual que Android.
    @State private var style: MapStyleKind = .satellite

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                MapLibreView(
                    // Cada punto tocado se pinta como círculo NUMERADO (antes no
                    // se veían: solo salía la línea con ≥2 puntos y el primer toque
                    // parecía no registrarse). Debajo, el contexto (otras piedras).
                    center: center, zoom: 17,
                    markers: contextMarkers + points.enumerated().map { i, p in
                        CumbreMarker(id: "pt\(i)", coordinate: p, title: "\(i + 1)",
                                     kind: .cluster, color: UIColor(Cumbre.terra), score: i + 1)
                    },
                    style: style,
                    onMapTap: { points.append($0) },
                    polylines: points.count >= 2
                        ? [CumbrePolyline(id: "trace", coordinates: points, color: UIColor(Cumbre.terra), width: 5)]
                        : []
                )
                .ignoresSafeArea(edges: .bottom)

                VStack(spacing: 8) {
                    Text("✎ TRAZA EL MURO · \(points.count) PUNTOS · TOCA LA BASE DEL MURO")
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10).background(Cumbre.terra)
                    // Toggle satélite / topográfico (antes estaba fijo).
                    HStack(spacing: 8) {
                        ForEach([("satellite", "SATÉLITE"), ("topo", "TOPO")], id: \.0) { value, label in
                            let on = style.rawValue == value
                            Button { style = MapStyleKind(rawValue: value) ?? .satellite } label: {
                                Text(label).font(Cumbre.mono(11, .bold))
                                    .foregroundStyle(on ? .white : Cumbre.ink)
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                                    .background(on ? Cumbre.terra : .white)
                                    .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 10)
                    HStack(spacing: 10) {
                        Button { if !points.isEmpty { points.removeLast() } } label: {
                            Text("↶ DESHACER").font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                .frame(maxWidth: .infinity).padding(.vertical, 10).background(.white)
                        }.buttonStyle(.plain).opacity(points.isEmpty ? 0.4 : 1).disabled(points.isEmpty)
                        Button { onDone(points); dismiss() } label: {
                            Text("✓ LISTO").font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 10).background(Cumbre.terra)
                        }.buttonStyle(.plain).opacity(points.count < 2 ? 0.4 : 1).disabled(points.count < 2)
                    }
                    .padding(.horizontal, 10)
                }
                .padding(.top, 8)
            }
            .navigationTitle("Trazar muro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_cancel", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
        .onAppear { if !started { started = true; points = initial } }
    }
}

/// Selector segmentado genérico (dos+ opciones) — geometría PUNTO/MURO y sentido.
struct WallSeg: View {
    let options: [(String, String)]
    @Binding var selected: String
    var body: some View {
        HStack(spacing: 8) {
            ForEach(options, id: \.0) { value, label in
                let on = selected == value
                Button { selected = value } label: {
                    Text(label).font(Cumbre.mono(12, .bold)).tracking(0.6)
                        .foregroundStyle(on ? .white : Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(on ? Cumbre.terra : Color.clear)
                        .overlay(Rectangle().stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
    }
}
