import SwiftUI
import Shared

/// Última inclinación por board (shared: `BoardAngleMemory`): la primera vez Kilter 45°, MoonBoard 25°, Tension 25°.
enum BoardAngleStore {
    static let memory = BoardAngleMemory(store: UserDefaultsKeyValueStore(defaults: UserDefaults(suiteName: "cumbre_board_prefs") ?? .standard))

    static func remember(_ board: BoardKind, _ angle: Int?) {
        memory.remember(board: board, angle: angle.map { KotlinInt(int: Int32($0)) })
    }
}

/// «Solo board»: boards SIN rocódromo (tablero de casa o da igual dónde). A altura completa, con «Cerrar» arriba y un único
/// «GUARDAR» que añade lo que haya en el formulario y lo guarda. Con [board] el tablero va fijado (el «+» de la pantalla Boards).
/// Se guarda como sesión sin rocódromo (`GymSession.NO_GYM`): primero en el móvil y se sube al volver la conexión.
/// Espejo de `SoloBoardSheet.kt`.
struct SoloBoardSheet: View {
    let dateLabel: String
    let date: Kotlinx_datetimeLocalDate
    var board: BoardKind?
    var onSaved: () -> Void = {}
    @StateObject private var vm = GymTrainSheetViewModel()
    @StateObject private var form = BoardForm()
    @Environment(\.dismiss) private var dismiss
    private var noGym: Int64 { GymSession.Companion.shared.NO_GYM }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(dateLabel.uppercased()).eyebrow(Cumbre.terra)
                    Text(board.map { L("gym_solo_board_sheet", BoardStyle.name($0)) } ?? L("gym_solo_board_title"))
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ClosePill { dismiss() }
            }
            Text(L("gym_solo_board_sub")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).padding(.bottom, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    GymTrainBoardsTab(ascents: vm.ascents, problems: vm.boardProblems, adjust: vm.adjust, addBoard: vm.addBoard,
                                      form: form, fixedBoard: board, suggestions: vm.suggestions, onQuery: vm.onNameChanged,
                                      onClear: vm.clearSuggestions, showAdded: false, saveCommits: true)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            saveButton
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 12)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear { vm.load(gymId: noGym, date: date) }
    }

    /// Guardar añade lo que haya en el formulario (si hay grado) y lo guarda todo; sin grado y a cero, quita el entreno.
    private var saveButton: some View {
        let total = vm.ascents.reduce(0) { $0 + Int($1.count) }
        let ready = form.grade != nil
        let canSave = !vm.saving && (ready || total > 0 || vm.hadAscents)
        return Button {
            form.commit(vm.addBoard, onClear: vm.clearSuggestions)
            vm.save(gymId: noGym, date: date) { onSaved(); dismiss() }
        } label: {
            Text((total == 0 && vm.hadAscents && !ready ? L("gym_sheet_remove_workout") : L("gym_board_save")).uppercased())
                .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(Cumbre.terraFill.opacity(canSave ? 1 : 0.4), in: Capsule())
        }
        .buttonStyle(.plain).disabled(!canSave).padding(.top, 8)
    }
}

/// Pastilla «Cerrar» (texto terracota con contorno) de las hojas de boards.
struct ClosePill: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(L("common_close")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.terraFill).lineLimit(1)
                .padding(.horizontal, 16).padding(.vertical, 8)
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Fila «Ángulo» siempre visible de un tablero con inclinaciones (variante B): etiqueta redonda y chips con scroll horizontal
/// («Todos» con «N bloques» debajo y cada ángulo con su cuenta). Elegir uno filtra grados, máximo y bloques. Espejo de `BoardAngleFilter.kt`.
struct BoardAngleFilter: View {
    let kind: BoardKind
    let list: [GymAscent]
    @Binding var selected: Int?

    var body: some View {
        let counts: [AngleCount] = BoardAngles.shared.counts(items: list, board: kind).compactMap { p in
            guard let a = p.first, let n = p.second else { return nil }
            return AngleCount(angle: Int(truncating: a), count: Int(truncating: n))
        }
        if !counts.isEmpty {
            let total = BoardAngles.shared.filter(items: list, board: kind, angle: nil).reduce(0) { $0 + Int($1.count) }
            HStack(spacing: 10) {
                Text(L("gym_board_angle_tag")).font(.system(size: 12, weight: .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Cumbre.rule.opacity(0.22), in: RoundedRectangle(cornerRadius: 12))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        chip(L("gym_board_angles_all"), LP("gym_board_blocks_n", total), selected == nil) { selected = nil }
                        ForEach(counts, id: \.angle) { c in
                            chip("\(c.angle)°", "\(c.count)", selected == c.angle) { selected = selected == c.angle ? nil : c.angle }
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func chip(_ top: String, _ bottom: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Text(top).font(.system(size: 12, weight: .bold)).lineLimit(1).foregroundStyle(on ? .white : Cumbre.ink)
                Text(bottom).font(.system(size: 10)).lineLimit(1).foregroundStyle(on ? Color.white.opacity(0.9) : Cumbre.ink2)
            }
            .padding(.horizontal, 12).padding(.vertical, 5)
            .background(on ? Cumbre.terraFill : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Un ángulo registrado y cuántos bloques lleva.
struct AngleCount {
    let angle: Int
    let count: Int
}

/// Tablero elegido para abrir su hoja.
struct BoardPick: Identifiable {
    let kind: BoardKind
    var id: String { kind.name }
}
