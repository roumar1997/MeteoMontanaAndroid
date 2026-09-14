import SwiftUI
import Shared

/// Numerar piedras por sector: elegir sector (o "sin sector"), ver sus
/// piedras en el orden actual y arrastrar para corregirlo cuando una se
/// añadió fuera de orden respecto al camino real, o pedir un primer orden
/// automático por distancia al parking más cercano (Álvaro, 2026-09-14:
/// "que cada sector tenga sus piedras numeradas del 1 al infinito... y que
/// se pueda arreglar si el camino no va en línea recta").
struct SchoolSectorReorderSheet: View {
    let school: School
    @Environment(\.dismiss) private var dismiss
    @State private var blocks: [Block] = []
    @State private var selectedSectorId: String?   // nil = "sin sector"
    @State private var ordered: [Block] = []
    @State private var busy = false
    @State private var dirty = false
    @State private var errorMsg: String?
    @State private var loaded = false

    private var sectors: [Block] { blocks.filter { $0.type == "ZONE" } }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                sectorPicker
                Divider().overlay(Cumbre.rule)
                if !loaded {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                    Spacer()
                } else if ordered.isEmpty {
                    Text("Este sector todavía no tiene piedras.")
                        .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink3)
                        .padding(.top, 40)
                    Spacer()
                } else {
                    List {
                        ForEach(Array(ordered.enumerated()), id: \.element.id) { idx, b in
                            HStack(spacing: 10) {
                                Text("\(idx + 1)").font(Cumbre.mono(13, .bold)).foregroundStyle(Cumbre.terra)
                                    .frame(width: 28, alignment: .leading)
                                Text(b.name.isEmpty ? "(sin número)" : b.name)
                                    .font(Cumbre.serif(15, .semibold)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: "line.3.horizontal").foregroundStyle(Cumbre.ink3)
                            }
                        }
                        .onMove { from, to in
                            ordered.move(fromOffsets: from, toOffset: to)
                            dirty = true
                        }
                    }
                    .environment(\.editMode, .constant(EditMode.active))
                    .listStyle(.plain)
                }
                if let errorMsg {
                    Text(errorMsg).font(Cumbre.mono(11)).foregroundStyle(Cumbre.bad)
                        .padding(.horizontal, 16).padding(.top, 6)
                }
                actions
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Ordenar piedras")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
        .task { await reload() }
    }

    private var sectorPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(title: "SIN SECTOR", isSelected: selectedSectorId == nil) {
                    selectedSectorId = nil; recomputeOrdered()
                }
                ForEach(sectors, id: \.id) { z in
                    chip(title: z.name.isEmpty ? "SECTOR" : z.name.uppercased(), isSelected: selectedSectorId == z.id) {
                        selectedSectorId = z.id; recomputeOrdered()
                    }
                }
            }.padding(12)
        }
    }

    private func chip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(Cumbre.mono(11, .bold)).tracking(0.5)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .foregroundStyle(isSelected ? .white : Cumbre.ink)
                .background(isSelected ? Cumbre.terraFill : Color.clear)
                .overlay(Rectangle().stroke(isSelected ? Color.clear : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private var actions: some View {
        VStack(spacing: 8) {
            Button { Task { await autoOrder() } } label: {
                HStack { if busy { ProgressView().tint(Cumbre.ink) }
                    Text("AUTO-ORDENAR POR GPS").font(Cumbre.mono(12, .bold)).tracking(0.6) }
                    .foregroundStyle(Cumbre.ink).frame(maxWidth: .infinity).padding(.vertical, 13)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }.buttonStyle(.plain).disabled(busy || ordered.isEmpty)
            Button { Task { await saveOrder() } } label: {
                HStack { if busy { ProgressView().tint(.white) }
                    Text("GUARDAR ORDEN").font(Cumbre.mono(12, .bold)).tracking(0.6) }
                    .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13)
                    .background(dirty ? Cumbre.terraFill : Cumbre.ink3)
            }.buttonStyle(.plain).disabled(busy || !dirty)
        }.padding(16)
    }

    private func reload() async {
        blocks = (try? await AppDependencies.shared.container.getBlocks.invoke(schoolId: school.id)) ?? []
        loaded = true
        recomputeOrdered()
    }

    /// Piedras del sector elegido, en su número actual — el punto de partida
    /// antes de arrastrar o pedir el auto-orden.
    private func recomputeOrdered() {
        ordered = blocks
            .filter { $0.type == "BLOCK" && $0.sectorBlockId == selectedSectorId }
            .sorted { (Int($0.name) ?? Int.max) < (Int($1.name) ?? Int.max) }
        dirty = false
        errorMsg = nil
    }

    private func saveOrder() async {
        busy = true; errorMsg = nil
        let ids = ordered.map { $0.id }
        do {
            let result = try await AppDependencies.shared.container.reorderBlocks
                .invoke(schoolId: school.id, sectorBlockId: selectedSectorId, orderedBlockIds: ids)
            applyResult(result)
        } catch {
            errorMsg = "No se pudo guardar: \(error.localizedDescription)"
        }
        busy = false
    }

    private func autoOrder() async {
        busy = true; errorMsg = nil
        do {
            let result = try await AppDependencies.shared.container.autoReorderBlocks
                .invoke(schoolId: school.id, sectorBlockId: selectedSectorId)
            applyResult(result)
        } catch {
            errorMsg = "No se pudo calcular el orden: \(error.localizedDescription)"
        }
        busy = false
    }

    /// Sustituye en `blocks` las piedras devueltas (con su número nuevo) y
    /// recalcula la vista — así se ve el resultado sin recargar toda la escuela.
    private func applyResult(_ updated: [Block]) {
        let byId = Dictionary(uniqueKeysWithValues: updated.map { ($0.id, $0) })
        blocks = blocks.map { byId[$0.id] ?? $0 }
        recomputeOrdered()
    }
}
