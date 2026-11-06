package com.meteomontana.android.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.components.EmptyState
import com.meteomontana.android.ui.components.MochilaCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Controles comunes: chips de filtro, celdas de la mochila y pantalla vacía. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class ControlsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chips() = rule.captureVariants("controls", "chips") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CumbreChip("Cerca", selected = true, onClick = {})
            CumbreChip("Granito", selected = false, onClick = {})
            CumbreChip("Boulder", selected = false, onClick = {})
        }
    }

    @Test fun mochilaCards() = rule.captureVariants("controls", "mochila") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MochilaCard("Cuerda de 70 m", selected = true, onClick = {})
            MochilaCard("Crashpad", selected = false, onClick = {})
        }
    }

    @Test fun emptyState() = rule.captureVariants("controls", "emptyState") {
        EmptyState(
            icon = Icons.Outlined.Search, title = "Sin resultados",
            message = "Prueba con otro grado o amplía la distancia para ver más escuelas.",
            actionLabel = "Quitar filtros", onAction = {}
        )
    }
}
