import SwiftUI

/// Regex de mención: @ + username (3-20, minúsculas/dígitos/_) sin cortar palabra.
private let mentionRegex = try! NSRegularExpression(
    pattern: "@([a-z0-9_]{3,20})(?![a-z0-9_])", options: [.caseInsensitive])

/// Construye un AttributedString con los `@usuario` en terracota y como enlace
/// `cumbremention://usuario` (lo intercepta MentionText para navegar). El resto
/// del texto va en [baseColor].
func feedMentionAttributed(_ text: String, baseColor: Color) -> AttributedString {
    var result = AttributedString("")
    let ns = text as NSString
    var last = 0
    for m in mentionRegex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
        if m.range.location > last {
            var pre = AttributedString(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
            pre.foregroundColor = baseColor
            result += pre
        }
        let full = ns.substring(with: m.range)                    // "@username"
        let uname = ns.substring(with: m.range(at: 1)).lowercased()
        var seg = AttributedString(full)
        seg.foregroundColor = Cumbre.terra
        seg.link = URL(string: "cumbremention://\(uname)")
        result += seg
        last = m.range.location + m.range.length
    }
    if last < ns.length {
        var tail = AttributedString(ns.substring(from: last))
        tail.foregroundColor = baseColor
        result += tail
    }
    return result
}

/// Igual que un Text normal pero con los `@usuario` pulsables → [onOpenUser]
/// con el username (sin @). Se usa en descripciones y comentarios del feed.
struct MentionText: View {
    let text: String
    var baseColor: Color = Cumbre.ink
    var font: Font = .system(size: 14)
    let onOpenUser: (String) -> Void

    var body: some View {
        Text(feedMentionAttributed(text, baseColor: baseColor))
            .font(font)
            .environment(\.openURL, OpenURLAction { url in
                if url.scheme == "cumbremention" {
                    let username = url.host ?? url.absoluteString
                        .replacingOccurrences(of: "cumbremention://", with: "")
                    if !username.isEmpty { onOpenUser(username) }
                    return .handled
                }
                return .systemAction
            })
    }
}
