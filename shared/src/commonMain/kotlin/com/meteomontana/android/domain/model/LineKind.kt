package com.meteomontana.android.domain.model

/**
 * Vocabulary rule: a known discipline says "bloque"/"vía" (boulder/route);
 * unknown or mixed says the neutral "ascenso" (climb). Never a generic "vía".
 */
enum class LineKind {
    BOULDER, ROUTE, UNKNOWN;

    /** Grammatical gender in Spanish (EN has none). */
    val isFeminine: Boolean get() = this == ROUTE

    companion object {
        fun of(discipline: String?): LineKind = when (discipline?.trim()?.uppercase()) {
            "BOULDER" -> BOULDER
            "ROUTE" -> ROUTE
            else -> UNKNOWN
        }

        /** Like [of] for a set of disciplines: mixed or empty is UNKNOWN. */
        fun ofAll(disciplines: Collection<String?>): LineKind =
            disciplines.map(::of).toSet().singleOrNull() ?: UNKNOWN
    }
}

/** Words for a line given its [LineKind]. `lang` is "es" or "en". */
object LineWords {
    private fun isEn(lang: String) = lang.lowercase().startsWith("en")

    fun noun(kind: LineKind, lang: String, plural: Boolean = false, capitalised: Boolean = false): String {
        val word = if (isEn(lang)) {
            when (kind) {
                LineKind.BOULDER -> if (plural) "boulders" else "boulder"
                LineKind.ROUTE -> if (plural) "routes" else "route"
                LineKind.UNKNOWN -> if (plural) "climbs" else "climb"
            }
        } else {
            when (kind) {
                LineKind.BOULDER -> if (plural) "bloques" else "bloque"
                LineKind.ROUTE -> if (plural) "vías" else "vía"
                LineKind.UNKNOWN -> if (plural) "ascensos" else "ascenso"
            }
        }
        return if (capitalised) word.replaceFirstChar { it.uppercaseChar() } else word
    }

    /** "un"/"una" (EN "a"). */
    fun article(kind: LineKind, lang: String): String =
        if (isEn(lang)) "a" else if (kind.isFeminine) "una" else "un"

    /** "hecho"/"hecha" (EN "done"). */
    fun done(kind: LineKind, lang: String): String =
        if (isEn(lang)) "done" else if (kind.isFeminine) "hecha" else "hecho"

    /** "nuevo"/"nueva" (EN "new"). */
    fun new(kind: LineKind, lang: String): String =
        if (isEn(lang)) "new" else if (kind.isFeminine) "nueva" else "nuevo"

    /** "1 bloque", "3 vías", "2 ascensos". */
    fun count(n: Int, kind: LineKind, lang: String): String =
        "$n ${noun(kind, lang, plural = n != 1)}"

    /** Same helpers taking the raw discipline ("BOULDER"/"ROUTE"/null), handy from Swift. */
    fun nounOf(discipline: String?, lang: String, plural: Boolean = false, capitalised: Boolean = false): String =
        noun(LineKind.of(discipline), lang, plural, capitalised)

    fun countOf(n: Int, discipline: String?, lang: String): String = count(n, LineKind.of(discipline), lang)

    fun newOf(discipline: String?, lang: String): String = new(LineKind.of(discipline), lang)

    /**
     * Mixed school/guide breakdown "3 bloques · 2 vías", only the types present.
     * Lines of unknown discipline ([others]) are counted as "ascensos".
     */
    fun breakdown(boulders: Int, routes: Int, lang: String, others: Int = 0): String {
        val parts = buildList {
            if (boulders > 0) add(count(boulders, LineKind.BOULDER, lang))
            if (routes > 0) add(count(routes, LineKind.ROUTE, lang))
            if (others > 0) add(count(others, LineKind.UNKNOWN, lang))
        }
        return if (parts.isEmpty()) count(0, LineKind.UNKNOWN, lang) else parts.joinToString(" · ")
    }
}
