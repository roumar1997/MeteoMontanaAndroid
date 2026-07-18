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
    // Moderación: denunciar comentarios ajenos + ocultar al instante.
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var reportTarget: LineCommentDto? = nil

    private var mine: [LineCommentDto] {
        store.comments
            .filter { $0.blockId == blockId && $0.lineId == lineId && !moderation.hiddenIds.contains("COMMENT:\($0.id)") }
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
                HStack(spacing: 6) {
                    Image(systemName: "bubble.right")
                        .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    Text("COMENTARIOS" + (mine.isEmpty ? "" : " · \(mine.count)"))
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
                ForEach(Array(mine.enumerated()), id: \.element.id) { idx, c in
                    if idx > 0 { Divider().overlay(Cumbre.rule) }
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 8) {
                            Text(c.author)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Cumbre.terra)
                            Spacer()
                            if Auth.auth().currentUser?.uid == c.uid {
                                Button { Task { await store.delete(commentId: c.id) } } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                                        .padding(10).contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            } else {
                                // Comentario ajeno → bandera de denuncia.
                                Button { reportTarget = c } label: {
                                    Image(systemName: "flag")
                                        .font(.system(size: 13))
                                        .foregroundStyle(Cumbre.ink3.opacity(0.7))
                                        .padding(10).contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Text(c.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        HStack(spacing: 16) {
                            voteChip(true, Int(c.upvotesCount), c.myVote == 1) {
                                Task { await store.vote(commentId: c.id, value: 1) }
                            }
                            voteChip(false, Int(c.downvotesCount), c.myVote == -1) {
                                Task { await store.vote(commentId: c.id, value: -1) }
                            }
                        }
                    }
                    .padding(.vertical, 7)
                }
                HStack(spacing: 8) {
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
                        Image(systemName: "paperplane.fill")
                            .font(.system(size: 17))
                            .foregroundStyle(draft.trimmingCharacters(in: .whitespaces).isEmpty
                                             ? Cumbre.ink3 : Cumbre.terra)
                            .padding(6)
                    }
                    .buttonStyle(.plain)
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.bottom, 4)
            }
        }
        .sheet(item: $reportTarget) { c in
            ReportSheet(title: "DENUNCIAR COMENTARIO", authorLabel: c.author) { reason, alsoBlock in
                moderation.report(targetType: "COMMENT", targetId: c.id, reason: reason,
                                  alsoBlockUid: alsoBlock ? c.uid : nil)
            }
        }
    }

    private func voteChip(_ up: Bool, _ count: Int, _ active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 3) {
                Image(systemName: up ? "arrowtriangle.up.fill" : "arrowtriangle.down.fill")
                    .font(.system(size: 9))
                Text("\(count)").font(Cumbre.mono(11, active ? .bold : .regular))
            }
            .foregroundStyle(active ? Cumbre.terra : Cumbre.ink3)
            .padding(.horizontal, 12).padding(.vertical, 9)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// Para .sheet(item:) — el id ya existe en el DTO de Kotlin.
extension LineCommentDto: Identifiable {}
