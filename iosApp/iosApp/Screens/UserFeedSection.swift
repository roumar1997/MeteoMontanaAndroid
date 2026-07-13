import SwiftUI
import Shared

// Sección "PUBLICACIONES" del perfil público: posts del usuario en el feed
// (GET /api/feed?scope=user&uid=…). El backend devuelve lista vacía si el
// perfil es privado y no le sigues. Con uid = nil (perfil PROPIO, pestaña
// Perfil) usa scope=mine. Espejo de UserFeedSection.kt.

@MainActor
final class UserFeedViewModel: ObservableObject {
    @Published var posts: [FeedPost] = []
    @Published var loading = true
    @Published var endReached = false
    @Published var loadingMore = false

    private let container = AppDependencies.shared.container
    private let pageSize = 10
    /// nil = perfil propio → scope "mine" (sin uid).
    let uid: String?
    private var scope: String { uid != nil ? "user" : "mine" }

    init(uid: String?) { self.uid = uid }

    func load() async {
        do {
            let page = try await container.getFeedPage.invoke(
                scope: scope, before: nil, limit: Int32(pageSize), uid: uid)
            posts = page
            endReached = page.count < pageSize
        } catch {
            endReached = true
        }
        loading = false
    }

    func loadMore() async {
        guard !loading, !loadingMore, !endReached, let lastId = posts.last?.id else { return }
        loadingMore = true
        do {
            let page = try await container.getFeedPage.invoke(
                scope: scope, before: KotlinLong(value: lastId),
                limit: Int32(pageSize), uid: uid)
            let known = Set(posts.map { $0.id })
            posts += page.filter { !known.contains($0.id) }
            endReached = page.count < pageSize
        } catch {}
        loadingMore = false
    }

    /// Borra un post propio (optimista: lo quita de la lista y llama al server).
    func deletePost(_ post: FeedPost) {
        let previous = posts
        posts = previous.filter { $0.id != post.id }
        Task {
            do {
                try await container.deleteFeedPost.invoke(postId: post.id)
            } catch {
                posts = previous
            }
        }
    }

    func toggleLike(_ post: FeedPost) {
        let liked = !post.likedByMe
        updatePost(post.id) {
            copyPost($0, likedByMe: liked,
                     likeCount: max($0.likeCount + (liked ? 1 : -1), 0))
        }
        Task {
            do {
                let count = liked
                    ? try await container.likeFeedPost.invoke(postId: post.id)
                    : try await container.unlikeFeedPost.invoke(postId: post.id)
                updatePost(post.id) { copyPost($0, likedByMe: liked, likeCount: count.int64Value) }
            } catch {
                updatePost(post.id) {
                    copyPost($0, likedByMe: post.likedByMe, likeCount: post.likeCount)
                }
            }
        }
    }

    private func updatePost(_ id: Int64, _ transform: (FeedPost) -> FeedPost) {
        posts = posts.map { $0.id == id ? transform($0) : $0 }
    }

    // Comentarios (para la hoja compartida).
    func loadComments(_ postId: Int64) async -> [FeedComment]? {
        try? await container.getFeedComments.invoke(postId: postId)
    }

    func addComment(_ postId: Int64, _ text: String) async -> FeedComment? {
        guard let created = try? await container.addFeedComment.invoke(postId: postId, text: text)
        else { return nil }
        updatePost(postId) { copyPost($0, commentCount: $0.commentCount + 1) }
        return created
    }

    func deleteComment(_ postId: Int64, _ commentId: String) async -> Bool {
        do {
            try await container.deleteFeedComment.invoke(commentId: commentId)
            updatePost(postId) { copyPost($0, commentCount: max($0.commentCount - 1, 0)) }
            return true
        } catch { return false }
    }
}

struct UserFeedSection: View {
    /// nil = perfil propio (scope=mine).
    let uid: String?
    /// Título de la sección (perfil propio usa "MIS PUBLICACIONES").
    let title: String
    /// true en el perfil PROPIO: recarga al volver a primer plano (post nuevo
    /// tras marcar una vía) y permite borrar los posts propios con confirmación.
    let ownProfile: Bool
    /// false cuando la sección vive en una pantalla dedicada con su propio
    /// título (MyPostsView) — paridad con showTitle de UserFeedSection.kt.
    let showTitle: Bool
    @StateObject private var vm: UserFeedViewModel
    @ObservedObject private var moderation = ModerationStore.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var commentsPost: FeedPost? = nil
    @State private var reportPost: FeedPost? = nil
    @State private var deleteCandidate: FeedPost? = nil
    @State private var navTarget: FeedNav? = nil

    init(uid: String?, title: String = "PUBLICACIONES", ownProfile: Bool = false,
         showTitle: Bool = true) {
        self.uid = uid
        self.title = title
        self.ownProfile = ownProfile
        self.showTitle = showTitle
        _vm = StateObject(wrappedValue: UserFeedViewModel(uid: uid))
    }

    var body: some View {
        Group {
            if !vm.loading {
                sectionBody
            }
        }
        .task { await vm.load() }
        // Perfil propio: refresca al volver a primer plano (patrón ON_RESUME
        // de Android — el post nuevo aparece tras marcar una vía).
        .onChange(of: scenePhase) { _, phase in
            if ownProfile, phase == .active { Task { await vm.load() } }
        }
        .alert("Eliminar publicación", isPresented: Binding(
            get: { deleteCandidate != nil },
            set: { if !$0 { deleteCandidate = nil } })) {
            Button("Cancelar", role: .cancel) { deleteCandidate = nil }
            Button("Eliminar", role: .destructive) {
                if let p = deleteCandidate { vm.deletePost(p) }
                deleteCandidate = nil
            }
        } message: {
            Text("¿Eliminar esta publicación del feed?")
        }
        .navigationDestination(item: $navTarget) { t in
            switch t {
            case .school(let id, let via): SchoolLoaderView(schoolId: id, openVia: via)
            case .user(let u): PublicProfileView(uid: u)
            }
        }
        .sheet(item: $commentsPost) { post in
            FeedCommentsSheet(
                post: post,
                loadComments: { await vm.loadComments($0) },
                addComment: { await vm.addComment($0, $1) },
                deleteComment: { await vm.deleteComment($0, $1) },
                onOpenUser: { u in
                    commentsPost = nil
                    navTarget = .user(u)
                })
        }
        .sheet(item: $reportPost) { post in
            ReportSheet(
                title: "DENUNCIAR PUBLICACIÓN",
                authorLabel: feedAuthorLabel(post.author)
            ) { reason, alsoBlock in
                moderation.report(
                    targetType: "FEED_POST", targetId: String(post.id), reason: reason,
                    alsoBlockUid: alsoBlock ? post.author.uid : nil)
                reportPost = nil
            }
        }
    }

    @ViewBuilder private var sectionBody: some View {
        let visible = vm.posts.filter { !moderation.hiddenIds.contains(String($0.id)) }
        VStack(alignment: .leading, spacing: 12) {
            if showTitle {
                Text(title).eyebrow()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if visible.isEmpty {
                Text("Sin publicaciones todavía.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
            } else {
                ForEach(visible, id: \.id) { post in
                    FeedPostCard(
                        post: post,
                        onOpenSchool: { id, _, lineName in
                            navTarget = .school(id, lineName)
                        },
                        onOpenUser: { navTarget = .user($0) },
                        onToggleLike: { vm.toggleLike(post) },
                        onOpenComments: { commentsPost = post },
                        onDelete: { if post.mine { deleteCandidate = post } },
                        onReport: post.mine ? nil : { reportPost = post })
                }
                if !vm.endReached {
                    Button {
                        Task { await vm.loadMore() }
                    } label: {
                        Text("CARGAR MÁS")
                            .font(Cumbre.mono(11, .bold)).tracking(1.2)
                            .foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.plain)
                    .disabled(vm.loadingMore)
                }
            }
        }
    }
}

/// Pantalla dedicada "Mis publicaciones" (perfil propio): tus posts del feed
/// (scope=mine) con likes/comentarios/borrar y CARGAR MÁS. Reutiliza
/// UserFeedSection; se abre desde la fila del perfil. Espejo de
/// MyPostsScreen.kt de Android.
struct MyPostsView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                UserFeedSection(uid: nil, ownProfile: true, showTitle: false)
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mis publicaciones")
        .navigationBarTitleDisplayMode(.inline)
    }
}
