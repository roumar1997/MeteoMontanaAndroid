import SwiftUI
import Shared
import CoreLocation

// Pestana GESTIONAR: escuelas -> piedras -> ficha de edicion. Reparto de AdminView.swift.

struct GestionarTab: View {
    @ObservedObject var vm: AdminViewModel
    @State private var query = ""
    @State private var selected: School?

    private var filtered: [School] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return Array(vm.allSchools.prefix(60)) }
        return vm.allSchools.filter {
            $0.name.lowercased().contains(q)
            || ($0.location?.lowercased().contains(q) ?? false)
            || ($0.region?.lowercased().contains(q) ?? false)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TextField("Buscar escuela (nombre, lugar, región)…", text: $query)
                .font(.system(size: 15)).padding(10)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1)).padding(16)
            if vm.allSchools.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.top, 30); Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered, id: \.id) { s in
                            Button { selected = s } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.name).font(Cumbre.serif(15, .semibold)).foregroundStyle(Cumbre.ink)
                                        let sub = [s.location, s.region].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                                        if !sub.isEmpty { Text(sub).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3) }
                                    }
                                    Spacer()
                                    Text("▸").foregroundStyle(Cumbre.terra)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 10).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .fullScreenCover(item: $selected) { s in
            NavigationStack {
                SchoolDetailView(school: s)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("CERRAR") { selected = nil }
                                .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
                        }
                    }
            }
        }
    }
}

/// Mapa a pantalla completa con todos los bloques de una escuela; tocar uno abre
/// la gestión (editar/borrar). Espejo del FullScreenMapDialog admin.
struct SchoolBlocksManageSheet: View {
    let school: School
    @Environment(\.dismiss) private var dismiss
    @State private var blocks: [Block] = []
    @State private var style: MapStyleKind = .topo
    @State private var manage: Block?
    @State private var moving: Block?   // bloque en modo "mover pulsando en el mapa"

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                MapLibreView(
                    center: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                    zoom: 15, markers: markers, style: style,
                    // En modo mover, el tap del mapa fija la nueva posición; si no,
                    // tocar un marcador abre su gestión.
                    onTapMarker: { id in if moving == nil { manage = blocks.first { $0.id == id } } },
                    onMapTap: moving == nil ? nil : { coord in Task { await move(to: coord) } })
                MapStyleChips(selection: $style)
            }
            .overlay(alignment: .top) { if let m = moving { moveBanner(m) } }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(school.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra) } }
        }
        .task { await reload() }
        .sheet(item: $manage) { b in
            BlockManageSheet(block: b,
                             onMove: { moved in moving = moved; manage = nil },
                             onDone: { Task { await reload() }; manage = nil })
        }
    }

    private func moveBanner(_ b: Block) -> some View {
        HStack(spacing: 10) {
            Text("PULSA LA NUEVA POSICIÓN DE «\((b.name.isEmpty ? blockTypeLabel(b.type) : b.name).uppercased())»")
                .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button { moving = nil } label: {
                Text("CANCELAR").font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity).background(Cumbre.terra)
    }

    /// Mueve el bloque a la coord pulsada, preservando type/foto/sector y las VÍAS
    /// (mismo cuidado que BlockManageSheet.save: si no se mandan, se borrarían).
    private func move(to coord: CLLocationCoordinate2D) async {
        guard let b = moving else { return }
        let lines = b.lines.map {
            CreateBlockLineRequest(name: $0.name, grade: $0.grade, startType: $0.startType, linePath: $0.linePath, photoPath: $0.photoPath, faceOrder: $0.faceOrder, description: $0.lineDescription, variant: $0.variant)
        }
        let req = CreateBlockRequest(type: b.type, name: b.name,
                                     lat: coord.latitude, lon: coord.longitude,
                                     photoPath: b.photoPath, description: b.descriptionText,
                                     lines: lines, sectorBlockId: b.sectorBlockId,
                                     discipline: b.discipline,
                                     geometry: b.geometry, path: b.path, direction: b.direction)
        _ = try? await AppDependencies.shared.container.updateBlock.invoke(blockId: b.id, req: req)
        moving = nil
        await reload()
    }

    private func reload() async {
        blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
    }

    private var markers: [CumbreMarker] {
        blocks.map { b in
            CumbreMarker(id: b.id, coordinate: CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon),
                         title: b.name.isEmpty ? b.type : b.name, kind: kind(b.type),
                         color: color(b.type), name: b.name)
        }
    }
    private func kind(_ t: String) -> MarkerKind {
        switch t.uppercased() { case "PARKING": return .parking; case "ZONE": return .zone; default: return .block }
    }
    private func color(_ t: String) -> UIColor {
        switch t.uppercased() {
        case "PARKING": return UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
        case "ZONE": return UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
        default: return UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
        }
    }
}

/// Etiqueta corta del tipo de un bloque (PARKING / ZONA / PIEDRA).
func blockTypeLabel(_ t: String) -> String {
    switch t.uppercased() { case "PARKING": return "PARKING"; case "ZONE": return "ZONA"; default: return "PIEDRA" }
}

/// Gestión de un bloque (admin): editar nombre + descripción + coordenadas
/// (preservando las vías) o borrarlo. Espejo de EditBlockDialog/BlockDetailDialog.
struct BlockManageSheet: View {
    let block: Block
    let onMove: (Block) -> Void
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var desc: String
    @State private var latText: String
    @State private var lonText: String
    @State private var discipline: String
    @State private var busy = false
    @State private var confirmDelete = false

    init(block: Block, onMove: @escaping (Block) -> Void, onDone: @escaping () -> Void) {
        self.block = block; self.onMove = onMove; self.onDone = onDone
        _name = State(initialValue: block.name)
        // `descriptionText` (alias Kotlin) evita el choque con NSObject.description.
        _desc = State(initialValue: block.descriptionText ?? "")
        _latText = State(initialValue: String(format: "%.6f", block.lat))
        _lonText = State(initialValue: String(format: "%.6f", block.lon))
        _discipline = State(initialValue: block.discipline)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(typeLabel).font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    field("NOMBRE", $name, "Nombre")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("DESCRIPCIÓN").eyebrow()
                        TextField("Descripción (opcional)", text: $desc, axis: .vertical)
                            .lineLimit(2...5).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    if block.type == "BLOCK" {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("MODALIDAD").eyebrow()
                            DisciplineSelector(selected: $discipline)
                        }
                    }
                    field("LATITUD", $latText, "lat")
                    field("LONGITUD", $lonText, "lon")
                    Button { Task { await save() } } label: {
                        HStack { if busy { ProgressView().tint(.white) }
                            Text("GUARDAR CAMBIOS").font(Cumbre.mono(12, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(busy)
                    Button { onMove(block) } label: {
                        Text("📍 MOVER PULSANDO EN EL MAPA").font(Cumbre.mono(12, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.ink)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(busy)
                    Button { confirmDelete = true } label: {
                        Text("BORRAR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.bad)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .overlay(Rectangle().stroke(Cumbre.bad, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(busy)
                }.padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(block.name.isEmpty ? typeLabel : block.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
            .alert("¿Borrar este bloque?", isPresented: $confirmDelete) {
                Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
                Button(NSLocalizedString("common_delete", comment: ""), role: .destructive) { Task { await remove() } }
            } message: { Text("Se borrará «\(block.name)» y sus vías. No se puede deshacer.") }
        }
    }

    private var typeLabel: String { blockTypeLabel(block.type) }

    private func save() async {
        busy = true
        let lat = Double(latText) ?? block.lat
        let lon = Double(lonText) ?? block.lon
        // Preservamos type/foto/sector y las VÍAS (si no, se borrarían).
        let lines = block.lines.map {
            CreateBlockLineRequest(name: $0.name, grade: $0.grade, startType: $0.startType, linePath: $0.linePath, photoPath: $0.photoPath, faceOrder: $0.faceOrder, description: $0.lineDescription, variant: $0.variant)
        }
        let trimmed = desc.trimmingCharacters(in: .whitespacesAndNewlines)
        let req = CreateBlockRequest(type: block.type, name: name, lat: lat, lon: lon,
                                     photoPath: block.photoPath,
                                     description: trimmed.isEmpty ? nil : trimmed,
                                     lines: lines, sectorBlockId: block.sectorBlockId,
                                     discipline: block.type == "BLOCK" ? discipline : nil,
                                     geometry: block.geometry, path: block.path, direction: block.direction)
        _ = try? await AppDependencies.shared.container.updateBlock.invoke(blockId: block.id, req: req)
        busy = false; dismiss(); onDone()
    }

    private func remove() async {
        busy = true
        _ = try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: block.id)
        busy = false; dismiss(); onDone()
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}
