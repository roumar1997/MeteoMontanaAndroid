import SwiftUI
import Shared

/// Última inclinación por board (shared: `BoardAngleMemory`): la primera vez Kilter 45°, MoonBoard 25°, Tension 25°.
enum BoardAngleStore {
    static let memory = BoardAngleMemory(store: UserDefaultsKeyValueStore(defaults: UserDefaults(suiteName: "cumbre_board_prefs") ?? .standard))

    static func remember(_ board: BoardKind, _ angle: Int?) {
        memory.remember(board: board, angle: angle.map { KotlinInt(int: Int32($0)) })
    }
}

/// «Solo board»: boards SIN rocódromo (tablero de casa o da igual dónde), con el mismo registro que «He entrenado».
/// Con [board] el tablero va fijado (el «+» de la pantalla Boards). Se guarda como sesión sin rocódromo
/// (`GymSession.NO_GYM`): primero en el móvil y se sube al volver la conexión. Espejo de `SoloBoardSheet.kt`.
struct SoloBoardSheet: View {
    let dateLabel: String
    let date: Kotlinx_datetimeLocalDate
    var board: BoardKind?
    var onSaved: () -> Void = {}
    @StateObject private var vm = GymTrainSheetViewModel()
    @Environment(\.dismiss) private var dismiss
    private var noGym: Int64 { GymSession.Companion.shared.NO_GYM }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(dateLabel.uppercased()).eyebrow(Cumbre.terra)
            Text(board.map { L("gym_solo_board_sheet", BoardStyle.name($0)) } ?? L("gym_solo_board_title"))
                .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(L("gym_solo_board_sub")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).padding(.bottom, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    GymTrainBoardsTab(ascents: vm.ascents, problems: vm.boardProblems, adjust: vm.adjust, addBoard: vm.addBoard,
                                      fixedBoard: board, suggestions: vm.suggestions, onQuery: vm.onNameChanged,
                                      onClear: vm.clearSuggestions)
                }
            }
            let total = vm.ascents.reduce(0) { $0 + Int($1.count) }
            HStack {
                Text(L("gym_sheet_total").uppercased()).eyebrow(Cumbre.ink2)
                Spacer()
                Text(LP("gym_sheet_total_value", total)).font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
            }
            .padding(.top, 8)
            let canSave = !vm.saving && (total > 0 || vm.hadAscents)
            Button {
                vm.save(gymId: noGym, date: date) { onSaved(); dismiss() }
            } label: {
                Text((total == 0 && vm.hadAscents ? L("gym_sheet_remove_workout")
                      : L("gym_solo_board_save") + (total > 0 ? " · \(total)" : "")).uppercased())
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.terraFill.opacity(canSave ? 1 : 0.4), in: Capsule())
            }
            .buttonStyle(.plain).disabled(!canSave).padding(.top, 8)
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 12)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear { vm.load(gymId: noGym, date: date) }
    }
}

/// Fila «Ángulos» (plegada por defecto) de un tablero: «Todos» y cada inclinación registrada con su cuenta.
struct BoardAngleFilter: View {
    let kind: BoardKind
    let list: [GymAscent]
    @Binding var selected: Int?
    @State private var open = false

    var body: some View {
        let counts: [(Int, Int)] = BoardAngles.shared.counts(items: list, board: kind).compactMap { p in
            guard let a = p.first, let n = p.second else { return nil }
            return (Int(truncating: a), Int(truncating: n))
        }
        if !counts.isEmpty {
            Button { open.toggle() } label: {
                HStack(spacing: 8) {
                    Text(L("gym_board_angles").uppercased()).eyebrow(Cumbre.ink2)
                    if let selected { Text("\(selected)°").font(.system(size: 12, weight: .bold)).foregroundStyle(Cumbre.terraFill) }
                    Spacer()
                    Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                }
                .padding(.vertical, 4).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                FlowLayoutView(spacing: 6) {
                    chip(L("gym_board_angles_all"), selected == nil) { selected = nil }
                    ForEach(counts, id: \.0) { a, n in
                        chip(L("gym_board_angle_chip", a, n), selected == a) { selected = selected == a ? nil : a }
                    }
                }
            }
        }
    }

    private func chip(_ text: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.system(size: 12, weight: .bold)).lineLimit(1).foregroundStyle(on ? .white : Cumbre.ink)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule()).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Tablero elegido para abrir su hoja.
struct BoardPick: Identifiable {
    let kind: BoardKind
    var id: String { kind.name }
}
