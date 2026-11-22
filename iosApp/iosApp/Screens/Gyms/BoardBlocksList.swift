import SwiftUI
import Shared

/// Buscador + lista única «BLOQUES · N» de UN tablero: con nombre y «Sin nombre», del grado más alto al más bajo
/// (shared: `BoardBlocks.rows`). Tocar uno con nombre abre su detalle por días. Espejo de `BoardProblemsList.kt`.
struct BoardNamedSection: View {
    let kind: BoardKind
    let summary: GymSummary
    var angle: Int?
    @State private var query = ""
    /// Own profile: the ⋯ of each day in the detail edits or deletes it (§26).
    var onEdit: ((BoardEntry) -> Void)?
    var onDelete: ((BoardEntry) -> Void)?
    @State private var detail: NamedProblemPick?

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink2)
            TextField(L("gym_board_search_hint", BoardStyle.name(kind)), text: $query).font(.system(size: 14))
                .onChange(of: query) { _, new in
                    let max = Int(BoardProblems.shared.MAX_NAME)
                    if new.count > max { query = String(new.prefix(max)) }
                }
        }
        .padding(12)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
        let found = BoardBlocks.shared.rows(summary: summary, board: kind, angle: angle.map { KotlinInt(int: Int32($0)) }, query: query)
        if found.isEmpty {
            if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(L("gym_board_search_empty")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(.vertical, 6)
            }
        } else {
            Text(L("gym_boards_blocks", BoardBlocks.shared.total(rows: found)).uppercased()).eyebrow(Cumbre.ink2).padding(.top, 4)
            ForEach(Array(found.enumerated()), id: \.offset) { _, r in
                if r.isNamed || onEdit != nil {
                    Button { detail = NamedProblemPick(row: r) } label: { BoardBlockRowView(row: r) }.buttonStyle(.plain)
                } else {
                    BoardBlockRowView(row: r)
                }
            }
        }
        Color.clear.frame(height: 0)
            .sheet(item: $detail) { d in
                NamedProblemSheet(problem: d.row, summary: summary, angle: angle,
                                  onEditDay: onEdit == nil ? nil : { day in pass(d.row, day, onEdit) },
                                  onDeleteDay: onDelete == nil ? nil : { day in pass(d.row, day, onDelete) })
            }
    }

    /// Closes the detail and hands the day over; one sheet at a time, so it waits for the detail to go.
    private func pass(_ row: BoardBlockRow, _ day: BoardBlockDay, _ action: ((BoardEntry) -> Void)?) {
        let entry = BoardEditing.shared.entry(row: row, day: day, angleFilter: angle.map { KotlinInt(int: Int32($0)) })
        detail = nil
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { action?(entry) }
    }
}

/// Bloque con nombre elegido para abrir su detalle.
struct NamedProblemPick: Identifiable {
    let row: BoardBlockRow
    var id: String { row.board.name + "|" + row.grade + "|" + (row.name ?? "") }
}

/// Una fila: grado, nombre (o «Sin nombre» en gris), «N días · última fecha», ×N y debajo FLASH dorado o «N intentos».
private struct BoardBlockRowView: View {
    let row: BoardBlockRow
    var body: some View {
        HStack(spacing: 12) {
            GymGradeBadge(grade: row.grade)
            VStack(alignment: .leading, spacing: 1) {
                if let name = row.name {
                    Text(name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                } else {
                    Text(L("gym_board_unnamed")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                }
                Text([row.dates.count > 1 ? LP("gym_board_reps_days", row.dates.count) : nil,
                      L("gym_board_reps_last", GymDayText.long(row.lastDate))].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 3) {
                Text(L("gym_board_reps_badge", row.reps)).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.terra).lineLimit(1)
                if row.isFlash {
                    FlashBadge()
                } else if let best = row.bestAttempts?.intValue {
                    Text(attemptsText(best)).font(.system(size: 11)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10).cardChrome(14)
        .contentShape(Rectangle())
    }
}

/// Detalle de un bloque con nombre: «Cerrar», línea «Kilter · 30° · ×5 en 2 días · ⚡ Flash / mejor: N intentos» y una fila por
/// día (el más reciente primero) con ×reps, «en N intentos» o FLASH y, pequeño, el rocódromo. Propio: el ⋯ de cada día lo edita o lo borra. Espejo de `NamedProblemSheet.kt`.
struct NamedProblemSheet: View {
    let problem: BoardBlockRow
    let summary: GymSummary
    var angle: Int?
    /// Own profile: the ⋯ of each day edits it or deletes it (§26).
    var onEditDay: ((BoardBlockDay) -> Void)?
    var onDeleteDay: ((BoardBlockDay) -> Void)?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                GymGradeBadge(grade: problem.grade)
                Text(problem.name ?? L("gym_board_unnamed")).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ClosePill { dismiss() }
            }
            .padding(.bottom, 4)
            Text(summaryLine).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(.bottom, 8)
            ScrollView {
                VStack(spacing: 6) {
                    ForEach(Array(problem.days.enumerated()), id: \.offset) { _, d in dayRow(d) }
                }
            }
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 12)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var summaryLine: String {
        let best = problem.bestAttempts?.intValue
        let attempts: String? = AttemptsBridge.isFlash(best) ? L("gym_board_flash_line") : best.map { L("gym_board_best", attemptsText($0)) }
        return [BoardStyle.name(problem.board), angle.map { "\($0)°" },
                LP("gym_board_detail_reps", problem.dates.count, problem.reps, problem.dates.count), attempts]
            .compactMap { $0 }.joined(separator: " · ")
    }

    private func dayRow(_ d: BoardBlockDay) -> some View {
        let gym = d.gymId == GymSession.Companion.shared.NO_GYM ? L("gym_board_no_gym") : summary.byGym.first { $0.gymId == d.gymId }?.name
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 1) {
                Text(GymDayText.long(d.date)).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                if let gym { Text(gym).font(.system(size: 11)).foregroundStyle(Cumbre.ink2).lineLimit(1) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 3) {
                Text(L("gym_board_reps_badge", d.reps)).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.terra).lineLimit(1)
                if AttemptsBridge.isFlash(d.attempts?.intValue) {
                    FlashBadge()
                } else if let n = d.attempts?.intValue {
                    Text(L("gym_board_in_attempts", attemptsText(n))).font(.system(size: 11)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                }
            }
            if onEditDay != nil || onDeleteDay != nil {
                EntryMoreMenu(onEdit: onEditDay.map { e -> () -> Void in { e(d) } }, onDelete: onDeleteDay.map { del -> () -> Void in { del(d) } },
                              deleteLabel: L("gym_board_delete"))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10).cardChrome(14)
    }
}
