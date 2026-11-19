package com.meteomontana.android.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.domain.model.gym.GymVerification
import com.meteomontana.android.domain.model.gym.OpeningSlot
import com.meteomontana.android.ui.screens.gyms.GymInfoSections
import com.meteomontana.android.ui.screens.gyms.GymListRow
import com.meteomontana.android.ui.screens.gyms.GymMapCard
import com.meteomontana.android.ui.screens.gyms.GymPresentation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rocódromos: fila de la lista, tarjeta de cristal del mapa y la ficha (completa y con todo a NULL), en claro,
 * oscuro, letra al doble y 360 dp. Las referencias se graban en Linux (CI), como el resto de capturas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class GymScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun gym(
        id: Long, name: String, kind: GymKind, discipline: GymDiscipline, full: Boolean
    ) = Gym(
        id = id, slug = "g$id", name = name, chain = if (full) "Sputnik" else null, kind = kind, discipline = discipline,
        region = "Madrid", province = "Madrid", city = "Las Rozas", address = if (full) "Calle de París 2, Las Rozas" else null,
        lat = 40.5, lon = -3.9, website = null, instagram = null,
        openingHours = if (full) listOf(OpeningSlot("MON-FRI", "10:00", "23:00"), OpeningSlot("SAT,SUN", "10:00", "21:00")) else null,
        priceSingleEur = if (full) 12.0 else null, pricePassEur = if (full) 90.0 else null,
        pricePassLabel = if (full) "Bono 10" else null, priceUpdatedAt = if (full) "2026-11-01" else null,
        hasAutobelay = if (full) true else null, hasGearRental = if (full) true else null,
        kidsFriendly = if (full) false else null, beginnerFriendly = null, outdoor = kind == GymKind.FREE,
        verification = if (full) GymVerification.COMMUNITY else GymVerification.UNCONFIRMED,
        source = if (full) null else "OpenStreetMap", hidden = false, updatedAt = null
    )

    private val commercial = gym(1, "Sputnik Las Rozas", GymKind.COMMERCIAL, GymDiscipline.BOTH, full = true)
    private val municipal = gym(2, "Roc 30 · Manzanares", GymKind.MUNICIPAL, GymDiscipline.ROPE, full = false)
    private val free = gym(3, "Boulder puente Almendrales con un nombre bastante largo", GymKind.FREE, GymDiscipline.BOULDER, full = false)

    @Test fun listRows() = rule.captureVariants("gyms", "listRows") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GymListRow(GymListItem(commercial, 18.2), GymOpenStatus.Open("23:00"), isFavorite = true, onClick = {})
            GymListRow(GymListItem(municipal, 9.0), GymOpenStatus.Unknown, isFavorite = false, onClick = {})
            GymListRow(GymListItem(free, 0.6), GymOpenStatus.Unknown, isFavorite = false, onClick = {})
        }
    }

    @Test fun mapCard() = rule.captureVariants("gyms", "mapCard") {
        GymMapCard(
            gym = commercial, status = GymOpenStatus.Open("23:00"), distanceText = GymPresentation.distanceText(18.2),
            onClose = {}, onDirections = {}, onOpen = {}
        )
    }

    @Test fun detailSectionsFull() = rule.captureVariants("gyms", "detailFull") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { GymInfoSections(commercial) }
    }

    @Test fun detailSectionsAllNull() = rule.captureVariants("gyms", "detailAllNull") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { GymInfoSections(municipal.copy(discipline = GymDiscipline.UNKNOWN)) }
    }
}
