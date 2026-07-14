import SwiftUI
import Shared

// Pestaña Comunidad: feed social (Explorar | Siguiendo | Mías + trofeo RANKING)
// — espejo de FeedScreen.kt / FeedViewModel.kt de Android.

/// Vistas del selector superior (se persiste la última en UserDefaults).
enum FeedTab: String {
    case following, all, mine, ranking

    var scopeParam: String {
        switch self {
        case .following: return "following"
        case .mine: return "mine"
        default: return "all"
        }
    }
}

/// Filtro "Mostrar:" por tipo de post — SOLO en cliente (oculta sobre lo
/// cargado; la paginación sigue trayendo todo).
enum FeedFilter: String {
    case all, sends, newBlocks
}

/// ¿El post pasa el filtro "Mostrar:"? (Ascensos = TICK|PROJECT_DONE,
/// Piedras nuevas = NEW_BLOCK|NEW_LINE).
func feedMatchesFilter(_ post: FeedPost, _ filter: FeedFilter) -> Bool {
    switch filter {
    case .all: return true
    case .sends: return post.kind == "TICK" || post.kind == "PROJECT_DONE"
    case .newBlocks: return post.kind == "NEW_BLOCK" || post.kind == "NEW_LINE"
    }
}

/// Eyebrow del tipo de post; para TICK distingue por modalidad (paridad con
/// kindLabel de FeedScreen.kt).
func feedKindLabel(_ kind: String, _ discipline: String?) -> String {
    switch kind {
    case "PROJECT_DONE": return "PROYECTO CONSEGUIDO"
    case "NEW_BLOCK": return "PIEDRA NUEVA"
    case "NEW_LINE": return "VÍA NUEVA"
    default:
        let d = (discipline ?? "").uppercased()
        if d == "BOULDER" { return "BLOQUE HECHO" }
        if d == "ROUTE" { return "VÍA HECHA" }
        return "HECHO"
    }
}

/// "hace 2 h" a partir de un createdAt "yyyy-MM-ddTHH:mm:ss" (hora del servidor).
func feedRelativeTime(_ createdAt: String) -> String {
    let df = DateFormatter()
    df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    df.locale = Locale(identifier: "en_US_POSIX")
    // La hora del servidor es UTC (interpretarla como local sumaba 2h en España).
    df.timeZone = TimeZone(identifier: "UTC")
    guard let date = df.date(from: String(createdAt.prefix(19))) else { return "" }
    let minutes = max(Int(Date().timeIntervalSince(date) / 60), 0)
    if minutes < 1 { return "ahora" }
    if minutes < 60 { return "hace \(minutes) min" }
    if minutes < 60 * 24 { return "hace \(minutes / 60) h" }
    return "hace \(minutes / (60 * 24)) días"
}

/// Tipo de inicio → etiqueta legible (mismo mapeo que StartTypeLabel.kt de
/// Android: SIT/STAND/JUMP/TRAV). nil si no se reconoce.
func feedStartTypeLabel(_ startType: String?) -> String? {
    switch startType?.uppercased() {
    case "SIT": return "Sentado"
    case "STAND": return "Pie"
    case "JUMP": return "Lance"
    case "TRAV": return "Trav."
    default: return nil
    }
}

/// Título «vía · grado · inicio» del post (paridad con FeedPostCard de Android).
func feedPostTitle(_ post: FeedPost) -> String {
    var t = post.lineName ?? post.blockName ?? ""
    if let g = post.grade, !g.isEmpty { t += " · \(g)" }
    if let s = feedStartTypeLabel(post.startType) { t += " · \(s)" }
    return t
}

/// «piedra · escuela · roca» del post.
func feedPostPlace(_ post: FeedPost) -> String {
    var parts: [String] = []
    if let b = post.blockName, !b.isEmpty, post.lineName != nil { parts.append(b) }
    if let s = post.schoolName, !s.isEmpty { parts.append(s) }
    if let r = post.rockType, !r.isEmpty { parts.append(r) }
    return parts.joined(separator: " · ")
}

// Los modelos de Kotlin ya traen `id` → conformance directa para
// sheet(item:)/ForEach.
extension FeedPost: Identifiable {}
extension FeedComment: Identifiable {}

// MARK: - ViewModel

@MainActor
final class FeedViewModel: ObservableObject {
    @Published var tab: FeedTab
    @Published var filter: FeedFilter
    @Published var posts: [FeedPost] = []
    /// Primera carga del scope actual (spinner a pantalla).
    @Published var loading = false
    @Published var loadingMore = false
    @Published var endReached = false
    /// Fallo de carga sin contenido previo → estado offline/error estándar.
    @Published var failed = false

    private let container = AppDependencies.shared.container
    private let pageSize = 20
    /// Token para descartar respuestas de un scope ya abandonado.
    private var loadGeneration = 0

    init() {
        let d = UserDefaults.standard
        tab = FeedTab(rawValue: d.string(forKey: "feed_tab") ?? "") ?? .all
        filter = FeedFilter(rawValue: d.string(forKey: "feed_filter") ?? "") ?? .all
    }

    func selectTab(_ t: FeedTab) {
        guard t != tab else { return }
        UserDefaults.standard.set(t.rawValue, forKey: "feed_tab")
        tab = t
        // RANKING pinta CommunityView (su propio VM); el feed no recarga.
        if t != .ranking {
            Task { await load(initial: posts.isEmpty) }
        }
    }

    func selectFilter(_ f: FeedFilter) {
        guard f != filter else { return }
        UserDefaults.standard.set(f.rawValue, forKey: "feed_filter")
        filter = f
    }

    /// Recarga silenciosa (al volver a primer plano / reintentar): primera
    /// página sin spinner si ya había contenido.
    func refreshSilent() async {
        guard tab != .ranking else { return }
        await load(initial: posts.isEmpty)
    }

    func load(initial: Bool) async {
        let t = tab
        loadGeneration += 1
        let gen = loadGeneration
        if initial { loading = true; failed = false }
        do {
            let page = try await container.getFeedPage.invoke(
                scope: t.scopeParam, before: nil, limit: Int32(pageSize), uid: nil)
            guard gen == loadGeneration, tab == t else { return }
            posts = page
            loading = false
            failed = false
            endReached = page.count < pageSize
        } catch {
            guard gen == loadGeneration, tab == t else { return }
            loading = false
            // Con contenido previo el error no borra la lista.
            if posts.isEmpty { failed = true }
        }
    }

    /// Página siguiente (cursor = id del último post visible).
    func loadMore() async {
        guard !loading, !loadingMore, !endReached, tab != .ranking else { return }
        guard let lastId = posts.last?.id else { return }
        loadingMore = true
        do {
            let page = try await container.getFeedPage.invoke(
                scope: tab.scopeParam, before: KotlinLong(value: lastId),
                limit: Int32(pageSize), uid: nil)
            let known = Set(posts.map { $0.id })
            posts += page.filter { !known.contains($0.id) }
            endReached = page.count < pageSize
        } catch {}
        loadingMore = false
    }

    /// Toggle like con actualización optimista; el server devuelve el contador real.
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
                // Revertir el optimismo si falló.
                updatePost(post.id) {
                    copyPost($0, likedByMe: post.likedByMe, likeCount: post.likeCount)
                }
            }
        }
    }

    func deletePost(_ post: FeedPost) {
        Task {
            do {
                try await container.deleteFeedPost.invoke(postId: post.id)
                posts.removeAll { $0.id == post.id }
            } catch {}
        }
    }

    private func updatePost(_ id: Int64, _ transform: (FeedPost) -> FeedPost) {
        posts = posts.map { $0.id == id ? transform($0) : $0 }
    }

    // ── Comentarios (hoja compartida) ──────────────────────────────────────

    func loadComments(_ postId: Int64) async -> [FeedComment]? {
        try? await container.getFeedComments.invoke(postId: postId)
    }

    func addComment(_ postId: Int64, _ text: String, _ parentId: String?) async -> FeedComment? {
        guard let created = try? await container.addFeedComment.invoke(
            postId: postId, text: text, parentId: parentId)
        else { return nil }
        updatePost(postId) { copyPost($0, commentCount: $0.commentCount + 1) }
        return created
    }

    /// Like/unlike de un comentario; devuelve el likeCount actualizado (nil si falló).
    func toggleCommentLike(_ commentId: String, _ like: Bool) async -> Int64? {
        do {
            let count = like
                ? try await container.likeFeedComment.invoke(commentId: commentId)
                : try await container.unlikeFeedComment.invoke(commentId: commentId)
            return count.int64Value
        } catch { return nil }
    }

    func deleteComment(_ postId: Int64, _ commentId: String) async -> Bool {
        do {
            try await container.deleteFeedComment.invoke(commentId: commentId)
            updatePost(postId) { copyPost($0, commentCount: max($0.commentCount - 1, 0)) }
            return true
        } catch { return false }
    }
}

/// Copia de un FeedPost con campos cambiados (los data class de Kotlin no
/// exponen copy() con defaults en Swift → se pasan TODOS los argumentos).
func copyPost(_ p: FeedPost, likedByMe: Bool? = nil, likeCount: Int64? = nil,
              commentCount: Int64? = nil) -> FeedPost {
    FeedPost(
        id: p.id, kind: p.kind, createdAt: p.createdAt, author: p.author,
        schoolId: p.schoolId, schoolName: p.schoolName,
        blockId: p.blockId, blockName: p.blockName,
        lineId: p.lineId, lineName: p.lineName, grade: p.grade,
        discipline: p.discipline, rockType: p.rockType,
        photoPath: p.photoPath, linePath: p.linePath,
        likeCount: likeCount ?? p.likeCount,
        likedByMe: likedByMe ?? p.likedByMe,
        commentCount: commentCount ?? p.commentCount,
        mine: p.mine,
        startType: p.startType, caption: p.caption, photoUrl: p.photoUrl)
}

// MARK: - Vista principal

/// Destino de navegación desde una tarjeta del feed.
enum FeedNav: Identifiable, Hashable {
    case school(String, String?)   // (schoolId, lineName para abrir su piedra)
    case user(String)
    var id: String {
        switch self {
        case .school(let s, let v): return "school-\(s)-\(v ?? "")"
        case .user(let u): return "user-\(u)"
        }
    }
}

struct FeedView: View {
    @StateObject private var vm = FeedViewModel()
    @ObservedObject private var moderation = ModerationStore.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var commentsPost: FeedPost? = nil
    @State private var deleteCandidate: FeedPost? = nil
    @State private var reportPost: FeedPost? = nil
    @State private var showSearchUsers = false
    @State private var navTarget: FeedNav? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                tabsRow
                Divider().overlay(Cumbre.rule)
                if vm.tab != .ranking {
                    filterRow
                    Divider().overlay(Cumbre.rule)
                }
                if vm.tab == .ranking {
                    // Ranking de contribuidores: la pantalla existente, embebida.
                    CommunityView()
                        .navigationTitle("")
                } else {
                    feedList
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(item: $navTarget) { t in
                switch t {
                case .school(let id, let via): SchoolLoaderView(schoolId: id, openVia: via)
                case .user(let uid): PublicProfileView(uid: uid)
                }
            }
        }
        .task {
            if vm.tab != .ranking { await vm.load(initial: vm.posts.isEmpty) }
        }
        // Frescura: recarga silenciosa al volver la app a primer plano
        // (patrón ON_RESUME de Android).
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await vm.refreshSilent() } }
        }
        .sheet(item: $commentsPost) { post in
            FeedCommentsSheet(
                post: post,
                loadComments: { await vm.loadComments($0) },
                addComment: { await vm.addComment($0, $1, $2) },
                deleteComment: { await vm.deleteComment($0, $1) },
                toggleCommentLike: { await vm.toggleCommentLike($0, $1) },
                onOpenUser: { uid in
                    commentsPost = nil
                    navTarget = .user(uid)
                })
        }
        // Hoja de denuncia de un post ajeno (target FEED_POST; mismo endpoint
        // /api/reports, 409 idempotente lo traga KtorModerationApi).
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
        .sheet(isPresented: $showSearchUsers) { SearchUsersView() }
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
    }

    private var header: some View {
        HStack {
            Text("Feed")
                .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Spacer()
            HelpButton(topicKey: "community")
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    /// Pestañas de texto subrayadas (tipo Instagram) + trofeo RANKING a la
    /// derecha. Con el ranking activo, ninguna pestaña lleva subrayado.
    private var tabsRow: some View {
        HStack(spacing: 20) {
            feedTextTab("Explorar", selected: vm.tab == .all) { vm.selectTab(.all) }
            feedTextTab("Siguiendo", selected: vm.tab == .following) { vm.selectTab(.following) }
            feedTextTab("Mías", selected: vm.tab == .mine) { vm.selectTab(.mine) }
            Spacer()
            Button { vm.selectTab(.ranking) } label: {
                Image(systemName: "trophy")
                    .font(.system(size: 17))
                    .foregroundStyle(vm.tab == .ranking ? Cumbre.terra : Cumbre.ink3)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 16).padding(.trailing, 4)
    }

    private func feedTextTab(_ label: String, selected: Bool,
                             action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Text(label)
                    .font(.system(size: 14, weight: selected ? .bold : .regular))
                    .foregroundStyle(selected ? Cumbre.ink : Cumbre.ink3)
                    .padding(.top, 10).padding(.bottom, 6)
                Rectangle()
                    .fill(selected ? Cumbre.terra : Color.clear)
                    .frame(height: 2)
            }
            .fixedSize()
        }
        .buttonStyle(.plain)
    }

    /// Fila "Mostrar:" con píldoras Todo / Ascensos / Piedras nuevas.
    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Text("Mostrar:").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                filterPill("Todo", selected: vm.filter == .all) { vm.selectFilter(.all) }
                filterPill("Ascensos", selected: vm.filter == .sends) { vm.selectFilter(.sends) }
                filterPill("Piedras nuevas", selected: vm.filter == .newBlocks) { vm.selectFilter(.newBlocks) }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
    }

    private func filterPill(_ label: String, selected: Bool,
                            action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(Cumbre.mono(11, .bold)).tracking(0.4)
                .foregroundStyle(selected ? .white : Cumbre.ink3)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(selected ? Cumbre.terra : Cumbre.paper)
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var feedList: some View {
        // Lo denunciado desaparece al instante para quien denuncia (Apple 1.2)
        // y el filtro "Mostrar:" solo OCULTA en cliente.
        let visible = vm.posts
            .filter { !moderation.hiddenIds.contains(String($0.id)) }
            .filter { feedMatchesFilter($0, vm.filter) }
        if vm.loading {
            Spacer()
            ProgressView()
            Spacer()
        } else if vm.failed {
            // Sin conexión / error sin contenido previo: estado estándar.
            Spacer()
            VStack(spacing: 10) {
                Image(systemName: "icloud.slash")
                    .font(.system(size: 34)).foregroundStyle(Cumbre.ink3)
                Text("Sin conexión.\nEl feed necesita internet.")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .multilineTextAlignment(.center)
                Button("REINTENTAR") { Task { await vm.refreshSilent() } }
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    .buttonStyle(.plain)
                    .padding(.top, 4)
            }
            Spacer()
        } else if vm.posts.isEmpty {
            ScrollView {
                VStack(spacing: 16) {
                    Text(emptyText)
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .multilineTextAlignment(.center)
                    if vm.tab == .following {
                        Button("BUSCAR USUARIOS") { showSearchUsers = true }
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                            .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 32).padding(.vertical, 80)
                .frame(maxWidth: .infinity)
            }
            .refreshable { await vm.refreshSilent() }
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if visible.isEmpty {
                        Text("No hay publicaciones con este filtro.")
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40)
                    }
                    ForEach(visible, id: \.id) { post in
                        FeedPostCard(
                            post: post,
                            onOpenSchool: { id, _, lineName in
                                navTarget = .school(id, lineName)
                            },
                            onOpenUser: { navTarget = .user($0) },
                            onToggleLike: { vm.toggleLike(post) },
                            onOpenComments: { commentsPost = post },
                            onDelete: { deleteCandidate = post },
                            onReport: post.mine ? nil : { reportPost = post })
                    }
                    if !vm.endReached {
                        // Sentinel: al aparecer (final de la lista) pide otra página.
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(16)
                            .onAppear { Task { await vm.loadMore() } }
                    }
                }
                .padding(.horizontal, 12).padding(.vertical, 12)
            }
            .refreshable { await vm.refreshSilent() }
        }
    }

    private var emptyText: String {
        switch vm.tab {
        case .following:
            return "Aún no sigues a nadie.\nSigue a otros escaladores para ver su actividad aquí."
        case .mine:
            return "Aún no has publicado ningún ascenso."
        default:
            return "Todavía no hay actividad.\nMarca una vía como hecha y estrena el feed."
        }
    }
}

/// Nombre visible del autor ("@usuario" si no hay displayName).
func feedAuthorLabel(_ author: FeedAuthor?) -> String? {
    guard let author else { return nil }
    if let d = author.displayName, !d.isEmpty { return d }
    if let u = author.username, !u.isEmpty { return "@" + u }
    return nil
}

// MARK: - Tarjeta de post (reutilizada por feed, detalle y perfil público)

struct FeedPostCard: View {
    let post: FeedPost
    let onOpenSchool: (String, String?, String?) -> Void
    let onOpenUser: (String) -> Void
    let onToggleLike: () -> Void
    let onOpenComments: () -> Void
    let onDelete: () -> Void
    /// Denunciar el post (solo posts ajenos); nil = sin bandera.
    var onReport: (() -> Void)? = nil
    /// Líneas máximas de la caption (nil en el detalle = entera).
    var captionMaxLines: Int? = 3

    /// Foto de celebración ampliada a pantalla completa (tap en la miniatura).
    @State private var showFullPhoto = false

    private var celebrationUrl: String? {
        guard let u = post.photoUrl, !u.isEmpty else { return nil }
        return u
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            headerRow
            topoImage
            textBlock
            Divider().overlay(Cumbre.rule)
            actionsRow
        }
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        .fullScreenCover(isPresented: $showFullPhoto) {
            if let url = celebrationUrl {
                FullScreenPhotoView(photoUrl: url) { showFullPhoto = false }
            }
        }
    }

    // ── Cabecera: avatar + nombre + eyebrow del tipo + tiempo relativo ──
    private var headerRow: some View {
        Button { onOpenUser(post.author.uid) } label: {
            HStack(spacing: 10) {
                AvatarCircle(url: post.author.photoUrl, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(authorName)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Cumbre.ink)
                        .lineLimit(1)
                    Text(feedKindLabel(post.kind, post.discipline))
                        .font(Cumbre.mono(10, .bold)).tracking(1.2)
                        .foregroundStyle(Cumbre.terra)
                }
                Spacer()
                Text(feedRelativeTime(post.createdAt))
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var authorName: String {
        if let d = post.author.displayName, !d.isEmpty { return d }
        if let u = post.author.username, !u.isEmpty { return "@" + u }
        return String(post.author.uid.prefix(6))
    }

    // ── Imagen: foto de la cara con SOLO la línea de esta vía. La foto de
    // celebración (si la hay) va como miniatura superpuesta arriba-derecha;
    // sin topo, la celebración ES la imagen principal ──
    @ViewBuilder private var topoImage: some View {
        if let photo = post.photoPath, !photo.isEmpty {
            ZStack(alignment: .topTrailing) {
                Button {
                    if let sid = post.schoolId { onOpenSchool(sid, post.lineId, post.lineName) }
                } label: {
                    TopoPhotoView(photoUrl: photo, lines: postLines)
                }
                .buttonStyle(.plain)
                if let celebration = celebrationUrl {
                    Button { showFullPhoto = true } label: {
                        FeedCelebrationThumb(photoUrl: celebration)
                    }
                    .buttonStyle(.plain)
                    .padding(8)
                }
            }
        } else if let celebration = celebrationUrl {
            Button { showFullPhoto = true } label: {
                FeedMainCelebrationImage(photoUrl: celebration)
            }
            .buttonStyle(.plain)
        }
    }

    private var postLines: [TopoLineVM] {
        let points = TopoParse.points(post.linePath)
        guard !points.isEmpty else { return [] }
        return [TopoLineVM(id: post.lineId ?? "feed", name: post.lineName,
                           grade: post.grade, startType: post.startType, points: points)]
    }

    // ── Texto: «vía · grado — piedra · escuela · roca» ──
    @ViewBuilder private var textBlock: some View {
        let title = feedPostTitle(post)
        let place = feedPostPlace(post)
        Button {
            if let sid = post.schoolId { onOpenSchool(sid, post.lineId, post.lineName) }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                if !title.isEmpty {
                    Text(title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Cumbre.ink)
                }
                if !place.isEmpty {
                    Text(place)
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                // Descripción del autor (caption): recortada en la tarjeta,
                // entera en el detalle (captionMaxLines = nil).
                if let caption = post.caption, !caption.isEmpty {
                    Text(caption)
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        .lineLimit(captionMaxLines)
                        .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // ── Acciones: like + comentarios + compartir + borrar/denunciar ──
    private var actionsRow: some View {
        HStack(spacing: 2) {
            Button(action: onToggleLike) {
                Image(systemName: post.likedByMe ? "heart.fill" : "heart")
                    .font(.system(size: 17))
                    .foregroundStyle(post.likedByMe ? Cumbre.terra : Cumbre.ink3)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            if post.likeCount > 0 {
                Text("\(post.likeCount)")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            Button(action: onOpenComments) {
                Image(systemName: "bubble.right")
                    .font(.system(size: 16))
                    .foregroundStyle(Cumbre.ink3)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            if post.commentCount > 0 {
                Text("\(post.commentCount)")
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
            }
            // Compartir como IMAGEN 1080×1920 (sin enlace).
            Button {
                Task { await ShareFeedPostImage.share(post: post) }
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 16))
                    .foregroundStyle(Cumbre.ink3)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            // Directo a Instagram Stories — SOLO si hay Facebook App ID.
            if ShareFeedPostImage.canShareToStories {
                Button {
                    Task { await ShareFeedPostImage.shareToStories(post: post) }
                } label: {
                    Text("HISTORIAS")
                        .font(Cumbre.mono(10, .bold)).tracking(1.2)
                        .foregroundStyle(Cumbre.terra)
                        .padding(.horizontal, 8).padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            }
            Spacer()
            if post.mine {
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 15))
                        .foregroundStyle(Cumbre.ink3)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
            } else if let onReport {
                // Bandera de denuncia (posts ajenos), zona táctil ≥40pt.
                Button(action: onReport) {
                    Image(systemName: "flag")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - Hoja de comentarios (patrón de los comentarios de vías)

/// Mención a insertar al responder: "@username " o, si el autor no tiene
/// username, su nombre visible — así RESPONDER siempre cambia algo visible.
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
            let visible = list.filter { !moderation.hiddenIds.contains($0.id) }
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
                .padding(.horizontal, 10).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
            Button {
                let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !t.isEmpty, !sending else { return }
                sending = true
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
                Text(comment.text)
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
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
