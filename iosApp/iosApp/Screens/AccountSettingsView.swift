import SwiftUI
import Shared
import PhotosUI

// Ajustes de la cuenta.

/// Ajustes del perfil: agrupa cuenta / mi actividad / preferencias + cerrar
/// sesión / borrar cuenta. Es una PANTALLA (NavigationLink desde el ⚙️), no un
/// sheet — así cada sub-ajuste se empuja y "atrás" vuelve aquí. Espejo del
/// ProfileSettingsScreen de Android.
struct AccountSettingsView: View {
    @ObservedObject var vm: AccountViewModel
    @Environment(\.dismiss) private var dismiss
    private let authBridge = AppDependencies.shared.authBridge
    @State private var showDeleteConfirm = false
    @State private var showHintsReset = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                sectionLabel(NSLocalizedString("profile_section_account", comment: ""))
                menuRow(NSLocalizedString("profile_edit", comment: ""), "pencil", EditProfileView())

                sectionLabel(NSLocalizedString("profile_section_activity", comment: ""))
                menuRow(L("Escuelas guardadas (offline)"), "arrow.down.circle", SavedSchoolsView())
                menuRow(L("Mis contribuciones"), "mappin.and.ellipse", MyContributionsUnifiedView())
                if vm.profile?.isPublic == false {
                    menuRow(L("Solicitudes de seguimiento"), "person.badge.plus", FollowRequestsView())
                }

                sectionLabel(NSLocalizedString("profile_section_prefs", comment: ""))
                menuRow(NSLocalizedString("profile_weather_alert", comment: ""), "bell.badge", WeekendAlertView())
                LanguageSettingRow()
                GuidesToggleRow().padding(.vertical, 8)
                Button {
                    FirstTimeHint.resetAll()
                    showHintsReset = true
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "questionmark.circle").font(.system(size: 16)).foregroundStyle(Cumbre.terra).frame(width: 24)
                        Text(NSLocalizedString("profile_show_hints", comment: "")).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                        Spacer()
                    }.padding(.vertical, 12).contentShape(Rectangle())
                }.buttonStyle(.plain)

                Divider().overlay(Cumbre.rule).padding(.vertical, 8)
                Button {
                    AppDependencies.shared.container.localCacheCleaner?.clearServerCaches()
                    authBridge.signOut {}
                    dismiss()
                } label: {
                    Text(NSLocalizedString("profile_logout", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.8)
                        .foregroundStyle(Cumbre.ink).padding(.vertical, 14).frame(maxWidth: .infinity)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
                Button { showDeleteConfirm = true } label: {
                    Text(NSLocalizedString("profile_delete_account", comment: ""))
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                        .padding(.vertical, 8).frame(maxWidth: .infinity)
                }.buttonStyle(.plain)
                selloDeVersion
            }
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 24)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle(NSLocalizedString("profile_settings", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
        .alert("¿Eliminar tu cuenta?", isPresented: $showDeleteConfirm) {
            Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                Task { await vm.deleteMyAccount(); dismiss() }
            }
        } message: {
            Text("Se borrarán tu perfil, diario, favoritas, seguimientos y propuestas de forma permanente. Esta acción no se puede deshacer.")
        }
        .alert("Pistas reactivadas", isPresented: $showHintsReset) {
            Button("Vale", role: .cancel) {}
        } message: {
            Text("Entra de nuevo en cada pantalla para volver a ver las pistas.")
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased()).font(Cumbre.mono(10, .bold)).tracking(1.4)
            .foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 16).padding(.bottom, 2)
    }

    /// Qué build es este. Sin esto, "no me ha llegado el arreglo" y "no lo has
    /// arreglado" son indistinguibles.
    private var selloDeVersion: some View {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        let entorno = AppConfig.apiBaseUrl.contains("staging") ? " · staging" : ""
        return Text(L("Cumbre %@ (build %@) · %@%@", v, b, BuildFlags.buildStamp, entorno))
            .font(Cumbre.mono(11))
            .foregroundStyle(Cumbre.ink3)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
    }

    private func menuRow<Dest: View>(_ title: String, _ icon: String, _ dest: Dest) -> some View {
        NavigationLink(destination: dest) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 16)).foregroundStyle(Cumbre.terra).frame(width: 24)
                Text(title).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.vertical, 12).contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

// MARK: - Diario propio: stats pulsables + listas con borrar (refresco en vivo)
