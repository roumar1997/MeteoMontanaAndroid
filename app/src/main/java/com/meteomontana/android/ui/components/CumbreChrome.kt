package com.meteomontana.android.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.theme.ChromeTreatment
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * El armazón de Cumbre: barra de pestañas y hojas, con material.
 *
 * **Un solo sitio, a propósito.** Las pantallas no saben que existe Haze, ni
 * qué versión de Android hay debajo, ni cómo se difumina nada: piden "ponme
 * armazón" y ya. Si mañana se cambia de librería o se quita el efecto, se toca
 * este fichero y nadie más se entera.
 *
 * **Cómo funciona el truco.** Difuminar el fondo exige dos piezas que tienen
 * que conocerse: quien PONE el fondo (el contenido de la app) y quien lo LEE
 * (la barra). Se conectan a través de un estado compartido que viaja por el
 * árbol de Compose, de ahí el CompositionLocal — es la forma de que una barra
 * dibujada al fondo del Scaffold sepa qué había detrás sin pasárselo a mano por
 * quince pantallas.
 */

/** El puente entre el contenido que se difumina y el armazón que lo lee. */
val LocalChromeBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** Qué tratamiento está activo. Lo fija [CumbreChromeHost]. */
val LocalChromeTreatment = staticCompositionLocalOf { ChromeTreatment.SOLIDO }

/**
 * Envuelve la app entera. Decide qué tratamiento cabe en ESTE móvil y prepara
 * el puente.
 *
 * [deseado] es lo que el usuario ha elegido; lo que se aplica puede ser menos
 * si el móvil no da para más. Esa rebaja la hace [ChromeTreatment.paraApi], que
 * está aparte justamente para poder probarla.
 */
@Composable
fun CumbreChromeHost(
    deseado: ChromeTreatment,
    content: @Composable () -> Unit
) {
    val efectivo = remember(deseado) {
        ChromeTreatment.paraApi(Build.VERSION.SDK_INT, deseado)
    }
    val backdrop = remember { HazeState() }
    CompositionLocalProvider(
        LocalChromeTreatment provides efectivo,
        LocalChromeBackdrop provides backdrop.takeIf { efectivo != ChromeTreatment.SOLIDO },
        content = content
    )
}

/**
 * Marca este contenido como "lo que se ve por detrás del armazón".
 *
 * Va en el contenedor del contenido de la app. Con tratamiento sólido no hace
 * absolutamente nada, ni cuesta nada.
 */
@Composable
fun Modifier.cumbreBackdrop(): Modifier {
    val backdrop = LocalChromeBackdrop.current ?: return this
    return this.haze(backdrop)
}

/**
 * Pinta la superficie del armazón: la cápsula de las pestañas, el fondo de una
 * hoja.
 *
 * Con desenfoque disponible, lee el fondo y lo difumina. Sin él, cae a color
 * liso — y no es un parche feo: es lo que la app lleva enseñando desde
 * siempre.
 */
@Composable
fun Modifier.cumbreChromeSurface(shape: Shape): Modifier {
    val tratamiento = LocalChromeTreatment.current
    val backdrop = LocalChromeBackdrop.current
    val superficie = MaterialTheme.colorScheme.surface
    val borde = MaterialTheme.colorScheme.outline

    val conFondo = if (tratamiento == ChromeTreatment.SOLIDO || backdrop == null) {
        this.clip(shape).background(superficie)
    } else {
        // El tinte NO es decorativo: sin un velo del color del papel por
        // encima, el desenfoque deja pasar los colores de lo que hay debajo y
        // los iconos de las pestañas dejan de leerse sobre según qué mapa.
        // La forma la marca el clip de antes: hazeChild difumina lo que hay
        // detrás del área ya recortada.
        this.clip(shape).hazeChild(
            state = backdrop,
            style = HazeStyle(
                blurRadius = RADIO_DESENFOQUE,
                tints = listOf(HazeTint(superficie.copy(alpha = OPACIDAD_TINTE))),
                // Con lo que hay debajo muy claro u oscuro el desenfoque solo no
                // separa la barra del fondo; este color de respaldo evita que la
                // cápsula se disuelva.
                backgroundColor = superficie
            )
        )
    }

    val conBorde = conFondo.border(1.dp, borde, shape)

    // El canto de luz: una diagonal clara arriba-izquierda que se apaga hacia
    // abajo. Es lo que separa "lámina translúcida" de "canto de vidrio", y es
    // la diferencia que se aprecia a simple vista entre ESMERILADO y CRISTAL.
    return if (tratamiento == ChromeTreatment.CRISTAL) {
        conBorde.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = BRILLO_CANTO),
                0.5f to Color.Transparent,
                1f to Color.White.copy(alpha = BRILLO_CANTO / 3f)
            ),
            shape = shape
        )
    } else conBorde
}

/** Forma de la cápsula de pestañas. Aquí para que barra y hojas no diverjan. */
val CumbreCapsuleShape: Shape = RoundedCornerShape(30.dp)

/** Forma de las hojas: solo las esquinas de arriba, que es por donde suben. */
val CumbreSheetShape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Acabado de una HOJA (los ModalBottomSheet).
 *
 * **Por qué aquí no hay desenfoque, aunque el tratamiento sea de cristal.** Las
 * hojas de Compose se dibujan en una VENTANA distinta a la del contenido de la
 * app. El desenfoque necesita leer los píxeles de lo que hay detrás, y desde
 * otra ventana esos píxeles sencillamente no están al alcance. No es que quede
 * feo: es que no se puede, igual que pasa con las superficies de los mapas.
 *
 * Lo que sí se hereda es el resto del acabado: el canto de luz, el borde y una
 * forma común con la barra. Es lo que hace que una hoja parezca una pieza
 * trabajada y no una losa de color plano, que era el problema real.
 */
@Composable
fun Modifier.cumbreSheetSurface(): Modifier {
    val tratamiento = LocalChromeTreatment.current
    val base = this
        .clip(CumbreSheetShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, CumbreSheetShape)

    return if (tratamiento == ChromeTreatment.CRISTAL) {
        base.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = BRILLO_CANTO),
                0.35f to Color.Transparent,
                1f to Color.Transparent
            ),
            shape = CumbreSheetShape
        )
    } else base
}

/**
 * El color que hay que pasarle al ModalBottomSheet para que el acabado de
 * [cumbreSheetSurface] se vea: si el propio sheet pinta su fondo opaco, tapa
 * el borde y el canto.
 */
@Composable
fun cumbreSheetContainerColor(): Color = Color.Transparent

// Los números del armazón, juntos y con nombre, para no tenerlos sueltos por
// las pantallas. Si el efecto queda muy fuerte o muy flojo, se tocan AQUÍ.
private val RADIO_DESENFOQUE = 24.dp
private const val OPACIDAD_TINTE = 0.72f
private const val BRILLO_CANTO = 0.55f
