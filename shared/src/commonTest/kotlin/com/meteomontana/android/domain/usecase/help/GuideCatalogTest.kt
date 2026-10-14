package com.meteomontana.android.domain.usecase.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideCatalogTest {

    private fun guides(key: String) = listOf("es", "en").map { GuideCatalog.byKey(key, it) }

    @Test fun `cada guia existe en español e ingles`() {
        for (key in GuideCatalog.KEYS) guides(key).forEach { assertNotNull(it, "falta la guía '$key'") }
    }

    @Test fun `ninguna guia pasa del maximo de puntos y ninguna esta vacia`() {
        for (key in GuideCatalog.KEYS) for (g in guides(key)) {
            assertTrue(g!!.steps.size in 1..GuideCatalog.MAX_STEPS, "'$key' tiene ${g.steps.size} puntos")
        }
    }

    @Test fun `español e ingles señalan los mismos elementos en el mismo orden`() {
        for (key in GuideCatalog.KEYS) {
            val (es, en) = guides(key).map { g -> g!!.steps.map { it.target } }
            assertEquals(es, en, "los targets de '$key' no coinciden entre idiomas")
        }
    }

    @Test fun `todo texto esta rellenado y los targets no se repiten dentro de una guia`() {
        for (key in GuideCatalog.KEYS) for (g in guides(key)) {
            g!!.steps.forEach {
                assertTrue(it.title.isNotBlank() && it.body.isNotBlank(), "texto vacío en '$key' (${it.target})")
                assertTrue(it.target.startsWith("$key."), "el target ${it.target} no es de la guía '$key'")
            }
            assertEquals(g.steps.size, g.steps.map { it.target }.toSet().size, "target repetido en '$key'")
        }
    }

    @Test fun `un idioma desconocido cae a español y una pantalla sin guia devuelve null`() {
        assertEquals(GuideCatalog.byKey("schools", "es"), GuideCatalog.byKey("schools", "fr"))
        assertNull(GuideCatalog.byKey("radar", "es"))
    }
}
