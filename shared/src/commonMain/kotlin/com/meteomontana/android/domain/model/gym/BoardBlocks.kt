package com.meteomontana.android.domain.model.gym

/** Lo hecho en boards un dia y una clave (`board:kilter:6c:40`), con o sin rocodromo ([GymSession.NO_GYM]); [attempts] = el mejor de los SIN nombre. */
data class BoardAscentRow(val gymId: Long, val date: String, val key: String, val count: Int, val attempts: Int? = null)

/** Un dia en que se hizo un bloque: [reps] veces y, si se dijo, el mejor numero de [attempts] de ese dia. */
data class BoardBlockDay(val date: String, val gymId: Long, val reps: Int, val attempts: Int? = null)

/**
 * Una fila de la lista unica «BLOQUES · N» de un tablero: un bloque con nombre ([name]) o todos los SIN nombre de un grado
 * (name = null, se pinta «Sin nombre» en gris). [bestAttempts] es el mejor (menor) numero de intentos; 1 = FLASH.
 */
data class BoardBlockRow(
    val board: BoardKind, val grade: String, val name: String?, val reps: Int, val lastDate: String,
    val dates: List<String>, val bestAttempts: Int?, val days: List<BoardBlockDay>, val problem: RepeatedProblem? = null
) {
    val isNamed: Boolean get() = name != null
    val isFlash: Boolean get() = Attempts.isFlash(bestAttempts)
}

/** Une los bloques con nombre y los sin nombre de un tablero en una sola lista, del grado mas alto al mas bajo. Igual en iOS y Android. */
object BoardBlocks {

    /**
     * Las filas de [board] de [summary]. [angle] null = todas las inclinaciones. Con [query] solo salen los CON nombre que
     * lo contienen (los sin nombre no se buscan). Orden: grado alto primero, luego mas reciente y mas repetido.
     */
    fun rows(summary: GymSummary, board: BoardKind, angle: Int?, query: String): List<BoardBlockRow> {
        val boardItems = GymAscentKeys.boards(summary.items, board)
        val pool = BoardAngles.problemsAt(summary.boardProblems, boardItems, board, angle)
        val named = BoardProblemRepeats.repeated(pool, board, query).map { p ->
            BoardBlockRow(board, p.grade, p.name, p.reps, p.lastDate, p.dates, p.bestAttempts, p.days.map { BoardBlockDay(it.date, it.gymId, it.reps, it.attempts) }, p)
        }
        val unnamed = if (query.isBlank()) unnamed(summary.boardAscents, pool, board, angle) else emptyList()
        return (named + unnamed).sortedWith(
            compareByDescending<BoardBlockRow> { GymAscentKeys.gradeRank(it.grade) }.thenByDescending { it.lastDate }
                .thenByDescending { it.reps }.thenBy { it.name ?: "￿" }
        )
    }

    /** El «N» de «BLOQUES · N»: todas las repeticiones de la lista. */
    fun total(rows: List<BoardBlockRow>): Int = rows.sumOf { it.reps }

    private data class Slot(val gymId: Long, val date: String, val grade: String)

    /**
     * Los bloques SIN nombre: lo hecho cada dia en cada grado menos lo que tiene nombre ese mismo dia y rocodromo, agrupado por
     * grado. Con [angle] solo cuenta lo de esa inclinacion (se supone que los nombrados de ese grado son de esa inclinacion).
     */
    internal fun unnamed(rows: List<BoardAscentRow>, named: List<NamedBoardProblem>, board: BoardKind, angle: Int?): List<BoardBlockRow> {
        val namedBySlot = named.filter { it.problem.board == board }.groupingBy { Slot(it.gymId, it.date, it.problem.grade.lowercase()) }.eachCount()
        val parsed = rows.mapNotNull { r -> (GymAscentKeys.parse(r.key) as? AscentKey.Board)?.takeIf { it.board == board }?.let { r to it } }
        val days = parsed.groupBy { (r, k) -> Slot(r.gymId, r.date, k.grade.lowercase()) }.mapNotNull { (slot, list) ->
            val at = if (angle == null) list else list.filter { it.second.angle == angle }
            val left = at.sumOf { it.first.count } - (namedBySlot[slot] ?: 0)
            if (at.isEmpty() || left <= 0) null else slot to BoardBlockDay(slot.date, slot.gymId, left, Attempts.best(at.map { it.first.attempts }))
        }
        return days.groupBy({ it.first.grade }, { it.second }).map { (grade, list) ->
            val sorted = list.sortedWith(compareByDescending<BoardBlockDay> { it.date }.thenBy { it.gymId })
            BoardBlockRow(board, grade, null, list.sumOf { it.reps }, sorted.first().date, sorted.map { it.date }.distinct(), Attempts.best(list.map { it.attempts }), sorted)
        }
    }
}
