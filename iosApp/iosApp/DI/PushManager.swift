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

    func registerIfEnabled() {
        guard Self.enabled else { return }
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    /// El token FCM → backend (PUT /api/me/fcm-token), igual que Android.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task {
            try? await AppDependencies.shared.container.updateFcmToken.invoke(req: FcmTokenRequest(token: token))
        }
    }

    /// Mostrar la notificación aunque la app esté en primer plano.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .badge, .sound])
    }
}
