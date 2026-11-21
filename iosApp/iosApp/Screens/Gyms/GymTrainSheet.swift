import SwiftUI
import Shared

/// Hoja «He entrenado aquí hoy»: lo hecho (bloques, vías, boards) y se guarda con la sesión de ese día.
/// Espejo de `GymTrainSheetViewModel.kt`; las cuentas y las claves salen de shared (`GymTraining`, `GymAscentKeys`).
@MainActor
final class GymTrainSheetViewModel: ObservableObject {
    @Published var ascents: [GymAscent] = []
    @Published var boardProblems: [BoardProblem] = []
    @Published var saving = false
    /// Ya había algo apuntado al abrir la hoja: dejarla a cero BORRA el entreno.
    @Published var hadAscents = false
    /// Sugerencias del nombre del bloque mientras se escribe (lo propio y el catálogo compartido).
    @Published var suggestions: [BoardProblemSuggestion] = []
    private var suggestTask: Task<Void, Never>?
    private let c = AppDependencies.shared.container.gyms

    func load(gymId: Int64, date: Kotlinx_datetimeLocalDate?) {
        let today = c.gymTraining.today()
        let day = date ?? today
        // session() y no summary(): summary excluye las sesiones sin rocódromo (gymId 0) y el segundo guardado pisaría el primero.
        _ = today
        let existing = c.gymTraining.session(gymId: gymId, date: day)
        ascents = existing?.ascents ?? []
        boardProblems = existing?.boardProblems ?? []
        hadAscents = !(existing?.ascents.isEmpty ?? true)
    }

    func adjust(_ key: String, _ delta: Int) {
        let next = GymTraining.shared.adjust(ascents: ascents, key: key, delta: Int32(delta))
        ascents = next
        boardProblems = BoardProblems.shared.prune(problems: boardProblems, ascents: next)
    }

    /// Apunta un board y, si lleva nombre (opcional, se recorta), también el nombre del bloque.
    func addBoard(_ board: BoardKind, _ grade: String, _ angle: Int?, _ name: String, _ reps: Int) {
        let n = BoardProblemRepeats.shared.clampReps(n: Int32(reps))
        let key = GymAscentKeys.shared.board(board: board, grade: grade, angle: angle.map { KotlinInt(int: Int32($0)) })
        ascents = GymTraining.shared.adjust(ascents: ascents, key: key, delta: n)
        boardProblems = BoardProblemRepeats.shared.addRepeated(problems: boardProblems, board: board, grade: grade, name: name, reps: n)
        suggestions = []
    }

    /// Al escribir el nombre: al instante lo propio y, tras 250 ms, también el catálogo de todas las personas (desde 2 letras).
    func onNameChanged(_ board: BoardKind, _ text: String) {
        suggestTask?.cancel()
        guard BoardProblemRepeats.shared.nameKey(name: text).count >= Int(BoardProblemRepeats.shared.SUGGEST_MIN_CHARS) else {
            suggestions = []
            return
        }
        suggestions = c.searchBoardProblems.local(board: board, query: text)
        suggestTask = Task {
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            if let found = try? await c.searchBoardProblems.search(board: board, query: text), !Task.isCancelled { suggestions = found }
        }
    }

    func clearSuggestions() {
        suggestTask?.cancel()
        suggestions = []
    }

    /// Apunta la sesión (hoy también en el servidor) y guarda lo hecho. Funciona sin red: primero en el móvil.
    func save(gymId: Int64, date: Kotlinx_datetimeLocalDate?, onDone: @escaping () -> Void) {
        guard !saving else { return }
        saving = true
        Task {
            let today = c.gymTraining.today()
            let day = date ?? today
            if gymId != GymSession.Companion.shared.NO_GYM && !ascents.isEmpty && day == today {
                _ = try? await c.recordGymSession.execute(gymId: gymId, source: .manual)
            }
            if !ascents.isEmpty || hadAscents {
                _ = try? await c.gymTraining.saveAscents(gymId: gymId, date: day, ascents: ascents, boardProblems: boardProblems)
            }
            saving = false
            onDone()
        }
    }
}

private enum TrainTab { case blocks, routes, boards }

/// Hoja «He entrenado aquí hoy» (maqueta 2): pestañas Bloques | Vías | Boards y «Guardar entreno» fijo abajo.
struct GymTrainSheet: View {
    let gym: Gym
    let dateLabel: String
    var date: Kotlinx_datetimeLocalDate?
    var onSaved: () -> Void = {}
    @StateObject private var vm = GymTrainSheetViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var tab: TrainTab = .blocks

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(dateLabel.uppercased()).eyebrow(Cumbre.terra)
            Text(L("gym_sheet_title", gym.name)).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink).padding(.bottom, 8)
            GymSegmented(options: [L("gym_sheet_tab_blocks"), L("gym_sheet_tab_routes"), L("gym_sheet_tab_boards")],
                         selected: tab == .blocks ? 0 : (tab == .routes ? 1 : 2)) { i in
                tab = i == 0 ? .blocks : (i == 1 ? .routes : .boards)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    switch tab {
                    case .blocks: GymTrainBlocksTab(gym: gym, ascents: vm.ascents, adjust: vm.adjust)
                    case .routes: GymTrainRoutesTab(ascents: vm.ascents, adjust: vm.adjust)
                    case .boards: GymTrainBoardsTab(ascents: vm.ascents, problems: vm.boardProblems, adjust: vm.adjust,
                                                    addBoard: vm.addBoard, suggestions: vm.suggestions,
                                                    onQuery: vm.onNameChanged, onClear: vm.clearSuggestions)
                    }
                }
                .padding(.top, 12)
            }
            let total = vm.ascents.reduce(0) { $0 + Int($1.count) }
            HStack {
                Text(L("gym_sheet_total").uppercased()).eyebrow(Cumbre.ink2)
                Spacer()
                Text(LP("gym_sheet_total_value", total)).font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
            }
            .padding(.top, 8)
            let enabled = total > 0 || vm.hadAscents
            Button {
                vm.save(gymId: gym.id, date: date) { onSaved(); dismiss() }
            } label: {
                Text((total == 0 && vm.hadAscents ? L("gym_sheet_remove_workout")
                      : L("gym_sheet_save") + (total > 0 ? " · \(total)" : "")).uppercased())
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.terraFill.opacity(enabled ? 1 : 0.4), in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(!enabled || vm.saving)
            .padding(.top, 8)
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 12)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear { vm.load(gymId: gym.id, date: date) }
    }
}

/// Segmento en píldora gris con la opción elegida en blanco (pestañas de las hojas y Encadenada | Proyecto).
struct GymSegmented: View {
    let options: [String]
    let selected: Int
    let onSelect: (Int) -> Void
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { i, text in
                Button { onSelect(i) } label: {
                    Text(text.uppercased()).font(Cumbre.mono(11, .bold)).tracking(0.8).lineLimit(1).minimumScaleFactor(0.8)
                        .foregroundStyle(i == selected ? Cumbre.ink : Cumbre.ink2)
                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                        .background(i == selected ? Cumbre.bg : Color.clear, in: Capsule())
                        .shadow(color: .black.opacity(i == selected ? 0.08 : 0), radius: 2, y: 1)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.45), in: Capsule())
    }
}

/// Pestaña «Bloques»: los colores de la escala del rocódromo (con su rango) o los grados con su color.
private struct GymTrainBlocksTab: View {
    let gym: Gym
    let ascents: [GymAscent]
    let adjust: (String, Int) -> Void

    var body: some View {
        let scale = gym.scale.flatMap { $0.isColors ? $0 : nil }
        let blocks = GymAscentKeys.shared.blocks(list: ascents)
        Text(L(scale != nil ? "gym_sheet_hint_colors" : "gym_sheet_hint_grades")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
        if let scale {
            ForEach(scale.levels, id: \.name) { lv in
                let color = GymPresentation.color(hex: lv.hex)
                GymCounterRow(label: lv.name.capitalized, sub: GymPresentation.gradeRange(lv.gradeFrom, lv.gradeTo),
                              swatch: color, accent: color, count: Int(GymTraining.shared.countOf(ascents: blocks, key: lv.name)),
                              onMinus: { adjust(lv.name, -1) }, onPlus: { adjust(lv.name, 1) })
            }
        } else {
            ForEach(GymAscentKeys.shared.BLOCK_GRADES, id: \.self) { g in
                let color = GradeColor.chip(g)
                GymCounterRow(label: g.uppercased(), sub: nil, swatch: color, accent: color,
                              count: Int(GymTraining.shared.countOf(ascents: blocks, key: g)),
                              onMinus: { adjust(g, -1) }, onPlus: { adjust(g, 1) })
            }
        }
    }
}

/// Fila con − n +: bolita de color, nombre (y rango) y el contador.
struct GymCounterRow: View {
    let label: String
    let sub: String?
    let swatch: Color?
    let accent: Color
    let count: Int
    let onMinus: () -> Void
    let onPlus: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            if let swatch { Circle().fill(swatch).frame(width: 26, height: 26).overlay(Circle().stroke(Cumbre.rule, lineWidth: 1)) }
            HStack(alignment: .lastTextBaseline, spacing: 6) {
                Text(label).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                if let sub { Text(sub).font(.system(size: 11)).foregroundStyle(Cumbre.ink2) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            round("−", enabled: count > 0, onMinus)
            Text("\(count)").font(.system(size: 16, weight: .bold)).foregroundStyle(Cumbre.ink).frame(minWidth: 24)
            round("+", enabled: true, onPlus)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(count > 0 ? accent : Cumbre.rule, lineWidth: count > 0 ? 2 : 1))
    }

    private func round(_ text: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.system(size: 18, weight: .bold))
                .foregroundStyle(enabled ? Cumbre.terraFill : Cumbre.ink2.opacity(0.5))
                .frame(width: 34, height: 34).overlay(Circle().stroke(Cumbre.rule, lineWidth: 1)).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Fechas de las hojas de entreno («viernes 21 nov»), en el idioma activo, y paso de Date a LocalDate de Kotlin.
enum GymDates {
    static func todayLong() -> String { long(Date()) }

    static func long(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = LanguageManager.shared.locale
        f.dateFormat = "EEEE d MMM"
        return f.string(from: date).replacingOccurrences(of: ".", with: "")
    }

    /// LocalDate de Kotlin a Date (mediodía local, sin saltos de zona).
    static func date(_ d: Kotlinx_datetimeLocalDate) -> Date {
        var c = DateComponents()
        c.year = Int(d.year)
        c.month = Int(d.monthNumber)
        c.day = Int(d.dayOfMonth)
        c.hour = 12
        return Calendar.current.date(from: c) ?? Date()
    }

    static func local(_ date: Date) -> Kotlinx_datetimeLocalDate {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return Kotlinx_datetimeLocalDate(year: Int32(c.year ?? 2026), monthNumber: Int32(c.month ?? 1), dayOfMonth: Int32(c.day ?? 1))
    }
}
