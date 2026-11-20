package com.meteomontana.android.schools

import com.meteomontana.android.ui.screens.schools.filtersRowMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersRowModeTest {

    @Test fun `pantalla ancha y letra normal lo enseña todo`() {
        val m = filtersRowMode(412f, 1f, active = false, hasFavs = true, hasDays = false)
        assertTrue(m.showCount); assertFalse(m.clearAsIcon); assertFalse(m.favsAsIcon)
    }

    @Test fun `estrecha con BORRAR oculta primero el contador`() {
        val m = filtersRowMode(340f, 1f, active = true, hasFavs = false, hasDays = false)
        assertFalse(m.showCount); assertFalse(m.clearAsIcon)
    }

    @Test fun `muy estrecha o letra grande deja BORRAR y favoritos en su icono`() {
        val m = filtersRowMode(320f, 1.6f, active = true, hasFavs = true, hasDays = true)
        assertFalse(m.showCount); assertTrue(m.clearAsIcon); assertTrue(m.favsAsIcon)
    }

    @Test fun `sin filtros activos ni favoritos caben el contador y la fila`() {
        val m = filtersRowMode(360f, 1f, active = false, hasFavs = false, hasDays = false)
        assertTrue(m.showCount)
    }

    @Test fun `lo que se recorta crece de forma monotona al estrechar`() {
        var hidden = 0
        for (w in listOf(480f, 412f, 360f, 320f, 280f)) {
            val m = filtersRowMode(w, 1.3f, active = true, hasFavs = true, hasDays = true)
            val level = (if (!m.showCount) 1 else 0) + (if (m.clearAsIcon) 1 else 0) + (if (m.favsAsIcon) 1 else 0)
            assertTrue("a $w dp el recorte no puede bajar", level >= hidden)
            hidden = level
        }
    }
}
