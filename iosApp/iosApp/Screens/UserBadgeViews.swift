import SwiftUI
import Shared

// Verificado (admin) y aureola del título alrededor de la foto, para CUALQUIER persona en cualquier sitio (perfil,
// feed, comentarios, chats). Álvaro, 2026-10-19: «que lo de admin sea un verificado como en Instagram» y «el título
// alrededor de la foto». Las filas dicen «voy a enseñar a esta persona» y la tienda los pide juntos al servidor.

@MainActor
final class UserBadgeStore: ObservableObject {
    static let shared = UserBadgeStore()

    @Published private(set) var badges: [String: UserBadge] = [:]
    private var asked: Set<String> = []
    private var pending: Set<String> = []
    private var flushing = false

    /// Una vista avisa de que va a enseñar a esta persona: se junta con las demás y se pide todo de golpe.
    func want(_ uid: String?) {
        guard let uid, !uid.isEmpty, !asked.contains(uid) else { return }
        pending.insert(uid)
        guard !flushing else { return }
        flushing = true
        Task {
            try? await Task.sleep(nanoseconds: 200_000_000)
            let ids = Array(pending)
            pending.removeAll()
            flushing = false
            await load(ids)
        }
    }

    /// Tras cambiar tu título o el título especial de alguien: se vuelve a pedir.
    func refresh(_ uid: String?) {
        guard let uid else { return }
        asked.remove(uid)
        want(uid)
    }

    private func load(_ uids: [String]) async {
        let fresh = uids.filter { !asked.contains($0) }
        var start = 0
        while start < fresh.count {
            let chunk = Array(fresh[start..<min(start + 150, fresh.count)])
            start += 150
            asked.formUnion(chunk)
            if let list = try? await AppDependencies.shared.container.getUserBadges.invoke(uids: chunk) {
                for b in list { badges[b.uid] = b }
            } else {
                asked.subtract(chunk)   // sin red: se volverá a pedir cuando reaparezca la vista
            }
        }
    }
}

/// La aureola de una persona: nada si oculta su título o es Aspirante; el color de su título; oro brillante para
/// «Señor de la Cumbre» y el color elegido (brillante) para un título especial.
struct AvatarRing: View {
    let badge: UserBadge?
    let size: CGFloat
    @State private var spin = false

    var body: some View {
        if let b = badge, !b.titleHidden, let style = ringStyle(b) {
            let width = max(2, size * 0.07)
            Group {
                if style.shiny {
                    Circle().stroke(
                        AngularGradient(colors: [style.color, Color.white.opacity(0.95), style.color, style.color.opacity(0.7), style.color],
                                        center: .center),
                        lineWidth: width)
                        .rotationEffect(.degrees(spin ? 360 : 0))
                        .shadow(color: style.color.opacity(0.6), radius: size * 0.08)
                        .onAppear { withAnimation(.linear(duration: 4).repeatForever(autoreverses: false)) { spin = true } }
                } else {
                    Circle().stroke(style.color, lineWidth: width)
                }
            }
            .frame(width: size + width * 2, height: size + width * 2)
            .allowsHitTesting(false)
        }
    }

    private func ringStyle(_ b: UserBadge) -> (color: Color, shiny: Bool)? {
        if let special = b.specialTitle, !special.isEmpty {
            return (colorFromHex(b.specialColor) ?? Color(red: 0.72, green: 0.53, blue: 0.04), true)
        }
        let level = Int(b.titleLevel)
        if level >= 50 { return (Color(red: 0.85, green: 0.65, blue: 0.13), true) }
        if level <= 0 { return nil }
        return (rewardStyle(level).color, false)
    }
}

/// El check azul de verificado junto al nombre, solo si esa persona es admin.
struct VerifiedCheck: View {
    let uid: String?
    var size: CGFloat = 14
    @ObservedObject private var store = UserBadgeStore.shared

    var body: some View {
        Group {
            if let uid, store.badges[uid]?.verified == true {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: size))
                    .foregroundStyle(.white, Color(red: 0.11, green: 0.61, blue: 0.94))
                    .accessibilityLabel(L("Verificado"))
            }
        }
        .onAppear { store.want(uid) }
    }
}
