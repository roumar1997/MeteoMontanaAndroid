import AuthenticationServices
import CryptoKit
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
    /// Foto de la cuenta de Google (respaldo cuando el backend no tiene photoUrl).
    func currentPhotoUrl() -> String? { Auth.auth().currentUser?.photoURL?.absoluteString }

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

    // MARK: - Login con Apple (Sign in with Apple → credencial Firebase)
    // Requisito de la App Store por ofrecer login de Google. Necesita:
    //  - Capability "Sign in with Apple" en el target (entitlement applesignin).
    //  - Apple habilitado como proveedor en Firebase Auth (consola).
    //  - Cuenta Apple Developer de pago (las firmas gratuitas/AltStore NO
    //    soportan esta capability → el botón fallará al sidecargar).

    private var appleCoordinator: AppleSignInCoordinator?

    @MainActor
    func signInWithApple() async throws {
        let nonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)

        let coordinator = AppleSignInCoordinator()
        appleCoordinator = coordinator
        let authorization = try await coordinator.perform(request: request)
        defer { appleCoordinator = nil }

        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = credential.identityToken,
            let idToken = String(data: tokenData, encoding: .utf8)
        else { throw SignInError.noToken }

        let firebaseCredential = OAuthProvider.appleCredential(
            withIDToken: idToken, rawNonce: nonce, fullName: credential.fullName)
        try await Auth.auth().signIn(with: firebaseCredential)
    }

    // Nonce aleatorio + SHA256 (recomendado por Firebase/Apple para evitar replay).
    private static func randomNonce(length: Int = 32) -> String {
        let chars = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            _ = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            if random < UInt8(chars.count) { result.append(chars[Int(random)]); remaining -= 1 }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Envuelve `ASAuthorizationController` (basado en delegate) en `async`.
private final class AppleSignInCoordinator: NSObject,
    ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {

    private var continuation: CheckedContinuation<ASAuthorization, Error>?

    @MainActor
    func perform(request: ASAuthorizationAppleIDRequest) async throws -> ASAuthorization {
        try await withCheckedThrowingContinuation { cont in
            self.continuation = cont
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        continuation?.resume(returning: authorization); continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithError error: Error) {
        continuation?.resume(throwing: error); continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .keyWindow ?? ASPresentationAnchor()
    }
}
