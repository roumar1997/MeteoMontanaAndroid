import Foundation
import UIKit
import UserNotifications
import FirebaseMessaging
import Shared

/// Notificaciones push (APNs → FCM) para iOS.
///
/// ⚠️ CÓDIGO LISTO PERO DESACTIVADO (`enabled = false`). Para activarlo antes de
/// publicar (requiere cuenta Apple Developer de pago):
///   1. Crear una APNs Auth Key (.p8) en developer.apple.com y subirla a Firebase
///      (proyecto climbingteams → Cloud Messaging → APNs).
///   2. En project.yml: descomentar la capability "aps-environment" (entitlement)
///      y añadir UIBackgroundModes: [remote-notification]. Poner
///      FirebaseAppDelegateProxyEnabled: true (o reenviar el apnsToken a Messaging
///      desde un AppDelegate en didRegisterForRemoteNotificationsWithDeviceToken).
///   3. Aquí: poner `enabled = true`.
/// Hasta entonces no se registra nada (las notificaciones in-app/campana sí van).
final class PushManager: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    static let shared = PushManager()
    /// Interruptor maestro. Mientras sea false no se pide permiso ni se registra.
    static let enabled = false

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
