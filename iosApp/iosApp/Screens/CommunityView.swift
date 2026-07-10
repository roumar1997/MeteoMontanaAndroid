import SwiftUI
import Shared

// Pantalla Comunidad: ranking de mayores contribuidores a la guía.
// Espejo de CommunityScreen.kt de Android.

@MainActor
final class CommunityViewModel: ObservableObject {
    @Published var contributors: [TopContributor] = []
    @Published var loading = true
    @Published var error: String? = nil

    private let getTopContributors: GetTopContributorsUseCase
    init(getTopContributors: GetTopContributorsUseCase =
            AppDependencies.shared.container.getTopContributors) {
        self.getTopContributors = getTopContributors
    }

    func load() async {
        loading = true
        error = nil
        do {
            contributors = try await getTopContributors.invoke(limit: 20)
        } catch {
            self.error = "No se pudo cargar el ranking"
        }
        loading = false
    }
}

struct CommunityView: View {
    @StateObject private var vm = CommunityViewModel()

    var body: some View {
        VStack(spacing: 0) {
            Text("MAYORES CONTRIBUIDORES")
                .font(Cumbre.mono(10, .bold)).kerning(1.8)
                .foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 10)
            Divider().overlay(Cumbre.rule)

            if vm.loading {
                Spacer()
                ProgressView()
                Spacer()
            } else if let err = vm.error {
                Spacer()
                VStack(spacing: 12) {
                    Text(err).font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    Button("REINTENTAR") { Task { await vm.load() } }
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                }
                Spacer()
            } else if vm.contributors.isEmpty {
                Spacer()
                Text("Aún no hay contribuciones aprobadas.\n¡Sé el primero en proponer algo!")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink3)
                    .multilineTextAlignment(.center)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(vm.contributors.enumerated()), id: \.element.uid) { index, c in
                            NavigationLink(destination: PublicProfileView(uid: c.uid)) {
                                ContributorRow(rank: index + 1, contributor: c)
                            }
                            .buttonStyle(.plain)
                            Divider().overlay(Cumbre.rule)
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Comunidad")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
    }
}

private struct ContributorRow: View {
    let rank: Int
    let contributor: TopContributor

    private var medal: String? {
        switch rank { case 1: return "🥇"; case 2: return "🥈"; case 3: return "🥉"
        default: return nil }
    }

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let medal {
                    Text(medal).font(.system(size: 22))
                } else {
                    Text("\(rank)").font(Cumbre.mono(14, .bold)).foregroundStyle(Cumbre.ink3)
                }
            }
            .frame(width: 32)

            AvatarCircle(url: contributor.photoUrl, size: 40)

            VStack(alignment: .leading, spacing: 2) {
                Text(contributor.displayName ?? contributor.username.map { "@" + $0 } ?? "Usuario")
                    .font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                if let u = contributor.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(contributor.approvedCount)")
                    .font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.terra)
                Text("APORTES").font(Cumbre.mono(9, .bold)).kerning(1.2)
                    .foregroundStyle(Cumbre.ink3)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}
