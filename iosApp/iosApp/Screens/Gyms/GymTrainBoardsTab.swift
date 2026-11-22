import SwiftUI
import Shared

/// Lo que hay en el formulario de boards (tablero, inclinación, grado, nombre, repeticiones e intentos). Vive fuera de la pestaña
/// para que el botón «Guardar» de «Solo board» añada lo que haya escrito (espejo de `BoardCommit` de Android).
@MainActor
final class BoardForm: ObservableObject {
    @Published var board: BoardKind = .kilter
    @Published var angle: Int?
    @Published var selected: [String] = []
    @Published var name = ""
    @Published var reps = 1
    /// Nada preseleccionado: sin tocar no se apunta ningún intento ni flash.
    @Published var attempts: Int?
    private var started = false

    var grade: String? { GymAscentKeys.shared.gradeOf(selected: selected) }

    func start(fixed: BoardKind?) {
        guard !started else { return }
        started = true
        if let fixed { board = fixed }
        angle = BoardAngleStore.memory.initial(board: board)?.intValue
    }

    /// Block being edited: the form starts with its values.
    func prefill(_ e: BoardEntry) {
        started = true
        board = e.board
        angle = e.angle?.intValue
        selected = e.gridGrades
        name = e.name ?? ""
        reps = min(max(Int(e.reps), 1), Int(BoardProblemRepeats.shared.MAX_REPS))
        attempts = e.attempts?.intValue
    }

    /// Añade lo del formulario (si hay grado) y lo deja limpio. Devuelve si añadió algo.
    @discardableResult
    func commit(_ addBoard: (BoardKind, String, Int?, String, Int, Int?) -> Void, onClear: () -> Void) -> Bool {
        guard let grade else { return false }
        addBoard(board, grade, angle, name, reps, attempts)
        BoardAngleStore.remember(board, angle)
        selected = []
        name = ""
        reps = 1
        attempts = nil
        onClear()
        return true
    }
}

/// Pestaña «Boards»: el board, el nombre opcional con sugerencias, la inclinación, el grado, los intentos y las repeticiones.
/// Con [saveCommits] no hay «Añadir»: el «Guardar» de la hoja añade lo que haya (registro de boards).
struct GymTrainBoardsTab: View {
    let ascents: [GymAscent]
    let problems: [BoardProblem]
    let adjust: (String, Int) -> Void
    let addBoard: (BoardKind, String, Int?, String, Int, Int?) -> Void
    @ObservedObject var form: BoardForm
    /// Tablero fijado (el «+» de la pantalla Boards): sin selector de marca.
    var fixedBoard: BoardKind?
    var suggestions: [BoardProblemSuggestion] = []
    var onQuery: (BoardKind, String) -> Void = { _, _ in }
    var onClear: () -> Void = {}
    /// «Añadido hoy · N» (solo en «He entrenado»).
    var showAdded = true
    var saveCommits = false

    var body: some View {
        let items = ascents.filter { GymAscentKeys.shared.parse(key: $0.key) is AscentKeyBoard }
        if showAdded { GymAddedList(list: items, names: BoardProblems.shared.namesByKey(items: items, problems: problems), adjust: adjust) }
        if fixedBoard == nil { boardPicker }
        nameField
        if !form.name.trimmingCharacters(in: .whitespaces).isEmpty { BoardNameSuggestions(suggestions: suggestions, onPick: pick) }
        let angles = BoardAngles.shared.anglesFor(board: form.board).map { Int(truncating: $0) }
        if !angles.isEmpty { GymEyebrow(text: L("gym_sheet_incline")) }
        FlowLayoutView(spacing: 6) {
            ForEach(angles, id: \.self) { a in
                let sel = form.angle == a
                Button { form.angle = sel ? nil : a; BoardAngleStore.remember(form.board, form.angle) } label: {
                    Text("\(a)°").font(.system(size: 12, weight: .bold)).foregroundStyle(sel ? .white : Cumbre.ink)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(sel ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        GymEyebrow(text: L("gym_sheet_grade_pick"))
        GymGradeGrid(selected: form.selected) { form.selected = GymAscentKeys.shared.toggleGrade(selected: form.selected, tapped: $0) }
        AttemptsCard(selected: $form.attempts)
        BoardRepsStepper(reps: $form.reps)
        if !saveCommits {
            GymAddButton(grade: form.grade) { form.commit(addBoard, onClear: onClear) }
        }
        Text(L("gym_sheet_board_note")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            .onAppear { form.start(fixed: fixedBoard) }
    }

    private var boardPicker: some View {
        HStack(spacing: 8) {
            ForEach(BoardStyle.all, id: \.self) { b in
                let sel = b == form.board
                Button {
                    form.board = b
                    form.angle = BoardAngleStore.memory.initial(board: b)?.intValue
                    if form.name.trimmingCharacters(in: .whitespaces).isEmpty { onClear() } else { onQuery(b, form.name) }
                } label: {
                    VStack(spacing: 5) {
                        BoardLogo(board: b)
                        Text(BoardStyle.name(b)).font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(sel ? BoardStyle.color(b).opacity(0.16) : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(sel ? BoardStyle.color(b) : Cumbre.rule, lineWidth: sel ? 2 : 1))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var nameField: some View {
        VStack(alignment: .leading, spacing: 3) {
            TextField(L("gym_board_name_hint"), text: $form.name)
                .font(.system(size: 15)).padding(12)
                .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: form.name) { _, new in
                    let max = Int(BoardProblems.shared.MAX_NAME)
                    if new.count > max { form.name = String(new.prefix(max)) }
                    onQuery(form.board, form.name)
                }
            Text("\(form.name.count)/\(BoardProblems.shared.MAX_NAME)").font(.system(size: 11)).foregroundStyle(Cumbre.ink2).padding(.leading, 12)
        }
    }

    /// Elegir una sugerencia rellena nombre, grado y, si se sabe, inclinación.
    private func pick(_ s: BoardProblemSuggestion) {
        form.name = s.name
        form.selected = Array(s.grade.split(separator: "/").map(String.init).filter { GymAscentKeys.shared.GRID_GRADES.contains($0) }.prefix(2))
        if let a = s.angle?.intValue, BoardAngles.shared.anglesFor(board: form.board).contains(where: { $0.intValue == a }) { form.angle = a }
        onClear()
    }
}
