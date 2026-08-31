package com.meteomontana.android.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Grados dobles "7a/7a+" — el espejo Swift es GradeRangeUI.swift. */
class GradeRangeTest {

    @Test fun base_toma_el_primero_del_rango() {
        assertEquals("7A", GradeRange.base("7a/7a+"))
        assertEquals("6B", GradeRange.base("6b"))
        assertNull(GradeRange.base(null))
        assertNull(GradeRange.base(""))
    }

    @Test fun primer_toque_deja_el_grado_suelto() {
        assertEquals("7A", GradeRange.toggle(null, "7a"))
    }

    @Test fun segundo_toque_forma_el_rango_de_facil_a_dificil() {
        assertEquals("7A/7A+", GradeRange.toggle("7A", "7a+"))
        // Aunque se toque al revés, el rango sale ordenado.
        assertEquals("7A/7A+", GradeRange.toggle("7A+", "7a"))
    }

    @Test fun tocar_uno_ya_puesto_lo_quita() {
        assertEquals("7A+", GradeRange.toggle("7A/7A+", "7a"))
        assertNull(GradeRange.toggle("7A", "7a"))
    }

    @Test fun un_tercer_grado_reinicia_la_seleccion() {
        assertEquals("6B", GradeRange.toggle("7A/7A+", "6b"))
    }

    @Test fun contains_ve_los_dos_grados_del_rango() {
        assertTrue(GradeRange.contains("7A/7A+", "7a"))
        assertTrue(GradeRange.contains("7A/7A+", "7a+"))
        assertFalse(GradeRange.contains("7A/7A+", "6b"))
    }

    @Test fun el_rango_puntua_colorea_y_filtra_como_su_grado_base() {
        assertEquals(gradeScore("7a"), gradeScore("7a/7a+"))
        assertEquals(gradeArgb("7a"), gradeArgb("7a/7a+"))
    }
}
