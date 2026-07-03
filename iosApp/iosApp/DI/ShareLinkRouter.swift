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
        let school: School
        let viaId: String?
        var id: String { school.id + (viaId ?? "") }
    }

    @Published var target: Target? = nil

    /// True si la URL era nuestra (consumida); false → que la maneje otro.
    func handle(_ url: URL) -> Bool {
        let seg = url.pathComponents.filter { $0 != "/" }
        guard seg.first == "s", seg.count >= 3 else { return false }
        let schoolId: String
        var viaId: String? = nil
        switch seg[1] {
        case "e":
            schoolId = seg[2]
        case "v":
            schoolId = seg[2]
            viaId = seg.count >= 4 ? seg[3] : nil
        default:
            return false
        }
        Task {
            if let school = try? await AppDependencies.shared.container.getSchoolById
                .invoke(id: schoolId) {
                self.target = Target(school: school, viaId: viaId)
            }
        }
        return true
    }
}
