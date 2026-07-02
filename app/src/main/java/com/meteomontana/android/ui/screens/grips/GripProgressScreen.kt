package com.meteomontana.android.ui.screens.grips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R

@Composable
fun GripProgressScreen(
    onBack: () -> Unit,
    viewModel: GripProgressViewModel = hiltViewModel()
) {
    val gripTypes by viewModel.gripTypes.collectAsState()
    val selected by viewModel.selectedGripType.collectAsState()
    val hand by viewModel.hand.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Progreso", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            GripTypeTwoAxisSelector(
                gripTypes = gripTypes, selected = selected,
                onSelect = { viewModel.selectGripType(it) }
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
            Text("MANO", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
            HandSelector(hand = hand, onSelect = { viewModel.selectHand(it) })
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Aún no has medido este agarre con esta mano",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            GripLineChart(
                points = sessions.map { ChartPoint(it.peakKg.toFloat()) },
                modifier = Modifier.padding(16.dp)
            )
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                Text("Máximo histórico: %.1f kg".format(sessions.maxOf { it.peakKg }),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
