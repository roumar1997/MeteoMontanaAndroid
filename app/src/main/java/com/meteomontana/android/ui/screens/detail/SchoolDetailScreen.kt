package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.NoteDto
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.NotesSection
import com.meteomontana.android.ui.components.forecastBody

@Composable
fun SchoolDetailScreen(
    onBack: () -> Unit,
    viewModel: SchoolDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val success = state as? SchoolDetailUiState.Success

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = success?.school?.name ?: "",
            isFavorite = success?.isFavorite ?: false,
            showFavorite = success != null,
            onBack = onBack,
            onToggleFavorite = viewModel::toggleFavorite
        )
        when (val s = state) {
            is SchoolDetailUiState.Loading -> Center { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is SchoolDetailUiState.Error -> Center { Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error) }
            is SchoolDetailUiState.Success -> Content(s.school, s.forecast, s.notes, viewModel::publishNote)
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    isFavorite: Boolean,
    showFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp).weight(1f))
        if (showFavorite) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

@Composable
private fun Content(
    school: School,
    forecast: ForecastDto,
    notes: List<NoteDto>,
    onPublishNote: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        forecastBody(forecast)
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }
        item { NotesSection(notes = notes, onPublish = onPublishNote) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
