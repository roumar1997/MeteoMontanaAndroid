import SwiftUI
import Shared

// Detalle de un post del feed Comunidad: tarjeta completa + comentarios +
// campo de respuesta. Destino del push/campanita "feed_post" y del VER POST
// del panel de admin. Espejo de FeedPostDetailScreen.kt.

@MainActor
final class FeedPostDetailViewModel: ObservableObject {
    @Published var loading = true
    @Published var post: FeedPost? = nil
    @Published var comments: [FeedComment] = []
    /// El post ya no existe (404 / id no válido) o no se pudo cargar.
    @Published var notFound = false

    private let container = AppDependencies.shared.container
    private let postId: Int64?

    /// Un id no numérico (p. ej. targetId inesperado desde admin) cae al flujo
    /// "ya no existe" en vez de crashear (misma decisión que Android).
    init(postIdString: String) {
        postId = Int64(postIdString)
    }

    func load() async {
        guard let id = postId else {
            loading = false
            notFound = true
            return
        }
        loading = true
        notFound = false
        do {
            post = try await container.getFeedPost.invoke(postId: id)
            loading = false
            comments = (try? await container.getFeedComments.invoke(postId: id)) ?? []
        } catch {
            loading = false
            notFound = true
        }
    }

    func toggleLike() {
        guard let p = post else { return }
        let liked = !p.likedByMe
        post = copyPost(p, likedByMe: liked,
                        likeCount: max(p.likeCount + (liked ? 1 : -1), 0))
        Task {
            do {
                let count = liked
                    ? try await container.likeFeedPost.invoke(postId: p.id)
                    : try await container.unlikeFeedPost.invoke(postId: p.id)
                if let cur = post { post = copyPost(cur, likeCount: count) }
            } catch {
                post = p   // revertir el optimismo
            }
        }
    }

    func addComment(_ text: String) async -> Bool {
        guard let p = post else { return false }
        guard let created = try? await container.addFeedComment.invoke(postId: p.id, text: text)
        else { return false }
        comments.append(created)
        if let cur = post { post = copyPost(cur, commentCount: cur.commentCount + 1) }
        return true
    }
}

struct FeedPostDetailView: View {
    @StateObject private var vm: FeedPostDetailViewModel
    @ObservedObject private var moderation = ModerationStore.shared
    @Environment(\.dismiss) private var dismiss

    @State private var reportPost = false
    @State private var reportComment: FeedComment? = nil
    @State private var navTarget: FeedNav? = nil
    @State private var text = ""
    @State private var sending = false

    init(postIdString: String) {
        _vm = StateObject(wrappedValue: FeedPostDetailViewModel(postIdString: postIdString))
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("PUBLICACIÓN")
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 10)
            Divider().overlay(Cumbre.rule)
            content
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Publicación")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $navTarget) { t in
            switch t {
            case .school(let id, let via): SchoolLoaderView(schoolId: id, openVia: via)
            case .user(let uid): PublicProfileView(uid: uid)
            }
        }
        .task { await vm.load() }
        .sheet(isPresented: $reportPost) {
            if let post = vm.post {
                ReportSheet(
                    title: "DENUNCIAR PUBLICACIÓN",
                    authorLabel: feedAuthorLabel(post.author)
                ) { reason, alsoBlock in
                    moderation.report(
                        targetType: "FEED_POST", targetId: String(post.id), reason: reason,
                        alsoBlockUid: alsoBlock ? post.author.uid : nil)
                }
            }
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

    @ViewBuilder private var content: some View {
        if vm.loading {
            Spacer()
            ProgressView()
            Spacer()
        } else if vm.notFound || vm.post == nil {
            Spacer()
            VStack(spacing: 12) {
                Text("La publicación ya no existe")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                Button("REINTENTAR") { Task { await vm.load() } }
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    .buttonStyle(.plain)
            }
            Spacer()
        } else if let post = vm.post {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    FeedPostCard(
                        post: post,
                        onOpenSchool: { id, _, lineName in
                            navTarget = .school(id, lineName)
                        },
                        onOpenUser: { navTarget = .user($0) },
                        onToggleLike: { vm.toggleLike() },
                        onOpenComments: {},
                        onDelete: {},
                        onReport: post.mine ? nil : { reportPost = true })
                    Text("COMENTARIOS")
                        .font(Cumbre.mono(10, .bold)).tracking(1.8)
                        .foregroundStyle(Cumbre.terra)
                        .padding(.top, 16).padding(.bottom, 4)
                    let visible = vm.comments.filter { !moderation.hiddenIds.contains($0.id) }
                    if visible.isEmpty {
                        Text("Sé el primero en comentar.")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                            .padding(.vertical, 12)
                    }
                    ForEach(visible, id: \.id) { comment in
                        FeedCommentRow(
                            comment: comment,
                            onOpenUser: { navTarget = .user($0) },
                            onDelete: nil,
                            onReport: comment.mine ? nil : { reportComment = comment })
                        Divider().overlay(Cumbre.rule)
                    }
                }
                .padding(12)
            }
            // Campo de respuesta inline.
            HStack(spacing: 8) {
                TextField("Escribe un comentario…", text: $text, axis: .vertical)
                    .lineLimit(1...3)
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink)
                    .padding(.horizontal, 10).padding(.vertical, 9)
                    .background(Cumbre.paper)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                Button {
                    let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !t.isEmpty, !sending else { return }
                    sending = true
                    Task {
                        if await vm.addComment(t) { text = "" }
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
}
