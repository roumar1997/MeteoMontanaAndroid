import SwiftUI
import Shared

// Buscador de una escuela (vías, bloques, sectores y parkings), abierto desde la lupa junto al nombre (espejo de
// SchoolViaSearchButton.kt en Android). La búsqueda en sí vive en shared (`searchSchool`, SchoolSearch.kt).

/// Lupa redonda junto al nombre de la escuela: mismo estilo que «? AYUDA» (borde terra, fondo terra suave, icono terra).
struct SchoolViaSearchButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Cumbre.terra)
                .frame(width: 40, height: 40)
                .background(Cumbre.terraBg, in: Circle())
                .overlay(Circle().stroke(Cumbre.terra, lineWidth: 1.2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Buscar vías y bloques"))
    }
}

/// Hoja con el campo (teclado activo al abrir) y los resultados. Al elegir uno se cierra y `onPick` recibe el id y si es
/// un sitio (sector o parking: la ficha va al MAPA y enfoca su mini-tarjeta) o una vía/piedra (va al MAPA y la abre).
struct SchoolViaSearchSheet: View {
    let school: School
    let onPick: (_ id: String, _ isPlace: Bool) -> Void

    @StateObject private var vm = SchoolMapViewModel()
    @ObservedObject private var gradeSplits = GradeSplitsStore.shared
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss

    private var trimmed: String { vm.searchQuery.trimmingCharacters(in: .whitespaces) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
                TextField(L("Buscar bloques, vías, sectores, parkings…"), text: $vm.searchQuery)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16))
                    .focused($focused)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                if !vm.searchQuery.isEmpty {
                    Button { vm.searchQuery = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("Borrar"))
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))

            if trimmed.count >= 2 { results }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .task {
            _ = await vm.reloadBlocks(school: school, selectedId: nil)
        }
        .task {
            // La hoja ya está en pantalla: sin este respiro el foco se pierde y el teclado no sale.
            try? await Task.sleep(nanoseconds: 350_000_000)
            focused = true
        }
    }

    @ViewBuilder private var results: some View {
        let hits = SchoolSearchKt.searchSchool(blocks: vm.blocks, query: trimmed, limit: 10)
        if hits.isEmpty {
            Text(L("Sin resultados en esta escuela"))
                .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
        } else {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(hits.enumerated()), id: \.offset) { _, h in
                        Button {
                            dismiss()
                            let isPlace = h.kind == .sector || h.kind == .parking
                            onPick(isPlace ? h.blockId : (h.lineId ?? h.blockId), isPlace)
                        } label: { row(h) }
                        .buttonStyle(.plain)
                        Divider().background(Cumbre.rule)
                    }
                }
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }

    /// Etiqueta de tipo, la misma de los chips y marcadores: mosquetón, crashpad, círculo verde «S», cuadrado azul «P».
    @ViewBuilder private func kindBadge(_ k: SchoolSearchKind) -> some View {
        if k == .sector {
            Text("S").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                .frame(width: 22, height: 22)
                .background(Color(red: 0.29, green: 0.49, blue: 0.35), in: Circle())
        } else if k == .parking {
            Text("P").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                .frame(width: 22, height: 22)
                .background(Color(red: 0.10, green: 0.34, blue: 0.86), in: RoundedRectangle(cornerRadius: 5))
        } else {
            Image(k == .route ? "kind_route" : "kind_boulder")
                .renderingMode(.template).resizable().scaledToFit()
                .foregroundStyle(Cumbre.terra)
                .frame(width: 22, height: 22)
        }
    }

    private func subtitle(_ h: SchoolSearchHit) -> String {
        if h.kind == .sector { return L("Sector") }
        if h.kind == .parking { return L("Parking") }
        if let p = h.parentName { return p }
        return h.lineCount > 0 ? L("%@ ascensos", Int(h.lineCount)) : ""
    }

    private func gradeOf(_ h: SchoolSearchHit) -> String? {
        guard let id = h.lineId,
              let g = vm.blocks.first(where: { $0.id == h.blockId })?.lines.first(where: { $0.id == id })?.grade,
              !g.isEmpty else { return nil }
        return g
    }

    private func row(_ h: SchoolSearchHit) -> some View {
        HStack(spacing: 10) {
            kindBadge(h.kind)
            if let g = gradeOf(h) {
                // La altura parte la opinión: el grado va en dos colores.
                HStack(spacing: 6) {
                    Text(h.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    GradePill(grade: g, small: true, split: h.lineId.flatMap { gradeSplits.splits[$0] })
                }
            } else {
                Text(h.label).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
            }
            Spacer()
            Text(subtitle(h)).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
        }
        .padding(.horizontal, 12).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onAppear { gradeSplits.want(h.lineId) }
    }
}
