import SwiftUI
import Shared

/// Estado del botón de seguir de un aviso de «nuevo seguidor» (espejo de FollowUi en NotificationRows.kt).
enum NotifFollowState { case none, following, requested }

// Color del iconito de la esquina según el tipo (mismos valores que NotificationRows.kt).
private let cornerBlue = Color(red: 0x3B / 255, green: 0x7D / 255, blue: 0xD8 / 255)
private let cornerRed = Color(red: 0xD6 / 255, green: 0x45 / 255, blue: 0x45 / 255)
private let cornerGreen = Color(red: 0x4F / 255, green: 0x7A / 255, blue: 0x3A / 255)
private let cornerGold = Color(red: 0xC9 / 255, green: 0xA2 / 255, blue: 0x4A / 255)

private func cornerIcon(_ type: String) -> (symbol: String, color: Color) {
    switch type {
    case "NEW_FOLLOWER", "FOLLOW_REQUEST", "FOLLOW_ACCEPTED": return ("person.fill.badge.plus", cornerBlue)
    case "FEED_LIKE", "FEED_COMMENT_LIKE": return ("heart.fill", cornerRed)
    case "FEED_COMMENT": return ("bubble.left.fill", cornerGreen)
    case "FEED_MENTION": return ("at", cornerGreen)
    case "MEETUP_NEW": return ("person.3.fill", cornerGold)
    default: return ("bell.fill", cornerGold)
    }
}

/// Fila de un aviso, como en Instagram: foto redonda real con iconito del tipo, «**usuario** acción · hace X»
/// y a la derecha el botón (Seguir / Ver), la miniatura de la publicación o una flecha.
struct NotificationRowView: View {
    let g: NotificationGroup
    let follow: NotifFollowState
    let onToggleFollow: () -> Void
    let onOpen: () -> Void

    private var n: Shared.Notification { g.lead }

    var body: some View {
        HStack(spacing: 12) {
            avatar
            text.font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                .lineLimit(3).frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(g.unread ? Cumbre.terra.opacity(0.08) : Color.clear)
        .contentShape(Rectangle())
    }

    // «**usuario** acción · hace X». Los avisos antiguos (sin actor) siguen con título y cuerpo.
    private var text: Text {
        let age = Text(" · " + notificationDateLabel(n.createdAt)).foregroundColor(Cumbre.ink3)
        guard n.type != "TEAM_MESSAGE", let who = n.actorName else {
            var t = Text(n.title).fontWeight(.bold)
            if let b = n.body, !b.isEmpty { t = t + Text(" · " + b) }
            return t + age
        }
        let many = g.others > 0
        let subject = (n.subject ?? "").isEmpty ? nil : n.subject
        var t = Text(who).fontWeight(.bold)
        if many {
            t = t + Text(" " + L("y") + " ") + Text(L("%@ más", Int(g.others))).fontWeight(.bold)
        }
        return t + Text(" " + action(many: many, subject: subject)) + age
    }

    private func action(many: Bool, subject: String?) -> String {
        switch n.type {
        case "NEW_FOLLOWER": return L("empezó a seguirte")
        case "FOLLOW_REQUEST": return L("quiere seguirte")
        case "FOLLOW_ACCEPTED": return L("aceptó tu solicitud")
        case "FEED_LIKE":
            if many { return subject != nil ? L("dieron me gusta a tu ascenso de %@", subject!) : L("dieron me gusta a tu publicación") }
            return subject != nil ? L("dio me gusta a tu ascenso de %@", subject!) : L("dio me gusta a tu publicación")
        case "FEED_COMMENT":
            return subject != nil ? L("comentó en tu ascenso de %@", subject!) : L("comentó en tu publicación")
        case "FEED_COMMENT_LIKE": return many ? L("dieron me gusta a tu comentario") : L("dio me gusta a tu comentario")
        case "FEED_MENTION": return L("te mencionó")
        case "MEETUP_NEW": return subject != nil ? L("propuso una quedada · %@", subject!) : L("propuso una quedada")
        default: return n.body ?? ""
        }
    }

    // Foto redonda real (con la aureola de su título) o la inicial; iconito del tipo en la esquina.
    private var avatar: some View {
        ZStack(alignment: .bottomTrailing) {
            if n.type == "TEAM_MESSAGE" {
                Image("logo_cumbre").resizable().scaledToFill()
                    .frame(width: 44, height: 44).clipShape(Circle())
            } else if let url = n.actorPhotoUrl {
                AvatarCircle(url: url, size: 44, uid: n.actorUid)
            } else {
                Text(String((n.actorName ?? n.title).trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
                    .font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                    .frame(width: 44, height: 44).background(Cumbre.paper2).clipShape(Circle())
            }
            if n.type != "TEAM_MESSAGE" {
                let icon = cornerIcon(n.type)
                Image(systemName: icon.symbol).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 20, height: 20).background(icon.color).clipShape(Circle())
                    .overlay(Circle().stroke(Cumbre.bg, lineWidth: 2))
                    .offset(x: 4, y: 2)
            }
        }
        .frame(width: 48, height: 48)
    }

    @ViewBuilder private var trailing: some View {
        if n.type == "NEW_FOLLOWER", n.actorUid != nil {
            let following = follow != .none
            Button(action: onToggleFollow) {
                Text(follow == .none ? NSLocalizedString("profile_follow", comment: "")
                     : follow == .following ? NSLocalizedString("profile_following", comment: "")
                     : NSLocalizedString("profile_requested", comment: ""))
                    .font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    .foregroundStyle(following ? Cumbre.ink : .white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(following ? Cumbre.paper : Cumbre.terra)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }.buttonStyle(.plain)
        } else if n.type == "MEETUP_NEW" {
            Button(action: onOpen) {
                Text(L("Ver")).font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .background(Cumbre.terra).clipShape(RoundedRectangle(cornerRadius: 8))
            }.buttonStyle(.plain)
        } else if let t = n.thumbUrl, let u = URL(string: t) {
            Button(action: onOpen) {
                CachedImage(url: u) { img in img.resizable().scaledToFill() } placeholder: { Rectangle().fill(Cumbre.paper) }
                    .frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 8))
            }.buttonStyle(.plain)
        } else if ["TEAM_MESSAGE", "FOLLOW_REQUEST", "FOLLOW_ACCEPTED"].contains(n.type) {
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink3)
        }
    }
}

/// Cabecera de tramo: HOY / ESTA SEMANA / ANTES.
func notificationSectionTitle(_ s: NotificationSection) -> String {
    if s == .today { return L("HOY") }
    if s == .thisWeek { return L("ESTA SEMANA") }
    return L("ANTES")
}
