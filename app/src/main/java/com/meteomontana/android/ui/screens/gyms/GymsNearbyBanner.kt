package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.gym.GymNear
import com.meteomontana.android.domain.usecase.gym.FindGymsNearUseCase
import com.meteomontana.android.domain.usecase.gym.ShouldSuggestGymsUseCase
import com.meteomontana.android.ui.components.LocalFeatureFlags
import com.meteomontana.android.ui.theme.CumbreStatCardShape
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Abre la ficha de un rocódromo desde cualquier pantalla del overlay (la provee `SheetNavHost`). */
@Suppress("CompositionLocalAllowlist")
val LocalOpenGym = staticCompositionLocalOf<(Long) -> Unit> { {} }

@HiltViewModel
class GymsNearbyViewModel @Inject constructor(
    private val shouldSuggestGyms: ShouldSuggestGymsUseCase,
    private val findGymsNear: FindGymsNearUseCase
) : ViewModel() {
    private val _near = MutableStateFlow<List<GymNear>>(emptyList())
    val near: StateFlow<List<GymNear>> = _near
    private var loadedFor: Pair<Double, Double>? = null

    /** Plan B: roca mojada + interruptor encendido (el criterio de roca mojada es el de siempre, sin umbrales nuevos). */
    fun shouldSuggest(current: Current, gymsEnabled: Boolean) = shouldSuggestGyms(current, gymsEnabled)

    fun load(lat: Double, lon: Double) {
        if (loadedFor == lat to lon) return
        loadedFor = lat to lon
        viewModelScope.launch { runCatching { findGymsNear.execute(lat, lon) }.onSuccess { _near.value = it } }
    }
}

/**
 * Plan B de la ficha de una escuela: si la roca está mojada, «Rocódromos cerca» con los más próximos (30 km, 5).
 * No sale con el interruptor `gyms_enabled` apagado ni mientras no haya resultados.
 */
@Composable
fun GymsNearbyBanner(
    lat: Double,
    lon: Double,
    current: Current,
    modifier: Modifier = Modifier,
    viewModel: GymsNearbyViewModel = hiltViewModel()
) {
    val show = viewModel.shouldSuggest(current, LocalFeatureFlags.current.isGymsEnabled)
    val near by viewModel.near.collectAsStateWithLifecycle()
    val open = LocalOpenGym.current
    LaunchedEffect(show, lat, lon) { if (show) viewModel.load(lat, lon) }
    if (!show || near.isEmpty()) return

    Column(
        modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(CumbreStatCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.gym_nearby_title).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.gym_nearby_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        near.forEach { gym ->
            Row(
                Modifier.fillMaxWidth().clickable { open(gym.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GymKindBadge(gym.kind, size = 34.dp)
                Text(
                    gym.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
                )
                Text(GymPresentation.distanceText(gym.distanceKm).orEmpty(), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
