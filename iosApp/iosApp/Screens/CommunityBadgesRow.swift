import SwiftUI
import Shared

/// Lo que sabe la comunidad de una vía, en pastillas (igual que Android):
/// ▶ rojo = hay vídeo de beta de esa altura (tocar lo abre; si hay varios, se elige de una lista),
/// ✓ gris = cuántas personas la han hecho, ✓ verde = cuántas de ellas miden más / menos de 1,70.
/// Va FUERA del botón de la fila, para que tocar un vídeo no abra también la piedra.
struct CommunityBadgesRow: View {
    let hit: LineSearchHit

    private static let videoTint = Color(red: 0.75, green: 0.22, blue: 0.17)
    private static let doneTint = Color(red: 0.18, green: 0.49, blue: 0.31)

    var body: some View {
        let badges = hit.communityBadges
        if !badges.isEmpty {
            GuideFlowLayout(spacing: 5) {
                ForEach(Array(badges.enumerated()), id: \.offset) { _, b in
                    badgeView(b)
                }
            }
        }
    }

    @ViewBuilder private func badgeView(_ b: CommunityBadge) -> some View {
        switch onEnum(of: b) {
        case .video(let v):
            videoBadge(v)
        case .done(let d):
            pill(icon: "checkmark", text: Self.peopleText(Int(d.people)), tint: Cumbre.ink2)
        case .doneByHeight(let d):
            pill(icon: "checkmark",
                 text: L("%@ de %@", Int(d.people), Self.heightText(d.height) ?? ""),
                 tint: Self.doneTint)
        }
    }

    @ViewBuilder private func videoBadge(_ v: CommunityBadge.Video) -> some View {
        let videos = v.videos
        let label = [videos.count == 1 ? L("Vídeo") : L("%@ vídeos", videos.count),
                     Self.heightText(v.height)]
            .compactMap { $0 }.joined(separator: " ")
        if videos.count == 1, let url = URL(string: videos[0].url) {
            Link(destination: url) { pill(icon: "play.fill", text: label, tint: Self.videoTint) }
        } else {
            Menu {
                ForEach(Array(videos.enumerated()), id: \.offset) { i, video in
                    if let url = URL(string: video.url) {
                        Link(destination: url) {
                            Label(L("Vídeo %@ · %@", i + 1, url.host?.replacingOccurrences(of: "www.", with: "") ?? video.url),
                                  systemImage: "play.fill")
                        }
                    }
                }
            } label: { pill(icon: "play.fill", text: label, tint: Self.videoTint) }
        }
    }

    private func pill(icon: String, text: String, tint: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 10, weight: .bold))
            Text(text).font(.system(size: 12, weight: .semibold)).lineLimit(1)
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 9).padding(.vertical, 4)
        .background(tint.opacity(0.12), in: Capsule())
    }

    /// "+1,70" / "−1,70" / nil (igual que `CommunityBadge.heightText` de shared).
    static func heightText(_ h: String?) -> String? { h == "TALL" ? "+1,70" : h == "SHORT" ? "−1,70" : nil }

    /// "1 persona" / "5 personas".
    static func peopleText(_ n: Int) -> String { n == 1 ? L("1 persona") : L("%@ personas", n) }

}
