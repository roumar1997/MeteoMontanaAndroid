import SwiftUI
import Shared

// Piezas del perfil propio y ajeno (maqueta 4): cuatro celdas, fichas pequeñas, la tarjeta de degradado con Roca y
// Rocódromo y la fila «Tu rocódromo». Espejo de ProfileClimbBlocks.kt y ProfileSummary.kt. Todo flexible: nada de anchos fijos.

/// Grado con el color de la paleta de grados de la app, en píldora; sin grado, una raya.
struct MaxGradePill: View {
    let grade: String?
    var body: some View {
        if let grade, !grade.trimmingCharacters(in: .whitespaces).isEmpty {
            let st = GradeColor.chipStyle(grade)
            Text(grade.uppercased()).font(.system(size: 14, weight: .bold)).lineLimit(1)
                .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white)
                .padding(.horizontal, 10).padding(.vertical, 3)
                .background(st.stroke, in: RoundedRectangle(cornerRadius: 10))
        } else {
            Text("—").font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
        }
    }
}

/// Bloques · Vías · Máx. bloque · Máx. vía, con el máximo en píldora del color de su grado.
struct ProfileCells: View {
    let boulders: Int
    let routes: Int
    let maxBoulder: String?
    let maxRoute: String?
    let onBoulders: () -> Void
    let onRoutes: () -> Void
    let onMax: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            cell(L("profile_blocks"), onBoulders) { number(boulders) }
            cell(L("profile_routes"), onRoutes) { number(routes) }
            cell(L("profile_max_boulder"), onMax) { MaxGradePill(grade: maxBoulder) }
            cell(L("profile_max_route"), onMax) { MaxGradePill(grade: maxRoute) }
        }
    }

    private func number(_ n: Int) -> some View {
        Text("\(n)").font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1).minimumScaleFactor(0.6)
    }

    private func cell<C: View>(_ label: String, _ action: @escaping () -> Void, @ViewBuilder _ content: () -> C) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                content().frame(height: 28)
                Text(label).font(Cumbre.mono(9)).tracking(1).foregroundStyle(Cumbre.ink2).lineLimit(2).multilineTextAlignment(.center)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity).padding(.horizontal, 4).padding(.vertical, 8)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.statCardRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Cuadradito redondeado con un icono del color dado, sobre su propio tinte.
struct TintedSymbol: View {
    let symbol: String
    let tint: Color
    var size: CGFloat = 36
    var body: some View {
        RoundedRectangle(cornerRadius: size / 3).fill(tint.opacity(0.14)).frame(width: size, height: size)
            .overlay(Image(systemName: symbol).font(.system(size: size / 2.4, weight: .semibold)).foregroundStyle(tint))
    }
}

enum ProfileTint {
    static let purple = Color(hex: 0x7B3FB0)
    static let blue = Cumbre.dyn(0x2563C7, 0x7BA3EE)
}

/// Fila redondeada con icono de color, etiqueta, cuenta opcional y flecha (Escuelas, Proyectos... de Estadísticas).
struct ProfileLinkRow: View {
    let symbol: String
    let tint: Color
    let label: String
    let count: Int?
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                TintedSymbol(symbol: symbol, tint: tint)
                Text(label).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let count {
                    Text("\(count)").font(Cumbre.serif(15, .bold)).foregroundStyle(tint)
                        .padding(.horizontal, 10).padding(.vertical, 2).background(tint.opacity(0.13), in: Capsule())
                }
                Text("›").font(.system(size: 20)).foregroundStyle(Cumbre.ink2)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Fichas pequeñas bajo los números: Publicaciones, Escuelas, Proyectos (solo propio) y Boards (con rocódromos).
struct ProfileMiniTiles: View {
    let posts: Int?
    let onPosts: () -> Void
    let schools: Int
    let onSchools: () -> Void
    let projects: Int
    let onProjects: (() -> Void)?
    var boards: Int?
    var onBoards: (() -> Void)?

    var body: some View {
        let compact = boards != nil && onBoards != nil && onProjects != nil
        HStack(spacing: compact ? 4 : 6) {
            tile("newspaper", ProfileTint.purple, L("feed_posts_section"), posts, onPosts, compact)
            tile("mountain.2", Cumbre.ok, L("profile_schools"), schools, onSchools, compact)
            if let onProjects { tile("flag", Cumbre.terraFill, L("profile_projects"), projects, onProjects, compact) }
            if let boards, let onBoards { tile("square.grid.2x2", ProfileTint.blue, L("profile_boards"), boards, onBoards, compact) }
        }
    }

    private func tile(_ symbol: String, _ tint: Color, _ label: String, _ count: Int?, _ action: @escaping () -> Void,
                      _ compact: Bool) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                TintedSymbol(symbol: symbol, tint: tint, size: 30)
                Text(count.map { "\($0)" } ?? "›").font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                Text(label.uppercased()).font(Cumbre.mono(compact ? 7 : 8)).tracking(compact ? 0.2 : 0.8).foregroundStyle(Cumbre.ink2)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity).padding(.horizontal, compact ? 2 : 4).padding(.vertical, 8)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Tarjeta de degradado (morado, terracota, naranja): total de días y dos mitades, Roca y Rocódromo, pulsables.
struct ClimbGradientCard: View {
    @ObservedObject var climb: ProfileClimbModel
    let onStats: () -> Void
    let onRock: () -> Void
    let onGym: () -> Void

    var body: some View {
        let s = climb.summary
        let st = climb.stats
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(L("profile_climb_card_title").uppercased()).eyebrow(Color.white.opacity(0.9)).lineLimit(1)
                    Text(LP("profile_days_climbing", climb.totalDays)).font(Cumbre.serif(20, .bold)).foregroundStyle(.white).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(L("gym_stats_see_all").uppercased() + " ›").eyebrow(Color(hex: 0x2A2924)).lineLimit(1)
                    .padding(.horizontal, 10).padding(.vertical, 5).background(Color.white.opacity(0.92), in: Capsule())
            }
            HStack(alignment: .top, spacing: 8) {
                half(L("profile_line_rock"), climb.rockDays.map { LP("profile_days", $0) } ?? "—", [
                    (L("profile_card_blocks"), withMax(Int(st?.boulderCount ?? 0), st?.maxBoulderGrade)),
                    (L("profile_card_routes"), withMax(Int(st?.routeCount ?? 0), st?.maxRouteGrade)),
                    (L("profile_card_schools"), "\(st?.schoolCount ?? 0)")
                ], onRock)
                half(L("profile_line_gym"), LP("profile_sessions", Int(s.sessions)), [
                    (L("profile_card_gyms"), "\(s.gyms)"),
                    (L("profile_card_ascents"), "\(s.ascents)"),
                    (L("profile_card_max"), s.maxGrade?.uppercased() ?? "—")
                ], onGym)
            }
            .fixedSize(horizontal: false, vertical: true)
            if !climb.segments.isEmpty {
                GeometryReader { geo in
                    let total = CGFloat(climb.segments.reduce(0) { $0 + $1.count })
                    HStack(spacing: 0) {
                        ForEach(climb.segments, id: \.self) { seg in
                            Rectangle().fill(seg.hex.map { GymPresentation.color(hex: $0) } ?? GradeColor.chip(seg.grade))
                                .frame(width: geo.size.width * CGFloat(seg.count) / max(total, 1))
                        }
                    }
                }
                .frame(height: 7).clipShape(Capsule())
            }
        }
        .padding(14)
        .background(LinearGradient(colors: [Color(hex: 0x7B3FB0), Color(hex: 0xC2410C), Color(hex: 0xE88A3A)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 22))
        .contentShape(RoundedRectangle(cornerRadius: 22))
        .onTapGesture(perform: onStats)
    }

    private func withMax(_ count: Int, _ max: String?) -> String {
        guard let max, !max.isEmpty else { return "\(count)" }
        return "\(count) · \(max.uppercased())"
    }

    private func half(_ title: String, _ big: String, _ lines: [(String, String)], _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(title.uppercased()).eyebrow(Color.white.opacity(0.9)).lineLimit(1)
                    Spacer(minLength: 4)
                    Text("›").font(.system(size: 16)).foregroundStyle(Color.white.opacity(0.8))
                }
                Text(big).font(Cumbre.serif(20, .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                ForEach(lines, id: \.0) { line in
                    HStack(spacing: 6) {
                        Text(line.0).font(.system(size: 12)).foregroundStyle(Color.white.opacity(0.85)).lineLimit(1)
                        Spacer(minLength: 2)
                        Text(line.1).font(.system(size: 12, weight: .bold)).foregroundStyle(.white).lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 9)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Color.white.opacity(0.16), in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

/// «Tu rocódromo» / «Su rocódromo», «Semanas seguidas» y (propio) «+ Entreno». Sin rocódromo, el propio enseña
/// el hueco «Elige tu rocódromo»; el ajeno no enseña nada.
struct HomeGymAndStreak<Trailing: View>: View {
    let home: HomeGym?
    let streakWeeks: Int
    let own: Bool
    let onOpenGym: (Int64) -> Void
    var onPick: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        let canPick = own && onPick != nil && home == nil
        HStack(spacing: 8) {
            if canPick {
                Button { onPick?() } label: {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 9).stroke(Cumbre.rule, lineWidth: 1).frame(width: 30, height: 30)
                            .overlay(Text("+").font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.terraFill))
                        Text(L("home_gym_pick")).font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(10)
                    .overlay(RoundedRectangle(cornerRadius: Cumbre.statCardRadius).stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            if let home {
                Button { onOpenGym(home.id) } label: {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 9).fill(Cumbre.terraFill).frame(width: 30, height: 30)
                            .overlay(Text(String(home.name.prefix(1)).uppercased()).font(Cumbre.serif(15, .bold)).foregroundStyle(.white))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(L(own ? "home_gym_yours" : "home_gym_theirs").uppercased()).font(Cumbre.mono(8)).tracking(1)
                                .foregroundStyle(Cumbre.ink2).lineLimit(1)
                            Text(home.name).font(.system(size: 13, weight: .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(10).cardChrome()
                }
                .buttonStyle(.plain)
            }
            if streakWeeks > 0 {
                HStack(spacing: 8) {
                    Image(systemName: "flame").font(.system(size: 20)).foregroundStyle(Cumbre.terraFill)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(L("gym_stats_streak").uppercased()).font(Cumbre.mono(8)).tracking(1).foregroundStyle(Cumbre.ink2).lineLimit(1)
                        Text("\(streakWeeks)").font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(10).cardChrome()
            }
            trailing()
        }
    }
}

extension View {
    /// Papel con borde y radio de tarjeta de estadística.
    func cardChrome(_ radius: CGFloat = Cumbre.statCardRadius) -> some View {
        self.background(Cumbre.paper, in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Botón pequeño «+ Entreno»: abre el selector de rocódromo (Hoy, Ayer u otro día) y después la hoja de ese día.
struct LogWorkoutPill: View {
    var onSaved: () -> Void = {}
    @State private var picking = false
    @State private var target: GymTrainTarget?

    var body: some View {
        Button { picking = true } label: {
            HStack(spacing: 4) {
                Image(systemName: "plus").font(.system(size: 13, weight: .bold))
                Text(L("profile_add_workout_short")).font(.system(size: 13, weight: .bold)).lineLimit(1)
            }
            .foregroundStyle(.white).padding(.horizontal, 14).padding(.vertical, 12)
            .background(Cumbre.terraFill, in: Capsule())
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $picking) {
            GymPickerSheet { gym, day in
                picking = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { target = GymTrainTarget(gym: gym, day: day) }
            }
        }
        .sheet(item: $target) { t in
            GymTrainSheet(gym: t.gym, dateLabel: GymDates.long(t.day), date: GymDates.local(t.day), onSaved: onSaved)
        }
    }
}

struct GymTrainTarget: Identifiable {
    let gym: Gym
    let day: Date
    var id: String { "\(gym.id)-\(day.timeIntervalSince1970)" }
}
