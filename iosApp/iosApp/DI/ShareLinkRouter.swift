import Foundation
import SwiftUI
import Shared

/// Router de enlaces compartidos (Universal Links).
/// Rutas: /s/e/{escuela} (abre la escuela) y /s/v/{escuela}/{lineId}
/// (abre la escuela con la piedra/vía desplegada, como el deep-link del diario).
/// Quien lo presenta es RootView (fullScreenCover observando `target`).
@MainActor
final class ShareLinkRouter: ObservableObject {
    static let shared = ShareLinkRouter()

    struct Target: Identifiable {
        var school: School? = nil
        var viaId: String? = nil
        var meetupId: String? = nil
        var userHandle: String? = nil
        var openAdminReports: Bool = false   // push de denuncia → panel DENUNCIAS
        var id: String {
            var parts: [String] = []
            parts.append(school?.id ?? "")
            parts.append(viaId ?? "")
            parts.append(meetupId ?? "")
            parts.append(userHandle ?? "")
            parts.append(openAdminReports ? "admin" : "")
            return parts.joined(separator: "|")
        }
    }

    @Published var target: Target? = nil

    /// True si la URL era nuestra (consumida); false → que la maneje otro.
    func handle(_ url: URL) -> Bool {
        let seg = url.pathComponents.filter { $0 != "/" }
        guard seg.first == "s", seg.count >= 3 else { return false }
        switch seg[1] {
        case "e", "v":
            let schoolId = seg[2]
            let viaId = (seg[1] == "v" && seg.count >= 4) ? seg[3] : nil
            Task {
                if let school = try? await AppDependencies.shared.container.getSchoolById
                    .invoke(id: schoolId) {
                    self.target = Target(school: school, viaId: viaId)
                }
            }
            return true
        case "q":
            // Invitación a quedada: guarda el token para que el join lo use
            // (salta FOLLOWERS; los "no mixto" siguen exigiendo género).
            let meetupId = seg[2]
            let token = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?.first(where: { $0.name == "i" })?.value
            PendingMeetupInvite.shared.set(meetupId: meetupId, token: token)
            target = Target(meetupId: meetupId)
            return true
        case "u":
            // Perfil compartido: /s/u/{username o uid} — el backend acepta ambos.
            target = Target(userHandle: seg[2])
            return true
        default:
            return false
        }
    }
}
