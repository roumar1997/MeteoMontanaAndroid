import SwiftUI

/// Weight of a child inside `WeightedRow` (Compose `Modifier.weight` equivalent). Defaults to 1.
struct RowWeightKey: LayoutValueKey {
    static let defaultValue: CGFloat = 1
}

extension View {
    /// Share of the row width this child gets inside `WeightedRow`.
    func rowWeight(_ weight: CGFloat) -> some View { layoutValue(key: RowWeightKey.self, value: weight) }
}

/// Horizontal row that splits the proposed width among its children by weight, like a Compose `Row` with `weight`.
/// Every child always gets its slice, so no chip can swallow the others on narrow screens.
struct WeightedRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        let widths = slices(width: width, subviews: subviews)
        let height = zip(subviews, widths).map { $0.sizeThatFits(ProposedViewSize(width: $1, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        for (view, width) in zip(subviews, slices(width: bounds.width, subviews: subviews)) {
            view.place(at: CGPoint(x: x, y: bounds.midY), anchor: .leading, proposal: ProposedViewSize(width: width, height: bounds.height))
            x += width + spacing
        }
    }

    private func slices(width: CGFloat, subviews: Subviews) -> [CGFloat] {
        let weights = subviews.map { $0[RowWeightKey.self] }
        let total = max(weights.reduce(0, +), 0.0001)
        let free = max(width - spacing * CGFloat(max(subviews.count - 1, 0)), 0)
        return weights.map { free * $0 / total }
    }
}
