package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

// Piezas del asistente «Nueva quedada» (3 pasos, maqueta A): barra de progreso, pregunta serif, tarjetas
// seleccionables, selector con − y + y etiquetas. Todo redondeado y sin medidas fijas de ancho.

/** Tres segmentos; los [step] primeros en terracota. */
@Composable
internal fun WizardProgress(step: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { i ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(CircleShape)
                    .background(if (i <= step) terraFillColor() else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
        }
    }
}

/** La pregunta grande de cada paso (serif, hasta dos líneas). */
@Composable
internal fun WizardQuestion(text: String) {
    Text(
        text, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 30.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
internal fun WizardEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Tarjeta redondeada seleccionable (resultado de búsqueda, privacidad, Bloque/Vía). */
@Composable
internal fun WizardOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val border = if (selected) BorderStroke(2.dp, terraFillColor()) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Row(
        modifier.fillMaxWidth().clip(shape).background(bg, shape).border(border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(if (selected) 13.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.weight(1f)) { content() }
        if (selected) Icon(Icons.Outlined.Check, null, tint = terraFillColor(), modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun WizardCardTitle(title: String, subtitle: String? = null, serif: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title, fontWeight = FontWeight.Bold, fontSize = if (serif) 20.sp else 15.sp,
            fontFamily = if (serif) Serif else null, color = MaterialTheme.colorScheme.onSurface
        )
        subtitle?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Selector en píldora: «−  valor  +». Con [onMinus] o [onPlus] nulos el botón se apaga. */
@Composable
internal fun WizardStepper(
    value: String,
    unit: String?,
    onMinus: (() -> Unit)?,
    onPlus: (() -> Unit)?,
    modifier: Modifier = Modifier,
    minusDescription: String = "−",
    plusDescription: String = "+"
) {
    val shape = CircleShape
    Row(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton("−", onMinus, primary = false, description = minusDescription)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
            unit?.let {
                Text(" $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        StepperButton("+", onPlus, primary = true, description = plusDescription)
    }
}

@Composable
internal fun StepperButton(symbol: String, onClick: (() -> Unit)?, primary: Boolean, description: String, size: Int = 42) {
    val enabled = onClick != null
    val bg = if (primary) terraFillColor() else MaterialTheme.colorScheme.background
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .then(if (primary) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
