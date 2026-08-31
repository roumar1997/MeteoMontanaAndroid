import SwiftUI
import Shared

// Pantalla Comunidad: ranking de mayores contribuidores a la guía.
// Espejo de CommunityScreen.kt de Android.

/// Un mes concreto (año+mes) o "total" histórico — pestañas del ranking.
enum RankingScope: Hashable {
    case total
    case month(year: Int, month: Int)

    var label: String {
        switch self {
        case .total: return "Total"
        case .month(let y, let m):
            let df = DateFormatter()
            df.locale = Locale(identifier: "es_ES")
            df.dateFormat = "LLL"
            let cal = Calendar(identifier: .gregorian)
            let date = cal.date(from: DateComponents(year: y, month: m)) ?? Date()
            return df.string(from: date).capitalized
        }
    }
}

@MainActor
final class CommunityViewModel: ObservableObject {
    @Published var contributors: [TopContributor] = []
    @Published var loading = true
    @Published var error: String? = nil
    @Published var scope: RankingScope = .total

    /// Total + los últimos 6 meses (el actual incluido, en curso).
    let availableScopes: [RankingScope] = {
        let cal = Calendar(identifier: .gregorian)
        let now = Date()
        var scopes: [RankingScope] = [.total]
        for i in 0..<6 {
            if let d = cal.date(byAdding: .month, value: -i, to: now) {
                let c = cal.dateComponents([.year, .month], from: d)
                if let y = c.year, let m = c.month { scopes.append(.month(year: y, month: m)) }
            }
        }
        return scopes
    }()

    private let getTopContributors: GetTopContributorsUseCase
    init(getTopContributors: GetTopContributorsUseCase =
            AppDependencies.shared.container.getTopContributors) {
        self.getTopContributors = getTopContributors
    }

    func selectScope(_ s: RankingScope) {
        guard s != scope else { return }
        scope = s
        Task { await load() }
    }

    func load() async {
        loading = true
        error = nil
        do {
            switch scope {
            case .total:
                contributors = try await getTopContributors.invoke(limit: 20, year: nil, month: nil)
            case .month(let y, let m):
                contributors = try await getTopContributors.invoke(limit: 10, year: KotlinInt(int: Int32(y)), month: KotlinInt(int: Int32(m)))
            }
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
                .padding(.horizontal, 16).padding(.top, 10)
            scopeRow
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
                    LazyVStack(spacing: 8) {
                        ForEach(Array(vm.contributors.enumerated()), id: \.element.uid) { index, c in
                            NavigationLink(destination: PublicProfileView(uid: c.uid)) {
                                ContributorRow(rank: index + 1, contributor: c)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 12).padding(.top, 10)
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Comunidad")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.load() }
    }

    /// Chips Total / últimos 6 meses — estilo "mochila" (celda plana, borde
    /// fino, activa con borde interior terra). Meses pasados dan el top 10
    /// de ESE mes; el actual, en vivo.
    private var scopeRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(vm.availableScopes, id: \.self) { s in
                    let active = vm.scope == s
                    Button { vm.selectScope(s) } label: {
                        Text(s.label)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(active ? Cumbre.terra : Cumbre.ink)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Cumbre.paper)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(
                                active ? Cumbre.terra : Cumbre.rule,
                                lineWidth: active ? 1.5 : 0.5))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
        }
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
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .contentShape(Rectangle())
    }
}
