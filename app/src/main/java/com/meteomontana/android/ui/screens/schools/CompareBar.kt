package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonSchoolRow
import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.ui.theme.terraFillColor
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.ui.components.SchoolListItem
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import kotlinx.coroutines.flow.first

/** Barra flotante de comparar escuelas (aparece al seleccionar con pulsación larga). */
@Composable
internal fun CompareBar(
    selection: Set<String>,
    onClear: () -> Unit,
    onCompare: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // POR ENCIMA de la cápsula de pestañas. Es una barra
            // flotante anclada abajo, así que el contentPadding de la
            // lista no la alcanza: se le da su propio hueco o el botón
            // COMPARAR queda debajo de las pestañas y no se puede
            // pulsar (lo cazó Rodrigo intentando comparar dos escuelas).
            .padding(bottom = com.meteomontana.android.ui.components.LocalTabBarInset.current)
            .padding(Spacing.md)
            .clip(MaterialTheme.shapes.small)
            // Fondo FIJO oscuro: onBackground se invierte en modo
            // oscuro y la barra salía blanca y deslumbrante.
            .background(androidx.compose.ui.graphics.Color(0xFF17170F))
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        Text(
            stringResource(R.string.school_list_screen_v2_1_s_seleccionada_2_s, selection.size, if (selection.size > 1) "s" else ""),
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.weight(1f)
        )
        // Botón Comparar grande (a partir de 2). Con 1, pista de qué falta.
        if (selection.size >= 2) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(terraFillColor())
                    .clickable { onCompare(selection.toList()); onClear() }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.schools_compare) + " ▸",
                    style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        } else {
            Text(
                stringResource(R.string.school_list_screen_v2_elige_otra_para_comparar),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = Spacing.sm)
            )
        }
    }
}
