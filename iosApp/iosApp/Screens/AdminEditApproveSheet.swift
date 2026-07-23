import SwiftUI
import Shared
import CoreLocation

// EDITAR Y APROBAR: el admin retoca la propuesta y aprueba con sus cambios.
// Reparto de AdminView.swift.

enum AdminEditApprove {
    /// Caras (fotos) editables de la propuesta, en orden de aparición.
    /// Vacía = no editable (sin vías o sin ninguna foto).
    static func editableFaces(of c: Contribution) -> [String] {
        guard let json = c.bloquesJson, !json.isEmpty,
              let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
              !arr.isEmpty else { return [] }
        var photos: [String] = []
        for o in arr {
            if let p = (o["photoUrl"] as? String), !p.isEmpty, !photos.contains(p) {
                photos.append(p)
            }
        }
        if photos.isEmpty, let cover = c.photoUrl, !cover.isEmpty { return [cover] }
        return photos
    }

    /// bloquesJson → formularios editables, conservando targetLineId y cara
    /// para que el round-trip sea fiel (espejo de parseBloquesForms de Android).
    static func parseForms(_ json: String?) -> [BoulderBlockForm] {
        guard let json, let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.map { o in
            var f = BoulderBlockForm()
            f.name = (o["name"] as? String) ?? ""
            f.grade = (o["grade"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            f.startType = uiStart(o["startType"] as? String)
            f.line = TopoParse.points(o["linePath"] as? String)
            f.existingLineId = (o["targetLineId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            f.facePhoto = (o["photoUrl"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            f.descriptionText = (o["description"] as? String) ?? ""
            f.variant = (o["variant"] as? String) ?? ""
            return f
        }
    }

    private static func uiStart(_ raw: String?) -> String? {
        switch raw?.uppercased() {
        case "STAND", "PIE": return "PIE"
        case "SIT": return "SIT"
        case "SEMI": return "SEMI"
        case "JUMP", "LANCE": return "LANCE"
        case "TRAV": return "TRAV"
        default: return nil
        }
    }
}

/// Hoja "EDITAR Y APROBAR": campos editables por vía + editor de líneas sobre
/// la foto de la propuesta. Guardar = aprobar CON los cambios del admin.
struct AdminEditApproveSheet: View {
    let contribution: Contribution
    let onApprove: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var blocks: [BoulderBlockForm] = []
    @State private var showTopo = false
    @State private var faceIdx = 0

    private var faces: [String] { AdminEditApprove.editableFaces(of: contribution) }
    private var facePhoto: String? { faces.indices.contains(faceIdx) ? faces[faceIdx] : faces.first }
    /// Vías de la cara seleccionada, como binding que fusiona los cambios de
    /// vuelta en la lista completa (las demás caras no se tocan).
    private var faceBlocksBinding: Binding<[BoulderBlockForm]> {
        Binding(
            get: { blocks.filter { ($0.facePhoto ?? faces.first) == facePhoto } },
            set: { updated in
                let others = blocks.filter { ($0.facePhoto ?? faces.first) != facePhoto }
                blocks = others + updated
            }
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Retoca lo que haga falta (nombres, grados, variantes, líneas) y aprueba con tus cambios. El autor sigue siendo quien lo propuso.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                    ForEach(blocks.indices, id: \.self) { i in
                        BoulderBlockRow(block: $blocks[i], index: i,
                                        onDelete: blocks.count > 1 ? { blocks.remove(at: i) } : nil)
                    }
                    if !faces.isEmpty {
                        if faces.count > 1 {
                            // Varias caras: elegir cuál editar (las vías de cada
                            // cara se dibujan sobre SU foto).
                            HStack(spacing: 8) {
                                ForEach(faces.indices, id: \.self) { i in
                                    Button { faceIdx = i } label: {
                                        Text("FOTO \(i + 1)")
                                            .font(Cumbre.mono(10, .bold)).tracking(1.2)
                                            .foregroundStyle(faceIdx == i ? .white : Cumbre.ink2)
                                            .padding(.horizontal, 12).padding(.vertical, 7)
                                            .background(faceIdx == i ? Cumbre.terra : Color.clear)
                                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                                    }.buttonStyle(.plain)
                                }
                            }
                        }
                        Button { showTopo = true } label: {
                            Text("✎ EDITAR LÍNEAS SOBRE LA FOTO")
                                .font(Cumbre.mono(11, .bold)).tracking(0.8)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Cumbre.terra)
                        }.buttonStyle(.plain)
                    }
                    Button {
                        onApprove(buildBloquesJson(blocks))
                    } label: {
                        Text("APROBAR CON MIS CAMBIOS")
                            .font(Cumbre.mono(12, .bold)).tracking(0.8)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .background(Cumbre.ink)
                    }.buttonStyle(.plain)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Editar y aprobar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
            .onAppear {
                if blocks.isEmpty {
                    blocks = AdminEditApprove.parseForms(contribution.bloquesJson)
                }
            }
            .sheet(isPresented: $showTopo) {
                TopoEditorView(photoUrl: facePhoto, blocks: faceBlocksBinding)
            }
        }
    }
}
