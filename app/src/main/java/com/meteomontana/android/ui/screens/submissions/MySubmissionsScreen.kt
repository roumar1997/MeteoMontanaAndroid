package com.meteomontana.android.ui.screens.submissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.Submission

@Composable
fun MySubmissionsScreen(
    onBack: () -> Unit,
    viewModel: MySubmissionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Mis propuestas", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            MySubmissionsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is MySubmissionsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is MySubmissionsUiState.Success -> {
                if (s.submissions.isEmpty() && s.contributions.isEmpty()) {
                    com.meteomontana.android.ui.components.EmptyState(
                        icon = Icons.Outlined.AddLocationAlt,
                        title = "Sin propuestas todavía",
                        message = "Desde el mapa de una escuela (+ PROPONER) o con \"+ Enviar escuela\" puedes proponer parkings, piedras, sectores o escuelas nuevas. Aquí verás su estado."
                    )
                } else {
                    LazyColumn {
                        if (s.contributions.isNotEmpty()) {
                            item {
                                SectionHeader("MEJORAS DE ESCUELAS")
                            }
                            items(s.contributions) { c ->
                                ContributionRow(c)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (s.submissions.isNotEmpty()) {
                            item {
                                SectionHeader("ESCUELAS NUEVAS")
                            }
                            items(s.submissions) { sub ->
                                SubmissionRow(sub)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionRow(s: Submission) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                s.proposedName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                buildString {
                    s.proposedRockType?.let { append(it.uppercase()) }
                    s.proposedRegion?.let {
                        if (this.isNotEmpty()) append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.reviewReason != null) {
                Text("Motivo: ${s.reviewReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        StatusChip(s.status)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ContributionRow(c: Contribution) {
    val typeLabel = when (c.type) {
        "PARKING"              -> "Parking"
        "BOULDER"              -> when {
            !c.targetLineId.isNullOrBlank() -> "Corregir vía"
            !c.targetBlockId.isNullOrBlank() -> "Añadir vías"
            else                             -> "Piedra nueva"
        }
        "SECTOR"               -> "Sector"
        "POSITION_CORRECTION"  -> "Mover ubicación"
        else                   -> c.type
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(end = 8.dp).weight(1f)) {
            Text(typeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(c.schoolName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.name?.takeIf { it.isNotBlank() }?.let {
                Text(it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            if (c.reviewReason != null) {
                Text("Motivo: ${c.reviewReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        StatusChip(c.status)
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, bg, fg) = when (status) {
        "APPROVED" -> Triple("Aprobada", MaterialTheme.colorScheme.secondary, androidx.compose.ui.graphics.Color.White)
        "REJECTED" -> Triple("Rechazada", MaterialTheme.colorScheme.error, androidx.compose.ui.graphics.Color.White)
        else       -> Triple("Pendiente", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, bg, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
