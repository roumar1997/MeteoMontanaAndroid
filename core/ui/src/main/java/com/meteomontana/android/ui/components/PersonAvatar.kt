package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Foto redonda con la aureola de su titulo (o la inicial si no tiene foto): la misma que el feed. */
@Composable
fun PersonAvatar(photoUrl: String?, name: String?, uid: String?, size: androidx.compose.ui.unit.Dp) {
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.userRing(uid, size).size(size).clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(name?.trim()?.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
