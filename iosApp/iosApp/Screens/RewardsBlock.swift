import SwiftUI
import Shared

/// Recompensas bajo el nombre del perfil (§F del plan): título, insignias y nivel. En el tuyo (uid nil) tocar abre la
/// hoja para elegir título y si eres viero o bloquero, y la primera vez sale una tarjeta que lo pregunta.
/// Espejo de RewardsBlock.kt (Android). Las ilustraciones de Cumbri por nivel llegarán aparte.
@MainActor
final class RewardsModel: ObservableObject {
    @Published var rewards: UserRewards?
    /// Cuánta gente tiene cada logro (nil = sin red o aún no se sabe: se enseña solo la dificultad).
    @Published var rarity: AchievementRarity?
    /// Mis logros ya sincronizados, para elegir el destacado (nil = aún no cargados).
    @Published var mineIds: Set<String>?
    private let c = AppDependencies.shared.container

    func load(uid: String?) async {
        if let uid { rewards = try? await c.getUserRewards.invoke(uid: uid) }
        else { rewards = try? await c.getMyRewards.invoke() }
        rarity = try? await c.getAchievementRarity.invoke()
    }

    func loadMineIds() async {
        if let ids = try? await c.getMyAchievementIds.invoke() { mineIds = Set(ids) }
    }

    func update(climberType: String?, titleLevel: Int?, featured: String? = nil, titleHidden: Bool? = nil) async {
        if let r = try? await c.updateMyRewards.invoke(climberType: climberType,
                                                       titleLevel: titleLevel.map { KotlinInt(int: Int32($0)) },
                                                       featuredAchievement: featured,
                                                       titleHidden: titleHidden.map { KotlinBoolean(bool: $0) }) {
            rewards = r
            UserBadgeStore.shared.refresh(r.uid)   // la aureola sigue al título elegido
        }
    }
}

func rewardTitle(_ level: Int) -> String {
    switch level {
    case 0: return L("Aspirante")
    case 1: return L("Primer Agarre")
    case 5: return L("Abrecaminos")
    case 10: return L("Cartógrafo de Roca")
    case 15: return L("Rastreador de Sectores")
    case 20: return L("Guardián de la Roca")
    case 25: return L("Señor de los Topos")
    case 30: return L("Domador de Muros")
    case 35: return L("Tejedor de Caminos")
    case 40: return L("Sabio de la Roca")
    case 45: return L("Viento de Granito")
    default: return L("Señor de la Cumbre")
    }
}

/// Cada título tiene su color y su icono (Álvaro, 2026-10-18: «que cada título tenga su propio estilo»).
func rewardStyle(_ level: Int) -> (color: Color, symbol: String) {
    switch level {
    case 0: return (Color(red: 0.45, green: 0.47, blue: 0.5), "leaf.fill")
    case 1: return (Color(red: 0.80, green: 0.36, blue: 0.20), "hand.raised.fill")
    case 5: return (Color(red: 0.20, green: 0.55, blue: 0.30), "figure.hiking")
    case 10: return (Color(red: 0.16, green: 0.45, blue: 0.75), "map.fill")
    case 15: return (Color(red: 0.05, green: 0.55, blue: 0.55), "scope")
    case 20: return (Color(red: 0.36, green: 0.35, blue: 0.62), "shield.fill")
    case 25: return (Color(red: 0.85, green: 0.48, blue: 0.10), "scribble.variable")
    case 30: return (Color(red: 0.72, green: 0.18, blue: 0.22), "mountain.2.fill")
    case 35: return (Color(red: 0.27, green: 0.31, blue: 0.70), "point.3.connected.trianglepath.dotted")
    case 40: return (Color(red: 0.55, green: 0.25, blue: 0.62), "book.fill")
    case 45: return (Color(red: 0.30, green: 0.45, blue: 0.55), "wind")
    default: return (Color(red: 0.72, green: 0.53, blue: 0.04), "crown.fill")
    }
}

/// Píldora de un título con su color e icono.
struct TitlePill: View {
    let level: Int
    var on = false
    var locked = false
    /// En la hoja de elegir: el título elegido lleva check, borde grueso y sombra (en el perfil, no).
    var showCheck = false
    var body: some View {
        let st = rewardStyle(level)
        HStack(spacing: 6) {
            Image(systemName: locked ? "lock.fill" : (on && showCheck ? "checkmark.circle.fill" : st.symbol)).font(.system(size: 12, weight: .semibold))
            Text(rewardTitle(level)).font(.system(size: 14, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.8)
        }
        .foregroundStyle(on ? Color.white : (locked ? Cumbre.ink3 : st.color))
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(on ? st.color : st.color.opacity(locked ? 0.04 : 0.10), in: Capsule())
        .overlay(Capsule().stroke(st.color.opacity(locked ? 0.25 : (on ? 1 : 0.7)), lineWidth: on && showCheck ? 3 : 1))
        .shadow(color: on && showCheck ? st.color.opacity(0.45) : .clear, radius: 6, y: 2)
    }
}

/// El título. El nivel 50 («Señor de la Cumbre») se nota a la legua: oro con brillo que recorre las letras, destellos a
/// los lados y un borde dorado. El resto, en terracota como siempre (Álvaro, 2026-10-18).
private struct RewardTitle: View {
    let level: Int
    @State private var shine = false
    private let gold = [Color(red: 0.72, green: 0.53, blue: 0.04), Color(red: 1, green: 0.84, blue: 0.29),
                        Color(red: 1, green: 0.96, blue: 0.76), Color(red: 1, green: 0.84, blue: 0.29),
                        Color(red: 0.72, green: 0.53, blue: 0.04)]

    var body: some View {
        if level < 50 {
            TitlePill(level: level)
        } else {
            let g = LinearGradient(colors: gold, startPoint: shine ? .trailing : .leading, endPoint: shine ? UnitPoint(x: 2, y: 1) : UnitPoint(x: 1, y: 1))
            HStack(spacing: 8) {
                Text("✦").font(.system(size: 14)).foregroundStyle(g)
                Text(rewardTitle(level)).font(.system(size: 20, weight: .bold)).foregroundStyle(g)
                    .shadow(color: Color(red: 1, green: 0.7, blue: 0).opacity(0.55), radius: 8)
                Text("✦").font(.system(size: 14)).foregroundStyle(g)
            }
            .padding(.horizontal, 16).padding(.vertical, 7)
            .overlay(Capsule().stroke(g, lineWidth: 1.5))
            .onAppear { withAnimation(.linear(duration: 2.6).repeatForever(autoreverses: true)) { shine = true } }
        }
    }
}

/// «#RRGGBB» → Color (nil si no es válido).
func colorFromHex(_ hex: String?) -> Color? {
    guard var h = hex?.trimmingCharacters(in: .whitespaces), h.hasPrefix("#"), h.count == 7 else { return nil }
    h.removeFirst()
    guard let v = Int(h, radix: 16) else { return nil }
    return Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
}

/// Color → «#RRGGBB» (para guardar el que se elige en el selector).
func hexFromColor(_ color: Color) -> String {
    var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
    UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
    return String(format: "#%02X%02X%02X", Int(round(max(0, min(1, r)) * 255)), Int(round(max(0, min(1, g)) * 255)),
                  Int(round(max(0, min(1, b)) * 255)))
}

/// Título especial puesto por un admin (p. ej. «Reina Hada»): brilla como «Señor de la Cumbre», con destellos y borde,
/// pero en el color que eligió el admin (Álvaro, 2026-10-19).
struct SpecialTitleView: View {
    let text: String
    let colorHex: String?
    @State private var shine = false

    var body: some View {
        let base = colorFromHex(colorHex) ?? Color(red: 0.72, green: 0.53, blue: 0.04)
        let colors = [base.opacity(0.75), base, Color.white.opacity(0.9), base, base.opacity(0.75)]
        let g = LinearGradient(colors: colors, startPoint: shine ? .trailing : .leading,
                               endPoint: shine ? UnitPoint(x: 2, y: 1) : UnitPoint(x: 1, y: 1))
        HStack(spacing: 8) {
            Text("✦").font(.system(size: 14)).foregroundStyle(g)
            Text(text).font(.system(size: 20, weight: .bold)).foregroundStyle(g)
                .shadow(color: base.opacity(0.55), radius: 8)
                .lineLimit(1).minimumScaleFactor(0.6)
            Text("✦").font(.system(size: 14)).foregroundStyle(g)
        }
        .padding(.horizontal, 16).padding(.vertical, 7)
        .overlay(Capsule().stroke(g, lineWidth: 1.5))
        .onAppear { withAnimation(.linear(duration: 2.6).repeatForever(autoreverses: true)) { shine = true } }
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

private func climberTypeStyle(_ t: String?) -> (color: Color, symbol: String) {
    switch t {
    case "ROUTES": return (Color(red: 0.16, green: 0.45, blue: 0.75), "figure.climbing")
    case "BOULDER": return (Color(red: 0.85, green: 0.48, blue: 0.10), "square.stack.3d.up.fill")
    case "BOTH": return (Color(red: 0.20, green: 0.55, blue: 0.30), "arrow.up.arrow.down")
    default: return (Color(red: 0.45, green: 0.47, blue: 0.5), "questionmark")
    }
}

private func climberChip(_ t: String?, on: Bool) -> some View {
    let st = climberTypeStyle(t)
    return HStack(spacing: 6) {
        Image(systemName: on ? "checkmark.circle.fill" : st.symbol).font(.system(size: 13, weight: .bold))
        Text(climberTypeLabel(t)).font(.system(size: 13, weight: on ? .bold : .medium))
    }
    .foregroundStyle(on ? Color.white : Cumbre.ink2)
    .padding(.horizontal, 12).padding(.vertical, 9)
    .background(on ? st.color : Cumbre.paper, in: Capsule())
    .overlay(Capsule().stroke(on ? st.color : Cumbre.rule, lineWidth: on ? 3 : 1))
    .shadow(color: on ? st.color.opacity(0.45) : .clear, radius: 6, y: 2)
    .opacity(on ? 1 : 0.8)
}

private let promptKey = "climber_type_prompt_done"

/// «★ 3 bloques de 7c o más en un día» con «ÉPICO · 3% de escaladores» debajo, en el color de su dificultad.
private struct FeaturedAchievement: View {
    let achievement: Achievement
    let rarity: AchievementRarity?
    /// Con menos escaladores sincronizados que esto el porcentaje no dice nada: se enseña solo la dificultad.
    private let minUsersForPercent = 5

    var body: some View {
        let color = tierColor(achievement.tier)
        let enough = (rarity?.totalUsers ?? 0) >= Int32(minUsersForPercent)
        let pct = enough ? Int(rarity?.percentById[achievement.id]?.intValue ?? 0) : 0
        VStack(spacing: 2) {
            Text("★ " + achievementText(achievement)).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center)
            Text(tierName(achievement.tier).uppercased()
                 + (enough ? " · " + (pct < 1 ? L("menos del 1% de escaladores") : L("%@% de escaladores", pct)) : ""))
                .font(Cumbre.mono(9.5, .bold)).foregroundStyle(color)
        }
        .padding(.horizontal, 14).padding(.vertical, 7)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(color, lineWidth: 1))
    }
}

struct RewardsBlock: View {
    let uid: String?
    @StateObject private var model = RewardsModel()
    @State private var sheet = false
    @State private var promptDone = UserDefaults.standard.bool(forKey: promptKey)
    @State private var badgeInfo: String?

    var body: some View {
        VStack(spacing: 6) {
            if let r = model.rewards {
                // En el tuyo abre la hoja para elegir; en el de otra persona cuenta cómo consiguió su título.
                Button { if uid == nil { sheet = true } else { badgeInfo = titleInfo(r) } } label: { summary(r) }
                    .buttonStyle(.plain)
                if uid == nil && !promptDone && r.climberType == nil { prompt }
            }
        }
        .task { await model.load(uid: uid) }
        .onChange(of: sheet) { open in if open { Task { await model.loadMineIds() } } }
        .alert(badgeInfo ?? "", isPresented: Binding(get: { badgeInfo != nil }, set: { if !$0 { badgeInfo = nil } })) {
            Button("OK", role: .cancel) {}
        }
        .sheet(isPresented: $sheet) {
            if let r = model.rewards { RewardsSheet(rewards: r, model: model) }
        }
    }

    private func summary(_ r: UserRewards) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                // Especial (puesto por un admin) en vez del de su nivel; nada si la persona ha ocultado su título.
                if !r.titleHidden {
                    if let special = r.specialTitle, !special.isEmpty {
                        SpecialTitleView(text: special, colorHex: r.specialColor)
                    } else {
                        RewardTitle(level: Int(r.titleLevel))
                    }
                }
                // Solo en el tuyo: una flechita que dice «aquí se puede elegir».
                if uid == nil {
                    Image(systemName: "chevron.down.circle.fill").font(.system(size: 17)).foregroundStyle(Cumbre.terra.opacity(0.8))
                }
            }
            // El logro que la persona ha elegido enseñar (con su dificultad y el % de gente que lo tiene).
            if let id = r.featuredAchievement, let a = AchievementsCalculator.shared.byId(id: id) {
                FeaturedAchievement(achievement: a, rarity: model.rarity)
            }
            HStack(spacing: 6) {
                if let t = r.climberType { badge(climberTypeLabel(t), climberTypeStyle(t).color, info: nil) }
                if r.badges.contains("TRUSTED") {
                    badge(L("De confianza"), Cumbre.ok, info: L("De confianza: lo que propone se publica sin pasar por revisión. Se consigue con 20 aportaciones aprobadas o lo da un admin."))
                }
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

    /// Qué es su título y cómo se consigue (Álvaro, 2026-10-19: «que le cuente lo que es»). nil = nada que contar.
    private func titleInfo(_ r: UserRewards) -> String? {
        if r.titleHidden { return nil }
        if let special = r.specialTitle, !special.isEmpty {
            var text = L("«%@» es un título especial que le ha dado el equipo de Cumbre.", special)
            switch TitleEffectKind(r.titleEffect) {
            case .fairies: text += " " + L("Lleva una aureola de hadas.")
            case .dragons: text += " " + L("Lleva una aureola de dragones.")
            case nil: break
            }
            return text
        }
        let level = Int(r.titleLevel)
        if level <= 0 {
            return L("«%@» es el título con el que empieza todo el mundo. Se suben de nivel aportando piedras, vías y correcciones que aprueba el equipo.", rewardTitle(0))
        }
        return L("«%@» se consigue con %@ aportaciones aprobadas (piedras, vías y correcciones que revisa el equipo). Lleva %@.",
                 rewardTitle(level), level, Int(r.approved))
    }

    @ViewBuilder
    private func badge(_ text: String, _ color: Color, info: String?) -> some View {
        let pill = Text(text.uppercased()).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(color)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .overlay(Capsule().stroke(color, lineWidth: 1))
        if let info {
            Button { badgeInfo = info } label: { pill }.buttonStyle(.plain)
        } else if uid == nil {
            Button { sheet = true } label: { pill }.buttonStyle(.plain)
        } else { pill }
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
                    } label: { climberChip(t, on: false) }.buttonStyle(.plain)
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
    @State private var featuredOpen = false
    @State private var tierFilter: Int? = nil

    private func tierPick(_ title: String, _ color: Color, on: Bool, count: Int, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Text(title).font(Cumbre.mono(10, .bold)).tracking(0.5)
                Text("\(count)").font(Cumbre.mono(9.5)).opacity(0.85)
            }
            .foregroundStyle(on ? Color.white : color)
            .padding(.horizontal, 11).padding(.vertical, 7)
            .background(on ? color : color.opacity(0.10), in: Capsule())
            .overlay(Capsule().stroke(color.opacity(0.7), lineWidth: 1))
        }.buttonStyle(.plain)
    }

    var body: some View {
        let r = model.rewards ?? rewards
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(L("¿VIERO O BLOQUERO?")).eyebrow(Cumbre.terra)
                FlowChips(items: ["ROUTES", "BOULDER", "BOTH", ""]) { t in
                    Button { Task { await model.update(climberType: t, titleLevel: nil) } } label: {
                        climberChip(t.isEmpty ? nil : t, on: (r.climberType ?? "") == t)
                    }.buttonStyle(.plain)
                }
                Button { withAnimation { featuredOpen.toggle() } } label: {
                    HStack {
                        Text(L("TU LOGRO DESTACADO")).eyebrow(Cumbre.terra)
                        Spacer()
                        Text(r.featuredAchievement.flatMap { AchievementsCalculator.shared.byId(id: $0) }.map { achievementText($0) } ?? L("Ninguno"))
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                        Image(systemName: featuredOpen ? "chevron.up" : "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.terra)
                    }
                    .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.terra.opacity(0.5), lineWidth: 1))
                }.buttonStyle(.plain)
                if !featuredOpen {
                    EmptyView()
                } else if let ids = model.mineIds {
                    let allUnlocked = AchievementsCalculator.shared.catalog.filter { ids.contains($0.id) }
                        .sorted { tierRank($0.tier) > tierRank($1.tier) }
                    let unlocked = allUnlocked.filter { tierFilter == nil || tierRank($0.tier) == tierFilter }
                    if allUnlocked.isEmpty {
                        Text(L("Aún no tienes logros sincronizados. Abre Mis estadísticas una vez y vuelve aquí."))
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    } else {
                        // Chips por dificultad, como en Mis estadísticas: tocas ÉPICO y solo salen los épicos que tienes.
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                tierPick(L("TODOS"), Cumbre.terra, on: tierFilter == nil, count: allUnlocked.count) { tierFilter = nil }
                                ForEach(Array(Set(allUnlocked.map { tierRank($0.tier) })).sorted(), id: \.self) { r in
                                    if let first = allUnlocked.first(where: { tierRank($0.tier) == r }) {
                                        tierPick(tierName(first.tier).uppercased(), tierColor(first.tier), on: tierFilter == r,
                                                 count: allUnlocked.filter { tierRank($0.tier) == r }.count) { tierFilter = (tierFilter == r ? nil : r) }
                                    }
                                }
                            }
                        }
                        VStack(spacing: 8) {
                            Button { Task { await model.update(climberType: nil, titleLevel: nil, featured: "") } } label: {
                                HStack {
                                    Text(L("Ninguno")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                    Spacer()
                                    if r.featuredAchievement == nil { Image(systemName: "checkmark.circle.fill").foregroundStyle(Cumbre.terra) }
                                }
                                .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(r.featuredAchievement == nil ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                            }.buttonStyle(.plain)
                            ForEach(unlocked, id: \.id) { a in
                                Button { Task { await model.update(climberType: nil, titleLevel: nil, featured: a.id) } } label: {
                                    achievementRow(a, rarity: model.rarity, on: r.featuredAchievement == a.id)
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                } else {
                    Text(L("Cargando tus logros…")).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                Text(L("TU TÍTULO")).eyebrow(Cumbre.terra)
                Text(L("Desbloqueas títulos con tus aportaciones aprobadas (llevas %@).", r.approved))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                if let special = r.specialTitle, !special.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        SpecialTitleView(text: special, colorHex: r.specialColor)
                        Text(L("Es un título especial que te ha dado Cumbre. Se enseña en lugar del de tu nivel."))
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                }
                // «Sin título»: no enseñar ninguno (Álvaro, 2026-10-19).
                Button { Task { await model.update(climberType: nil, titleLevel: nil, titleHidden: !r.titleHidden) } } label: {
                    HStack(spacing: 6) {
                        Image(systemName: r.titleHidden ? "checkmark.circle.fill" : "eye.slash").font(.system(size: 13, weight: .bold))
                        Text(L("Sin título")).font(.system(size: 14, weight: r.titleHidden ? .bold : .medium))
                    }
                    .foregroundStyle(r.titleHidden ? Color.white : Cumbre.ink2)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(r.titleHidden ? Cumbre.ink2 : Cumbre.paper, in: Capsule())
                    .overlay(Capsule().stroke(r.titleHidden ? Cumbre.ink2 : Cumbre.rule, lineWidth: r.titleHidden ? 3 : 1))
                }.buttonStyle(.plain)
                let unlockedLevels = Set(r.unlockedLevels.map { Int(truncating: $0) })
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(allRewardLevels, id: \.self) { l in
                        let has = unlockedLevels.contains(l)
                        HStack(spacing: 10) {
                            Button { if has { Task { await model.update(climberType: nil, titleLevel: l, titleHidden: false) } } } label: {
                                TitlePill(level: l, on: !r.titleHidden && Int(r.titleLevel) == l, locked: !has, showCheck: true)
                            }.buttonStyle(.plain).disabled(!has)
                            if !has {
                                Text(L("con %@ aportaciones", l)).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                            }
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
            .padding(20)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}

/// Los doce niveles de título, de menor a mayor.
private let allRewardLevels = [0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

/// Fila para elegir el logro destacado: franja del color de su dificultad, texto, y «ÉPICO · 3% de escaladores».
private func achievementRow(_ a: Achievement, rarity: AchievementRarity?, on: Bool) -> some View {
    let color = tierColor(a.tier)
    let enough = (rarity?.totalUsers ?? 0) >= 5
    let pct = enough ? Int(rarity?.percentById[a.id]?.intValue ?? 0) : 0
    return HStack(spacing: 0) {
        Rectangle().fill(color).frame(width: 5)
        VStack(alignment: .leading, spacing: 5) {
            Text(achievementText(a)).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.leading)
            HStack(spacing: 6) {
                AdminChip(text: tierName(a.tier).uppercased(), color: color, filled: true)
                if enough {
                    AdminChip(text: pct < 1 ? L("<1% de escaladores") : L("%@% de escaladores", pct), color: color)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        Spacer(minLength: 0)
        if on { Image(systemName: "checkmark.circle.fill").foregroundStyle(Cumbre.terra).padding(.trailing, 12) }
    }
    .background(on ? color.opacity(0.08) : Cumbre.paper)
    .clipShape(RoundedRectangle(cornerRadius: 10))
    .overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? color : Cumbre.rule, lineWidth: on ? 1.5 : 1))
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
