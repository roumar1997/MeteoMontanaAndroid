import SwiftUI

/// Estado vacío que ENSEÑA: icono en círculo + título + mensaje + acción opcional.
/// Espejo de EmptyState.kt de Android.
struct EmptyStateView: View {
    let icon: String          // SF Symbol
    let title: String
    let message: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle().fill(Cumbre.terra.opacity(0.10)).frame(width: 72, height: 72)
                Image(systemName: icon).font(.system(size: 30)).foregroundStyle(Cumbre.terra)
            }
            Text(title).font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Cumbre.ink).multilineTextAlignment(.center)
            Text(message).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
            if let actionLabel, let action {
                Button(action: action) {
                    Text(actionLabel).font(Cumbre.mono(13, .bold)).tracking(0.8)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Cumbre.terra, in: RoundedRectangle(cornerRadius: 2))
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 32).padding(.vertical, 48)
    }
}

/// Pista de primera vez (coach-mark ligero): aviso tintado descartable que sale
/// UNA vez por [hintKey] (persistido en UserDefaults). Espejo de FirstTimeHint.kt.
struct FirstTimeHint: View {
    let hintKey: String
    let text: String
    @State private var visible: Bool

    init(hintKey: String, text: String) {
        self.hintKey = hintKey
        self.text = text
        _visible = State(initialValue: !UserDefaults.standard.bool(forKey: "hint_\(hintKey)"))
    }

    var body: some View {
        if visible {
            HStack(alignment: .center, spacing: 8) {
                Image(systemName: "lightbulb").font(.system(size: 16)).foregroundStyle(Cumbre.terra)
                Text(text).font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    UserDefaults.standard.set(true, forKey: "hint_\(hintKey)")
                    visible = false
                } label: {
                    Image(systemName: "xmark").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.terra.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal, 16).padding(.vertical, 4)
        }
    }
}
