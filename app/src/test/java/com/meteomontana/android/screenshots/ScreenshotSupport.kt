package com.meteomontana.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.meteomontana.android.ui.theme.MeteoMontanaTheme
import com.meteomontana.android.util.AppText
import org.robolectric.RuntimeEnvironment

/** Las cuatro capturas que sacamos de cada componente (ver [captureVariants]). */
enum class ShotVariant(val suffix: String, val dark: Boolean, val fontScale: Float, val widthDp: Int) {
    LIGHT("light", dark = false, fontScale = 1f, widthDp = 411),
    DARK("dark", dark = true, fontScale = 1f, widthDp = 411),
    FONT_2X("font2x", dark = false, fontScale = 2f, widthDp = 411),
    NARROW("narrow360", dark = false, fontScale = 1f, widthDp = 360),
}

/** Carpeta de referencias. Se graba SOLO en Linux (CI): ver .github/workflows/screenshots-record.yml. */
const val SCREENSHOT_DIR = "src/test/screenshots"

@Composable
private fun ShotFrame(variant: ShotVariant, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    MeteoMontanaTheme(darkTheme = variant.dark) {
        CompositionLocalProvider(LocalDensity provides Density(base.density, variant.fontScale)) {
            Box(
                Modifier
                    .testTag(SHOT_TAG)
                    .width(variant.widthDp.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) { content() }
        }
    }
}

private const val SHOT_TAG = "shot"

/**
 * Pinta [content] una vez y la captura en claro, oscuro, letra al doble y 360 dp de ancho:
 * `<area>_<nombre>_<variante>.png`. Solo datos fijos dentro de [content] (nada de reloj ni red).
 */
fun ComposeContentTestRule.captureVariants(area: String, name: String, content: @Composable () -> Unit) {
    AppText.init(RuntimeEnvironment.getApplication()) // textos desde código no composable (días, factores…)
    var current by mutableStateOf(ShotVariant.LIGHT)
    setContent { ShotFrame(current, content) }
    ShotVariant.entries.forEach { variant ->
        current = variant
        waitForIdle()
        onNodeWithTag(SHOT_TAG).captureRoboImage("$SCREENSHOT_DIR/${area}_${name}_${variant.suffix}.png")
    }
}
