package com.meteomontana.android.ui.screens.schools

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolsFilterSummaryTest {

    private val letters = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
    private fun label(selected: Set<Int>, today: Int) =
        dayRangeLabel(selected, today, letters, "HOY") { "$it DÍAS" }

    @Test fun `sin dias elegidos es HOY`() = assertEquals("HOY", label(emptySet(), 3))

    @Test fun `un dia es su letra`() = assertEquals("VIE", label(setOf(5), 3))

    @Test fun `dias seguidos es un tramo`() = assertEquals("VIE–DOM", label(setOf(5, 6, 7), 3))

    @Test fun `un tramo que cruza el domingo sigue siendo seguido`() =
        // Hoy jueves (4): jueves(4), viernes(5)... lunes(1) caen en la ventana de 7 dias y son seguidos si no hay huecos.
        assertEquals("SÁB–LUN", label(setOf(6, 7, 1), 4))

    @Test fun `con huecos es N DIAS`() = assertEquals("2 DÍAS", label(setOf(5, 7), 3))

    @Test fun `sin filtros tocados no hay ninguno activo`() {
        assertEquals(0, activeFilterCount(SchoolFilters(), ExploreTab.Schools, null, null, emptySet(), ExploreSortBy.Distance))
    }

    @Test fun `cada filtro movido cuenta uno`() {
        val moved = SchoolFilters(maxDistanceKm = 100.0, style = StyleFilter.Boulder, rockTypes = listOf("Granito"), onlyFavorites = true)
        assertEquals(4, activeFilterCount(moved, ExploreTab.Schools, null, null, emptySet(), ExploreSortBy.Distance))
    }

    @Test fun `en Vias-Bloques cuentan grado, orientacion y orden`() {
        assertEquals(3, activeFilterCount(SchoolFilters(), ExploreTab.Blocks, "6A", null, setOf("N"), ExploreSortBy.MostRepeated))
    }

    @Test fun `en Escuelas no cuentan los filtros de Vias-Bloques`() {
        assertEquals(0, activeFilterCount(SchoolFilters(), ExploreTab.Schools, "6A", "7A", setOf("N"), ExploreSortBy.MostRepeated))
    }
}
