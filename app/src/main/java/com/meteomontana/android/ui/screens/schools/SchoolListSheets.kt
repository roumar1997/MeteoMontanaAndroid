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

// Hojas y avisos de Escuelas: «Aportar», banner y diálogo del café, y su botón con borde.

/** Las dos formas de aportar al catalogo, cada una con su porque. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AportarSheet(
    onDismiss: () -> Unit,
    onPiedra: () -> Unit,
    onEscuela: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = com.meteomontana.android.ui.components.CumbreSheetShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()
            .cumbreSheetSurface()
            .padding(Spacing.lg)) {
            Text(
                stringResource(R.string.contribute_title),
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = Terra
            )
            Spacer(Modifier.height(Spacing.md))
            AportarOpcion(
                titulo = stringResource(R.string.contribute_block),
                detalle = stringResource(R.string.contribute_block_hint),
                onClick = onPiedra
            )
            Spacer(Modifier.height(Spacing.sm))
            AportarOpcion(
                titulo = stringResource(R.string.contribute_school),
                detalle = stringResource(R.string.contribute_school_hint),
                onClick = onEscuela
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
internal fun AportarOpcion(titulo: String, detalle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text(detalle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Banner café. Adaptativo al tema (usa surface/onSurface). */
@Composable
internal fun CoffeeBanner(onDonate: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "☕",
            modifier = Modifier.padding(end = Spacing.sm),
            style = MaterialTheme.typography.headlineMedium
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.school_list_screen_v2_te_ayuda_la_app),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.school_list_screen_v2_mantenida_con_amor_por_la),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
        OutlinedCumbreButton(text = stringResource(R.string.school_list_screen_apoyanos), onClick = { showDialog = true; onDonate() })
    }
    if (showDialog) DonateDialog(onDismiss = { showDialog = false })
}

/** Dialog que explica y abre Ko-fi en el navegador. */
@Composable
internal fun DonateDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(Spacing.lg)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("✕",
                    modifier = Modifier.clickable(onClick = onDismiss),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("☕",
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.displayMedium)
            Text(stringResource(R.string.school_list_screen_te_es_util_la),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.school_list_screen_v2_climbingteams_es_una_app_gratuita),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.md))
            Column(modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(Spacing.md)) {
                listOf(
                    stringResource(R.string.school_list_screen_v3_condiciones_de_escalada_en_tiempo),
                    stringResource(R.string.school_list_screen_v3_escuelas_cercanas_con_prevision),
                    stringResource(R.string.school_list_screen_v3_prevision_de_7_dias),
                    stringResource(R.string.school_list_screen_v3_mejor_dia_y_analisis_de)
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Box(modifier = Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .clickable {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://ko-fi.com/climbingteams"))
                    context.startActivity(intent)
                    onDismiss()
                }
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.school_list_screen_invitame_a_un_cafe),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.school_list_screen_cada_cafe_nos_acerca),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Botón outlined estilo PWA: borde `ink`, texto `ink`, fondo transparente,
 * radius muy bajo. Material3 OutlinedButton tiene esquinas redondeadas y
 * padding excesivos, así que lo construimos como Box clickable.
 */
@Composable
internal fun OutlinedCumbreButton(
    text: String,
    onClick: () -> Unit,
    textColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, com.meteomontana.android.ui.theme.CumbrePillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor ?: MaterialTheme.colorScheme.onBackground
        )
    }
}

