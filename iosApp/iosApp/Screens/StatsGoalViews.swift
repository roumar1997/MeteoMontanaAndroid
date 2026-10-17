import SwiftUI
import Shared

// Objetivo del año, % flash / a vista, año frente al anterior y LOGROS. Espejo de StatsGoalSection.kt (Android).

private let goalGrades: [String] = ["5", "6", "7", "8"].flatMap { n in ["a", "a+", "b", "b+", "c", "c+"].map { n + $0 } }

private func goalPromptKey(_ year: Int) -> String { "goal_prompt_dismissed_\(year)" }

/// OBJETIVO DEL AÑO (opcional, solo en tus estadísticas). Sin objetivo: tarjeta que lo propone (una vez al año) o un
/// enlace discreto. Con objetivo: cada meta con su barra de progreso.
struct GoalSection: View {
    let goal: GoalProgress?
    let year: Int
    let onEdit: () -> Void
    @State private var dismissed = false

    var body: some View {
        Group {
            if let goal {
                progressCard(goal)
            } else if dismissed {
                Button(action: onEdit) {
                    Text(L("+ PONER OBJETIVO %@", year)).font(Cumbre.mono(10, .bold)).tracking(1).foregroundStyle(Cumbre.terra)
                }.buttonStyle(.plain)
            } else {
                promptCard
            }
        }
        .onAppear { dismissed = UserDefaults.standard.bool(forKey: goalPromptKey(year)) }
    }

    private var promptCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L("OBJETIVO %@", year)).eyebrow(Cumbre.terra)
            Text(L("¿Te pones un objetivo para este año? Un grado, un número de ascensiones o de días de roca. Es opcional y solo lo ves tú."))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            HStack(spacing: 16) {
                Button(action: onEdit) {
                    Text(L("Poner objetivo").uppercased()).font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 4))
                }.buttonStyle(.plain)
                Button {
                    UserDefaults.standard.set(true, forKey: goalPromptKey(year)); dismissed = true
                } label: {
                    Text(L("Ahora no").uppercased()).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                }.buttonStyle(.plain)
            }
        }
        .modifier(StatsCard())
    }

    private func progressCard(_ p: GoalProgress) -> some View {
        let g = p.goal
        return Button(action: onEdit) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(L("OBJETIVO %@", year)).eyebrow(Cumbre.terra)
                    Spacer()
                    Text(L("EDITAR")).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                }
                if let target = g.grade {
                    HStack(spacing: 8) {
                        Text(g.discipline == "ROUTE" ? L("Grado de vía") : L("Grado de bloque"))
                            .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        Spacer()
                        GradePill(grade: target)
                        Text(p.gradeReached ? "✓" : (p.maxGradeThisYear.map { L("ahora %@", $0) } ?? "—"))
                            .font(Cumbre.mono(12, .bold)).foregroundStyle(p.gradeReached ? Cumbre.ok : Cumbre.ink2)
                    }
                }
                if let a = g.ascents?.intValue { progressLine(L("Ascensiones"), Int(p.ascentsDone), a) }
                if let d = g.days?.intValue { progressLine(L("Días de roca"), Int(p.daysDone), d) }
            }
            .modifier(StatsCard())
        }
        .buttonStyle(.plain)
    }

    private func progressLine(_ label: String, _ done: Int, _ target: Int) -> some View {
        VStack(spacing: 4) {
            HStack {
                Text(label).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                Spacer()
                Text("\(done) / \(target)").font(Cumbre.mono(12, .bold)).foregroundStyle(done >= target ? Cumbre.ok : Cumbre.ink2)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(Cumbre.rule.opacity(0.5))
                    RoundedRectangle(cornerRadius: 3).fill(done >= target ? Cumbre.ok : Cumbre.terraFill)
                        .frame(width: geo.size.width * min(1, CGFloat(done) / CGFloat(max(1, target))))
                }
            }
            .frame(height: 6)
        }
    }
}

/// Hoja para poner o cambiar el objetivo: grado (bloque o vía), ascensiones y días. Basta con uno.
struct GoalSheet: View {
    let year: Int
    let initial: YearGoal?
    let onSave: (String?, String?, Int?, Int?) async -> Bool
    let onRemove: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var discipline = "BOULDER"
    @State private var grade: String?
    @State private var ascents = ""
    @State private var days = ""
    @State private var saving = false
    @State private var failed = false

    private var ascentsN: Int? { Int(ascents).flatMap { (1...10000).contains($0) ? $0 : nil } }
    private var daysN: Int? { Int(days).flatMap { (1...366).contains($0) ? $0 : nil } }
    private var valid: Bool { grade != nil || ascentsN != nil || daysN != nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(L("OBJETIVO %@", year)).eyebrow(Cumbre.terra)
                Text(L("Rellena lo que quieras: un grado, ascensiones o días. Con una cosa basta."))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                Text(L("GRADO (OPCIONAL)")).eyebrow()
                HStack(spacing: 8) {
                    chip(L("BLOQUE"), on: discipline == "BOULDER") { discipline = "BOULDER" }
                    chip(L("VÍA"), on: discipline == "ROUTE") { discipline = "ROUTE" }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(goalGrades, id: \.self) { g in
                            let on = grade == g
                            Button { grade = on ? nil : g } label: {
                                GradePill(grade: g, large: true)
                                    .opacity(on || grade == nil ? 1 : 0.45)
                                    .overlay(Capsule().stroke(on ? Cumbre.ink : .clear, lineWidth: 2))
                            }.buttonStyle(.plain)
                        }
                    }
                }
                HStack(spacing: 12) {
                    field(L("Ascensiones"), $ascents)
                    field(L("Días de roca"), $days)
                }
                if failed {
                    Text(L("No se pudo guardar. Revisa la conexión.")).font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                }
                HStack(spacing: 16) {
                    Button {
                        Task {
                            saving = true
                            let ok = await onSave(grade != nil ? discipline : nil, grade, ascentsN, daysN)
                            saving = false
                            if ok { dismiss() } else { failed = true }
                        }
                    } label: {
                        Text(L("GUARDAR")).font(Cumbre.mono(12, .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 4))
                            .opacity(valid && !saving ? 1 : 0.4)
                    }
                    .buttonStyle(.plain).disabled(!valid || saving)
                    if initial != nil {
                        Button { onRemove(); dismiss() } label: {
                            Text(L("Quitar objetivo").uppercased()).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.bad)
                        }.buttonStyle(.plain)
                    }
                }
            }
            .padding(20)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .onAppear {
            guard let g = initial else { return }
            discipline = g.discipline ?? "BOULDER"
            grade = g.grade
            ascents = g.ascents.map { "\($0.intValue)" } ?? ""
            days = g.days.map { "\($0.intValue)" } ?? ""
        }
    }

    private func chip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(11, .bold)).foregroundStyle(on ? .white : Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private func field(_ label: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            TextField("", text: text).keyboardType(.numberPad).font(.system(size: 15)).padding(10)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// % de flash y a vista de lo filtrado, y el año elegido frente al anterior.
struct StyleAndYearSection: View {
    let share: StyleShare?
    let comparison: YearComparison?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let s = share, s.total > 0 {
                HStack(spacing: 8) {
                    small("\(s.flashPct)%", L("% AL FLASH"))
                    small("\(s.onsightPct)%", L("% A VISTA"))
                }
            }
            if let c = comparison, let y = Int(c.year) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("%@ FRENTE A %@", c.year, y - 1)).eyebrow()
                    line(L("Ascensiones"), "\(c.ascents)", "\(c.previousAscents)", Int(c.ascents) - Int(c.previousAscents))
                    line(L("Días de roca"), "\(c.days)", "\(c.previousDays)", Int(c.days) - Int(c.previousDays))
                    line(L("Grado máximo"), c.maxGrade ?? "—", c.previousMaxGrade ?? "—", nil)
                }
                .modifier(StatsCard())
            }
        }
    }

    private func small(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(label).font(Cumbre.mono(8, .bold)).tracking(1).foregroundStyle(Cumbre.ink3)
        }
        .frame(maxWidth: .infinity).padding(8)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func line(_ label: String, _ now: String, _ before: String, _ delta: Int?) -> some View {
        HStack(spacing: 4) {
            Text(label).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
            Spacer()
            Text(before).font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
            Text("→").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            Text(now).font(Cumbre.mono(13, .bold)).foregroundStyle(Cumbre.ink)
            if let d = delta, d != 0 {
                Text(d > 0 ? "+\(d)" : "\(d)").font(Cumbre.mono(11)).foregroundStyle(d > 0 ? Cumbre.ok : Cumbre.bad)
            }
        }
    }
}

/// LOGROS: conseguidos en color (con la fecha) y los que faltan en gris.
struct AchievementsSection: View {
    let items: [Achievement]

    var body: some View {
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(L("LOGROS")).eyebrow()
                    Spacer()
                    Text("\(items.filter(\.unlocked).count) / \(items.count)").font(Cumbre.mono(12, .bold))
                        .foregroundStyle(Cumbre.terra)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 6)], alignment: .leading, spacing: 6) {
                    ForEach(items.sorted { $0.unlocked && !$1.unlocked }, id: \.id) { a in
                        VStack(alignment: .leading, spacing: 2) {
                            Text((a.unlocked ? "★ " : "☆ ") + achievementText(a))
                                .font(.system(size: 12.5, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            if a.unlocked, let d = a.unlockedOn {
                                Text(d).font(Cumbre.mono(9.5)).foregroundStyle(Cumbre.ink3)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(a.unlocked ? Cumbre.terra.opacity(0.12) : Cumbre.paper, in: RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(a.unlocked ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                        .opacity(a.unlocked ? 1 : 0.55)
                    }
                }
            }
        }
    }
}

func achievementText(_ a: Achievement) -> String {
    switch a.kind {
    case .grade: return a.discipline == "ROUTE" ? L("Primer %@ de vía", a.param) : L("Primer %@ de bloque", a.param)
    case .volume: return L("%@ ascensiones", a.param)
    case .flashes: return L("%@ al flash", a.param)
    case .onsights: return L("%@ a vista", a.param)
    case .flashMax: return L("Flash de tu grado máximo")
    case .onsightMax: return L("A vista de tu grado máximo")
    case .schools: return L("%@ escuelas distintas", a.param)
    case .regions: return L("%@ comunidades distintas", a.param)
    case .abroad: return L("Una escuela fuera de España")
    case .weekends: return L("%@ fines de semana seguidos", a.param)
    case .daysInYear: return L("%@ días de roca en un año", a.param)
    case .everyMonth: return L("Un año entero saliendo cada mes")
    case .projects: return a.param == "1" ? L("Cerrar un proyecto") : L("Cerrar %@ proyectos", a.param)
    default: return a.id
    }
}

/// Tarjeta de las estadísticas: fondo papel, borde fino.
struct StatsCard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }
}
