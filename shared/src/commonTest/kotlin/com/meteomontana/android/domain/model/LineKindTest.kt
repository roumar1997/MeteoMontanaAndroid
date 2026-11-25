package com.meteomontana.android.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LineKindTest {
    @Test fun parsesDiscipline() {
        assertEquals(LineKind.BOULDER, LineKind.of("BOULDER"))
        assertEquals(LineKind.ROUTE, LineKind.of(" route "))
        assertEquals(LineKind.UNKNOWN, LineKind.of(null))
        assertEquals(LineKind.UNKNOWN, LineKind.of("GYM"))
        assertEquals(LineKind.UNKNOWN, LineKind.ofAll(listOf("BOULDER", "ROUTE")))
        assertEquals(LineKind.UNKNOWN, LineKind.ofAll(emptyList()))
        assertEquals(LineKind.ROUTE, LineKind.ofAll(listOf("ROUTE", "ROUTE")))
    }

    @Test fun unknownNeverSaysVia() {
        for (plural in listOf(false, true)) for (cap in listOf(false, true)) {
            assertFalse(LineWords.noun(LineKind.UNKNOWN, "es", plural, cap).lowercase().contains("vía"))
            assertFalse(LineWords.noun(LineKind.UNKNOWN, "en", plural, cap).lowercase().contains("route"))
        }
        assertEquals("Ascensos", LineWords.noun(LineKind.UNKNOWN, "es", plural = true, capitalised = true))
        assertEquals("climb", LineWords.noun(LineKind.of(null), "en"))
    }

    @Test fun nounsAndGender() {
        assertEquals("bloque", LineWords.noun(LineKind.BOULDER, "es"))
        assertEquals("Vías", LineWords.noun(LineKind.ROUTE, "es", plural = true, capitalised = true))
        assertEquals("route", LineWords.noun(LineKind.ROUTE, "en"))
        assertEquals("una", LineWords.article(LineKind.ROUTE, "es"))
        assertEquals("un", LineWords.article(LineKind.BOULDER, "es"))
        assertEquals("hecho", LineWords.done(LineKind.UNKNOWN, "es"))
        assertEquals("nueva", LineWords.new(LineKind.ROUTE, "es"))
        assertEquals("new", LineWords.new(LineKind.BOULDER, "en"))
    }

    @Test fun countsAndBreakdown() {
        assertEquals("1 bloque", LineWords.count(1, LineKind.BOULDER, "es"))
        assertEquals("3 vías", LineWords.breakdown(0, 3, "es"))
        assertEquals("2 bloques · 1 vía", LineWords.breakdown(2, 1, "es"))
        assertEquals("2 boulders · 3 routes", LineWords.breakdown(2, 3, "en"))
        assertEquals("0 ascensos", LineWords.breakdown(0, 0, "es"))
    }
}
