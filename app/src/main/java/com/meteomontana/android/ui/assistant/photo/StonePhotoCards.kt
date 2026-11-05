package com.meteomontana.android.ui.assistant.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.screens.schools.MiniTopoThumbnail
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg

/** Las piedras más probables de la foto, una fila por piedra: su foto, su nombre y cuánto se parece. Tocar abre la piedra. */
@Composable
fun AssistantPhotoMatchesView(
    candidates: List<StoneCandidate>,
    words: StonePhotoWords,
    modifier: Modifier = Modifier,
    onOpen: (StoneCandidate) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.forEach { c -> StoneCandidateRow(c, words, onOpen) }
    }
}

@Composable
private fun StoneCandidateRow(c: StoneCandidate, words: StonePhotoWords, onOpen: (StoneCandidate) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (c.lineId != null) Modifier.clickable { onOpen(c) } else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val thumb = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
        if (c.photoUrl != null) {
            MiniTopoThumbnail(c.photoUrl, null, null, thumb)
        } else {
            Box(thumb.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(c.blockName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                if (c.lineId == null) words.noLines else words.detail(c),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp
            )
        }
        if (c.lineId != null) {
            Text(words.open, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(TerraBg).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}
