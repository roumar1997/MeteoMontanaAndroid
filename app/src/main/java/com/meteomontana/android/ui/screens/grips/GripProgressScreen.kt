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

        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            items(gripTypes, key = { it.id }) { g ->
                val isSel = g.id == selected?.id
                Text(
                    g.label(),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable { viewModel.selectGripType(g) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LEFT" to "IZQUIERDA", "RIGHT" to "DERECHA").forEach { (value, label) ->
                val isSel = hand == value
                Text(
                    label,
                    modifier = Modifier.weight(1f)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { viewModel.selectHand(value) }
                        .padding(vertical = 12.dp),
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
