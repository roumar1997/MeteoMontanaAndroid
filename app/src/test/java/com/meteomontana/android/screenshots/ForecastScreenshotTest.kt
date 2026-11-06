package com.meteomontana.android.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.ScoreFactor
import com.meteomontana.android.ui.components.DayRow
import com.meteomontana.android.ui.components.FactorList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Filas del tiempo de la ficha: día de la semana y factores de la puntuación. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class ForecastScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dayRows() = rule.captureVariants("forecast", "dayRows") {
        Column {
            DayRow(DayForecast("2026-11-07", 14.5, 3.0, 0.0, 88, "MUY BUENO"), dayIndex = 0)
            DayRow(DayForecast("2026-11-08", 11.0, 4.5, 2.4, 55, "ACEPTABLE"), dayIndex = 1)
            DayRow(DayForecast("2026-11-09", 8.0, 5.0, 14.8, 12, "MALO"), dayIndex = 2)
        }
    }

    @Test fun factorList() = rule.captureVariants("forecast", "factorList") {
        FactorList(
            listOf(
                ScoreFactor("Temperatura", "9 °C", true),
                ScoreFactor("Humedad", "82 %", false),
                ScoreFactor("Viento", "12 km/h", true),
                ScoreFactor("Lluvia últimas 72 h", "4,2 mm", false),
            )
        )
    }
}
