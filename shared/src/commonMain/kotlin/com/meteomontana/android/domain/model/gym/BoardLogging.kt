package com.meteomontana.android.domain.model.gym

/** Lo apuntado en un registro de boards: los recuentos por clave y los bloques con nombre. */
data class BoardLog(val ascents: List<GymAscent>, val problems: List<BoardProblem>)

/** Apuntar un bloque de board con sus repeticiones e intentos (opcionales). Igual en iOS y Android. */
object BoardLogging {

    /**
     * Anade [reps] veces el bloque. Con nombre, cada repeticion guarda sus intentos en su [BoardProblem]; sin nombre, los intentos
     * van en la clave (el mejor de los de ese dia). [attempts] null = no se dijo: no se apunta nada ni hay flash.
     */
    fun add(
        log: BoardLog, board: BoardKind, grade: String, angle: Int?, name: String, reps: Int, attempts: Int?
    ): BoardLog {
        val n = BoardProblemRepeats.clampReps(reps)
        val tries = Attempts.clean(attempts)
        val key = GymAscentKeys.board(board, grade, angle)
        val named = BoardProblems.cleanName(name).isNotEmpty()
        var next = GymTraining.adjust(log.ascents, key, n)
        if (!named && tries != null) next = next.map { if (it.key == key) it.copy(attempts = Attempts.best(it.attempts, tries)) else it }
        return BoardLog(next, BoardProblemRepeats.addRepeated(log.problems, board, grade, name, n, tries))
    }
}
