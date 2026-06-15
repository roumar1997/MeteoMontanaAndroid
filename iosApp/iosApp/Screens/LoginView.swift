import FirebaseAuth
import SwiftUI

/// Estado de sesión observable para SwiftUI. Se apoya en el listener nativo de
/// FirebaseAuth (no en el StateFlow de Kotlin) para el gating de UI, mientras
/// que el `AuthService` compartido sigue alimentando el token del HttpClient.
@MainActor
final class SessionStore: ObservableObject {
    @Published var user: FirebaseAuth.User?

    private var handle: AuthStateDidChangeListenerHandle?

    init() {
        user = Auth.auth().currentUser
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.user = user
        }
    }

    deinit {
        if let handle { Auth.auth().removeStateDidChangeListener(handle) }
    }
}

/// Pantalla de cuenta. Si no hay sesión: botón "Continuar con Google". Si la
/// hay: muestra el perfil y permite cerrar sesión. Réplica funcional del
/// LoginScreen.kt de Android (que usa CredentialManager + Firebase).
struct LoginView: View {
    @StateObject private var session = SessionStore()
    @Environment(\.dismiss) private var dismiss
    @State private var working = false
    @State private var errorText: String?

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        VStack(spacing: 20) {
            header
            if let user = session.user {
                signedIn(user)
            } else {
                signedOut
            }
            if let err = errorText {
                Text(err).font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                    .multilineTextAlignment(.center)
            }
            Spacer()
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private var header: some View {
        HStack {
            Text("Cuenta").font(Cumbre.serif(34, .bold)).foregroundStyle(Cumbre.ink)
            Spacer()
            Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
        }
        .padding(.top, 8)
    }

    private func signedIn(_ user: FirebaseAuth.User) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "person.crop.circle.fill")
                .font(.system(size: 56)).foregroundStyle(Cumbre.ink3)
            Text(user.displayName ?? "Sin nombre")
                .font(.system(size: 18, weight: .semibold)).foregroundStyle(Cumbre.ink)
            if let email = user.email {
                Text(email).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            }
            Button {
                authBridge.signOut {}
            } label: {
                Text("CERRAR SESIÓN").font(Cumbre.mono(12, .bold)).tracking(0.8)
                    .foregroundStyle(Cumbre.ink)
                    .padding(.vertical, 12).padding(.horizontal, 20)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.top, 8)
        }
        .padding(.top, 24)
    }

    private var signedOut: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.circle").font(.system(size: 56)).foregroundStyle(Cumbre.ink3)
            Text("Inicia sesión para acceder a tus favoritas, notas y perfil.")
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
            Button {
                Task { await signIn() }
            } label: {
                HStack(spacing: 8) {
                    if working { ProgressView().tint(.white) }
                    Text("CONTINUAR CON GOOGLE").font(Cumbre.mono(12, .bold)).tracking(0.8)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(Cumbre.terra)
            }
            .buttonStyle(.plain)
            .disabled(working)
            .padding(.top, 8)
        }
        .padding(.top, 24)
    }

    private func signIn() async {
        working = true; errorText = nil
        do { try await authBridge.signInWithGoogle() }
        catch { errorText = error.localizedDescription }
        working = false
    }
}
