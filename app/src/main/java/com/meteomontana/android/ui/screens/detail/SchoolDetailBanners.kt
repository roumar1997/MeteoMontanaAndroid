package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.ui.components.SkeletonSchoolDetail
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.BlocksSection
import com.meteomontana.android.ui.components.MonthlyStatsSection
import com.meteomontana.android.ui.components.NotesSection
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

// Avisos de la ficha (sin conexión, previsión vieja) y compartir la escuela.

@Composable
internal fun OfflineBanner(timestamp: Long) {
    val label = remember(timestamp) { formatOfflineTimestamp(timestamp) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.school_detail_screen_sin_conexion),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.padding(start = Spacing.sm))
        Text(stringResource(R.string.school_detail_screen_v2_datos_del_1_s, label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Aviso ámbar: el forecast viene de la caché local porque la red falló.
 * Muestra la antigüedad de los datos y permite reintentar.
 */
@Composable
internal fun StaleForecastBanner(timestamp: Long, onRetry: () -> Unit) {
    val ageMin = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    val ageLabel = when {
        ageMin < 60        -> stringResource(R.string.school_detail_screen_v4_hace_min, ageMin)
        ageMin < 60 * 24   -> stringResource(R.string.school_detail_screen_v4_hace_h, ageMin / 60)
        else               -> stringResource(R.string.school_detail_screen_v3_hace_dias, ageMin / (60 * 24))
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.school_detail_screen_stale_forecast, ageLabel).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f))
        Text(stringResource(R.string.common_retry),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry))
    }
}

internal fun formatOfflineTimestamp(ms: Long): String {
    val date = java.util.Date(ms)
    val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es"))
    return fmt.format(date)
}

@Composable
internal fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

internal fun shareSchool(
    context: android.content.Context,
    school: School,
    forecast: Forecast?
) {
    // Formato WhatsApp (los *asteriscos* son negrita allí) + nuestro enlace
    // inteligente: abre la app si la tienes, o la página con las stores si no.
    val base = com.meteomontana.android.BuildConfig.API_BASE_URL.removeSuffix("api/")
    val sb = StringBuilder()
    sb.append("🧗 *").append(school.name).append("*")
    school.region?.let { sb.append(" · ").append(com.meteomontana.android.util.CatalogLabels.region(it)) }
    sb.append("\n")
    if (forecast != null) {
        val c = forecast.current
        sb.append(AppText.get(R.string.school_detail_screen_v4_indice)).append(c.score).append("/100* (")
            .append(com.meteomontana.android.util.CatalogLabels.scoreLabel(c.score)).append(")\n")
        forecast.bestWindow?.let {
            sb.append(AppText.get(R.string.school_detail_screen_v4_optimo)).append(it.start).append("–").append(it.end).append("*\n")
        }
        sb.append(if (c.dryRock) AppText.get(R.string.school_detail_screen_v4_roca_seca) else AppText.get(R.string.school_detail_screen_v4_roca_mojada))
        sb.append(" · ").append(c.temperature.toInt()).append(AppText.get(R.string.school_detail_screen_v4_viento))
            .append(c.windSpeed.toInt()).append(" km/h\n")
    }
    sb.append(AppText.get(R.string.school_detail_screen_v4_n_abrela_en_cumbre_n)).append(base).append("s/e/").append(school.id)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
        putExtra(android.content.Intent.EXTRA_SUBJECT, school.name)
    }
    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_utils_v3_compartir_escuela)))
}

internal val CONTADOR_DETALLES = java.util.concurrent.atomic.AtomicInteger(0)
