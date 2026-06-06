package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel

@Composable
fun BlocksSection(
    blocks: List<Block>,
    onAddBlock: () -> Unit,
    onBlockClick: (String) -> Unit = {},
    schoolLat: Double? = null,
    schoolLon: Double? = null,
    viewModel: SchoolDetailViewModel? = null,
    onMyProposals: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MAPA DE LA ESCUELA",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+ Proponer",
                modifier = Modifier.clickable(onClick = onAddBlock),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }

        if (schoolLat != null && schoolLon != null) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            viewModel?.let { vm ->
                SchoolMap(
                    centerLat     = schoolLat,
                    centerLon     = schoolLon,
                    blocks        = blocks,
                    viewModel     = vm,
                    onMyProposals = onMyProposals
                )
            }
        }

        if (blocks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center) {
                Text("Aún no hay bloques mapeados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(blocks) { b -> BlockCard(b, onClick = { onBlockClick(b.id) }) }
        }
    }
}

@Composable
private fun BlockCard(b: Block, onClick: () -> Unit) {
    Column(modifier = Modifier
        .width(160.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        .padding(8.dp)
    ) {
        // Etiqueta del tipo
        Box(modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(typeColor(b.type), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(typeLabel(b.type), color = Color.White,
                style = MaterialTheme.typography.labelMedium)
        }
        if (!b.photoPath.isNullOrBlank()) {
            // Coil cargará el path (TODO: si es path-de-storage, pedir signed URL)
            AsyncImage(
                model = b.photoPath, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 6.dp)
            )
        }
        Text(b.name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1)
        if (b.lines.isNotEmpty()) {
            Text("${b.lines.size} ${if (b.lines.size == 1) "línea" else "líneas"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    "BLOCK"   -> "PIEDRA"
    "PARKING" -> "PARKING"
    "ZONE"    -> "ZONA"
    else      -> type
}

private fun typeColor(type: String) = when (type) {
    "BLOCK"   -> Color(0xFFC2410C)   // terra
    "PARKING" -> Color(0xFF5E6B4F)   // moss
    "ZONE"    -> Color(0xFFB45309)   // warn
    else      -> Color(0xFF5A574F)
}
