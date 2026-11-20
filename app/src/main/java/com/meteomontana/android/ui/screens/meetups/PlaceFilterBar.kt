package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.screens.schools.TabButton

/** Qué quedadas se ven: todas, solo de roca (escuelas) o solo de rocódromo. */
enum class PlaceFilter { ALL, ROCK, GYM }

/** Selector «Todas | Roca | Rocódromo»: tres segmentos iguales, como el de Roca | Rocódromos de Escuelas. */
@Composable
fun PlaceFilterBar(selected: PlaceFilter, onSelect: (PlaceFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(3.dp)
    ) {
        TabButton(stringResource(R.string.meetups_place_all).uppercase(), selected == PlaceFilter.ALL, Modifier.weight(1f)) { onSelect(PlaceFilter.ALL) }
        TabButton(stringResource(R.string.meetups_place_rock).uppercase(), selected == PlaceFilter.ROCK, Modifier.weight(1f)) { onSelect(PlaceFilter.ROCK) }
        TabButton(stringResource(R.string.meetups_place_gym).uppercase(), selected == PlaceFilter.GYM, Modifier.weight(1f)) { onSelect(PlaceFilter.GYM) }
    }
}
