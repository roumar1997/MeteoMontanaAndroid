package com.meteomontana.android.domain.model.gym

/** Un bloque de board con nombre (opcional) dentro de una sesion: Kilter, 6C, «Slab Master»; [attempts] = intentos que costo esa vez (opcional, 1 = flash). */
data class BoardProblem(val board: BoardKind, val grade: String, val name: String, val attempts: Int? = null)

/** Un bloque con nombre y cuando/donde se hizo (viene del resumen del servidor, todo el historial). */
data class NamedBoardProblem(val gymId: Long, val date: String, val problem: BoardProblem)

/**
 * Nombres de bloques de board. Los recuentos (`ascents`) mandan: no puede haber mas bloques con nombre de un
 * board+grado que ascensiones de ese board+grado (igual que el servidor). Igual en iOS y Android.
 */
object BoardProblems {
    const val MAX_NAME = 60

    /** Nombre recortado, sin saltos ni espacios repetidos y como mucho [MAX_NAME] caracteres; vacio = sin nombre. */
    fun cleanName(raw: String): String {
        val s = raw.replace(Regex("\\s+"), " ").trim()
        return if (s.length > MAX_NAME) s.take(MAX_NAME).trim() else s
    }

    /** Anade un bloque con nombre; sin nombre (vacio) no anade nada. */
    fun add(problems: List<BoardProblem>, board: BoardKind, grade: String, name: String): List<BoardProblem> {
        val clean = cleanName(name)
        return if (clean.isEmpty()) problems else problems + BoardProblem(board, grade.lowercase(), clean)
    }

    /** Quita los nombres que ya no caben en los recuentos de [ascents] (se queda con los primeros de cada board+grado). */
    fun prune(problems: List<BoardProblem>, ascents: List<GymAscent>): List<BoardProblem> {
        val left = HashMap<String, Int>()
        for (a in ascents) (GymAscentKeys.parse(a.key) as? AscentKey.Board)?.let { k ->
            left[slot(k.board, k.grade)] = (left[slot(k.board, k.grade)] ?: 0) + a.count
        }
        return problems.filter { p ->
            val k = slot(p.board, p.grade)
            val n = left[k] ?: 0
            if (n > 0) { left[k] = n - 1; true } else false
        }
    }

    /** Los nombres de cada clave de board de [items] repartidos en orden (mismo board+grado: se van gastando). */
    fun namesByKey(items: List<GymAscent>, problems: List<BoardProblem>): Map<String, List<String>> {
        val pool = HashMap<String, MutableList<String>>()
        for (p in problems) pool.getOrPut(slot(p.board, p.grade)) { mutableListOf() }.add(p.name)
        val out = LinkedHashMap<String, List<String>>()
        for (a in items) {
            val k = GymAscentKeys.parse(a.key) as? AscentKey.Board ?: continue
            val names = pool[slot(k.board, k.grade)] ?: continue
            val take = names.take(a.count)
            repeat(take.size) { names.removeAt(0) }
            if (take.isNotEmpty()) out[a.key] = out[a.key].orEmpty() + take
        }
        return out
    }

    /** Busca por nombre (sin mayusculas ni acentos) entre los bloques con nombre de ese tablero; mas recientes primero. */
    fun search(all: List<NamedBoardProblem>, board: BoardKind, query: String): List<NamedBoardProblem> {
        val q = fold(query.trim())
        return all.filter { it.problem.board == board && (q.isEmpty() || fold(it.problem.name).contains(q)) }
            .sortedByDescending { it.date }
    }

    /** Minusculas y sin acentos, para que «n» encuentre «ñ»... y «a» encuentre «á». */
    fun fold(s: String): String = buildString {
        for (c in s.lowercase()) append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                else -> c
            }
        )
    }

    private fun slot(board: BoardKind, grade: String) = board.wire + ":" + grade.lowercase()
}
