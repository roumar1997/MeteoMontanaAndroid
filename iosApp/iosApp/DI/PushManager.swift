import Foundation
import UIKit
import UserNotifications
import FirebaseMessaging
import Shared

/// Notificaciones push (APNs → FCM) para iOS.
///
/// ACTIVO desde 2026-07-03: capability Push en el App ID, APNs key en Firebase
/// (producción) y entitlement aps-environment en project.yml.
final class PushManager: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    static let shared = PushManager()
    /// Interruptor maestro. Mientras sea false no se pide permiso ni se registra.
    static let enabled = true

    /// Debe llamarse desde `application(_:didFinishLaunchingWithOptions:)`, NO
    /// desde el `.onAppear` de SwiftUI: si el usuario abre la app tocando una
    /// notificación con la app cerrada del todo, iOS entrega el toque al
    /// delegate de `UNUserNotificationCenter` casi en el instante del arranque
    /// — puesto más tarde (en onAppear, que llega después de construir la
    /// primera vista), el aviso se pierde y la app abre en Escuelas en vez de
    /// en el chat/perfil. Con la app en segundo plano sí funcionaba porque el
    /// delegate ya llevaba puesto desde antes (bug cazado 2026-09-06, solo en
    /// iOS: Android no tiene esta separación registro/permiso).
    func setDelegate() {
        guard Self.enabled else { return }
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
    }

    func registerIfEnabled() {
        guard Self.enabled else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    /// El token FCM → backend (PUT /api/me/fcm-token), igual que Android.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task {
            await attempt("updateFcmToken") {
                try await deps.updateFcmToken.invoke(req: FcmTokenRequest(token: token))
            }
        }
    }

    /// Mostrar la notificación aunque la app esté en primer plano.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .badge, .sound])
    }

    /// TOCAR la notificación → navegar al destino según su `targetType`
    /// (antes no había handler: cualquier notificación abría Escuelas).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        let type = info["targetType"] as? String
        let id = info["targetId"] as? String
        DispatchQueue.main.async {
            switch type {
            case "admin_reports":
                ShareLinkRouter.shared.target = ShareLinkRouter.Target(openAdminReports: true)
            case "admin_contributions":
                ShareLinkRouter.shared.target = ShareLinkRouter.Target(openAdminContributions: true)
            case "user":
                if let id, !id.isEmpty { ShareLinkRouter.shared.target = ShareLinkRouter.Target(userHandle: id) }
            case "meetup":
                if let id, !id.isEmpty { ShareLinkRouter.shared.target = ShareLinkRouter.Target(meetupId: id) }
            case "feed_post":
                // Actividad del feed Comunidad → detalle del post.
                if let id, !id.isEmpty { ShareLinkRouter.shared.target = ShareLinkRouter.Target(feedPostId: id) }
            case "chat", "message":
                // Mensaje 1-a-1 → conversación con el remitente (targetId = su uid;
                // el title del push es su nombre). Antes caía en default → no navegaba.
                if let id, !id.isEmpty {
                    let name = response.notification.request.content.title
                    ShareLinkRouter.shared.target =
                        ShareLinkRouter.Target(chatPeerUid: id, chatPeerName: name)
                }
            case "group":
                // Mensaje de grupo → chat del grupo (targetId = convId).
                if let id, !id.isEmpty {
                    let name = response.notification.request.content.title
                    ShareLinkRouter.shared.target =
                        ShareLinkRouter.Target(groupChatId: id, groupChatName: name)
                }
            case "school", "school_detail":
                if let id, !id.isEmpty {
                    Task {
                        if let s = await attempt("getSchoolById(push)", { try await deps.getSchoolById.invoke(id: id) }) {
                            ShareLinkRouter.shared.target = ShareLinkRouter.Target(school: s)
                        }
                    }
                }
            default: break
            }
        }
        completionHandler()
    }
}
