import SwiftUI
import FirebaseAuth
import Shared

// Comentarios de la comunidad (con votos ▲/▼) en piedras/muros y vías.
// Espejo de LineCommentsSection.kt de Android: un fetch por piedra (el store
// trae TODOS los comentarios del bloque) y cada hilo filtra los suyos.

@MainActor
final class LineCommentsStore: ObservableObject {
    @Published var comments: [LineCommentDto] = []
    private var loadedBlockId: String?

    func load(blockId: String) async {
        guard loadedBlockId != blockId else { return }
        loadedBlockId = blockId
        if let list = try? await AppDependencies.shared.container.blockApi.getComments(blockId: blockId) {
            comments = list
        }
    }

    func add(blockId: String, lineId: String?, text: String) async {
        let req = CreateLineCommentRequest(lineId: lineId, text: text)
        if let created = try? await AppDependencies.shared.container.blockApi.addComment(blockId: blockId, req: req) {
            comments.append(created)
        }
    }

    func vote(commentId: String, value: Int) async {
        guard let myVote = try? await AppDependencies.shared.container.blockApi
            .voteComment(commentId: commentId, value: Int32(value)) else { return }
        comments = comments.map { c in
            guard c.id == commentId else { return c }
            let old = Int(c.myVote)
            let neu = Int(truncating: myVote)
            return LineCommentDto(
                id: c.id, blockId: c.blockId, lineId: c.lineId, author: c.author,
                uid: c.uid, createdAt: c.createdAt, text: c.text,
                upvotesCount: c.upvotesCount + Int32((neu == 1 ? 1 : 0) - (old == 1 ? 1 : 0)),
                downvotesCount: c.downvotesCount + Int32((neu == -1 ? 1 : 0) - (old == -1 ? 1 : 0)),
                myVote: Int32(neu))
        }
    }

    func delete(commentId: String) async {
        try? await AppDependencies.shared.container.blockApi.deleteComment(commentId: commentId)
        comments.removeAll { $0.id == commentId }
    }
}

/// Hilo desplegable de comentarios: la CABECERA ENTERA es pulsable.
/// lineId = nil → comentarios de la piedra entera.
struct LineCommentsThreadView: View {
    @ObservedObject var store: LineCommentsStore
    let blockId: String
    let lineId: String?
    @State private var expanded = false
    @State private var draft = ""

    private var mine: [LineCommentDto] {
        store.comments
            .filter { $0.blockId == blockId && $0.lineId == lineId }
            .sorted {
                let a = $0.upvotesCount - $0.downvotesCount
                let b = $1.upvotesCount - $1.downvotesCount
                if a != b { return a > b }
                return ($0.createdAt ?? "") > ($1.createdAt ?? "")
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("💬 COMENTARIOS" + (mine.isEmpty ? "" : " · \(mine.count)"))
                        .font(Cumbre.mono(10, .bold)).tracking(1.0)
                        .foregroundStyle(Cumbre.ink3)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10)).foregroundStyle(Cumbre.ink3)
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                if mine.isEmpty {
                    Text("Sé el primero en comentar.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                ForEach(mine, id: \.id) { c in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(c.author).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            Spacer()
                            if Auth.auth().currentUser?.uid == c.uid {
                                Button("BORRAR") { Task { await store.delete(commentId: c.id) } }
                                    .font(Cumbre.mono(9, .bold))
                                    .foregroundStyle(Cumbre.bad)
                                    .buttonStyle(.plain)
                            }
                        }
                        Text(c.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        HStack(spacing: 8) {
                            voteChip("▲", Int(c.upvotesCount), c.myVote == 1) {
                                Task { await store.vote(commentId: c.id, value: 1) }
                            }
                            voteChip("▼", Int(c.downvotesCount), c.myVote == -1) {
                                Task { await store.vote(commentId: c.id, value: -1) }
                            }
                        }
                    }
                    .padding(10)
                    .background(Cumbre.bg)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                HStack(spacing: 6) {
                    TextField("Escribe un comentario…", text: $draft, axis: .vertical)
                        .textFieldStyle(.plain)
                        .font(.system(size: 14))
                        .lineLimit(1...3)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Cumbre.paper)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    Button {
                        let t = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !t.isEmpty else { return }
                        draft = ""
                        Task { await store.add(blockId: blockId, lineId: lineId, text: t) }
                    } label: {
                        Text("ENVIAR").font(Cumbre.mono(11, .bold)).tracking(0.6)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .background(draft.trimmingCharacters(in: .whitespaces).isEmpty
                                        ? Color.gray.opacity(0.4) : Cumbre.terra)
                    }
                    .buttonStyle(.plain)
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.bottom, 4)
            }
        }
    }

    private func voteChip(_ symbol: String, _ count: Int, _ active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 3) {
                Text(symbol).font(.system(size: 11))
                Text("\(count)").font(Cumbre.mono(11))
            }
            .foregroundStyle(active ? .white : Cumbre.ink3)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(active ? Cumbre.terra : Cumbre.paper)
            .overlay(Rectangle().stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
