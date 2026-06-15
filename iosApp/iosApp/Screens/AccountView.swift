import SwiftUI

/// Pantalla de cuenta — se abre desde el icono de persona del header de la
/// lista. Muestra el usuario con sesión iniciada y permite cerrar sesión.
/// (Versión mínima; la `ProfileScreen` completa de Android — diario, favoritas,
/// seguidores, ajustes — se replicará en próximas sesiones.)
struct AccountView: View {
    @Environment(\.dismiss) private var dismiss

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(Cumbre.ink3)
                    .padding(.top, 32)
                Text(authBridge.currentDisplayName() ?? "Sin nombre")
                    .font(Cumbre.serif(22, .bold))
                    .foregroundStyle(Cumbre.ink)
                if let email = authBridge.currentEmail() {
                    Text(email)
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink2)
                }

                Spacer()

                Button {
                    authBridge.signOut {}
                    // El SessionStore detecta el cambio y la raíz vuelve a LoginView.
                    dismiss()
                } label: {
                    Text("CERRAR SESIÓN")
                        .font(Cumbre.mono(12, .bold))
                        .tracking(0.8)
                        .foregroundStyle(Cumbre.ink)
                        .padding(.vertical, 14)
                        .frame(maxWidth: .infinity)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Cuenta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }
}
