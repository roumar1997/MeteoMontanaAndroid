package com.meteomontana.android.domain.model.gym

/**
 * Un bloque de board con nombre repetido: mismo tablero, mismo nombre (sin mayusculas, acentos ni espacios de mas) y
 * mismo grado. [reps] cuenta cada vez que se hizo (cada repeticion es una ascension en el recuento de su board+grado).
 * Mismo nombre con otro grado es OTRO problema. Igual en iOS y Android.
 */
data class RepeatedProblem(
    val board: BoardKind, val name: String, val grade: String, val reps: Int,
    val firstDate: String, val lastDate: String,
    /** Rocodromo de la ultima vez ([GymSession.NO_GYM] = sin rocodromo). */
    val lastGymId: Long,
    /** Dias distintos en que se hizo, del mas reciente al mas antiguo. */
    val dates: List<String>,
    /** Cada dia (y rocodromo) en que se hizo con sus repeticiones, del mas reciente al mas antiguo. */
    val days: List<ProblemDay> = emptyList()
)

/** Un dia en que se hizo un bloque: [reps] veces en [gymId] ([GymSession.NO_GYM] = sin rocodromo). */
data class ProblemDay(val date: String, val gymId: Long, val reps: Int)

/** Sugerencia para autocompletar el nombre: nombre tal como se escribio, grado e inclinacion mas habituales y cuanta gente/veces. */
data class BoardProblemSuggestion(val name: String, val grade: String, val angle: Int?, val count: Int, val mine: Boolean = false)

/** Repeticiones y sugerencias de nombres de bloques de board (puro, sin red). */
object BoardProblemRepeats {
    const val MAX_REPS = 20
    const val MAX_NAMED_PER_SESSION = 100
    const val SUGGEST_MIN_CHARS = 2
    const val SUGGEST_MAX = 10

    /** Clave de comparacion de un nombre: recortado, sin espacios repetidos, minusculas y sin acentos. */
    fun nameKey(name: String): String = BoardProblems.fold(BoardProblems.cleanName(name))

    /** Nombres de una fila agrupados sin mayusculas ni acentos: «Fanjulandia» x3 (el primero tal como se escribio). */
    fun groupNames(names: List<String>): List<Pair<String, Int>> {
        val groups = LinkedHashMap<String, Pair<String, Int>>()
        for (n in names) { val k = nameKey(n); groups[k] = (groups[k]?.first ?: n) to ((groups[k]?.second ?: 0) + 1) }
        return groups.values.toList()
    }

    /** Cuantas repeticiones se admiten de una vez (entre 1 y [MAX_REPS]). */
    fun clampReps(n: Int): Int = n.coerceIn(1, MAX_REPS)

    /**
     * Anade [reps] veces el mismo bloque con nombre (sin nombre no anade nada). Nunca pasa de [MAX_NAMED_PER_SESSION]
     * nombres por sesion (el servidor rechazaria mas); lo que sobra sigue contando en los recuentos.
     */
    fun addRepeated(problems: List<BoardProblem>, board: BoardKind, grade: String, name: String, reps: Int): List<BoardProblem> {
        val clean = BoardProblems.cleanName(name)
        if (clean.isEmpty()) return problems
        val room = (MAX_NAMED_PER_SESSION - problems.size).coerceAtLeast(0)
        return problems + List(minOf(clampReps(reps), room)) { BoardProblem(board, grade.lowercase(), clean) }
    }

    /**
     * Los bloques con nombre de [board] agrupados por nombre normalizado + grado, con la cuenta de veces y los dias.
     * Con [query] solo los que lo contienen (sin acentos ni mayusculas). Mas recientes primero y, a igual dia, mas repetidos.
     */
    fun repeated(all: List<NamedBoardProblem>, board: BoardKind, query: String): List<RepeatedProblem> {
        val q = BoardProblems.fold(query.trim())
        val groups = LinkedHashMap<String, MutableList<NamedBoardProblem>>()
        for (p in all) {
            if (p.problem.board != board) continue
            val key = nameKey(p.problem.name)
            if (key.isEmpty() || (q.isNotEmpty() && !key.contains(q))) continue
            groups.getOrPut(key + "|" + p.problem.grade.lowercase()) { mutableListOf() }.add(p)
        }
        return groups.values.map { g ->
            val newest = g.maxByOrNull { it.date }!!
            RepeatedProblem(
                board, newest.problem.name, newest.problem.grade, g.size,
                g.minOf { it.date }, newest.date, newest.gymId,
                g.map { it.date }.distinct().sortedDescending(),
                g.groupingBy { it.date to it.gymId }.eachCount().map { (k, n) -> ProblemDay(k.first, k.second, n) }
                    .sortedWith(compareByDescending<ProblemDay> { it.date }.thenBy { it.gymId })
            )
        }.sortedWith(compareByDescending<RepeatedProblem> { it.lastDate }.thenByDescending { it.reps }.thenBy { it.name })
    }

    /** Todos los bloques con nombre de [sessions] (del movil, tambien los de «Solo board») con dia y rocodromo. */
    fun history(sessions: List<GymSession>): List<NamedBoardProblem> =
        sessions.flatMap { s -> s.boardProblems.map { NamedBoardProblem(s.gymId, s.date.toString(), it) } }

    /**
     * Sugerencias SIN red: los nombres que la persona ya uso en [board] y contienen [query] (minimo [SUGGEST_MIN_CHARS]
     * letras). Primero los que empiezan por lo escrito, luego los mas hechos y los mas recientes. Grado: el mas habitual.
     */
    fun suggestLocal(all: List<NamedBoardProblem>, board: BoardKind, query: String, limit: Int = SUGGEST_MAX): List<BoardProblemSuggestion> {
        val q = nameKey(query)
        if (q.length < SUGGEST_MIN_CHARS) return emptyList()
        val byName = LinkedHashMap<String, MutableList<NamedBoardProblem>>()
        for (p in all.sortedByDescending { it.date }) {
            if (p.problem.board != board) continue
            val key = nameKey(p.problem.name)
            if (key.contains(q)) byName.getOrPut(key) { mutableListOf() }.add(p)
        }
        return byName.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<NamedBoardProblem>>> { it.key.startsWith(q) }.thenByDescending { it.value.size })
            .take(limit.coerceIn(1, SUGGEST_MAX))
            .map { (_, g) ->
                val grade = g.groupingBy { it.problem.grade.lowercase() }.eachCount().maxByOrNull { it.value }!!.key
                BoardProblemSuggestion(g.first().problem.name, grade, null, g.size, mine = true)
            }
    }

    /** Une lo propio (primero) con lo del catalogo compartido sin repetir nombres; como mucho [SUGGEST_MAX]. */
    fun merge(local: List<BoardProblemSuggestion>, remote: List<BoardProblemSuggestion>): List<BoardProblemSuggestion> {
        val seen = HashSet<String>()
        val out = ArrayList<BoardProblemSuggestion>()
        // lo propio manda, pero si el catalogo conoce la inclinacion de ese problema se aprovecha
        for (l in local) {
            if (!seen.add(nameKey(l.name))) continue
            val shared = remote.firstOrNull { nameKey(it.name) == nameKey(l.name) }
            out += if (shared != null) l.copy(angle = shared.angle ?: l.angle, grade = l.grade.ifBlank { shared.grade }) else l
        }
        for (r in remote) if (seen.add(nameKey(r.name))) out += r.copy(mine = false)
        return out.take(SUGGEST_MAX)
    }
}
