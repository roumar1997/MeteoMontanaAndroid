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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    schoolName: String = "",
    schoolId: String = "",
    viewModel: SchoolDetailViewModel? = null,
    onMyProposals: () -> Unit = {}
) {
    if (schoolLat == null || schoolLon == null || viewModel == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Buscador de vías/bloques de la escuela (como el de escuelas en la
        // lista): al elegir un resultado se abre su piedra con la vía marcada.
        var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Buscar vías/bloques…") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor   = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        val q = query.trim()
        if (q.length >= 2) {
            // Vías cuyo nombre casa + piedras cuyo nombre casa.
            data class Hit(val label: String, val sub: String, val lineId: String?, val name: String)
            val hits = buildList {
                blocks.filter { it.type == "BLOCK" }.forEach { b ->
                    b.lines.forEach { l ->
                        if (l.name.contains(q, ignoreCase = true)) add(Hit(
                            label = l.name + (l.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            sub = b.name, lineId = l.id, name = l.name))
                    }
                    if (b.name.contains(q, ignoreCase = true)) add(Hit(
                        label = b.name, sub = "${b.lines.size} vías", lineId = null, name = b.name))
                }
            }.take(8)
            if (hits.isEmpty()) {
                Text("Sin resultados en esta escuela",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                ) {
                    hits.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    viewModel.openVia(h.lineId, h.name)
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(h.label, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                modifier = Modifier.weight(1f))
                            Text(h.sub, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        SchoolMap(
            centerLat     = schoolLat,
            centerLon     = schoolLon,
            blocks        = blocks,
            schoolName    = schoolName,
            schoolId      = schoolId,
            viewModel     = viewModel,
            onMyProposals = onMyProposals
        )
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
