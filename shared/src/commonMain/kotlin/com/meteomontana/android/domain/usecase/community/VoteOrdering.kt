package com.meteomontana.android.domain.usecase.community

import com.meteomontana.android.domain.util.gradeScore

/** Una barra de votos: la opción (un grado o una orientación) y cuántos votos tiene. */
data class VoteBar(val option: String, val count: Int)

/** Orden de las barras de votos (grado u orientación). Pura: igual en iOS y Android. */
object VoteOrdering {

    /**
     * Más votado primero. En empate, por grado (del más fácil al más difícil) y, si no es un grado
     * (orientaciones), por nombre — así las barras NO cambian de sitio entre aperturas.
     */
    fun ordered(votes: Map<String, Int>): List<VoteBar> =
        votes.map { VoteBar(it.key, it.value) }
            .sortedWith(
                compareByDescending<VoteBar> { it.count }
                    .thenBy { gradeScore(it.option) ?: Int.MAX_VALUE }
                    .thenBy { it.option }
            )
}
