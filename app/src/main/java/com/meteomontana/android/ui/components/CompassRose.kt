package com.meteomontana.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.meteomontana.android.ui.theme.Terra

/**
 * Rosa de los vientos del mapa: dice hacia dónde queda el norte cuando has
 * girado el mapa con dos dedos, y al tocarla lo devuelve al norte.
 *
 * Ojo con lo que representa: aquí NO se dibuja el rumbo del móvil sino el
 * **giro del mapa**, que son cosas distintas. El rumbo del móvil es lo que se
 * usa en la brújula de elegir orientación ([CompassDial]).
 *
 * Espejo de `CompassRose.swift` en iOS.
 */
@Composable
fun CompassRoseIcon(mapBearing: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
        Canvas(modifier = Modifier.size(22.dp)) {
            // El mapa girado `bearing` grados en sentido horario deja el norte
            // `-bearing` respecto a la pantalla.
            rotate(degrees = -mapBearing, pivot = center) { aguja() }
        }
    }
}

/** Aguja de dos puntas: la que apunta al norte va en terracota. */
private fun DrawScope.aguja() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val largo = size.minDimension * 0.42f
    val ancho = size.minDimension * 0.16f

    fun punta(haciaArriba: Boolean, color: Color) {
        val signo = if (haciaArriba) -1f else 1f
        val p = Path().apply {
            moveTo(cx, cy + signo * largo)
            lineTo(cx + ancho, cy - signo * largo * 0.22f)
            lineTo(cx, cy - signo * largo * 0.05f)
            lineTo(cx - ancho, cy - signo * largo * 0.22f)
            close()
        }
        drawPath(p, color)
    }
    punta(haciaArriba = true, color = Terra)
    punta(haciaArriba = false, color = Color(0xFF8A8478))
    drawCircle(Color(0xFF2B2B28), radius = size.minDimension * 0.06f, center = Offset(cx, cy))
}

/**
 * Brújula grande para elegir la orientación de una pared: enseña el rumbo del
 * MÓVIL, con su valor en grados escrito debajo.
 *
 * El número se escribe a propósito: la brújula de un móvil se descalibra con
 * facilidad —mochilas con imanes, mosquetones, hierro cerca— y ver el grado
 * ayuda a desconfiar cuando pega un salto. Por eso esto informa y no decide:
 * la orientación la elige el usuario tocando su chip.
 */
@Composable
fun CompassDial(headingDegrees: Float?, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(120.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFD8D4C8), radius = r - 2f, style =
                androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            if (headingDegrees != null) rotate(degrees = headingDegrees, pivot = center) { aguja() }
        }
        // Letras fijas: la que gira es la aguja, como en una brújula de verdad.
        Text("N", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.TopCenter))
        Text("S", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter))
        Text("E", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd))
        Text("O", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterStart))
    }
}
