import FirebaseAuth
import FirebaseCore
import Foundation
import GoogleSignIn
import Shared
import UIKit

/// Implementación Swift del bridge `IosAuthBridge` (definido en Kotlin
/// iosMain). Usa FirebaseAuth. El lado Kotlin (`IosAuthService`) la envuelve
/// para cumplir el port `AuthService` (StateFlow + suspend).
///
/// El flujo de login con Google (`signInWithGoogle`) NO es parte del port
/// (igual que en Android, donde lo dispara la UI): lo llama `LoginView`.
final class AuthBridge: NSObject, IosAuthBridge {

    private var stateHandle: AuthStateDidChangeListenerHandle?

    func currentUid() -> String? { Auth.auth().currentUser?.uid }
    func currentEmail() -> String? { Auth.auth().currentUser?.email }
    func currentDisplayName() -> String? { Auth.auth().currentUser?.displayName }

    func currentIdToken(forceRefresh: Bool, callback: @escaping (String?) -> Void) {
        guard let user = Auth.auth().currentUser else { callback(nil); return }
        user.getIDTokenForcingRefresh(forceRefresh) { token, _ in callback(token) }
    }

    func signOut(callback: @escaping () -> Void) {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        callback()
    }

    func observeAuthState(callback: @escaping () -> Void) {
        // Quitamos un listener previo por si se re-registra.
        if let h = stateHandle { Auth.auth().removeStateDidChangeListener(h) }
        stateHandle = Auth.auth().addStateDidChangeListener { _, _ in callback() }
    }

    // MARK: - Login con Google (lo dispara la UI, no el port)

    enum SignInError: LocalizedError {
        case noClientID, noPresenter, noToken
        var errorDescription: String? {
            switch self {
            case .noClientID: return "Falta CLIENT_ID en GoogleService-Info.plist"
            case .noPresenter: return "No hay pantalla desde la que presentar el login"
            case .noToken: return "Google no devolvió un token válido"
            }
        }
    }

    /// Lanza el flujo de Google Sign-In y, con la credencial, hace login en
    /// Firebase. Equivale al CredentialManager + signInWithCredential de Android.
    @MainActor
    func signInWithGoogle() async throws {
        guard let clientID = FirebaseApp.app()?.options.clientID else { throw SignInError.noClientID }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let presenter = Self.topViewController() else { throw SignInError.noPresenter }

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else { throw SignInError.noToken }
        let credential = GoogleAuthProvider.credential(
            withIDToken: idToken,
            accessToken: result.user.accessToken.tokenString
        )
        try await Auth.auth().signIn(with: credential)
    }

    /// View controller frontal para presentar el flujo de Google (SwiftUI).
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
