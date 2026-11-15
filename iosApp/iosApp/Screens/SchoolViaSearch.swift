import SwiftUI
import Shared

// Buscador de vías y bloques de UNA escuela, abierto desde la lupa junto al nombre (espejo de
// SchoolViaSearchButton.kt en Android). Antes era una barra fija encima del mapa.

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

/// Hoja con el campo (teclado activo al abrir) y los resultados. Al elegir uno se cierra y `onPick` recibe el id de la
/// vía (o el de la piedra si se eligió una piedra): la ficha cambia a MAPA y abre esa piedra.
struct SchoolViaSearchSheet: View {
    let school: School
    let onPick: (String) -> Void

    @StateObject private var vm = SchoolMapViewModel()
    @ObservedObject private var gradeSplits = GradeSplitsStore.shared
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss

    private var trimmed: String { vm.searchQuery.trimmingCharacters(in: .whitespaces) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
                TextField(L("Buscar vías/bloques…"), text: $vm.searchQuery)
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
        let hits = vm.searchHits(trimmed)
        if hits.isEmpty {
            Text(L("Sin resultados en esta escuela"))
                .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
        } else {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(hits, id: \.id) { h in
                        Button {
                            dismiss()
                            onPick(h.lineId ?? h.block.id)
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

    private func row(_ h: SchoolMapViewModel.SearchHit) -> some View {
        HStack {
            if let g = h.grade, let split = h.lineId.flatMap({ gradeSplits.splits[$0] }) {
                // La altura parte la opinión: el grado va en dos colores.
                HStack(spacing: 6) {
                    Text(h.name).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    GradePill(grade: g, small: true, split: split)
                }
            } else {
                Text(h.label).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineLimit(1)
            }
            Spacer()
            Text(h.sub).font(.system(size: 12)).foregroundStyle(Cumbre.ink3).lineLimit(1)
        }
        .padding(.horizontal, 12).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onAppear { gradeSplits.want(h.lineId) }
    }
}
