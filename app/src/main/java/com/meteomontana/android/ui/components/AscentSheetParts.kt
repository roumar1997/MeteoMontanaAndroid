package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.util.CalendarLabels

// Piezas pequeñas de la hoja de ascenso (botón de foto, fecha corta y la fila TU ALTURA), aparte de FeedPublishSheet.kt.

@androidx.compose.runtime.Composable
internal fun AscentPhotoButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Terra, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatShortDate(iso: String): String = runCatching {
    val d = java.time.LocalDate.parse(iso)
    "%d %s".format(d.dayOfMonth, CalendarLabels.monthsShort()[d.monthValue - 1].uppercase())
}.getOrDefault(iso)

/** «Mides más de 1,70 · CAMBIAR», o las tres opciones si aún no la has dicho (o al pulsar CAMBIAR). */
@Composable
internal fun AscentHeightRow(
    myHeight: com.meteomontana.android.domain.model.HeightGroup?,
    onSetHeight: (com.meteomontana.android.domain.model.HeightGroup) -> Unit
) {
    // La PRIMERA vez que apuntas algo sin altura (o con «prefiero no decirlo»): se abren las opciones y se ilumina
    // esta fila como en AYUDA, con el porqué; hay que pulsar Entendido. Una sola vez (lo recuerda la propia guía).
    val guides = com.meteomontana.android.ui.guide.LocalGuideController.current
    val nudge = remember(myHeight) {
        myHeight == com.meteomontana.android.domain.model.HeightGroup.UNSPECIFIED && !guides.isUsed(HEIGHT_GUIDE)
    }
    var choosing by remember(myHeight) { mutableStateOf(myHeight == null || nudge) }
    androidx.compose.runtime.LaunchedEffect(myHeight, nudge) {
        if ((myHeight == null || nudge) && !guides.isUsed(HEIGHT_GUIDE)) {
            kotlinx.coroutines.delay(1100)  // que la hoja termine de subir del todo: sin «flashazo» a medio camino
            guides.start(HEIGHT_GUIDE)
        }
    }
    Column(Modifier.guideTarget("ascent.height"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (choosing) {
            if (myHeight == null || nudge) Text(stringResource(R.string.ascent_height_ask), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            HeightChoiceView(selected = myHeight) { onSetHeight(it); choosing = false }
            Text(stringResource(R.string.ascent_height_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(when (myHeight) {
                        com.meteomontana.android.domain.model.HeightGroup.TALL -> R.string.ascent_height_tall
                        com.meteomontana.android.domain.model.HeightGroup.SHORT -> R.string.ascent_height_short
                        else -> R.string.ascent_height_none
                    }),
                    Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(stringResource(R.string.ascent_height_change), style = EyebrowTextStyle.copy(fontSize = 10.sp), color = Terra,
                    modifier = Modifier.clickable { choosing = true }.padding(vertical = 4.dp, horizontal = 2.dp))
            }
            if (myHeight == com.meteomontana.android.domain.model.HeightGroup.UNSPECIFIED) {
                Text(stringResource(R.string.ascent_height_share_why), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal const val HEIGHT_GUIDE = "ascent_height"

/**
 * Lo que vale el interruptor «Compartir en Comunidad» de verdad: si el servidor ha puesto en pausa publicar
 * (interruptor `feed_publish`), nunca está encendido aunque la persona lo hubiera dejado así. Guardar en el diario sigue igual.
 */
internal fun effectiveShare(share: Boolean, publishEnabled: Boolean): Boolean = share && publishEnabled

/**
 * Fila del interruptor «Compartir en Comunidad». Con la función en pausa ([enabled] = false) el interruptor queda
 * apagado y sin respuesta, y debajo sale el aviso de «en pausa».
 */
@Composable
internal fun AscentShareRow(share: Boolean, enabled: Boolean, offMessage: String?, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(enabled = enabled) { onChange(!share) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.ascent_share_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.ascent_share_sub), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(
                checked = share && enabled, onCheckedChange = onChange, enabled = enabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Terra))
        }
        if (!enabled) FeatureOffNotice(offMessage)
    }
}
