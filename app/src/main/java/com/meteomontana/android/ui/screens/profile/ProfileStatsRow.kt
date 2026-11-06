package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonProfile
import com.meteomontana.android.ui.components.VerifiedCheck
import com.meteomontana.android.ui.components.userRing
import androidx.compose.material.icons.outlined.Language
import com.meteomontana.android.util.AppText
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile

// Fila de números del diario en el perfil (bloques, vías, escuelas…) y su celda.

@Composable
internal fun StatsRow(
    stats: JournalStats,
    onBoulders: () -> Unit,
    onRoutes: () -> Unit,
    onSchools: () -> Unit,
    onMax: () -> Unit,
    onProjects: () -> Unit,
    onMyPosts: () -> Unit,
    onOpenStats: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).guideTarget("profile.diary"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ESTADÍSTICAS arriba del todo, que se vea sin bajar (Álvaro, 2026-10-19).
        Box(Modifier.guideTarget("profile.stats")) { com.meteomontana.android.ui.components.StatsEntryButton(onClick = onOpenStats) }
        // Fila 1: contadores BLOQUES / VÍAS / ESCUELAS.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_blocks), stats.boulderCount.toString(), Modifier.weight(1f).clickable(onClick = onBoulders))
            StatCell(stringResource(R.string.profile_routes), stats.routeCount.toString(), Modifier.weight(1f).clickable(onClick = onRoutes))
            StatCell(stringResource(R.string.profile_schools), stats.schoolCount.toString(), Modifier.weight(1f).clickable(onClick = onSchools))
        }
        // Fila 2: grado máximo separado por modalidad (escalas distintas).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.profile_max_boulder), stats.maxBoulderGrade ?: "—", Modifier.weight(1f).clickable(onClick = onMax))
            StatCell(stringResource(R.string.profile_max_route), stats.maxRouteGrade ?: "—", Modifier.weight(1f).clickable(onClick = onMax))
            StatCell(stringResource(R.string.profile_projects), stats.projectCount.toString(), Modifier.weight(1f).clickable(onClick = onProjects))
        }
        // Mis publicaciones: una fila fina en vez de otra celda grande (como iOS).
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, com.meteomontana.android.ui.theme.CumbreStatCardShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, com.meteomontana.android.ui.theme.CumbreStatCardShape)
                .clickable(onClick = onMyPosts).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.feed_my_posts_section).uppercase(), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, com.meteomontana.android.ui.theme.CumbreStatCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, com.meteomontana.android.ui.theme.CumbreStatCardShape)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
