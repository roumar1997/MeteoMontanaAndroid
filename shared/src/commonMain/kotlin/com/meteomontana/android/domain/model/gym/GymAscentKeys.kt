package com.meteomontana.android.domain.model.gym

/** Cómo se ha hecho una vía: con compañero (cuerda) o autoaseguro. */
enum class RouteStyle(val wire: String) { PARTNER("partner"), SOLO("solo") }

/** Los boards de entrenamiento que se pueden apuntar a mano (no hay API oficial). */
enum class BoardKind(val wire: String) {
    KILTER("kilter"), TENSION("tension"), MOONBOARD("moonboard"), SYSTEM("system");

    /** System ya no se ofrece ni se pinta (el servidor sigue aceptando su clave por compatibilidad). */
    val visible: Boolean get() = this != SYSTEM

    companion object {
        /** Los tableros que se ofrecen en los selectores y pantallas. */
        val selectable: List<BoardKind> = entries.filter { it.visible }
    }
}

/** Qué es una clave de `ascents`: un bloque (color o grado), una vía o un board. */
sealed interface AscentKey {
    data class Block(val key: String) : AscentKey
    data class Route(val style: RouteStyle, val project: Boolean, val grade: String) : AscentKey
    data class Board(val board: BoardKind, val grade: String, val angle: Int?) : AscentKey
}

/**
 * Claves de vías y boards dentro de `ascents` (el servidor las valida igual, ver BACKEND-PENDIENTE.md §8):
 * `route:<partner|solo>:<sent|project>:<grado>` y `board:<kilter|tension|moonboard|system>:<grado>[:<inclinación>]`.
 * Cualquier otra clave es un bloque (color o grado). Igual en iOS y Android.
 */
object GymAscentKeys {
    /** Grados que se ofrecen para vías (de 5 a 7b+, como la maqueta). */
    val ROUTE_GRADES: List<String> = listOf("5", "5+", "6a", "6a+", "6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+")

    /** Grados que se ofrecen para bloques sin escala de colores y para boards. */
    val BLOCK_GRADES: List<String> = listOf("5", "6a", "6a+", "6b", "6b+", "6c", "7a", "7a+")


    /** Todos los grados de la rejilla de «He entrenado» (vías y boards), de fácil a difícil. */
    val GRID_GRADES: List<String> = listOf("5", "5+", "6a", "6a+", "6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+")

    /** El orden de grados del servidor (FrenchGrade): un grado doble solo vale si sus dos grados son vecinos AQUÍ. */
    private val SERVER_ORDER = listOf(
        "3", "3+", "4", "4+", "5", "5+", "5a", "5a+", "5b", "5b+", "5c", "5c+",
        "6a", "6a+", "6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+",
        "8a", "8a+", "8b", "8b+", "8c", "8c+", "9a", "9a+", "9b", "9b+", "9c", "9c+"
    )

    /** ¿Son [a] y [b] dos grados consecutivos (6b+ y 6c; no 5+ y 6a, que el servidor no tiene pegados)? */
    fun areContiguous(a: String, b: String): Boolean {
        val i = SERVER_ORDER.indexOf(a.lowercase())
        val j = SERVER_ORDER.indexOf(b.lowercase())
        return i >= 0 && j >= 0 && (i - j == 1 || j - i == 1)
    }

    /**
     * Toque en la rejilla de grados: uno suelto; tocar otro CONTIGUO lo convierte en grado doble (6b+/6c); tocar el
     * ya elegido lo quita (de un doble queda el otro); en cualquier otro caso se empieza de nuevo con el tocado.
     */
    fun toggleGrade(selected: List<String>, tapped: String): List<String> {
        val t = tapped.lowercase()
        val cur = selected.map { it.lowercase() }
        return when {
            t in cur -> cur - t
            cur.size == 1 && areContiguous(cur[0], t) -> listOf(cur[0], t).sortedBy { SERVER_ORDER.indexOf(it) }
            else -> listOf(t)
        }
    }

    /** Grado de la clave: «6b+» o «6b+/6c», o null si no hay nada elegido. */
    fun gradeOf(selected: List<String>): String? =
        selected.map { it.lowercase() }.sortedBy { SERVER_ORDER.indexOf(it) }.takeIf { it.isNotEmpty() }?.joinToString("/")

    /** Posición de dificultad de un grado (cualquier grado doble cuenta por su grado ALTO); -1 si no es un grado francés. */
    fun gradeRank(grade: String): Int = SERVER_ORDER.indexOf(grade.substringAfter('/').trim().lowercase())

    /** El mas dificil de dos grados (o el que no sea null); los dobles cuentan por el alto. */
    fun harder(a: String?, b: String?): String? = when {
        a == null -> b
        b == null -> a
        gradeRank(b) > gradeRank(a) -> b
        else -> a
    }

    /** El grado de una clave de via, board o bloque por grado (un doble, por su grado ALTO); null si es un color. */
    fun gradeOfKey(key: String): String? {
        val raw = when (val k = parse(key)) {
            is AscentKey.Route -> k.grade
            is AscentKey.Board -> k.grade
            is AscentKey.Block -> k.key
        }
        val high = raw.substringAfter('/').trim().lowercase()
        return high.takeIf { SERVER_ORDER.contains(it) }
    }

    fun maxGradeOfKey(key: String): String? = gradeOfKey(key)

    /** El grado base (el primero) de uno doble, para darle color y ordenar: «6b+/6c» -> «6b+». */
    fun baseGrade(grade: String): String = grade.substringBefore('/').trim().lowercase()

    fun route(style: RouteStyle, project: Boolean, grade: String): String =
        "route:${style.wire}:${if (project) "project" else "sent"}:${grade.lowercase()}"

    fun board(board: BoardKind, grade: String, angle: Int? = null): String =
        "board:${board.wire}:${grade.lowercase()}" + (angle?.let { ":$it" } ?: "")

    fun parse(key: String): AscentKey {
        val p = key.split(":")
        if (p[0] == "route" && p.size == 4) {
            val style = RouteStyle.entries.firstOrNull { it.wire == p[1] }
            if (style != null && (p[2] == "sent" || p[2] == "project")) return AscentKey.Route(style, p[2] == "project", p[3])
        }
        if (p[0] == "board" && (p.size == 3 || p.size == 4)) {
            val board = BoardKind.entries.firstOrNull { it.wire == p[1] }
            if (board != null) return AscentKey.Board(board, p[2], p.getOrNull(3)?.toIntOrNull())
        }
        return AscentKey.Block(key)
    }

    fun blocks(list: List<GymAscent>): List<GymAscent> = list.filter { parse(it.key) is AscentKey.Block }
    fun routes(list: List<GymAscent>): List<GymAscent> = list.filter { parse(it.key) is AscentKey.Route }
    fun boards(list: List<GymAscent>, board: BoardKind): List<GymAscent> =
        list.filter { (parse(it.key) as? AscentKey.Board)?.board == board }

    /** Vías sumadas por grado y estilo (para las etiquetas «6A+ ×2 · compañero»), en el orden de los grados. */
    fun routeSummary(list: List<GymAscent>): List<Triple<String, RouteStyle, Int>> =
        routes(list).mapNotNull { a -> (parse(a.key) as? AscentKey.Route)?.let { Triple(it.grade, it.style, a.count) } }
            .sortedWith(compareBy({ SERVER_ORDER.indexOf(baseGrade(it.first)).let { i -> if (i < 0) 99 else i } }, { it.second }))
}
