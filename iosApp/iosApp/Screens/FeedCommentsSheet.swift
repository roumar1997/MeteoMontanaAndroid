import SwiftUI
import Shared

// Pestaña Comunidad: feed social (Explorar | Siguiendo | Mías + trofeo RANKING)
// — espejo de FeedScreen.kt / FeedViewModel.kt de Android.


// Hoja de comentarios del feed (hilo) + orden/menciones. Reparto de FeedView.swift.

func feedReplyMention(_ c: FeedComment) -> String {
    if let u = c.author?.username { return "@" + u + " " }
    if let n = c.author?.displayName { return n + " " }
    return ""
}

/// Copia de un FeedComment con el estado de like cambiado (los data class de
/// Kotlin llegan a Swift sin copy con defaults).
func copyCommentLike(_ c: FeedComment, liked: Bool, count: Int64) -> FeedComment {
    FeedComment(
        id: c.id, postId: c.postId, uid: c.uid, author: c.author,
        text: c.text, createdAt: c.createdAt, mine: c.mine,
        likeCount: count, likedByMe: liked, parentId: c.parentId)
}

/// Ordena los comentarios en hilos: cada raíz seguido de TODAS sus respuestas
/// (también las de respuestas, aplanadas bajo el mismo raíz, estilo Instagram)
/// en orden cronológico. Respuestas sin raíz visible van al final.
func feedThreadOrder(_ list: [FeedComment]) -> [FeedComment] {
    let byId = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
    func rootId(_ c: FeedComment) -> String {
        var cur = c
        var guardCount = 0
        while let p = cur.parentId, guardCount < 50 {
            guard let parent = byId[p] else { return p }
            cur = parent
            guardCount += 1
        }
        return cur.id
    }
    let roots = list.filter { $0.parentId == nil }
    let replies = Dictionary(grouping: list.filter { $0.parentId != nil }, by: rootId)
    let rootIds = Set(roots.map { $0.id })
    var out: [FeedComment] = []
    for r in roots {
        out.append(r)
        out.append(contentsOf: replies[r.id] ?? [])
    }
    for (rid, group) in replies where !rootIds.contains(rid) {
        out.append(contentsOf: group)
    }
    return out
}

struct FeedCommentsSheet: View {
    let post: FeedPost
    let loadComments: (Int64) async -> [FeedComment]?
    let addComment: (Int64, String, String?) async -> FeedComment?
    let deleteComment: (Int64, String) async -> Bool
    /// (commentId, like) → likeCount actualizado, nil si falló.
    let toggleCommentLike: (String, Bool) async -> Int64?
    let onOpenUser: (String) -> Void

    @ObservedObject private var moderation = ModerationStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var comments: [FeedComment]? = nil
    @State private var text = ""
    @State private var sending = false
    @State private var reportComment: FeedComment? = nil
    /// Comentario al que se está respondiendo (banner sobre el campo).
    @State private var replyTo: FeedComment? = nil
    @FocusState private var commentFocused: Bool

    private func patchComment(_ id: String, _ transform: (FeedComment) -> FeedComment) {
        comments = comments?.map { $0.id == id ? transform($0) : $0 }
    }

    private func toggleLike(_ comment: FeedComment) {
        let liked = !comment.likedByMe
        // Optimista (como el like del post); si el server falla, se revierte.
        patchComment(comment.id) {
            copyCommentLike($0, liked: liked, count: max($0.likeCount + (liked ? 1 : -1), 0))
        }
        Task {
            if let count = await toggleCommentLike(comment.id, liked) {
                patchComment(comment.id) { copyCommentLike($0, liked: liked, count: count) }
            } else {
                patchComment(comment.id) { _ in comment }
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("COMENTARIOS")
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 10)
            Divider().overlay(Cumbre.rule)
            commentsList
            replyBanner
            MentionSuggestionsView(text: $text)
            inputRow
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .task {
            comments = await loadComments(post.id) ?? []
        }
        .sheet(item: $reportComment) { c in
            ReportSheet(
                title: "DENUNCIAR COMENTARIO",
                authorLabel: feedAuthorLabel(c.author)
            ) { reason, alsoBlock in
                let blockUid = c.author?.uid ?? c.uid
                moderation.report(
                    targetType: "FEED_COMMENT", targetId: c.id, reason: reason,
                    alsoBlockUid: alsoBlock ? blockUid : nil)
                reportComment = nil
            }
        }
    }

    @ViewBuilder private var commentsList: some View {
        if let list = comments {
            let visible = list.filter { !moderation.hiddenIds.contains("FEED_COMMENT:\($0.id)") }
            ScrollView {
                LazyVStack(spacing: 0) {
                    if visible.isEmpty {
                        Text("Sé el primero en comentar.")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                    }
                    ForEach(feedThreadOrder(visible), id: \.id) { comment in
                        FeedCommentRow(
                            comment: comment,
                            onOpenUser: onOpenUser,
                            onDelete: comment.mine ? {
                                Task {
                                    if await deleteComment(post.id, comment.id) {
                                        comments = comments?.filter { $0.id != comment.id }
                                    }
                                }
                            } : nil,
                            onReport: comment.mine ? nil : { reportComment = comment },
                            isReply: comment.parentId != nil,
                            onToggleLike: { toggleLike(comment) },
                            onReply: {
                                replyTo = comment
                                // Mención automática (estilo Instagram).
                                let mention = feedReplyMention(comment)
                                if !mention.isEmpty, !text.hasPrefix(mention) {
                                    text = mention + text
                                }
                            })
                        Divider().overlay(Cumbre.rule)
                    }
                }
            }
        } else {
            ProgressView().frame(maxWidth: .infinity).padding(32)
            Spacer()
        }
    }

    @ViewBuilder private var replyBanner: some View {
        if let target = replyTo {
            HStack {
                Text("Respondiendo a " + (feedAuthorLabel(target.author) ?? ""))
                    .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                Spacer()
                Button { replyTo = nil } label: {
                    Text("✕").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
        }
    }

    private var inputRow: some View {
        HStack(spacing: 8) {
            TextField("Escribe un comentario…", text: $text, axis: .vertical)
                .lineLimit(1...3)
                .font(.system(size: 14))
                .foregroundStyle(Cumbre.ink)
                .focused($commentFocused)
                .padding(.horizontal, 10).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
            Button {
                let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !t.isEmpty, !sending else { return }
                sending = true
                // Resignar el foco antes de vaciar (ver nota en FeedPostDetailView):
                // con axis:.vertical un `text = ""` con el campo enfocado no limpia
                // lo visible → el mensaje se quedaba y se podía reenviar.
                commentFocused = false
                Task {
                    if let created = await addComment(post.id, t, replyTo?.id) {
                        comments = (comments ?? []) + [created]
                        text = ""
                        replyTo = nil
                    }
                    sending = false
                }
            } label: {
                Image(systemName: "paperplane")
                    .font(.system(size: 17))
                    .foregroundStyle(Cumbre.terra)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || sending)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
    }
}

struct FeedCommentRow: View {
    let comment: FeedComment
    let onOpenUser: (String) -> Void
    var onDelete: (() -> Void)? = nil
    /// Denunciar comentario ajeno; nil = sin bandera.
    var onReport: (() -> Void)? = nil
    /// true = respuesta (se indenta bajo su comentario raíz).
    var isReply: Bool = false
    var onToggleLike: (() -> Void)? = nil
    var onReply: (() -> Void)? = nil

    private var authorUid: String? { comment.author?.uid ?? comment.uid }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Button { if let u = authorUid { onOpenUser(u) } } label: {
                AvatarCircle(url: comment.author?.photoUrl, size: 28)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Button { if let u = authorUid { onOpenUser(u) } } label: {
                        Text(feedAuthorLabel(comment.author) ?? "")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                    }
                    .buttonStyle(.plain)
                    Text(feedRelativeTime(comment.createdAt))
                        .font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                }
                MentionText(text: comment.text, onOpenUser: onOpenUser)
                // Acciones: like (corazón + contador) y responder.
                HStack(spacing: 4) {
                    // Zona táctil ≥40pt (padding generoso), como las banderas.
                    if let onToggleLike {
                        Button(action: onToggleLike) {
                            HStack(spacing: 4) {
                                Image(systemName: comment.likedByMe ? "heart.fill" : "heart")
                                    .font(.system(size: 12))
                                if comment.likeCount > 0 {
                                    Text("\(comment.likeCount)").font(Cumbre.mono(10))
                                }
                            }
                            .foregroundStyle(comment.likedByMe ? Cumbre.terra : Cumbre.ink3)
                            .padding(.horizontal, 10).padding(.vertical, 12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    if let onReply {
                        Button(action: onReply) {
                            Text("RESPONDER")
                                .font(Cumbre.mono(10, .bold)).tracking(1.2)
                                .foregroundStyle(Cumbre.ink3)
                                .padding(.horizontal, 10).padding(.vertical, 12)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Spacer()
            if let onDelete {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
            }
            if let onReport {
                // Zona táctil ≥40pt, como las banderas existentes.
                Button(action: onReport) {
                    Image(systemName: "flag")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, isReply ? 44 : 16)
        .padding(.trailing, 16)
        .padding(.vertical, 10)
    }
}
