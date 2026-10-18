import SwiftUI
import Shared

/// Recompensas bajo el nombre del perfil (§F del plan): título, insignias y nivel. En el tuyo (uid nil) tocar abre la
/// hoja para elegir título y si eres viero o bloquero, y la primera vez sale una tarjeta que lo pregunta.
/// Espejo de RewardsBlock.kt (Android). Las ilustraciones de Cumbri por nivel llegarán aparte.
@MainActor
final class RewardsModel: ObservableObject {
    @Published var rewards: UserRewards?
    private let c = AppDependencies.shared.container

    func load(uid: String?) async {
        if let uid { rewards = try? await c.getUserRewards.invoke(uid: uid) }
        else { rewards = try? await c.getMyRewards.invoke() }
    }

    func update(climberType: String?, titleLevel: Int?) async {
        if let r = try? await c.updateMyRewards.invoke(climberType: climberType,
                                                       titleLevel: titleLevel.map { KotlinInt(int: Int32($0)) }) {
            rewards = r
        }
    }
}

func rewardTitle(_ level: Int) -> String {
    switch level {
    case 0: return L("Recién llegado")
    case 1: return L("Colaborador")
    case 5: return L("Aportador")
    case 10: return L("Croquista")
    case 15: return L("Explorador de sectores")
    case 20: return L("De confianza")
    case 25: return L("Guardián de la roca")
    case 30: return L("Cartógrafo")
    case 35: return L("Equipador de la guía")
    case 40: return L("Veterano de Cumbre")
    case 45: return L("Maestro de topos")
    default: return L("Leyenda de Cumbre")
    }
}

private func climberTypeLabel(_ t: String?) -> String {
    switch t {
    case "ROUTES": return L("Viero")
    case "BOULDER": return L("Bloquero")
    case "BOTH": return L("Las dos")
    default: return L("Prefiero no decirlo")
    }
}

private let promptKey = "climber_type_prompt_done"

struct RewardsBlock: View {
    let uid: String?
    @StateObject private var model = RewardsModel()
    @State private var sheet = false
    @State private var promptDone = UserDefaults.standard.bool(forKey: promptKey)

    var body: some View {
        VStack(spacing: 6) {
            if let r = model.rewards {
                Button { if uid == nil { sheet = true } } label: { summary(r) }
                    .buttonStyle(.plain).disabled(uid != nil)
                if uid == nil && !promptDone && r.climberType == nil { prompt }
            }
        }
        .task { await model.load(uid: uid) }
        .sheet(isPresented: $sheet) {
            if let r = model.rewards { RewardsSheet(rewards: r, model: model) }
        }
    }

    private func summary(_ r: UserRewards) -> some View {
        VStack(spacing: 6) {
            Text(rewardTitle(Int(r.titleLevel))).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.terra)
            HStack(spacing: 6) {
                if let t = r.climberType { badge(climberTypeLabel(t), Cumbre.ink2) }
                if r.badges.contains("COLLABORATOR") { badge(L("Colaborador"), Cumbre.terra) }
                if r.badges.contains("TRUSTED") { badge(L("De confianza"), Cumbre.ok) }
            }
            if uid == nil, let next = r.nextLevel?.intValue {
                Text(L("%@ aportaciones aprobadas · siguiente nivel con %@", r.approved, next))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3).multilineTextAlignment(.center)
                let from = Int(r.level)
                ProgressView(value: min(1, max(0, Double(Int(r.approved) - from) / Double(max(1, next - from)))))
                    .tint(Cumbre.terraFill).frame(maxWidth: 220)
            }
        }
    }

    private func badge(_ text: String, _ color: Color) -> some View {
        Text(text.uppercased()).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(color)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .overlay(Capsule().stroke(color, lineWidth: 1))
    }

    private var prompt: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("¿VIERO O BLOQUERO?")).eyebrow(Cumbre.terra)
            Text(L("¿Escalas sobre todo vías o bloque? Cambia tu Cumbri del perfil. Es opcional y puedes cambiarlo cuando quieras tocando tu título."))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            HStack(spacing: 8) {
                ForEach(["ROUTES", "BOULDER", "BOTH"], id: \.self) { t in
                    Button {
                        Task { await model.update(climberType: t, titleLevel: nil) }
                        close()
                    } label: { chip(climberTypeLabel(t), on: false) }.buttonStyle(.plain)
                }
            }
            Button { close() } label: {
                Text(L("Ahora no").uppercased()).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
            }.buttonStyle(.plain)
        }
        .padding(14).frame(maxWidth: 420, alignment: .leading)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func close() {
        UserDefaults.standard.set(true, forKey: promptKey)
        promptDone = true
    }
}

private func chip(_ text: String, on: Bool) -> some View {
    Text(text).font(.system(size: 13, weight: .semibold)).foregroundStyle(on ? .white : Cumbre.ink)
        .padding(.horizontal, 12).padding(.vertical, 7)
        .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
        .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
}

/// Elegir el título (entre los desbloqueados) y si eres viero, bloquero o las dos.
private struct RewardsSheet: View {
    let rewards: UserRewards
    @ObservedObject var model: RewardsModel

    var body: some View {
        let r = model.rewards ?? rewards
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(L("¿VIERO O BLOQUERO?")).eyebrow(Cumbre.terra)
                FlowChips(items: ["ROUTES", "BOULDER", "BOTH", ""]) { t in
                    Button { Task { await model.update(climberType: t, titleLevel: nil) } } label: {
                        chip(climberTypeLabel(t.isEmpty ? nil : t), on: (r.climberType ?? "") == t)
                    }.buttonStyle(.plain)
                }
                Text(L("TU TÍTULO")).eyebrow(Cumbre.terra)
                Text(L("Desbloqueas títulos con tus aportaciones aprobadas (llevas %@).", r.approved))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                FlowChips(items: r.unlockedLevels.map { Int(truncating: $0) }) { l in
                    Button { Task { await model.update(climberType: nil, titleLevel: l) } } label: {
                        chip(rewardTitle(l), on: Int(r.titleLevel) == l)
                    }.buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}

/// Chips que saltan de línea cuando no caben.
private struct FlowChips<Item: Hashable, Content: View>: View {
    let items: [Item]
    @ViewBuilder let content: (Item) -> Content
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(items, id: \.self) { content($0) }
        }
    }
}
