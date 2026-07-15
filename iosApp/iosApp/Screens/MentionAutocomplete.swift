import SwiftUI
import Shared

/// Username en curso al FINAL del texto (lo que va tras el último `@` sin
/// espacios). nil si no se está escribiendo una mención ahí.
func activeMentionQuery(_ text: String) -> String? {
    guard let at = text.lastIndex(of: "@") else { return nil }
    if at > text.startIndex {
        let before = text[text.index(before: at)]
        if !before.isWhitespace { return nil }   // @ pegado a palabra → no es mención
    }
    let after = String(text[text.index(after: at)...])
    if after.count > 20 || after.contains(where: { $0.isWhitespace }) { return nil }
    return after
}

/// Reemplaza la mención en curso (del último `@`) por `@username ` completo.
func applyMention(_ text: String, _ username: String) -> String {
    guard let at = text.lastIndex(of: "@") else { return text + "@\(username) " }
    return String(text[..<at]) + "@\(username) "
}

/// Lista de sugerencias de usuarios mientras escribes `@`. Se coloca encima del
/// campo; al tocar, reemplaza el texto con la mención. Nada si no hay mención.
struct MentionSuggestionsView: View {
    @Binding var text: String
    @State private var results: [PublicProfile] = []
    @State private var searchTask: Task<Void, Never>? = nil

    var body: some View {
        Group {
            if activeMentionQuery(text) != nil, !results.isEmpty {
                VStack(spacing: 0) {
                    ForEach(results, id: \.uid) { p in
                        if let uname = p.username {
                            Button { text = applyMention(text, uname) } label: {
                                HStack(spacing: 8) {
                                    AvatarCircle(url: p.photoUrl, size: 26)
                                    Text("@\(uname)")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(Cumbre.terra)
                                    if let dn = p.displayName, !dn.isEmpty {
                                        Text(dn).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 10).padding(.vertical, 8)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                .padding(.horizontal, 12)
            }
        }
        .onChange(of: text) { _, _ in
            searchTask?.cancel()
            guard let q = activeMentionQuery(text), q.count >= 1 else { results = []; return }
            searchTask = Task {
                try? await Task.sleep(nanoseconds: 220_000_000)   // debounce
                if Task.isCancelled { return }
                let r = (try? await AppDependencies.shared.container.searchUsers.invoke(
                    query: q, limit: 6)) ?? []
                if !Task.isCancelled { results = r }
            }
        }
    }
}
