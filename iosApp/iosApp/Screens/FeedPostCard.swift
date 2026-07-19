import SwiftUI
import Shared

// Pestaña Comunidad: feed social (Explorar | Siguiendo | Mías + trofeo RANKING)
// — espejo de FeedScreen.kt / FeedViewModel.kt de Android.


// Tarjeta de un post del feed. Reparto del antiguo FeedView.swift de 1.062 lineas.

func feedAuthorLabel(_ author: FeedAuthor?) -> String? {
    guard let author else { return nil }
    if let d = author.displayName, !d.isEmpty { return d }
    if let u = author.username, !u.isEmpty { return "@" + u }
    return nil
}

// MARK: - Tarjeta de post (reutilizada por feed, detalle y perfil público)

struct FeedPostCard: View {
    let post: FeedPost
    // (schoolId, lineId, lineName, blockId) — blockId abre la piedra en los
    // posts "piedra nueva" (sin vía).
    let onOpenSchool: (String, String?, String?, String?) -> Void
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
                    if let sid = post.schoolId { onOpenSchool(sid, post.lineId, post.lineName, post.blockId) }
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
        if !points.isEmpty {
            return [TopoLineVM(id: post.lineId ?? "feed", name: post.lineName,
                               grade: post.grade, startType: post.startType, points: points)]
        }
        // Piedra nueva (NEW_BLOCK, sin lineId): todas las vías de la cara
        // portada (blockLines del backend — antes la foto salía sin líneas).
        guard let lines = post.blockLines else { return [] }
        return lines.enumerated().compactMap { idx, l in
            let pts = TopoParse.points(l.linePath)
            guard !pts.isEmpty else { return nil }
            return TopoLineVM(id: "feed-\(idx)", name: l.name,
                              grade: l.grade, startType: l.startType, points: pts)
        }
    }

    // ── Texto: «vía · grado — piedra · escuela · roca» ──
    @ViewBuilder private var textBlock: some View {
        let title = feedPostTitle(post)
        let place = feedPostPlace(post)
        Button {
            if let sid = post.schoolId { onOpenSchool(sid, post.lineId, post.lineName, post.blockId) }
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
                    MentionText(text: caption, onOpenUser: onOpenUser)
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
