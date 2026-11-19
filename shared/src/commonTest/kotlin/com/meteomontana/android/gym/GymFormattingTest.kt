package com.meteomontana.android.gym

import com.meteomontana.android.domain.model.gym.GymDayLabels
import com.meteomontana.android.domain.model.gym.GymDistance
import com.meteomontana.android.domain.model.gym.GymHoursFormatter
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymLinks
import com.meteomontana.android.domain.model.gym.GymPrice
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymFormattingTest {

    private val labels = GymDayLabels(
        monFri = "L–V", satSun = "S–D", sat = "S", sun = "D", daily = "Todos los días",
        shortNames = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    )

    @Test fun commonDayGroupsUseTheCompactLabels() {
        assertEquals("L–V", GymHoursFormatter.daysLabel("MON-FRI", labels))
        assertEquals("S–D", GymHoursFormatter.daysLabel("SAT,SUN", labels))
        assertEquals("S–D", GymHoursFormatter.daysLabel("SAT-SUN", labels))
        assertEquals("Todos los días", GymHoursFormatter.daysLabel("MON-SUN", labels))
        assertEquals("S", GymHoursFormatter.daysLabel("SAT", labels))
        assertEquals("D", GymHoursFormatter.daysLabel("SUN", labels))
    }

    @Test fun otherCombinationsUseTheShortNames() {
        assertEquals("lun–mié", GymHoursFormatter.daysLabel("MON-WED", labels))
        assertEquals("lun, vie", GymHoursFormatter.daysLabel("MON, FRI", labels))
        assertEquals("XYZ", GymHoursFormatter.daysLabel("XYZ", labels))
    }

    @Test fun midnightIsShownAsZeroZero() {
        val e = GymHoursFormatter.entries(listOf(OpeningSlot("MON-FRI", "10:00", "24:00")), labels).single()
        assertEquals("L–V", e.daysLabel)
        assertEquals("10:00", e.open)
        assertEquals("00:00", e.close)
        assertTrue(GymHoursFormatter.entries(null, labels).isEmpty())
    }

    @Test fun distanceUsesMetersBelowAKilometre() {
        assertEquals(GymDistance(850, true), GymDistance.of(0.85))
        assertEquals(GymDistance(1, true), GymDistance.of(0.0))
        assertEquals(GymDistance(18, false), GymDistance.of(18.2))
        assertNull(GymDistance.of(null))
    }

    @Test fun priceNeedsADateAndAFreeWallNeedsNone() {
        val free = gym(kind = GymKind.FREE)
        assertTrue(GymPrice.of(free)!!.isFree)
        // Sin fecha de revision no se enseña el precio.
        assertNull(GymPrice.of(gym(priceSingle = 12.0)))
        val dated = gym(priceSingle = 12.0).copy(priceUpdatedAt = "2026-10-01")
        val p = assertNotNull(GymPrice.of(dated))
        assertEquals(12.0, p.singleEur)
        assertTrue(p.unverified)
        assertFalse(GymPrice.of(dated.copy(verification = GymVerification.COMMUNITY))!!.unverified)
        // Una fecha rota tampoco vale.
        assertNull(GymPrice.of(dated.copy(priceUpdatedAt = "ayer")))
    }

    @Test fun amountsDropDecimalsOnlyWhenWhole() {
        assertEquals("12", GymPrice.amountText(12.0, ','))
        assertEquals("12,50", GymPrice.amountText(12.5, ','))
        assertEquals("9.90", GymPrice.amountText(9.9, '.'))
    }

    @Test fun linksAcceptAnyForm() {
        assertEquals("https://roca.es", GymLinks.webUrl("roca.es"))
        assertEquals("http://roca.es", GymLinks.webUrl(" http://roca.es "))
        assertEquals("https://instagram.com/roca", GymLinks.instagramUrl("@roca"))
        assertEquals("https://instagram.com/roca", GymLinks.instagramUrl("roca"))
        assertEquals("https://instagram.com/roca/", GymLinks.instagramUrl("https://instagram.com/roca/"))
    }
}
