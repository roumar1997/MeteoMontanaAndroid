package com.meteomontana.android.domain.model.gym

/**
 * A board block already logged on one day and gym ([gymId] = [GymSession.NO_GYM] for «Solo board»), opened from the detail sheet
 * to edit or delete it. [name] null = the unnamed ones of that grade. [angle] null = not known.
 */
data class BoardEntry(
    val gymId: Long, val date: String, val board: BoardKind, val grade: String, val name: String?,
    val angle: Int?, val reps: Int, val attempts: Int?
) {
    /** The day as a date (the sheet saves that day). */
    val day: kotlinx.datetime.LocalDate get() = kotlinx.datetime.LocalDate.parse(date)

    /** The grid grades to preselect (one, or two for a double grade like 6b+/6c). */
    val gridGrades: List<String>
        get() = grade.split('/').mapNotNull { g -> GymAscentKeys.GRID_GRADES.firstOrNull { it.equals(g, ignoreCase = true) } }.take(2)
}

/**
 * Edit or delete a logged board block: the entry is taken out of that day's log and, on save, the form adds it back with the new
 * values ([BoardLogging.add]). The whole day is then saved as usual (local first, queued offline). Same on iOS and Android.
 */
object BoardEditing {

    /** The entry of [day] in [row]; [angleFilter] is the angle chosen in the list (null = all). */
    fun entry(row: BoardBlockRow, day: BoardBlockDay, angleFilter: Int?): BoardEntry =
        BoardEntry(day.gymId, day.date, row.board, row.grade, row.name, angleFilter, day.reps, day.attempts)

    /** If the angle is not known and that day has a single angle for the board and grade, that one. */
    fun withAngle(entry: BoardEntry, session: GymSession): BoardEntry {
        if (entry.angle != null) return entry
        val angles = boardKeys(session.ascents, entry).map { it.second.angle }.distinct()
        return if (angles.size == 1) entry.copy(angle = angles.first()) else entry
    }

    /** True when [log] holds [entry] (so editing can't double count something that isn't there). */
    fun contains(log: BoardLog, entry: BoardEntry): Boolean = remove(log, entry) != log

    /**
     * [log] without [entry]: [BoardEntry.reps] fewer in the board+grade counts (its angle first) and, if it has a name, that many
     * named blocks fewer. Unnamed: the attempts of the remaining keys are dropped (they were the unnamed ones' attempts).
     */
    fun remove(log: BoardLog, entry: BoardEntry): BoardLog {
        val name = entry.name?.let { BoardProblemRepeats.nameKey(it) }?.takeIf { it.isNotEmpty() }
        var problems = log.problems
        if (name != null) {
            var left = entry.reps
            problems = log.problems.filter { p ->
                val match = left > 0 && p.board == entry.board && p.grade.equals(entry.grade, ignoreCase = true) && BoardProblemRepeats.nameKey(p.name) == name
                if (match) left--
                !match
            }
        }
        var left = entry.reps
        var ascents = log.ascents
        val keys = boardKeys(log.ascents, entry).sortedBy { if (it.second.angle == entry.angle) 0 else 1 }
        for ((a, _) in keys) {
            if (left <= 0) break
            val take = minOf(left, a.count)
            ascents = GymTraining.adjust(ascents, a.key, -take)
            left -= take
        }
        if (name == null) {
            val touched = keys.map { it.first.key }.toSet()
            ascents = ascents.map { if (it.key in touched) it.copy(attempts = null) else it }
        }
        return BoardLog(ascents, BoardProblems.prune(problems, ascents))
    }

    /** [log] with [old] replaced by the new values (same as deleting it and logging it again). */
    fun replace(log: BoardLog, old: BoardEntry, grade: String, angle: Int?, name: String, reps: Int, attempts: Int?): BoardLog =
        BoardLogging.add(remove(log, old), old.board, grade, angle, name, reps, attempts)

    private fun boardKeys(ascents: List<GymAscent>, entry: BoardEntry): List<Pair<GymAscent, AscentKey.Board>> =
        ascents.mapNotNull { a ->
            (GymAscentKeys.parse(a.key) as? AscentKey.Board)
                ?.takeIf { it.board == entry.board && it.grade.equals(entry.grade, ignoreCase = true) }?.let { a to it }
        }
}
