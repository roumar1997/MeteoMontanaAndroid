import SwiftUI
import Shared

@MainActor
final class AccountViewModel: ObservableObject {
    @Published var profile: PrivateProfile?
    @Published var loading = true

    private let getMyProfile: GetMyProfileUseCase
    init(getMyProfile: GetMyProfileUseCase = AppDependencies.shared.container.getMyProfile) {
        self.getMyProfile = getMyProfile
    }

    func load() async {
        loading = true
        profile = try? await getMyProfile.invoke()  // JIT provisioning en el backend
        loading = false
    }
}

/// Pantalla de cuenta/perfil — se abre desde el icono de persona del header.
/// Muestra el perfil privado (avatar, nombre, usuario, bio, grado, badges) y
/// permite cerrar sesión. Espejo parcial de ProfileScreen.kt; el diario,
/// seguidores y edición de foto llegarán con sus bridges.
struct AccountView: View {
    @StateObject private var vm = AccountViewModel()
    @Environment(\.dismiss) private var dismiss

    private let authBridge = AppDependencies.shared.authBridge

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    avatar
                    names
                    if let p = vm.profile, let bio = p.bio, !bio.isEmpty {
                        Text(bio)
                            .font(.system(size: 15))
                            .foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    badges
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    menuLinks
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    signOutButton
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 24)
                .padding(.horizontal, 24)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Cuenta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
            .task { await vm.load() }
        }
    }

    private var avatar: some View {
        let url = vm.profile?.photoUrl.flatMap { URL(string: $0) }
        return Group {
            if let url {
                AsyncImage(url: url) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Image(systemName: "person.crop.circle.fill")
                        .resizable().foregroundStyle(Cumbre.ink3)
                }
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .resizable().foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: 88, height: 88)
        .clipShape(Circle())
        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
    }

    private var names: some View {
        VStack(spacing: 2) {
            Text(displayName).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            if let u = vm.profile?.username, !u.isEmpty {
                Text("@\(u)").font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink3)
            }
            if let e = email { Text(e).font(.system(size: 13)).foregroundStyle(Cumbre.ink2) }
        }
    }

    private var badges: some View {
        HStack(spacing: 8) {
            if let g = vm.profile?.topGrade, !g.isEmpty { badge("TOPE \(g.uppercased())", Cumbre.terra) }
            if vm.profile?.isAdmin == true { badge("ADMIN", Cumbre.ink) }
            if vm.profile?.isPremium == true { badge("PREMIUM", Cumbre.ok) }
        }
    }

    private func badge(_ t: String, _ c: Color) -> some View {
        Text(t).font(Cumbre.mono(10, .bold)).tracking(1.0)
            .foregroundStyle(c)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .overlay(Rectangle().stroke(c, lineWidth: 1))
    }

    private var menuLinks: some View {
        VStack(spacing: 0) {
            menuRow("Mis propuestas", "mappin.and.ellipse", MySubmissionsView())
            menuRow("Mis contribuciones", "square.and.pencil", MyContributionsView())
            menuRow("Solicitudes de seguimiento", "person.badge.plus", FollowRequestsView())
        }
    }

    private func menuRow<Dest: View>(_ title: String, _ icon: String, _ dest: Dest) -> some View {
        NavigationLink(destination: dest) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 16)).foregroundStyle(Cumbre.terra).frame(width: 24)
                Text(title).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var signOutButton: some View {
        Button {
            authBridge.signOut {}
            dismiss()
        } label: {
            Text("CERRAR SESIÓN").font(Cumbre.mono(12, .bold)).tracking(0.8)
                .foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).frame(maxWidth: .infinity)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var displayName: String {
        vm.profile?.displayName ?? authBridge.currentDisplayName() ?? "Sin nombre"
    }
    private var email: String? {
        vm.profile?.email ?? authBridge.currentEmail()
    }
}
