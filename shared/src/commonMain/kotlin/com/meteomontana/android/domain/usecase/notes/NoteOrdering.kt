package com.meteomontana.android.domain.usecase.notes

import com.meteomontana.android.domain.model.Note

/** Orden de las notas de una escuela por utilidad (▲ − ▼). Pura: igual en iOS y Android. */
object NoteOrdering {

    /**
     * Más votadas primero (o menos votadas primero). El desempate es la fecha — la más reciente primero en
     * los dos sentidos — para que el orden no baile entre recargas.
     */
    fun byVotes(notes: List<Note>, mostVotedFirst: Boolean): List<Note> =
        notes.sortedWith(Comparator { a, b ->
            val sa = a.upvotesCount - a.downvotesCount
            val sb = b.upvotesCount - b.downvotesCount
            when {
                sa != sb -> if (mostVotedFirst) sb.compareTo(sa) else sa.compareTo(sb)
                else -> b.createdAt.compareTo(a.createdAt)
            }
        })
}
