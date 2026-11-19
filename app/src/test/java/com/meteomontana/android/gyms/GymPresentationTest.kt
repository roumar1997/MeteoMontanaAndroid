package com.meteomontana.android.gyms

import com.meteomontana.android.EsTextSource
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot
import com.meteomontana.android.ui.screens.gyms.GymPresentation
import com.meteomontana.android.ui.screens.gyms.instagramUrl
import com.meteomontana.android.ui.screens.gyms.webUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Textos de la ficha de un rocódromo (los de docs/rocodromos/TEXTOS.md, en español). */
class GymPresentationTest {

    @Before fun setUp() {
        EsTextSource.install()
        java.util.Locale.setDefault(java.util.Locale("es", "ES"))
    }

    private fun gym(
        kind: GymKind = GymKind.COMMERCIAL,
        discipline: GymDiscipline = GymDiscipline.BOTH,
        outdoor: Boolean = false,
        single: Double? = null,
        pass: Double? = null,
        passLabel: String? = null,
        priceDate: String? = null,
        verification: GymVerification = GymVerification.UNCONFIRMED
    ) = Gym(
        id = 1, slug = "g", name = "Sputnik", chain = "Sputnik", kind = kind, discipline = discipline, region = "Madrid",
        province = "Madrid", city = "Las Rozas", address = null, lat = 40.5, lon = -3.9, website = null, instagram = null,
        openingHours = null, priceSingleEur = single, pricePassEur = pass, pricePassLabel = passLabel,
        priceUpdatedAt = priceDate, hasAutobelay = null, hasGearRental = null, kidsFriendly = null,
        beginnerFriendly = null, outdoor = outdoor, verification = verification, source = null, hidden = false, updatedAt = null
    )

    @Test fun `modalidad en etiquetas y muro al aire libre`() {
        assertEquals(listOf("Bloque", "Cuerda"), GymPresentation.disciplineChips(gym()))
        assertEquals(listOf("Bloque", "Muro al aire libre"), GymPresentation.disciplineChips(gym(GymKind.FREE, GymDiscipline.BOULDER,
            outdoor = true)))
        assertEquals(emptyList<String>(), GymPresentation.disciplineChips(gym(discipline = GymDiscipline.UNKNOWN)))
    }

    @Test fun `etiqueta de precio no afirma lo que no sabe`() {
        assertEquals("Gratis", GymPresentation.priceTag(gym(GymKind.FREE)))
        assertEquals("De pago", GymPresentation.priceTag(gym(GymKind.COMMERCIAL)))
        assertNull(GymPresentation.priceTag(gym(GymKind.MUNICIPAL)))
        assertEquals("De pago", GymPresentation.priceTag(gym(GymKind.MUNICIPAL, single = 3.0)))
    }

    @Test fun `distancia en metros por debajo del kilometro y en km por encima`() {
        assertEquals("850 m", GymPresentation.distanceText(0.85))
        assertEquals("18 km", GymPresentation.distanceText(18.2))
        assertNull(GymPresentation.distanceText(null))
    }

    @Test fun `estado de apertura`() {
        assertEquals("Abierto · hasta 23:00", GymPresentation.openStatusText(GymOpenStatus.Open("23:00")))
        assertEquals("Cerrado · abre a las 10:00", GymPresentation.openStatusText(GymOpenStatus.Closed("10:00")))
        assertEquals("Cerrado", GymPresentation.openStatusText(GymOpenStatus.Closed(null)))
        assertNull(GymPresentation.openStatusText(GymOpenStatus.Unknown))
    }

    @Test fun `horario agrupa los dias habituales`() {
        val lines = GymPresentation.hoursLines(
            listOf(OpeningSlot("MON-FRI", "10:00", "23:00"), OpeningSlot("SAT,SUN", "10:00", "21:00"), OpeningSlot("MON-SUN", "09:00",
                "24:00"))
        )
        assertEquals(listOf("L–V 10:00–23:00", "S–D 10:00–21:00", "Todos los días 09:00–00:00"), lines)
        assertEquals(emptyList<String>(), GymPresentation.hoursLines(null))
    }

    @Test fun `precio sin fecha no se enseña`() {
        assertEquals(emptyList<String>(), GymPresentation.priceInfo(gym(single = 12.0)).lines)
        assertEquals(emptyList<String>(), GymPresentation.priceInfo(gym(priceDate = "2026-11-01")).lines)
    }

    @Test fun `precio con fecha y sin verificar lleva su nota`() {
        val info = GymPresentation.priceInfo(gym(single = 12.0, pass = 90.5, passLabel = "Bono 10", priceDate = "2026-11-01"))
        assertEquals(listOf("Entrada suelta ~12 €", "Bono 10 ~90,50 €"), info.lines)
        assert(info.note!!.startsWith("Revisado el ")) { info.note!! }
        assert(info.note!!.endsWith("(sin verificar)")) { info.note!! }
    }

    @Test fun `precio confirmado no dice sin verificar`() {
        val info = GymPresentation.priceInfo(gym(single = 8.0, priceDate = "2026-11-01", verification = GymVerification.COMMUNITY))
        assert(!info.note!!.contains("sin verificar"))
    }

    @Test fun `un muro gratis dice entrada gratuita aunque no haya fecha`() {
        assertEquals(listOf("Entrada gratuita"), GymPresentation.priceInfo(gym(GymKind.FREE)).lines)
    }

    @Test fun `servicios nunca dicen no por un nulo`() {
        assertEquals("Sí", GymPresentation.serviceValue(true))
        assertEquals("No", GymPresentation.serviceValue(false))
        assertEquals("Sin confirmar", GymPresentation.serviceValue(null))
    }

    @Test fun `enlaces de web e instagram`() {
        assertEquals("https://sputnik.es", webUrl("sputnik.es"))
        assertEquals("http://x.es", webUrl("http://x.es"))
        assertEquals("https://instagram.com/sputnik", instagramUrl("@sputnik"))
        assertEquals("https://instagram.com/sputnik", instagramUrl("sputnik"))
        assertEquals("https://www.instagram.com/s/", instagramUrl("https://www.instagram.com/s/"))
    }
}
