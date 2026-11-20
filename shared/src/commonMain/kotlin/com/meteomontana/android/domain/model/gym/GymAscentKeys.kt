package com.meteomontana.android.domain.model.gym

/** Cómo se ha hecho una vía: con compañero (cuerda) o autoaseguro. */
enum class RouteStyle(val wire: String) { PARTNER("partner"), SOLO("solo") }

/** Los boards de entrenamiento que se pueden apuntar a mano (no hay API oficial). */
enum class BoardKind(val wire: String) { KILTER("kilter"), TENSION("tension"), MOONBOARD("moonboard") }

/** Qué es una clave de `ascents`: un bloque (color o grado), una vía o un board. */
sealed interface AscentKey {
    data class Block(val key: String) : AscentKey
    data class Route(val style: RouteStyle, val project: Boolean, val grade: String) : AscentKey
    data class Board(val board: BoardKind, val grade: String, val angle: Int?) : AscentKey
}

/**
 * Claves de vías y boards dentro de `ascents` (el servidor las valida igual, ver BACKEND-PENDIENTE.md §8):
 * `route:<partner|solo>:<sent|project>:<grado>` y `board:<kilter|tension|moonboard>:<grado>[:<inclinación>]`.
 * Cualquier otra clave es un bloque (color o grado). Igual en iOS y Android.
 */
object GymAscentKeys {
    /** Grados que se ofrecen para vías (de 5 a 7b+, como la maqueta). */
    val ROUTE_GRADES: List<String> = listOf("5", "5+", "6a", "6a+", "6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+")

    /** Grados que se ofrecen para bloques sin escala de colores y para boards. */
    val BLOCK_GRADES: List<String> = listOf("5", "6a", "6a+", "6b", "6b+", "6c", "7a", "7a+")

    val BOARD_ANGLES: List<Int> = listOf(30, 40, 45)

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
            .sortedWith(compareBy({ ROUTE_GRADES.indexOf(it.first).let { i -> if (i < 0) 99 else i } }, { it.second }))
}
