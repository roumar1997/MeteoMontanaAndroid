package com.meteomontana.android.ui.screens.weather

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.meteomontana.android.data.api.dto.FavoriteSchoolDto
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.components.FavoritesGridTable
import com.meteomontana.android.ui.components.forecastBody

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) viewModel.tryLoad()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            WeatherUiState.Loading -> {
                TopBar(title = "Tiempo", subtitle = "")
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            WeatherUiState.NeedPermission -> {
                TopBar(title = "Tiempo", subtitle = "")
                PermissionPrompt { locationPermission.launchPermissionRequest() }
            }
            is WeatherUiState.Error -> {
                TopBar(title = "Tiempo", subtitle = "")
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is WeatherUiState.Success -> {
                TopBar(
                    title = "Tiempo",
                    subtitle = if (s.selectedFavoriteId == null) "En tu ubicación"
                               else s.favorites.firstOrNull { it.id == s.selectedFavoriteId }?.name ?: ""
                )
                if (s.favorites.isNotEmpty()) {
                    FavoriteChips(s.favorites, s.selectedFavoriteId, viewModel::selectFavorite)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    forecastBody(s.forecast)
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                    if (s.grid != null && s.grid.rows.isNotEmpty()) {
                        item { FavoritesGridTable(grid = s.grid) }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun FavoriteChips(
    favorites: List<FavoriteSchoolDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { CumbreChip("Ubicación", selectedId == null, { onSelect(null) }) }
        items(favorites) { fav ->
            CumbreChip(fav.name, fav.id == selectedId, { onSelect(fav.id) })
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Necesitamos tu ubicación",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Para mostrarte el tiempo donde estás.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1A), contentColor = Color.White),
            shape = MaterialTheme.shapes.small
        ) { Text("DAR PERMISO") }
    }
}
