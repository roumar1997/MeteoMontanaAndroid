import SwiftUI
import Shared

/// «GRADOS» row of a board (§28), right under its header. Collapsed: label, stacked colour bar and «Top 7B+». Expanded: one
/// pill + bar + count row per grade; tapping a grade filters the problem list (again = clear). Logic in shared `BoardGrades`.
/// Mirror of `BoardGradeRow.kt`.
struct BoardGradeRow: View {
    let items: [GymAscent]
    @Binding var selected: String?
    @State private var open = false

    var body: some View {
        let slices = BoardGrades.shared.slices(items: items)
        if !slices.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Button { open.toggle() } label: { header(slices) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("gym_board_grades_toggle_cd"))
                if open {
                    let top = Double(BoardGrades.shared.maxCount(slices: slices))
                    ForEach(slices, id: \.grade) { s in line(s, top) }
                    Text(L("gym_board_grades_hint")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading).cardChrome(16)
        }
    }

    private func header(_ slices: [BoardGradeSlice]) -> some View {
        HStack(spacing: 10) {
            Text(L("gym_board_grades_label").uppercased()).eyebrow(Cumbre.ink2).lineLimit(1)
            GeometryReader { geo in
                HStack(spacing: 2) {
                    ForEach(slices, id: \.grade) { s in
                        GradeColor.chipStyle(s.grade).stroke
                            .frame(width: max(2, (geo.size.width - CGFloat(slices.count - 1) * 2) * CGFloat(s.share)))
                    }
                }
            }
            .frame(height: 9).clipShape(Capsule())
            if let top = BoardGrades.shared.top(slices: slices) {
                (Text(L("gym_board_grades_top") + " ").foregroundColor(Cumbre.ink2)
                    + Text(top.uppercased()).foregroundColor(GradeColor.chipStyle(top).stroke).bold())
                    .font(.system(size: 13, weight: .semibold)).lineLimit(1)
            }
            Image(systemName: open ? "chevron.up" : "chevron.down").font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Cumbre.ink2)
        }
        .contentShape(Rectangle())
    }

    private func line(_ s: BoardGradeSlice, _ top: Double) -> some View {
        let st = GradeColor.chipStyle(s.grade)
        let on = BoardGrades.shared.isSelected(selected: selected, grade: s.grade)
        let dim = selected != nil && !on
        return Button { selected = BoardGrades.shared.toggle(selected: selected, tapped: s.grade) } label: {
            HStack(spacing: 10) {
                Text(s.grade.uppercased()).font(.system(size: 13, weight: .bold))
                    .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white).lineLimit(1)
                    .frame(width: 64).padding(.vertical, 4)
                    .background(st.stroke, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Cumbre.ink : .clear, lineWidth: 2))
                GymBar(fraction: Double(s.count) / top, color: st.stroke)
                Text("\(s.count)").font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink)
            }
            .opacity(dim ? 0.4 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
