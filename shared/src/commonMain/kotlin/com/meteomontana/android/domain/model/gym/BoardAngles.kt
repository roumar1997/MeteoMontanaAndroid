package com.meteomontana.android.domain.model.gym

import com.meteomontana.android.domain.port.KeyValueStore

/**
 * Inclinaciones de cada board, la que se preselecciona y el filtro por inclinacion de las pantallas Boards. Igual en iOS y
 * Android. La inclinacion de cada ascension ya va en la clave (`board:kilter:6c:40`), no hace falta nada nuevo en el servidor.
 */
object BoardAngles {
    private val STEP_20_TO_70: List<Int> = (20..70 step 5).toList()

    /** Kilter y Tension: de 20 a 70 grados de 5 en 5; MoonBoard solo 25 y 40 (los oficiales); System no tiene (null en la clave). */
    fun anglesFor(board: BoardKind): List<Int> = when (board) {
        BoardKind.KILTER, BoardKind.TENSION -> STEP_20_TO_70
        BoardKind.MOONBOARD -> listOf(25, 40)
        BoardKind.SYSTEM -> emptyList()
    }

    /** La inclinacion con la que se abre el selector la primera vez: Kilter 45, MoonBoard 25, Tension 25, System ninguna. */
    fun defaultAngle(board: BoardKind): Int? = when (board) {
        BoardKind.KILTER -> 45
        BoardKind.MOONBOARD, BoardKind.TENSION -> 25
        BoardKind.SYSTEM -> null
    }

    /** La que se preselecciona: la ultima usada en ese board si sigue siendo valida; si no, la de por defecto. */
    fun initial(board: BoardKind, lastUsed: Int?): Int? =
        lastUsed?.takeIf { it in anglesFor(board) } ?: defaultAngle(board)

    /** Cuantas ascensiones hay en cada inclinacion de [board] dentro de [items] (de menos a mas grados; las claves sin inclinacion no salen). */
    fun counts(items: List<GymAscent>, board: BoardKind): List<Pair<Int, Int>> =
        items.mapNotNull { a -> (GymAscentKeys.parse(a.key) as? AscentKey.Board)?.takeIf { it.board == board }?.angle?.let { it to a.count } }
            .groupBy({ it.first }, { it.second }).map { (angle, list) -> angle to list.sum() }.sortedBy { it.first }

    /** Las claves de [board] en [angle]; angle null = todas (sin filtrar). */
    fun filter(items: List<GymAscent>, board: BoardKind, angle: Int?): List<GymAscent> =
        if (angle == null) items else items.filter { a ->
            (GymAscentKeys.parse(a.key) as? AscentKey.Board)?.let { it.board == board && it.angle == angle } == true
        }

    /**
     * Bloques con nombre de [board] que salen con el filtro de inclinacion. El nombre no guarda su inclinacion: se mantienen los
     * que tienen ascensiones de ese mismo grado en [angle] (en [items], ya del board). angle null = todos.
     */
    fun problemsAt(problems: List<NamedBoardProblem>, items: List<GymAscent>, board: BoardKind, angle: Int?): List<NamedBoardProblem> {
        if (angle == null) return problems
        val grades = filter(items, board, angle).mapNotNull { (GymAscentKeys.parse(it.key) as? AscentKey.Board)?.grade?.lowercase() }.toSet()
        return problems.filter { it.problem.board == board && it.problem.grade.lowercase() in grades }
    }
}

/** Ultima inclinacion elegida en cada board (guardada en el movil, por board) para preseleccionarla la proxima vez. */
class BoardAngleMemory(private val store: KeyValueStore) {

    fun last(board: BoardKind): Int? =
        runCatching { store.getString(KEY + board.wire) }.getOrNull()?.toIntOrNull()?.takeIf { it in BoardAngles.anglesFor(board) }

    fun remember(board: BoardKind, angle: Int?) {
        if (angle != null && angle in BoardAngles.anglesFor(board)) runCatching { store.putString(KEY + board.wire, angle.toString()) }
    }

    /** Lo que se preselecciona al abrir el registro de [board]. */
    fun initial(board: BoardKind): Int? = BoardAngles.initial(board, last(board))

    private companion object { const val KEY = "board_last_angle_" }
}
