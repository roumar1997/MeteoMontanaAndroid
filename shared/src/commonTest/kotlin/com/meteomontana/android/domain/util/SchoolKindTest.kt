package com.meteomontana.android.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** El icono de la lista (mosquetón / crashpad / los dos) sale del `style` del catálogo. */
class SchoolKindTest {

    @Test fun via() {
        val k = SchoolKind.from("Vía")
        assertTrue(k.hasRoutes); assertFalse(k.hasBoulders)
    }

    @Test fun bloque() {
        val k = SchoolKind.from("Bloque")
        assertTrue(k.hasBoulders); assertFalse(k.hasRoutes)
    }

    @Test fun ambos() {
        val k = SchoolKind.from("Bloque,Vía")
        assertTrue(k.hasBoulders); assertTrue(k.hasRoutes)
    }

    @Test fun sinTildeNiMayusculas() {
        assertTrue(SchoolKind.from("VIA").hasRoutes)
        assertTrue(SchoolKind.from("bloque").hasBoulders)
    }

    @Test fun isSingleSoloSiEsUnaDeLasDos() {
        assertTrue(SchoolKind.from("Vía").isSingle)
        assertTrue(SchoolKind.from("Bloque").isSingle)
        assertFalse(SchoolKind.from("Bloque,Vía").isSingle)
        assertFalse(SchoolKind.from(null).isSingle)
    }

    @Test fun sinEstiloNoMuestraNada() {
        assertTrue(SchoolKind.from(null).isEmpty)
        assertTrue(SchoolKind.from("").isEmpty)
        assertTrue(SchoolKind.from("otra cosa").isEmpty)
        assertEquals(SchoolKind(false, false), SchoolKind.from(null))
    }
}
