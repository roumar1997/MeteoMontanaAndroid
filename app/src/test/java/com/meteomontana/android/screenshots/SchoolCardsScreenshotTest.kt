package com.meteomontana.android.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.meteomontana.android.domain.model.RangeDayScore
import com.meteomontana.android.domain.model.RangeScore
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.DayRangeRow
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.components.ScoreRing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tarjeta de escuela de la lista de Escuelas, el anillo de puntuación y la fila de días. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class SchoolCardsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val school = School(
        id = "la-pedriza", name = "La Pedriza", location = "Manzanares el Real", region = "Madrid",
        style = "Vías y bloques", rockType = "Granito", lat = 40.73, lon = -3.87, source = "catalog"
    )
    private val range = RangeScore(
        id = "la-pedriza", combinedScore = 72, avgScore = 78,
        days = listOf(
            RangeDayScore("2026-11-07", 85, 0.0, 5, false),
            RangeDayScore("2026-11-08", 64, 1.2, 40, false),
            RangeDayScore("2026-11-09", 30, 6.5, 85, true),
            RangeDayScore("2026-11-10", 78, 0.0, 10, false),
        ),
        rainDays = 1, maxRainMm = 6.5
    )

    @Test fun schoolListItem_withScoreAndDistance() = rule.captureVariants("schools", "listItem") {
        SchoolListItem(
            school = school, todayScore = 72, distanceKm = 26.4, dry = true, range = range,
            isFavorite = true, onClick = {}
        )
    }

    @Test fun schoolListItem_noScoreLongName() = rule.captureVariants("schools", "listItemNoScore") {
        SchoolListItem(
            school = school.copy(name = "Cuenca Alta del Manzanares - Sector Cinco Cerros y Alrededores"),
            onClick = {}
        )
    }

    @Test fun scoreRingAndDayRow() = rule.captureVariants("schools", "scoreRing") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreRing(score = 91)
            ScoreRing(score = 45)
            ScoreRing(score = 12)
            ScoreRing(score = null)
            DayRangeRow(range = range)
        }
    }
}
