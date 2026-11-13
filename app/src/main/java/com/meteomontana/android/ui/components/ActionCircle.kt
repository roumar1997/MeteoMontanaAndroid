package com.meteomontana.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.guide.pulseRing
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Acción de cabecera: círculo suelto de papel (borde fino y sombra suave) con el icono relleno centrado y, debajo,
 * su nombre en pequeño. Encendida ([active]) va en terracota con el icono blanco. Es la única forma de acción en las
 * cabeceras de Escuelas y de la ficha: todas a la vista, ninguna escondida en un «⋯». Sin [label] es el círculo solo
 * (el «Volver»). El círculo mide siempre lo mismo (es un objetivo táctil); el nombre crece con la letra del sistema.
 *
 * [iconContent] permite un icono propio (la oruga) en lugar de un vector. [badge] > 0 pinta el globo con el número.
 */
@Composable
fun ActionCircle(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = label,
    active: Boolean = false,
    badge: Long = 0,
    enabled: Boolean = true,
    pulsing: Boolean = false,
    activeColor: Color? = null,
    iconContent: (@Composable (tint: Color) -> Unit)? = null
) {
    val fill = activeColor ?: terraFillColor()
    val tint = if (active) Color.White else MaterialTheme.colorScheme.onBackground
    Column(
        modifier
            .defaultMinSize(minWidth = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (pulsing) Modifier.pulseRing() else Modifier)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(if (active) fill else MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, if (active) fill else MaterialTheme.colorScheme.outline), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val glyph: @Composable () -> Unit = {
                if (iconContent != null) iconContent(tint)
                else if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            if (badge > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(if (badge > 9) "9+" else badge.toString(), color = Color.White)
                    }
                }) { glyph() }
            } else glyph()
        }
        if (label != null) {
            Text(
                label,
                color = if (active) fill else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
            )
        }
    }
}
