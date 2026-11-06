import SwiftUI
import Shared

// Datos de Estadísticas (diario, objetivo, logros), separados de la vista.

/// Baja el diario una vez y calcula en local con el calculador compartido.
@MainActor
final class StatsStore: ObservableObject {
    @Published var loading = true
    @Published var discipline = "BOULDER"
    @Published var year: String? = nil
    @Published var month: String? = nil
    @Published var day: String? = nil    // "yyyy-MM-dd": estadísticas de UN día
    @Published var grade: String? = nil  // G: filtrar por UN grado
    @Published var availableYears: [String] = []
    @Published var summary: JournalStatsCalculator.Summary? = nil
    @Published var progression: JournalStatsCalculator.Progression? = nil
    // Objetivo del año, extras y logros (2026-10-18), espejo de StatsViewModel.kt.
    let currentYear = Calendar.current.component(.year, from: Date())
    @Published var goal: GoalProgress? = nil
    @Published var goalLoaded = false
    @Published var styleShare: StyleShare? = nil
    @Published var yearComparison: YearComparison? = nil
    @Published var achievements: [Achievement] = []
    /// Cuánta gente tiene cada logro (nil = aún no se sabe o sin red).
    @Published var rarity: AchievementRarity? = nil
    private var lastSyncedIds: Set<String> = []
    @Published private(set) var isOwn = true

    private var entries: [JournalSession] = []
    private var places: [String: SchoolPlace] = [:]
    private(set) var yearGoal: YearGoal? = nil

    func load(uid: String? = nil) async {
        guard entries.isEmpty else { return }
        isOwn = uid == nil
        let container = AppDependencies.shared.container
        if let uid {
            if let list = try? await container.getUserJournal.invoke(uid: uid) { entries = list }
        } else if let list = try? await container.getMyJournal.invoke() {
            entries = list
        }
        loading = false
        recompute()
        // Cuánta gente tiene cada logro: si falla, simplemente no se enseña el porcentaje.
        rarity = try? await container.getAchievementRarity.invoke()
        // Región y país de cada escuela (logros de explorar). Si falla, esos logros quedan sin conseguir.
        if let schools = try? await container.getSchools.invoke(region: nil, style: nil, rockType: nil,
                                                                 lat: nil, lon: nil, radioKm: nil) {
            places = Dictionary(schools.map { ($0.id, SchoolPlace(region: $0.region, country: $0.country)) },
                                uniquingKeysWith: { a, _ in a })
        }
        if uid == nil {
            yearGoal = try? await container.getYearGoal.invoke(year: Int32(currentYear))
            goalLoaded = true
        }
        recompute()
    }

    /// Guarda el objetivo del año en curso. false si no se pudo (sin red…).
    func saveGoal(discipline: String?, grade: String?, ascents: Int?, days: Int?) async -> Bool {
        let g = YearGoal(year: Int32(currentYear), discipline: discipline, grade: grade,
                         ascents: ascents.map { KotlinInt(int: Int32($0)) }, days: days.map { KotlinInt(int: Int32($0)) })
        guard let saved = try? await AppDependencies.shared.container.saveYearGoal.invoke(goal: g) else { return false }
        yearGoal = saved
        recompute()
        return true
    }

    func removeGoal() async {
        if (try? await AppDependencies.shared.container.deleteYearGoal.invoke(year: Int32(currentYear))) != nil {
            yearGoal = nil
            recompute()
        }
    }

    /** G: grados presentes, del más duro al más fácil (para el filtro). */
    var availableGrades: [String] {
        let calc = JournalStatsCalculator.shared
        let gs = Set(entries.compactMap { $0.grade?.trimmingCharacters(in: .whitespaces).lowercased() }
            .filter { !$0.isEmpty })
        return gs.sorted { calc.gradeRank(grade: $0) > calc.gradeRank(grade: $1) }
    }

    /** N7: dias del filtro actual con nº de ascensos, recientes primero. */
    func daysWithCounts() -> [(String, Int)] {
        var counts: [String: Int] = [:]
        for e in filteredEntries() { counts[e.date, default: 0] += 1 }
        return counts.sorted { $0.key > $1.key }.map { ($0.key, $0.value) }
    }

    /** N7: entradas del filtro actual de UNA escuela (únicas, más duras primero). */
    func entriesForSchool(_ school: String) -> [JournalSession] {
        JournalStatsCalculator.shared.entriesForSchool(
            entries: filteredEntries(), school: school)
    }

    /** Un día de roca pulsado: qué ascensos hiciste ESE día. */
    func entriesForDay(_ day: String) -> [JournalSession] {
        filteredEntries().filter { $0.date == day }
    }

    /** Fila de la pirámide: vías de ESE grado (únicas, recientes primero). */
    func entriesForGrade(_ grade: String) -> [JournalSession] {
        JournalStatsCalculator.shared.entriesForGrade(
            entries: filteredEntries(), grade: grade)
    }

    private func filteredEntries() -> [JournalSession] {
        let calc = JournalStatsCalculator.shared
        var filtered = calc.filter(entries: entries, discipline: discipline, year: year)
        if let m = month {
            filtered = filtered.filter { e in
                guard e.date.count >= 7 else { return false }
                let start = e.date.index(e.date.startIndex, offsetBy: 5)
                let end = e.date.index(e.date.startIndex, offsetBy: 7)
                return String(e.date[start..<end]) == m
            }
        }
        if let d = day { filtered = filtered.filter { $0.date == d } }
        if let g = grade { filtered = filtered.filter { $0.grade?.lowercased() == g } }
        return filtered
    }

    func recompute() {
        let calc = JournalStatsCalculator.shared
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let today = df.string(from: Date())
        var filtered = calc.filter(entries: entries, discipline: discipline, year: year)
        if let m = month {
            filtered = filtered.filter { entry in
                let d = entry.date
                guard d.count >= 7 else { return false }
                let start = d.index(d.startIndex, offsetBy: 5)
                let end = d.index(d.startIndex, offsetBy: 7)
                return String(d[start..<end]) == m
            }
        }
        if let dd = day { filtered = filtered.filter { $0.date == dd } }
        if let g = grade { filtered = filtered.filter { $0.grade?.lowercased() == g } }
        availableYears = calc.availableYears(entries: entries)
        summary = calc.summary(entries: filtered, allEntries: entries, today: today)
        progression = calc.progression(entries: filtered, today: today)
        let goals = GoalCalculator.shared
        goal = yearGoal.map { goals.progress(goal: $0, all: entries) }
        styleShare = goals.styleShare(entries: filtered)
        yearComparison = year.map { goals.compareYears(entries: calc.filter(entries: entries, discipline: discipline, year: nil),
                                                       year: $0) }
        achievements = AchievementsCalculator.shared.compute(all: entries, places: places, today: today)
        syncAchievementsIfChanged()
    }

    /// Solo en las mías y solo si cambió el conjunto de logros desde la última vez. Silencioso.
    private func syncAchievementsIfChanged() {
        guard isOwn, !entries.isEmpty else { return }
        let unlocked = achievements.filter { $0.unlocked }
        let ids = Set(unlocked.map { $0.id })
        guard ids != lastSyncedIds else { return }
        lastSyncedIds = ids
        Task {
            do { try await AppDependencies.shared.container.syncMyAchievements.invoke(achievements: unlocked) }
            catch { lastSyncedIds = [] }
        }
    }
}
